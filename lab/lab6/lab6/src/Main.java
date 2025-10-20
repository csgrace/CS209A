import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {

    public static List<Path> collectJavaFiles(Path root) throws IOException {
        try (Stream<Path> s = Files.walk(root)) {
            return s.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".java"))
                    .collect(Collectors.toList());
        }
    }

    public static int countTotalLines(List<Path> files) {
        long total = 0L;
        for (Path p : files) {
            total += countFileLinesSafe(p);
        }
        return (int) total;
    }

    public static int countTotalLinesMultiThreaded(List<Path> files, int nThreads) {
        if (nThreads <= 0) throw new IllegalArgumentException("nThreads must be > 0");
        final int size = files.size();
        final int[] partialResults = new int[nThreads];
        Thread[] threads = new Thread[nThreads];

        int chunk = (size + nThreads - 1) / nThreads;
        for (int i = 0; i < nThreads; i++) {
            final int idx = i;
            final int from = i * chunk;
            final int to = Math.min(size, from + chunk);

            threads[i] = new Thread(() -> {
                int subtotal = 0;
                for (int j = from; j < to; j++) {
                    subtotal += countFileLinesSafe(files.get(j));
                }
                partialResults[idx] = subtotal;
            }, "line-counter-" + i);
            threads[i].start();
        }

        for (Thread t : threads) {
            try {
                t.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting threads", e);
            }
        }

        long sum = 0L;
        for (int v : partialResults) sum += v;
        return (int) sum;
    }

    private static int countFileLinesSafe(Path p) {
        try (Stream<String> lines = Files.lines(p)) {
            long c = lines.count();
            return (int) c;
        } catch (IOException e) {
            System.err.println("WARN: Failed to read " + p + " -> " + e.getMessage());
            return 0;
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: java JdkSrcLineCounter <jdk-src-root> [threads1 threads2 ...]");
            System.out.println("Example: java JdkSrcLineCounter \"D:\\E\\SUSTECH\\jdk8-src\" 1 2 4 8");
            return;
        }

        Path root = Paths.get(args[0]);
        if (!Files.isDirectory(root)) {
            System.err.println("Not a directory: " + root.toAbsolutePath());
            return;
        }

        System.out.println("Scanning .java files under: " + root.toAbsolutePath());
        long t0 = System.currentTimeMillis();
        List<Path> files = collectJavaFiles(root);
        long scanMs = System.currentTimeMillis() - t0;
        System.out.println("Total Java files: " + files.size() + " (scanned in " + scanMs + " ms)");
        System.out.println();

        long s1 = System.currentTimeMillis();
        int singleTotal = countTotalLines(files);
        long singleMs = System.currentTimeMillis() - s1;
        System.out.println("(Single thread) Total lines: " + singleTotal);
        System.out.println("(Single thread) Time: " + singleMs + " ms");
        System.out.println();

        List<Integer> threadCounts = new ArrayList<>();
        if (args.length >= 2) {
            for (int i = 1; i < args.length; i++) {
                threadCounts.add(Integer.parseInt(args[i]));
            }
        } else {
            int avail = Math.max(1, Runtime.getRuntime().availableProcessors());
            threadCounts.add(1);
            threadCounts.add(avail);
            threadCounts.add(Math.min(files.size(), Math.max(2, avail * 2)));
        }

        for (int n : threadCounts) {
            long s = System.currentTimeMillis();
            int mtTotal = countTotalLinesMultiThreaded(files, n);
            long ms = System.currentTimeMillis() - s;
            System.out.println("(Multi threads, n=" + n + ") Total lines: " + mtTotal);
            System.out.println("(Multi threads, n=" + n + ") Time: " + ms + " ms");
            if (mtTotal != singleTotal) {
                System.out.println("WARNING: mismatch with single-thread total!");
            }
            System.out.println();
        }

        System.out.println("Done.");
    }
}