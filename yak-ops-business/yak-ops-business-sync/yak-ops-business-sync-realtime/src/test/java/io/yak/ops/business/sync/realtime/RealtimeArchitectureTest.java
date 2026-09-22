package io.yak.ops.business.sync.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import io.yak.ops.business.sync.realtime.controller.v1.ComputeEnvironmentController;
import io.yak.ops.business.sync.realtime.controller.v1.RealtimeJobController;
import io.yak.ops.business.sync.realtime.controller.v1.mapper.RealtimeRequestMapper;
import io.yak.ops.business.sync.realtime.controller.v1.mapper.RealtimeViewMapper;
import io.yak.ops.business.sync.realtime.definition.RealtimeDefinitionManager;
import io.yak.ops.business.sync.realtime.definition.RealtimeDefinitionPublisher;
import io.yak.ops.business.sync.realtime.definition.RealtimeDefinitionValidator;
import io.yak.ops.business.sync.realtime.definition.RealtimeJobDefinitionService;
import io.yak.ops.business.sync.realtime.definition.RealtimeSourceConfigDigestCalculator;
import io.yak.ops.business.sync.realtime.definition.RealtimeYamlCodec;
import io.yak.ops.business.sync.realtime.domain.DefinitionDigest;
import io.yak.ops.business.sync.realtime.domain.DefinitionVersion;
import io.yak.ops.business.sync.realtime.domain.RealtimeJobState;
import io.yak.ops.business.sync.realtime.domain.RuntimeEnvironmentRef;
import io.yak.ops.business.sync.realtime.domain.SyncDefinition;
import io.yak.ops.business.sync.realtime.domain.SyncDefinitionDigestCalculator;
import io.yak.ops.business.sync.realtime.domain.SyncExecution;
import io.yak.ops.business.sync.realtime.domain.SyncExecutionStateMachine;
import io.yak.ops.business.sync.realtime.environment.ComputeEnvironmentConfigNormalizer;
import io.yak.ops.business.sync.realtime.environment.ComputeEnvironmentDiagnoser;
import io.yak.ops.business.sync.realtime.environment.ComputeEnvironmentManager;
import io.yak.ops.business.sync.realtime.environment.ComputeEnvironmentService;
import io.yak.ops.business.sync.realtime.environment.RealtimeRuntimeResolver;
import io.yak.ops.business.sync.realtime.execution.RealtimeExecutionCoordinator;
import io.yak.ops.business.sync.realtime.execution.RealtimeExecutionPreparation;
import io.yak.ops.business.sync.realtime.execution.RealtimeExecutionReplacementManager;
import io.yak.ops.business.sync.realtime.execution.RealtimeExecutionReservationManager;
import io.yak.ops.business.sync.realtime.execution.RealtimeExecutionStarter;
import io.yak.ops.business.sync.realtime.execution.RealtimeExecutionStateManager;
import io.yak.ops.business.sync.realtime.execution.RealtimeJobExecutionService;
import io.yak.ops.business.sync.realtime.execution.query.RealtimeJobReadModelQuery;
import io.yak.ops.business.sync.realtime.observability.RealtimeEventQuery;
import io.yak.ops.business.sync.realtime.observability.RealtimeEventStream;
import io.yak.ops.business.sync.realtime.observability.RealtimeObservabilityReader;
import io.yak.ops.business.sync.realtime.reconcile.RealtimeDeleteSafetyChecker;
import io.yak.ops.business.sync.realtime.reconcile.RealtimeReconcileCoordinator;
import io.yak.ops.business.sync.realtime.reconcile.RealtimeReconciler;
import io.yak.ops.business.sync.realtime.reconcile.RealtimeRuntimeIdentityRecovery;
import io.yak.ops.business.sync.realtime.reconcile.RealtimeRuntimeStateReconciler;
import io.yak.ops.business.sync.realtime.repository.ComputeEnvironmentStore;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobListQuery;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore;
import io.yak.ops.business.sync.realtime.repository.RealtimeRuntimeIdentityStore;
import io.yak.ops.business.sync.realtime.repository.support.CdcPipelineSpecCompatibilityMapper;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

class RealtimeArchitectureTest {

  private static final String[] CORE_FORBIDDEN = {
    "org.springframework",
    "com.fasterxml.jackson",
    "com.baomidou",
    ".controller.",
    ".service.",
    ".repository.",
    ".dao.",
    ".engine.",
    "JdbcTemplate"
  };

