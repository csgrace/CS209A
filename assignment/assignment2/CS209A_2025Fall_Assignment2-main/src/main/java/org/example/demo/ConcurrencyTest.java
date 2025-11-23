package org.example.demo;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;

/**
 * Ripe4ConcurrentTest:
 * - Plants 4 crops for victim, waits for them to ripen.
 * - Victim views alice (victim is "away").
 * - Alice & Bob VIEW victim and then attempt concurrent STEAL.
 *
 * Two modes:
 *   exactSimultaneous = true  -> likely TWO successes (each gets one different ripe crop).
 *   staggerSecond     = true  -> second thread delayed slightly; first success reduces ripe=4->3,
 *                                floor(3*0.25)=0 => second fails (ERR No allowed steals (maxSteal=0)).
 *
 * Run:
 *   mvn exec:java -Dexec.mainClass=org.example.demo.Ripe4ConcurrentTest
 * Make sure server already running on port 5050.
 */
public class ConcurrencyTest {

    private static final boolean exactSimultaneous = true;   // 改成 false 即可看“错位”
    private static final boolean staggerSecond = !exactSimultaneous;
    private static final int secondDelayMillis = 120;        // 错位延迟

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
            // Welcome
            readLine(1500);
            send("LOGIN " + name);
            drain(6, 800);
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

        void drain(int max, int timeoutMs) {
            for (int i = 0; i < max; i++) {
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
        System.out.println("=== Ripe4ConcurrentTest START ===");
        Client victim = new Client("victim");
        Client alice  = new Client("alice");
        Client bob    = new Client("bob");

        // Reset steal cycle via planting (关键：确保 canStealThisCycle 对 victim 为 true)
        plantFour(victim);

        // Wait for ripening (≥10s)
        System.out.println("[victim] Waiting 11s for ripening...");
        Thread.sleep(11_000);
        victim.send("GET");
        victim.drain(5, 800);

        // Victim leaves home
        victim.send("VIEW alice");
        victim.drain(5, 800);

        // Thieves must VIEW victim
        alice.send("VIEW victim"); alice.drain(5, 800);
        bob.send("VIEW victim");   bob.drain(5, 800);

        // Concurrent steal attempt
        System.out.println("\n--- Concurrent STEAL Attempt (ripe=4) ---");
        runConcurrentSteal(alice, bob);

        // Show victim updated snapshot
        victim.send("GET");
        victim.drain(5, 800);

        victim.close();
        alice.close();
        bob.close();
        System.out.println("=== Ripe4ConcurrentTest END ===");
        System.out.println("Mode: exactSimultaneous=" + exactSimultaneous + ", staggerSecond=" + staggerSecond);
        System.out.println("Explanation:");
        System.out.println("  - If exactSimultaneous=true: both likely succeed (each takes a different RIPE).");
        System.out.println("  - If staggerSecond=true: first success reduces ripe from 4->3, second arrives late, maxSteal=floor(3*0.25)=0 => fail.");
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

        Thread tA = new Thread(() -> stealOnce(a, ready, go, false), "steal-" + a.name);
        Thread tB = new Thread(() -> stealOnce(b, ready, go, staggerSecond), "steal-" + b.name);

        tA.start();
        tB.start();
        ready.await();
        System.out.println(">>> Countdown 3...2...1... GO!");
        go.countDown();
        tA.join();
        tB.join();
    }

    private static void stealOnce(Client thief, CountDownLatch ready, CountDownLatch go, boolean delayed) {
        try {
            ready.countDown();
            go.await();
            if (delayed) {
                Thread.sleep(secondDelayMillis);
            }
            thief.send("STEAL victim");
            // Primary response (OK / ERR) + maybe UPDATE lines; we just read a few
            thief.drain(6, 1500);
        } catch (Exception e) {
            System.out.println("[" + thief.name + "] error: " + e.getMessage());
        }
    }
}