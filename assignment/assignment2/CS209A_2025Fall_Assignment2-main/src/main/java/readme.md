# QQ Farm Concurrent Multiplayer Demo

## 1. Overview
A minimal concurrent multiplayer “farm” game:
- Each player owns a 4x4 grid of plots (states: `EMPTY`, `GROWING`, `RIPE`).
- Planting consumes coins; crops mature automatically after 10 seconds.
- Harvesting yields coins.
- Stealing lets a visiting player take up to 25% of a victim’s current ripe yield if the victim is “away” (viewing someone else’s farm).

Focus areas: server concurrency, atomic state updates, client networking responsiveness, visual feedback, and failure handling.

## 2. Architecture

### 2.1 Components
| Component | Responsibility |
|-----------|---------------|
| `Server` (`org.example.demo.server.Server`) | Manages player connections, per-player `Game` objects, command parsing, broadcast updates, stealing rules, statistics monitor. |
| `Game` (`org.example.demo.game.Game`) | Encapsulates a farm’s 4x4 board, crop growth scheduler, synchronized plant/harvest/steal operations. |
| `Controller` (JavaFX) | Client-side UI logic, asynchronous socket I/O, snapshot application, action button management, visual refresh. |
| `Application` | Launches login window then main UI; connects to server. |
| `LoginWindow` | Modal login dialog. |

### 2.2 Threading Model
| Thread / Pool | Purpose |
|---------------|---------|
| Server `cachedThreadPool` (`client-io`) | One runnable per client connection for command handling. |
| Server `statsExecutor` (`stats-monitor`) | Periodic statistics output every 60s. |
| Game `scheduler` (`crop-growth`) | Per-game background tasks for crop maturation (10s delay). |
| Client `listenThread` (`server-listener`) | Asynchronous receive of server push updates (non-blocking UI). |
| JavaFX Application Thread | Rendering, user input, UI updates (only via `Platform.runLater`). |
| Client `Timeline` refresh | 1-second tick to keep UI states responsive even during network lag. |

### 2.3 Synchronization & Atomicity
- `Game` methods `plant`, `harvest`, `stealOneRipe`, `addCoins` use `synchronized(this)` to serialize state transitions per farm.
- Steal operation: `Server.stealAtomic(thief, victim)` acquires locks in deterministic order (`IdentityHashCode`) to prevent deadlock, then calls `victim.stealOneRipe()` and credits thief.
- Broadcast callbacks moved outside locks (non-blocking), reducing critical section length.
- Session & cycle maps: `canStealThisCycle`, `sessionStealCounts` (per thief/victim) constrain repeated steals.

### 2.4 Data Structures
- `ConcurrentHashMap<String, Game> farms`: each player’s farm.
- `CopyOnWriteArrayList<PrintWriter> allClients`: active output channels for broadcast.
- `ConcurrentHashMap<String,String> currentView`: tracks which farm each player is currently viewing (determines “at home” vs “away”).
- Stealing control maps: nested `ConcurrentHashMap` structures enabling per-session restrictions.

### 2.5 Snapshot Consistency
A snapshot is JSON: `{"coins":<int>,"board":[["EMPTY","GROWING",...], ...]}`.  
Current implementation builds snapshot by repeated synchronized getters. (Recommended enhancement: enclose entire snapshot build in a single synchronized block to guarantee a single-timestamp view.)

## 3. Build & Run Instructions

### 3.1 Requirements
- JDK 17+ (tested on Java 17)
- Maven 3.x
- JavaFX runtime (if not bundled, ensure module path includes JavaFX libs)

### 3.2 Start Server
```bash
mvn compile
mvn exec:java -Dexec.mainClass=org.example.demo.server.Server
```
Server starts on port 5050 by default.

### 3.3 Start Client
In a new terminal (repeat for multiple clients):
```bash
mvn exec:java -Dexec.mainClass=org.example.demo.Application
```
Login window appears; enter a unique username (e.g., `alice`, `bob`, `carol`).

### 3.4 Multi-Client Demo
Launch 2–3 clients with distinct names. Use “Visit” to view a friend’s farm and “Back Home” to return.

### 3.5 Clean Shutdown
- Close client window → `Controller.shutdown()` stops timers, closes socket.
- Press Ctrl+C on server terminal → thread pools attempt graceful termination.

