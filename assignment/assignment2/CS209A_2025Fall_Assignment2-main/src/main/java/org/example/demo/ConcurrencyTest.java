package org.example.demo;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;

/**
 * ConcurrencyTest for 5.5 Multi-Thief Scenario.
 *
 * Script behavior:
 *  1. Start three scripted clients: victim, alice, bob.
 *  2. Victim plants 4 crops (row 0, col 0..3), waits for them to ripen (10s -> wait 11s).
 *  3. Victim VIEW alice (victim is "away").
 *  4. Alice and Bob VIEW victim.
 *  5. Alice and Bob concurrently send STEAL victim.
 *
 * Two modes:
 *   exactSimultaneous = true  -> likely TWO successes (each taking a different RIPE crop).
 *   exactSimultaneous = false -> bob slightly delayed; alice's success reduces ripe 4->3,
 *                                so bob possibly sees maxSteal=0 and fails.
 *
 * Run (with server already running on port 5050):
 *   mvn compile exec:java -Dexec.mainClass=org.example.demo.ConcurrencyTest
 */
public class ConcurrencyTest {

    // 改这里可以切换 "两人都成功" vs "一个成功一个失败" 的演示模式
    private static final boolean exactSimultaneous = true;
    private static final boolean staggerSecond = !exactSimultaneous;
    private static final int secondDelayMillis = 120; // only used when staggerSecond=true

    // 简单的 socket 客户端封装
    static class Client implements Closeable {
        final String name;
        final Socket socket;
        final BufferedReader in;
        final PrintWriter out;

        Client(String name) throws IOException {
            this.name = name;
            this.socket = new Socket("127.0.0.1", 5050);
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            this.out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);

            // 读取欢迎行
            readLine(1500);
            send("LOGIN " + name);
            drain(5, 800);
        }

        void send(String cmd) {
            System.out.println("[" + name + "] -> " + cmd + " @" + Instant.now());
            out.println(cmd);
        }

        String readLine(long timeoutMs) {
            try {
                socket.setSoTimeout((int) timeoutMs);
                return in.readLine();
            } catch (IOException e) {
                return null;
            }
        }

        void drain(int maxLines, int timeoutMs) {
            for (int i = 0; i < maxLines; i++) {
                String line = readLine(timeoutMs);
                if (line == null) break;
                System.out.println("[" + name + "] <= " + line);
            }
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=== ConcurrencyTest (Multi-Thief) START ===");

        Client victim = new Client("victim");
        Client alice  = new Client("alice");
        Client bob    = new Client("bob");

        // 1) victim 种 4 个格子
        plantFour(victim);

        // 等 11s 让作物成熟
        System.out.println("[victim] Waiting 11s for ripening...");
        Thread.sleep(11_000);
        victim.send("GET");
        victim.drain(5, 800);

        // 2) victim 离开自己农场 -> VIEW alice
        victim.send("VIEW alice");
        victim.drain(5, 800);

        // 3) 小偷 A/B 都 VIEW victim
        alice.send("VIEW victim"); alice.drain(5, 800);
        bob.send("VIEW victim");   bob.drain(5, 800);

        // 4) 并发 STEAL victim
        System.out.println("\n--- Concurrent STEAL Attempt (victim has 4 RIPE) ---");
        runConcurrentSteal(alice, bob);

        // 打印 victim 最终快照，用于证明扣减次数 & 没有负数或乱状态
        victim.send("GET");
        victim.drain(5, 800);

        victim.close();
        alice.close();
        bob.close();

        System.out.println("=== ConcurrencyTest END ===");
        System.out.println("Mode: exactSimultaneous=" + exactSimultaneous +
                ", staggerSecond=" + staggerSecond +
                ", delay(ms)=" + (staggerSecond ? secondDelayMillis : 0));
    }

    private static void plantFour(Client victim) {
        for (int c = 0; c < 4; c++) {
            victim.send("PLANT 0 " + c);
            victim.drain(4, 800);
        }
    }

    private static void runConcurrentSteal(Client a, Client b) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go    = new CountDownLatch(1);

        Thread tAlice = new Thread(() -> stealOnce(a, ready, go, false), "steal-" + a.name);
        Thread tBob   = new Thread(() -> stealOnce(b, ready, go, staggerSecond), "steal-" + b.name);

        tAlice.start();
        tBob.start();

        // 等待两个线程都准备好
        ready.await();
        System.out.println(">>> Countdown 3...2...1... GO!");
        go.countDown();

        tAlice.join();
        tBob.join();
    }

    private static void stealOnce(Client thief, CountDownLatch ready, CountDownLatch go, boolean delayed) {
        try {
            ready.countDown();
            go.await();
            if (delayed) {
                Thread.sleep(secondDelayMillis);
            }
            thief.send("STEAL victim");
            // 读取主响应 (OK/ERR) + 可能的 UPDATE 行
            thief.drain(6, 1500);
        } catch (Exception e) {
            System.out.println("[" + thief.name + "] error: " + e.getMessage());
        }
    }
}