  @Test
  void controllersUseDeclaredStableApplicationEntries() {
    assertFieldTypesIn(
        RealtimeJobController.class,
        Set.of(
            RealtimeJobDefinitionService.class,
            RealtimeJobExecutionService.class,
            io.yak.ops.business.sync.realtime.execution.query.RealtimeJobQueryService.class,
            io.yak.ops.business.sync.realtime.observability.RealtimeObservabilityService.class,
            RealtimeRequestMapper.class,
            RealtimeViewMapper.class));

    assertFieldTypesIn(
        ComputeEnvironmentController.class,
        Set.of(ComputeEnvironmentService.class, RealtimeRequestMapper.class, RealtimeViewMapper.class));
  }

  @Test
  void stableApplicationEntriesUseServiceStereotype() {
    for (Class<?> facade :
        new Class<?>[] {
          RealtimeJobDefinitionService.class,
          RealtimeJobExecutionService.class,
          io.yak.ops.business.sync.realtime.execution.query.RealtimeJobQueryService.class,
          io.yak.ops.business.sync.realtime.observability.RealtimeObservabilityService.class,
          ComputeEnvironmentService.class
        }) {
      assertThat(facade.getAnnotation(Service.class))
          .as("%s must remain a stable application facade", facade.getSimpleName())
          .isNotNull();
    }
  }

  @Test
  void definitionSubsystemUsesRoleComponentsBehindStableFacade() {
    assertFieldsAvoid(RealtimeJobDefinitionService.class, ".service.", ".dao.", "JdbcTemplate");
    for (Class<?> internal :
        new Class<?>[] {
          RealtimeDefinitionManager.class,
          RealtimeDefinitionPublisher.class,
          RealtimeDefinitionValidator.class,
          RealtimeSourceConfigDigestCalculator.class,
          RealtimeYamlCodec.class
        }) {
      assertInternalComponent(internal, "definition");
    }
  }

  @Test
  void executionSubsystemUsesRoleComponentsBehindStableFacade() {
    assertFieldsAvoid(
        RealtimeJobExecutionService.class,
        "RealtimeJobService",
        "RealtimeJobLifecycleCoordinator",
        ".dao.",
        "JdbcTemplate");
    for (Class<?> internal :
        new Class<?>[] {
          RealtimeExecutionCoordinator.class,
          RealtimeExecutionPreparation.class,
          RealtimeExecutionReservationManager.class,
          RealtimeExecutionStateManager.class,
          RealtimeExecutionStarter.class,
          RealtimeExecutionReplacementManager.class
        }) {
      assertInternalComponent(internal, "execution");
    }
    assertThat(methodNames(RealtimeExecutionCoordinator.class))
        .contains("start", "stop", "restartExecution", "applyPublishedVersion")
        .doesNotContain("publish", "save", "delete", "reconcile");
  }

  @Test
  void reconcileSubsystemUsesExplicitRolesWithoutApplicationServiceLeakage() {
    for (Class<?> internal :
        new Class<?>[] {
          RealtimeReconcileCoordinator.class,
          RealtimeRuntimeIdentityRecovery.class,
          RealtimeRuntimeStateReconciler.class,
          RealtimeDeleteSafetyChecker.class,
          RealtimeReconciler.class
        }) {
      assertInternalComponent(internal, "reconcile");
      assertFieldsAvoid(internal, ".dao.", ".dao.mapper.", ".dao.model.", "JdbcTemplate");
    }
    assertThat(methodNames(RealtimeReconcileCoordinator.class))
        .contains("reconcile", "reconcileAll")
        .doesNotContain("start", "stop", "restartExecution", "applyPublishedVersion");
  }

  @Test
  void queryAndObservabilityStayReadOnlyBehindStableFacades() {
    Class<?> queryFacade =
        io.yak.ops.business.sync.realtime.execution.query.RealtimeJobQueryService.class;
    Class<?> observabilityFacade =
        io.yak.ops.business.sync.realtime.observability.RealtimeObservabilityService.class;

    assertFieldsAvoid(queryFacade, ".service.", ".dao.", "JdbcTemplate");
    assertFieldsAvoid(observabilityFacade, ".service.", ".reconcile.", ".dao.", "JdbcTemplate");

    for (Class<?> internal :
        new Class<?>[] {
          RealtimeJobReadModelQuery.class,
          RealtimeObservabilityReader.class,
          RealtimeEventQuery.class,
          RealtimeEventStream.class
        }) {
      assertInternalComponent(internal, "read-side");
      assertFieldsAvoid(
          internal,
          "SyncExecutionStateMachine",
          "RealtimeExecutionCoordinator",
          "RealtimeExecutionPreparation",
          "RealtimeExecutionStarter",
          "RealtimeExecutionStateManager",
          "RealtimeExecutionReservationManager",
          "RealtimeExecutionReplacementManager",
          ".reconcile.",
          ".dao.",
          ".dao.mapper.",
          ".dao.model.",
          "JdbcTemplate");
    }

    for (Class<?> readSide :
        new Class<?>[] {
          queryFacade,
          RealtimeJobReadModelQuery.class,
          observabilityFacade,
          RealtimeObservabilityReader.class,
          RealtimeEventQuery.class,
          RealtimeEventStream.class
        }) {
      assertThat(methodNames(readSide))
          .as("%s must not expose execution commands", readSide.getSimpleName())
          .doesNotContain(
              "start",
              "stop",
              "restartExecution",
              "applyPublishedVersion",
              "reconcile",
              "delete",
              "save");
    }
  }

