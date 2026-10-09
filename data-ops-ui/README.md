# Yak Ops UI

Yak Ops 的 Web 前端，基于 Ant Design Pro / Umi 构建。

## Engineering Standard

新增或重构前端代码前，请先阅读 [FRONTEND_CODE_STYLE.md](./FRONTEND_CODE_STYLE.md)。

规范采用渐进式迁移：新代码立即遵守，历史代码按业务模块逐步收敛，不做一次性全仓目录搬迁。

## Environment Prepare

安装依赖：

```bash
npm install
# CI uses: yarn install --frozen-lockfile
```

## Provided Scripts

### Start project

```bash
npm run start:dev
```

### Build project

```bash
npm run build
```

### Check code style and types

工程检查（新提交的代码需遵守样式规范，仓库已有的 TypeScript 债务通过机器可检查的基线约束）：

```bash
npm run biome:lint
npm run check:types
```

`npm run lint` 会额外执行直接 `tsc --noEmit`，可能因现存类型诊断返回非零状态；CI 当前采用 `scripts/check-type-baseline.mjs` 比较 `scripts/type-baseline.json`，不使用历史 `tsc-output.txt`。


### Test code

```bash
npm test
```
