# Implement: 面板抗探测与防封锁

## Checklist

1. [x] `ConfigService`：`validateSubscribePathInSaveBody` + 保留前缀列表；`save` 入口调用
2. [x] 单元测试：合法/空/非法/`..`/冲突前缀；必要时补 registrar「改路径后默认失效」断言
3. [x] `PaymentService` + `WechatPayNativeDriver`：默认文案用 `getAppName()`；补或改测试
4. [x] UI：`AdminSystemConfigView` 订阅路径说明与 placeholder
5. [x] UI：`index.html` 中性 title + `public/favicon.svg`（去掉 vite 默认图标引用）
6. [x] `docs/ops-panel-anti-block.md` + README 链接
7. [x] Spec：`subscribe-delivery.md` 增加保存校验条款；`guides/index.md` 可选链到 ops 文档

## Validation

```bash
# API repo
mvn -Dtest=SubscribeRouteRegistrarTest,ConfigServiceSubscribePathValidationTest,PaymentServiceProductNameTest test
# 若测试类名不同，以实际新增类为准；至少覆盖 subscribe path 校验

# UI
# 打开 index.html / 构建产物：title 非 V2Board；favicon 非 vite.svg
```

手动：管理端改 `subscribe_path` → curl 新路径 / 旧默认路径；改 `app_name` 后看支付创建参数（或单测断言）。

## Risky files

- `ConfigService.java` — 校验过严影响保存
- `WechatPayNativeDriver.java` / `PaymentService.java` — 支付文案
- `AdminSystemConfigView.vue` — 仅文案

## Rollback points

- 校验逻辑可单独回滚而不动运维文档
- favicon/title 可独立回滚

## Before `task.py start`

- [x] prd / design / implement 齐全
- [x] implement.jsonl / check.jsonl 非仅 seed
- [ ] 用户明确批准本规划摘要