  @Test
  void environmentSubsystemUsesExplicitRolesAndFrozenExecutionSnapshots() {
    assertFieldsAvoid(ComputeEnvironmentService.class, ".service.", ".dao.", "JdbcTemplate");
    for (Class<?> internal :
        new Class<?>[] {
          ComputeEnvironmentManager.class,
          ComputeEnvironmentConfigNormalizer.class,
          ComputeEnvironmentDiagnoser.class,
          RealtimeRuntimeResolver.class
        }) {
      assertInternalComponent(internal, "environment");
      assertFieldsAvoid(internal, ".dao.", ".dao.mapper.", ".dao.model.", "JdbcTemplate");
    }
    assertThat(methodNames(RealtimeRuntimeResolver.class))
        .contains("environment", "definition", "deployment")
        .doesNotContain("create", "update", "setDefault", "setEnabled", "delete", "diagnose");
  }

  @Test
  void persistenceCompatibilityMappingStaysInternalAndOutOfCoreDomain() {
    assertInternalComponent(CdcPipelineSpecCompatibilityMapper.class, "persistence-compatibility");
    assertFieldsAvoid(CdcPipelineSpecCompatibilityMapper.class, ".dao.", ".engine.", "JdbcTemplate");
  }

  @Test
  void controllersDependOnApplicationBoundariesInsteadOfPersistenceOrEnginePorts() {
    assertFieldsAvoid(
        RealtimeJobController.class,
        ".repository.",
        ".dao.",
        ".service.",
        "RealtimeEngineGateway",
        "JdbcTemplate");
    assertFieldsAvoid(
        ComputeEnvironmentController.class,
        ".repository.",
        ".dao.",
        ".service.",
        "RealtimeEngineGateway",
        "JdbcTemplate");
  }

  @Test
  void applicationAndInternalRolesDoNotDependOnDaoMapperPoOrJdbcTemplate() {
    for (Class<?> type :
        new Class<?>[] {
          RealtimeJobDefinitionService.class,
          RealtimeDefinitionManager.class,
          RealtimeDefinitionPublisher.class,
          RealtimeDefinitionValidator.class,
          RealtimeSourceConfigDigestCalculator.class,
          RealtimeYamlCodec.class,
          RealtimeJobExecutionService.class,
          RealtimeExecutionCoordinator.class,
          RealtimeExecutionPreparation.class,
          RealtimeExecutionReservationManager.class,
          RealtimeExecutionStateManager.class,
          RealtimeExecutionStarter.class,
          RealtimeExecutionReplacementManager.class,
          RealtimeReconcileCoordinator.class,
          RealtimeRuntimeIdentityRecovery.class,
          RealtimeRuntimeStateReconciler.class,
          RealtimeDeleteSafetyChecker.class,
          RealtimeReconciler.class,
          io.yak.ops.business.sync.realtime.execution.query.RealtimeJobQueryService.class,
          RealtimeJobReadModelQuery.class,
          io.yak.ops.business.sync.realtime.observability.RealtimeObservabilityService.class,
          RealtimeObservabilityReader.class,
          RealtimeEventQuery.class,
          RealtimeEventStream.class,
          ComputeEnvironmentService.class,
          ComputeEnvironmentManager.class,
          ComputeEnvironmentConfigNormalizer.class,
          ComputeEnvironmentDiagnoser.class,
          RealtimeRuntimeResolver.class,
          CdcPipelineSpecCompatibilityMapper.class
        }) {
      assertFieldsAvoid(type, ".dao.", ".dao.mapper.", ".dao.model.", "JdbcTemplate");
    }
  }

  @Test
  void repositoryContractsDoNotExposeDaoOrControllerTypes() {
    for (Class<?> repository :
        new Class<?>[] {
          RealtimeJobStore.class,
          RealtimeJobListQuery.class,
          RealtimeRuntimeIdentityStore.class,
          ComputeEnvironmentStore.class
        }) {
      for (Method method : repository.getDeclaredMethods()) {
        assertTypeBoundary(method.getReturnType());
        for (Class<?> parameterType : method.getParameterTypes()) {
          assertTypeBoundary(parameterType);
        }
      }
    }
  }

