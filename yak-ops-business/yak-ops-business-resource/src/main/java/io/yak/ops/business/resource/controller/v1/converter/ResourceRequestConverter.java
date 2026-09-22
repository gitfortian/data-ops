package io.yak.ops.business.resource.controller.v1.converter;

import io.yak.ops.business.resource.content.ResourceBinarySource;
import io.yak.ops.business.resource.content.ResourceContentCommand;
import io.yak.ops.business.resource.domain.ResourceQuery;
import io.yak.ops.business.resource.exception.ResourceException;
import io.yak.ops.business.resource.namespace.ResourceNamePolicy;
import io.yak.ops.business.resource.namespace.ResourceNamespaceCommand;
import io.yak.ops.common.bean.dto.resource.ResourceContentUpdateDTO;
import io.yak.ops.common.bean.dto.resource.ResourceCreateContentDTO;
import io.yak.ops.common.bean.dto.resource.ResourceCreateDirectoryDTO;
import io.yak.ops.common.bean.dto.resource.ResourceMoveDTO;
import io.yak.ops.common.bean.dto.resource.ResourceQueryDTO;
import io.yak.ops.common.bean.dto.resource.ResourceUpdateDTO;
import io.yak.ops.common.enums.resource.ResourceErrorCode;
import io.yak.ops.common.enums.resource.ResourceNodeType;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/** HTTP request conversion. Spring transport types stop at this boundary. */
@Component
@RequiredArgsConstructor
public class ResourceRequestConverter {

  private final ResourceNamePolicy names;

  public ResourceNamespaceCommand.CreateDirectory createDirectory(ResourceCreateDirectoryDTO request) {
    return request == null ? null : new ResourceNamespaceCommand.CreateDirectory(
        request.getParentId(), request.getName(), request.getDescription());
  }

  public ResourceNamespaceCommand.Update update(ResourceUpdateDTO request) {
    return request == null ? null : new ResourceNamespaceCommand.Update(request.getName(), request.getDescription());
  }

  public ResourceNamespaceCommand.Move move(ResourceMoveDTO request) {
    return request == null ? null : new ResourceNamespaceCommand.Move(request.getTargetParentId());
  }

  public ResourceContentCommand.Create createContent(ResourceCreateContentDTO request) {
    return request == null ? null : new ResourceContentCommand.Create(
        request.getParentId(), request.getName(), request.getDescription(), request.getContentType(), request.getContent());
  }

  public ResourceContentCommand.Update updateContent(ResourceContentUpdateDTO request) {
    return request == null ? null : new ResourceContentCommand.Update(request.getContent());
  }

  public ResourceQuery query(ResourceQueryDTO request) {
    ResourceQueryDTO source = request == null ? new ResourceQueryDTO() : request;
    ResourceNodeType nodeType = null;
    if (source.getNodeType() != null && !source.getNodeType().isBlank()) {
      String normalized = source.getNodeType().trim().toUpperCase(Locale.ROOT);
      try {
        nodeType = ResourceNodeType.valueOf(normalized);
      } catch (IllegalArgumentException exception) {
        throw new ResourceException(ResourceErrorCode.INVALID_NODE_TYPE, normalized);
      }
    }
    return new ResourceQuery(source.getPageNo(), source.getPageSize(), source.getParentId(),
        names.trimToNull(source.getKeyword()), nodeType);
  }

  public ResourceBinarySource binary(MultipartFile file) {
    return file == null ? null : new MultipartBinarySource(file);
  }

  private static final class MultipartBinarySource implements ResourceBinarySource {
    private final MultipartFile file;
    private MultipartBinarySource(MultipartFile file) { this.file = file; }
    @Override public String fileName() { return file.getOriginalFilename(); }
    @Override public String contentType() { return file.getContentType(); }
    @Override public long size() { return file.getSize(); }
    @Override public InputStream openStream() throws IOException { return file.getInputStream(); }
  }
}
