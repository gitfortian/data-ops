package io.yak.ops.business.dataservice.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.dataservice.dao.mapper.DataServiceDocumentationMapper;
import io.yak.ops.business.dataservice.dao.model.DataServiceDocumentationPO;
import io.yak.ops.business.dataservice.domain.documentation.DataServiceDocumentation;
import io.yak.ops.business.dataservice.domain.documentation.DataServiceDocumentation.ParameterDoc;
import io.yak.ops.business.dataservice.domain.documentation.DataServiceDocumentation.ResponseFieldDoc;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
public class DataServiceDocumentationRepositoryAdapter implements DataServiceDocumentationRepository {
  private final DataServiceDocumentationMapper mapper;
  private final ObjectMapper objectMapper;

  @Override
  public Optional<DataServiceDocumentation> findByApiId(Long apiId) {
    return Optional.ofNullable(apiId == null ? null : mapper.selectById(apiId)).map(this::toDomain);
  }

  @Override
  public DataServiceDocumentation save(DataServiceDocumentation documentation) {
    DataServiceDocumentationPO po = mapper.selectById(documentation.apiId());
    boolean creating = po == null;
    if (creating) { po = new DataServiceDocumentationPO(); po.setApiId(documentation.apiId()); }
    po.setSqlHash(documentation.sqlHash());
    po.setParameterSchemaJson(write(documentation.parameters()));
    po.setResponseSchemaJson(write(documentation.responseFields()));
    po.setUpdateTime(documentation.updateTime());
    if (creating) mapper.insert(po); else mapper.updateById(po);
    return toDomain(po);
  }

  @Override
  public void delete(Long apiId) { if (apiId != null) mapper.deleteById(apiId); }

  private DataServiceDocumentation toDomain(DataServiceDocumentationPO po) {
    return new DataServiceDocumentation(
        po.getApiId(), po.getSqlHash(),
        read(po.getParameterSchemaJson(), new TypeReference<List<ParameterDoc>>() {}),
        read(po.getResponseSchemaJson(), new TypeReference<List<ResponseFieldDoc>>() {}),
        po.getUpdateTime());
  }

  private String write(Object value) {
    try { return objectMapper.writeValueAsString(value); }
    catch (Exception exception) { throw new IllegalStateException("API 文档序列化失败", exception); }
  }

  private <T> T read(String value, TypeReference<T> type) {
    try { return objectMapper.readValue(value == null || value.isBlank() ? "[]" : value, type); }
    catch (Exception exception) { throw new IllegalStateException("API 文档内容无法解析", exception); }
  }
}
