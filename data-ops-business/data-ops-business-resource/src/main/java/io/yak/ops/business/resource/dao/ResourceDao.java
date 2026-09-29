package io.yak.ops.business.resource.dao;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.yak.ops.common.bean.po.resource.ResourcePO;
import io.yak.ops.common.enums.resource.ResourceNodeType;
import java.util.List;

/** 资源数据访问接口，只暴露持久化模型和 DAO 查询条件。 */
public interface ResourceDao {

  int insert(ResourcePO resourcePO);

  boolean update(ResourcePO resourcePO);

  boolean update(Long projectId, ResourcePO resourcePO);

  ResourcePO selectById(Long id);

  ResourcePO selectById(Long projectId, Long id);

  ResourcePO selectByFullPath(String fullPath);

  ResourcePO selectByFullPath(Long projectId, String fullPath);

  boolean existsByParentAndName(Long parentId, String name, Long excludeId);

  boolean existsByParentAndName(
      Long projectId, Long parentId, String name, Long excludeId);

  List<ResourcePO> selectChildren(Long parentId, String keyword);

  List<ResourcePO> selectChildren(Long projectId, Long parentId, String keyword);

  List<ResourcePO> selectAll();

  List<ResourcePO> selectAll(Long projectId);

  List<ResourcePO> selectDescendants(String fullPath);

  List<ResourcePO> selectDescendants(Long projectId, String fullPath);

  IPage<ResourcePO> selectPage(PageQuery query);

  boolean updateBatch(List<ResourcePO> resources);

  boolean updateBatch(Long projectId, List<ResourcePO> resources);

  boolean deleteBatch(List<Long> ids);

  boolean deleteBatch(Long projectId, List<Long> ids);

  /** DAO 自有分页条件，不依赖 HTTP DTO。 */
  record PageQuery(
      Long projectId,
      int pageNo,
      int pageSize,
      Long parentId,
      String keyword,
      ResourceNodeType nodeType) {

    public PageQuery(
        int pageNo,
        int pageSize,
        Long parentId,
        String keyword,
        ResourceNodeType nodeType) {
      this(null, pageNo, pageSize, parentId, keyword, nodeType);
    }
  }
}
