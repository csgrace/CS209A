package cs209a.finalproject_demo.controller;

import cs209a.finalproject_demo.object.TagCooccurrence;
import cs209a.finalproject_demo.server.TagCooccurrenceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/api/tag-cooccurrence")
//  http://localhost:8081/api/tag-cooccurrence/pair?n=20
public class TagCooccurrenceController {
    @Autowired
    private TagCooccurrenceService tagCooccurrenceService;

    //GET /api/tag-cooccurrence/pair?n=20
    @GetMapping("/pair")
    public ResponseEntity<List<TagCooccurrence>> getTagTriples(
            @RequestParam(value = "n", defaultValue = "15") int limit) {

        List<TagCooccurrence> results = tagCooccurrenceService.getTagPairs(limit);
        return ResponseEntity.ok(results);
    }

}