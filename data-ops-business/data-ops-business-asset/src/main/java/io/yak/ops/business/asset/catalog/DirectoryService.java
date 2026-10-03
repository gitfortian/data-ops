package io.yak.ops.business.asset.catalog;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.yak.ops.business.asset.exception.AssetException;
import io.yak.ops.business.audit.AuditTransactions;
import io.yak.ops.business.audit.AuditEventType;
import io.yak.ops.business.audit.AuditOperationHandle;
import io.yak.ops.business.audit.AuditOperationRequest;
import io.yak.ops.business.audit.BusinessAuditService;
import io.yak.ops.business.semantic.api.LayerConfigApi;
import io.yak.ops.business.semantic.api.ProcessApi;
import io.yak.ops.business.semantic.api.BusinessDomain;
import io.yak.ops.business.semantic.api.WarehouseLayer;
import io.yak.ops.business.asset.dao.model.AssetDirectoryPO;
import io.yak.ops.business.asset.dao.model.AssetItemPO;
import io.yak.ops.business.asset.dao.mapper.AssetDirectoryMapper;
import io.yak.ops.business.asset.dao.mapper.AssetItemMapper;
import io.yak.ops.common.enums.asset.AssetErrorCode;
import io.yak.ops.core.project.CurrentProject;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 跨域资产目录树(ticket 92):物化路径维护、防环、模板初始化。
 * 目录是资产中心的自有事实(归哪类由平台治理决定),与 modeling 目录并存不回迁。
 */
@Service
@RequiredArgsConstructor
public class DirectoryService {

  public static final long ROOT_PARENT_ID = 0L;

  private final CurrentProject currentProject;
  private final AssetDirectoryMapper directoryMapper;
  private final AssetItemMapper itemMapper;
  private final LayerConfigApi layerConfigApi;
  private final ProcessApi processApi;
  private final BusinessAuditService auditService;

  /** 树节点(controller 直接序列化)。 */
  public record DirNode(
      Long id,
      String dirCode,
      String dirName,
      Long parentId,
      String path,
      int sortOrder,
      String iconKey,
      String description,
      boolean builtin,
      List<DirNode> children) {}

  public List<DirNode> tree() {
    List<AssetDirectoryPO> all = directoryMapper.selectList(
        new LambdaQueryWrapper<AssetDirectoryPO>()
            .eq(AssetDirectoryPO::getProjectId, currentProject.requireProjectId())
            .eq(AssetDirectoryPO::getDeleted, false)
            .orderByAsc(AssetDirectoryPO::getSortOrder)
            .orderByAsc(AssetDirectoryPO::getId));
    Map<Long, List<DirNode>> childrenByParent = new HashMap<>();
    for (AssetDirectoryPO po : all) {
      childrenByParent
          .computeIfAbsent(po.getParentId(), k -> new ArrayList<>())
          .add(new DirNode(po.getId(), po.getDirCode(), po.getDirName(), po.getParentId(),
              po.getPath(), po.getSortOrder() == null ? 0 : po.getSortOrder(), po.getIconKey(),
              po.getDescription(), Boolean.TRUE.equals(po.getBuiltin()), new ArrayList<>()));
    }
    return assemble(ROOT_PARENT_ID, childrenByParent);
  }

