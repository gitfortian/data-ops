package io.yak.ops.business.resource.domain;

/** Current resource content revision. This is a fencing revision, not a historical version store. */
public record ResourceRevision(
    int version,
    long fileSize,
    String checksum,
    String contentType) {

  public static ResourceRevision current(ResourceNode resource) {
    int version = resource.getVersion() == null ? 1 : resource.getVersion();
    long fileSize = resource.getFileSize() == null ? 0L : resource.getFileSize();
    return new ResourceRevision(version, fileSize, resource.getChecksum(), resource.getContentType());
  }

  public ResourceRevision next(long newFileSize, String newChecksum, String newContentType) {
    return new ResourceRevision(version + 1, newFileSize, newChecksum, newContentType);
  }

  public void applyTo(ResourceNode resource) {
    resource.setVersion(version);
    resource.setFileSize(fileSize);
    resource.setChecksum(checksum);
    resource.setContentType(contentType);
  }
}
