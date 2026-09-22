package io.yak.ops.business.sync.offline.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.annotation.TableName;
import io.yak.framework.common.PageData;
import io.yak.ops.business.sync.offline.backfill.OfflineBackfillDispatcher;
import io.yak.ops.business.sync.offline.backfill.OfflineBackfillPlanner;
import io.yak.ops.business.sync.offline.backfill.OfflineBackfillService;
import io.yak.ops.business.sync.offline.controller.OfflineBackfillController;
import io.yak.ops.business.sync.offline.controller.OfflineControlPlaneController;
import io.yak.ops.business.sync.offline.controller.OfflineJobDefinitionController;
import io.yak.ops.business.sync.offline.controller.OfflineJobExecutionController;
import io.yak.ops.business.sync.offline.cursor.OfflineCursorGateway;
import io.yak.ops.business.sync.offline.cursor.OfflineCursorManager;
import io.yak.ops.business.sync.offline.dao.OfflineBatchExecutionDao;
import io.yak.ops.business.sync.offline.dao.OfflineExecutionEventDao;
import io.yak.ops.business.sync.offline.dao.OfflineJobDefinitionDao;
import io.yak.ops.business.sync.offline.dao.OfflineJobExecutionDao;
import io.yak.ops.business.sync.offline.definition.OfflineJobDefinitionService;
import io.yak.ops.business.sync.offline.domain.OfflineDefinitionQuery;
import io.yak.ops.business.sync.offline.execution.OfflineBatchRuntime;
import io.yak.ops.business.sync.offline.execution.OfflineExecutionAttemptFactory;
import io.yak.ops.business.sync.offline.execution.OfflineExecutionClaimManager;
import io.yak.ops.business.sync.offline.execution.OfflineExecutionCoordinator;
import io.yak.ops.business.sync.offline.execution.OfflineExecutionScopeValidator;
import io.yak.ops.business.sync.offline.execution.OfflineExecutionStateManager;
import io.yak.ops.business.sync.offline.execution.OfflineExistingBatchClaimManager;
import io.yak.ops.business.sync.offline.execution.OfflineJobExecutionService;
import io.yak.ops.business.sync.offline.execution.adapter.OfflineBatchScopeExecutionAdapter;
import io.yak.ops.business.sync.offline.execution.query.OfflineExecutionLogQuery;
import io.yak.ops.business.sync.offline.execution.query.OfflineExecutionQuery;
import io.yak.ops.business.sync.offline.reconcile.OfflineExecutionReconciler;
import io.yak.ops.business.sync.offline.repository.OfflineBatchExecutionRepository;
import io.yak.ops.business.sync.offline.repository.OfflineExecutionEventRepository;
import io.yak.ops.business.sync.offline.repository.OfflineJobDefinitionRepository;
import io.yak.ops.business.sync.offline.repository.OfflineJobExecutionRepository;
import io.yak.ops.business.sync.offline.repository.OfflineScheduleRepository;
import io.yak.ops.business.sync.offline.schedule.OfflineScheduleExecutionGateway;
import io.yak.ops.business.sync.offline.schedule.OfflineScheduleHandler;
import io.yak.ops.common.bean.po.sync.offline.OfflineBatchExecutionPO;
import io.yak.ops.common.bean.po.sync.offline.OfflineExecutionEventPO;
import io.yak.ops.common.bean.po.sync.offline.OfflineJobDefinitionPO;
import io.yak.ops.common.bean.po.sync.offline.OfflineJobExecutionPO;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

class OfflineSyncLayeringConventionTest {

  private static final Set<String> EXECUTION_INTERNAL_IMPORTS =
      Set.of(
          "io.yak.ops.business.sync.offline.execution.OfflineExecutionCoordinator",
          "io.yak.ops.business.sync.offline.execution.OfflineExecutionClaimManager",
          "io.yak.ops.business.sync.offline.execution.OfflineExistingBatchClaimManager",
          "io.yak.ops.business.sync.offline.execution.OfflineExecutionAttemptFactory",
          "io.yak.ops.business.sync.offline.execution.OfflineExecutionStateManager",
          "io.yak.ops.business.sync.offline.execution.OfflineBatchRuntime",
          "io.yak.ops.business.sync.offline.execution.query.",
          "io.yak.ops.business.sync.offline.execution.adapter.");

