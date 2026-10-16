package io.yak.ops.business.sync.offline.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.business.sync.offline.dao.model.OfflineOdsAppendFencePO;

/** Atomic DB-unique ODS target reservation; never released on UNKNOWN or failure. */
public interface OfflineOdsAppendFenceMapper extends BaseMapper<OfflineOdsAppendFencePO> {}
