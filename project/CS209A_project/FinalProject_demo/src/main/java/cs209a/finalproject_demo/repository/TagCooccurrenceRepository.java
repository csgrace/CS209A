package cs209a.finalproject_demo.repository;

import cs209a.finalproject_demo.unity.VirtualEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TagCooccurrenceRepository  extends JpaRepository<VirtualEntity, Long> {

    @Query(value = """
        SELECT p.tag1, p.tag2, p.frequency, q.title as example_question, p.example_question_id
        FROM (
          SELECT\s
            t1.tag as tag1,
            t2.tag as tag2,
            COUNT(DISTINCT t1.question_id) as frequency,
            MIN(t1.question_id) as example_question_id
          FROM question_tags t1
          JOIN question_tags t2 ON t1.question_id = t2.question_id\s
              AND t1.tag < t2.tag
          WHERE t1.tag != 'java' \s
                  AND t2.tag != 'java'\s
          GROUP BY t1.tag, t2.tag
          ORDER BY frequency DESC
          LIMIT :limit
        ) p
        LEFT JOIN questions q ON q.question_id = p.example_question_id
    """, nativeQuery = true)
    List<Object[]> findTagPairs(@Param("limit") int limit);

}
