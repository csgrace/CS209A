# QQ Farm Report
#### 12311043 魏宇晴

## 1. Overview
Instructions on to run the programme:
1. The environment of this project is JDK 22.
2. Open a terminal and navigate to the directory containing the source code.
3. Run the server using the command:
   ```
   mvn compile exec:java "-Dexec.mainClass=org.example.demo.server.Server"
   ```
   The server will start listening on **port 5050**.
   You should see:
    ```
    ========================================
    Server started on port: 5050
    Waiting for clients...
    ========================================
    ```
4. Right click → **Run 'Application.main()'**.
5. Firstly, you will see the following login window.
   ![img.png](img.png)
   Enter a username (e.g., `alice`, `bob`, `grace`). Click **Login** to proceed.
6. After logging in, you will see the main interface of the farm.
    ![img_2.png](img_2.png)
    You can see your current coins at the top left corner. Each player owns a 4x4 grid of plots (states: `EMPTY`, `GROWING`, `RIPE`). 
    
    - **Select a plot** by clicking on an empty cell (it will have a red border).
    - Click **Plant** (costs 5 coins).
      - The plot turns **green** (`GROWING`).
      - After **10 seconds**, it automatically becomes **gold** (`RIPE`).
    - Click **Harvest** to collect the crop.
      - You gain **+12 coins**.
      - The plot returns to `EMPTY`.
      
    ![img_3.png](img_3.png)
7. Type the name of the user you want to steal from in the text field at the top and click the "Visit" button to enter his/her farm.
    ![img_4.png](img_4.png)
    You can see your own coins and the coins of the user you are visiting at the top. You can also click the "Back Home" button on the top right corner to return to your own farm. 
    ![img_5.png](img_5.png)
    **Real-time synchronization:** If `mark` plants/harvests while you're viewing, you'll see the updates immediately.
    
    Click the "Steal" button at the bottom right corner to steal a random crop from that user's farm.  
    Stealing Rules:
      - **Victim must be "away"** (viewing another player's farm, not their own).
      - You can only steal **ripe crops** (`RIPE` state).
      - **25% limit:** You can steal at most `floor(currentRipe × 0.25)` crops per session.
          - Example: If victim has 8 ripe crops, you can steal **2 times** (floor(8×0.25)=2).
          - After stealing twice, the session ends. You can't steal again until the victim plants new crops.
      - **Each successful steal:**
          - Victim loses 1 ripe crop (it becomes `EMPTY`).
          - You gain **+3 coins**.
        
    ![img_7.png](img_7.png)
    On the above screenshot, you can see that the visitor successfully stole a mature crop from the victim's farm, and the visitor's coins increased accordingly. The server side response the following meassage:
    ```
    [VIEW] mark is viewing grace's farm
    [STEAL ATTEMPT] Player: bob | Victim: mark | currentRipe: 8 | maxSteal: 2
    [STEAL] bob successfully stole from mark | session count: 1
    [PERSIST] Farms saved to farms.txt
    [STEAL ATTEMPT] Player: bob | Victim: mark | currentRipe: 7 | maxSteal: 1
    [STEAL] bob failed - session limit reached, current: 1, max: 1
    ```
   
## 2. Key Implementations 
This part is written mainly to match the **Key Requirements** in the project description pdf.
### 2.1 Server-Side Implementation
The server manages all players, crop growth, and concurrent stealing requests. It ensures that updates to shared data (e.g., crop yield) are **atomic** and **thread-safe**.
#### Connection Management
- **Multi-threaded Architecture**: Uses `CachedThreadPool` to spawn one thread per client connection.
- **ClientHandler**: Each connected client is handled by a dedicated `client-io` thread that processes incoming commands (LOGIN, PLANT, HARVEST, STEAL, etc.).
- **Thread Pool Configuration**:
  ```java
  private final ExecutorService pool = Executors.newCachedThreadPool((Runnable runnable) -> {
      Thread t = new Thread(runnable, "client-io");
      t.setDaemon(true);
      return t;
  });