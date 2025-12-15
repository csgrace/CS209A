package cs209a.finalproject_demo.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import cs209a.finalproject_demo.unity.VirtualEntity;

import java.util.List;

@Repository
public interface QuestionSolvabilityRepository extends JpaRepository<VirtualEntity, Long> {

    @Query(value = """
        WITH first_answer AS (
          SELECT question_id, MIN(creation_date) AS first_answer_ts
          FROM answers
          GROUP BY question_id
        ),
        question_categories AS (
          SELECT
            q.question_id,
            q.title,
            q.body,
            q.score,
            q.view_count,
            q.answer_count,
            q.creation_date,
            u.reputation,
            LENGTH(q.body) AS body_length,
            LENGTH(q.title) AS title_length,
            (CASE WHEN q.body LIKE '%```%' OR q.body LIKE '%<code>%' THEN true ELSE false END) AS has_code_snippet,
            (SELECT COUNT(*) FROM question_tags qt WHERE qt.question_id = q.question_id) AS tag_count,

            EXTRACT(EPOCH FROM (
              (CASE
                 WHEN pg_typeof(q.creation_date)::text = 'bigint' THEN TO_TIMESTAMP(q.creation_date::double precision)::text
                 ELSE q.creation_date::text
               END)::timestamp
            )) AS creation_epoch,
            EXTRACT(EPOCH FROM (
              (CASE
                 WHEN pg_typeof(fa.first_answer_ts)::text = 'bigint' THEN TO_TIMESTAMP(fa.first_answer_ts::double precision)::text
                 ELSE fa.first_answer_ts::text
               END)::timestamp
            )) AS first_answer_epoch,
            EXTRACT(EPOCH FROM NOW()) AS now_epoch,

            (CASE
               WHEN EXISTS(SELECT 1 FROM answers a WHERE a.question_id = q.question_id AND a.is_accepted = true) THEN 'solvable'
               WHEN q.answer_count > 0 AND EXISTS(SELECT 1 FROM answers a WHERE a.question_id = q.question_id AND a.score > :minHighScore) THEN 'solvable'
               WHEN fa.first_answer_ts IS NOT NULL
                    AND (
                      (
                        EXTRACT(EPOCH FROM (
                          (CASE WHEN pg_typeof(fa.first_answer_ts)::text = 'bigint'
                                THEN TO_TIMESTAMP(fa.first_answer_ts::double precision)::text
                                ELSE fa.first_answer_ts::text END)::timestamp
                        ))
                        -
                        EXTRACT(EPOCH FROM (
                          (CASE WHEN pg_typeof(q.creation_date)::text = 'bigint'
                                THEN TO_TIMESTAMP(q.creation_date::double precision)::text
                                ELSE q.creation_date::text END)::timestamp
                        ))
                      ) / 3600.0
                    ) <= :maxHours THEN 'solvable'
               WHEN q.answer_count = 0
                    AND (
                      EXTRACT(EPOCH FROM NOW())
                      -
                      EXTRACT(EPOCH FROM (
                        (CASE WHEN pg_typeof(q.creation_date)::text = 'bigint'
                              THEN TO_TIMESTAMP(q.creation_date::double precision)::text
                              ELSE q.creation_date::text END)::timestamp
                      ))
                    ) > :noAnswerSeconds THEN 'hard-to-solve'
               WHEN q.answer_count > 0 AND NOT EXISTS(SELECT 1 FROM answers a WHERE a.question_id = q.question_id AND a.score > 0) THEN 'hard-to-solve'
               ELSE 'neutral'
             END) AS category,

            fa.first_answer_ts AS first_answer_ts,
            (CASE WHEN fa.first_answer_ts IS NULL THEN NULL ELSE
              (
                EXTRACT(EPOCH FROM (
                  (CASE WHEN pg_typeof(fa.first_answer_ts)::text = 'bigint'
                        THEN TO_TIMESTAMP(fa.first_answer_ts::double precision)::text
                        ELSE fa.first_answer_ts::text END)::timestamp
                ))
                -
                EXTRACT(EPOCH FROM (
                  (CASE WHEN pg_typeof(q.creation_date)::text = 'bigint'
                        THEN TO_TIMESTAMP(q.creation_date::double precision)::text
                        ELSE q.creation_date::text END)::timestamp
                ))
              ) / 3600.0
            END) AS first_answer_hours
          FROM questions q
          LEFT JOIN users u ON q.user_id = u.user_id
          LEFT JOIN first_answer fa ON fa.question_id = q.question_id
         
          WHERE EXTRACT(EPOCH FROM (
                  (CASE WHEN pg_typeof(q.creation_date)::text = 'bigint'
                        THEN TO_TIMESTAMP(q.creation_date::double precision)::text
                        ELSE q.creation_date::text END)::timestamp
                )) >= :startTime
            AND q.body IS NOT NULL
        )

        SELECT
          question_id,
          title,
          body,
          score,
          view_count,
          answer_count,
          creation_date,
          reputation,
          body_length,
          title_length,
          has_code_snippet,
          tag_count,
          category,
          first_answer_ts,
          first_answer_hours
        FROM question_categories
        WHERE category IN ('solvable', 'hard-to-solve')
        ORDER BY creation_date DESC
        LIMIT :limit
    """, nativeQuery = true)
    List<Object[]> getCategorizedQuestions(
            @Param("startTime") long startTime,
            @Param("limit") int limit,
            @Param("maxHours") int maxHours,
            @Param("minHighScore") int minHighScore,
            @Param("noAnswerSeconds") long noAnswerSeconds);
}