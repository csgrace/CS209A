package cs209a.finalproject_demo.controller;

import cs209a.finalproject_demo.object.Multithreading;
import cs209a.finalproject_demo.server.MultithreadingIssueService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/thread-analysis")
// http://localhost:8081/api/thread-analysis/issues?n=10&sort=frequency&startDate=2015-12-14&endDate=2025-12-14
public class MultithreadingController {

    @Autowired
    private MultithreadingIssueService multithreadingIssueService;

    // GET /api/thread-analysis/issues?n=15&sort=frequency&hasCode=true&hasException=true&startDate=2024-01-01&endDate=2024-12-31
    @GetMapping("/issues")
    public ResponseEntity<List<Multithreading>> getTopMultithreadingIssues(
            @RequestParam(value = "n", defaultValue = "15") int limit,
            @RequestParam(value = "sort", required = false) String sort,
            @RequestParam(value = "hasCode", required = false) String hasCode,
            @RequestParam(value = "hasException", required = false) String hasException,
            @RequestParam(value = "startDate", required = false) String startDate,
            @RequestParam(value = "endDate", required = false) String endDate
    ) {
        List<Multithreading> results = multithreadingIssueService.getTopMultithreadingIssues(
                limit, sort, hasCode, hasException, startDate, endDate);
        return ResponseEntity.ok(results);
    }

}