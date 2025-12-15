package cs209a.finalproject_demo.server;

import cs209a.finalproject_demo.object.TagCooccurrence;
import cs209a.finalproject_demo.repository.TagCooccurrenceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class TagCooccurrenceService {
    @Autowired
    private TagCooccurrenceRepository tagCooccurrenceRepository;

    public List<TagCooccurrence> getTagPairs(int limit) {
//        limit = Math.min(limit, 100);

        List<Object[]> results = tagCooccurrenceRepository.findTagPairs(limit);

        return results.stream()
                .map(this::convertToTagCooccurrence)
                .collect(Collectors.toList());
    }

    private TagCooccurrence convertToTagCooccurrence(Object[] row) {
        List<String> tags = Arrays.asList(
                (String) row[0],
                (String) row[1]
        );

        int count = ((Number) row[2]).intValue();
        String exampleQuestion = null;
        Long exampleQuestionId = null;
        if (row.length > 3 && row[3] != null) exampleQuestion = (String) row[3];
        if (row.length > 4 && row[4] != null) exampleQuestionId = ((Number) row[4]).longValue();

        return new TagCooccurrence(
                tags,
                count,
                exampleQuestion,
                exampleQuestionId
        );
    }
}