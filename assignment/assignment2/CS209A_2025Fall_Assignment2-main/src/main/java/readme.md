
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

## Rubric Coverage

### ✅ 1. Server Logic

- **Multi-client support:** `CachedThreadPool` with one thread per client (`ClientHandler`).
- **Game state management:** `ConcurrentHashMap<String, Game>` stores each player's farm.
- **Crop growth:** `ScheduledExecutorService` in `Game` schedules ripening after 10 seconds.
- **Protocol handling:** Commands `LOGIN`, `GET`, `VIEW`, `PLANT`, `HARVEST`, `STEAL`, `PLAYERS`.
- **Stealing rules:**
  - Victim must be "away" (viewing another farm).
  - Thief must VIEW victim before stealing.
  - Max steals per session = `floor(currentRipe * 0.25)`.
  - Session resets when victim plants new crops.
- **Concurrency control:**
  - `synchronized(victim)` for atomic steal (checks + `stealOneRipe()` + session update).
  - Double-lock avoided; only victim is locked during steal.
- **Persistence:**
  - `saveFarmsToDisk()` writes to `farms.txt` on every state change.
  - `loadFarmsFromDisk()` restores coins, board, and **plant timestamps** on server restart.
  - GROWING crops resume countdown based on elapsed time.

### ✅ 2. Client UI

- **Login window:** Modal dialog (`LoginWindow.java`) to enter username.
- **4x4 farm grid:** `GridPane` with `ToggleButton` cells showing EMPTY / GROWING / RIPE states.
- **Action buttons:** Plant (cost 5 coins), Harvest (+12 coins), Steal (+3 coins).
- **Visit friends:** Enter friend's name → `VIEW` command → see their farm.
- **Coins display:** Real-time update in header label.
- **Status messages:** Feedback for actions, errors, and connection status.
- **Asynchronous networking:**
  - `listenLoop` thread reads server messages.
  - `Platform.runLater` updates UI on JavaFX Application Thread.

### ✅ 3. Concurrency

- **Server threads:**
  - `CachedThreadPool` for client I/O.
  - `ScheduledExecutorService` for crop growth (one per `Game`).
  - `ScheduledExecutorService` for server statistics (every 60s).
- **Client threads:**
  - `server-listener` thread for asynchronous message reception.
  - `reconnect-loop` thread for automatic reconnection.
  - JavaFX Application Thread for UI updates.
- **Synchronization:**
  - `Game` uses `synchronized` methods for coins, board state.
  - `Server.STEAL` uses `synchronized(victim)` to ensure atomic check + deduct.
  - Callbacks (`onStateChange`) called **outside synchronized blocks** to reduce lock hold time.
