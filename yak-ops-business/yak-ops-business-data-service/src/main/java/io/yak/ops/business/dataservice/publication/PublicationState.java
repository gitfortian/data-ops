package io.yak.ops.business.dataservice.publication;

import io.yak.ops.business.dataservice.publication.source.DataServiceSourceProvider.SourceDescriptor;
import io.yak.ops.business.dataservice.query.DataServiceView;

public record PublicationState(
    boolean published,
    boolean updateAvailable,
    SourceDescriptor source,
    DataServiceView detail) {}
