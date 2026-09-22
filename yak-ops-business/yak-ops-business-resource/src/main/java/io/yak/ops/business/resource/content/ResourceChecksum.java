package io.yak.ops.business.resource.content;

import io.yak.ops.business.resource.exception.ResourceException;
import io.yak.ops.common.enums.resource.ResourceErrorCode;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.stereotype.Component;

/** SHA-256 checksum calculator for physical resource content. */
@Component
public class ResourceChecksum {

  public String sha256(byte[] content) {
    return sha256(new ByteArrayInputStream(content));
  }

  public String sha256(ResourceBinarySource source) {
    try (InputStream inputStream = source.openStream()) {
      return sha256(inputStream);
    } catch (IOException exception) {
      throw new ResourceException(
          ResourceErrorCode.STORAGE_OPERATION_FAILED,
          "计算文件校验值失败",
          exception);
    }
  }

  public String sha256(InputStream inputStream) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      try (DigestInputStream digestInputStream = new DigestInputStream(inputStream, digest)) {
        byte[] buffer = new byte[8192];
        while (digestInputStream.read(buffer) >= 0) {
          // Consume the stream to update the digest.
        }
      }
      StringBuilder value = new StringBuilder(64);
      for (byte item : digest.digest()) {
        value.append(String.format("%02x", item & 0xff));
      }
      return value.toString();
    } catch (NoSuchAlgorithmException | IOException exception) {
      throw new ResourceException(
          ResourceErrorCode.STORAGE_OPERATION_FAILED,
          "计算文件校验值失败",
          exception);
    }
  }
}
