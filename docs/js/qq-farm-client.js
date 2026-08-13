/**
 * QQ Farm Web Client — WebSocket frontend for the Java server
 * Connects to websocket_proxy.py (port 8765) → Java Server (port 5050)
 */
(() => {
  const WS_URL = 'ws://localhost:8765';
  const els = {};
  let ws = null;
  let playerName = null;
  let viewingPlayer = null;
  let myCoins = 0;
  let selectedPlot = null;
  let isOwnFarm = true;

  // ── DOM ready ──────────────────────────────────────────────
  document.addEventListener('DOMContentLoaded', init);

  function init() {
    els.loginOverlay = document.getElementById('login-overlay');
    els.loginBtn = document.getElementById('login-btn');
    els.usernameInput = document.getElementById('username-input');
    els.loginError = document.getElementById('login-error');
    els.terminal = document.getElementById('terminal');
    els.terminalOutput = document.getElementById('terminal-output');
    els.cmdInput = document.getElementById('cmd-input');
    els.cmdSubmit = document.getElementById('cmd-submit');
    els.farmGrid = document.getElementById('farm-grid');
    els.playerLabel = document.getElementById('player-label');
    els.viewingLabel = document.getElementById('viewing-label');
    els.coinsDisplay = document.getElementById('coins-display');
    els.statusBar = document.getElementById('status-bar');
    els.quickBtns = document.querySelectorAll('.quick-btn');
    els.connDot = document.getElementById('conn-dot');
    els.connText = document.getElementById('conn-text');

    els.loginBtn.addEventListener('click', handleLogin);
    els.usernameInput.addEventListener('keydown', e => { if (e.key === 'Enter') handleLogin(); });
    els.cmdSubmit.addEventListener('click', submitCommand);
    els.cmdInput.addEventListener('keydown', e => { if (e.key === 'Enter') submitCommand(); });

    els.quickBtns.forEach(btn => {
      btn.addEventListener('click', () => {
        const cmd = btn.dataset.cmd;
        if (cmd) sendCommand(cmd);
      });
    });

    createGrid();
    connectWebSocket();
  }

  // ── WebSocket ──────────────────────────────────────────────
  function connectWebSocket() {
    try {
      ws = new WebSocket(WS_URL);
    } catch {
      setConnectionStatus('error', '连接失败');
      printOutput('系统', '无法连接到 WebSocket 代理。请确认：\n  1. Java 服务器已启动 (端口 5050)\n  2. websocket_proxy.py 已启动 (端口 8765)', 'error');
      return;
    }

    ws.onopen = () => {
      setConnectionStatus('connected', '已连接');
      printOutput('系统', '已连接到服务器代理。请输入用户名登录。', 'info');
    };

    ws.onmessage = (event) => handleServerMessage(event.data);

    ws.onclose = () => {
      setConnectionStatus('disconnected', '已断开');
      printOutput('系统', '与服务器断开连接。', 'error');
    };

    ws.onerror = () => {
      setConnectionStatus('error', '连接错误');
      printOutput('系统', 'WebSocket 连接错误。请确认代理服务器正在运行。', 'error');
    };
  }

  function sendCommand(cmd) {
    if (!ws || ws.readyState !== WebSocket.OPEN) {
      printOutput('系统', '未连接到服务器。', 'error');
      return;
    }
    ws.send(cmd);
    els.cmdInput.value = '';
  }

  // ── Server message handling ────────────────────────────────
  function handleServerMessage(raw) {
    const lines = raw.split('\n').filter(l => l.trim());
    for (const line of lines) {
      parseLine(line.trim());
    }
  }

  function parseLine(line) {
    if (line.startsWith('OK WELCOME')) {
      printOutput('服务器', line, 'success');
    } else if (line.startsWith('OK LOGGED_IN')) {
      playerName = line.split(' ')[2];
      viewingPlayer = playerName;
      isOwnFarm = true;
      els.loginOverlay.classList.add('hidden');
      printOutput('服务器', `登录成功！欢迎, ${playerName}`, 'success');
      updateLabels();
      sendCommand('GET');
    } else if (line.startsWith('OK {')) {
      const json = line.substring(3);
      updateFarmFromJson(json);
      const cmd = getLastCommand();
      if (cmd.startsWith('VIEW ')) {
        viewingPlayer = cmd.split(' ')[1];
        isOwnFarm = viewingPlayer === playerName;
        printOutput('服务器', `正在查看 ${viewingPlayer} 的农场`, 'info');
      } else if (cmd === 'GET') {
        viewingPlayer = playerName;
        isOwnFarm = true;
      }
      updateLabels();
    } else if (line.startsWith('OK ')) {
      printOutput('服务器', line.substring(3), 'success');
      // PlayERS list etc
      if (!line.includes('{')) {
        // Simple OK response, refresh own farm
        sendCommand('GET');
      }
    } else if (line.startsWith('ERR ')) {
      printOutput('错误', line.substring(4), 'error');
      setStatus(line.substring(4));
    } else if (line.startsWith('UPDATE ')) {
      handleUpdate(line);
    } else {
      printOutput('服务器', line, 'info');
    }
  }

  let lastCmd = '';
  function getLastCommand() { return lastCmd; }

  function handleUpdate(line) {
    // UPDATE <player> {"coins":...,"board":[...]}
    const match = line.match(/UPDATE (\S+) (.+)/);
    if (!match) return;
    const [, player, json] = match;
    if (player === viewingPlayer || (isOwnFarm && player === playerName)) {
      updateFarmFromJson(json);
    }
    if (player === playerName) {
      myCoins = parseCoins(json);
      updateCoinsDisplay();
    }
    // Show brief notification
    if (player !== playerName) {
      setStatus(`${player} 的农场有更新`);
    }
  }

  // ── JSON parsing ───────────────────────────────────────────
  function parseCoins(json) {
    const m = json.match(/"coins":(\d+)/);
    return m ? parseInt(m[1]) : myCoins;
  }

  function updateFarmFromJson(json) {
    try {
      const data = JSON.parse(json);
      myCoins = data.coins;
      updateCoinsDisplay();
      if (Array.isArray(data.board)) {
        data.board.forEach((row, r) => {
          if (Array.isArray(row)) {
            row.forEach((cell, c) => {
              const idx = r * 4 + c;
              const cellEl = els.farmGrid.children[idx];
              if (cellEl) updateCell(cellEl, cell);
            });
          }
        });
      }
      // Update selected state
      Array.from(els.farmGrid.children).forEach(c => c.classList.remove('selected'));
    } catch (e) {
      console.warn('JSON parse error:', e);
    }
  }

  function updateCell(cellEl, state) {
    cellEl.classList.remove('empty', 'growing', 'ripe');
    const plotIdx = parseInt(cellEl.dataset.idx);
    if (state === 'EMPTY') {
      cellEl.classList.add('empty');
      cellEl.innerHTML = '<span class="cell-icon">🟫</span><span class="cell-text">空地</span>';
    } else if (state === 'GROWING') {
      cellEl.classList.add('growing');
      cellEl.innerHTML = '<span class="cell-icon">🌱</span><span class="cell-text">生长中</span>';
    } else if (state === 'RIPE') {
      cellEl.classList.add('ripe');
      cellEl.innerHTML = '<span class="cell-icon">🌻</span><span class="cell-text">成熟!</span>';
    }
    cellEl.dataset.state = state;
  }

  // ── Grid ───────────────────────────────────────────────────
  function createGrid() {
    els.farmGrid.innerHTML = '';
    for (let i = 0; i < 16; i++) {
      const cell = document.createElement('button');
      cell.className = 'farm-cell empty';
      cell.dataset.idx = i;
      cell.dataset.state = 'EMPTY';
      cell.innerHTML = '<span class="cell-icon">🟫</span><span class="cell-text">空地</span>';
      cell.addEventListener('click', () => selectPlot(i));
      els.farmGrid.appendChild(cell);
    }
  }

  function selectPlot(idx) {
    selectedPlot = idx;
    Array.from(els.farmGrid.children).forEach(c => c.classList.remove('selected'));
    els.farmGrid.children[idx].classList.add('selected');
    const r = Math.floor(idx / 4);
    const c = idx % 4;
    setStatus(`已选中地块 (${r},${c})`);
    // If viewing own farm and cell is ripe, suggest harvest
    const state = els.farmGrid.children[idx].dataset.state;
    if (isOwnFarm && state === 'EMPTY') {
      setStatus(`已选中空地 (${r},${c}) — 可种植`);
    } else if (isOwnFarm && state === 'RIPE') {
      setStatus(`已选成熟地块 (${r},${c}) — 可收获`);
    } else if (!isOwnFarm && state === 'RIPE') {
      setStatus(`已选 ${viewingPlayer} 的成熟作物 (${r},${c})`);
    }
  }

  // ── Command interface ──────────────────────────────────────
  function submitCommand() {
    const cmd = els.cmdInput.value.trim();
    if (!cmd) return;
    lastCmd = cmd;
    printOutput('>', cmd, 'command');
    sendCommand(cmd);
    // Handle GET locally to refresh
    if (cmd.toUpperCase() === 'GET') {
      viewingPlayer = playerName;
      isOwnFarm = true;
      updateLabels();
    } else if (cmd.toUpperCase().startsWith('GET ')) {
      viewingPlayer = playerName;
      isOwnFarm = true;
      updateLabels();
    }
  }

  function handleLogin() {
    const name = els.usernameInput.value.trim();
    if (!name) {
      els.loginError.textContent = '请输入用户名';
      return;
    }
    lastCmd = 'LOGIN ' + name;
    printOutput('>', `LOGIN ${name}`, 'command');
    sendCommand('LOGIN ' + name);
  }

  // ── UI helpers ─────────────────────────────────────────────
  function printOutput(prefix, text, type) {
    const line = document.createElement('div');
    line.className = `output-line ${type}`;
    const prefixSpan = document.createElement('span');
    prefixSpan.className = 'output-prefix';
    prefixSpan.textContent = prefix;
    const textSpan = document.createElement('span');
    textSpan.className = 'output-text';
    textSpan.textContent = text;
    line.appendChild(prefixSpan);
    line.appendChild(textSpan);
    els.terminalOutput.appendChild(line);
    els.terminal.scrollTop = els.terminal.scrollHeight;
  }

  function setStatus(text) {
    els.statusBar.textContent = text;
  }

  function setConnectionStatus(status, text) {
    els.connDot.className = `conn-dot ${status}`;
    els.connText.textContent = text;
  }

  function updateLabels() {
    els.playerLabel.textContent = playerName || '未登录';
    els.viewingLabel.textContent = viewingPlayer || '-';
  }

  function updateCoinsDisplay() {
    els.coinsDisplay.textContent = myCoins;
  }
})();
