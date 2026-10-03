declare module "legendary-cursor" {
  /** Options read by the installed cursor's init function. */
  export interface LegendaryCursorOptions {
    lineSize?: number;
    opacityDecrement?: number;
    speedExpFactor?: number;
    lineExpFactor?: number;
    sparklesCount?: number;
    maxOpacity?: number;
    texture1?: string;
    texture2?: string;
    texture3?: string;
  }

  const LegendaryCursor: {
    init(options?: LegendaryCursorOptions): void;
  };
  export default LegendaryCursor;
}
