package io.yak.ops.business.digitalscreen.application;

import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.business.digitalscreen.domain.DigitalScreen;
import io.yak.ops.business.digitalscreen.domain.DigitalScreenVersion;
import io.yak.ops.business.digitalscreen.publication.DigitalScreenPublisher;
import io.yak.ops.business.digitalscreen.repository.DigitalScreenRepository;
import io.yak.ops.business.digitalscreen.repository.DigitalScreenVersionRepository;
import io.yak.ops.business.digitalscreen.version.DigitalScreenVersionReader;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Stable Digital Screen application facade for draft editing and publication history. */
@Service
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
public class DigitalScreenApplicationService {

  private static final int MAX_NAME_LENGTH = 200;
  private static final int MAX_DESCRIPTION_LENGTH = 2000;
  private static final int MAX_TEMPLATE_ID_LENGTH = 128;
  private static final String COPY_SUFFIX = " - 副本";

  private final DigitalScreenRepository repository;
  private final DigitalScreenVersionRepository versionRepository;
  private final DigitalScreenPublisher publisher;
  private final DigitalScreenVersionReader versionReader;

  public List<DigitalScreen> list() {
    return repository.list();
  }

  public long count() {
    return repository.count();
  }

  public DigitalScreen get(long id) {
    return repository.findById(id).orElseThrow(() -> notFound(id));
  }

  public List<DigitalScreenVersion> versions(long id) {
    return versionReader.versions(id);
  }

  public DigitalScreenVersion version(long id, int versionNo) {
    return versionReader.version(id, versionNo);
  }

  public DigitalScreenVersion published(long id) {
    return versionReader.published(id);
  }

  @Transactional
  public DigitalScreen create(CreateDigitalScreenCommand command) {
    return repository.insert(
        normalizeName(command.name()),
        normalizeDescription(command.description()),
        normalizeTemplateId(command.templateId()),
        1,
        normalizeBindings(command.bindings()));
  }

  @Transactional
  public DigitalScreen update(long id, UpdateDigitalScreenCommand command) {
    DigitalScreen current = get(id);
    String name = command.name() == null ? current.name() : normalizeName(command.name());
    String description = command.description() == null
        ? current.description()
        : normalizeDescription(command.description());
    Map<String, Object> bindings = command.bindings() == null
        ? current.bindings()
        : normalizeBindings(command.bindings());
    return repository.update(id, name, description, bindings);
  }

  public DigitalScreen publish(long id) {
    return publisher.publish(id);
  }

  public DigitalScreen offline(long id) {
    return publisher.offline(id);
  }

  public DigitalScreen rollback(long id, int versionNo) {
    return publisher.rollback(id, versionNo);
  }

  @Transactional
  public DigitalScreen duplicate(long id) {
    DigitalScreen source = get(id);
    return repository.insert(
        copyName(source.name()),
        source.description(),
        source.templateId(),
        source.templateVersion(),
        source.bindings());
  }

  @Transactional
  public void delete(long id) {
    get(id);
    versionRepository.deleteByScreenId(id);
    if (!repository.deleteById(id)) throw notFound(id);
  }

  private String normalizeName(String value) {
    String name = value == null ? "" : value.trim();
    if (name.isEmpty()) throw new IllegalArgumentException("请输入大屏名称");
    if (name.length() > MAX_NAME_LENGTH) {
      throw new IllegalArgumentException("大屏名称不能超过 " + MAX_NAME_LENGTH + " 个字符");
    }
    return name;
  }

  private String normalizeDescription(String value) {
    if (value == null) return null;
    String description = value.trim();
    if (description.isEmpty()) return null;
    if (description.length() > MAX_DESCRIPTION_LENGTH) {
      throw new IllegalArgumentException("大屏描述不能超过 " + MAX_DESCRIPTION_LENGTH + " 个字符");
    }
    return description;
  }

  private String normalizeTemplateId(String value) {
    String templateId = value == null ? "" : value.trim();
    if (templateId.isEmpty()) throw new IllegalArgumentException("请选择大屏模板");
    if (templateId.length() > MAX_TEMPLATE_ID_LENGTH) {
      throw new IllegalArgumentException("大屏模板标识不能超过 " + MAX_TEMPLATE_ID_LENGTH + " 个字符");
    }
    return templateId;
  }

  private Map<String, Object> normalizeBindings(Map<String, Object> bindings) {
    return bindings == null ? Map.of() : new LinkedHashMap<>(bindings);
  }

  private String copyName(String sourceName) {
    int available = MAX_NAME_LENGTH - COPY_SUFFIX.length();
    String base = sourceName.length() <= available ? sourceName : sourceName.substring(0, available);
    return base + COPY_SUFFIX;
  }

  private IllegalArgumentException notFound(long id) {
    return new IllegalArgumentException("数字化大屏不存在或已被删除：" + id);
  }
}
