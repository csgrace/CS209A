import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.io.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ExecutorPractice
{
    /**
     * Counts occurrences of a given word in a file.
     * @return the number of times the word occurs in the given word
     */
    public static long getWordCount(String word, Path path)
    {
        // TODO
        // 整词匹配，大小写敏感；如需不敏感，添加 Pattern.CASE_INSENSITIVE
        Pattern pattern = Pattern.compile("\\b" + Pattern.quote(word) + "\\b");

        long total = 0L;

        // 优先用 UTF-8 读取；若编码异常，可退回系统默认编码，避免少量非源码文件导致中断
        try (BufferedReader br = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = br.readLine()) != null) {
                Matcher m = pattern.matcher(line);
                while (m.find()) total++;
            }
        } catch (MalformedInputException mie) {
            // 退回默认编码
            try (BufferedReader br = Files.newBufferedReader(path)) {
                String line;
                while ((line = br.readLine()) != null) {
                    Matcher m = pattern.matcher(line);
                    while (m.find()) total++;
                }
            } catch (IOException ignored) {
                // 读不到就当 0 次
            }
        } catch (IOException e) {
            // 读不到就当 0 次
        }
        return total;
    }

    /**
     * Returns all descendants (Path) of a given directory
     * @param rootDir the root directory
     * @return a set of all descendants of the root directory
     */
    public static Set<Path> getDescendants(Path rootDir)
    {
        // TODO
        try (Stream<Path> s = Files.walk(rootDir)) {
            return s.filter(Files::isRegularFile)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to walk directory: " + rootDir, e);
        }
    }

    /**
     * Yields a task that searches for a word in a file.
     * @param word the word to search
     * @param path the file in which to search
     * @return the search task that yields the path upon success
     */
    public static Callable<Path> findMatchFile(String word, Path path)
    {
        // TODO
        return () -> {
            // 中断检测（被取消后应尽快退出）
            if (Thread.currentThread().isInterrupted()) {
                System.out.println("Search in " + path + " canceled.");
                return null; // 按题目建议：打印后返回
            }

            Pattern pattern = Pattern.compile("\\b" + Pattern.quote(word) + "\\b");

            // 同 getWordCount 的读法：先 UTF-8，失败再退回默认编码
            try (BufferedReader br = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (Thread.currentThread().isInterrupted()) {
                        System.out.println("Search in " + path + " canceled.");
                        return null;
                    }
                    if (pattern.matcher(line).find()) {
                        return path;
                    }
                }
            } catch (MalformedInputException mie) {
                try (BufferedReader br = Files.newBufferedReader(path)) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        if (Thread.currentThread().isInterrupted()) {
                            System.out.println("Search in " + path + " canceled.");
                            return null;
                        }
                        if (pattern.matcher(line).find()) {
                            return path;
                        }
                    }
                }
            } catch (IOException e) {
                // 视为未命中
            }
            // 未命中则抛异常，让 invokeAny 继续其他任务
            throw new NoSuchElementException("No match in " + path);
        };
    }

    public static void main(String[] args){
        Scanner in = new Scanner(System.in);
        System.out.print("Enter keyword: ");
        String word = in.nextLine();

        Set<Path> files = getDescendants(Path.of("jdk8_src"));

        // Task 1: Total word occurrences
        List<Callable<Long>> tasks1 = new ArrayList<>();
        for (Path file : files)
        {
            Callable<Long> task = () -> getWordCount(word, file);
            tasks1.add(task);
        }
        // TODO
        // create an executor that invoke all tasks1 and sum all the results
        // 你可以尝试不同的线程池：CachedThreadPool / SingleThreadExecutor / FixedThreadPool
        ExecutorService pool1 = Executors.newFixedThreadPool(
                Math.max(1, Runtime.getRuntime().availableProcessors())
        );

        long totalOccurrences = 0L;
        try {
            List<Future<Long>> futures = pool1.invokeAll(tasks1);
            for (Future<Long> f : futures) {
                try {
                    totalOccurrences += f.get();
                } catch (ExecutionException e) {
                    // 单个任务失败，计作 0
                }
            }
            System.out.println("Occurrences of " + word + ": " + totalOccurrences);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Counting interrupted.");
        } finally {
            pool1.shutdown();
        }

        // Task 2: Find first matching file
        List<Callable<Path>> tasks2 = new ArrayList<>();
        for (Path file : files){
            tasks2.add(findMatchFile(word, file));
        }

        // TODO
        // create an executor that invoke any one of tasks2 and get the result
        // 建议试试 CachedThreadPool 来加速“抢到第一个命中”
        ExecutorService pool2 = Executors.newCachedThreadPool();
        try {
            Path found = pool2.invokeAny(tasks2);
            if (found != null) {
                System.out.println("Found the first file that contains " + word + ":");
                System.out.println(found.toString());
            } else {
                // 按题目建议，某些被取消任务会返回 null，这里 null 并不代表找到
                // invokeAny 一般在真正找到后才会返回，这里仅做健壮性处理
                System.out.println("invokeAny returned null (likely due to cancellation timing).");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Search interrupted.");
        } catch (ExecutionException e) {
            // 所有任务都失败（没找到）
            System.out.println("No file contains the word: " + word);
        } finally {
            // 加速中断其余任务，便于看到 "canceled" 打印
            pool2.shutdownNow();
        }
    }
}
