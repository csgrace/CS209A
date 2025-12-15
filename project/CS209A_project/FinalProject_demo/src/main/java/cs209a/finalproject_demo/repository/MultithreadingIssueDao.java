package cs209a.finalproject_demo.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.rowset.SqlRowSet;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Repository
public class MultithreadingIssueDao {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ---- 1. 多线程问题模式（>= 20 种） ----
    // key: canonical issue key; value: regex pattern on full thread text
    private static final LinkedHashMap<String, Pattern> MULTI_ISSUE_PATTERNS = new LinkedHashMap<>();
    static {
        // 1. Deadlock
        MULTI_ISSUE_PATTERNS.put("Deadlock",
                Pattern.compile("\\bdeadlock\\b|\"dead lock\"|both threads (are )?waiting|circular wait", Pattern.CASE_INSENSITIVE));

        // 2. Race condition
        MULTI_ISSUE_PATTERNS.put("Race Condition",
                Pattern.compile("\\brace\\s*condition\\b|race-condition|data race|non[- ]deterministic|sometimes works sometimes", Pattern.CASE_INSENSITIVE));

        // 3. ConcurrentModificationException
        MULTI_ISSUE_PATTERNS.put("ConcurrentModificationException",
                Pattern.compile("ConcurrentModificationException", Pattern.CASE_INSENSITIVE));

        // 4. IllegalMonitorStateException
        MULTI_ISSUE_PATTERNS.put("IllegalMonitorStateException",
                Pattern.compile("IllegalMonitorStateException", Pattern.CASE_INSENSITIVE));

        // 5. InterruptedException misuse
        MULTI_ISSUE_PATTERNS.put("InterruptedException Misuse",
                Pattern.compile("InterruptedException(?!.*(catch.*interrupt|rethrow))", Pattern.CASE_INSENSITIVE));

        // 6. Thread starvation
        MULTI_ISSUE_PATTERNS.put("Thread Starvation",
                Pattern.compile("thread starvation|starv(ed|ation)|no threads available|all threads are busy", Pattern.CASE_INSENSITIVE));

        // 7. Busy waiting / spin
        MULTI_ISSUE_PATTERNS.put("Busy Waiting / Spin",
                Pattern.compile("busy wait|busy-wait|spin loop|while ?\\(true\\).*sleep\\(|100% cpu", Pattern.CASE_INSENSITIVE | Pattern.DOTALL));

        // 8. Incorrect synchronized usage
        MULTI_ISSUE_PATTERNS.put("Incorrect Synchronized Usage",
                Pattern.compile("wrong monitor|synchronized (this|method) .*deadlock|synchronize(d)? on (string|integer|class)", Pattern.CASE_INSENSITIVE));

        // 9. ReentrantLock / Lock misuse
        MULTI_ISSUE_PATTERNS.put("ReentrantLock Misuse",
                Pattern.compile("ReentrantLock|lock\\(\\).*finally\\s*\\{|lock\\(\\).*unlock\\(\\)", Pattern.CASE_INSENSITIVE));

        // 10. Thread pool misconfiguration
        MULTI_ISSUE_PATTERNS.put("Thread Pool Misconfiguration",
                Pattern.compile("ThreadPoolExecutor|ExecutorService|Executors\\.(newFixedThreadPool|newCachedThreadPool|newSingleThreadExecutor)", Pattern.CASE_INSENSITIVE));

        // 11. Blocking operations in thread pool
        MULTI_ISSUE_PATTERNS.put("Blocking Operations in Thread Pool",
                Pattern.compile("ExecutorService.*(get\\(|join\\(|sleep\\()", Pattern.CASE_INSENSITIVE | Pattern.DOTALL));

        // 12. Future / Callable misuse
        MULTI_ISSUE_PATTERNS.put("Future/Callable Misuse",
                Pattern.compile("Future(?!Task)|Callable|FutureTask|get\\(\\).*blocks|get\\(\\).*never returns", Pattern.CASE_INSENSITIVE | Pattern.DOTALL));

        // 13. ExecutorService not shutdown
        MULTI_ISSUE_PATTERNS.put("ExecutorService Not Shutdown",
                Pattern.compile("ExecutorService(?!.*shutdown)|never shutdown executor|thread pool.*never terminated", Pattern.CASE_INSENSITIVE | Pattern.DOTALL));

        // 14. Volatile misuse
        MULTI_ISSUE_PATTERNS.put("Volatile Misuse",
                Pattern.compile("\\bvolatile\\b(?!.*(visibility|happens-before|memory))", Pattern.CASE_INSENSITIVE));

        // 15. Atomic classes misuse
        MULTI_ISSUE_PATTERNS.put("Atomic Classes Misuse",
                Pattern.compile("Atomic(Integer|Long|Reference)(?!.*(compareAndSet|getAndIncrement))", Pattern.CASE_INSENSITIVE));

        // 16. ThreadLocal leaks
        MULTI_ISSUE_PATTERNS.put("ThreadLocal Leak",
                Pattern.compile("ThreadLocal.*(leak|memory leak|not removed|remove\\(\\) missing)", Pattern.CASE_INSENSITIVE));

        // 17. Using sleep instead of proper sync
        MULTI_ISSUE_PATTERNS.put("Using sleep Instead of Sync",
                Pattern.compile("Thread\\.sleep\\(\\).*wait\\(|sleep\\(\\).*synchroniz", Pattern.CASE_INSENSITIVE | Pattern.DOTALL));

        // 18. Non-thread-safe collection shared between threads
        MULTI_ISSUE_PATTERNS.put("Non-thread-safe Collections",
                Pattern.compile("(ArrayList|HashMap|HashSet).*shared between threads|ConcurrentModificationException", Pattern.CASE_INSENSITIVE));

        // 19. UI thread violations (Swing / Android)
        MULTI_ISSUE_PATTERNS.put("UI Thread Violations",
                Pattern.compile("(SwingUtilities\\.invokeLater|EventQueue\\.invokeLater|runOnUiThread|NetworkOnMainThreadException|android\\.os\\.NetworkOnMainThreadException)", Pattern.CASE_INSENSITIVE));

        // 20. Double-checked locking without volatile
        MULTI_ISSUE_PATTERNS.put("Double-Checked Locking Bug",
                Pattern.compile("double[- ]checked locking|if \\(instance == null\\).*synchronized.*if \\(instance == null\\)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL));

        // 21. Unsafe publication
        MULTI_ISSUE_PATTERNS.put("Unsafe Publication",
                Pattern.compile("published without synchronization|escape this from constructor|partially constructed", Pattern.CASE_INSENSITIVE));

        // 22. Deprecated thread control methods
        MULTI_ISSUE_PATTERNS.put("Deprecated Thread Control (stop/suspend)",
                Pattern.compile("\\.stop\\(\\)|\\.suspend\\(\\)|\\.resume\\(\\)", Pattern.CASE_INSENSITIVE));

        // 23. Wait/notify misuse
        MULTI_ISSUE_PATTERNS.put("Wait/Notify Misuse",
                Pattern.compile("IllegalMonitorStateException.*wait|notify called outside synchronized|wait\\(\\) without loop", Pattern.CASE_INSENSITIVE));

        // 24. Nested locks / multiple lock ordering deadlock
        MULTI_ISSUE_PATTERNS.put("Nested Locks / Lock Ordering",
                Pattern.compile("acquire.*lockA.*then.*lockB|acquire.*lockB.*then.*lockA|lock ordering", Pattern.CASE_INSENSITIVE | Pattern.DOTALL));

        // 25. Incorrect Thread Creation / Lifecycle Management
        MULTI_ISSUE_PATTERNS.put("Incorrect Thread Creation/Lifecycle",
                Pattern.compile(
                        // 生命周期 & 状态
                        "thread (lifecycle|life cycle)|" +
                                "thread state (NEW|RUNNABLE|BLOCKED|WAITING|TIMED_WAITING|TERMINATED)|" +
                                "IllegalThreadStateException|" +
                                // 重复 start / 未 start
                                "start\\(\\) .* already started|" +
                                "cannot restart thread|" +
                                "start\\(\\) .* more than once|" +
                                "thread never started|thread is not started|" +
                                // run() vs start()
                                "Thread\\s*\\.run\\(\\) instead of start\\(\\)|" +
                                "calling run\\(\\) directly on thread|" +
                                // 线程数量过多
                                "creating too many threads|" +
                                "unbounded number of threads|" +
                                "one thread per request",
                        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
                ));

        // 26. Shared Mutable State Without Proper Synchronization
        MULTI_ISSUE_PATTERNS.put("Shared Mutable State Without Synchronization",
                Pattern.compile(
                        "shared variable(s)? between threads(?!.*(Atomic|volatile))|" +
                                "shared state without (lock|synchronization)|" +
                                "multiple threads writing to the same variable|" +
                                "concurrent write without synchroni[sz]e",
                        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
                ));

        // 27. Singleton / Initialization / Double-Checked Locking Issues
        MULTI_ISSUE_PATTERNS.put("Singleton / Initialization Concurrency Issues",
                Pattern.compile(
                        "lazy singleton.*(thread[- ]safe|not thread[- ]safe)|" +
                                "double[- ]checked locking|" +
                                "double[- ]checked locking.*without volatile|" +
                                "getInstance\\(\\).*synchronized.*performance|" +
                                "race condition.*(initialization|startup|shutdown|lifecycle|order(ing)?)",
                        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
                ));

        // 28. Liveness / Progress / Hanging Threads
        MULTI_ISSUE_PATTERNS.put("Liveness / Progress / Hanging Threads",
                Pattern.compile(
                        "thread hangs forever|" +
                                "thread never completes|" +
                                "program (sometimes )?never finishes|" +
                                "application (never|does not) exit|" +
                                "threads? stuck in (WAITING|BLOCKED) state|" +
                                "all threads are waiting|" +
                                "no progress is being made",
                        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
                ));

        // 29. Producer-Consumer / BlockingQueue Issues
        MULTI_ISSUE_PATTERNS.put("Producer-Consumer / BlockingQueue Issues",
                Pattern.compile(
                        // producer-consumer 描述
                        "producer[- ]consumer|" +
                                "producer consumer problem|" +
                                "consumer threads? not receiving items|" +
                                "producer faster than consumer|" +
                                "consumer faster than producer|" +
                                // BlockingQueue 问题
                                "BlockingQueue|" +
                                "LinkedBlockingQueue|ArrayBlockingQueue|SynchronousQueue|" +
                                "take\\(\\).*never returns|" +
                                "offer\\(\\).*times out|" +
                                "queue is always empty in consumer|" +
                                "queue is always full in producer",
                        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
                ));

        // 30. ReadWriteLock / Lock Ordering with Read-Write Locks
        MULTI_ISSUE_PATTERNS.put("ReadWriteLock / Lock Ordering Issues",
                Pattern.compile(
                        "ReadWriteLock|ReentrantReadWriteLock|" +
                                "upgrade read lock to write lock|" +
                                "downgrade write lock to read lock|" +
                                "deadlock with readwritelock",
                        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
                ));

        // 31. Scheduled Tasks / Timer Misuse
        MULTI_ISSUE_PATTERNS.put("Scheduled Task / Timer Misuse",
                Pattern.compile(
                        "ScheduledExecutorService|scheduleAtFixedRate|scheduleWithFixedDelay|" +
                                "java\\.util\\.Timer|TimerTask|" +
                                "task executed multiple times|" +
                                "scheduled task never runs|" +
                                "timer thread has been terminated|" +
                                "timer already cancelled",
                        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
                ));

        // 32. ForkJoinPool / Parallelism Misuse
        MULTI_ISSUE_PATTERNS.put("ForkJoin / Parallelism Misuse",
                Pattern.compile(
                        "ForkJoinPool|RecursiveTask|RecursiveAction|" +
                                "commonPool\\(\\)|" +
                                "too many tasks submitted to forkjoin|" +
                                "forkjoin.*starvation|" +
                                "parallelStream\\(\\)|\\.parallel\\(\\)|" +
                                "ForkJoinPool\\.commonPool\\(\\).*parallel stream|" +
                                "non[- ]thread[- ]safe collector|" +
                                "modifying shared state in parallel stream",
                        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
                ));

        // 33. CompletableFuture / Async Chaining Issues
        MULTI_ISSUE_PATTERNS.put("CompletableFuture / Async Chaining Issues",
                Pattern.compile(
                        "CompletableFuture|" +
                                "thenApplyAsync|thenComposeAsync|thenAcceptAsync|" +
                                "thenRunAsync|" +
                                "blocking get\\(\\) on CompletableFuture|" +
                                "CompletableFuture.*never completes|" +
                                "deadlock with CompletableFuture|" +
                                "callback not executed|" +
                                "chained future.*not triggered",
                        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
                ));

        // 34. Synchronization on Wrong Object / Escaping This
        MULTI_ISSUE_PATTERNS.put("Synchronization on Wrong Object / Escaping This",
                Pattern.compile(
                        "synchroniz(e|ed) on this in constructor|" +
                                "synchroniz(e|ed) on (a )?local variable|" +
                                "synchroniz(e|ed) on mutable field|" +
                                "escaping this.*from constructor|" +
                                "published without synchronization",
                        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
                ));

        // 35. Condition / Wait-Set Misuse with Lock
        MULTI_ISSUE_PATTERNS.put("Condition / Wait-Set Misuse",
                Pattern.compile(
                        "Condition\\s+await\\(\\)|Condition\\s+signal(All)?\\(\\)|" +
                                "await\\(\\) without loop|" +
                                "signal\\(\\) without lock|" +
                                "signalAll\\(\\) without lock",
                        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
                ));

        // 36. UI / Main Thread Blocking (合并 UI 问题)
        MULTI_ISSUE_PATTERNS.put("UI / Main Thread Blocking",
                Pattern.compile(
                        "UI thread blocked|" +
                                "main thread is blocked|" +
                                "do not block the main thread|" +
                                "long running operation on main thread|" +
                                "NetworkOnMainThreadException|" +
                                "android\\.os\\.NetworkOnMainThreadException|" +
                                "SwingUtilities\\.invokeLater|EventQueue\\.invokeLater|" +
                                "runOnUiThread",
                        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
                ));

        // 37. Incorrect Interruption Handling
        MULTI_ISSUE_PATTERNS.put("Incorrect Interruption Handling",
                Pattern.compile(
                        "interrupted flag cleared|" +
                                "swallow InterruptedException|" +
                                "ignoring InterruptedException|" +
                                "Thread\\.interrupted\\(\\) called and ignored|" +
                                "InterruptedException(?!.*(catch.*interrupt|rethrow|restore))",
                        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
                ));
    }

    // 2. 文本匹配 / 抽取用 regex（用在 question/answer 正文上）
    private static final Pattern TOKEN_DEADLOCK =
            Pattern.compile("\\bdeadlock\\b|\"dead lock\"|blocked waiting for|threads? blocked", Pattern.CASE_INSENSITIVE);
    private static final Pattern TOKEN_RACE =
            Pattern.compile("\\brace\\s*condition\\b|race-condition|data race|thread[- ]safety", Pattern.CASE_INSENSITIVE);
    private static final Pattern TOKEN_THREAD_POOL =
            Pattern.compile("\\bThreadPoolExecutor\\b|\\bExecutorService\\b|\\bExecutors\\b|thread pool|FixedThreadPool|newWorkStealingPool", Pattern.CASE_INSENSITIVE);
    private static final Pattern TOKEN_FUTURE =
            Pattern.compile("\\bFuture\\b|\\bCallable\\b|\\bCompletableFuture\\b|FutureTask", Pattern.CASE_INSENSITIVE);
    private static final Pattern TOKEN_VOLATILE =
            Pattern.compile("\\bvolatile\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern TOKEN_ATOMIC =
            Pattern.compile("\\bAtomic(Integer|Long|Reference)\\b|\\batomic\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern TOKEN_SYNC =
            Pattern.compile("\\bsynchronized\\b|monitor|wait\\(\\)|notify(All)?\\(\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern TOKEN_LOCK =
            Pattern.compile("\\bReentrantLock\\b|\\bLock\\b|lock ordering|ReadWriteLock|StampedLock", Pattern.CASE_INSENSITIVE);
    private static final Pattern TOKEN_THREADLOCAL =
            Pattern.compile("\\bThreadLocal\\b", Pattern.CASE_INSENSITIVE);

    // code/exception extraction patterns
    private static final Pattern CODE_BLOCK_BACKTICK =
            Pattern.compile("```(?:java)?\\s*([\\s\\S]*?)\\s*```", Pattern.CASE_INSENSITIVE);
    private static final Pattern CODE_BLOCK_PRE =
            Pattern.compile("<pre[^>]*>\\s*<code[^>]*>([\\s\\S]*?)</code>\\s*</pre>", Pattern.CASE_INSENSITIVE);
    private static final Pattern INLINE_CODE =
            Pattern.compile("<code[^>]*>([\\s\\S]*?)</code>", Pattern.CASE_INSENSITIVE);

    private static final Pattern EXCEPTION_NAME =
            Pattern.compile("(?:java\\.lang\\.)?([A-Z][A-Za-z0-9_]*Exception)\\b");
    private static final Pattern STACK_TRACE_LINE =
            Pattern.compile("(?m)^\\s*at\\s+[a-zA-Z0-9_.$]+\\.[a-zA-Z0-9_<>$]+\\([^)]*\\)\\s*$");
    private static final Pattern ERROR_MESSAGE =
            Pattern.compile("(?m)([A-Z][a-zA-Z0-9_.]+Exception: .*|Exception in thread \".*\": .*|Caused by: .*$)");

    // 否定词（避免 "no deadlock" 之类被当成真实问题）
    private static final Pattern NEGATION_WINDOW =
            Pattern.compile("\\b(no|not|never|none|avoid|avoiding|without|doesn't|don't|didn't|nor)\\b", Pattern.CASE_INSENSITIVE);

    public List<Object[]> findMultithreadingIssuesByContent(int limit, Long startEpoch, Long endEpoch) {
        final int pageSize = 5000;
        final int maxCandidates = 100000;
        int processed = 0;
        int offset = 0;

        // 对每条 thread（title + question body + answer body ）构建 IssueEvidence 实例
        Map<Long, IssueEvidence> perQuestion = new LinkedHashMap<>();

        while (processed < maxCandidates) {
            StringBuilder sql = new StringBuilder();
            sql.append("""
                SELECT q.question_id,
                       q.title,
                       q.body AS question_body,
                       a.body AS answer_body,
                       q.score AS q_score,
                       q.view_count AS q_view_count,
                       q.answer_count AS q_answer_count
                FROM questions q
                LEFT JOIN answers a ON q.question_id = a.question_id
                WHERE (
                    q.title ILIKE '%thread%' OR q.body ILIKE '%thread%' OR
                    q.title ILIKE '%concurren%' OR q.body ILIKE '%concurren%' OR
                    q.title ILIKE '%synchroni%' OR q.body ILIKE '%synchroni%' OR
                    q.title ILIKE '%deadlock%' OR q.body ILIKE '%deadlock%' OR
                    a.body ILIKE '%thread%' OR a.body ILIKE '%synchroni%' OR
                    a.body ILIKE '%deadlock%' OR a.body ILIKE '%ConcurrentModificationException%' OR
                    a.body ILIKE '%IllegalMonitorStateException%' OR a.body ILIKE '%InterruptedException%'
                )
                """);

            List<Object> params = new ArrayList<>();
            if (startEpoch != null) {
                sql.append(" AND q.creation_date >= ?");
                params.add(startEpoch);
            }
            if (endEpoch != null) {
                sql.append(" AND q.creation_date <= ?");
                params.add(endEpoch);
            }

            sql.append(" ORDER BY q.creation_date DESC");
            sql.append(" LIMIT ? OFFSET ?");

            params.add(pageSize);
            params.add(offset);

            SqlRowSet rs = params.isEmpty()
                    ? jdbcTemplate.queryForRowSet(sql.toString())
                    : jdbcTemplate.queryForRowSet(sql.toString(), params.toArray());

            int rowsThisPage = 0;
            while (rs.next()) {
                rowsThisPage++;
                processed++;

                long qid = rs.getLong("question_id");
                String title = rs.getString("title");
                String qbody = rs.getString("question_body");
                String abody = rs.getString("answer_body");
                int qScore = rs.getInt("q_score");
                int qView = rs.getInt("q_view_count");
                int qAnswerCount = rs.getInt("q_answer_count");

                String combined = joinNonNull("\n", title, qbody, abody);
                if (combined == null || combined.isEmpty()) continue;

                IssueEvidence ev = perQuestion.computeIfAbsent(qid, IssueEvidence::new);
                ev.title = ev.title == null ? title : ev.title;
                ev.maxScore = Math.max(ev.maxScore, qScore);
                ev.viewCount = qView;
                ev.answerCount = qAnswerCount;

                // 1. 从正文中抽取代码、异常、并发 token
                extractAndAddCodeSamples(combined, ev);
                extractAndAddExceptions(combined, ev);
                extractConcurrencyTokens(combined, ev);
                matchIssuePatterns(combined, ev);

                // 2. 代表性示例评分
                int candidateScore = 0;
                if (ev.hasCode) candidateScore += 4;
                if (qAnswerCount > 0) candidateScore += 2;
                candidateScore += Math.min(5, Math.max(0, qScore / 5));

                if (ev.exampleQuestionId == null || candidateScore > ev.exampleScore) {
                    ev.exampleQuestionId = qid;
                    ev.exampleTitle = title;
                    ev.exampleScore = candidateScore;
                }
            }

            if (rowsThisPage == 0) break;
            offset += rowsThisPage;
            if (rowsThisPage < pageSize) break;
        }

        // 2. 基于 evidence + 匹配到的 issueKeys 归类到 pitfall 类型
        Map<String, IssueStat> aggByType = new LinkedHashMap<>();
        for (IssueEvidence ev : perQuestion.values()) {
            String issueType = classifyFromEvidence(ev.issueKeys, ev.exceptions, ev.tokens);
            if (issueType == null || issueType.equals("Other Concurrency Issues")) {
                continue;
            }
            IssueStat st = aggByType.computeIfAbsent(issueType, IssueStat::new);
            if (st.addQuestionId(ev.questionId)) {
                st.sumScore += ev.maxScore;
                st.sumViewCount += ev.viewCount;
                st.sumAnswerCount += ev.answerCount;
            }
            if (ev.exampleQuestionId != null) {
                int candidateScore = ev.exampleScore;
                if (st.exampleQuestionId == null || candidateScore > st.exampleScore) {
                    st.exampleQuestionId = ev.exampleQuestionId;
                    st.exampleTitle = ev.exampleTitle;
                    st.exampleScore = candidateScore;
                }
            }
            st.codeSamples.addAll(ev.codeSamples);
            st.exceptions.addAll(ev.exceptions);
        }

        // 3. 排序 + 输出
        List<IssueStat> stats = new ArrayList<>(aggByType.values());
        stats.sort(Comparator.comparingInt(IssueStat::getFrequency).reversed());

        List<Object[]> out = new ArrayList<>();
        int cnt = 0;
        for (IssueStat st : stats) {
            if (cnt++ >= limit) break;
            int freq = st.getFrequency();
            double avgScore = freq > 0 ? (double) st.sumScore / freq : 0.0;
            double avgView = freq > 0 ? (double) st.sumViewCount / freq : 0.0;
            double avgAnswer = freq > 0 ? (double) st.sumAnswerCount / freq : 0.0;
            String codeJoined = String.join(" ||| ", st.codeSamples);
            String exceptionsJoined = String.join(", ", st.exceptions);

            out.add(new Object[] {
                    st.issueType,
                    freq,
                    avgScore,
                    avgView,
                    avgAnswer,
                    st.exampleQuestionId,
                    st.exampleTitle,
                    codeJoined,
                    exceptionsJoined
            });
        }
        return out;
    }

    private static String joinNonNull(String sep, String... parts) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String p : parts) {
            if (p == null || p.isEmpty()) continue;
            if (!first) sb.append(sep);
            sb.append(p);
            first = false;
        }
        return sb.toString();
    }

    private static boolean isNegatedAround(String text, int matchStartIndex) {
        if (text == null) return false;
        int lookback = 120;
        int start = Math.max(0, matchStartIndex - lookback);
        String context = text.substring(start, Math.max(start, matchStartIndex));
        Matcher neg = NEGATION_WINDOW.matcher(context);
        return neg.find();
    }

    private static void addTokensFromPattern(String text, Pattern p, IssueEvidence ev, String logicalToken) {
        if (text == null) return;
        Matcher m = p.matcher(text);
        while (m.find()) {
            if (!isNegatedAround(text, m.start())) {
                ev.tokens.add(logicalToken);
            }
        }
    }

    private static void extractConcurrencyTokens(String text, IssueEvidence ev) {
        addTokensFromPattern(text, TOKEN_DEADLOCK, ev, "deadlock");
        addTokensFromPattern(text, TOKEN_RACE, ev, "race-condition");
        addTokensFromPattern(text, TOKEN_THREAD_POOL, ev, "thread-pool");
        addTokensFromPattern(text, TOKEN_FUTURE, ev, "future-callable");
        addTokensFromPattern(text, TOKEN_VOLATILE, ev, "volatile");
        addTokensFromPattern(text, TOKEN_ATOMIC, ev, "atomic");
        addTokensFromPattern(text, TOKEN_SYNC, ev, "synchronized");
        addTokensFromPattern(text, TOKEN_LOCK, ev, "lock");
        addTokensFromPattern(text, TOKEN_THREADLOCAL, ev, "threadlocal");
    }

    private static void extractAndAddCodeSamples(String text, IssueEvidence ev) {
        if (text == null) return;

        Matcher m = CODE_BLOCK_BACKTICK.matcher(text);
        while (m.find()) {
            String code = m.group(1).trim();
            if (!code.isEmpty()) {
                ev.hasCode = true;
                ev.codeSamples.add(truncateSafe(code, 4000));
            }
        }

        m = CODE_BLOCK_PRE.matcher(text);
        while (m.find()) {
            String code = m.group(1).trim();
            if (!code.isEmpty()) {
                ev.hasCode = true;
                ev.codeSamples.add(truncateSafe(code, 4000));
            }
        }

        Matcher inline = INLINE_CODE.matcher(text);
        while (inline.find()) {
            String code = inline.group(1).trim();
            if (!code.isEmpty()) {
                ev.hasCode = true;
                ev.codeSamples.add(truncateSafe(code, 2000));
            }
        }

        if (!ev.hasCode &&
                (text.contains("public class ") ||
                        text.contains("public static void main") ||
                        text.contains("System.out.println("))) {
            ev.hasCode = true;
        }
    }

    private static void extractAndAddExceptions(String text, IssueEvidence ev) {
        if (text == null) return;

        Matcher m = EXCEPTION_NAME.matcher(text);
        while (m.find()) {
            String ex = m.group(1).trim();
            if (!ex.isEmpty()) {
                ev.exceptions.add(ex);
                ev.tokens.add(ex.toLowerCase());
            }
        }

        Matcher em = ERROR_MESSAGE.matcher(text);
        while (em.find()) {
            String line = em.group(1).trim();
            if (!line.isEmpty()) {
                Matcher en = EXCEPTION_NAME.matcher(line);
                if (en.find()) {
                    String ex = en.group(1).trim();
                    if (!ex.isEmpty()) {
                        ev.exceptions.add(ex);
                        ev.tokens.add(ex.toLowerCase());
                    }
                } else {
                    ev.exceptions.add(line);
                }
            }
        }

        Matcher st = STACK_TRACE_LINE.matcher(text);
        if (st.find()) {
            ev.exceptions.add("StackTrace");
        }
    }

    private static String truncateSafe(String s, int max) {
        if (s == null) return "";
        if (s.length() <= max) return s;
        return s.substring(0, max - 1) + "…";
    }

    // 使用 MULTI_ISSUE_PATTERNS 在完整文本上匹配，记录命中的 issue keys
    private static void matchIssuePatterns(String text, IssueEvidence ev) {
        if (text == null) return;
        for (Map.Entry<String, Pattern> e : MULTI_ISSUE_PATTERNS.entrySet()) {
            Matcher m = e.getValue().matcher(text);
            if (m.find()) {
                if (!isNegatedAround(text, m.start())) {
                    ev.issueKeys.add(e.getKey());
                }
            }
        }
    }

    // 根据匹配到的 issueKeys + 抽到的异常名/并发 token，确定最终 issueType.

    private static String classifyFromEvidence(Set<String> issueKeys,
                                               Set<String> exceptions,
                                               Set<String> tokens) {

        // 1. 如果某些具体 issue key 直接匹配到了，就优先使用
        if (!issueKeys.isEmpty()) {
            // 可以保持顺序（MULTI_ISSUE_PATTERNS 是 LinkedHashMap）
            // 对于一个 question 命中多个 issue 的情况，选第一优先的
            return issueKeys.iterator().next();
        }

        // 2. 没有命中具体 key 时，用异常名做粗分类
        for (String ex : exceptions) {
            String lower = ex.toLowerCase();
            if (lower.contains("concurrentmodificationexception")) {
                return "ConcurrentModificationException";
            }
            if (lower.contains("illegalmonitorstateexception")) {
                return "IllegalMonitorStateException";
            }
            if (lower.contains("interruptedexception")) {
                return "InterruptedException";
            }
        }

        // 3. 再根据 tokens 做 fallback 分类
        if (tokens.contains("deadlock")) return "Deadlock";
        if (tokens.contains("race-condition")) return "Race Condition";

        if (tokens.contains("thread-pool")) return "Thread Pool / Executor";
        if (tokens.contains("future-callable")) return "Future/Callable";

        if (tokens.contains("volatile") && tokens.contains("atomic")) {
            return "Volatile / Atomic Operations";
        }
        if (tokens.contains("volatile")) return "Volatile Variables";
        if (tokens.contains("atomic")) return "Atomic Operations";

        if (tokens.contains("lock") && tokens.contains("synchronized")) {
            return "Locking + Synchronization";
        }
        if (tokens.contains("synchronized")) return "Synchronization (synchronized)";
        if (tokens.contains("lock")) return "Locking (ReentrantLock/Lock)";

        if (tokens.contains("threadlocal")) return "ThreadLocal";

        if (!exceptions.isEmpty() || !tokens.isEmpty()) {
            return "Other Concurrency Issues";
        }
        return null;
    }

    // 单个 question 的 evidence
    private static class IssueEvidence {
        final long questionId;
        String title;
        int maxScore = Integer.MIN_VALUE;
        int viewCount = 0;
        int answerCount = 0;

        boolean hasCode = false;
        LinkedHashSet<String> codeSamples = new LinkedHashSet<>();
        LinkedHashSet<String> exceptions = new LinkedHashSet<>();
        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        LinkedHashSet<String> issueKeys = new LinkedHashSet<>(); // 命中的具体问题 key

        Long exampleQuestionId = null;
        String exampleTitle = null;
        int exampleScore = 0;

        IssueEvidence(long questionId) {
            this.questionId = questionId;
        }
    }

    // 聚合后的统计：issueType -> metrics
    private static class IssueStat {
        final String issueType;
        final Set<Long> questionIds = new LinkedHashSet<>();
        long sumScore = 0;
        long sumViewCount = 0;
        long sumAnswerCount = 0;

        String exampleTitle = null;
        Long exampleQuestionId = null;
        int exampleScore = 0;

        LinkedHashSet<String> codeSamples = new LinkedHashSet<>();
        LinkedHashSet<String> exceptions = new LinkedHashSet<>();

        IssueStat(String issueType) {
            this.issueType = issueType;
        }

        boolean addQuestionId(long qid) {
            return questionIds.add(qid);
        }

        int getFrequency() {
            return questionIds.size();
        }
    }
}