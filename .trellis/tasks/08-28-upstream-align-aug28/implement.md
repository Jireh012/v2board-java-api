# Implement — 对齐 wyx2685 上游（2026-08）

## Checklist

### 1. v2node

1. [x] merge/cherry-pick `160320c`（remote 证书）+ `ad749f5`（xray-core v26.7.28）
2. [x] 解决冲突：保留 ApiPrefix/SM4、Jireh012 `install.sh`、SNI 重签
3. [x] `go test ./...`（或至少 `api/v2board`、`node`）
4. [x] 更新 `.trellis/spec/node/upstream.md` 记录已合入 commit

### 2. Java API — 远程证书 + pcs

5. [x] v2node save 生成 remote 证书（无 PIN 时）
6. [x] `ServerService` 订阅 map 去掉 `tls_cert`/`tls_key`
7. [x] `Helper` URI 增加 `pcs`
8. [x] 单测：生成 / 幂等 / 剥离 / URI

### 3. UI

9. [x] `AdminServersView` remote 选项 + PIN 只读
10. [x] Paytaro 静态表单兜底

### 4. Java API — Paytaro + cancel

11. [x] `PaytaroDriver` + factory + methods 列表置顶 + form
12. [x] `OrderService.cancel` CAS
13. [x] 单测签名/回调/取消
14. [x] spec：`admin-commerce.md` 或支付相关 + `server-node.md` remote 字段

### 5. 发布（代码合入后）

15. [ ] Jireh012/v2node Release（用户确认后再打）

## Validation

```bash
# v2node
go test ./api/v2board ./node ./common/...

# API（JAVA_HOME=17）
mvn -Dtest=PaytaroDriverTest,OrderServiceCancelTest,RemoteTlsCertTest,HelperPcsTest test
```

## Risky files

- `v2node/script/install.sh`：合并时禁止改回 wyx2685 URL
- `ServerService` 订阅 map：漏剥 PEM 会把私钥发给用户
- `OrderService.cancel`：CAS 失败后不得退余额

## Rollback

Paytaro / remote 为增量；cancel CAS 行为更严（已非待支付订单无法再取消）。v2node 回滚 Release 即可。