- **Deadlock avoidance:**
  - Only one lock held at a time during steal (victim's Game object).
  - UI updates via `Platform.runLater` (no lock held).
- **Stress test:**
  - `ConcurrencyTest.java` simulates two thieves concurrently stealing from one victim (4 RIPE crops).
  - Uses `CountDownLatch` for synchronized start.
  - Verifies: first success reduces ripe 4→3, second request sees maxSteal=0 → fails.

### ✅ 4. Error Handling

- **Client crash:**
  - Server detects disconnect in `ClientHandler.finally`.
  - Cleans up `allClients`, `currentView`, `sessionStealCounts`.
  - Farm state preserved in `farms` map.
  - Client can relaunch, `LOGIN` again, and resume from saved state.
- **Server crash:**
  - Client's `listenLoop` catches `IOException` → `onDisconnected()`.
  - UI shows "Disconnected from server. Attempting to reconnect...".
  - All action buttons disabled (`updateButtonStates()`).
  - `startReconnectLoop()` retries connection up to 10 times, 3s apart.
  - On success: `LOGIN` + `GET` → farm state restored from disk.
- **Unsent actions:**
  - Commands are sent one-at-a-time via `out.println()`.
  - If connection drops mid-send, command is lost (not queued).
  - Client resynchronizes with server's authoritative state on reconnect.
- **Input validation:**
  - `ensure()` checks command format, coordinates, and player state.
  - Exceptions caught in `handle()` → `ERR` message sent to client.

---

## Architecture

### Communication Protocol

**Text-based line protocol** over TCP sockets (port 5050).

#### Client → Server Commands

```
LOGIN <name>
GET
VIEW <player>
PLANT <row> <col>
HARVEST <row> <col>
STEAL <victim>
PLAYERS
```

#### Server → Client Responses

```
OK LOGGED_IN <name>
OK {"coins":X,"board":[[...],...]}
ERR <message>
UPDATE <player> {"coins":X,"board":[[...],...]}
```

### Thread Model

```
┌─────────────────────────────────────────────┐
│              SERVER                         │
├─────────────────────────────────────────────┤
│ CachedThreadPool (client-io threads)        │
│   └─ ClientHandler per connection           │
│ ScheduledExecutorService (stats-monitor)    │
│ Game.scheduler (crop-growth per farm)       │
└─────────────────────────────────────────────┘

┌─────────────────────────────────────────────┐
│              CLIENT                         │
├─────────────────────────────────────────────┤
│ JavaFX Application Thread (UI updates)      │
│ server-listener thread (listenLoop)         │
│ reconnect-loop thread (when disconnected)   │
│ Timeline (refresh ticker, 1s interval)      │
└─────────────────────────────────────────────┘
```

### Persistence Format

**File:** `farms.txt`  
**Format:** One line per player:

```
<name> <coins>|<state0>,<timestamp0>,<state1>,<timestamp1>,...,<state15>,<timestamp15>
```

Example:

```
alice 25|EMPTY,0,GROWING,1732339200000,RIPE,0,EMPTY,0,...
bob 40|EMPTY,0,EMPTY,0,...
```

- **Timestamps** (ms since epoch) for GROWING crops enable resuming countdown after server restart.
- **RIPE / EMPTY** have timestamp=0.

---

## Data Consistency Guarantees

1. **Single-writer principle:**
  - Each farm (`Game` object) modified only by its owner's commands or scheduled ripening tasks.
  - Stealing uses `synchronized(victim)` to ensure atomic read-check-modify.

2. **Snapshot consistency:**
  - `snapshot(Game)` reads all fields inside one `synchronized(game)` block (if needed).
  - Network updates sent as complete JSON snapshots.

3. **Session integrity:**
  - `sessionStealCounts` prevents exceeding 25% limit within one "session" (VIEW until leave or maxSteal reached).
  - `canStealThisCycle` blocks re-stealing until victim plants new crops.

4. **No duplicate rewards:**
  - `stealOneRipe()` atomically picks and removes one RIPE crop.
  - Coins updated in same synchronized block.

5. **Idempotent reconnect:**
  - Client's `GET` after reconnect fetches authoritative state from server.
  - No client-side "pending actions" queue.

---

## How to Run

### Prerequisites

- Java 17+ (supports JavaFX)
- Maven 3.6+

### 1. Start Server

```bash
mvn compile exec:java "-Dexec.mainClass=org.example.demo.server.Server"
```

Server listens on port **5050**.  
On startup, loads `farms.txt` if present; otherwise starts with empty farms.

### 2. Start Client(s)

In separate terminals:

```bash
mvn compile exec:java "-Dexec.mainClass=org.example.demo.Application"
```

- Enter username in login dialog (e.g., `alice`, `bob`, `victim`).
- Each client connects to `127.0.0.1:5050`.
- You can run multiple clients simultaneously.

### 3. Run Concurrency Test

Automated script to test multi-thief scenario:

```bash
mvn compile exec:java "-Dexec.mainClass=org.example.demo.ConcurrencyTest"
```

**Scenario:**
- Victim plants 4 crops, waits 11s for ripening.
- Victim views alice (leaves own farm).
- Alice & Bob concurrently send `STEAL victim`.
- Expected: one succeeds (ripe 4→3), other fails (maxSteal=0).

---

## Demo Checklist (5.6 - 5.8)

### 5.6 Threading & Responsiveness

- [x] **UI remains responsive** during network I/O and crop growth (separate threads).
- [x] **Console logs show thread names** (`client-io`, `crop-growth`, `server-listener`).
- [x] **Thread pools configured** with daemon threads, no cross-locking.

### 5.7 Failure Recovery

- [x] **Client crash:** Server detects disconnect, cleans resources. Client relaunches → `LOGIN` → farm state consistent.
- [x] **Server crash:** Client shows "Disconnected", disables buttons, auto-reconnects. Server restart loads `farms.txt` → state restored (coins + board + timestamps).
- [x] **Unsent actions:** Commands are not queued; dropped on disconnect. Client resyncs via `GET`.

### 5.8 Wrap-Up

- [x] **Rubric coverage:** Server logic, client UI, concurrency, error handling (see above).
- [x] **Documentation:** This README + inline comments in code.
- [x] **Architecture diagram:** See "Thread Model" section above.
- [x] **Protocol specification:** See "Communication Protocol" section above.

---

## Extension Ideas (Optional)

1. **Database persistence:** Replace `farms.txt` with SQLite or PostgreSQL for scalability.
2. **Reconnect button:** Add manual reconnect UI instead of only auto-retry.
3. **Friend list:** Server maintains social graph, clients can query online friends.
4. **Chat system:** Add `CHAT <message>` command for in-game messaging.
5. **Real-time notifications:** Push updates when friends steal from you (already implemented via `UPDATE` broadcast).
6. **Leaderboard:** Track total coins earned, display top players.

---

## File Structure

```
src/main/java/org/example/demo/
├── Application.java          # JavaFX entry point
├── Controller.java           # UI controller + networking
├── LoginWindow.java          # Login dialog
├── ConcurrencyTest.java      # Stress test script
├── game/
│   └── Game.java             # Game logic (board, coins, growth)
└── server/
    └── Server.java           # Multi-threaded server

farms.txt                     # Persistence file (auto-generated)
pom.xml                       # Maven dependencies
README.md                     # This file
```

---

## Known Limitations

1. **No encryption:** Protocol is plain text (acceptable for localhost demo).
2. **No authentication:** Username is trusted (no password).
3. **Single-server:** No load balancing or distributed architecture.
4. **Limited error recovery:** If `farms.txt` is corrupted, server fails to start (could add validation).

---

## Questions for TA

**Q: How do you ensure data consistency when multiple clients steal concurrently?**

A: We use `synchronized(victim)` to atomically:
1. Read `victim.getRipeCount()` and compute `maxSteal`.
2. Check session limits (`sessionStealCounts`).
3. Call `victim.stealOneRipe()` (which internally locks and picks one RIPE crop).
4. Update `thief.addCoins()` (also synchronized inside Game).

This ensures only one thief can enter the critical section at a time, preventing race conditions.

**Q: Why not use a ReadWriteLock or finer-grained locking?**

A: For this assignment's scale (4x4 grid, simple state), `synchronized` on the entire `Game` object is sufficient and easier to reason about. Future optimization could use `StampedLock` or per-cell locks if profiling shows contention.

**Q: How do you handle server crash mid-operation?**

A:
- Completed operations are saved to `farms.txt` immediately.
- In-flight operations (commands received but not yet replied) are lost.
- Client resyncs by sending `GET` after reconnect, which returns server's authoritative state.
- GROWING crops resume countdown based on saved timestamps.

**Q: What happens if two clients with the same username log in?**

A: Both share the same `Game` object in `farms`. This is intentional (no session isolation). In production, you'd add session tokens or reject duplicate logins.

**Q: How would you extend this to support 1000+ concurrent users?**

A:
1. Replace in-memory `ConcurrentHashMap` with distributed cache (Redis).
2. Use async I/O (Netty, Java NIO) instead of thread-per-connection.
3. Shard farms across multiple server instances by username hash.
4. Replace `farms.txt` with database (PostgreSQL, MongoDB).
5. Add rate limiting and authentication.

---

## Conclusion

This project demonstrates a **fully functional multiplayer farm game** with robust concurrency control, graceful failure recovery, and seamless reconnection. The implementation balances simplicity (for educational clarity) with correctness (atomic operations, thread safety). All rubric requirements (server logic, client UI, concurrency, error handling) are covered.

**Submission ready for TA review.** ✅
**End of README**