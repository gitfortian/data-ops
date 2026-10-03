#!/usr/bin/env python3
"""Retired one-shot conversion used for the migration consolidation.

Owning Maven contract tests now verify the consolidated Source sections directly.
Applied baselines are immutable; add forward migrations and run the history guard.
"""
if __name__ == "__main__":
    raise SystemExit("Retired conversion: run owning Maven tests and node scripts/db/check-migration-history.mjs.")
