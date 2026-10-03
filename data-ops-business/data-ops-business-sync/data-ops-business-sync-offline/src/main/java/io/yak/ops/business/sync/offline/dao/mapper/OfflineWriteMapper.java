package io.yak.ops.business.sync.offline.dao.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 只承载需要数据库原子语义、无法拆成普通 BaseMapper CRUD 的操作。
 *
 * <p>原 XML 已移除；行锁（{@code FOR UPDATE}）Wrapper 无法表达，SQL 保留在注解里。
 */
@Mapper
public interface OfflineWriteMapper {

  @Select("SELECT id FROM yak_offline_job_definition WHERE id = #{id} FOR UPDATE")
  Long lockDefinition(@Param("id") Long id);
}
