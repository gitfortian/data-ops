package io.yak.ops.business.sync.realtime.dao.mapper;

import io.yak.ops.business.sync.realtime.dao.model.RealtimeJobListRow;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RealtimeJobQueryMapper {
  long countByProject(
      @Param("projectId") long projectId,
      @Param("keyword") String keyword,
      @Param("id") Long id,
      @Param("releaseState") String releaseState,
      @Param("stateGroup") String stateGroup);

  List<RealtimeJobListRow> pageByProject(
      @Param("projectId") long projectId,
      @Param("keyword") String keyword,
      @Param("id") Long id,
      @Param("releaseState") String releaseState,
      @Param("stateGroup") String stateGroup,
      @Param("limit") int limit,
      @Param("offset") int offset);
}