## 4. Network Protocol Description

### 4.1 Transport
- Plain TCP sockets (UTF-8 lines).
- Client sends newline-delimited commands.
- Server responds with either:
    - `OK <payload>` (payload may be snapshot JSON or informational text)
    - `ERR <message>`
    - `UPDATE <player> <snapshot_json>` (push broadcast)

### 4.2 Commands (Client → Server)
| Command | Format | Description | Error Cases |
|---------|--------|-------------|-------------|
| LOGIN | `LOGIN <name>` | Create/log in player; initializes farm if absent. | Name missing returns `ERR Usage: LOGIN <name>` |
| GET | `GET` | Fetch own farm snapshot; sets view back to self (home). | Not logged in |
| VIEW | `VIEW <other>` | Switch view to another player’s farm. | Player not found |
| PLANT | `PLANT <row> <col>` | Plant on own farm at empty plot; costs 5 coins; schedules maturation. | Not logged in, out of bounds, occupied, insufficient coins |
| HARVEST | `HARVEST <row> <col>` | Harvest RIPE plot; +12 coins. | Not logged in, not RIPE |
| STEAL | `STEAL <victim>` | Attempt steal (victim must be “away” + have ripe); up to 25% per session. | Victim at home, not viewing victim, session/cycle exhausted, no ripe |
| PLAYERS | `PLAYERS` | List all known player names. | None |

### 4.3 Push Broadcast
`UPDATE <player> <snapshot>` emitted on: plant, harvest, maturation, successful steal (both thief & victim).

### 4.4 Snapshot Format
```json
{"coins":40,"board":[["EMPTY","GROWING","RIPE","EMPTY"],["EMPTY","EMPTY","EMPTY","EMPTY"],["EMPTY","EMPTY","EMPTY","EMPTY"],["EMPTY","EMPTY","EMPTY","EMPTY"]]}
```

### 4.5 Concurrency Semantics
- Steal atomicity: only one thief removes a given ripe slot; victim’s yield decremented once; thief gains STEAL_REWARD (3 coins).
- 25% limit: `maxSteal = floor(currentRipe * 0.25)` computed when handling command (suggested improvement: compute inside atomic lock).
- Session termination triggers when thief leaves victim’s view or reaches max limit.

### 4.6 Recommended Future Protocol Extension
Add event metadata:
```
UPDATE <player> <snapshot> EVENT=<PLANT|HARVEST|RIPEN|STEAL>
```
Improves client visual feedback granularity.

## 5. Rubric Coverage (Summary)

| Rubric Category | Implementation Highlights |
|-----------------|---------------------------|
| Server (35) | Multi-client thread pool, per-player Game, growth scheduling, broadcast updates, atomic stealing. |
| Client (30) | JavaFX UI, asynchronous listener thread, dynamic view switching, command mapping, local caching. |
| GUI (10) | 4x4 grid, distinct colors/tooltips, status & coin labels, animated Visit button. |
| Concurrency (15) | synchronized Game ops, double-lock ordering, separate growth scheduler, non-blocking callbacks. |
| Error Handling (10) | Input validation → `ERR`; client disconnect detection; disabled actions on failure; safe JSON parsing with fallback. |

## 6. Demonstration & Self-Check (Checklist for Section 5)

### 6.1 Pre-Demo Preparation
- Start server (`mvn exec:java ...Server`).
- Launch at least 3 clients (`alice`, `bob`, `carol`).
- Observe server logs showing thread names & actions.

### 6.2 UI Walkthrough (Single Client)
1. Login and identify UI regions: grid, coins label, status label, action buttons.
2. Plant on empty plot → status “Planting…”, cell changes to green “Growing”.
3. Wait 10s (or multiple plants) → cell becomes gold “RIPE!”.
4. Harvest → coins increase by 12; cell returns “Empty”; status updated.

### 6.3 Single-Client Robustness
- Attempt Plant on occupied cell → `ERR Plot occupied`.
- Kill Wi-Fi / simulate socket close → client shows “Disconnected…” and disables buttons.
- Relaunch client → login and verify farm state persisted (other crops still maturing/ripe).

