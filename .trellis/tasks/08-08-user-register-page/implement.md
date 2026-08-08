# Implement: 用户注册页与注册开关

## Checklist

1. Backend：`CommController#config` 增加 `stop_register`、`invite_force`；更新/扩展单测字段断言。
2. Frontend API：`PublicSiteConfig` + `siteBrand` 暴露开关；`auth.ts` 增加 `register`。
3. `RegisterView.vue` + 路由 `/register`；登录页条件入口。
4. 手动：开关关/开分别验入口与关闭态；邀请码预填；成功进仪表盘。

## Validation

```bash
curl -sS http://localhost:8080/api/v1/passport/comm/config
# UI: stop_register off → login shows 注册 → register → dashboard
# UI: stop_register on → no button; /register shows closed
```

## Risky files

- `CommController` — 勿泄露敏感配置
- `siteBrand.ts` — 保持 `auth: false` 与缓存策略
