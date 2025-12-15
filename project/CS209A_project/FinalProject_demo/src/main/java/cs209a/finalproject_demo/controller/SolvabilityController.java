package cs209a.finalproject_demo.controller;

import cs209a.finalproject_demo.server.QuestionSolvabilityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/solvability")
// 问题正文长度: http://localhost:8081/api/solvability/questionlength?n=1000&years=10&maxHours=48
// 用户声誉比较: http://localhost:8081/api/solvability/reputation?n=1000&years=10&maxHours=48
// 是否包含代码片段: http://localhost:8081/api/solvability/code?n=1000&years=10&maxHours=48
// 标签数量比较: http://localhost:8081/api/solvability/tagcount?n=1000&years=10&maxHours=48
// 问题得分比较: http://localhost:8081/api/solvability/questionscore?n=1000&years=10&maxHours=48
// 返回所有特征: http://localhost:8081/api/solvability/all?n=1000&years=10&maxHours=48
public class SolvabilityController {

    @Autowired
    private QuestionSolvabilityService service;

    // 提取 group 内 count
    private int extractCount(Map<String,Object> featureResult, String groupKey) {
        if (featureResult == null) return 0;
        Object g = featureResult.get(groupKey);
        if (!(g instanceof Map)) return 0;
        Map<?,?> m = (Map<?,?>) g;
        // 优先尝试常见的字段名：count -> total -> sample_size -> have_count (退而求其总和)
        Object cnt = m.get("count");
        if (cnt instanceof Number) return ((Number) cnt).intValue();
        if (cnt != null) {
            try { return Integer.parseInt(String.valueOf(cnt)); } catch (Exception ignored) {}
        }
        Object tot = m.get("total");
        if (tot instanceof Number) return ((Number) tot).intValue();
        if (tot != null) {
            try { return Integer.parseInt(String.valueOf(tot)); } catch (Exception ignored) {}
        }
        // 如果只有 have_count / no_count，可求和
        Object have = m.get("have_count");
        Object no = m.get("no_count");
        int sum = 0;
        boolean found = false;
        if (have instanceof Number) { sum += ((Number) have).intValue(); found = true; }
        else if (have != null) {
            try { sum += Integer.parseInt(String.valueOf(have)); found = true; } catch (Exception ignored) {}
        }
        if (no instanceof Number) { sum += ((Number) no).intValue(); found = true; }
        else if (no != null) {
            try { sum += Integer.parseInt(String.valueOf(no)); found = true; } catch (Exception ignored) {}
        }
        if (found) return sum;
        return 0;
    }

    private Map<String,Object> wrap(String featureName, Map<String,Object> raw, int limit, int years, int maxHours) {
        Map<String,Object> out = new HashMap<>();
        Map<String,Object> meta = new HashMap<>();
        meta.put("feature", featureName);
        meta.put("limit", limit);
        meta.put("years", years);
        meta.put("maxHours", maxHours);
        meta.put("timestamp", System.currentTimeMillis());
        int solvableCount = extractCount(raw, "solvable");
        int hardCount = extractCount(raw, "hard-to-solve");
        meta.put("sample_solvable", solvableCount);
        meta.put("sample_hard_to_solve", hardCount);
        if (raw!=null && raw.get("unit")!=null) meta.put("unit", raw.get("unit"));
        out.put("meta", meta);
        out.put("data", raw);
        return out;
    }

    @GetMapping("/questionlength")
    // GET /api/solvability/questionlength
    public ResponseEntity<Map<String,Object>> getQuestionLengthComparison(
            @RequestParam(value="n", defaultValue="1000") int limit,
            @RequestParam(value="years", defaultValue="3") int years,
            @RequestParam(value="maxHours", defaultValue="48") int maxHours) {
        Map<String,Object> raw = service.getQuestionLengthComparison(years, limit, maxHours);
        return ResponseEntity.ok(wrap("question_length", raw, limit, years, maxHours));
    }

    @GetMapping("/reputation")
    // GET /api/solvability/reputation
    public ResponseEntity<Map<String,Object>> getReputationComparison(
            @RequestParam(value="n", defaultValue="1000") int limit,
            @RequestParam(value="years", defaultValue="3") int years,
            @RequestParam(value="maxHours", defaultValue="48") int maxHours) {
        Map<String,Object> raw = service.getReputationComparison(years, limit, maxHours);
        return ResponseEntity.ok(wrap("reputation", raw, limit, years, maxHours));
    }

    @GetMapping("/code")
    // GET /api/solvability/code
    public ResponseEntity<Map<String,Object>> getCodeSnippetComparison(
            @RequestParam(value="n", defaultValue="1000") int limit,
            @RequestParam(value="years", defaultValue="3") int years,
            @RequestParam(value="maxHours", defaultValue="48") int maxHours) {
        Map<String,Object> raw = service.getCodeSnippetComparison(years, limit, maxHours);
        return ResponseEntity.ok(wrap("code_snippet", raw, limit, years, maxHours));
    }

    @GetMapping("/tagcount")
    // GET /api/solvability/tagcount
    public ResponseEntity<Map<String,Object>> getTagCountComparison(
            @RequestParam(value="n", defaultValue="1000") int limit,
            @RequestParam(value="years", defaultValue="3") int years,
            @RequestParam(value="maxHours", defaultValue="48") int maxHours) {
        Map<String,Object> raw = service.getTagCountComparison(years, limit, maxHours);
        return ResponseEntity.ok(wrap("tag_count", raw, limit, years, maxHours));
    }

    @GetMapping("/questionscore")
    // GET /api/solvability/questionscore
    public ResponseEntity<Map<String,Object>> getQuestionScoreComparison(
            @RequestParam(value="n", defaultValue="1000") int limit,
            @RequestParam(value="years", defaultValue="3") int years,
            @RequestParam(value="maxHours", defaultValue="48") int maxHours) {
        Map<String,Object> raw = service.getQuestionScoreComparison(years, limit, maxHours);
        return ResponseEntity.ok(wrap("question_score", raw, limit, years, maxHours));
    }

    @GetMapping("/all")
    // GET /api/solvability/all
    public ResponseEntity<Map<String,Object>> getAllComparisons(
            @RequestParam(value="n", defaultValue="1000") int limit,
            @RequestParam(value="years", defaultValue="3") int years,
            @RequestParam(value="maxHours", defaultValue="48") int maxHours) {

        Map<String,Object> out = new HashMap<>();
        out.put("question_length", wrap("question_length", service.getQuestionLengthComparison(years, limit, maxHours), limit, years, maxHours));
        out.put("reputation", wrap("reputation", service.getReputationComparison(years, limit, maxHours), limit, years, maxHours));
        out.put("code_snippet", wrap("code_snippet", service.getCodeSnippetComparison(years, limit, maxHours), limit, years, maxHours));
        out.put("tag_count", wrap("tag_count", service.getTagCountComparison(years, limit, maxHours), limit, years, maxHours));
        out.put("question_score", wrap("question_score", service.getQuestionScoreComparison(years, limit, maxHours), limit, years, maxHours));

        Map<String,Object> meta = new HashMap<>();
        meta.put("limit", limit);
        meta.put("years", years);
        meta.put("maxHours", maxHours);
        meta.put("timestamp", System.currentTimeMillis());
        out.put("meta", meta);

        return ResponseEntity.ok(out);
    }
}