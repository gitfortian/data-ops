package io.yak.ops.business.sync.realtime.engine;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

/** Helpers for process integration tests that create executable POSIX shell fixtures. */
final class PosixTestSupport {

  private PosixTestSupport() {}

  static void makeOwnerExecutable(Path path) throws IOException {
    assumeTrue(
        FileSystems.getDefault().supportedFileAttributeViews().contains("posix"),
        "Shell executable integration tests require POSIX file permissions");
    Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rwx------"));
  }
}
