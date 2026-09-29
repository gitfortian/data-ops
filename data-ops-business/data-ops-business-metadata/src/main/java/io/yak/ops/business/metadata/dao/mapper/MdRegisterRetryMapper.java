package io.yak.ops.business.metadata.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.common.bean.po.metadata.MdRegisterRetryPO;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 写时登记 outbox（ticket 130）。插入走 {@code BaseMapper.insert}——撞
 * {@code uk_yak_md_retry_change} 抛 {@code DuplicateKeyException}，由 store catch 成"已排队"；
 * 队列状态机（due/claim/complete/fail）必须是<b>显式 SQL</b>：每步都是条件更新，
 * 通用 CRUD 给不了"只有抢到状态迁移的那一个 worker 能干活"这一保证。
 */
@Mapper
public interface MdRegisterRetryMapper extends BaseMapper<MdRegisterRetryPO> {

  /** 到期的 PENDING + 卡死超 10 分钟的 IN_PROGRESS（worker 崩溃后任务不永占）。 */
  List<MdRegisterRetryPO> selectDue(@Param("limit") int limit);

  /** 原子认领：状态迁移与 attempts+1 在同一条 UPDATE 里；返回 1 才算抢到。 */
  int claim(@Param("taskId") String taskId);

  int complete(@Param("taskId") String taskId);

  /** 回写失败：{@code newStatus} 由 store 算好（PENDING=退避重试，DEAD=终态）。 */
  int fail(
      @Param("taskId") String taskId,
      @Param("lastError") String lastError,
      @Param("delaySeconds") long delaySeconds,
      @Param("newStatus") String newStatus);
}
