package cs209a.finalproject_demo.repository;

import cs209a.finalproject_demo.unity.VirtualEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TagTrendRepository extends JpaRepository<VirtualEntity, Long> {

    @Query(value = """
        SELECT
          qt.tag,
          EXTRACT(YEAR FROM TO_TIMESTAMP(q.creation_date)) as year,
          COUNT(*) as question_count,
          AVG(q.score) as avg_score,
          AVG(q.view_count) as avg_view_count,
          AVG(q.answer_count) as avg_answer_count,
          -- answers associated with these questions in the same year
          SUM(
            (SELECT COUNT(*) FROM answers a WHERE a.question_id = q.question_id
              AND EXTRACT(YEAR FROM TO_TIMESTAMP(a.creation_date)) = EXTRACT(YEAR FROM TO_TIMESTAMP(q.creation_date)))
          ) as answer_count,
          -- comments: question_comments + answer_comments (answers joined)
          SUM(
            (SELECT COUNT(*) FROM question_comments qc WHERE qc.question_id = q.question_id
               AND EXTRACT(YEAR FROM TO_TIMESTAMP(qc.creation_date)) = EXTRACT(YEAR FROM TO_TIMESTAMP(q.creation_date)))
            +
            (SELECT COUNT(*) FROM answer_comments ac JOIN answers a2 ON ac.answer_id = a2.answer_id WHERE a2.question_id = q.question_id
               AND EXTRACT(YEAR FROM TO_TIMESTAMP(ac.creation_date)) = EXTRACT(YEAR FROM TO_TIMESTAMP(q.creation_date)))
          ) as comment_count
        FROM questions q
        JOIN question_tags qt ON q.question_id = qt.question_id
        WHERE qt.tag = :tag
          AND EXTRACT(YEAR FROM TO_TIMESTAMP(q.creation_date)) >= :startYear
        GROUP BY qt.tag, EXTRACT(YEAR FROM TO_TIMESTAMP(q.creation_date))
        ORDER BY year DESC
    """, nativeQuery = true)
    List<Object[]> getTagTrendsByTag(@Param("tag") String tag, @Param("startYear") int startYear);

    @Query(value = """
        SELECT
          qt.tag,
          AVG(q.score) as avg_score,
          AVG(q.view_count) as avg_view_count,
          AVG(q.answer_count) as avg_answer_count,
          SUM(q.score) as total_score,
          COUNT(*) as question_count,
          SUM(CASE WHEN q.score > 0 THEN q.score ELSE 0 END) as total_upvotes,
          SUM(CASE WHEN q.score < 0 THEN ABS(q.score) ELSE 0 END) as total_downvotes,
          -- total answers for this tag (join answers->questions->question_tags)
          (SELECT COUNT(*) FROM answers a JOIN question_tags qt2 ON a.question_id = qt2.question_id WHERE qt2.tag = qt.tag) as total_answers,
          -- total comments for this tag
          (
            (SELECT COUNT(*) FROM question_comments qc JOIN question_tags qt3 ON qc.question_id = qt3.question_id WHERE qt3.tag = qt.tag)
            +
            (SELECT COUNT(*) FROM answer_comments ac JOIN answers a2 ON ac.answer_id = a2.answer_id JOIN question_tags qt4 ON a2.question_id = qt4.question_id WHERE qt4.tag = qt.tag)
          ) as total_comments
        FROM questions q
        JOIN question_tags qt ON q.question_id = qt.question_id
        WHERE qt.tag IN :tags
        GROUP BY qt.tag
        ORDER BY question_count DESC
    """, nativeQuery = true)
    List<Object[]> getTagMetrics(@Param("tags") List<String> tags);

    @Query(value = """
    SELECT 
        tag,
        COUNT(*) as tag_count
    FROM question_tags
    GROUP BY tag
    ORDER BY tag_count DESC
    LIMIT :limit
""", nativeQuery = true)
    List<Object[]> getTagPopular(@Param("limit") int limit);
}