  private static final Set<String> LEGACY_ROLE_NAMES =
      Set.of(
          "OfflineExecutionOrchestrator",
          "OfflineExecutionClaimService",
          "OfflineBatchRuntimeService",
          "OfflineCursorService",
          "OfflineExecutionReadService",
          "OfflineExecutionLogService");

  private static final Set<String> TRANSITIONAL_COMPATIBILITY_NAMES =
      Set.of(
          "LegacyBatchTriggerCompatibilityMapper",
          "LegacyOfflineExecutionCompatibilityMapper",
          "OfflineExecutionControlRepository",
          "OfflineExecutionIdempotencyRepository");

  @Test
  void controllersDependOnlyOnStableApplicationFacades() {
    Set<Class<?>> facades =
        Set.of(
            OfflineJobDefinitionService.class,
            OfflineJobExecutionService.class,
            OfflineBackfillService.class);

    for (Class<?> type :
        List.of(
            OfflineJobDefinitionController.class,
            OfflineJobExecutionController.class,
            OfflineControlPlaneController.class,
            OfflineBackfillController.class)) {
      for (Field field : type.getDeclaredFields()) {
        assertThat(field.getType())
            .as(
                "%s.%s must depend on a stable Application Facade",
                type.getSimpleName(),
                field.getName())
            .isIn(facades);
      }
    }
  }

  @Test
  void serviceStereotypeIsReservedForApplicationFacades() {
    for (Class<?> facade :
        List.of(
            OfflineJobDefinitionService.class,
            OfflineJobExecutionService.class,
            OfflineBackfillService.class)) {
      assertThat(facade.getAnnotation(Service.class))
          .as("%s must remain an Application Service", facade.getSimpleName())
          .isNotNull();
    }

    for (Class<?> internal :
        List.of(
            OfflineExecutionCoordinator.class,
            OfflineExecutionClaimManager.class,
            OfflineExistingBatchClaimManager.class,
            OfflineExecutionAttemptFactory.class,
            OfflineExecutionStateManager.class,
            OfflineBatchRuntime.class,
            OfflineCursorManager.class,
            OfflineExecutionQuery.class,
            OfflineExecutionLogQuery.class,
            OfflineBackfillPlanner.class)) {
      assertThat(internal.getAnnotation(Component.class))
          .as("%s must remain an internal role component", internal.getSimpleName())
          .isNotNull();
      assertThat(internal.getAnnotation(Service.class))
          .as("%s must not masquerade as an Application Service", internal.getSimpleName())
          .isNull();
    }
  }

  @Test
  void coreResponsibilitiesStayDecomposed() {
    List<Class<?>> coordinatorDependencies = fieldTypes(OfflineExecutionCoordinator.class);
    assertThat(coordinatorDependencies)
        .contains(OfflineExecutionStateManager.class)
        .doesNotContain(OfflineJobDefinitionRepository.class, OfflineExecutionEventRepository.class);

    List<Class<?>> claimDependencies = fieldTypes(OfflineExecutionClaimManager.class);
    assertThat(claimDependencies)
        .contains(OfflineExistingBatchClaimManager.class, OfflineExecutionAttemptFactory.class);

    List<Class<?>> backfillDependencies = fieldTypes(OfflineBackfillService.class);
    assertThat(backfillDependencies)
        .contains(OfflineBackfillPlanner.class)
        .doesNotContain(
            OfflineCursorManager.class,
            OfflineScheduleRepository.class,
            OfflineBatchScopeExecutionAdapter.class);
  }

  @Test
  void explicitSubsystemBoundariesStayImplemented() {
    assertThat(OfflineCursorGateway.class.isAssignableFrom(OfflineCursorManager.class)).isTrue();
    assertThat(
            OfflineExecutionScopeValidator.class.isAssignableFrom(
                OfflineBatchScopeExecutionAdapter.class))
        .isTrue();
    assertThat(
            OfflineScheduleExecutionGateway.class.isAssignableFrom(
                OfflineJobExecutionService.class))
        .isTrue();
  }

