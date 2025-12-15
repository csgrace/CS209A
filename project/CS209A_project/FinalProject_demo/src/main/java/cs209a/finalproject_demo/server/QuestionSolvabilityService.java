package cs209a.finalproject_demo.server;

import cs209a.finalproject_demo.object.SolvabilityComparison;
import cs209a.finalproject_demo.repository.QuestionSolvabilityRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 0: question_id
 * 1: title
 * 2: body
 * 3: score
 * 4: view_count
 * 5: answer_count
 * 6: creation_date
 * 7: reputation
 * 8: body_length
 * 9: title_length
 * 10: has_code_snippet
 * 11: tag_count
 * 12: category ('solvable'|'hard-to-solve'|'neutral')
 * 13: first_answer_ts (timestamp or null)
 * 14: first_answer_hours (double hours or null)
 */
@Service
public class QuestionSolvabilityService {

    @Autowired
    private QuestionSolvabilityRepository repository;

    private static final int DEFAULT_MIN_HIGH_SCORE = 5;
    private static final int DEFAULT_NO_ANSWER_DAYS = 30;

    // 1-问题长度（字符数）
    public Map<String, Object> getQuestionLengthComparison(int years, int limit, int maxHours) {
        List<Object[]> data = getCategorizedData(years, limit, maxHours);

        Map<String, List<Double>> lengthData = new HashMap<>();
        lengthData.put("solvable", new ArrayList<>());
        lengthData.put("hard-to-solve", new ArrayList<>());

        for (Object[] row : data) {
            try {
                String category = safeToString(row, 12);
                Number bodyLenNum = safeNumber(row, 8);
                if (category != null && bodyLenNum != null) {
                    List<Double> list = lengthData.get(category);
                    if (list != null) list.add(bodyLenNum.doubleValue());
                }
            } catch (Exception ignored) {}
        }

        Map<String,Object> out = calculateStatistics("questionlength_chars", lengthData);
        out.put("unit", "characters");
        return out;
    }

    // 2-用户声誉
    public Map<String, Object> getReputationComparison(int years, int limit, int maxHours) {
        List<Object[]> data = getCategorizedData(years, limit, maxHours);

        Map<String, List<Double>> reputationData = new HashMap<>();
        reputationData.put("solvable", new ArrayList<>());
        reputationData.put("hard-to-solve", new ArrayList<>());

        for (Object[] row : data) {
            try {
                String category = safeToString(row, 12);
                Number reputationNum = safeNumber(row, 7);
                if (category != null && reputationNum != null) {
                    List<Double> list = reputationData.get(category);
                    if (list != null) list.add(reputationNum.doubleValue());
                }
            } catch (Exception ignored) {}
        }

        Map<String,Object> out = calculateStatistics("reputation", reputationData);
        out.put("unit", "reputation points");
        return out;
    }

    // 3-代码片段（百分比统计）
    public Map<String, Object> getCodeSnippetComparison(int years, int limit, int maxHours) {
        List<Object[]> data = getCategorizedData(years, limit, maxHours);

        Map<String, Map<String, Integer>> codeData = new HashMap<>();
        codeData.put("solvable", new HashMap<>());
        codeData.put("hard-to-solve", new HashMap<>());

        for (Object[] row : data) {
            try {
                String category = safeToString(row, 12);
                Boolean hasCode = safeBoolean(row, 10);
                if (category != null && hasCode != null) {
                    Map<String,Integer> map = codeData.get(category);
                    if (map != null) {
                        String key = hasCode ? "have" : "no";
                        map.put(key, map.getOrDefault(key, 0) + 1);
                    }
                }
            } catch (Exception ignored) {}
        }

        return calculatePercentageStatistics("code_snippet", codeData);
    }

    // 4-标签数量
    public Map<String, Object> getTagCountComparison(int years, int limit, int maxHours) {
        List<Object[]> data = getCategorizedData(years, limit, maxHours);

        Map<String, List<Double>> tagData = new HashMap<>();
        tagData.put("solvable", new ArrayList<>());
        tagData.put("hard-to-solve", new ArrayList<>());

        for (Object[] row : data) {
            try {
                String category = safeToString(row, 12);
                Number tagCountNum = safeNumber(row, 11);
                if (category != null && tagCountNum != null) {
                    List<Double> list = tagData.get(category);
                    if (list != null) list.add(tagCountNum.doubleValue());
                }
            } catch (Exception ignored) {}
        }

        Map<String,Object> out = calculateStatistics("tag_count", tagData);
        out.put("unit", "tags per question");
        return out;
    }

    // 5-问题得分
    public Map<String, Object> getQuestionScoreComparison(int years, int limit, int maxHours) {
        List<Object[]> data = getCategorizedData(years, limit, maxHours);

        Map<String, List<Double>> scoreData = new HashMap<>();
        scoreData.put("solvable", new ArrayList<>());
        scoreData.put("hard-to-solve", new ArrayList<>());

        for (Object[] row : data) {
            try {
                String category = safeToString(row, 12);
                Number scoreNum = safeNumber(row, 3);
                if (category != null && scoreNum != null) {
                    List<Double> list = scoreData.get(category);
                    if (list != null) list.add(scoreNum.doubleValue());
                }
            } catch (Exception ignored) {}
        }

        Map<String,Object> out = calculateStatistics("question_score", scoreData);
        out.put("unit", "score");
        return out;
    }

