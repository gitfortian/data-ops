package io.yak.ops.business.metadata.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.common.bean.po.metadata.MdRegisterRetryPO;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 写时登记 outbox（ticket 130）。插入走 {@code BaseMapper.insert}——撞
 * {@code uk_yak_md_retry_change} 抛 {@code DuplicateKeyException}，由 store catch 成"已排队"；
 * 队列状态机（due/claim/complete/fail）必须是<b>显式 SQL</b>：每步都是条件更新，
 * 通用 CRUD 给不了"只有抢到状态迁移的那一个 worker 能干活"这一保证。
 *
 * <p>原 XML 已移除：形状逐字保留在注解里，理由同 {@link LineageCatalogRowMapper}。
 */
@Mapper
public interface MdRegisterRetryMapper extends BaseMapper<MdRegisterRetryPO> {

  /** 到期的 PENDING + 卡死超 10 分钟的 IN_PROGRESS（worker 崩溃后任务不永占）。 */
  @Select(
      """
      SELECT task_id, project_id, type_name, asset_key, source_id, operation, status,
             attempts, next_attempt_time, last_error, payload, source_updated_at,
             create_time, update_time
      FROM yak_md_register_retry
      WHERE (status = 'PENDING' AND next_attempt_time <= NOW(6))
         OR (status = 'IN_PROGRESS' AND update_time < DATE_SUB(NOW(6), INTERVAL 10 MINUTE))
      ORDER BY next_attempt_time
      LIMIT #{limit}
      """)
  List<MdRegisterRetryPO> selectDue(@Param("limit") int limit);

  /**
   * 原子认领：状态迁移与 attempts+1 在同一条 UPDATE 里；返回 1 才算抢到。
   *
   * <p>WHERE 重带 due 的整个谓词：selectDue 与 claim 之间别的 worker 可能已抢走，
   * 也可能时间刚走过退避点——认领必须只对"此刻仍然可认领"的行生效。
   */
  @Update(
      """
      UPDATE yak_md_register_retry
      SET status = 'IN_PROGRESS', attempts = attempts + 1, update_time = NOW(6)
      WHERE task_id = #{taskId}
        AND ((status = 'PENDING' AND next_attempt_time <= NOW(6))
          OR (status = 'IN_PROGRESS' AND update_time < DATE_SUB(NOW(6), INTERVAL 10 MINUTE)))
      """)
  int claim(@Param("taskId") String taskId);

  @Update(
      """
      UPDATE yak_md_register_retry
      SET status = 'DONE', last_error = NULL, update_time = NOW(6)
      WHERE task_id = #{taskId} AND status = 'IN_PROGRESS'
      """)
  int complete(@Param("taskId") String taskId);

  /**
   * 回写失败：{@code newStatus} 由 store 算好（PENDING=退避重试，DEAD=终态）。
   *
   * <p>退避时长与 DEAD 判定在 Java（RegisterRetryStore.fail）算，这里只执行：
   * SQL 里再写一遍 1&lt;&lt;attempts 就有了第二个真相源，改上界时必漏一边。
   */
  @Update(
      """
      UPDATE yak_md_register_retry
      SET status = #{newStatus},
          last_error = #{lastError},
          next_attempt_time = DATE_ADD(NOW(6), INTERVAL #{delaySeconds} SECOND),
          update_time = NOW(6)
      WHERE task_id = #{taskId} AND status = 'IN_PROGRESS'
      """)
  int fail(
      @Param("taskId") String taskId,
      @Param("lastError") String lastError,
      @Param("delaySeconds") long delaySeconds,
      @Param("newStatus") String newStatus);
}
