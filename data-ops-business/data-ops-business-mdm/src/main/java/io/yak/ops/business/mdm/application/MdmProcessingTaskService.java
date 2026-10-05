package io.yak.ops.business.mdm.application;

import io.yak.ops.business.development.domain.DevelopmentNode;
import io.yak.ops.business.development.domain.DevelopmentTaskDraft;
import io.yak.ops.business.development.node.DevelopmentNodeService;
import io.yak.ops.business.development.service.DevelopmentDraftConflictException;
import io.yak.ops.business.development.task.DevelopmentTaskService;
import io.yak.ops.business.mdm.domain.collect.MdmCollectLink;
import io.yak.ops.business.mdm.domain.entity.MdmEntity;
import io.yak.ops.business.mdm.exception.MdmException;
import io.yak.ops.business.mdm.infrastructure.repository.MdmCollectLinkRepository;
import io.yak.ops.business.sync.offline.definition.OfflineJobDefinitionService;
import io.yak.ops.common.enums.mdm.MdmErrorCode;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 主数据加工任务注册(R2,review P2-6):把「裸复制 SQL」升级为「注册数据开发草稿」。
 * find-or-create SQL 节点(名称 {@code MDM主数据加工-{实体编码}},目录根)→
 * {@code DevelopmentTaskService.saveDraft} 写入加工 SQL(读落地表,同库);
 * configJson 预填落地任务的 sink 数据源(能默认就默认),用户在编辑器确认连接后运行/发布。
 * MDM 仍零执行引擎(D-M11):执行/调度/发布归数据开发。
 */
@Slf4j
@Component
public class MdmProcessingTaskService {

  /** SQL 插件 schemaVersion=1;dialect 跟随平台业务库。 */
  private static final String CONFIG_JSON_TEMPLATE = "{\"dialect\":\"%s\"%s}";

  private final MdmRecordService recordService;
  private final MdmEntityService entityService;
  private final MdmCollectLinkRepository linkRepository;
  private final ObjectProvider<DevelopmentNodeService> nodeServices;
  private final ObjectProvider<DevelopmentTaskService> taskServices;
  private final ObjectProvider<OfflineJobDefinitionService> definitionServices;

  public MdmProcessingTaskService(
      MdmRecordService recordService,
      MdmEntityService entityService,
      MdmCollectLinkRepository linkRepository,
      ObjectProvider<DevelopmentNodeService> nodeServices,
      ObjectProvider<DevelopmentTaskService> taskServices,
      ObjectProvider<OfflineJobDefinitionService> definitionServices) {
    this.recordService = recordService;
    this.entityService = entityService;
    this.linkRepository = linkRepository;
    this.nodeServices = nodeServices;
    this.taskServices = taskServices;
    this.definitionServices = definitionServices;
  }

  /** 注册结果:前端据此跳转 {@code /data-development/task/{nodeId}}。 */
  public record ProcessingTaskReceipt(Long nodeId, String taskName, boolean nodeCreated) {}

  /** 生成/更新加工任务草稿(幂等:同名节点复用,草稿按最新来源绑定重写)。 */
  public ProcessingTaskReceipt register(Long entityId) {
    String sql = recordService.generateMasterSql(entityId);
    MdmEntity entity = entityService.get(entityId);
    DevelopmentNodeService nodes = nodeServices.getIfAvailable();
    DevelopmentTaskService tasks = taskServices.getIfAvailable();
    if (nodes == null || tasks == null) {
      throw new MdmException(
          MdmErrorCode.PROCESSING_TASK_REGISTER_FAILED, "数据开发模块未启用,无法注册加工任务");
    }
    String taskName = "MDM主数据加工-" + entity.code();
    Optional<DevelopmentNode> existing =
        nodes.list().stream().filter(node -> taskName.equals(node.name())).findFirst();
    boolean created = existing.isEmpty();
    DevelopmentNode node = existing.orElseGet(() -> createNodeSafely(nodes, taskName));
    if (!"SQL".equalsIgnoreCase(node.type())) {
      throw new MdmException(
          MdmErrorCode.PROCESSING_TASK_REGISTER_FAILED,
          "同名节点已存在但类型不是 SQL:" + taskName);
    }
    String configJson =
        String.format(
            CONFIG_JSON_TEMPLATE, recordService.isPostgresql() ? "POSTGRESQL" : "MYSQL",
            sinkDatasourceConfigFragment(entityId));
    try {
      saveDraft(tasks, node.id(), sql, configJson);
    } catch (DevelopmentDraftConflictException conflict) {
      // 乐观锁竞争(他人正在编辑):重读草稿基线重试一次,仍冲突则透出。
      try {
        saveDraft(tasks, node.id(), sql, configJson);
      } catch (DevelopmentDraftConflictException retry) {
        throw new MdmException(
            MdmErrorCode.PROCESSING_TASK_REGISTER_FAILED,
            "草稿正被其他会话编辑," + retry.getMessage(),
            retry);
      }
    } catch (RuntimeException exception) {
      throw new MdmException(
          MdmErrorCode.PROCESSING_TASK_REGISTER_FAILED,
          exception.getMessage() == null ? "注册加工任务草稿失败" : exception.getMessage(),
          exception);
    }
    return new ProcessingTaskReceipt(node.id(), taskName, created);
  }

  private void saveDraft(
      DevelopmentTaskService tasks, Long nodeId, String sql, String configJson) {
    DevelopmentTaskDraft draft = tasks.getDraft(nodeId);
    tasks.saveDraft(
        nodeId,
        "SQL",
        draft.definition().schemaVersion(),
        sql,
        configJson,
        draft.draftRevision());
  }

  /** 并发保护:create 重名抛错时回读既有节点。 */
  private DevelopmentNode createNodeSafely(DevelopmentNodeService nodes, String taskName) {
    try {
      return nodes.create(taskName, "SQL", null);
    } catch (IllegalStateException duplicate) {
      return nodes.list().stream()
          .filter(node -> taskName.equals(node.name()))
          .findFirst()
          .orElseThrow(
              () ->
                  new MdmException(
                      MdmErrorCode.PROCESSING_TASK_REGISTER_FAILED,
                      duplicate.getMessage(),
                      duplicate));
    }
  }

  /** 预填执行连接:取任一采集链路落地任务的 sink 数据源(其对平台库可写,R1 已验证)。 */
  private String sinkDatasourceConfigFragment(Long entityId) {
    Long sinkId = platformSinkDatasourceId(entityId).orElse(null);
    return sinkId == null ? "" : ",\"dataSourceId\":\"" + sinkId + "\"";
  }

  /**
   * 平台库对应的已注册数据源 ID(R1 落地任务 sink 反查,零额外配置)。
   * R5 分发 API 与加工任务共用:同一平台库连接,一处真相。
   */
  public Optional<Long> platformSinkDatasourceId(Long entityId) {
    List<MdmCollectLink> links = linkRepository.listByEntity(entityId);
    OfflineJobDefinitionService definitions = definitionServices.getIfAvailable();
    if (definitions == null) {
      return Optional.empty();
    }
    for (MdmCollectLink link : links) {
      try {
        var definition = definitions.get(link.jobDefinitionId());
        Long sinkId = definition == null ? null : definition.getSinkDatasourceId();
        if (sinkId != null && sinkId > 0) {
          return Optional.of(sinkId);
        }
      } catch (RuntimeException exception) {
        log.warn("反查落地任务 sink 数据源失败, jobDefinitionId={}", link.jobDefinitionId(), exception);
      }
    }
    return Optional.empty();
  }
}
