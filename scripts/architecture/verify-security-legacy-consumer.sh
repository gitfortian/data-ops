#!/usr/bin/env bash
# Compile a consumer with real legacy Starter bytes from the target main SHA.
# Run its unchanged class files with ONLY the A8.2 packaged Security runtime.
# Never modify main, production databases, or existing Pull Request branches.
set -euo pipefail

if [[ "${#}" -ne 2 ]]; then
  echo "Usage: verify-security-legacy-consumer.sh OLD_MAIN_SHA NEW_BOOT_JAR" >&2
  exit 2
fi

base_sha="$1"
boot_jar="$(realpath "$2")"
[[ "$base_sha" =~ ^[0-9a-f]{40}$ ]] || { echo "Expected immutable main SHA" >&2; exit 2; }
[[ -f "$boot_jar" ]] || { echo "Boot release JAR missing" >&2; exit 2; }

repo="$(git rev-parse --show-toplevel)"
tmp="$(mktemp -d)"
legacy="$tmp/main-baseline"
cleanup() {
  git -C "$repo" worktree remove --force "$legacy" 2>/dev/null || true
  rm -rf "$tmp"
}
trap cleanup EXIT

# GITHUB_BASE_REF may advance, but the exact SHA from the PR event is fixed.
# Never fetch or checkout a mutable branch name in this acceptance test.
if ! git -C "$repo" cat-file -e "$base_sha^{commit}" 2>/dev/null; then
  git -C "$repo" fetch --no-tags --depth=1 origin "$base_sha"
fi
git -C "$repo" cat-file -e "$base_sha^{commit}"
git -C "$repo" worktree add --detach "$legacy" "$base_sha"
legacy_source="$legacy/data-ops-framework/data-security/src/main/java/io/yak/framework/security/common/dto/PageParamDTO.java"
new_owner="$legacy/data-ops-platform/data-ops-platform-security-contract/src/main/java/io/yak/framework/security/common/dto/PageParamDTO.java"
[[ -f "$legacy_source" && ! -f "$new_owner" ]] || {
  echo "Refusing non-legacy baseline: Security must still be in Framework Starter" >&2
  exit 1
}

echo "Building original Security Starter from immutable main SHA $base_sha"
(
  cd "$legacy"
  bash ./mvnw -B -ntp -Dmaven.test.skip=true \
    -pl data-ops-framework/data-security -am package
)
legacy_jar="$legacy/data-ops-framework/data-security/target/data-security-spring-boot-starter-0.1.0.jar"
[[ -f "$legacy_jar" ]] || { echo "Original Security Starter artifact missing" >&2; exit 1; }

mkdir -p "$tmp/consumer" "$tmp/current"
javac --release 21 -cp "$legacy_jar" -d "$tmp/consumer" \
  "$repo/scripts/architecture/fixtures/SecurityLegacyConsumer.java"
[[ -f "$tmp/consumer/SecurityLegacyConsumer.class" ]] || exit 1

# Explicitly flatten only the NEW Boot artifact. Never add $legacy_jar
# or the original main's target/classes to the runtime classpath.
python3 - "$boot_jar" "$tmp/current" <<'PY'
import pathlib
import shutil
import sys
import zipfile
boot_path, output = sys.argv[1:]
output = pathlib.Path(output)
libs = output / 'libs'
classes = output / 'classes'
libs.mkdir(parents=True)
classes.mkdir(parents=True)
with zipfile.ZipFile(boot_path) as archive:
    for member in archive.namelist():
        if member.startswith('BOOT-INF/lib/') and member.endswith('.jar'):
            target = libs / pathlib.Path(member).name
        elif member.startswith('BOOT-INF/classes/') and not member.endswith('/'):
            relative = pathlib.PurePosixPath(member).relative_to('BOOT-INF/classes')
            if '..' in relative.parts:
                raise ValueError("Untrusted classpath entry")
            target = classes.joinpath(*relative.parts)
        else:
            continue
        target.parent.mkdir(parents=True, exist_ok=True)
        with archive.open(member) as source, open(target, 'wb') as dest:
            shutil.copyfileobj(source, dest)
PY

[[ -f "$tmp/current/libs/data-ops-platform-security-contract-1.0.0.jar" ]] || {
  echo "Canonical new Security API JAR absent" >&2
  exit 1
}
echo "Executing old class bytes against only the NEW packaged Security libraries"
java -cp "$tmp/consumer:$tmp/current/classes:$tmp/current/libs/*" SecurityLegacyConsumer
echo "A8.2 legacy compiled consumer ABI passed (old main $base_sha)"
