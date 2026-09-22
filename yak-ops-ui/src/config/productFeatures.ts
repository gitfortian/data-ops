/**
 * Yak Ops product rollout gates.
 *
 * These capabilities already exist at the framework/API layer but are not yet
 * complete product experiences in Yak Ops. Keep the implementation addressable
 * while removing unfinished entry points from the default navigation.
 */
export const productFeatures = {
  projectSpace: true,
  resourceAuthorization: false,
  systemConfig: false,
} as const;

export type ProductFeature = keyof typeof productFeatures;

export const isProductFeatureEnabled = (feature: ProductFeature): boolean =>
  productFeatures[feature];
