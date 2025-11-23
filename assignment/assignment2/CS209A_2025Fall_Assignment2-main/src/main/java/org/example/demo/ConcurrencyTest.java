package org.example.demo;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;

public class ConcurrencyTest {
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

        plantFour(victim);

        System.out.println("[victim] Waiting 11s for ripening...");
        Thread.sleep(11_000);
        victim.send("GET");
        victim.drain(5, 800);

        victim.send("VIEW alice");
        victim.drain(5, 800);

        alice.send("VIEW victim");
        alice.drain(5, 800);
        bob.send("VIEW victim");
        bob.drain(5, 800);

        System.out.println("[victim] Confirming victim is 'away' (VIEW alice)");
        victim.send("VIEW alice");
        victim.drain(5, 800);

        System.out.println("\n--- Concurrent STEAL Attempt (victim has 4 RIPE) ---");
        runConcurrentSteal(alice, bob);

        victim.send("GET");
        victim.drain(5, 800);

        victim.close();
        alice.close();
        bob.close();

        System.out.println("=== ConcurrencyTest END ===");
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

        Thread tAlice = new Thread(() -> stealOnce(a, ready, go), "steal-" + a.name);
        Thread tBob   = new Thread(() -> stealOnce(b, ready, go), "steal-" + b.name);

        tAlice.start();
        tBob.start();

        ready.await();
        System.out.println(">>> Countdown 3...2...1... GO!");
        go.countDown();

        tAlice.join();
        tBob.join();
    }

    private static void stealOnce(Client thief, CountDownLatch ready, CountDownLatch go) {
        try {
            ready.countDown();
            go.await();
            thief.send("STEAL victim");
            thief.drain(6, 1500);
        } catch (Exception e) {
            System.out.println("[" + thief.name + "] error: " + e.getMessage());
        }
    }
}
