package org.example;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
public class ExecutorPractice
{
    /**
     * Counts occurrences of a given word in a file.
     * @return the number of times the word occurs in the given word
     */
    public static long getWordCount(String word, Path path)
    {
        // TODO
        long count = 0;
        try (BufferedReader reader = Files.newBufferedReader(path))
        {
            String line;
            while ((line = reader.readLine()) != null)
            {
                int index = 0;
                while ((index = line.indexOf(word, index)) != -1)
                {
                    count++;
                    index += word.length();
                }

            }
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        return count;
    }

    /**
     * Returns all descendants (Path) of a given directory
     * @param rootDir the root directory
     * @return a set of all descendants of the root directory
     */
    public static Set<Path> getDescendants(Path rootDir)
    {
        // TODO
        Set<Path> descendants = new HashSet<>();
        try
        {
            Files.walk(rootDir).filter(Files::isRegularFile).forEach(descendants::add);
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        return descendants;
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
            try (BufferedReader reader = Files.newBufferedReader(path))
            {
                String line;
                while ((line = reader.readLine()) != null)
                {
                    if (Thread.currentThread().isInterrupted()) {
                        System.out.println("Search in " + path + " canceled.");
                        return null;
                    }

                    if (line.contains(word)) {
                        return path;
                    }
                }
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }

            throw new NoSuchElementException("Word not found in " + path);
        };
    }

    public static void main(String[] args){
        Scanner in = new Scanner(System.in);
        System.out.print("Enter keyword: ");
        String word = in.nextLine();

        Set<Path> files = getDescendants(Path.of("D:\\E\\SUSTECH\\grade3\\3_up\\CS\\CS209A\\lab\\lab8\\jdk8_src"));

        // Task 1: Total word occurrences
        List<Callable<Long>> tasks1 = new ArrayList<>();
        for (Path file : files)
        {
            Callable<Long> task = () -> getWordCount(word, file);
            tasks1.add(task);
        }
        // TODO
        // create an executor that invoke all tasks1 and sum all the results
        ExecutorService executor1 = Executors.newFixedThreadPool(10);
        long totalCount = 0;
        try
        {
            List<Future<Long>> results = executor1.invokeAll(tasks1);
            for (Future<Long> f : results)
            {
                totalCount += f.get();
            }
            System.out.println("Occurrences: " + totalCount);
        }
        catch (InterruptedException | ExecutionException e)
        {
            e.printStackTrace();
        }
        finally
        {
            executor1.shutdown();
        }


        // Task 2: Find first matching file
        List<Callable<Path>> tasks2 = new ArrayList<>();
        for (Path file : files){
            tasks2.add(findMatchFile(word, file));
        }

        // TODO
        // create an executor that invoke any one of tasks2 and get the result
        ExecutorService executor2 = Executors.newCachedThreadPool();
        try
        {
            Path found = executor2.invokeAny(tasks2);
            if (found != null)
                System.out.println("First file: " + found);
            else
                System.out.println("No file");
        }
        catch (InterruptedException | ExecutionException e)
        {
            e.printStackTrace();
        }
        finally
        {
            executor2.shutdown();
        }

    }
}
