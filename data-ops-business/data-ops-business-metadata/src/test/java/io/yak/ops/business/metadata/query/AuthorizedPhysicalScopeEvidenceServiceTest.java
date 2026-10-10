package io.yak.ops.business.metadata.query;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.yak.framework.security.context.YakSecurityContext;
import io.yak.ops.business.datasource.api.ProjectDataSourceReadApi;
import io.yak.ops.business.metadata.api.PhysicalScopeEvidenceQueryApi;
import io.yak.ops.common.constant.datasource.DataSourcePermissionCode;
import io.yak.ops.common.constant.metadata.MetadataPermissionCode;
import io.yak.ops.core.project.CurrentProject;
import io.yak.ops.core.project.ProjectContext;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class AuthorizedPhysicalScopeEvidenceServiceTest {
  private final ProjectDataSourceReadApi datasource = mock(ProjectDataSourceReadApi.class);
  private final PhysicalScopeEvidenceQueryApi metadata = mock(PhysicalScopeEvidenceQueryApi.class);
  private final CurrentProject project = () -> Optional.of(new ProjectContext(17L, "test"));
  private final AuthorizedPhysicalScopeEvidenceService gate =
      new AuthorizedPhysicalScopeEvidenceService(project, datasource, metadata);

  @Test void readOnlyPreviewChecksBothPermissionsAndCurrentDatasourceOwner() {
    var expected = new PhysicalScopeEvidenceQueryApi.Evidence(
        17L,"42","warehouse","public","capture-1","2026-10-10","hash",List.of());
    when(metadata.readSelectedTables(List.of("key1"))).thenReturn(expected);
    try (MockedStatic<YakSecurityContext> auth=mockStatic(YakSecurityContext.class)) {
      auth.when(YakSecurityContext::getCurrentUserId).thenReturn(7L);
      auth.when(()->YakSecurityContext.hasPermission(DataSourcePermissionCode.READ)).thenReturn(true);
      auth.when(()->YakSecurityContext.hasPermission(MetadataPermissionCode.READ)).thenReturn(true);
      auth.when(()->YakSecurityContext.canAccessProject(17L)).thenReturn(true);
      assertSame(expected,gate.read(42L,List.of("key1")));
      verify(datasource).requireReadableSource(42L);
      verify(metadata).readSelectedTables(List.of("key1"));
    }
  }

  @Test void missingDatasourcePrivilegeRejectsBeforeAnyRead() {
    try (MockedStatic<YakSecurityContext> auth=mockStatic(YakSecurityContext.class)) {
      auth.when(YakSecurityContext::getCurrentUserId).thenReturn(7L);
      assertThrows(IllegalArgumentException.class,()->gate.read(42L,List.of("key1")));
      verifyNoInteractions(datasource,metadata);
    }
  }

  @Test void unrelatedProjectOrSourceIdentityCannotBeSubstituted() {
    var incorrect = new PhysicalScopeEvidenceQueryApi.Evidence(
        18L,"999","db","","capture","time","hash",List.of());
    when(metadata.readSelectedTables(List.of("key1"))).thenReturn(incorrect);
    try (MockedStatic<YakSecurityContext> auth=mockStatic(YakSecurityContext.class)) {
      auth.when(YakSecurityContext::getCurrentUserId).thenReturn(7L);
      auth.when(()->YakSecurityContext.hasPermission(DataSourcePermissionCode.READ)).thenReturn(true);
      auth.when(()->YakSecurityContext.hasPermission(MetadataPermissionCode.READ)).thenReturn(true);
      auth.when(()->YakSecurityContext.canAccessProject(17L)).thenReturn(true);
      assertThrows(IllegalStateException.class,()->gate.read(42L,List.of("key1")));
    }
  }

  @Test void overLimitSelectionNeverReachesDatasource() {
    try (MockedStatic<YakSecurityContext> auth=mockStatic(YakSecurityContext.class)) {
      auth.when(YakSecurityContext::getCurrentUserId).thenReturn(7L);
      auth.when(()->YakSecurityContext.hasPermission(DataSourcePermissionCode.READ)).thenReturn(true);
      auth.when(()->YakSecurityContext.hasPermission(MetadataPermissionCode.READ)).thenReturn(true);
      auth.when(()->YakSecurityContext.canAccessProject(17L)).thenReturn(true);
      assertThrows(IllegalArgumentException.class,()->gate.read(42L,
          java.util.Collections.nCopies(21,"same")));
      verifyNoInteractions(datasource,metadata);
    }
  }
}
