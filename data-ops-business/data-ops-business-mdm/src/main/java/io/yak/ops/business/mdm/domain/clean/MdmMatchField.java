package io.yak.ops.business.mdm.domain.clean;

/** 去重规则中的一个匹配字段:实体属性编码 + 匹配方式。 */
public record MdmMatchField(String attrCode, MdmMatchType matchType) {}