  private List<DirNode> assemble(Long parentId, Map<Long, List<DirNode>> byParent) {
    List<DirNode> nodes = byParent.getOrDefault(parentId, List.of());
    for (DirNode node : nodes) {
      node.children().addAll(assemble(node.id(), byParent));
    }
    return nodes;
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public DirNode create(Long parentId, String dirCode, String dirName, String iconKey,
      String description, Integer sortOrder, String operator) {
    Long projectId = currentProject.requireProjectId();
    long parent = parentId == null ? ROOT_PARENT_ID : parentId;
    String parentPath = "/";
    if (parent != ROOT_PARENT_ID) {
      AssetDirectoryPO parentPo = requireDirectory(projectId, parent);
      parentPath = parentPo.getPath();
    }
    String code = StringUtils.hasText(dirCode)
        ? uniqueCode(projectId, dirCode.trim()) : generateCode(projectId, parent);

    AssetDirectoryPO po = new AssetDirectoryPO();
    po.setProjectId(projectId);
    po.setDirCode(code);
    po.setDirName(dirName.trim());
    po.setParentId(parent);
    po.setPath(parentPath + "0/");
    po.setSortOrder(sortOrder != null ? sortOrder : nextSort(projectId, parent));
    po.setIconKey(iconKey);
    po.setDescription(description);
    po.setBuiltin(false);
    po.setCreatedBy(operator);
    po.setUpdatedBy(operator);
    po.setDeleted(false);
    try {
      directoryMapper.insert(po);
    } catch (DuplicateKeyException e) {
      throw new AssetException(AssetErrorCode.DUPLICATE_ASSET_KEY, code);
    }
    po.setPath(parentPath + po.getId() + "/");
    directoryMapper.updateById(po);
    audit("ASSET_DIR_CREATE", "Create asset directory", po.getId(), code, operator,
        AuditEventType.RESOURCE_CREATED, "新建目录 " + dirName);
    return toNode(po, List.of());
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public DirNode update(Long id, String dirName, String iconKey, String description,
      Integer sortOrder, String operator) {
    AssetDirectoryPO po = requireDirectory(currentProject.requireProjectId(), id);
    if (StringUtils.hasText(dirName)) {
      po.setDirName(dirName.trim());
    }
    if (iconKey != null) {
      po.setIconKey(iconKey);
    }
    if (description != null) {
      po.setDescription(description);
    }
    if (sortOrder != null) {
      po.setSortOrder(sortOrder);
    }
    po.setUpdatedBy(operator);
    po.setUpdateTime(LocalDateTime.now());
    directoryMapper.updateById(po);
    return toNode(po, List.of());
  }

  /** 移动到新父级:防环(48006),级联改写子树 path。 */
  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public void move(Long id, Long targetParentId, String operator) {
    Long projectId = currentProject.requireProjectId();
    AssetDirectoryPO po = requireDirectory(projectId, id);
    long target = targetParentId == null ? ROOT_PARENT_ID : targetParentId;
    if (target == id) {
      throw new AssetException(AssetErrorCode.DIRECTORY_INVALID, "不能移动到自身之下");
    }
    String targetPath = "/";
    int targetSort = 0;
    if (target != ROOT_PARENT_ID) {
      AssetDirectoryPO parent = requireDirectory(projectId, target);
      if (parent.getPath() != null && parent.getPath().contains("/" + id + "/")) {
        throw new AssetException(AssetErrorCode.DIRECTORY_INVALID, "不能移动到自己的子目录之下");
      }
      targetPath = parent.getPath();
      targetSort = nextSort(projectId, target);
    }
    String oldPath = po.getPath();
    String newPath = targetPath + id + "/";
    po.setParentId(target);
    po.setPath(newPath);
    po.setSortOrder(targetSort);
    po.setUpdatedBy(operator);
    po.setUpdateTime(LocalDateTime.now());
    directoryMapper.updateById(po);
    // 级联子树:oldPath 前缀替换为 newPath
    List<AssetDirectoryPO> subtree = directoryMapper.selectList(
        new LambdaQueryWrapper<AssetDirectoryPO>()
            .eq(AssetDirectoryPO::getProjectId, projectId)
            .eq(AssetDirectoryPO::getDeleted, false)
            .likeRight(AssetDirectoryPO::getPath, oldPath)
            .ne(AssetDirectoryPO::getId, id));
    for (AssetDirectoryPO node : subtree) {
      node.setPath(newPath + node.getPath().substring(oldPath.length()));
      directoryMapper.updateById(node);
    }
    audit("ASSET_DIR_MOVE", "Move asset directory", id, po.getDirCode(), operator,
        AuditEventType.RESOURCE_UPDATED, "目录 " + po.getDirName() + " 移动至 " + targetPath);
  }

  /** 删除:builtin 不可删;有子目录或资产不可删(48007)。 */
  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public void delete(Long id, String operator) {
    Long projectId = currentProject.requireProjectId();
    AssetDirectoryPO po = requireDirectory(projectId, id);
    if (Boolean.TRUE.equals(po.getBuiltin())) {
      throw new AssetException(AssetErrorCode.ILLEGAL_STATE_OPERATION, "模板初始化的内置目录不可删除,可编辑");
    }
    long children = directoryMapper.selectCount(new LambdaQueryWrapper<AssetDirectoryPO>()
        .eq(AssetDirectoryPO::getProjectId, projectId)
        .eq(AssetDirectoryPO::getParentId, id)
        .eq(AssetDirectoryPO::getDeleted, false));
    long assets = itemMapper.selectCount(new LambdaQueryWrapper<AssetItemPO>()
        .eq(AssetItemPO::getProjectId, projectId)
        .eq(AssetItemPO::getDirectoryId, id)
        .eq(AssetItemPO::getDeleted, false));
    if (children > 0 || assets > 0) {
      throw new AssetException(AssetErrorCode.DIRECTORY_NOT_EMPTY,
          "含 " + children + " 个子目录 / " + assets + " 个资产");
    }
    po.setDeleted(true);
    po.setUpdatedBy(operator);
    po.setUpdateTime(LocalDateTime.now());
    directoryMapper.updateById(po);
    audit("ASSET_DIR_DELETE", "Delete asset directory", id, po.getDirCode(), operator,
        AuditEventType.RESOURCE_DELETED, "删除目录 " + po.getDirName());
  }

  /**
   * 一键初始化:按 semantic 分层 + 业务域树建 builtin 目录(根下各一层/域目录,域保留树形)。
   * 幂等保护:已有 builtin 目录即报 48008。返回新建条数。
   */
  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public int initTemplate(String operator) {
    Long projectId = currentProject.requireProjectId();
    long builtin = directoryMapper.selectCount(new LambdaQueryWrapper<AssetDirectoryPO>()
        .eq(AssetDirectoryPO::getProjectId, projectId)
        .eq(AssetDirectoryPO::getBuiltin, true)
        .eq(AssetDirectoryPO::getDeleted, false));
    if (builtin > 0) {
      throw new AssetException(AssetErrorCode.TEMPLATE_ALREADY_INITIALIZED);
    }
    int created = 0;
    List<WarehouseLayer> layers = layerConfigApi.listLayers();
    int sort = 10;
    for (WarehouseLayer layer : layers == null ? List.<WarehouseLayer>of() : layers) {
      createBuiltin(projectId, ROOT_PARENT_ID, layer.code().toLowerCase(),
          layer.name() + "(" + layer.code().toUpperCase() + ")", sort, operator);
      created++;
      sort += 10;
    }
    // 业务域扁平列表按 parentId 树形落目录(域根目录 → 子域目录)
    List<BusinessDomain> domains = processApi.listDomains();
    Map<Long, Long> dirIdByDomainId = new HashMap<>();
    int domainSort = sort + 100;
    List<BusinessDomain> ordered = flattenParentsFirst(domains == null ? List.of() : domains);
    for (BusinessDomain domain : ordered) {
      Long parentDirId = domain.parentId() == null || domain.parentId() == BusinessDomain.ROOT_PARENT_ID
          ? ROOT_PARENT_ID : dirIdByDomainId.getOrDefault(domain.parentId(), ROOT_PARENT_ID);
      AssetDirectoryPO po = createBuiltin(projectId, parentDirId,
          "dom_" + domain.code().toLowerCase(), domain.name(), domainSort, operator);
      dirIdByDomainId.put(domain.id(), po.getId());
      created++;
      domainSort += 10;
    }
    if (created > 0) {
      audit("ASSET_DIR_INIT", "Initialize asset directory template", null, "template", operator,
          AuditEventType.RESOURCE_CREATED, "模板初始化目录 " + created + " 个");
    }
    return created;
  }

  /** 批量移目录(directoryId=null 即移出目录);仅本项目未删资产生效,返回移动数。 */
  @Transactional(transactionManager = "yakBusinessTransactionManager", rollbackFor = Exception.class)
  public int moveAssets(List<Long> assetIds, Long directoryId, String operator) {
    Long projectId = currentProject.requireProjectId();
    if (directoryId != null) {
      requireDirectory(projectId, directoryId);
    }
    int moved = 0;
    for (Long assetId : assetIds) {
      AssetItemPO item = itemMapper.selectOne(new LambdaQueryWrapper<AssetItemPO>()
          .eq(AssetItemPO::getProjectId, projectId)
          .eq(AssetItemPO::getId, assetId)
          .eq(AssetItemPO::getDeleted, false));
      if (item == null) {
        continue;
      }
      item.setDirectoryId(directoryId);
      item.setUpdatedBy(operator);
      item.setUpdateTime(LocalDateTime.now());
      itemMapper.updateById(item);
      moved++;
    }
    if (moved > 0) {
      audit("ASSET_MOVE_DIR", "Move assets between directories", directoryId,
          directoryId == null ? "uncategorized" : String.valueOf(directoryId), operator,
          AuditEventType.RESOURCE_UPDATED, "批量移动 " + moved + " 个资产");
    }
    return moved;
  }

  /** 校验目录在本项目且未删(供台账/规则引用)。 */
  public AssetDirectoryPO requireDirectory(Long projectId, Long id) {
    AssetDirectoryPO po = directoryMapper.selectOne(new LambdaQueryWrapper<AssetDirectoryPO>()
        .eq(AssetDirectoryPO::getProjectId, projectId)
        .eq(AssetDirectoryPO::getId, id)
        .eq(AssetDirectoryPO::getDeleted, false));
    if (po == null) {
      throw new AssetException(AssetErrorCode.DIRECTORY_INVALID, "id=" + id);
    }
    return po;
  }

  // ---------- internal ----------

  private AssetDirectoryPO createBuiltin(Long projectId, long parentId, String baseCode,
      String name, int sortOrder, String operator) {
    String parentPath = parentId == ROOT_PARENT_ID
        ? "/" : requireDirectory(projectId, parentId).getPath();
    AssetDirectoryPO po = new AssetDirectoryPO();
    po.setProjectId(projectId);
    po.setDirCode(uniqueCode(projectId, baseCode));
    po.setDirName(name);
    po.setParentId(parentId);
    po.setPath(parentPath + "0/");
    po.setSortOrder(sortOrder);
    po.setBuiltin(true);
    po.setCreatedBy(operator);
    po.setUpdatedBy(operator);
    po.setDeleted(false);
    directoryMapper.insert(po);
    po.setPath(parentPath + po.getId() + "/");
    directoryMapper.updateById(po);
    return po;
  }

  /** 父下序号式编码:{父编码}_{n};根下 dir_{n}。冲突时递增。 */
  private String generateCode(Long projectId, long parentId) {
    String prefix = parentId == ROOT_PARENT_ID ? "dir_" : codeOf(projectId, parentId) + "_";
    return uniqueCode(projectId, prefix + nextSiblingSeq(projectId, parentId));
  }

  private long nextSiblingSeq(Long projectId, long parentId) {
    long count = directoryMapper.selectCount(new LambdaQueryWrapper<AssetDirectoryPO>()
        .eq(AssetDirectoryPO::getProjectId, projectId)
        .eq(AssetDirectoryPO::getParentId, parentId));
    return count + 1;
  }

  private String codeOf(Long projectId, Long id) {
    AssetDirectoryPO po = directoryMapper.selectOne(new LambdaQueryWrapper<AssetDirectoryPO>()
        .eq(AssetDirectoryPO::getProjectId, projectId)
        .eq(AssetDirectoryPO::getId, id));
    return po == null ? "dir" : po.getDirCode();
  }

  private String uniqueCode(Long projectId, String base) {
    String code = base;
    int suffix = 1;
    while (codeExists(projectId, code)) {
      code = base + "_" + suffix++;
    }
    return code;
  }

  private boolean codeExists(Long projectId, String code) {
    return directoryMapper.selectCount(new LambdaQueryWrapper<AssetDirectoryPO>()
        .eq(AssetDirectoryPO::getProjectId, projectId)
        .eq(AssetDirectoryPO::getDirCode, code)) > 0;
  }

  private int nextSort(Long projectId, long parentId) {
    Long max = 0L;
    List<AssetDirectoryPO> siblings = directoryMapper.selectList(
        new LambdaQueryWrapper<AssetDirectoryPO>()
            .eq(AssetDirectoryPO::getProjectId, projectId)
            .eq(AssetDirectoryPO::getParentId, parentId));
    for (AssetDirectoryPO sibling : siblings) {
      if (sibling.getSortOrder() != null && sibling.getSortOrder() > max) {
        max = (long) sibling.getSortOrder();
      }
    }
    return max.intValue() + 10;
  }

  /** 父先于子排序(parentId 升序即可保证,根=0 最先)。 */
  private static List<BusinessDomain> flattenParentsFirst(List<BusinessDomain> domains) {
    List<BusinessDomain> copy = new ArrayList<>(domains);
    copy.sort((a, b) -> {
      long pa = a.parentId() == null ? ROOT_PARENT_ID : a.parentId();
      long pb = b.parentId() == null ? ROOT_PARENT_ID : b.parentId();
      int byParent = Long.compare(pa, pb);
      return byParent != 0 ? byParent : Integer.compare(a.sortOrder(), b.sortOrder());
    });
    return copy;
  }

  private void audit(String code, String action, Long id, String name, String operator,
      AuditEventType type, String message) {
    AuditOperationHandle handle = auditService.start(new AuditOperationRequest(
        code, action, "ASSET_DIRECTORY",
        id == null ? null : String.valueOf(id), name, "APPLICATION", Map.of()));
    AuditTransactions.completeOnCommit(handle, type, message,
        Map.of("target", String.valueOf(id)), null);
  }

  private static DirNode toNode(AssetDirectoryPO po, List<DirNode> children) {
    return new DirNode(po.getId(), po.getDirCode(), po.getDirName(), po.getParentId(),
        po.getPath(), po.getSortOrder() == null ? 0 : po.getSortOrder(), po.getIconKey(),
        po.getDescription(), Boolean.TRUE.equals(po.getBuiltin()), children);
  }
}