  @Test
  void backgroundEntrypointsUseOnlyDeclaredExecutionCorridors() {
    for (Class<?> type :
        List.of(OfflineBackfillDispatcher.class, OfflineExecutionReconciler.class)) {
      List<Class<?>> dependencies = fieldTypes(type);
      assertThat(dependencies)
          .as("%s must enter execution through OfflineJobExecutionService", type.getSimpleName())
          .contains(OfflineJobExecutionService.class);
      assertNoExecutionInternals(type, dependencies);
    }

    List<Class<?>> scheduleDependencies = fieldTypes(OfflineScheduleHandler.class);
    assertThat(scheduleDependencies)
        .contains(OfflineScheduleExecutionGateway.class)
        .doesNotContain(OfflineJobExecutionService.class);
    assertNoExecutionInternals(OfflineScheduleHandler.class, scheduleDependencies);
  }

  @Test
  void nonExecutionProductionCodeDoesNotImportExecutionInternals() throws IOException {
    Path root = productionRoot();
    try (Stream<Path> paths = Files.walk(root)) {
      for (Path file : paths.filter(path -> path.toString().endsWith(".java")).toList()) {
        String relative = root.relativize(file).toString().replace('\\', '/');
        if (relative.startsWith("execution/")) {
          continue;
        }

        String source = Files.readString(file);
        for (String forbidden : EXECUTION_INTERNAL_IMPORTS) {
          assertThat(source)
              .as("%s must not import execution internal API %s", relative, forbidden)
              .doesNotContain("import " + forbidden);
        }
      }
    }
  }

  @Test
  void productionSourceDoesNotReintroduceLegacyRoleNames() throws IOException {
    Path root = productionRoot();
    try (Stream<Path> paths = Files.walk(root)) {
      for (Path file : paths.filter(path -> path.toString().endsWith(".java")).toList()) {
        String relative = root.relativize(file).toString().replace('\\', '/');
        String source = Files.readString(file);
        for (String legacyRoleName : LEGACY_ROLE_NAMES) {
          assertThat(source)
              .as("%s must use current role vocabulary instead of %s", relative, legacyRoleName)
              .doesNotContain(legacyRoleName);
        }
      }
    }
  }

  @Test
  void productionSourceDoesNotContainTransitionalCompatibilityLayer() throws IOException {
    Path root = productionRoot();
    try (Stream<Path> paths = Files.walk(root)) {
      for (Path file : paths.filter(path -> path.toString().endsWith(".java")).toList()) {
        String relative = root.relativize(file).toString().replace('\\', '/');
        String source = Files.readString(file);
        assertThat(relative)
            .as("transitional domain compat package must stay removed")
            .doesNotStartWith("domain/compat/");
        assertThat(source)
            .as("%s must not reintroduce @Deprecated transitional code", relative)
            .doesNotContain("@Deprecated");
        for (String compatibilityName : TRANSITIONAL_COMPATIBILITY_NAMES) {
          assertThat(source)
              .as(
                  "%s must not reintroduce transitional compatibility type %s",
                  relative,
                  compatibilityName)
              .doesNotContain(compatibilityName);
        }
      }
    }
  }

  @Test
  void repositoriesExposeOnlyDomainContracts() {
    for (Class<?> type :
        List.of(
            OfflineBatchExecutionRepository.class,
            OfflineJobDefinitionRepository.class,
            OfflineJobExecutionRepository.class,
            OfflineExecutionEventRepository.class,
            OfflineScheduleRepository.class)) {
      assertMethodsAvoid(
          type,
          ".bean.dto.",
          ".bean.vo.",
          ".bean.po.",
          "com.baomidou.mybatisplus");
    }
  }

  @Test
  void attemptPersistenceDoesNotExposeLegacyTaskRuntimeOrRetroactiveBinding() {
    List<String> repositoryMethods =
        Arrays.stream(OfflineJobExecutionRepository.class.getMethods())
            .map(Method::getName)
            .toList();
    List<String> daoMethods =
        Arrays.stream(OfflineJobExecutionDao.class.getMethods()).map(Method::getName).toList();

    assertThat(repositoryMethods).doesNotContain("hasActiveExecution", "bindBatch");
    assertThat(daoMethods).doesNotContain("hasActiveExecution", "bindBatch");
  }

