package io.yak.ops.business.development.dataservice;

import io.yak.ops.business.dataservice.management.DataServiceManager;
import io.yak.ops.business.dataservice.publication.DataServicePublicationReader;
import io.yak.ops.business.dataservice.publication.DataServicePublisher;
import io.yak.ops.business.dataservice.publication.PublicationSettings;
import io.yak.ops.business.dataservice.publication.PublicationState;
import io.yak.ops.business.dataservice.publication.PublishRequest;
import io.yak.ops.business.dataservice.query.DataServiceView;
import io.yak.ops.business.dataservice.query.DataServiceViewFactory;
import io.yak.ops.business.development.domain.DevelopmentNode;
import io.yak.ops.business.development.domain.DevelopmentNodeType;
import io.yak.ops.business.development.repository.DevelopmentNodeRepository;
import org.springframework.stereotype.Service;

/** Owner-context publication boundary for Data Service Nodes. */
@Service
public class DevelopmentDataServicePublicationService {

  public static final String SOURCE_TYPE = "DATA_DEVELOPMENT_DATA_SERVICE";

  private final DevelopmentNodeRepository nodes;
  private final DataServicePublicationReader publicationReader;
  private final DataServicePublisher publisher;
  private final DataServiceManager manager;
  private final DataServiceViewFactory viewFactory;

  public DevelopmentDataServicePublicationService(
      DevelopmentNodeRepository nodes,
      DataServicePublicationReader publicationReader,
      DataServicePublisher publisher,
      DataServiceManager manager,
      DataServiceViewFactory viewFactory) {
    this.nodes = nodes;
    this.publicationReader = publicationReader;
    this.publisher = publisher;
    this.manager = manager;
    this.viewFactory = viewFactory;
  }

  public PublicationState state(long nodeId) {
    requireDataServiceNode(nodeId);
    return publicationReader.state(SOURCE_TYPE, String.valueOf(nodeId));
  }

  public DataServiceView online(long nodeId) {
    requireDataServiceNode(nodeId);
    PublicationState current = publicationReader.state(SOURCE_TYPE, String.valueOf(nodeId));
    if (!current.published()) {
      return publisher.publish(
          new PublishRequest(
              SOURCE_TYPE,
              String.valueOf(nodeId),
              null,
              null,
              null,
              null,
              Boolean.TRUE,
              null));
    }

    DataServiceView detail = requireRuntime(current);
    if (current.updateAvailable()) {
      detail = publisher.republish(
          detail.id(),
          new PublicationSettings(null, null, null, null, Boolean.TRUE, null));
    } else if (!Boolean.TRUE.equals(detail.enabled())) {
      detail = viewFactory.view(manager.setEnabled(detail.id(), true));
    }
    return detail;
  }

  public DataServiceView offline(long nodeId) {
    requireDataServiceNode(nodeId);
    PublicationState current = publicationReader.state(SOURCE_TYPE, String.valueOf(nodeId));
    DataServiceView detail = requireRuntime(current);
    if (!Boolean.FALSE.equals(detail.enabled())) {
      detail = viewFactory.view(manager.setEnabled(detail.id(), false));
    }
    return detail;
  }

  private DevelopmentNode requireDataServiceNode(long nodeId) {
    DevelopmentNode node = nodes.findById(nodeId)
        .orElseThrow(() -> new IllegalArgumentException("数据开发节点不存在：" + nodeId));
    if (node.nodeType() != DevelopmentNodeType.DATA_SERVICE) {
      throw new IllegalArgumentException("当前节点不是 Data Service Node：" + nodeId);
    }
    return node;
  }

  private DataServiceView requireRuntime(PublicationState state) {
    if (state == null || !state.published() || state.detail() == null) {
      throw new IllegalStateException("Data Service Node 尚未上线 Runtime");
    }
    return state.detail();
  }
}
