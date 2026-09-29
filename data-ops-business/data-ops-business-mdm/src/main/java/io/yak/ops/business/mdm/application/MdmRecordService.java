package io.yak.ops.business.mdm.application;

import io.yak.framework.common.PageData;
import io.yak.ops.business.mdm.domain.attribute.MdmAttribute;
import io.yak.ops.business.mdm.domain.attribute.MdmAttributeType;
import io.yak.ops.business.mdm.domain.collect.MdmCollectLink;
import io.yak.ops.business.mdm.domain.entity.MdmEntity;
import io.yak.ops.business.mdm.domain.record.MdmRecord;
import io.yak.ops.business.mdm.domain.record.MdmRecordStatus;
import io.yak.ops.business.mdm.domain.source.MdmSource;
import io.yak.ops.business.mdm.exception.MdmException;
import io.yak.ops.business.mdm.infrastructure.repository.MdmAttributeRepository;
import io.yak.ops.business.mdm.infrastructure.repository.MdmCollectLinkRepository;
import io.yak.ops.business.mdm.infrastructure.repository.MdmRecordRepository;
import io.yak.ops.business.mdm.infrastructure.repository.MdmSourceRepository;
import io.yak.ops.business.mdm.processing.DedupSql;
import io.yak.ops.business.mdm.processing.MdmMasterSqlGenerator;
import io.yak.ops.business.mdm.processing.MdmMasterSqlGenerator.AttributeSpec;
import io.yak.ops.business.mdm.processing.MdmMasterSqlGenerator.LandingSpec;
import io.yak.ops.common.enums.mdm.MdmErrorCode;
import io.yak.ops.core.project.CurrentProject;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Owns the master data record read side (55a): records are written by
 * master-data processing tasks executed in data-development (plan A, D-M11),
 * MDM only queries them and generates the processing SQL. R2 口径:加工 SQL 读
 * 平台库落地表(经 R1 采集链路),不再直连业务源表;未生成落地任务的来源不允许加工。
 */
@Component
public class MdmRecordService {

  private final MdmRecordRepository recordRepository;
  private final MdmEntityService entityService;
  private final MdmAttributeRepository attributeRepository;
  private final MdmSourceRepository sourceRepository;
  private final MdmCollectLinkRepository collectLinkRepository;
  private final MdmCollectService collectService;
  private final CurrentProject currentProject;

  public MdmRecordService(
      MdmRecordRepository recordRepository,
      MdmEntityService entityService,
      MdmAttributeRepository attributeRepository,
      MdmSourceRepository sourceRepository,
      MdmCollectLinkRepository collectLinkRepository,
      MdmCollectService collectService,
      CurrentProject currentProject) {
    this.recordRepository = recordRepository;
    this.entityService = entityService;
    this.attributeRepository = attributeRepository;
    this.sourceRepository = sourceRepository;
    this.collectLinkRepository = collectLinkRepository;
    this.collectService = collectService;
    this.currentProject = currentProject;
  }

  /**
   * 记录分页(review P0-1.4):关键词同时匹配 master_id 与实体属性值
   * (属性编码经安全标识符白名单过滤后下推 JSON_EXTRACT 谓词)。
   */
  public PageData<MdmRecord> page(
      Long entityId, int pageNo, int pageSize, String keyword, String status) {
    entityService.get(entityId);
    List<String> attributeCodes =
        attributeRepository.listByEntity(entityId).stream()
            .map(MdmAttribute::code)
            .filter(DedupSql::isValidAttrCode)
            .toList();
    return recordRepository.page(
        entityId, pageNo, pageSize, keyword, resolveStatus(status), attributeCodes);
  }

  /**
   * 生成主数据加工 SQL(R2:每落地表一段 INSERT...SELECT,同库拼接交数据开发执行)。
   * 来源未生成采集落地任务时阻断,保证「识别→落地→加工→记录」主链路不脱节。
   */
  public String generateMasterSql(Long entityId) {
    MdmEntity entity = entityService.get(entityId);
    List<MdmAttribute> attributes = attributeRepository.listByEntity(entityId);
    if (attributes.isEmpty()) {
      throw new MdmException(MdmErrorCode.PK_ATTRIBUTE_MISSING, "请先为实体定义属性");
    }
    boolean hasPk = attributes.stream().anyMatch(a -> a.type() == MdmAttributeType.PK);
    if (!hasPk) {
      throw new MdmException(MdmErrorCode.PK_ATTRIBUTE_MISSING, entity.code());
    }
    List<MdmSource> sources = sourceRepository.listByEntity(entityId);
    if (sources.isEmpty()) {
      throw new MdmException(MdmErrorCode.SOURCE_NOT_FOUND, "请先在「主数据识别」绑定来源");
    }
    Map<Long, MdmCollectLink> linksBySource =
        collectLinkRepository.listByEntity(entityId).stream()
            .collect(Collectors.toMap(MdmCollectLink::sourceId, Function.identity(), (a, b) -> a));
    List<String> missing =
        sources.stream()
            .filter(source -> !linksBySource.containsKey(source.id()))
            .map(MdmSource::table)
            .toList();
    if (!missing.isEmpty()) {
      throw new MdmException(
          MdmErrorCode.SOURCE_NOT_LANDED, "请先在「主数据识别」页生成落地任务: " + String.join(", ", missing));
    }
    String platformDatabase = collectService.businessDatabase();
    List<AttributeSpec> specs =
        attributes.stream()
            .map(a -> new AttributeSpec(a.code(), a.type() == MdmAttributeType.PK))
            .toList();
    StringBuilder sql = new StringBuilder();
    for (MdmSource source : sources) {
      if (sql.length() > 0) {
        sql.append('\n');
      }
      MdmCollectLink link = linksBySource.get(source.id());
      sql.append(
          MdmMasterSqlGenerator.generate(
              currentProject.requireProjectId(),
              entity.id(),
              entity.code(),
              specs,
              new LandingSpec(platformDatabase, link.landingTable(), source.datasourceId()),
              source.fieldMapping()));
    }
    return sql.toString();
  }

  /** 实体是否存在可加工的落地链路(前端按钮可用性提示用)。 */
  public boolean hasLandedSources(Long entityId) {
    return !collectLinkRepository.listByEntity(entityId).isEmpty();
  }

  private static MdmRecordStatus resolveStatus(String status) {
    if (!StringUtils.hasText(status)) {
      return null;
    }
    try {
      return MdmRecordStatus.valueOf(status);
    } catch (IllegalArgumentException exception) {
      throw new MdmException(MdmErrorCode.INVALID_STATUS, status);
    }
  }
}
