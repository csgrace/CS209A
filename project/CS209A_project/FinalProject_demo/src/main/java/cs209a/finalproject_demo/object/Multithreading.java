package cs209a.finalproject_demo.object;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Multithreading {
    private String issueType;
    private String description;
    private int frequency;
    private double avgScore;
    private double avgViewCount;
    private double avgAnswerCount;
    private String exampleQuestion;
    private Long exampleQuestionId;
    private String exampleCode;
    private List<String> exceptions;

    private Double evidenceScore;
    private Map<String, Boolean> evidenceComponents; // keys: "title", "code", "exceptions", "answers"
}