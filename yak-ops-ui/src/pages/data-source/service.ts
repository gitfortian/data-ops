/**
 * @deprecated New data-source code should import from `@/services/data-source`.
 *
 * This compatibility export keeps existing cross-module imports stable while
 * the data-source page adopts the new service boundary incrementally.
 */
export * from '@/services/data-source/legacy';
