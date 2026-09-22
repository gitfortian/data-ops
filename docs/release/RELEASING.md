# Yak Ops Release Guide

Yak Ops uses a tag-driven release flow. The first public release baseline is `v0.1.0`.

## Release source of truth

`release.env` is the single release metadata file shared by CI, the tag release workflow and local Docker publishing scripts.

It records:

- `YAK_OPS_VERSION`: public Yak Ops release version;
- `YAK_FRAMEWORK_VERSION`: Maven Central version expected by Yak Ops;
- `DOCKERHUB_NAMESPACE`: namespace used for published images.

Yak Framework is published to Maven Central under `io.github.weifuwan`, so CI and release builds resolve it as a normal public Maven dependency. No private repository checkout, Maven repository credential, or Yak Framework read token is required.

## Why Maven POMs are normalized during release

The existing reactor contains the same project version in many parent/module POMs. The release workflow runs `scripts/release/prepare-release-version.sh`, which uses the pinned Versions Maven Plugin to update the complete reactor in the clean release workspace before packaging. This avoids hand-editing dozens of POMs while ensuring the produced distribution is versioned with `YAK_OPS_VERSION`.

The committed frontend `package.json` and Docker example tags must already match `release.env`; `scripts/release/check-release-metadata.sh` enforces these invariants in CI.

## Required GitHub Actions secrets

Configure these repository secrets before pushing a release tag:

- `DOCKERHUB_USERNAME`: Docker Hub account allowed to push the Yak Ops images;
- `DOCKERHUB_TOKEN`: Docker Hub access token for that account.

The Docker Hub account must be able to push to the namespace declared by `DOCKERHUB_NAMESPACE`.

## Release flow

1. Update `release.env` for the next release, including the Yak Framework version when it changes.
2. Update the committed frontend version and `.env.example` Docker tags to the same Yak Ops version.
3. Run `./scripts/release/check-release-metadata.sh`.
4. Merge the release preparation changes into `main` and confirm CI passes.
5. Create and push a tag whose version exactly matches `release.env`, for example `v0.1.0`.
6. The `Release` workflow builds the frontend and Maven distribution, publishes both Docker images, creates `SHA256SUMS`, and creates the GitHub Release.

A mismatched tag or missing Docker Hub credential is rejected before any image is published.

## Published artifacts

For `v0.1.0`, the workflow publishes:

- `weifuwan/yak-ops:0.1.0` and `weifuwan/yak-ops:latest`;
- `weifuwan/yak-ops-api:0.1.0` and `weifuwan/yak-ops-api:latest`;
- `yak-ops-0.1.0.tar.gz`;
- `SHA256SUMS` attached to the GitHub Release.

## Local image publishing

`build-and-push-linux.sh` and `build-and-push-windows.ps1` read the same `release.env`. They still publish an already-built local distribution and are intended as a manual fallback, not the canonical release path.

The canonical public release path is Git tag -> GitHub Actions -> Docker Hub + GitHub Release.
