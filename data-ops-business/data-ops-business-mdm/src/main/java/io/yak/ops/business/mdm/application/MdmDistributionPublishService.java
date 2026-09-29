package io.yak.ops.business.mdm.application;

import io.yak.ops.business.dataservice.publication.DataServicePublicationReader;
import io.yak.ops.business.dataservice.publication.DataServicePublisher;
import io.yak.ops.business.dataservice.publication.PublicationSettings;
import io.yak.ops.business.dataservice.publication.PublicationState;
import io.yak.ops.business.dataservice.publication.PublishRequest;
import io.yak.ops.business.dataservice.query.DataServiceView;
import io.yak.ops.business.mdm.domain.distribution.MdmDistribution;
import io.yak.ops.business.mdm.exception.MdmException;
import io.yak.ops.business.mdm.distribution.MdmDataServiceSourceProvider;
import io.yak.ops.common.enums.mdm.MdmErrorCode;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * 分发 API 的发布编排(R5):把「执行分发 = 推送至 API 发布态」的语义落到 data-service。
 *
 * <p>发布来源由 {@link MdmDataServiceSourceProvider} 供数,本服务只负责幂等地把一条分发配置
 * 上线为运行态 API(publish→republish→ensureEnabled 三态收敛),不复制任何 SQL 口径。
 * 数据服务模块缺席时({@link DataServicePublisher} 未装配)抛 44037,与「数据质量未启用」同一
 * 优雅降级范式;调用方(手动执行/定时执行)据此写失败而非假成功时间戳(review P0-1.8)。
 */
@Service
public class MdmDistributionPublishService {

  private final ObjectProvider<DataServicePublisher> publishers;
  private final ObjectProvider<DataServicePublicationReader> readers;

  public MdmDistributionPublishService(
      ObjectProvider<DataServicePublisher> publishers,
      ObjectProvider<DataServicePublicationReader> readers) {
    this.publishers = publishers;
    this.readers = readers;
  }

  /** 数据服务发布能力是否可用(缺席时前端隐藏发布入口、执行走失败分支)。 */
  public boolean available() {
    return publishers.getIfAvailable() != null && readers.getIfAvailable() != null;
  }

  /** 幂等上线:未发布→publish;有更新→republish;已发布但停用→启用。返回运行态视图与是否真发生了发布。 */
  public PublishOutcome online(MdmDistribution config) {
    DataServicePublisher publisher = publishers.getIfAvailable();
    DataServicePublicationReader reader = readers.getIfAvailable();
    if (publisher == null || reader == null) {
      throw new MdmException(MdmErrorCode.DATA_SERVICE_DISABLED, "数据服务模块未启用,无法发布分发 API");
    }
    String ref = sourceRef(config);
    PublicationState state = reader.state(MdmDataServiceSourceProvider.SOURCE_TYPE, ref);
    if (!state.published()) {
      return new PublishOutcome(
          publisher.publish(
              new PublishRequest(
                  MdmDataServiceSourceProvider.SOURCE_TYPE, ref, null, null, null, null,
                  Boolean.TRUE, null)),
          true);
    }
    DataServiceView view = state.detail();
    if (state.updateAvailable()) {
      return new PublishOutcome(
          publisher.republish(
              view.id(), new PublicationSettings(null, null, null, null, Boolean.TRUE, null)),
          true);
    }
    if (!Boolean.TRUE.equals(view.enabled())) {
      return new PublishOutcome(
          publisher.republish(
              view.id(), new PublicationSettings(null, null, null, null, Boolean.TRUE, null)),
          true);
    }
    return new PublishOutcome(view, false);
  }

  /** 发布结果:{@code changed} 表示本次真的发生发布/重发布(口径或启停变了),纯幂等复查为 false。 */
  public record PublishOutcome(DataServiceView view, boolean changed) {}

  /** 反查发布态(仅读,不触发发布);数据服务缺席或未发布时返回空。 */
  public Optional<DataServiceView> currentView(MdmDistribution config) {
    DataServicePublicationReader reader = readers.getIfAvailable();
    if (reader == null) {
      return Optional.empty();
    }
    PublicationState state = reader.state(MdmDataServiceSourceProvider.SOURCE_TYPE, sourceRef(config));
    return state.published() && state.detail() != null
        ? Optional.of(state.detail())
        : Optional.empty();
  }

  private static String sourceRef(MdmDistribution config) {
    return String.valueOf(config.id());
  }
}
