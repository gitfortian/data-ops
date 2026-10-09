// A8.2n: forbid accidental duplicate Sa-Token adapter ownership.
import { readFileSync, existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { resolve, dirname } from 'node:path';
const root = resolve(dirname(fileURLToPath(import.meta.url)), '../..');
const oldPath = resolve(root, 'data-ops-framework/data-security/src/main/java/io/yak/framework/security/authentication/SaTokenAuthenticationManager.java');
const newPath = resolve(root, 'data-ops-platform/data-ops-platform-security-runtime/src/main/java/io/yak/framework/security/authentication/SaTokenAuthenticationManager.java');
const rootPom = readFileSync(resolve(root, 'pom.xml'), 'utf8');
const starter = readFileSync(resolve(root, 'data-ops-framework/data-security/pom.xml'), 'utf8');
const runtime = readFileSync(resolve(root, 'data-ops-platform/data-ops-platform-security-runtime/pom.xml'), 'utf8');
if (existsSync(oldPath)) throw Error('Duplicate legacy SaTokenAuthenticationManager owner');
if (!existsSync(newPath)) throw Error('Missing Platform Runtime SaTokenAuthenticationManager');
const java = readFileSync(newPath, 'utf8');
if (!java.includes('package io.yak.framework.security.authentication;') || !java.includes('implements AuthenticationManager')) throw Error('Sa-Token authentication ABI changed');
if (!rootPom.includes('<module>data-ops-platform/data-ops-platform-security-runtime</module>')) throw Error('Runtime absent from reactor');
if (!starter.includes('<artifactId>data-ops-platform-security-runtime</artifactId>')) throw Error('Starter does not consume runtime');
if (!runtime.includes('<artifactId>data-ops-platform-security-contract</artifactId>')) throw Error('Runtime does not depend on canonical contract');
if (/data-ops-(?:business|boot|common)/.test(runtime)) throw Error('Runtime must not depend on product layers');
console.log('A8.2n Sa-Token Runtime owner checks passed');
