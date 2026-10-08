import test from 'node:test';
import assert from 'node:assert/strict';
import { verifyArchitectureGate } from './check-architecture-gate.mjs';

const scoped = {
  impact: 'success',
  full: 'false',
  expectBackend: 'true',
  expectFrontend: 'false',
  backend: 'success',
  frontend: 'skipped',
  distribution: 'skipped',
};

test('backend-only PR correctly accepts only the planned frontend skip', () => {
  assert.equal(verifyArchitectureGate(scoped), 'scoped');
});

test('frontend-only and docs-only PR checks remain enforceable', () => {
  assert.equal(verifyArchitectureGate({
    ...scoped,
    expectBackend: 'false', expectFrontend: 'true',
    backend: 'skipped', frontend: 'success',
  }), 'scoped');
  assert.equal(verifyArchitectureGate({
    ...scoped,
    expectBackend: 'false', expectFrontend: 'false',
    backend: 'skipped', frontend: 'skipped',
  }), 'scoped');
});

test('high-risk and main runs require all three successful jobs', () => {
  assert.equal(verifyArchitectureGate({
    ...scoped,
    full: 'true', expectBackend: 'true', expectFrontend: 'true',
    frontend: 'success', distribution: 'success',
  }), 'full');
  assert.throws(() => verifyArchitectureGate({
    ...scoped, full: 'true',
  }), /Full frontend/);
});

test('required check failure, cancellation or unexpected skip cannot pass', () => {
  for (const result of ['failure', 'cancelled', 'skipped']) {
    assert.throws(() => verifyArchitectureGate({ ...scoped, backend: result }), /Backend/);
  }
  assert.throws(() => verifyArchitectureGate({ ...scoped, frontend: 'success' }), /Frontend/);
  assert.throws(() => verifyArchitectureGate({ ...scoped, distribution: 'success' }),
    /Scoped distribution/);
});

test('impact planner and static-contract failures cannot pass regardless of skips', () => {
  assert.throws(() => verifyArchitectureGate({
    ...scoped, impact: 'failure',
  }), /Impact/);
  assert.throws(() => verifyArchitectureGate({
    ...scoped, full: '',
  }), /Missing verified/);
  assert.throws(() => verifyArchitectureGate({
    ...scoped, expectBackend: '',
  }), /expected scope is missing/);
});
