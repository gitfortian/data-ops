package io.yak.ops.spi.resource;

import java.io.InputStream;

/**
 * Result of a resource file download.
 *
 * @param fileName    original file name
 * @param suffix      file suffix (e.g. "jar", "py", "sh")
 * @param fileSize    file size in bytes
 * @param checksum    SHA-256 checksum of the file
 * @param version     current version of the resource (for version validation)
 * @param inputStream content stream; caller is responsible for closing
 */
public record ResourceDownloadResult(
    String fileName,
    String suffix,
    long fileSize,
    String checksum,
    int version,
    InputStream inputStream
) {}
