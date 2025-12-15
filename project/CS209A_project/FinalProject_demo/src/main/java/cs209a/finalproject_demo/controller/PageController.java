package cs209a.finalproject_demo.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {

    @GetMapping("/topic-trends")
    public String topicTrends() {
        return "topic-trends";
    }

    @GetMapping({ "/cooccurrence"})
    public String cooccurrence() {
        return "coocurrence";
    }

    @GetMapping("/concurrency-pitfalls")
    public String concurrencyPitfalls() {
        return "multithreading";
    }

    @GetMapping("/solvability")
    public String solvability() {
        return "solvability";
    }
}