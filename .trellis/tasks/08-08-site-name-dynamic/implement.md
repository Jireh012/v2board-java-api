# Implement: 站点名称动态渲染

## Checklist

1. Backend：`CommController` 增加 `GET /config`，返回 `{ app_name }`；补简单单元/控制器测试（可选但推荐至少 ConfigService 已有 getAppName 覆盖）。
2. Frontend API：`src/api/site.ts` + `auth: false`。
3. Frontend brand store：`src/siteBrand.ts`（load / cache / document.title）。
4. `main.ts` 触发 load。
5. 替换所有「谜之站点」展示：`App.vue`、`AdminLayout.vue`、`LoginView.vue`、`AdminLoginView.vue`、`InviteView.vue`。
6. 自检：无鉴权 curl 新接口；改 admin 站点名后刷新登录页与顶栏。

## Validation

```bash
# api
curl -sS http://localhost:8080/api/v1/passport/comm/config
# ui：系统配置改 app_name → 刷新 /login、/admin/login、登录后顶栏与邀请页
```

## Risky files

- `CommController.java` — 勿误挂需鉴权逻辑
- `src/api/http.ts` — 公开请求必须 `auth: false`，避免 401 踢登录页死循环

## Rollback points

- 后端：删除 `/config` mapping
- 前端：恢复硬编码或仅用回退常量
