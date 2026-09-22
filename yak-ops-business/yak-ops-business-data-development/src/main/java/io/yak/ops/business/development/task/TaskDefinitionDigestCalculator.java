package io.yak.ops.business.development.task;

import io.yak.ops.spi.task.model.TaskDefinition;
import io.yak.ops.common.version.VersionDigests;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

/** Calculates the stable digest used to identify an immutable normalized TaskDefinition snapshot. */
@Component
public class TaskDefinitionDigestCalculator {

  public String calculate(TaskDefinition definition) {
    return VersionDigests.sha256Fields(
        definition.taskType(),
        Integer.toString(definition.schemaVersion()),
        definition.content(),
        definition.configJson());
  }
}