### 6.4 Multi-Client Interaction
- Client A visits Client B.
- B plants crop; A sees updates via `UPDATE`.
- A steals when B is “away”: A coins +3, B loses one ripe; both UIs refresh automatically.

### 6.5 Concurrency Stress Test (Multi-Thief)
- Prepare ripe crop(s) on Player C.
- Simultaneously issue `STEAL C` from A and B (manual countdown or automation script).
- Show logs: only one ripe consumed per attempt; each steal yields at most per-crop reward; no duplicates.
- Repeat quickly to prove consistency.

### 6.6 Threading & Responsiveness
- Interact with UI while steals & ripening occur; no freeze.
- Show logs: `client-io`, `crop-growth`, `stats-monitor`, `server-listener`.
- Explain avoidance of deadlocks: lock ordering by identity hash code; callbacks outside synchronized blocks; UI updates via `Platform.runLater`.

### 6.7 Failure Recovery Scenarios
- Client crash: kill process → server logs disconnect; no resource leak; reopen → consistent state.
- Server crash: client detects IO error, disables buttons; restart server + reopen client → resynchronizes.
- Unsent actions: no queue; actions only apply if server acknowledges; failed sends are lost with status message.

### 6.8 Wrap-Up & Questions
Prepare to explain:
- Snapshot structure & timing.
- Atomic steal rationale.
- Growth scheduler isolation.
- Potential improvements (atomic snapshot build, event tags, reconnect logic).

## 7. Concurrency & Synchronization Guarantees
- Per-game exclusivity via `synchronized`.
- Cross-farm operation (steal) uses deterministic double-lock to prevent inversion deadlocks.
- Callback-outside-lock pattern reduces contention and prevents user-handling code from holding farm lock.
- Growth events scheduled independently; no interference with plant/harvest critical sections.

## 8. Error Handling Strategy
| Scenario | Handling |
|----------|----------|
| Invalid command args | Throws IllegalArgumentException → `ERR <message>` |
| Plot occupied / not ripe | Business exceptions → `ERR` |
| Network IO (client) | Caught, UI status updated, actions disabled |
| Snapshot parse errors | Try/catch swallow, keep old state |
| Disconnect cleanup | Removes writer from `allClients`, adjusts stealing session flags |

## 9. Limitations & Future Work
- Snapshot atomicity: build entire JSON under one synchronized block (recommended).
- Steal 25% calculation: move ripe count & maxSteal check inside `stealAtomic` for strict correctness.
- Event metadata for broadcasts to refine UI messages (PLANT/HARVEST/RIPEN/STEAL).
- Optional auto-reconnect mechanism with exponential backoff.
- More granular UI indicators (icons, animation on maturation).
- Central logging/tracing system instead of `System.out`.
- Security/authentication (currently trust-based usernames).
- Persistence layer (farms reset when server restarts).

## 10. Example Log Snippets

### Plant & Ripen
```
client-io PLANT (1,2)
crop-growth RIPEN (1,2)
```

### Steal Attempt
```
[STEAL ATTEMPT] Player: alice | Victim: bob | currentRipe: 4 | maxSteal: 1
[STEAL] alice successfully stole from bob | session count: 1
```

### Disconnect
```
[ERROR] Connection error: Connection reset
[DISCONNECT] Player: carol
```

## 11. Quick Reference Command Cheatsheet
```
LOGIN alice
PLANT 0 1
HARVEST 0 1
VIEW bob
STEAL bob
GET
PLAYERS
```

## 12. FAQ (Anticipated TA Questions)
| Question | Short Answer |
|----------|--------------|
| Why double-lock ordering? | Prevent deadlock when thief & victim reversed in concurrent steals. |
| Why move callbacks outside synchronized? | Avoid long lock hold; prevent potential re-entrance or blocking I/O while holding farm state lock. |
| How ensure UI thread safety? | All network events funnel through `listenThread` + `Platform.runLater`. |
| What happens if crop ripens while stealing? | Steal uses victim lock; ripen task acquires same lock—serializing modifications ensures consistency. |
| Can two steals take same ripe? | `victim.stealOneRipe()` under lock decrements exactly one slot; second steal sees updated board. |
| If server crashes mid-action? | No partial commit—actions only change shared state if method completes; client treats failure as disconnect and requires re-login. |

---

**End of README**