package io.yak.ops.business.dataservice.access;

import io.yak.ops.business.dataservice.domain.access.IpAccessMode;
import java.util.List;

public record ConsumerIpAccessPolicyView(
    IpAccessMode mode,
    List<ConsumerIpAccessRuleView> rules) {}
