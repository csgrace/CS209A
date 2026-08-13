/**
 * QQ Farm Interactive Client — connects to the in-browser FarmServer engine.
 * Handles all UI rendering, user interactions, and the concurrency demo.
 */
(() => {
  'use strict';

  let server = null;
  let playerName = null;
  let viewingPlayer = null;
  let selectedPlot = -1;
  let growthTimer = null;
  const els = {};

  document.addEventListener('DOMContentLoaded', init);

  function init() {
    server = new window.FarmServer();
    server.onEvent(onServerEvent);

    // Cache DOM elements
    els.loginOverlay = document.getElementById('login-overlay');
    els.loginBtn = document.getElementById('login-btn');
    els.usernameInput = document.getElementById('username-input');
    els.loginError = document.getElementById('login-error');
    els.playerLabel = document.getElementById('player-label');
    els.viewingLabel = document.getElementById('viewing-label');
    els.coinsDisplay = document.getElementById('coins-display');
    els.statusBar = document.getElementById('status-bar');
    els.farmGrid = document.getElementById('farm-grid');
    els.logPanel = document.getElementById('log-panel');
    els.logContent = document.getElementById('log-content');
    els.playerTabs = document.getElementById('player-tabs');
    els.stealModal = document.getElementById('steal-modal');
    els.concurrentModal = document.getElementById('concurrent-modal');
    els.visitModal = document.getElementById('visit-modal');
    els.locationBanner = document.getElementById('location-banner');
    els.locText = document.getElementById('loc-text');
    els.locStatus = document.getElementById('loc-status');
    els.stealBtn = document.getElementById('btn-steal-mode');

    els.loginBtn.addEventListener('click', handleLogin);
    els.usernameInput.addEventListener('keydown', e => { if (e.key === 'Enter') handleLogin(); });

    document.getElementById('btn-players').addEventListener('click', openVisitModal);

    document.getElementById('btn-plant-mode').addEventListener('click', () => { setSelectedAction('plant'); });
    document.getElementById('btn-harvest-mode').addEventListener('click', () => { setSelectedAction('harvest'); });
    document.getElementById('btn-steal-mode').addEventListener('click', openStealModal);
    document.getElementById('btn-concurrent').addEventListener('click', openConcurrentModal);

    document.getElementById('btn-view-home').addEventListener('click', () => {
      if (!playerName) return;
      viewingPlayer = playerName;
      renderFarm(server.get(playerName));
      updateUI();
      setStatus(`回到自己的农场`);
    });

    document.getElementById('tabs-btn-game').addEventListener('click', () => switchTab('game'));
    document.getElementById('tabs-btn-log').addEventListener('click', () => switchTab('log'));
    document.getElementById('tabs-btn-concurrent').addEventListener('click', () => switchTab('concurrent'));

    // Steal modal
    document.getElementById('steal-confirm-btn').addEventListener('click', executeSteal);
    document.getElementById('steal-cancel-btn').addEventListener('click', () => {
      els.stealModal.classList.add('hidden');
    });

    // Concurrent modal
    document.getElementById('concurrent-start-btn').addEventListener('click', executeConcurrentTest);
    document.getElementById('concurrent-close-btn').addEventListener('click', () => {
      els.concurrentModal.classList.add('hidden');
    });

    // Visit modal
    document.getElementById('visit-close-btn').addEventListener('click', () => {
      els.visitModal.classList.add('hidden');
    });

    createGrid();
    startGrowthTicker();
    // Setup demo players with crops so the farm isn't empty
    setupDemoPlayers();
    // Show initial log messages
    server.logMsg('SERVER', '🌾 QQ Farm 服务器已启动', 'MAIN');
    server.logMsg('SERVER', '📡 模拟 Java synchronized 互斥锁 + ConcurrentHashMap', 'MAIN');
    server.logMsg('INFO', '💡 点击 "去别人家" 选择一个玩家，然后就可以偷菜了！', 'MAIN');
    server.logMsg('INFO', '💡 点击 "并发测试" 可以看到 CountDownLatch 并发演示', 'MAIN');
    switchTab('log');
  }

  // ── Login ──────────────────────────────────────────────────
  function handleLogin() {
    const name = els.usernameInput.value.trim();
    if (!name) { els.loginError.textContent = '请输入用户名'; return; }
    playerName = name;
    viewingPlayer = playerName;
    const result = server.login(name);
    els.loginOverlay.classList.add('hidden');
    renderFarm(result.snapshot);
    updateUI();
    setStatus(`登录成功！欢迎 ${name}`);
  }

  // ── Farm Grid ──────────────────────────────────────────────
  function createGrid() {
    els.farmGrid.innerHTML = '';
    for (let i = 0; i < 16; i++) {
      const cell = document.createElement('button');
      cell.className = 'farm-cell empty';
      cell.dataset.idx = i;
      cell.innerHTML = '<span class="cell-icon">🟫</span><span class="cell-text">空地</span>';
      cell.addEventListener('click', () => onPlotClick(i));
      els.farmGrid.appendChild(cell);
    }
  }

  function renderFarm(snapshot) {
    if (!snapshot) return;
    els.coinsDisplay.textContent = snapshot.coins;
    snapshot.board.forEach((state, i) => {
      const cell = els.farmGrid.children[i];
      if (!cell) return;
      cell.classList.remove('empty', 'growing', 'ripe');
      cell.dataset.state = state;
      if (state === 'EMPTY') {
        cell.classList.add('empty');
        cell.innerHTML = '<span class="cell-icon">🟫</span><span class="cell-text">空地</span>';
      } else if (state === 'GROWING') {
        cell.classList.add('growing');
        cell.innerHTML = '<span class="cell-icon">🌱</span><span class="cell-text">生长中</span>';
      } else if (state === 'RIPE') {
        cell.classList.add('ripe');
        cell.innerHTML = '<span class="cell-icon">🌻</span><span class="cell-text">成熟!</span>';
      }
    });
  }

  function onPlotClick(idx) {
    const cell = els.farmGrid.children[idx];
    const state = cell.dataset.state;
    const isOwn = viewingPlayer === playerName;
    const r = Math.floor(idx / 4);
    const c = idx % 4;

    // Clear selection
    Array.from(els.farmGrid.children).forEach(c => c.classList.remove('selected'));

    if (currentAction === 'plant') {
      if (!isOwn) { setStatus('只能在自己的农场种植'); return; }
      if (state !== 'EMPTY') { setStatus('这块地不是空地'); return; }
      try {
        const snap = server.plant(playerName, r, c);
        renderFarm(snap);
        setStatus(`在 (${r},${c}) 种下种子，10秒后成熟`);
      } catch (e) { setStatus(e.message); }
    } else if (currentAction === 'harvest') {
      if (!isOwn) { setStatus('只能收获自己的作物'); return; }
      if (state !== 'RIPE') { setStatus('这块地的作物还没熟'); return; }
      try {
        const snap = server.harvest(playerName, r, c);
        renderFarm(snap);
        setStatus(`收获成功！+12 金币`);
      } catch (e) { setStatus(e.message); }
    } else {
      // Selection mode
      selectedPlot = idx;
      cell.classList.add('selected');
      if (state === 'EMPTY') setStatus(`选中空地 (${r},${c})`);
      else if (state === 'GROWING') setStatus(`选中生长中 (${r},${c})`);
      else if (state === 'RIPE') {
        if (isOwn) setStatus(`选中成熟作物 (${r},${c}) — 可收获`);
        else setStatus(`选中 ${viewingPlayer} 的成熟作物 (${r},${c}) — 可偷！`);
      }
    }
  }

  let currentAction = null;
  function setSelectedAction(action) {
    currentAction = currentAction === action ? null : action;
    document.getElementById('btn-plant-mode').classList.toggle('active', currentAction === 'plant');
    document.getElementById('btn-harvest-mode').classList.toggle('active', currentAction === 'harvest');
    if (currentAction) setStatus(`模式: ${action === 'plant' ? '种植' : '收获'} — 点击地块`);
  }

  // ── Setup Demo Players with Crops ────────────────────────────
  function setupDemoPlayers() {
    const demoNames = ['alice', 'bob', 'victim'];
    demoNames.forEach(name => {
      const isNew = !server.getPlayers().includes(name);
      if (isNew) {
        server.login(name);
      }
      const farm = server.getFarm(name);
      // Auto-plant some ripe crops if the farm is empty
      if (farm.getRipeCount() === 0) {
        let planted = 0;
        for (let i = 0; i < 16 && planted < 6; i++) {
          if (farm.board[i] === 'EMPTY') {
            farm.board[i] = 'RIPE';
            farm.timestamps[i] = 0;
            planted++;
          }
        }
        if (isNew && planted > 0) {
          server.logMsg('SETUP', `🌻 ${name} 的农场种了 ${planted} 棵成熟作物`, 'INIT');
        }
      }
      // Make NPC players "away from home" so they can be stolen
      server.currentView.set(name, '_away_');
    });
  }

  // ── Visit Other Players ─────────────────────────────────────
  function openVisitModal() {
    if (!playerName) return;
    // Ensure demo players exist with crops
    setupDemoPlayers();
    const otherPlayers = server.getPlayers().filter(p => p !== playerName);
    if (otherPlayers.length === 0) {
      setStatus('没有其他玩家可以访问');
      return;
    }
    // Build player list
    const list = document.getElementById('visit-player-list');
    list.innerHTML = '';
    otherPlayers.forEach(name => {
      const farm = server.getFarm(name);
      const ripeCount = farm.getRipeCount();
      const item = document.createElement('button');
      item.className = 'player-list-item';
      item.innerHTML = `
        <span class="pli-name">🌻 ${name}</span>
        <span class="pli-info">🌻×${ripeCount} 成熟 — 可偷！</span>
      `;
      item.addEventListener('click', () => {
        els.visitModal.classList.add('hidden');
        visitPlayer(name);
      });
      list.appendChild(item);
    });
    els.visitModal.classList.remove('hidden');
  }

  function visitPlayer(name) {
    // Make sure target has been viewed by current player (start session)
    const result = server.view(playerName, name);
    viewingPlayer = name;
    selectedPlot = -1;
    renderFarm(result.snapshot);
    updateUI();
    setStatus(`🔓 进入 ${name} 的农场 — 偷菜按钮已解锁！`);
  }

  function refreshFarm() {
    const snap = server.get(playerName);
    viewingPlayer = playerName;
    renderFarm(snap);
    updateUI();
    setStatus('已刷新自己的农场');
  }

  // ── Steal ──────────────────────────────────────────────────
  function openStealModal() {
    if (!playerName) return;
    const players = server.getPlayers().filter(p => p !== playerName);
    if (players.length === 0) {
      // Auto-create
      server.login('alice');
      server.login('bob');
      players.push('alice', 'bob');
    }
    const select = document.getElementById('steal-target');
    select.innerHTML = players.map(p => `<option value="${p}">${p}</option>`).join('');
    document.getElementById('steal-info').innerHTML = `
      <p><strong>规则：</strong></p>
      <ul>
        <li>受害者必须不在家（不能正在查看自己的农场）</li>
        <li>每次 session 最多偷 25% 的成熟作物</li>
        <li>偷一次后本轮禁止再偷（需等对方种新作物）</li>
      </ul>
    `;
    els.stealModal.classList.remove('hidden');
  }

  async function executeSteal() {
    const target = document.getElementById('steal-target').value;
    if (!target) return;
    els.stealModal.classList.add('hidden');

    // Step 1: View the victim first (required by server)
    try {
      server.view(playerName, target);
    } catch (e) { /* ok if already viewing */ }

    // Step 2: Make victim "away from home"
    server.currentView.set(target, '_away_');
    server.logMsg('STATUS', `${target} 离开了家（ victim is away ）`, 'SYSTEM');

    try {
      const result = await server.steal(playerName, target);
      // Show the victim's farm after stealing
      const victimSnap = server.getFarm(target).snapshot();
      viewingPlayer = target;
      renderFarm(victimSnap);
      updateUI();
      setStatus(`偷菜成功！偷了 ${target} 的地块 #${result.stolenIdx}，+3 金币 | session: ${result.sessionCount}/${result.maxSteal}`);
    } catch (e) {
      setStatus(`偷菜失败: ${e.message}`);
    }
  }

  // ── Concurrent Test ─────────────────────────────────────────
  function openConcurrentModal() {
    // Ensure we have enough players
    const existing = server.getPlayers();
    if (!existing.includes('alice')) server.login('alice');
    if (!existing.includes('bob')) server.login('bob');
    if (!existing.includes('victim')) server.login('victim');

    const victim = server.getFarm('victim');

    // Plant some ripe crops on victim's farm
    let planted = 0;
    for (let i = 0; i < 16 && planted < 8; i++) {
      if (victim.board[i] === 'EMPTY') {
        victim.board[i] = 'RIPE';
        victim.timestamps[i] = 0;
        planted++;
      }
    }

    // Make victim away from home
    server.currentView.set('victim', '_away_');

    // Setup thieves: both view victim
    server.view('alice', 'victim');
    server.view('bob', 'victim');

    document.getElementById('concurrent-setup').innerHTML = `
      <p><strong>Victim:</strong> victim (8 个成熟作物)</p>
      <p><strong>Thief A:</strong> alice</p>
      <p><strong>Thief B:</strong> bob</p>
      <p><strong>规则:</strong> 两人同时发起 steal，maxSteal = floor(8 × 25%) = 2</p>
      <p class="hint">synchronized 锁保证同一时刻只有一个线程能偷</p>
    `;

    document.getElementById('concurrent-result').innerHTML = `
      <p class="concurrent-hint">👆 点击 "开始并发测试" 按钮运行 CountDownLatch 并发演示</p>
    `;
    server.logMsg('CONCURRENT-SETUP', '⚡ 并发测试就绪: alice + bob → victim (8 作物, maxSteal=2)', 'MAIN');
    els.concurrentModal.classList.remove('hidden');
  }

  async function executeConcurrentTest() {
    const btn = document.getElementById('concurrent-start-btn');
    btn.disabled = true;
    btn.textContent = '运行中...';

    const result = await server.runConcurrentSteal('alice', 'bob', 'victim');

    const resultDiv = document.getElementById('concurrent-result');
    resultDiv.innerHTML = `
      <div class="concurrent-results">
        <div class="concurrent-result-card ${result.resultA.success ? 'success' : 'fail'}">
          <h4>Alice</h4>
          <p>${result.resultA.success
            ? `✓ 偷到了！地块#${result.resultA.stolenIdx}，+3金币`
            : `✗ 失败: ${result.resultA.reason}`}</p>
        </div>
        <div class="concurrent-result-card ${result.resultB.success ? 'success' : 'fail'}">
          <h4>Bob</h4>
          <p>${result.resultB.success
            ? `✓ 偷到了！地块#${result.resultB.stolenIdx}，+3金币`
            : `✗ 失败: ${result.resultB.reason}`}</p>
        </div>
      </div>
      <p class="concurrent-summary">
        victim 剩余成熟作物: ${server.getFarm('victim').getRipeCount()}
        | victim金币: ${server.getFarm('victim').coins}
        | alice金币: ${server.getFarm('alice').coins}
        | bob金币: ${server.getFarm('bob').coins}
      </p>
    `;

    btn.disabled = false;
    btn.textContent = '开始并发测试';
  }

  // ── Growth Ticker ───────────────────────────────────────────
  function startGrowthTicker() {
    growthTimer = setInterval(() => {
      let changed = false;
      for (const [name, farm] of server.farms) {
        farm.board.forEach((state, i) => {
          if (state === 'GROWING' && Date.now() - farm.timestamps[i] >= window.FARM_CONFIG.GROW_TIME_MS) {
            farm.board[i] = 'RIPE';
            farm.timestamps[i] = 0;
            changed = true;
          }
        });
      }
      // Refresh if the currently viewing farm changed
      if (changed && viewingPlayer) {
        const farm = server.getFarm(viewingPlayer);
        renderFarm(farm.snapshot());
      }
    }, 500);
  }

  // ── Server Event Log ────────────────────────────────────────
  function onServerEvent(evt) {
    const div = document.createElement('div');
    div.className = `log-entry log-${getTagClass(evt.tag)}`;

    const time = new Date(evt.time);
    const ts = `${time.getMinutes().toString().padStart(2, '0')}:${time.getSeconds().toString().padStart(2, '0')}.${Math.floor(time.getMilliseconds()).toString().padStart(3, '0')}`;

    div.innerHTML = `
      <span class="log-time">${ts}</span>
      <span class="log-tag">${evt.tag}</span>
      <span class="log-thread">[${evt.thread}]</span>
      <span class="log-msg">${escapeHtml(evt.msg)}</span>
    `;
    els.logContent.appendChild(div);
    els.logContent.scrollTop = els.logContent.scrollHeight;
  }

  function getTagClass(tag) {
    if (tag.includes('STEAL-SUCCESS')) return 'success';
    if (tag.includes('STEAL-DENY') || tag.includes('STEAL-FAIL')) return 'error';
    if (tag.includes('CONCURRENT')) return 'info';
    if (tag.includes('ENTER') || tag.includes('EXIT')) return 'warn';
    return '';
  }

  function escapeHtml(str) {
    const d = document.createElement('div');
    d.textContent = str;
    return d.innerHTML;
  }

  // ── UI Helpers ──────────────────────────────────────────────
  function updateUI() {
    els.playerLabel.textContent = playerName || '未登录';
    // Update location banner & viewing label
    if (!playerName) {
      els.viewingLabel.textContent = '未登录';
      els.locText.textContent = '请先登录';
      els.locStatus.textContent = '';
      els.locationBanner.className = 'location-banner';
    } else if (viewingPlayer === playerName) {
      els.viewingLabel.textContent = '自家 🏠';
      els.locText.textContent = '自家农场';
      els.locStatus.textContent = '🌱 安全—种植/收获模式';
      els.locationBanner.className = 'location-banner at-home';
      // Disable steal button at own farm
      els.stealBtn.disabled = true;
      els.stealBtn.title = '不能偷自己的菜，先去别人家！';
      els.stealBtn.textContent = '🦹 偷菜 🔒';
    } else {
      els.viewingLabel.textContent = `${viewingPlayer} 的家`;
      els.locText.textContent = `正在访问 ${viewingPlayer} 的农场`;
      els.locStatus.textContent = '🔓 可以偷菜！';
      els.locationBanner.className = 'location-banner at-other';
      // Enable steal button when visiting others
      els.stealBtn.disabled = false;
      els.stealBtn.title = '';
      els.stealBtn.textContent = '🦹 偷菜';
    }
  }

  function setStatus(text) { els.statusBar.textContent = text; }

  function switchTab(tab) {
    document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
    document.getElementById(`tabs-btn-${tab}`).classList.add('active');
    document.getElementById('panel-game').classList.toggle('hidden', tab !== 'game');
    document.getElementById('panel-log').classList.toggle('hidden', tab !== 'log');
    document.getElementById('panel-concurrent').classList.toggle('hidden', tab !== 'concurrent');
  }
})();
