package io.yak.ops.spi.resource;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

/** Utilities for managing temporary directories used by resolved resource files. */
public final class TempDirectoryUtils {

  private TempDirectoryUtils() {}

  /**
   * Recursively deletes all files and subdirectories under the given path,
   * then deletes the path itself. Uses {@link Files#walkFileTree} for safe traversal.
   *
   * <p>Deletion failures on individual entries are silently ignored — the method
   * makes a best-effort attempt to clean up as much as possible.
   */
  public static void deleteRecursively(Path path) throws IOException {
    if (path == null || !Files.exists(path)) return;
    Files.walkFileTree(path, new SimpleFileVisitor<>() {
      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        Files.deleteIfExists(file);
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
        Files.deleteIfExists(dir);
        return FileVisitResult.CONTINUE;
      }
    });
  }
}
