# Implement: 用户列表筛选与流量排序

## Checklist

1. **Backend — 排序**
   - 在 `AdminUserController.fetch`：`sort=total_used` 时用 `orderByAsc/Desc` 表达式或 `last`/`apply` 安全排序 `(IFNULL(u,0)+IFNULL(d,0))`；其它 sort 保持列名 orderBy。
2. **Backend — 过期 filter**
   - `ALLOWED_FILTER_KEYS` 增加 `expired`。
   - `applyFilters`：识别 `expired` + `=` + `0|1`，按 design 写入时间条件（`now` 用秒）。
3. **Backend — 备注回归**
   - 确认 `remarks` + `模糊` 路径仍走 `like`；必要时补单测或手工验证步骤写入 check。
4. **Frontend — API**
   - 确认 `fetchAdminUsers` 已传 `sort` / `sort_type`；若类型需收窄可改默认参数文档注释即可。
5. **Frontend — UI**
   - `AdminUsersView.vue`：过期快捷 tab；流量表头可排序；`buildFilters` / `clearSearch` / `load` 接线；清除按钮显示条件含过期/排序。
6. **Validate**
   - API：`mvn -Dtest=...` 若已有/可加相关测试；否则用 curl 验证三种能力。
   - UI：本地点选备注搜索、流量排序、过期 tab 与组合场景。
7. **Check**
   - 派发 `trellis-check`；通过后进入 finish / spec 更新（`admin-user.md` 增补 expired + total_used sort）。

## Validation commands

```bash
# API 仓
mvn -q -DskipTests compile
# 有测试时针对性跑；否则手工：
# curl '.../admin/user/fetch?filter[0][key]=remarks&filter[0][condition]=模糊&filter[0][value]=xx'
# curl '.../admin/user/fetch?sort=total_used&sort_type=DESC'
# curl '.../admin/user/fetch?filter[0][key]=expired&filter[0][condition]=&=&filter[0][value]=1'

# UI 仓
cd ../v2board-ui && npm run build   # 或项目惯用 typecheck/lint
```

## Risky files

- `v2board-java-api/.../AdminUserController.java` — filter/sort 注入面；表达式排序勿拼接用户输入列名以外的内容。
- `v2board-ui/src/views/admin/AdminUsersView.vue` — 筛选状态组合易漏清除。

## Rollback points

- 仅改上述控制器与 Vue（+ 可选 admin.ts）；无 DB 变更。
