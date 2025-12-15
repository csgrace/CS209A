package cs209a.finalproject_demo.server;

import cs209a.finalproject_demo.object.Multithreading;
import cs209a.finalproject_demo.repository.MultithreadingIssueDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class MultithreadingIssueService {

    @Autowired
    private MultithreadingIssueDao multithreadingIssueDao;

    public List<Multithreading> getTopMultithreadingIssues(int limit,
                                                           String sort,
                                                           String hasCode,
                                                           String hasException,
                                                           String startDate,
                                                           String endDate) {

        Long startEpoch = parseDateToEpoch(startDate);
        Long endEpoch = parseDateToEpochInclusive(endDate);

        List<Object[]> results = multithreadingIssueDao.findMultithreadingIssuesByContent(Math.max(limit, 200), startEpoch, endEpoch);

        List<Multithreading> items = mapDaoResultsToObjects(results);
        items = applyFilters(items, hasCode, hasException);
        items = computeEvidence(items);
        items = applySort(items, sort);
        return items.stream().limit(limit).collect(Collectors.toList());
    }

    private Long parseDateToEpoch(String dateStr) {
        try {
            if (dateStr == null || dateStr.isEmpty()) return null;
            LocalDate ld = LocalDate.parse(dateStr);
            return ld.atStartOfDay().toEpochSecond(ZoneOffset.UTC);
        } catch (Exception e) {
            return null;
        }
    }

    private Long parseDateToEpochInclusive(String dateStr) {
        try {
            if (dateStr == null || dateStr.isEmpty()) return null;
            LocalDate ld = LocalDate.parse(dateStr);
            return ld.plusDays(1).atStartOfDay().minusSeconds(1).toEpochSecond(ZoneOffset.UTC);
        } catch (Exception e) {
            return null;
        }
    }

    private List<Multithreading> mapDaoResultsToObjects(List<Object[]> results) {
        return results.stream().map(row -> {
            String issueType = row.length > 0 && row[0] != null ? (String) row[0] : "Unknown";
            Integer freq = row.length > 1 && row[1] != null ? ((Number) row[1]).intValue() : 0;
            Double avgScore = row.length > 2 && row[2] != null ? ((Number) row[2]).doubleValue() : 0.0;
            Double avgView = row.length > 3 && row[3] != null ? ((Number) row[3]).doubleValue() : 0.0;
            Double avgAnswer = row.length > 4 && row[4] != null ? ((Number) row[4]).doubleValue() : 0.0;
            Long exampleQuestionId = row.length > 5 && row[5] != null ? ((Number) row[5]).longValue() : null;
            String exampleTitle = row.length > 6 && row[6] != null ? (String) row[6] : "";
            String exampleCodeJoined = row.length > 7 && row[7] != null ? (String) row[7] : null;
            String exceptionsJoined = row.length > 8 && row[8] != null ? (String) row[8] : null;

            String exampleCode = null;
            if (exampleCodeJoined != null && !exampleCodeJoined.isEmpty()) {
                String[] parts = exampleCodeJoined.split("\\s*\\|\\|\\|\\s*");
                if (parts.length > 0) exampleCode = parts[0];
            }

            List<String> exceptionsList = exceptionsJoined != null && !exceptionsJoined.isEmpty()
                    ? Arrays.stream(exceptionsJoined.split("\\s*,\\s*")).filter(s -> !s.isEmpty()).collect(Collectors.toList())
                    : Collections.emptyList();

            Multithreading mt = new Multithreading();
            mt.setIssueType(issueType);
            mt.setDescription(getDescriptionByIssueType(issueType));
            mt.setFrequency(freq);
            mt.setAvgScore(avgScore);
            mt.setAvgViewCount(avgView);
            mt.setAvgAnswerCount(avgAnswer);
            mt.setExampleQuestion(exampleTitle);
            mt.setExampleQuestionId(exampleQuestionId);
            mt.setExampleCode(exampleCode);
            mt.setExceptions(exceptionsList);

            mt.setEvidenceScore(null);
            mt.setEvidenceComponents(null);
            return mt;
        }).collect(Collectors.toList());
    }

    private List<Multithreading> applyFilters(List<Multithreading> items, String hasCode, String hasException) {
        return items.stream().filter(item -> {
            if ("true".equalsIgnoreCase(hasCode) && (item.getExampleCode() == null || item.getExampleCode().isEmpty())) return false;
            if ("false".equalsIgnoreCase(hasCode) && item.getExampleCode() != null && !item.getExampleCode().isEmpty()) return false;
            if ("true".equalsIgnoreCase(hasException) && (item.getExceptions() == null || item.getExceptions().isEmpty())) return false;
            if ("false".equalsIgnoreCase(hasException) && item.getExceptions() != null && !item.getExceptions().isEmpty()) return false;
            return true;
        }).collect(Collectors.toList());
    }

    private List<Multithreading> computeEvidence(List<Multithreading> items) {
        for (Multithreading it : items) {
            boolean hasTitle = it.getExampleQuestion() != null && !it.getExampleQuestion().trim().isEmpty();
            boolean hasCode = it.getExampleCode() != null && !it.getExampleCode().trim().isEmpty();
            boolean hasExceptions = it.getExceptions() != null && !it.getExceptions().isEmpty();
            boolean hasAnswers = it.getAvgAnswerCount() > 0.0;

            int score = 0;
            if (hasTitle) score++;
            if (hasCode) score++;
            if (hasExceptions) score++;
            if (hasAnswers) score++;

            it.setEvidenceScore((double) score);

            Map<String, Boolean> comps = new LinkedHashMap<>();
            comps.put("title", hasTitle);
            comps.put("code", hasCode);
            comps.put("exceptions", hasExceptions);
            comps.put("answers", hasAnswers);
            it.setEvidenceComponents(comps);
        }
        return items;
    }

    private List<Multithreading> applySort(List<Multithreading> items, String sort) {
        if (sort == null || sort.isEmpty() || "frequency".equalsIgnoreCase(sort)) {
            items.sort(Comparator.comparingInt(Multithreading::getFrequency).reversed());
            return items;
        }
        switch (sort) {
            case "avgView":
                items.sort(Comparator.comparingDouble((Multithreading m) -> m.getAvgViewCount()).reversed());
                break;
            case "avgScore":
                items.sort(Comparator.comparingDouble((Multithreading m) -> m.getAvgScore()).reversed());
                break;
            case "evidence":
                items.sort(Comparator.comparingDouble((Multithreading m) -> m.getEvidenceScore() == null ? 0.0 : m.getEvidenceScore()).reversed());
                break;
            default:
                items.sort(Comparator.comparingInt(Multithreading::getFrequency).reversed());
        }
        return items;
    }

    private String getDescriptionByIssueType(String issueType) {
        switch (issueType) {
            case "Deadlock":
                return "Two or more threads are blocked forever, waiting for each other";
            case "Race Condition":
            case "Race Condition (Lifecycle / Ordering)":
                return "Program's behavior depends on timing/order of threads (race conditions, ordering issues)";
            case "ConcurrentModificationException":
                return "Concurrent modification of collections";
            case "IllegalMonitorStateException":
                return "Incorrect use of wait/notify or monitor-related API causing IllegalMonitorStateException";
            case "InterruptedException":
            case "InterruptedException Misuse":
            case "Incorrect Interruption Handling":
                return "Incorrect handling of thread interruption / InterruptedException";
            case "Thread Starvation":
            case "Liveness / Progress / Hanging Threads":
                return "Threads not making progress (starvation, hanging, dead threads)";
            case "Busy Waiting / Spin":
                return "Busy-wait loops or spin-wait causing high CPU usage";
            case "Incorrect Synchronized Usage":
            case "Synchronization (synchronized)":
                return "Issues with synchronized blocks/methods or monitor usage";
            case "ReentrantLock Misuse":
            case "Locking (ReentrantLock/Lock)":
                return "Problems using explicit locks (ReentrantLock/Lock) or missing unlocks";
            case "Thread Pool Misconfiguration":
            case "Thread Pool / Executor":
                return "Problems with ThreadPoolExecutor, ExecutorService, wrong sizing or blocking tasks";
            case "Blocking Operations in Thread Pool":
                return "Blocking operations executed inside thread pools causing pool starvation";
            case "Future/Callable Misuse":
            case "Future/Callable":
                return "Issues with futures/callables, blocking get() or wrong usage";
            case "ExecutorService Not Shutdown":
                return "ExecutorService not properly shutdown leading to non-terminating apps";
            case "Volatile Misuse":
            case "Volatile Variables":
                return "Problems with volatile keyword usage and memory visibility";
            case "Atomic Classes Misuse":
            case "Atomic Operations":
                return "Issues with AtomicInteger/AtomicReference usage or expectations";
            case "ThreadLocal Leak":
            case "ThreadLocal":
                return "ThreadLocal values not removed causing memory leaks or incorrect reuse";
            case "Using sleep Instead of Sync":
                return "Using Thread.sleep() as synchronization leading to race/timing bugs";
            case "Non-thread-safe Collections":
                return "Using non-thread-safe collections (ArrayList/HashMap) across threads";
            case "UI Thread Violations":
            case "UI/Main Thread Blocking":
                return "Long-running or blocking operations executed on UI/main thread";
            case "Double-Checked Locking Bug":
            case "Singleton Initialization / Double-Checked Locking":
                return "Incorrect lazy initialization / double-checked locking without proper memory barriers";
            case "Unsafe Publication":
                return "Objects published without synchronization leading to partially-constructed visibility";
            case "Deprecated Thread Control (stop/suspend)":
                return "Use of deprecated Thread control methods (stop/suspend/resume)";
            case "Wait/Notify Misuse":
            case "Condition/Wait-Set Misuse":
                return "Incorrect wait/notify or Condition usage (missing loop, wrong signaling)";
            case "Nested Locks / Lock Ordering":
            case "ReadWriteLock / Lock Ordering Issues":
                return "Nested/mismatched lock acquisition ordering leading to deadlock";
            case "Thread Life Cycle Misuse":
            case "Incorrect Thread Creation/Lifecycle":
            case "Task/Service Lifecycle Issues":
                return "Thread/task/service lifecycle mismanagement (start/stop/init/shutdown issues)";
            case "Shared Mutable State Without Synchronization":
                return "Shared mutable state accessed without proper synchronization";
            case "Singleton / Initialization Concurrency Issues":
                return "Initialization-time concurrency issues and singleton safety problems";
            case "Producer-Consumer / BlockingQueue Issues":
            case "BlockingQueue Misuse":
            case "Producer-Consumer Issues":
                return "Producer-consumer patterns or BlockingQueue misuse (empty/full queues, deadlocks)";
            case "ForkJoin / Parallelism Misuse":
            case "ForkJoinPool Misuse":
            case "Parallel Stream Misuse":
                return "Misuse of ForkJoinPool/parallel streams leading to starvation or shared-state bugs";
            case "CompletableFuture / Async Chaining Issues":
            case "CompletableFuture Misuse":
                return "Async chaining / CompletableFuture pitfalls (blocking get, never completes)";
            case "Synchronization on Wrong Object / Escaping This":
                return "Synchronizing on wrong object or escaping 'this' during construction";
            case "Incorrect Thread Interruption Handling":
                return "Improper interruption handling (swallowed InterruptedException, flag cleared incorrectly)";
            default:
                return "General multithreading issue";
        }
    }
}