    private List<Object[]> getCategorizedData(int years, int limit, int maxHours) {
        LocalDateTime startTime = LocalDateTime.now().minusYears(years);
        long startTimestamp = startTime.toEpochSecond(ZoneOffset.UTC);

        int minHighScore = DEFAULT_MIN_HIGH_SCORE;
        long noAnswerSeconds = (long) DEFAULT_NO_ANSWER_DAYS * 24L * 3600L;

        return repository.getCategorizedQuestions(startTimestamp, limit, maxHours, minHighScore, noAnswerSeconds);
    }

    // 汇总统计（count, avg, min, median, max）
    private Map<String, Object> calculateStatistics(String featureName, Map<String, List<Double>> data) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("feature", featureName);
        result.put("timestamp", System.currentTimeMillis());

        Map<String, Object> solvableStats = summarizeList(data.get("solvable"));
        Map<String, Object> hardStats = summarizeList(data.get("hard-to-solve"));

        result.put("solvable", solvableStats);
        result.put("hard-to-solve", hardStats);

        return result;
    }

    private Map<String,Object> summarizeList(List<Double> values) {
        Map<String,Object> stats = new LinkedHashMap<>();
        if (values == null || values.isEmpty()) {
            stats.put("count", 0);
            stats.put("avg", 0.0);
            stats.put("min", 0.0);
            stats.put("median", 0.0);
            stats.put("max", 0.0);
            return stats;
        }
        List<Double> copy = new ArrayList<>(values);
        Collections.sort(copy);
        int count = copy.size();
        double avg = copy.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double min = copy.get(0);
        double max = copy.get(count - 1);
        double median = (count % 2 == 0) ? (copy.get(count/2 - 1) + copy.get(count/2)) / 2.0 : copy.get(count/2);

        stats.put("count", count);
        stats.put("avg", avg);
        stats.put("min", min);
        stats.put("median", median);
        stats.put("max", max);
        return stats;
    }

    private Map<String, Object> calculatePercentageStatistics(String featureName, Map<String, Map<String, Integer>> data) {
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("feature", featureName);
        result.put("timestamp", System.currentTimeMillis());

        Map<String,Object> solvableStats = new LinkedHashMap<>();
        Map<String,Object> hardStats = new LinkedHashMap<>();

        Map<String,Integer> solvableData = data.getOrDefault("solvable", Collections.emptyMap());
        Map<String,Integer> hardData = data.getOrDefault("hard-to-solve", Collections.emptyMap());

        int solvableTotal = solvableData.values().stream().mapToInt(Integer::intValue).sum();
        for (Map.Entry<String,Integer> e : solvableData.entrySet()) {
            double pct = solvableTotal>0 ? e.getValue()*100.0/solvableTotal : 0.0;
            solvableStats.put(e.getKey()+"_count", e.getValue());
            solvableStats.put(e.getKey()+"_percentage", String.format("%.1f%%", pct));
        }
        solvableStats.put("total", solvableTotal);

        int hardTotal = hardData.values().stream().mapToInt(Integer::intValue).sum();
        for (Map.Entry<String,Integer> e : hardData.entrySet()) {
            double pct = hardTotal>0 ? e.getValue()*100.0/hardTotal : 0.0;
            hardStats.put(e.getKey()+"_count", e.getValue());
            hardStats.put(e.getKey()+"_percentage", String.format("%.1f%%", pct));
        }
        hardStats.put("total", hardTotal);

        result.put("solvable", solvableStats);
        result.put("hard-to-solve", hardStats);
        return result;
    }

    private String safeToString(Object[] row, int idx) {
        if (row==null || row.length<=idx) return null;
        return row[idx]==null?null:row[idx].toString();
    }

    private Number safeNumber(Object[] row, int idx) {
        if (row==null || row.length<=idx) return null;
        Object o = row[idx];
        if (o instanceof Number) return (Number)o;
        if (o instanceof String) {
            try { return Long.parseLong((String)o); } catch (Exception ignored){}
            try { return Double.parseDouble((String)o); } catch (Exception ignored){}
        }
        return null;
    }

    private Boolean safeBoolean(Object[] row, int idx) {
        if (row==null || row.length<=idx) return null;
        Object o = row[idx];
        if (o instanceof Boolean) return (Boolean)o;
        if (o instanceof Number) return ((Number)o).intValue()!=0;
        if (o instanceof String) {
            String s = ((String)o).toLowerCase();
            if (s.equals("true")||s.equals("1")) return true;
            if (s.equals("false")||s.equals("0")) return false;
        }
        return null;
    }
}