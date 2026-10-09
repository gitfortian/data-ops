#!/usr/bin/env python3
"""Inspect the actual Boot and distribution JAR using only Python stdlib.

The probe does not compile consumers or imply deployed/old-JAR ABI acceptance.
It reports every Security class location in the assembled nested JAR classpath.
"""
import hashlib
import io
import json
import pathlib
import sys
import tarfile
import zipfile


def inspect(boot_path, dist_path):
    boot = pathlib.Path(boot_path).read_bytes()
    boot_digest = hashlib.sha256(boot).hexdigest()

    with tarfile.open(dist_path, "r:gz") as archive:
        members = [member for member in archive.getmembers()
                   if member.isfile()
                   and member.name.endswith("/libs/yak-ops-api.jar")]
        if len(members) != 1:
            raise ValueError("Distribution must have exactly one libs/yak-ops-api.jar")
        dist_jar = archive.extractfile(members[0]).read()
    dist_digest = hashlib.sha256(dist_jar).hexdigest()

    owners = {}
    nested = []
    with zipfile.ZipFile(io.BytesIO(boot)) as boot_jar:
        names = boot_jar.namelist()
        for name in names:
            if name.startswith("BOOT-INF/classes/io/yak/framework/security/") and name.endswith(".class"):
                path = name[len("BOOT-INF/classes/"):]
                owners.setdefault(path, []).append("BOOT-INF/classes")
        for name in names:
            if not name.startswith("BOOT-INF/lib/") or not name.endswith(".jar"):
                continue
            nested.append(name)
            with zipfile.ZipFile(io.BytesIO(boot_jar.read(name))) as dependency:
                for class_path in dependency.namelist():
                    if (class_path.startswith("io/yak/framework/security/")
                            and class_path.endswith(".class")):
                        owners.setdefault(class_path, []).append(name)

    return {
        "boot_sha256": boot_digest,
        "release_sha256": dist_digest,
        "nested": nested,
        "class_owners": owners,
    }


if __name__ == "__main__":
    try:
        if len(sys.argv) != 3:
            raise ValueError("Usage: probe-security-distribution.py BOOT_JAR DIST_TAR_GZ")
        print(json.dumps(inspect(sys.argv[1], sys.argv[2]), sort_keys=True))
    except (ValueError, OSError, zipfile.BadZipFile, tarfile.TarError) as exc:
        print("Security distribution probe failed: " + str(exc), file=sys.stderr)
        sys.exit(1)
