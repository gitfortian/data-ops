package io.yak.ops.business.semantic.controller.v1.converter;

import io.yak.framework.common.PagingData;
import io.yak.ops.business.semantic.controller.v1.vo.BusinessProcessVO;
import io.yak.ops.business.semantic.api.BusinessProcess;
import org.springframework.stereotype.Component;

/** Converts business-process domain objects to API view objects. */
@Component
public class BusinessProcessViewConverter {

  public BusinessProcessVO toView(BusinessProcess process) {
    BusinessProcessVO vo = new BusinessProcessVO();
    vo.setId(process.id());
    vo.setCode(process.code());
    vo.setName(process.name());
    vo.setDomainId(process.domainId());
    vo.setGrain(process.grain());
    vo.setBizType(process.bizType());
    vo.setOwner(process.owner());
    vo.setDescription(process.description());
    vo.setSortOrder(process.sortOrder());
    vo.setCreatedBy(process.createdBy());
    vo.setCreateTime(process.createTime());
    vo.setUpdateTime(process.updateTime());
    return vo;
  }

  public PagingData<BusinessProcessVO> page(io.yak.framework.common.PageData<BusinessProcess> page) {
    return PagingData.from(page.map(this::toView));
  }
}
