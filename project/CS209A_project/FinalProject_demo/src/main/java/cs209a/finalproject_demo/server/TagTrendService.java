package cs209a.finalproject_demo.server;

import cs209a.finalproject_demo.object.TagTrend_c;
import cs209a.finalproject_demo.object.TagTrend_n;
import cs209a.finalproject_demo.object.TagTrend_num;
import cs209a.finalproject_demo.repository.TagTrendRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TagTrendService {

    @Autowired
    private TagTrendRepository tagTrendRepository;

    public List<TagTrend_c> getTagTrends(String tag, int years) {
        Calendar cal = Calendar.getInstance();
        int currentYear = cal.get(Calendar.YEAR);
        int startYear = currentYear - years + 1;

        List<Object[]> results = tagTrendRepository.getTagTrendsByTag(tag, startYear);

        Map<Integer, TagTrend_c> yearDataMap = results.stream()
                .map(this::convertToTagTrendC)
                .collect(Collectors.toMap(TagTrend_c::getYear, Function.identity()));

        List<TagTrend_c> allYearsData = new ArrayList<>();
        for (int year = startYear; year <= currentYear; year++) {
            if (yearDataMap.containsKey(year)) {
                allYearsData.add(yearDataMap.get(year));
            } else {
                // row shape: (tag, year, questionCount, avgScore, avgViewCount, avgAnswerCount, answerCount, commentCount)
                allYearsData.add(new TagTrend_c(
                        tag, year, 0, 0.0, 0.0, 0.0, 0, 0
                ));
            }
        }

        return allYearsData;
    }

    private TagTrend_c convertToTagTrendC(Object[] row) {
        // Expected row order from TagTrendRepository.getTagTrendsByTag:
        // 0: tag
        // 1: year
        // 2: question_count
        // 3: avg_score
        // 4: avg_view_count
        // 5: avg_answer_count
        // 6: answer_count
        // 7: comment_count
        return new TagTrend_c(
                (String) row[0],
                ((Number) row[1]).intValue(),
                ((Number) row[2]).intValue(),
                row[3] != null ? ((Number) row[3]).doubleValue() : 0.0,
                row[4] != null ? ((Number) row[4]).doubleValue() : 0.0,
                row[5] != null ? ((Number) row[5]).doubleValue() : 0.0,
                row.length > 6 && row[6] != null ? ((Number) row[6]).intValue() : 0,
                row.length > 7 && row[7] != null ? ((Number) row[7]).intValue() : 0
        );
    }

    public List<TagTrend_n> getTagMetrics(List<String> tags) {
        List<Object[]> results = tagTrendRepository.getTagMetrics(tags);

        return results.stream().map(row -> {
            // Expected row order from TagTrendRepository.getTagMetrics:
            // 0: tag
            // 1: avg_score
            // 2: avg_view_count
            // 3: avg_answer_count
            // 4: total_score
            // 5: question_count
            // 6: total_upvotes
            // 7: total_downvotes
            // 8: total_answers
            // 9: total_comments
            String tag = (String) row[0];
            Double avgScore = row[1] != null ? ((Number) row[1]).doubleValue() : 0.0;
            Double avgViewCount = row[2] != null ? ((Number) row[2]).doubleValue() : 0.0;
            Double avgAnswerCount = row[3] != null ? ((Number) row[3]).doubleValue() : 0.0;
            Long totalScore = row[4] != null ? ((Number) row[4]).longValue() : 0L;
            Long questionCount = row[5] != null ? ((Number) row[5]).longValue() : 0L;
            Long totalUpvotes = row[6] != null ? ((Number) row[6]).longValue() : 0L;
            Long totalDownvotes = row[7] != null ? ((Number) row[7]).longValue() : 0L;
            Long answerCount = row.length > 8 && row[8] != null ? ((Number) row[8]).longValue() : 0L;
            Long totalComments = row.length > 9 && row[9] != null ? ((Number) row[9]).longValue() : 0L;

            return new TagTrend_n(tag, avgScore, avgViewCount, avgAnswerCount,
                    totalScore, questionCount, totalUpvotes, totalDownvotes,
                    answerCount, totalComments);
        }).collect(Collectors.toList());
    }

    public List<TagTrend_num> getTagPopular(int limit) {
        if (limit <= 0) {
            limit = 20;
        }

        List<Object[]> popularTags = tagTrendRepository.getTagPopular(limit);

        return popularTags.stream()
                .map(row -> new TagTrend_num(
                        (String) row[0],  // tag
                        ((Number) row[1]).intValue()  // count
                ))
                .collect(Collectors.toList());
    }
}