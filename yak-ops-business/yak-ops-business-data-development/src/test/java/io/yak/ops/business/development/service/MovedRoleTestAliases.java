package io.yak.ops.business.development.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.dataset.DevelopmentDatasetFacade;
import io.yak.ops.business.development.repository.DevelopmentDirectoryRepository;
import io.yak.ops.business.development.repository.DevelopmentNodeRepository;
import io.yak.ops.business.development.repository.DevelopmentTaskDraftRepository;
import io.yak.ops.business.development.repository.DevelopmentTaskExecutionRepository;
import io.yak.ops.business.development.repository.DevelopmentTaskRevisionRepository;
import io.yak.ops.business.job.task.TaskExecutionGateway;
import io.yak.ops.business.taskcatalog.service.TaskCatalogService;
import io.yak.ops.core.plugin.task.TaskPluginRegistry;

/** Test-only source-compatibility aliases while production services move to role packages. */
final class MovedRoleTestAliases {
  private MovedRoleTestAliases() {}
}

class DataDevelopmentTaskRevisionProvider
    extends io.yak.ops.business.development.task.DataDevelopmentTaskRevisionProvider {
  DataDevelopmentTaskRevisionProvider(
      DevelopmentTaskRevisionRepository repository,
      DevelopmentNodeRepository nodes) {
    super(repository, nodes);
  }
}

class DevelopmentTaskService extends io.yak.ops.business.development.task.DevelopmentTaskService {
  DevelopmentTaskService(
      DevelopmentNodeRepository nodes,
      DevelopmentTaskDraftRepository drafts,
      DevelopmentTaskRevisionRepository revisions,
      TaskCatalogService catalog,
      TaskPluginRegistry plugins,
      ObjectMapper mapper) {
    super(nodes, drafts, revisions, catalog, plugins, mapper);
  }
}

class DevelopmentTaskExecutionService
    extends io.yak.ops.business.development.execution.DevelopmentTaskExecutionService {
  DevelopmentTaskExecutionService(
      DevelopmentTaskExecutionRepository repository,
      ObjectMapper mapper) {
    super(repository, mapper);
  }
}

class DevelopmentTaskRunService
    extends io.yak.ops.business.development.execution.DevelopmentTaskRunService {
  DevelopmentTaskRunService(
      DevelopmentNodeRepository nodes,
      TaskExecutionGateway gateway,
      DevelopmentTaskExecutionService executions,
      ObjectMapper mapper) {
    super(nodes, gateway, executions, mapper);
  }
}

class DevelopmentNodeService extends io.yak.ops.business.development.node.DevelopmentNodeService {
  DevelopmentNodeService(
      DevelopmentNodeRepository nodes,
      DevelopmentDirectoryRepository directories,
      TaskCatalogService catalog) {
    super(nodes, directories, catalog);
  }
}

class DevelopmentDirectoryService
    extends io.yak.ops.business.development.directory.DevelopmentDirectoryService {
  DevelopmentDirectoryService(
      DevelopmentDirectoryRepository directories,
      DevelopmentNodeRepository nodes) {
    super(directories, nodes);
  }
}

class DevelopmentDatasetNodeService
    extends io.yak.ops.business.development.dataset.DevelopmentDatasetNodeService {
  DevelopmentDatasetNodeService(
      DevelopmentNodeRepository nodes,
      TaskCatalogService catalog,
      DevelopmentDatasetFacade datasets) {
    super(nodes, catalog, datasets);
  }
}

class DevelopmentReleaseService
    extends io.yak.ops.business.development.release.DevelopmentReleaseService {
  DevelopmentReleaseService(
      TaskCatalogService catalog,
      DevelopmentTaskRevisionRepository revisions) {
    super(catalog, revisions);
  }
}
