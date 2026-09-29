package io.yak.ops.business.mdm.controller.v1.vo;

import io.yak.ops.business.mdm.domain.collect.MdmCollectLink;

/** 采集落地链路视图(R1)。 */
public record MdmCollectLinkVO(
    Long id,
    Long entityId,
    Long sourceId,
    Long datasourceId,
    String landingTable,
    Long jobDefinitionId) {

  public static MdmCollectLinkVO from(MdmCollectLink link) {
    return new MdmCollectLinkVO(
        link.id(),
        link.entityId(),
        link.sourceId(),
        link.datasourceId(),
        link.landingTable(),
        link.jobDefinitionId());
  }
}
