# Implement — Admin API SM4

## Checklist

1. [x] `ConfigService`：`site.admin_api_prefix` 校验/自动生成/热刷新；纳入冲突检测
2. [x] 扩展 `ClientApiPathRegistry` / `ClientApiPathFilter`：admin 前缀 rewrite + 经典 `/api/v1/admin` 404
3. [x] 扩展 `PanelSm4Filter`：admin 加密区
4. [x] 公开配置 `data` 增加 `admin_api_prefix`
5. [x] Auth：管理登录/拦截器走 `X-A`（复用用户侧逻辑）
6. [x] UI：`paths`/`http` + 全部 admin API 调用；系统配置字段
7. [x] 单测 + docs；支付/Telegram 未破

## Validation

```bash
mvn -Dtest=ClientApiPathFilterTest,PanelSm4SupportTest,ConfigServiceClientApiPrefixTest,CommControllerConfigTest test
```
（按新增测试类名调整；JAVA_HOME=17）
