package io.yak.ops.business.resource.content;

import java.io.IOException;
import java.io.InputStream;

/** Re-openable binary input used by Content without depending on Spring MultipartFile. */
public interface ResourceBinarySource {

  String fileName();

  String contentType();

  long size();

  InputStream openStream() throws IOException;
}