  @Test
  void migratedExecutionContractCannotReintroduceTaskRuntimeSidePaths() {
    assertThat(methodNames(RealtimeJobStore.class))
        .doesNotContain("desiredJobs", "hasOtherDesiredRunning", "markStarting");
    assertThat(methodNames(ComputeEnvironmentStore.class)).doesNotContain("hasActiveRealtimeJobs");
  }

  @Test
  void coreDomainTypesStayFrameworkAndAdapterFree() {
    for (Class<?> root :
        new Class<?>[] {
          RealtimeJobState.class,
          SyncDefinition.class,
          RuntimeEnvironmentRef.class,
          DefinitionDigest.class,
          SyncDefinitionDigestCalculator.class,
          DefinitionVersion.class,
          SyncExecution.class,
          SyncExecutionStateMachine.class
        }) {
      assertCoreType(root);
      for (Class<?> nested : root.getDeclaredClasses()) {
        assertCoreType(nested);
      }
    }
  }

  private static void assertInternalComponent(Class<?> type, String subsystem) {
    assertThat(type.getAnnotation(Component.class))
        .as("%s must remain an internal %s role", type.getSimpleName(), subsystem)
        .isNotNull();
    assertThat(type.getAnnotation(Service.class))
        .as("%s must not masquerade as an application service", type.getSimpleName())
        .isNull();
  }

  private static void assertCoreType(Class<?> type) {
    assertAnnotationsAvoid(type, CORE_FORBIDDEN);
    for (Field field : type.getDeclaredFields()) {
      assertTypeAvoids(type, field.getName(), field.getGenericType(), CORE_FORBIDDEN);
      assertAnnotationsAvoid(field, CORE_FORBIDDEN);
    }
    for (Method method : type.getDeclaredMethods()) {
      assertTypeAvoids(type, method.getName() + " return", method.getGenericReturnType(), CORE_FORBIDDEN);
      for (Type parameter : method.getGenericParameterTypes()) {
        assertTypeAvoids(type, method.getName() + " parameter", parameter, CORE_FORBIDDEN);
      }
      assertAnnotationsAvoid(method, CORE_FORBIDDEN);
    }
    for (Constructor<?> constructor : type.getDeclaredConstructors()) {
      for (Type parameter : constructor.getGenericParameterTypes()) {
        assertTypeAvoids(type, "constructor parameter", parameter, CORE_FORBIDDEN);
      }
      assertAnnotationsAvoid(constructor, CORE_FORBIDDEN);
    }
    if (type.isRecord()) {
      for (RecordComponent component : type.getRecordComponents()) {
        assertTypeAvoids(type, component.getName(), component.getGenericType(), CORE_FORBIDDEN);
        assertAnnotationsAvoid(component, CORE_FORBIDDEN);
      }
    }
  }

  private static void assertAnnotationsAvoid(AnnotatedElement element, String... forbidden) {
    Arrays.stream(element.getDeclaredAnnotations())
        .forEach(
            annotation -> {
              String name = annotation.annotationType().getName();
              for (String value : forbidden) {
                assertThat(name)
                    .as("%s must not carry annotation from %s", element, value)
                    .doesNotContain(value);
              }
            });
  }

  private static void assertTypeAvoids(
      Class<?> owner, String member, Type type, String... forbidden) {
    String name = type.getTypeName();
    for (String value : forbidden) {
      assertThat(name)
          .as("%s.%s must not reference %s", owner.getSimpleName(), member, value)
          .doesNotContain(value);
    }
  }

  private static Set<String> methodNames(Class<?> type) {
    return Arrays.stream(type.getDeclaredMethods()).map(Method::getName).collect(Collectors.toSet());
  }

  private static void assertFieldsAvoid(Class<?> type, String... forbidden) {
    for (Field field : type.getDeclaredFields()) {
      String name = field.getType().getName();
      for (String value : forbidden) {
        assertThat(name)
            .as("%s.%s must not depend on %s", type.getSimpleName(), field.getName(), value)
            .doesNotContain(value);
      }
    }
  }

  private static void assertFieldTypesIn(Class<?> type, Set<Class<?>> allowedTypes) {
    for (Field field : type.getDeclaredFields()) {
      assertThat(field.getType())
          .as("%s.%s must use a declared application/transport boundary", type.getSimpleName(), field.getName())
          .isIn(allowedTypes);
    }
  }

  private static void assertTypeBoundary(Class<?> type) {
    String name = type.getName();
    assertThat(name).doesNotContain(".dao.");
    assertThat(name).doesNotContain(".controller.");
    assertThat(name).doesNotContain("JdbcTemplate");
  }
}
