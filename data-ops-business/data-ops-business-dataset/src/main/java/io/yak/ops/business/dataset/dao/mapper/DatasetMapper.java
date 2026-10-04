package io.yak.ops.business.dataset.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.business.dataset.dao.model.DatasetPO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DatasetMapper extends BaseMapper<DatasetPO> {

  @Insert("INSERT IGNORE INTO yak_dataset_source_publication_lock (project_id, source_task_asset_id) VALUES (#{projectId}, #{sourceTaskAssetId})")
  @Insert(databaseId = "postgresql", value = """
      INSERT INTO yak_dataset_source_publication_lock (project_id, source_task_asset_id) VALUES (#{projectId}, #{sourceTaskAssetId})
      ON CONFLICT DO NOTHING
      """)
  int insertSourcePublicationLock(
      @Param("projectId") Long projectId,
      @Param("sourceTaskAssetId") long sourceTaskAssetId);

  @Select("SELECT source_task_asset_id FROM yak_dataset_source_publication_lock WHERE project_id = #{projectId} AND source_task_asset_id = #{sourceTaskAssetId} FOR UPDATE")
  Long selectSourcePublicationLockForUpdate(
      @Param("projectId") Long projectId,
      @Param("sourceTaskAssetId") long sourceTaskAssetId);

  @Select("SELECT id FROM yak_dataset WHERE project_id = #{projectId} AND id = #{datasetId} FOR UPDATE")
  Long selectProjectDatasetForUpdate(
      @Param("projectId") Long projectId, @Param("datasetId") long datasetId);
}
