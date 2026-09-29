package io.yak.ops.business.resource.domain;

/** Builds physical storage paths without leaking Project Space prefixes into logical paths. */
public final class ResourceStoragePath {

  private static final String PROJECT_ROOT = "projects/";

  private ResourceStoragePath() {}

  public static String forProject(Long projectId, ResourcePath logicalPath) {
    String path = logicalPath.storagePath();
    return projectId == null ? path : PROJECT_ROOT + projectId + "/" + path;
  }
}
