# Implement — Node API path obfuscation + SM4

## Checklist

### A. Java panel (`v2board-java-api`)

1. [x] `ConfigService`：`server_api_prefix` 校验 + 空则自动生成；**不**新增 SM4 配置项
2. [x] `NodeSm4Codec`：`workingKey = SHA-256(server_token)[0:16]`；query `e` + 体加解密
3. [x] `NodeTypeCodes`：`vn`↔`v2node` 等
4. [x] 鉴权：解密 `e` → 校验 `k`；拒绝明文身份 query
5. [x] 移除经典 UniProxy / `/api/v2/server` 映射；`NodeApiRouteRegistrar` 注册 `{prefix}/{c,u,p,a,l}`；save 后 refresh
6. [x] 单测：前缀、派生密钥互通、`e`/体信封、旧路径不可用、拒绝明文 query
7. [x] `AdminManageController.install_command`：`Jireh012/v2node` + `--api-prefix`（沿用 `--api-key`）
8. [x] 更新 `server-node.md` + `ops-panel-anti-block.md`

### B. Admin UI (`v2board-ui`)

9. [x] `config.ts` + `AdminSystemConfigView`：API 前缀字段
10. [x] 通讯密钥说明：兼作节点 SM4 派生材料
11. [x] 节点编辑页展示更新后的 `install_command`（后端字段已含 `--api-prefix`，UI 原样展示）

### C. v2node (`v2node`)

12. [x] `conf.NodeConfig`：`ApiPrefix`（无 `Sm4Key`）
13. [x] SM4 工具：`SHA256(ApiKey)[:16]` + CBC/PKCS7 对齐 Java
14. [x] `api/v2board`：新路径 + `e` + 体信封；删经典路径
15. [x] `install.sh` / `v2node.sh`：`--api-prefix` → `ApiPrefix`
16. [x] README + Go 轻量测试

### D. Validation

```bash
mvn -Dtest=ConfigServiceServerValidationTest,ConfigServiceServerApiPrefixTest,UniProxyControllerTest,V2ServerControllerTest,NodeSm4CodecTest,Sm4UtilTest test
# PASS (Corretto 17)

cd /Users/jirehlam/Repos/v2node && go test ./common/crypt/... ./api/v2board/... ./conf/...
# PASS
```

## Before `task.py start`

- [x] 决策修订：SM4 = 通讯密钥派生（取消独立密钥）
- [ ] 用户批准本规划摘要后再 `start`