  @Test
  void repositoriesUseSharedPageData() throws Exception {
    Method definitions =
        OfflineJobDefinitionRepository.class.getMethod("page", OfflineDefinitionQuery.class);
    Method executions =
        OfflineJobExecutionRepository.class.getMethod(
            "page", io.yak.ops.business.sync.offline.domain.OfflineExecutionQuery.class);
    assertThat(((ParameterizedType) definitions.getGenericReturnType()).getRawType())
        .isEqualTo(PageData.class);
    assertThat(((ParameterizedType) executions.getGenericReturnType()).getRawType())
        .isEqualTo(PageData.class);
  }

  @Test
  void daosDoNotDependOnTransportModels() {
    for (Class<?> type :
        List.of(
            OfflineBatchExecutionDao.class,
            OfflineJobDefinitionDao.class,
            OfflineJobExecutionDao.class,
            OfflineExecutionEventDao.class)) {
      assertMethodsAvoid(type, ".bean.dto.", ".bean.vo.");
    }
  }

  @Test
  void batchPersistenceKeepsEstablishedPhysicalTables() throws Exception {
    assertTable(OfflineBatchExecutionPO.class, "yak_offline_batch_execution");
    assertTable(OfflineJobDefinitionPO.class, "yak_offline_job_definition");
    assertTable(OfflineJobExecutionPO.class, "yak_offline_job_execution");
    assertTable(OfflineExecutionEventPO.class, "yak_offline_execution_event");

    Field batchId = OfflineJobExecutionPO.class.getDeclaredField("batchId");
    assertThat(batchId.getType()).isEqualTo(Long.class);
  }

  private void assertNoExecutionInternals(Class<?> owner, List<Class<?>> dependencies) {
    for (Class<?> dependency : dependencies) {
      String name = dependency.getName();
      assertThat(name)
          .as("%s must not depend on an execution internal component", owner.getSimpleName())
          .doesNotContain("OfflineExecutionCoordinator")
          .doesNotContain("OfflineExecutionClaimManager")
          .doesNotContain("OfflineExistingBatchClaimManager")
          .doesNotContain("OfflineExecutionStateManager")
          .doesNotContain("OfflineBatchRuntime")
          .doesNotContain(".execution.query.")
          .doesNotContain(".execution.adapter.");
    }
  }

  private List<Class<?>> fieldTypes(Class<?> type) {
    return Arrays.stream(type.getDeclaredFields()).map(Field::getType).toList();
  }

  private Path productionRoot() {
    Path moduleRoot = Path.of("src/main/java/io/yak/ops/business/sync/offline");
    if (Files.isDirectory(moduleRoot)) {
      return moduleRoot;
    }

    Path repositoryRoot =
        Path.of(
            "yak-ops-business",
            "yak-ops-business-sync",
            "yak-ops-business-sync-offline",
            "src",
            "main",
            "java",
            "io",
            "yak",
            "ops",
            "business",
            "sync",
            "offline");
    assertThat(Files.isDirectory(repositoryRoot))
        .as("offline-sync production source root must be available to architecture test")
        .isTrue();
    return repositoryRoot;
  }

  private void assertMethodsAvoid(Class<?> owner, String... forbidden) {
    for (Method method : owner.getMethods()) {
      assertTypeAvoids(owner, method, method.getGenericReturnType(), forbidden);
      for (Type parameter : method.getGenericParameterTypes()) {
        assertTypeAvoids(owner, method, parameter, forbidden);
      }
    }
  }

  private void assertTypeAvoids(
      Class<?> owner, Method method, Type type, String... forbidden) {
    String signature = typeName(type);
    for (String packagePart : forbidden) {
      assertThat(signature)
          .as("%s.%s must not expose %s", owner.getSimpleName(), method.getName(), packagePart)
          .doesNotContain(packagePart);
    }
  }

  private String typeName(Type type) {
    if (type instanceof ParameterizedType parameterized) {
      StringBuilder value = new StringBuilder(parameterized.getRawType().getTypeName());
      for (Type argument : parameterized.getActualTypeArguments()) {
        value.append('<').append(typeName(argument)).append('>');
      }
      return value.toString();
    }
    return type.getTypeName();
  }

  private void assertTable(Class<?> poType, String tableName) {
    TableName mapping = poType.getAnnotation(TableName.class);
    assertThat(mapping).as(poType.getSimpleName() + " must declare @TableName").isNotNull();
    assertThat(mapping.value()).isEqualTo(tableName);
  }
}
