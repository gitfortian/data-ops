package io.yak.ops.business.agent.runtime;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * F-039 application-level guard around SDK PlanMode workspace content.
 * PlanMode "active=false" alone is not proof that a plan exists or matches what was approved.
 *
 * Path comes from a server-trusted task workspace, never from a model or HTTP request.
 * No arbitrary file browsing, symlink traversal, or unbounded text reads.
 */
public final class SourceSemanticPlanDocumentGuard {
  private static final int MAX_PLAN_BYTES = 65536;

  public record Review(String markdown, String sha256, int bytes) {}

  public Review readReview(Path trustedWorkspace) {
    Objects.requireNonNull(trustedWorkspace, "trustedWorkspace");
    Path directory = trustedWorkspace.toAbsolutePath().normalize();
    Path plans = directory.resolve("plans");
    Path file = plans.resolve("PLAN.md");
    if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)
        || !Files.isDirectory(plans, LinkOption.NOFOLLOW_LINKS)
        || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
      throw new IllegalStateException("[F039_PLAN_NOT_PERSISTED]");
    }
    try {
      if (Files.size(file) == 0 || Files.size(file) > MAX_PLAN_BYTES)
        throw new IllegalStateException("[F039_PLAN_SIZE]");
      byte[] bytes;
      try (var stream = Files.newInputStream(file)) {
        bytes = stream.readNBytes(MAX_PLAN_BYTES + 1);
      }
      if (bytes.length == 0 || bytes.length > MAX_PLAN_BYTES)
        throw new IllegalStateException("[F039_PLAN_SIZE]");
      String text = StandardCharsets.UTF_8.newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT)
          .decode(ByteBuffer.wrap(bytes)).toString();
      if (text.isBlank()) throw new IllegalStateException("[F039_PLAN_EMPTY]");
      return new Review(text, sha256(bytes), bytes.length);
    } catch (CharacterCodingException invalid) {
      throw new IllegalStateException("[F039_PLAN_ENCODING]", invalid);
    } catch (IOException missing) {
      throw new IllegalStateException("[F039_PLAN_UNAVAILABLE]", missing);
    }
  }

  /** Refuse an approved hash not matching the current real file bytes. */
  public Review verifyApproved(Path workspace, String reviewedSha256) {
    SourceSemanticTaskState.digest(reviewedSha256, "reviewedSha256");
    Review latest = readReview(workspace);
    if (!MessageDigest.isEqual(latest.sha256().getBytes(StandardCharsets.US_ASCII),
        reviewedSha256.getBytes(StandardCharsets.US_ASCII))) {
      throw new IllegalStateException("[F039_PLAN_CHANGED_SINCE_REVIEW]");
    }
    return latest;
  }

  private static String sha256(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 unavailable", impossible);
    }
  }
}
