package io.yak.ops.business.mdm.distribution;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.yak.ops.business.dataservice.publication.source.DataServiceSourceProvider;
import io.yak.ops.business.mdm.application.MdmCollectService;
import io.yak.ops.business.mdm.application.MdmEntityService;
import io.yak.ops.business.mdm.application.MdmProcessingTaskService;
import io.yak.ops.business.mdm.dao.mapper.MdmDistributionMapper;
import io.yak.ops.business.mdm.domain.attribute.MdmAttribute;
import io.yak.ops.business.mdm.domain.distribution.MdmDistributionMode;
import io.yak.ops.business.mdm.domain.distribution.MdmDistributionStatus;
import io.yak.ops.business.mdm.domain.entity.MdmEntity;
import io.yak.ops.business.mdm.domain.entity.MdmEntityStatus;
import io.yak.ops.business.mdm.exception.MdmException;
import io.yak.ops.business.mdm.infrastructure.repository.MdmAttributeRepository;
import io.yak.ops.business.mdm.processing.DedupSql;
import io.yak.ops.common.bean.po.mdm.MdmDistributionPO;
import io.yak.ops.common.enums.mdm.MdmErrorCode;
import io.yak.ops.core.project.CurrentProject;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 把主数据分发配置暴露为 data-service 的发布来源(R5,复用通道唯一真口:
 * {@code publication.source.DataServiceSourceProvider},见 DEPENDENCIES.md)。
 *
 * <p>粒度=「一条分发配置一个 API」:{@code sourceRef=distributionId}——
 * data-service 侧 {@code UNIQUE(source_type, source_ref)} 下多目标系统互不覆盖,
 * 目标系统的凭证(API Key/访问控制)天然按配置隔离。
 * {@code managesServiceDefinition=true}:定义归 MDM,数据服务页对该 API 的
 * 改删/再发布被中心拒绝,生命周期由分发配置(生效/停用/删除、执行分发)驱动。
 *
 * <p>发布即"当前实体查询定义":{@code resolve} 每次实时组 SQL(数据不锁快照,
 * 运行时逐次查 {@code yak_mdm_record});revision 取 SQL 模板的稳定 hash——
 * 属性集/范围变了才会翻转 {@code updateAvailable},纯执行结果回写不触发。
 */
@Component
public class MdmDataServiceSourceProvider implements DataServiceSourceProvider {

  public static final String SOURCE_TYPE = "MDM_DISTRIBUTION";

  private final MdmDistributionMapper distributionMapper;
  private final MdmEntityService entityService;
  private final MdmAttributeRepository attributeRepository;
  private final MdmProcessingTaskService processingTaskService;
  private final MdmCollectService collectService;
  private final CurrentProject currentProject;

  public MdmDataServiceSourceProvider(
      MdmDistributionMapper distributionMapper,
      MdmEntityService entityService,
      MdmAttributeRepository attributeRepository,
      MdmProcessingTaskService processingTaskService,
      MdmCollectService collectService,
      CurrentProject currentProject) {
    this.distributionMapper = distributionMapper;
    this.entityService = entityService;
    this.attributeRepository = attributeRepository;
    this.processingTaskService = processingTaskService;
    this.collectService = collectService;
    this.currentProject = currentProject;
  }

  @Override
  public String sourceType() {
    return SOURCE_TYPE;
  }

  @Override
  public boolean managesServiceDefinition() {
    return true;
  }

  /** 数据服务「发布来源」列表口径:本项目内可对外供数的 API 模式生效配置。 */
  @Override
  public SourcePage list(int pageNo, int pageSize, String keyword) {
    Long projectId = currentProject.requireProjectId();
    LambdaQueryWrapper<MdmDistributionPO> wrapper =
        new LambdaQueryWrapper<MdmDistributionPO>()
            .eq(MdmDistributionPO::getProjectId, projectId)
            .eq(MdmDistributionPO::getDistributeMode, MdmDistributionMode.API.name())
            .eq(MdmDistributionPO::getStatus, MdmDistributionStatus.ACTIVE.name());
    if (StringUtils.hasText(keyword)) {
      String like = keyword.trim();
      wrapper.and(w -> w.like(MdmDistributionPO::getTargetSystem, like)
          .or().like(MdmDistributionPO::getTargetName, like));
    }
    List<MdmDistributionPO> rows = distributionMapper.selectList(wrapper);
    int from = Math.max(0, Math.min((pageNo - 1) * pageSize, rows.size()));
    int to = Math.max(from, Math.min(from + pageSize, rows.size()));
    List<SourceDescriptor> records = new ArrayList<>();
    for (MdmDistributionPO row : rows.subList(from, to)) {
      records.add(resolve(String.valueOf(row.getId())).descriptor());
    }
    return new SourcePage(records, rows.size(), pageNo, pageSize);
  }

