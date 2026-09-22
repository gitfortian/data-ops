package io.yak.ops.business.sync.offline.dao.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 只承载需要数据库原子语义、无法拆成普通 BaseMapper CRUD 的操作。 */
@Mapper
public interface OfflineWriteMapper {
  Long lockDefinition(@Param("id") Long id);
}
