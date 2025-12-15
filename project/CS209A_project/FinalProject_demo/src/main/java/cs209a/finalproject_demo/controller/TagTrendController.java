package cs209a.finalproject_demo.controller;

import cs209a.finalproject_demo.object.TagTrend_num;
import cs209a.finalproject_demo.server.TagTrendService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import cs209a.finalproject_demo.object.TagTrend_c;
import cs209a.finalproject_demo.object.TagTrend_n;

@RestController
@RequestMapping("/api/tag-trends")
// 年度趋势： http://localhost:8081/api/tag-trends/year/tag?tag=java&years=10
// 指标汇总： http://localhost:8081/api/tag-trends/features?tags=java&tags=spring&tags=multithreading
// 热门标签： http://localhost:8081/api/tag-trends/popular-tags?limit=20
@CrossOrigin(origins = "*")
public class TagTrendController {

    @Autowired
    private TagTrendService tagTrendService;

    //GET /api/tag-trends/year/tag?tag=java&years=3
    @GetMapping("/year/tag")
    public ResponseEntity<List<TagTrend_c>> getTagTrendsByYear(
            @RequestParam String tag,
            @RequestParam(defaultValue = "3") int years) {

        if (tag == null || tag.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Collections.emptyList());
        }
        if (years <= 0) {
            years = 3;
        }

        List<TagTrend_c> trends = tagTrendService.getTagTrends(tag, years);
        return ResponseEntity.ok(trends);
    }

     //GET /api/tag-trends/metrics?tags=java&tags=spring&tags=multithreading
    @GetMapping("/features")
    public ResponseEntity<List<TagTrend_n>> getTagMetrics(
            @RequestParam List<String> tags) {

        List<TagTrend_n> metrics = tagTrendService.getTagMetrics(tags);
        return ResponseEntity.ok(metrics);
    }

    // GET /api/tag-trends/popular-tags?limit=10
    @GetMapping("/popular-tags")
    public ResponseEntity<List<TagTrend_num>> getPopularTags(
            @RequestParam(defaultValue = "20") int limit) {

        List<TagTrend_num> popularTags = tagTrendService.getTagPopular(limit);
        return ResponseEntity.ok(popularTags);
    }

}