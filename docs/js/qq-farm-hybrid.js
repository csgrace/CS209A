/**
 * QQ Farm Hybrid Client — Terminal + JavaFX-Style GUI.
 *
 * Left: terminal input (simulates CLI)
 * Right: JavaFX-style window showing farm state
 *
 * Both views are driven by commands typed in the terminal.
 */
(() => {
  'use strict';

  // ── State ──
  let server = null;
  let currentUser = null;
  let currentFarm = null;   // whose farm we're viewing
  let plantedCrops = {};    // { "r,c": true } — tracks planted positions (instant-ripe on plant)

  // ── DOM refs ──
  const termOutput = document.getElementById('terminal-output');
  const termInput = document.getElementById('term-input');
  const termSend = document.getElementById('term-send');
  const javafxWindow = document.getElementById('javafx-window');
  const jfxTitleText = document.getElementById('jfx-title-text');
  const jfxPlayerName = document.getElementById('jfx-player-name');
  const jfxCoins = document.getElementById('jfx-coins');
  const jfxStatus = document.getElementById('jfx-status');
  const jfxViewing = document.getElementById('jfx-viewing');
  const jfxFarmGrid = document.getElementById('jfx-farm-grid');
  const jfxGridLabel = document.getElementById('jfx-grid-label');
  const jfxStatusMsg = document.getElementById('jfx-statusbar-msg');
  const btnPlant = document.getElementById('jfx-btn-plant');
  const btnHarvest = document.getElementById('jfx-btn-harvest');
  const btnVisit = document.getElementById('jfx-btn-visit');
  const btnSteal = document.getElementById('jfx-btn-steal');
  const btnHome = document.getElementById('jfx-btn-home');

  // ── Helpers ──
  function termPrint(text, cls = '') {
    const div = document.createElement('div');
    div.className = 'term-line ' + cls;
    div.innerHTML = text;
    termOutput.appendChild(div);
    termOutput.scrollTop = termOutput.scrollHeight;
  }

  function termLog(msg, type = 'info') {
    const cls = type === 'ok' ? 'ok' : type === 'err' ? 'err' : type === 'thread' ? 'thread' : type === 'warn' ? 'warn' : 'info';
    termPrint(msg, cls);
  }

  function updateJavaFX() {
    if (!currentUser || !server) {
      jfxTitleText.textContent = 'QQ Farm — Not Logged In';
      jfxPlayerName.textContent = '—';
      jfxCoins.textContent = '0';
      jfxStatus.textContent = 'Waiting for login...';
      jfxViewing.textContent = '—';
      jfxGridLabel.textContent = 'Farm Grid';
      renderEmptyGrid();
      disableAllButtons();
      return;
    }

    const user = server.users.get(currentUser);
    jfxTitleText.textContent = `🌻 QQ Farm - ${currentUser}`;
    jfxPlayerName.textContent = currentUser;
    jfxCoins.textContent = user ? user.coins : 0;

    if (currentFarm === currentUser) {
      jfxStatus.textContent = '🌱 Own farm';
      jfxViewing.textContent = currentUser;
      jfxGridLabel.textContent = `${currentUser}'s Farm`;
      renderFarmGrid(currentUser, true);
      setButtons({ plant: true, harvest: true, visit: true, steal: false, home: false });
    } else {
      jfxStatus.textContent = `👀 Visiting ${currentFarm}`;
      jfxViewing.textContent = currentFarm;
      jfxGridLabel.textContent = `${currentFarm}'s Farm`;
      renderFarmGrid(currentFarm, false);
      setButtons({ plant: false, harvest: false, visit: true, steal: true, home: true });
    }
  }

  function renderEmptyGrid() {
    jfxFarmGrid.innerHTML = '';
    for (let r = 0; r < 4; r++) {
      for (let c = 0; c < 4; c++) {
        const cell = document.createElement('div');
        cell.className = 'jfx-cell empty';
        cell.textContent = '⬛';
        jfxFarmGrid.appendChild(cell);
      }
    }
  }

  function renderFarmGrid(ownerName, isOwn) {
    const farm = server.getFarm(ownerName);
    jfxFarmGrid.innerHTML = '';
    for (let r = 0; r < 4; r++) {
      for (let c = 0; c < 4; c++) {
        const cell = document.createElement('div');
        const plot = farm.board[r * 4 + c];
        let cls = 'jfx-cell';
        let content = '⬛';
        if (plot === 'GROWING') { cls += ' sprout'; content = '🌱'; }
        else if (plot === 'RIPE') { cls += ' ripe'; content = '🌻'; }
        else { cls += ' empty'; }
        cell.className = cls;
        cell.textContent = content;
        cell.title = `Row ${r}, Col ${c} — ${plot === 'RIPE' ? 'RIPE' : plot === 'GROWING' ? 'Growing' : 'Empty'}`;
        jfxFarmGrid.appendChild(cell);
      }
    }
  }

  function setButtons({ plant, harvest, visit, steal, home }) {
    btnPlant.disabled = !plant;
    btnHarvest.disabled = !harvest;
    btnVisit.disabled = !visit;
    btnSteal.disabled = !steal;
    btnHome.disabled = !home;
  }

  function disableAllButtons() {
    btnPlant.disabled = true;
    btnHarvest.disabled = true;
    btnVisit.disabled = true;
    btnSteal.disabled = true;
    btnHome.disabled = true;
  }

  function setStatusMsg(msg) {
    jfxStatusMsg.textContent = msg;
  }

  // ── Commands ──
  function cmdHelp() {
    termPrint('<strong>Commands:</strong>', 'info');
    termPrint('<code>java -jar Server.jar</code> — Start server', 'info');
    termPrint('<code>login &lt;name&gt;</code> — Login as player', 'info');
    termPrint('<code>players</code> — List players', 'info');
    termPrint('<code>plant &lt;r&gt; &lt;c&gt;</code> — Plant crop (-5 coins)', 'info');
    termPrint('<code>harvest &lt;r&gt; &lt;c&gt;</code> — Harvest ripe crop (+12)', 'info');
    termPrint('<code>visit &lt;name&gt;</code> — Visit friend\'s farm', 'info');
    termPrint('<code>steal &lt;name&gt;</code> — Steal from friend (+3)', 'info');
    termPrint('<code>look</code> — View own farm', 'info');
termPrint('<code>concurrent</code> — CountDownLatch concurrent test', 'info');
termPrint('<code>demo</code> — Auto-run a successful steal scenario', 'info');
termPrint('<code>help</code> — Show this help', 'info');
  }

  function cmdServer() {
    if (server) {
      termLog('[Server] Already running on port 5050', 'warn');
      return;
    }
    server = new FarmServer();
    server.start();
    currentFarm = null;
    setStatusMsg('Server running on port 5050');
    updateJavaFX();
    // Auto-populate NPCs
    setTimeout(() => {
      if (server) {
        server.login('bob');
        server.login('charlie');
        server.login('diana');
        termLog('[Server] NPC players connected: bob, charlie, diana', 'info');
        // NPC auto-plant
        ['bob', 'charlie', 'diana'].forEach(name => {
          const positions = [[0,0],[0,1],[1,1],[2,2],[3,0],[3,3]];
          positions.forEach(([r,c]) => {
            server.plant(name, r, c);
          });
        });
        termLog('[Server] NPCs have planted crops', 'info');
        termLog('[Server] Type <code>login &lt;name&gt;</code> to join', 'ok');
        updateJavaFX();
      }
    }, 600);
  }

  function cmdLogin(parts) {
    if (!server) {
      termLog('Error: Server not running. Type <code>java -jar Server.jar</code> first.', 'err');
      return;
    }
    if (parts.length < 2) {
      termLog('Usage: login <name>', 'err');
      return;
    }
    const name = parts[1].toLowerCase();
    server.login(name);
    currentUser = name;
    currentFarm = name;
    const user = server.users.get(name);
    termLog(`[Server] Welcome, ${name}! You have ${user.coins} coins.`, 'ok');
    setStatusMsg(`Logged in as ${name}. ${user.coins} coins.`);
    updateJavaFX();
    setTimeout(() => {
      termLog(`[Tip] Type <code>plant 0 0</code> to plant, <code>visit bob</code> to visit friends.`, 'info');
    }, 200);
  }

  function cmdPlayers() {
    if (!server) { termLog('Error: Server not running.', 'err'); return; }
    const names = [...server.users.keys()];
    termLog(`Connected players: ${names.join(', ')}`, 'info');
    names.forEach(n => {
      const u = server.users.get(n);
      const ripe = server.countRipe(n);
      termLog(`  ${n}: ${u.coins} coins, ${ripe} ripe crops`, 'info');
    });
  }

  function cmdPlant(parts) {
    if (!currentUser) { termLog('Please login first.', 'err'); return; }
    if (parts.length < 3) { termLog('Usage: plant <row> <col>', 'err'); return; }
    const r = parseInt(parts[1]), c = parseInt(parts[2]);
    if (isNaN(r) || isNaN(c) || r < 0 || r > 3 || c < 0 || c > 3) {
      termLog('Invalid position. Use 0-3 for row and col.', 'err');
      return;
    }
    if (currentFarm !== currentUser) {
      termLog('Can only plant on your own farm. Type <code>look</code> first.', 'warn');
      return;
    }
    const success = server.plant(currentUser, r, c);
    if (success) {
      const user = server.users.get(currentUser);
      termLog(`[${currentUser}] Planted at (${r},${c}). -5 coins. Now have ${user.coins} coins.`, 'ok');
      termLog(`[${currentUser}] Crop grew instantly to 🌻 RIPE!`, 'info');
      setStatusMsg(`Planted at (${r},${c}). Crop is now ripe!`);
    } else {
      termLog(`[${currentUser}] Cannot plant at (${r},${c}) — not enough coins or already occupied.`, 'err');
      setStatusMsg('Plant failed.');
    }
    updateJavaFX();
  }

  function cmdHarvest(parts) {
    if (!currentUser) { termLog('Please login first.', 'err'); return; }
    if (parts.length < 3) { termLog('Usage: harvest <row> <col>', 'err'); return; }
    const r = parseInt(parts[1]), c = parseInt(parts[2]);
    if (isNaN(r) || isNaN(c) || r < 0 || r > 3 || c < 0 || c > 3) {
      termLog('Invalid position. Use 0-3 for row and col.', 'err'); return;
    }
    if (currentFarm !== currentUser) {
      termLog('Can only harvest on your own farm. Type <code>look</code> first.', 'warn'); return;
    }
    const success = server.harvest(currentUser, r, c);
    if (success) {
      const user = server.users.get(currentUser);
      termLog(`[${currentUser}] Harvested at (${r},${c}). +12 coins. Now have ${user.coins} coins.`, 'ok');
      setStatusMsg(`Harvested at (${r},${c}). +12 coins.`);
    } else {
      termLog(`[${currentUser}] Nothing to harvest at (${r},${c}).`, 'warn');
      setStatusMsg('Nothing to harvest.');
    }
    updateJavaFX();
  }

  function cmdVisit(parts) {
    if (!currentUser) { termLog('Please login first.', 'err'); return; }
    if (parts.length < 2) { termLog('Usage: visit <name>', 'err'); return; }
    const name = parts[1].toLowerCase();
    if (!server.users.has(name)) { termLog(`Player "${name}" not found.`, 'err'); return; }
    if (name === currentUser) {
      cmdLook(); return;
    }
    server.visit(currentUser, name);
    currentFarm = name;
    server.newSession(currentUser); // new steal session
    const ripe = server.countRipe(name);
    termLog(`[${currentUser}] Visiting ${name}'s farm... (new steal session)`, 'ok');
    termLog(`[${currentUser}] ${name} has ${ripe} ripe crops available.`, 'info');
    setStatusMsg(`Visiting ${name}'s farm.`);
    updateJavaFX();
    setTimeout(() => {
      termLog(`[Tip] Type <code>steal ${name}</code> to steal, or <code>visit &lt;other&gt;</code> to leave.`, 'info');
    }, 200);
  }

  async function cmdSteal(parts) {
    if (!currentUser) { termLog('Please login first.', 'err'); return; }
    if (parts.length < 2) { termLog('Usage: steal <name>', 'err'); return; }
    const victim = parts[1].toLowerCase();
    if (!server.users.has(victim)) { termLog(`Player "${victim}" not found.`, 'err'); return; }
    if (victim === currentUser) { termLog('Cannot steal from yourself!', 'warn'); return; }
    if (currentFarm !== victim) {
      termLog(`Must be visiting ${victim} first. Type <code>visit ${victim}</code>.`, 'warn'); return;
    }

    // Show mutex lock in terminal
    termLog(`[Thread-${currentUser}] Entering synchronized(${victim})...`, 'thread');
    
    const result = await server.steal(currentUser, victim);
    if (result.success) {
      const user = server.users.get(currentUser);
      termLog(`[Thread-${currentUser}] synchronized(${victim}) ACQUIRED`, 'thread');
      termLog(`[${currentUser}] stole from ${victim}! +3 coins. Now have ${user.coins} coins.`, 'ok');
      termLog(`[Thread-${currentUser}] Exiting synchronized(${victim})`, 'thread');
      termLog(`[${victim}] Lost 1 ripe crop (${result.victimRipeLeft} remaining this session)`, 'info');
      setStatusMsg(`Stole from ${victim}! +3 coins.`);
    } else {
      termLog(`[Thread-${currentUser}] synchronized(${victim}) — DENIED: ${result.reason}`, 'err');
      termLog(`[${currentUser}] Steal failed: ${result.reason}`, 'err');
      setStatusMsg(`Steal failed: ${result.reason}`);
    }
    updateJavaFX();
  }

  function cmdLook() {
    if (!currentUser) { termLog('Please login first.', 'err'); return; }
    currentFarm = currentUser;
    updateJavaFX();
    const farm = server.getFarm(currentUser);
    let ripeCount = 0;
    termLog(`[${currentUser}] Looking at own farm:`, 'info');
    for (let r = 0; r < 4; r++) {
      let row = '  ';
      for (let c = 0; c < 4; c++) {
        const v = farm.board[r * 4 + c];
        row += v === 'RIPE' ? '🌻 ' : v === 'GROWING' ? '🌱 ' : '⬛ ';
        if (v === 'RIPE') ripeCount++;
      }
      termLog(row, 'info');
    }
    termLog(`  Total ripe: ${ripeCount}`, 'info');
    setStatusMsg(`Viewing own farm. ${ripeCount} ripe crops.`);
  }

  async function cmdConcurrent() {
    if (!server) { termLog('Start server first.', 'err'); return; }
    if (!currentUser) { termLog('Login first.', 'err'); return; }
    // Ensure we have a victim with crops
    const victim = currentFarm && currentFarm !== currentUser ? currentFarm : 'bob';
    if (!server.users.has(victim)) {
      server.login(victim);
    }
    // Plant plenty of crops for victim
    const farm = server.getFarm(victim);
    for (let i = 0; i < 10; i++) {
      if (farm.board[i] === 'EMPTY') farm.board[i] = 'RIPE';
    }

    // Create two temporary thieves
    const thiefA = currentUser + '_A';
    const thiefB = currentUser + '_B';
    server.login(thiefA);
    server.login(thiefB);
    server.currentView.set(thiefA, victim);
    server.currentView.set(thiefB, victim);

    termPrint('<strong>⚡ CONCURRENT STEAL TEST (CountDownLatch simulation)</strong>', 'warn');
    termLog('Two threads try to steal from same victim simultaneously...', 'warn');
    termLog(`[Main] Preparing CountDownLatch(1)...`, 'thread');
    termLog(`[Thread-${thiefA}] Ready to steal from ${victim}`, 'thread');
    termLog(`[Thread-${thiefB}] Ready to steal from ${victim}`, 'thread');

    await new Promise(r => setTimeout(r, 300));
    termLog(`[Main] CountDownLatch.countDown() — GO!`, 'thread');

    // Run both steals "simultaneously" via Promise.all
    const [resultA, resultB] = await Promise.all([
      server.steal(thiefA, victim),
      server.steal(thiefB, victim)
    ]);

    // Show results
    await new Promise(r => setTimeout(r, 100));
    termLog(`[Result] ${thiefA}: ${resultA.success ? '✓ SUCCESS +3 coins' : '✗ FAILED - ' + resultA.reason}`, resultA.success ? 'ok' : 'err');
    termLog(`[Result] ${thiefB}: ${resultB.success ? '✓ SUCCESS +3 coins' : '✗ FAILED - ' + resultB.reason}`, resultB.success ? 'ok' : 'err');

    if (resultA.success && resultB.success) {
      termLog(`[Analysis] Both succeeded! Mutex serialized: one went first, then the other.`, 'info');
    } else if (resultA.success || resultB.success) {
      termLog(`[Analysis] Only one succeeded — mutex ensured atomic access.`, 'info');
    }

    termPrint('<strong>✅ Concurrent test complete.</strong>', 'ok');
    setStatusMsg('Concurrent test complete.');
    updateJavaFX();
  }

  // Demo: a successful steal scenario
  async function cmdDemo() {
    if (!server) { termLog('Start server first.', 'err'); return; }
    if (!currentUser) { termLog('Login first.', 'err'); return; }

    termPrint('<strong>🎬 STEAL DEMO — A successful steal scenario</strong>', 'info');

    // 1. Check victim
    const victim = 'bob';
    if (!server.users.has(victim)) {
      server.login(victim);
    }
    // Make sure bob has crops
    const vfarm = server.getFarm(victim);
    vfarm.board[0] = 'RIPE';
    vfarm.board[1] = 'RIPE';
    vfarm.board[2] = 'RIPE';

    termLog(`[Step 1] Ensure ${victim} has ripe crops → ${vfarm.getRipeCount()} ripe`, 'info');
    await new Promise(r => setTimeout(r, 400));

    // 2. Visit
    termLog(`[Step 2] visit ${victim} — enter victim's farm`, 'info');
    server.visit(currentUser, victim);
    currentFarm = victim;
    server.newSession(currentUser);
    await new Promise(r => setTimeout(r, 400));

    // 3. Steal
    termLog(`[Step 3] steal ${victim} — enter synchronized(victim)`, 'info');
    const result = await server.steal(currentUser, victim);
    if (result.success) {
      const user = server.users.get(currentUser);
      termLog(`[Result] ✓ Stole from ${victim}! Session: ${result.sessionCount}/${result.maxSteal}, Coins: ${user.coins}`, 'ok');
      termLog(`[Analysis] Mutex acquired → checked limit → stole crop → released lock`, 'info');
    } else {
      termLog(`[Result] ✗ ${result.reason}`, 'err');
    }

    updateJavaFX();
  }

  function cmdUnknown(input) {
    termLog(`Unknown command: "${input}". Type <code>help</code> for list.`, 'err');
  }

  // ── Command Router ──
  function handleCommand() {
    const raw = termInput.value.trim();
    if (!raw) return;
    termInput.value = '';
    // Echo
    termPrint(`<span class="term-prompt">➜</span> ${escapeHtml(raw)}`, 'echo');

    const parts = raw.split(/\s+/);
    const cmd = parts[0].toLowerCase();

    switch (cmd) {
      case 'help': cmdHelp(); break;
      case 'java': cmdServer(); break;
      case 'server': cmdServer(); break;
      case 'login': cmdLogin(parts); break;
      case 'players': cmdPlayers(); break;
      case 'plant': cmdPlant(parts); break;
      case 'harvest': cmdHarvest(parts); break;
      case 'visit': cmdVisit(parts); break;
      case 'steal': cmdSteal(parts); break;
      case 'look': cmdLook(); break;
      case 'concurrent': cmdConcurrent(); break;
      case 'demo': cmdDemo(); break;
      case 'cls': case 'clear':
        termOutput.innerHTML = ''; break;
      default: cmdUnknown(raw);
    }
  }

  function escapeHtml(str) {
    const d = document.createElement('div');
    d.textContent = str;
    return d.innerHTML;
  }

  // ── Event Listeners ──
  termInput.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') handleCommand();
  });
  termSend.addEventListener('click', handleCommand);

  // Quick buttons
  document.querySelectorAll('.qk-btn').forEach(btn => {
    btn.addEventListener('click', () => {
      termInput.value = btn.dataset.cmd;
      handleCommand();
    });
  });

  // JavaFX buttons drive terminal commands
  btnPlant.addEventListener('click', () => {
    if (!currentUser || currentFarm !== currentUser) return;
    const farm = server.getFarm(currentUser);
    for (let r = 0; r < 4; r++) {
      for (let c = 0; c < 4; c++) {
        if (farm.board[r * 4 + c] === 'EMPTY') {
          termInput.value = `plant ${r} ${c}`;
          handleCommand();
          return;
        }
      }
    }
    termLog('No empty plots!', 'warn');
  });

  btnHarvest.addEventListener('click', () => {
    if (!currentUser || currentFarm !== currentUser) return;
    const farm = server.getFarm(currentUser);
    for (let r = 0; r < 4; r++) {
      for (let c = 0; c < 4; c++) {
        if (farm.board[r * 4 + c] === 'RIPE') {
          termInput.value = `harvest ${r} ${c}`;
          handleCommand();
          return;
        }
      }
    }
    termLog('Nothing ripe to harvest.', 'warn');
  });

  btnVisit.addEventListener('click', () => {
    if (!server) return;
    const others = [...server.users.keys()].filter(n => n !== currentUser);
    if (others.length === 0) { termLog('No other players.', 'warn'); return; }
    let next = others[Math.floor(Math.random() * others.length)];
    termInput.value = `visit ${next}`;
    handleCommand();
  });

  btnSteal.addEventListener('click', () => {
    if (!currentUser || !currentFarm || currentFarm === currentUser) return;
    termInput.value = `steal ${currentFarm}`;
    handleCommand();
  });

  btnHome.addEventListener('click', () => {
    termInput.value = 'look';
    handleCommand();
  });

  // ── Walkthrough ──
  const wtSteps = document.querySelectorAll('.wt-step');
  let wtAutoRunning = false;

  wtSteps.forEach(step => {
    const runBtn = step.querySelector('.wt-run');
    if (!runBtn) return;
    
    // Run button click
    runBtn.addEventListener('click', (e) => {
      e.stopPropagation();
      if (wtAutoRunning) return;
      const cmd = runBtn.dataset.cmd;
      termInput.value = cmd;
      handleCommand();
      // Mark previous steps as done
      markStepDone(step);
    });

    // Step card click - highlight
    step.addEventListener('click', () => {
      if (wtAutoRunning) return;
      wtSteps.forEach(s => s.classList.remove('active'));
      step.classList.add('active');
    });
  });

  function markStepDone(currentStep) {
    wtSteps.forEach(s => {
      if (s === currentStep || Array.from(wtSteps).indexOf(s) < Array.from(wtSteps).indexOf(currentStep)) {
        s.classList.add('done');
        s.classList.remove('active');
      }
    });
  }

  // Auto-run all steps
  const wtAutorunBtn = document.getElementById('wt-autorun');
  if (wtAutorunBtn) {
    wtAutorunBtn.addEventListener('click', async () => {
      if (wtAutoRunning) {
        wtAutoRunning = false;
        wtAutorunBtn.textContent = '▶▶ Auto-Run All Steps';
        wtAutorunBtn.classList.remove('running');
        return;
      }

      wtAutoRunning = true;
      wtAutorunBtn.textContent = '⏹ Stop';
      wtAutorunBtn.classList.add('running');

      const allSteps = document.querySelectorAll('.wt-step');
      const commands = [];
      allSteps.forEach(s => {
        const btn = s.querySelector('.wt-run');
        if (btn) commands.push(btn.dataset.cmd);
      });

      for (let i = 0; i < commands.length; i++) {
        if (!wtAutoRunning) break;
        const step = allSteps[i];
        // Highlight current step
        wtSteps.forEach(s => s.classList.remove('active'));
        step.classList.add('active');
        
        // Run command
        termInput.value = commands[i];
        handleCommand();
        
        // Wait between steps
        await new Promise(r => setTimeout(r, 1200));
        
        // Mark done
        step.classList.add('done');
        step.classList.remove('active');
      }

      wtAutoRunning = false;
      wtAutorunBtn.textContent = '▶▶ Auto-Run All Steps';
      wtAutorunBtn.classList.remove('running');
    });
  }

  // ── Initialize GUI ──
  renderEmptyGrid();
  disableAllButtons();
})();
