package io.yak.ops.business.dataservice.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.business.dataservice.dao.model.DataServiceConsumerPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DataServiceConsumerMapper extends BaseMapper<DataServiceConsumerPO> {

  @Select("SELECT COUNT(DISTINCT c.id) "
      + "FROM yak_ops_data_service_consumer c "
      + "LEFT JOIN yak_ops_data_service_consumer_api_grant g ON g.consumer_id = c.id "
      + "WHERE c.project_id = #{projectId} "
      + "AND (c.access_scope = 'ALL' OR g.api_id = #{apiId})")
  long countConfiguredAccess(@Param("projectId") Long projectId, @Param("apiId") Long apiId);

  @Select("SELECT COUNT(DISTINCT c.id) "
      + "FROM yak_ops_data_service_consumer c "
      + "LEFT JOIN yak_ops_data_service_consumer_api_grant g ON g.consumer_id = c.id "
      + "WHERE c.id = #{consumerId} AND c.project_id = #{projectId} "
      + "AND (c.access_scope = 'ALL' OR g.api_id = #{apiId})")
  long countConsumerAccess(
      @Param("consumerId") Long consumerId,
      @Param("projectId") Long projectId,
      @Param("apiId") Long apiId);
}
