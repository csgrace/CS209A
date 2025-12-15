package cs209a.finalproject_demo.object;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TagCooccurrence {
    private List<String> tags;
    private int count;
    private String exampleQuestion;
    private Long exampleQuestionId;
}