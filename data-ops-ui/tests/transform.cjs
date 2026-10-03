// Strip TypeScript before CommonJS conversion; Umi's pre-hoist transformer leaves
// type references to mocked imports behind and fails before test discovery.
module.exports = require("babel-jest").createTransformer({
  babelrc: false,
  configFile: false,
  presets: [
    require.resolve("@babel/preset-typescript"),
    [require.resolve("@babel/preset-react"), { runtime: "automatic" }],
  ],
  plugins: [require.resolve("@babel/plugin-transform-modules-commonjs")],
});
