package cs209a.finalproject_demo.object;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TagTrend_c {
    private String tag;
    private int year;
    private int questionCount;
    private double avgScore;
    private double avgViewCount;
    private double avgAnswerCount;
    private int answerCount;
    private int commentCount;

}
