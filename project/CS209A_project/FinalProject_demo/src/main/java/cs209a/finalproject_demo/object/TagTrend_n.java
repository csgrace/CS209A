package cs209a.finalproject_demo.object;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TagTrend_n {
    private String tag;
    private double avgScore;
    private double avgViewCount;
    private double avgAnswerCount;
    private long totalScore;
    private long questionCount;
    private long totalUpvotes;
    private long totalDownvotes;
    private long answerCount;
    private long totalComments;
}