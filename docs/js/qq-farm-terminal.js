/**
 * QQ Farm Terminal Client — simulates Java CLI experience.
 * Commands: server, login, plant, harvest, visit, steal, concurrent, etc.
 */
(() => {
  'use strict';

  let server = null;
  let currentPlayer = null;
  let viewingPlayer = null;

  const els = {};

  document.addEventListener('DOMContentLoaded', init);

  function init() {
    server = new window.FarmServer();
    server.onEvent(onServerEvent);

    els.output = document.getElementById('terminal-output');
    els.input = document.getElementById('term-input');
    els.send = document.getElementById('term-send');
    els.farmDisplay = document.getElementById('farm-display');
    els.farmGrid = document.getElementById('farm-grid-term');
    els.fdPlayer = document.getElementById('fd-player-name');
    els.fdCoins = document.getElementById('fd-coins');
    els.fdLocation = document.getElementById('fd-location');

    els.send.addEventListener('click', handleCommand);
    els.input.addEventListener('keydown', e => { if (e.key === 'Enter') handleCommand(); });

    // Quick buttons
    document.querySelectorAll('.qk-btn').forEach(btn => {
      btn.addEventListener('click', () => {
        const cmd = btn.dataset.cmd;
        els.input.value = cmd === 'server' ? 'java -jar Server.jar' : cmd;
        handleCommand();
      });
    });

    // Auto-focus input
    els.input.focus();
  }

  // ── Terminal Output ──────────────────────────────────────────
  function print(text, cls = 'result') {
    const div = document.createElement('div');
    div.className = `term-line ${cls}`;
    div.innerHTML = text;
    els.output.appendChild(div);
    els.output.scrollTop = els.output.scrollHeight;
  }

  function printPrompt(cmd) {
    print(`<span class="term-prompt">➜</span> <span class="term-tilde">~</span> ${escapeHtml(cmd)}`, 'info');
  }

  // ── Command Handler ──────────────────────────────────────────
  function handleCommand() {
    const raw = els.input.value.trim();
    if (!raw) return;
    els.input.value = '';
    printPrompt(raw);

    const parts = raw.split(/\s+/);
    const cmd = parts[0].toLowerCase();

    // Handle "java -jar Server.jar"
    if (raw.startsWith('java -jar')) {
      runServer();
      return;
    }

    switch (cmd) {
      case 'help': cmdHelp(); break;
      case 'server': runServer(); break;
      case 'login': cmdLogin(parts); break;
      case 'players': cmdPlayers(); break;
      case 'plant': cmdPlant(parts); break;
      case 'harvest': cmdHarvest(parts); break;
      case 'visit': cmdVisit(parts); break;
      case 'steal': cmdSteal(parts); break;
      case 'look': cmdLook(); break;
      case 'concurrent': cmdConcurrent(); break;
      case 'clear': clearTerm(); break;
      case 'about': cmdAbout(); break;
      default:
        print(`未知命令: ${escapeHtml(cmd)}。输入 <code>help</code> 查看命令列表。`, 'error');
    }
  }

  // ── Commands ─────────────────────────────────────────────────
  function cmdHelp() {
    print('┌─────────────────────────────────────────────┐', 'info');
    print('│  QQ Farm Terminal — 命令列表                │', 'info');
    print('├─────────────────────────────────────────────┤', 'info');
    print('│  server              启动服务器             │', 'info');
    print('│  login <name>        登录/创建玩家           │', 'info');
    print('│  players             列出所有玩家            │', 'info');
    print('│  plant <r> <c>       在 (r,c) 种植          │', 'info');
    print('│  harvest <r> <c>     收获 (r,c)             │', 'info');
    print('│  visit <name>        查看别人的农场          │', 'info');
    print('│  steal <name>        偷别人的菜              │', 'info');
    print('│  look                查看当前农场            │', 'info');
    print('│  concurrent          CountDownLatch测试     │', 'info');
    print('│  clear               清空终端               │', 'info');
    print('│  about               关于                   │', 'info');
    print('└─────────────────────────────────────────────┘', 'info');
    print('提示: 上方快捷按钮可快速执行常用命令', 'info');
  }

  function runServer() {
    print('[Server] Starting QQ Farm Server...', 'server');
    print('[Server] Loading class: Game.java, Server.java, Controller.java', 'server');
    print('[Thread-0] ServerSocket opened on port 5050', 'thread');
    print('[Server] ✓ Server started. Ready for connections.', 'server');
    print('提示: 输入 login <name> 来登录或创建玩家', 'info');
    // Auto-create NPC players
    server.login('alice');
    server.login('bob');
    server.login('victim');
    ['alice', 'bob', 'victim'].forEach(name => {
      server.currentView.set(name, '_away_');
      const farm = server.getFarm(name);
      let planted = 0;
      for (let i = 0; i < 16 && planted < 6; i++) {
        if (farm.board[i] === 'EMPTY') {
          farm.board[i] = 'RIPE';
          planted++;
        }
      }
    });
    print('[Server] NPC players created: alice, bob, victim (each has 6 ripe crops)', 'server');
  }

  function cmdLogin(parts) {
    if (parts.length < 2) { print('用法: login <name>', 'error'); return; }
    const name = parts[1];
    server.login(name);
    currentPlayer = name;
    viewingPlayer = name;
    print(`[Client] Connected to server as "${name}"`, 'info');
    print(`[Client] Session started. You have ${server.getFarm(name).coins} coins.`, 'info');
    showFarm(server.get(name));
  }

  function cmdPlayers() {
    const players = server.getPlayers();
    if (players.length === 0) { print('没有玩家。请先运行 server 命令。', 'error'); return; }
    print(`已注册玩家 (${players.length}):`, 'info');
    players.forEach(p => {
      const f = server.getFarm(p);
      const marker = p === currentPlayer ? ' ← (你)' : '';
      print(`  🌻 ${p}: ${f.coins}币, ${f.getRipeCount()}成熟${marker}`, 'result');
    });
  }

  function cmdPlant(parts) {
    if (!requireLogin()) return;
    if (parts.length < 3) { print('用法: plant <row> <col>  (0-3)', 'error'); return; }
    const r = parseInt(parts[1]), c = parseInt(parts[2]);
    if (isNaN(r) || isNaN(c) || r < 0 || r > 3 || c < 0 || c > 3) {
      print('行和列必须是 0-3 的数字', 'error'); return;
    }
    if (viewingPlayer !== currentPlayer) {
      print('只能在自己的农场种植。先输入 look 回到自家农场。', 'error'); return;
    }
    try {
      const snap = server.plant(currentPlayer, r, c);
      print(`[${threadName()}] synchronized(${currentPlayer}) ENTER`, 'thread');
      print(`🌱 在 (${r},${c}) 种下种子！金币: ${snap.coins} → 10秒后成熟`, 'result');
      print(`[${threadName()}] synchronized(${currentPlayer}) EXIT`, 'thread');
      showFarm(snap);
    } catch (e) {
      print(`✗ 种植失败: ${e.message}`, 'error');
    }
  }

  function cmdHarvest(parts) {
    if (!requireLogin()) return;
    if (parts.length < 3) { print('用法: harvest <row> <col>', 'error'); return; }
    const r = parseInt(parts[1]), c = parseInt(parts[2]);
    if (isNaN(r) || isNaN(c) || r < 0 || r > 3 || c < 0 || c > 3) {
      print('行和列必须是 0-3 的数字', 'error'); return;
    }
    if (viewingPlayer !== currentPlayer) {
      print('只能收获自己的作物。先输入 look 回到自家农场。', 'error'); return;
    }
    try {
      const snap = server.harvest(currentPlayer, r, c);
      print(`[${threadName()}] synchronized(${currentPlayer}) ENTER`, 'thread');
      print(`🧺 收获成功！+12金币. 总金币: ${snap.coins}`, 'result');
      print(`[${threadName()}] synchronized(${currentPlayer}) EXIT`, 'thread');
      showFarm(snap);
    } catch (e) {
      print(`✗ 收获失败: ${e.message}`, 'error');
    }
  }

  function cmdVisit(parts) {
    if (!requireLogin()) return;
    if (parts.length < 2) { print('用法: visit <name>', 'error'); return; }
    const target = parts[1];
    if (!server.getPlayers().includes(target)) {
      print(`玩家 "${target}" 不存在`, 'error'); return;
    }
    const result = server.view(currentPlayer, target);
    viewingPlayer = target;
    print(`[${threadName()}] Viewing ${target}'s farm...`, 'info');
    print(`📍 进入 ${target} 的农场`, 'result');
    showFarm(result.snapshot);
  }

  async function cmdSteal(parts) {
    if (!requireLogin()) return;
    if (parts.length < 2) { print('用法: steal <name>', 'error'); return; }
    const target = parts[1];
    if (!server.getPlayers().includes(target)) {
      print(`玩家 "${target}" 不存在`, 'error'); return;
    }
    if (target === currentPlayer) {
      print('不能偷自己的菜', 'error'); return;
    }
    // Auto-visit if needed
    if (viewingPlayer !== target) {
      server.view(currentPlayer, target);
      viewingPlayer = target;
      print(`[Client] Auto-visiting ${target} before steal...`, 'info');
    }
    // Ensure victim is away
    server.currentView.set(target, '_away_');
    try {
      const result = await server.steal(currentPlayer, target);
      print(`[${threadName()}] synchronized(${target}) ENTER ← LOCK ACQUIRED`, 'thread');
      print(`🦹 偷菜成功！从 ${target} 的地块#${result.stolenIdx} 偷了 +3金币`, 'steal-ok');
      print(`  Session: ${result.sessionCount}/${result.maxSteal} (max 25% of ripe)`, 'result');
      print(`[${threadName()}] synchronized(${target}) EXIT ← LOCK RELEASED`, 'thread');
      const victimSnap = server.getFarm(target).snapshot();
      showFarm(victimSnap);
    } catch (e) {
      print(`✗ 偷菜失败: ${e.message}`, 'steal-fail');
    }
  }

  function cmdLook() {
    if (!requireLogin()) return;
    viewingPlayer = currentPlayer;
    const snap = server.get(currentPlayer);
    print('🏠 回到自己的农场', 'info');
    showFarm(snap);
  }

  async function cmdConcurrent() {
    if (!requireLogin()) return;
    print('═══════════════════════════════════════════', 'thread');
    print('⚡ CountDownLatch Concurrent Steal Test', 'thread');
    print('═══════════════════════════════════════════', 'thread');

    // Setup: ensure alice, bob, victim exist
    ['alice', 'bob', 'victim'].forEach(n => {
      if (!server.getPlayers().includes(n)) server.login(n);
    });
    // Give victim 8 ripe crops
    const victimFarm = server.getFarm('victim');
    let planted = 0;
    for (let i = 0; i < 16 && planted < 8; i++) {
      if (victimFarm.board[i] === 'EMPTY') {
        victimFarm.board[i] = 'RIPE';
        planted++;
      }
    }
    server.currentView.set('victim', '_away_');
    server.view('alice', 'victim');
    server.view('bob', 'victim');

    print(`[Setup] victim has ${victimFarm.getRipeCount()} ripe crops`, 'info');
    print(`[Setup] maxSteal = floor(8 × 0.25) = 2`, 'info');
    print(`[Setup] alice and bob both visiting victim`, 'info');
    print('───────────────────────────────────────────', 'info');
    print('[CountDownLatch] countdown(1) → both threads await...', 'thread');
    print('[CountDownLatch] RELEASE! Both threads proceed simultaneously', 'thread');
    print('');

    const result = await server.runConcurrentSteal('alice', 'bob', 'victim');

    print('');
    print('────────── Results ──────────', 'info');
    const rA = result.resultA, rB = result.resultB;
    print(`alice: ${rA.success ? `✓ 偷到 #${rA.stolenIdx}, +3币` : `✗ ${rA.reason}`}`,
      rA.success ? 'steal-ok' : 'steal-fail');
    print(`bob:   ${rB.success ? `✓ 偷到 #${rB.stolenIdx}, +3币` : `✗ ${rB.reason}`}`,
      rB.success ? 'steal-ok' : 'steal-fail');
    print('');
    print(`victim 剩余: ${server.getFarm('victim').getRipeCount()} 成熟`, 'result');
    print(`alice 金币: ${server.getFarm('alice').coins} | bob 金币: ${server.getFarm('bob').coins}`, 'result');
    print('');
    print('[结论] synchronized 保证同一时间只有一个线程操作 victim → 原子性 ✓', 'thread');
  }

  function clearTerm() {
    els.output.innerHTML = '';
    print('终端已清空', 'info');
  }

  function cmdAbout() {
    print('═══════════════════════════════════════', 'info');
    print('QQ Farm Terminal Demo v1.0', 'info');
    print('CS209A Assignment 2 — Concurrency Demo', 'info');
    print('───────────────────────────────────────', 'info');
    print('模拟 Java 实现:', 'result');
    print('  • ServerSocket + Thread per client', 'result');
    print('  • synchronized(victim) 互斥锁', 'result');
    print('  • ConcurrentHashMap 状态管理', 'result');
    print('  • CountDownLatch 并发测试', 'result');
    print('  • Platform.runLater() GUI 更新', 'result');
    print('═══════════════════════════════════════', 'info');
  }

  // ── Helpers ──────────────────────────────────────────────────
  function requireLogin() {
    if (!currentPlayer) {
      print('请先登录: login <name>', 'error');
      return false;
    }
    return true;
  }

  function threadName() {
    const names = ['Thread-1', 'Thread-2', 'Thread-5', 'Thread-8', 'Thread-12', 'pool-2-thread-3'];
    return names[Math.floor(Math.random() * names.length)];
  }

  function showFarm(snap) {
    if (!snap) return;
    els.farmDisplay.classList.remove('hidden');
    els.fdPlayer.textContent = viewingPlayer || '-';
    els.fdCoins.textContent = `🪙 ${snap.coins}`;
    els.fdLocation.textContent = viewingPlayer === currentPlayer ? '自家农场' : `${viewingPlayer} 的家`;

    els.farmGrid.innerHTML = '';
    snap.board.forEach(state => {
      const cell = document.createElement('div');
      cell.className = 'fc ' + state.toLowerCase();
      if (state === 'EMPTY') cell.textContent = '⬛';
      else if (state === 'GROWING') cell.textContent = '🌱';
      else if (state === 'RIPE') cell.textContent = '🌻';
      els.farmGrid.appendChild(cell);
    });
  }

  function onServerEvent(evt) {
    // Real-time server log to terminal
    const time = new Date(evt.time);
    const ts = `${time.getMinutes().toString().padStart(2,'0')}:${time.getSeconds().toString().padStart(2,'0')}`;
    let cls = 'server';
    if (evt.tag.includes('STEAL-SUCCESS')) cls = 'steal-ok';
    else if (evt.tag.includes('STEAL-DENY') || evt.tag.includes('STEAL-FAIL')) cls = 'steal-fail';
    else if (evt.tag.includes('ENTER') || evt.tag.includes('EXIT')) cls = 'thread';
    print(`[${ts}] [${evt.thread}] ${evt.tag}: ${escapeHtml(evt.msg)}`, cls);
  }

  function escapeHtml(str) {
    const d = document.createElement('div');
    d.textContent = str;
    return d.innerHTML;
  }
})();
