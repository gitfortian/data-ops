package io.yak.ops.business.resource.controller.v1.converter;

import static org.assertj.core.api.Assertions.assertThat;

import io.yak.ops.business.resource.namespace.ResourceNamePolicy;
import io.yak.ops.common.bean.dto.resource.ResourceQueryDTO;
import io.yak.ops.common.enums.resource.ResourceNodeType;
import org.junit.jupiter.api.Test;

class ResourceRequestConverterTest {
  private final ResourceRequestConverter converter = new ResourceRequestConverter(new ResourceNamePolicy());

  @Test
  void mapsQueryDtoWithoutMutatingTransportInput() {
    ResourceQueryDTO request = new ResourceQueryDTO();
    request.setPageNo(2);
    request.setPageSize(30);
    request.setParentId(9L);
    request.setKeyword("  report  ");
    request.setNodeType(" file ");
    var query = converter.query(request);
    assertThat(query.pageNo()).isEqualTo(2);
    assertThat(query.pageSize()).isEqualTo(30);
    assertThat(query.parentId()).isEqualTo(9L);
    assertThat(query.keyword()).isEqualTo("report");
    assertThat(query.nodeType()).isEqualTo(ResourceNodeType.FILE);
    assertThat(request.getKeyword()).isEqualTo("  report  ");
    assertThat(request.getNodeType()).isEqualTo(" file ");
  }
}
