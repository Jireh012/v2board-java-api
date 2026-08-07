# 管理端用户列表：备注模糊、流量排序、过期筛选

## Goal

管理员在用户列表中能按备注模糊查找用户、按已使用流量排序，并按「已过期 / 未过期」筛选，从而更快定位目标账号。

## Background

- 列表接口：`GET /api/v1/admin/user/fetch`（`AdminUserController`）。
- 前端：`v2board-ui` 的 `AdminUsersView.vue` + `fetchAdminUsers`。
- 已使用流量在响应中为 `total_used = u + d`（字节），非 DB 列。
- 业务「未过期」约定（README / 节点侧）：`expired_at IS NULL OR expired_at > now`。
- 用户确认：未过期**包含**「长期有效」（`expired_at` 为空）。

## Confirmed Facts

| 项 | 现状 |
|----|------|
| 备注模糊 | 后端 `ALLOWED_FILTER_KEYS` 含 `remarks`，条件 `模糊` → `LIKE`；前端搜索项含备注且走模糊。需回归确认可用，并在本任务中保持契约。 |
| 流量排序 | 后端 `sort` 直接 `orderBy` 列名；无 `total_used` / `(u+d)` 支持。前端固定默认 `created_at DESC`，表头无排序控件。 |
| 过期筛选 | 无 `expired` 类 filter；前端仅有本页「已过期」统计与行内标红，无快捷筛选。 |

## Requirements

1. **R1 备注模糊查询**：按备注关键词模糊搜索用户；空关键字不施加备注条件；与现有封禁/套餐等 filter 可叠加。
2. **R2 已使用流量排序**：支持按 `total_used`（`IFNULL(u,0)+IFNULL(d,0)`）升序/降序分页排序；默认仍为 `created_at DESC`；前端在「流量」列表头提供切换。
3. **R3 过期状态筛选**：快捷筛选「全部 / 未过期 / 已过期」。
   - 已过期：`expired_at IS NOT NULL AND expired_at < now`（unix 秒）。
   - 未过期：`expired_at IS NULL OR expired_at >= now`。
4. **R4 组合**：备注搜索、过期筛选、封禁/套餐筛选、流量排序可同时生效；清除筛选时一并复位过期筛选与流量排序默认值。

## Acceptance Criteria

- [ ] AC1：选择搜索字段「备注」并输入子串，仅返回备注包含该子串的用户（大小写按 DB collation；空备注不匹配非空关键词）。
- [ ] AC2：点击流量列表头可在已用流量 DESC / ASC 间切换，分页结果顺序与 `u+d` 一致，且 `total` 正确。
- [ ] AC3：选「已过期」只出现到期时间早于当前时间的用户；选「未过期」包含长期有效与未到到期日的用户。
- [ ] AC4：默认进入列表时排序仍为创建时间倒序；未选手动流量排序时行为与改前一致。
- [ ] AC5：清除筛选后恢复：无搜索词、封禁/套餐/过期均为「全部」、排序回 `created_at DESC`。

## Out of Scope

- 按上传/下载单独排序、按剩余流量排序。
- 过期时间区间筛选、即将过期提醒。
- CSV 导出排序规则改造（仍按现有 dump 逻辑）。
- PHP 端对齐改造（本任务只改 Java API + Vue UI）。

## Key Decisions

| 决策 | 选择 |
|------|------|
| 未过期是否含长期 | 是（`expired_at` null） |
| 过期 UI | 与封禁类似的快捷 tab：全部 / 未过期 / 已过期 |
| 流量排序 UI | 「流量」表头可点击，在 DESC ↔ ASC 切换；未点击时保持默认 `created_at` |
| 虚拟 filter key | `expired`，`=`，`value` 为 `0`（未过期）/ `1`（已过期） |
| 排序参数 | `sort=total_used`，`sort_type=ASC\|DESC` |

## Open Questions

（无阻塞项）
