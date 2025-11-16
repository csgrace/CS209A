import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Scanner;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;


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
                    enumerate(Path.of(directory));
                    blockingQueue.put(DUMMY);
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
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
                            if (file == DUMMY) {
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

    private static void enumerate(Path directory) throws IOException, InterruptedException {
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(directory)) {
            for (Path entry : entries) {
                if (Files.isDirectory(entry)) {
                    enumerate(entry);
                } else if (entry.toString().endsWith(".java")) {
                    blockingQueue.put(entry);
                }
            }
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
        try (var in = new BufferedReader(new InputStreamReader(Files.newInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            int lineNumber = 0;
            while ((line = in.readLine()) != null) {
                lineNumber++;
                if (line.contains(keyword)) {
                    System.out.printf("%s:%d: %s%n", file, lineNumber, line.trim());
                }
            }
        }
    }
}