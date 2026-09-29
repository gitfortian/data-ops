package io.yak.ops.business.resource.domain;

import java.io.InputStream;

/** 资源文件下载流。 */
public record ResourceDownload(
    String fileName,
    String contentType,
    long fileSize,
    InputStream inputStream) {}