  @Override
  public ResolvedSource resolve(String sourceRef) {
    MdmDistributionPO config = load(sourceRef);
    MdmEntity entity = entityOf(config);
    List<MdmAttribute> attributes = publishableAttributes(config.getEntityId());
    List<String> codes = attributes.stream().map(MdmAttribute::code).toList();
    String sql =
        MdmDistributionQuerySql.build(
            config.getProjectId(), config.getEntityId(), collectService.businessDatabase(), codes);
    long revision = revisionOf(sql);
    SourceDescriptor descriptor =
        new SourceDescriptor(
            SOURCE_TYPE,
            String.valueOf(config.getId()),
            apiName(entity, config),
            "MDM_RECORD",
            publishable(config, entity) ? "ONLINE" : "OFFLINE",
            revision,
            (int) Math.floorMod(revision, Integer.MAX_VALUE - 1L) + 1,
            datasourceId(entity.id()),
            null,
            null,
            apiPath(entity, config),
            description(entity, config),
            config.getUpdateTime() == null
                ? null
                : config.getUpdateTime().atZone(ZoneId.systemDefault()).toInstant(),
            Boolean.FALSE);
    return new ResolvedSource(descriptor, sql, contract(attributes));
  }

  private MdmDistributionPO load(String sourceRef) {
    long id;
    try {
      id = Long.parseLong(StringUtils.hasText(sourceRef) ? sourceRef.trim() : "");
    } catch (NumberFormatException exception) {
      throw new MdmException(
          MdmErrorCode.DISTRIBUTE_FAILED, "分发来源 sourceRef 必须是分发配置 id: " + sourceRef);
    }
    // 不带项目窄化:sourceRef 全局唯一,发布/状态反查入口均已带正确的上下文或精确 id。
    MdmDistributionPO config = distributionMapper.selectById(id);
    if (config == null) {
      throw new MdmException(MdmErrorCode.DISTRIBUTE_FAILED, "分发配置不存在: " + id);
    }
    return config;
  }

  private MdmEntity entityOf(MdmDistributionPO config) {
    // 复用实体读侧(含存在性校验);调用方均在项目上下文内(HTTP 头或调度 ProjectContextScope)。
    return entityService.get(config.getEntityId());
  }

  private List<MdmAttribute> publishableAttributes(Long entityId) {
    return attributeRepository.listByEntity(entityId).stream()
        .filter(attribute -> MdmAttribute.STATUS_ENABLED.equals(attribute.status()))
        .filter(attribute -> DedupSql.isValidAttrCode(attribute.code()))
        .sorted(Comparator.comparingInt(MdmAttribute::sortOrder))
        .toList();
  }

  private Long datasourceId(Long entityId) {
    return processingTaskService
        .platformSinkDatasourceId(entityId)
        .orElseThrow(
            () ->
                new MdmException(
                    MdmErrorCode.DISTRIBUTE_FAILED,
                    "实体尚未生成采集落地任务,无法确定平台库数据源,不能发布分发 API"));
  }

  private static boolean publishable(MdmDistributionPO config, MdmEntity entity) {
    return MdmDistributionMode.API.name().equals(config.getDistributeMode())
        && MdmDistributionStatus.ACTIVE.name().equals(config.getStatus())
        && entity.status() == MdmEntityStatus.ACTIVE;
  }

  /** 只有 API 模板变了才翻转 revision(updateAvailable 语义);执行结果回写不触发重发布。 */
  private static long revisionOf(String sql) {
    return Math.floorMod(sql.hashCode() * 31L, Long.MAX_VALUE - 2L) + 1L;
  }

  private static String apiName(MdmEntity entity, MdmDistributionPO config) {
    String target =
        StringUtils.hasText(config.getTargetName()) ? config.getTargetName() : config.getTargetSystem();
    return "主数据分发-" + entity.code() + "-" + target;
  }

  /** 目标系统编码可改,路径用不可变实体编码 + 分发配置 id 保证稳定且避开全局 path 撞名。 */
  private static String apiPath(MdmEntity entity, MdmDistributionPO config) {
    return "/mdm/" + sanitize(entity.code()) + "/" + config.getId();
  }

  private static String description(MdmEntity entity, MdmDistributionPO config) {
    return "MDM 自动发布:实体「" + entity.name() + "」→ 目标系统「" + config.getTargetSystem()
        + "」,读统一主数据表 ACTIVE 记录,数据实时查表。";
  }

  private static String sanitize(String value) {
    return value == null || value.isBlank()
        ? "entity"
        : value.replaceAll("[^A-Za-z0-9._~-]", "-");
  }

  private static SourceContract contract(List<MdmAttribute> attributes) {
    List<ResponseFieldContract> fields = new ArrayList<>();
    fields.add(new ResponseFieldContract("master_id", "STRING", false, "主数据统一标识(MD5)", null));
    fields.add(new ResponseFieldContract("version", "INTEGER", false, "记录版本号", null));
    for (MdmAttribute attribute : attributes) {
      fields.add(
          new ResponseFieldContract(
              attribute.code(),
              "STRING",
              true,
              StringUtils.hasText(attribute.name()) ? attribute.name() : attribute.code(),
              null));
    }
    fields.add(new ResponseFieldContract("update_time", "DATETIME", true, "记录更新时间", null));
    return new SourceContract(List.of(), fields);
  }
}
