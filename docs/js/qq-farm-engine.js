/**
 * QQ Farm Server Engine — JavaScript simulation of the Java server.
 * Implements the same concurrency controls:
 *   - synchronized(victim) → async mutex lock
 *   - session-based steal limits (max 25% of ripe per session)
 *   - victim-at-home protection
 *   - CountDownLatch-style concurrent steal simulation
 *   - ConcurrentHashMap → Map with atomic operations
 */
(() => {
  'use strict';

  // ── Mutex: simulates Java's synchronized block ────────────
  class Mutex {
    constructor() { this._queue = []; this._locked = false; }
    async acquire() {
      return new Promise(resolve => {
        if (!this._locked) { this._locked = true; resolve(); }
        else { this._queue.push(resolve); }
      });
    }
    release() {
      if (this._queue.length > 0) {
        const next = this._queue.shift();
        next();
      } else {
        this._locked = false;
      }
    }
    async runExclusive(fn) {
      await this.acquire();
      try { return await fn(); }
      finally { this.release(); }
    }
  }

  // ── Game Logic (mirrors Game.java) ─────────────────────────
  const PlotState = { EMPTY: 'EMPTY', GROWING: 'GROWING', RIPE: 'RIPE' };
  const PLANT_COST = 5;
  const HARVEST_REWARD = 12;
  const STEAL_REWARD = 3;
  const GROW_TIME_MS = 10_000;

  class Game {
    constructor() {
      this.board = Array(16).fill(PlotState.EMPTY);
      this.timestamps = Array(16).fill(0);
      this._coins = 40;
      this.mutex = new Mutex();
    }
    get coins() { return this._coins; }
    set coins(v) { this._coins = Math.max(0, v); }
    getState(i) { return this.board[i]; }
    plant(i) {
      if (this.board[i] !== PlotState.EMPTY) throw new Error('地块已被占用');
      if (this._coins < PLANT_COST) throw new Error('金币不足');
      this._coins -= PLANT_COST;
      this.board[i] = PlotState.RIPE; // Demo: instant growth
      this.timestamps[i] = Date.now();
      return this._coins;
    }
    harvest(i) {
      if (this.board[i] !== PlotState.RIPE) throw new Error('作物尚未成熟');
      this.board[i] = PlotState.EMPTY;
      this.timestamps[i] = 0;
      this._coins += HARVEST_REWARD;
      return this._coins;
    }
    getRipeCount() {
      return this.board.filter(s => s === PlotState.RIPE).length;
    }
    getGrowingIndices() {
      return this.board.map((s, i) => s === PlotState.GROWING ? i : -1).filter(i => i >= 0);
    }
    getRipeIndices() {
      return this.board.map((s, i) => s === PlotState.RIPE ? i : -1).filter(i => i >= 0);
    }
    addCoins(delta) { this._coins = Math.max(0, this._coins + delta); }
    // Called from within synchronized block only
    stealOneRipe() {
      const ripeIdx = this.getRipeIndices();
      if (ripeIdx.length === 0) return -1;
      const idx = ripeIdx[Math.floor(Math.random() * ripeIdx.length)];
      this.board[idx] = PlotState.EMPTY;
      this.timestamps[idx] = 0;
      return idx;
    }
    snapshot() {
      return { coins: this._coins, board: [...this.board] };
    }
  }

  // ── Server Logic (mirrors Server.java) ─────────────────────
  class FarmServer {
    constructor() {
      this.farms = new Map();          // playerName → Game
      this.users = new Map();          // playerName → { coins, name }
      this.currentView = new Map();    // playerName → viewingPlayerName
      this.canStealThisCycle = new Map(); // playerName → Map<victim, boolean>
      this.sessionStealCounts = new Map();  // playerName → Map<victim, count>
      this.globalMutex = new Mutex();  // Simulates synchronized(victim)
      this.log = [];                   // Server log for display
      this.listeners = [];
      this._running = false;
    }

    onEvent(fn) { this.listeners.push(fn); }
    emit(evt) {
      this.log.push(evt);
      if (this.log.length > 200) this.log.shift();
      this.listeners.forEach(fn => fn(evt));
    }
    logMsg(tag, msg, thread) {
      this.emit({ tag, msg, thread, time: Date.now(), nano: performance.now() });
    }

    getFarm(name) {
      if (!this.farms.has(name)) {
        this.farms.set(name, new Game());
      }
      return this.farms.get(name);
    }

    getPlayers() {
      return [...this.farms.keys()];
    }

    start() {
      this._running = true;
      this.logMsg('SERVER', 'Server started on port 5050', 'SYSTEM');
    }

    isRunning() { return this._running; }

    countRipe(name) {
      const farm = this.getFarm(name);
      return farm.getRipeCount();
    }

    visit(viewer, target) {
      this.currentView.set(viewer, target);
      this.logMsg('VIEW', `${viewer} visits ${target}'s farm`, viewer);
    }

    newSession(thief) {
      // Reset steal session for all victims
      if (this.sessionStealCounts.has(thief)) {
        this.sessionStealCounts.delete(thief);
      }
      if (this.canStealThisCycle.has(thief)) {
        this.canStealThisCycle.delete(thief);
      }
    }

    // Concurrent steal with callback for live logging
    async concurrentSteal(thieves, victim, onLog) {
      const msg = (m, type) => {
        this.logMsg('CONCURRENT', m, 'SYSTEM');
        if (onLog) on(m, type);
      };

      msg(`=== Concurrent Steal Test: ${thieves.join(' & ')} stealing from ${victim} ===`);
      msg('Preparing CountDownLatch(1)...', 'thread');

      const ready = {};
      thieves.forEach(t => { ready[t] = false; });

      const stealAttempt = async (thiefName) => {
        ready[thiefName] = true;
        msg(`[Thread-${thiefName}] Ready, waiting for countdown...`, 'thread');
        // Wait for all ready
        while (!thieves.every(t => ready[t])) { await new Promise(r => setTimeout(r, 5)); }
        // Small extra delay to ensure both are in the race
        await new Promise(r => setTimeout(r, 10));
        try {
          const result = await this.steal(thiefName, victim);
          return { thief: thiefName, ...result };
        } catch (e) {
          return { thief: thiefName, success: false, reason: e.message };
        }
      };

      const promises = thieves.map(t => stealAttempt(t));

      await new Promise(r => setTimeout(r, 200));
      msg('>>> CountDownLatch.countDown() — GO!', 'thread');

      const results = await Promise.all(promises);

      results.forEach(r => {
        msg(`[${r.thief}] ${r.success ? '✓ SUCCESS' : '✗ FAILED: ' + r.reason}`, r.success ? 'ok' : 'err');
      });

      msg('=== Concurrent test complete ===', 'info');
      return results;
    }

    isVictimAtHome(victimName) {
      return this.currentView.get(victimName) === victimName;
    }

    // ── Actions ──
    login(playerName) {
      const farm = this.getFarm(playerName);
      this.currentView.set(playerName, playerName);
      this.users.set(playerName, { name: playerName, coins: farm.coins });
      this.logMsg('LOGIN', `${playerName} 已登录`, playerName);
      return { player: playerName, snapshot: farm.snapshot() };
    }

    get(playerName) {
      const farm = this.getFarm(playerName);
      this.currentView.set(playerName, playerName);
      return farm.snapshot();
    }

    view(viewer, target) {
      const farm = this.getFarm(target);
      const prev = this.currentView.get(viewer);
      if (prev && prev !== target && prev !== viewer) {
        const sessions = this.sessionStealCounts.get(viewer);
        if (sessions) {
          const cnt = sessions.get(prev) || 0;
          if (cnt > 0) {
            if (!this.canStealThisCycle.has(viewer)) {
              this.canStealThisCycle.set(viewer, new Map());
            }
            this.canStealThisCycle.get(viewer).set(prev, false);
            this.logMsg('VIEW-CHANGE', `${viewer} 离开 ${prev} 的农场 → 本轮禁止再偷`, viewer);
          }
          sessions.delete(prev);
        }
      }
      this.currentView.set(viewer, target);
      const canStart = this.canStealThisCycle.get(viewer)?.get(target) ?? true;
      const victimAtHome = this.isVictimAtHome(target);
      if (canStart && !victimAtHome) {
        if (!this.sessionStealCounts.has(viewer)) {
          this.sessionStealCounts.set(viewer, new Map());
        }
        this.sessionStealCounts.get(viewer).set(target, 0);
      }
      this.logMsg('VIEW', `${viewer} 查看 ${target} 的农场`, viewer);
      return { target, snapshot: farm.snapshot() };
    }

    plant(playerName, row, col) {
      const farm = this.getFarm(playerName);
      const idx = row * 4 + col;
      try {
        const coins = farm.plant(idx);
        // Update user coins
        if (this.users.has(playerName)) {
          this.users.get(playerName).coins = farm.coins;
        }
        // Reset steal cycles for all other players
        for (const [thief, map] of this.canStealThisCycle) {
          if (thief !== playerName) {
            if (!map) { /* noop */ }
            map.set(playerName, true);
            if (!this.sessionStealCounts.has(thief)) {
              this.sessionStealCounts.set(thief, new Map());
            }
            this.sessionStealCounts.get(thief).set(playerName, 0);
          }
        }
        this.logMsg('PLANT', `${playerName} 在 (${row},${col}) 种植, 金币=${coins}`, playerName);
        return true;
      } catch (e) {
        this.logMsg('PLANT-FAIL', `${playerName} 种植失败: ${e.message}`, playerName);
        return false;
      }
    }

    harvest(playerName, row, col) {
      const farm = this.getFarm(playerName);
      const idx = row * 4 + col;
      try {
        const coins = farm.harvest(idx);
        // Update user coins
        if (this.users.has(playerName)) {
          this.users.get(playerName).coins = farm.coins;
        }
        this.logMsg('HARVEST', `${playerName} 收获 (${row},${col}), 金币=${coins}`, playerName);
        return true;
      } catch (e) {
        this.logMsg('HARVEST-FAIL', `${playerName} 收获失败: ${e.message}`, playerName);
        return false;
      }
    }

    // ── STEAL: the key concurrency-controlled operation ──
    async steal(thiefName, victimName) {
      if (victimName === thiefName) {
        return { success: false, reason: '不能偷自己' };
      }

      // Check: must be viewing victim
      if (this.currentView.get(thiefName) !== victimName) {
        return { success: false, reason: '必须 VIEW 受害者之后才能偷菜' };
      }

      // Check: victim at home?
      if (this.isVictimAtHome(victimName)) {
        this.logMsg('STEAL-DENY', `${thiefName} 偷 ${victimName} 失败 → 受害者在家`, thiefName);
        return { success: false, reason: '受害者在家，无法偷菜' };
      }

      // Check: cycle allowed?
      const thiefCanMap = this.canStealThisCycle.get(thiefName) || new Map();
      const allowed = thiefCanMap.get(victimName) ?? true;
      if (!allowed) {
        this.logMsg('STEAL-DENY', `${thiefName} 偷 ${victimName} 失败 → 本轮禁止`, thiefName);
        return { success: false, reason: '本轮不能偷，等受害者种新作后再试' };
      }

      // ★ CRITICAL SECTION: synchronized(victim) ★
      // This async mutex ensures only one thief at a time
      const result = await this.globalMutex.runExclusive(async () => {
        const victim = this.getFarm(victimName);
        const thief = this.getFarm(thiefName);

        const preRipe = victim.getRipeCount();
        const maxSteal = Math.floor(preRipe * 0.25);

        this.logMsg('STEAL-ENTER', `${thiefName} 进入临界区 → victimRipe=${preRipe}, maxSteal=${maxSteal}`, thiefName);

        if (maxSteal <= 0) {
          return { success: false, reason: '可偷数量不够 (maxSteal=0)', preRipe: 0 };
        }

        const thiefSessions = this.sessionStealCounts.get(thiefName) || new Map();
        const sessionCount = thiefSessions.get(victimName) || 0;
        if (sessionCount >= maxSteal) {
          return { success: false, reason: '本轮偷菜已达上限', preRipe, maxSteal, sessionCount };
        }

        // Simulate processing delay to make concurrency visible
        await new Promise(r => setTimeout(r, 50 + Math.random() * 100));

        const stolenIdx = victim.stealOneRipe();
        if (stolenIdx < 0) {
          return { success: false, reason: '没有成熟作物了', preRipe: 0 };
        }

        thief.addCoins(STEAL_REWARD);
        const newCount = sessionCount + 1;
        thiefSessions.set(victimName, newCount);
        this.sessionStealCounts.set(thiefName, thiefSessions);

        const newMaxSteal = Math.floor(victim.getRipeCount() * 0.25);
        if (newCount >= maxSteal) {
          if (!this.canStealThisCycle.has(thiefName)) {
            this.canStealThisCycle.set(thiefName, new Map());
          }
          this.canStealThisCycle.get(thiefName).set(victimName, false);
        }

        // Update users Map
        if (this.users.has(thiefName)) {
          this.users.get(thiefName).coins = thief.coins;
        }

        this.logMsg('STEAL-SUCCESS',
          `${thiefName} 偷了 ${victimName} 的地块#${stolenIdx}！session=${newCount}/${maxSteal}`,
          thiefName);

        return {
          success: true,
          stolenIdx,
          sessionCount: newCount,
          maxSteal,
          thiefCoins: thief.coins,
          victimRipe: victim.getRipeCount(),
          victimRipeLeft: victim.getRipeCount(),
          preRipe,
        };
      });

      if (!result.success) {
        this.logMsg('STEAL-FAIL', `${thiefName} 偷 ${victimName}: ${result.reason}`, thiefName);
      }

      return result;
    }

    // ── Concurrency Test: CountDownLatch simulation ──
    async runConcurrentSteal(thiefA, thiefB, victim) {
      this.logMsg('CONCURRENT-START', `=== 并发偷菜测试: ${thiefA} 和 ${thiefB} 同时偷 ${victim} ===`, 'SYSTEM');

      const ready = { a: false, b: false, go: false };

      const stealAttempt = async (thiefName) => {
        ready[thiefName === thiefA ? 'a' : 'b'] = true;
        // Wait for both to be ready
        while (!(ready.a && ready.b)) { await new Promise(r => setTimeout(r, 5)); }
        // Wait for the GO signal
        while (!ready.go) { await new Promise(r => setTimeout(r, 5)); }
        // GO!
        try {
          const result = await this.steal(thiefName, victim);
          return { thief: thiefName, ...result };
        } catch (e) {
          return { thief: thiefName, success: false, reason: e.message };
        }
      };

      const promiseA = stealAttempt(thiefA);
      const promiseB = stealAttempt(thiefB);

      // Countdown: ensure both threads are ready, then GO!
      await new Promise(r => setTimeout(r, 200));
      this.logMsg('CONCURRENT-COUNTDOWN', '>>> 3... 2... 1... GO!', 'SYSTEM');
      ready.go = true;

      const [resultA, resultB] = await Promise.all([promiseA, promiseB]);

      this.logMsg('CONCURRENT-END',
        `结果: ${thiefA}=${resultA.success ? '✓' : '✗'}, ${thiefB}=${resultB.success ? '✓' : '✗'}`,
        'SYSTEM');

      return { resultA, resultB };
    }
  }

  // ── Expose globally ──
  window.FarmServer = FarmServer;
  window.Game = Game;
  window.PlotState = PlotState;
  window.FARM_CONFIG = { PLANT_COST, HARVEST_REWARD, STEAL_REWARD, GROW_TIME_MS };
})();
