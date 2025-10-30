import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.*;

public class BlockingQueueTest
{
    // --- Constants ---
    private static final int FILE_QUEUE_SIZE = 10;
    private static final int SEARCH_THREADS = 100;
    private static final Path DUMMY = Path.of("");

    // --- Shared Queue ---
    private static BlockingQueue<Path> blockingQueue = new ArrayBlockingQueue<>(FILE_QUEUE_SIZE);

    public static void main(String[] args)
    {
        try (var in = new Scanner(System.in))
        {
            String directory = System.getProperty("user.dir");
            System.out.print("Enter keyword (e.g. volatile): ");
            String keyword = in.nextLine();

            // --- Producer Thread ---
            Runnable enumerator = () -> {
                try {
                    // TODO: enumerate all files and put them into the blockingQueue
                    // TODO: after enumeration, put the DUMMY object to signal completion
                    enumerate(Paths.get(directory));
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    // 遍历完成后放入一个 DUMMY，作为完成信号
                    try {
                        blockingQueue.put(DUMMY);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            };

            new Thread(enumerator).start();

            // --- Consumer Threads ---
            for (int i = 1; i <= SEARCH_THREADS; i++) {
                Runnable searcher = () -> {
                    try {
                        boolean done = false;
                        while (!done) {
                            // TODO: take a file path from the blockingQueue
                            // TODO: if it's the dummy, put it back and exit
                            // TODO: otherwise, call a method to search for keyword
                            Path file = blockingQueue.take();

                            if (file == DUMMY || file.equals(DUMMY)) {
                                blockingQueue.put(DUMMY);
                                done = true;
                            } else {
                                search(file, keyword);
                            }
                        }
                    } catch (IOException | InterruptedException e) {
                        e.printStackTrace();
                    }
                };
                new Thread(searcher).start();
            }
        }
    }
    private static void enumerate(Path root) throws IOException, InterruptedException {
        if (root == null) return;
        if (Files.isDirectory(root)) {
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(root)) {
                for (Path child : ds) {
                    if (Files.isDirectory(child)) {
                        enumerate(child);
                    } else if (Files.isRegularFile(child) && child.getFileName().toString().endsWith(".java")) {
                        blockingQueue.put(child);
                    }
                }
            }
        } else if (Files.isRegularFile(root) && root.getFileName().toString().endsWith(".java")) {
            blockingQueue.put(root);
        }
    }

    /**
     * Searches a file for a given keyword and prints all matching lines.
     * @param file the file to search
     * @param keyword the keyword to search for
     */
    public static void search(Path file, String keyword) throws IOException
    {
        // TODO: open the file read each line and print those containing the keyword
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            int lineNo = 0;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                if (line.contains(keyword)) {
                    System.out.printf("%s:%d:%s%n", file.toAbsolutePath(), lineNo, line);
                }
            }
        } catch (MalformedInputException mie) {
            try (BufferedReader reader = Files.newBufferedReader(file, Charset.defaultCharset())) {
                String line;
                int lineNo = 0;
                while ((line = reader.readLine()) != null) {
                    lineNo++;
                    if (line.contains(keyword)) {
                        System.out.printf("%s:%d:%s%n", file.toAbsolutePath(), lineNo, line);
                    }
                }
            }
        }
    }
}