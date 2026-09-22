package io.yak.ops.business.resource.content;

import io.yak.ops.business.resource.config.ConditionalOnResourceEnabled;
import io.yak.ops.business.resource.domain.ResourceContent;
import io.yak.ops.business.resource.domain.ResourceDownload;
import io.yak.ops.business.resource.domain.ResourceNode;
import io.yak.ops.business.resource.exception.ResourceException;
import io.yak.ops.business.resource.namespace.ResourceNamespaceReader;
import io.yak.ops.business.resource.storage.ResourceStorageGateway;
import io.yak.ops.common.enums.resource.ResourceErrorCode;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Read boundary for resource bytes and editable text content. */
@Component
@ConditionalOnResourceEnabled
@RequiredArgsConstructor
public class ResourceContentReader {

  private static final int MAX_CONTENT_LINES = 2000;

  private final ResourceNamespaceReader namespace;
  private final ResourceContentPolicy policy;
  private final ResourceStorageGateway storage;

  public ResourceContent getContent(Long id, int skipLineNum, int limit) {
    ResourceNode resource = namespace.requireFile(id);
    policy.ensureReadableContent(resource);
    int normalizedSkip = Math.max(0, skipLineNum);
    int normalizedLimit = Math.max(1, Math.min(limit, MAX_CONTENT_LINES));
    try (InputStream inputStream = storage.open(resource.getStorageType(), resource.getStoragePath());
        BufferedReader reader =
            new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
      for (int index = 0; index < normalizedSkip; index++) {
        if (reader.readLine() == null) {
          return content(resource, "", normalizedSkip, 0, false);
        }
      }
      List<String> lines = new ArrayList<>();
      String line;
      while (lines.size() <= normalizedLimit && (line = reader.readLine()) != null) {
        lines.add(line);
      }
      boolean hasMore = lines.size() > normalizedLimit;
      if (hasMore) {
        lines.remove(lines.size() - 1);
      }
      return content(
          resource,
          String.join("\n", lines),
          normalizedSkip,
          lines.size(),
          hasMore);
    } catch (IOException exception) {
      throw new ResourceException(
          ResourceErrorCode.STORAGE_OPERATION_FAILED,
          "读取资源内容失败",
          exception);
    }
  }

  public ResourceDownload download(Long id) {
    ResourceNode resource = namespace.requireFile(id);
    InputStream inputStream = storage.open(resource.getStorageType(), resource.getStoragePath());
    return new ResourceDownload(
        resource.getName(),
        policy.contentType(resource.getContentType()),
        resource.getFileSize() == null ? 0L : resource.getFileSize(),
        inputStream);
  }

  private ResourceContent content(
      ResourceNode resource,
      String text,
      int skipLineNum,
      int lineCount,
      boolean hasMore) {
    return new ResourceContent(
        resource.getId(),
        resource.getFullPath(),
        text,
        skipLineNum,
        lineCount,
        hasMore);
  }
}
