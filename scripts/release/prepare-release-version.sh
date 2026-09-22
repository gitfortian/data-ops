#!/usr/bin/env bash

set -Eeuo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

set -a
# shellcheck disable=SC1091
source release.env
set +a

printf 'Normalizing Maven reactor to Yak Ops %s...\n' "$YAK_OPS_VERSION"
bash ./mvnw -B -ntp \
    org.codehaus.mojo:versions-maven-plugin:2.21.0:set \
    -DnewVersion="$YAK_OPS_VERSION" \
    -DprocessAllModules=true \
    -DgenerateBackupPoms=false

node <<'NODE'
const fs = require('fs');
const path = 'yak-ops-ui/package.json';
const packageJson = JSON.parse(fs.readFileSync(path, 'utf8'));
packageJson.version = process.env.YAK_OPS_VERSION;
fs.writeFileSync(path, `${JSON.stringify(packageJson, null, 2)}\n`);
NODE

printf 'Release workspace is normalized to Yak Ops %s.\n' "$YAK_OPS_VERSION"
