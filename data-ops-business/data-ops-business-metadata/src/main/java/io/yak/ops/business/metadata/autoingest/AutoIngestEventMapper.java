package io.yak.ops.business.metadata.autoingest;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** All updates are claim-token CAS. No in-memory queue is considered a delivery receipt. */
@Mapper
public interface AutoIngestEventMapper {
  @Insert("""
      INSERT INTO yak_md_auto_ingest_event
        (project_id, run_id, entity_id, status, attempt_count,
         next_attempt_at, created_at, updated_at)
      VALUES (#{projectId}, #{runId}, #{entityId}, 'PENDING', 0,
              #{now}, #{now}, #{now})
      """)
  int enqueue(@Param("projectId") Long projectId, @Param("runId") Long runId,
              @Param("entityId") Long entityId, @Param("now") LocalDateTime now);

  @Select("""
      SELECT id, project_id AS projectId, run_id AS runId, entity_id AS entityId,
             status, attempt_count AS attemptCount, next_attempt_at AS nextAttemptAt,
             lease_until AS leaseUntil, claim_token AS claimToken,
             error_message AS errorMessage, created_at AS createdAt, updated_at AS updatedAt
      FROM yak_md_auto_ingest_event
      WHERE status IN ('PENDING', 'RETRY')
        AND next_attempt_at <= #{now}
      ORDER BY next_attempt_at, id
      LIMIT #{limit}
      """)
  List<AutoIngestEvent> ready(@Param("now") LocalDateTime now, @Param("limit") int limit);

  @Update("""
      UPDATE yak_md_auto_ingest_event SET
        status = 'RETRY', claim_token = NULL, lease_until = NULL,
        next_attempt_at = #{now}, updated_at = #{now}
      WHERE status = 'RUNNING' AND lease_until < #{now}
      """)
  int releaseExpired(@Param("now") LocalDateTime now);

  @Update("""
      UPDATE yak_md_auto_ingest_event SET
        status = 'RUNNING', claim_token = #{token}, lease_until = #{leaseUntil},
        attempt_count = attempt_count + 1, updated_at = #{now}
      WHERE id = #{id} AND status IN ('PENDING', 'RETRY')
        AND next_attempt_at <= #{now}
      """)
  int claim(@Param("id") Long id, @Param("token") String token,
            @Param("leaseUntil") LocalDateTime leaseUntil,
            @Param("now") LocalDateTime now);

  @Update("""
      UPDATE yak_md_auto_ingest_event SET
        status = 'SUCCEEDED', claim_token = NULL, lease_until = NULL,
        error_message = NULL, updated_at = #{now}
      WHERE id = #{id} AND status = 'RUNNING' AND claim_token = #{token}
      """)
  int succeed(@Param("id") Long id, @Param("token") String token,
              @Param("now") LocalDateTime now);

  @Update("""
      UPDATE yak_md_auto_ingest_event SET
        status = #{status}, claim_token = NULL, lease_until = NULL,
        next_attempt_at = #{retryAt}, error_message = #{error},
        updated_at = #{now}
      WHERE id = #{id} AND status = 'RUNNING' AND claim_token = #{token}
      """)
  int fail(@Param("id") Long id, @Param("token") String token,
           @Param("status") String status, @Param("retryAt") LocalDateTime retryAt,
           @Param("error") String error, @Param("now") LocalDateTime now);

  @Select("""
      SELECT id, project_id AS projectId, run_id AS runId, entity_id AS entityId,
             status, attempt_count AS attemptCount, next_attempt_at AS nextAttemptAt,
             lease_until AS leaseUntil, claim_token AS claimToken,
             error_message AS errorMessage, created_at AS createdAt, updated_at AS updatedAt
      FROM yak_md_auto_ingest_event
      WHERE project_id = #{projectId} AND entity_id = #{entityId}
      ORDER BY id DESC LIMIT 1
      """)
  AutoIngestEvent latest(@Param("projectId") Long projectId,
                         @Param("entityId") Long entityId);

  @Update("""
      UPDATE yak_md_auto_ingest_event
      SET status = 'RETRY', next_attempt_at = #{now}, error_message = NULL,
          updated_at = #{now}
      WHERE project_id = #{projectId} AND id = #{id} AND status = 'DEAD'
      """)
  int retryDead(@Param("projectId") Long projectId,
                @Param("id") Long id, @Param("now") LocalDateTime now);
}
