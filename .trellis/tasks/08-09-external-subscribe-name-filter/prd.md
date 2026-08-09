# 第三方订阅名称过滤规则

## Goal

管理端编辑第三方订阅源时可配置多条名称规则；同步写库时对节点显示名执行「匹配 → 替换」（替换可为空），支持字面量与正则。

## Background

- UI：`v2board-ui` `AdminExternalSubscribeView` 编辑弹窗现仅 name / url / remark / enable。
- API：`AdminExternalSubscribeController.save`；同步：`ExternalSubscribeSyncService` upsert 写 `name` / outbound / share_uri。
- 示例：pattern=`abc`、replacement=`""` → `xxxxabcxxname` → `xxxxxxname`。

## Requirements

1. 每源多条规则，顺序依次应用。
2. 规则字段：`pattern`（必填非空）、`replacement`（可空字符串）、`regex`（boolean，独立开关）。
3. 字面量：子串全局替换为 `replacement`。
4. 正则：Java `Matcher.replaceAll`；支持 `$1` 等捕获组；`replacement` 可为空。
5. **同步时**重写显示名，并同步 `singbox_outbound.tag` 与 `share_uri` 备注；fingerprint / 逻辑键不受名称影响。
6. **保存时**校验：`regex=true` 的 pattern 必须可编译，否则拒绝保存并返回明确错误。
7. 空 pattern 的规则：保存时拒绝或忽略（实现取拒绝空 pattern）。
8. 改规则后需重新同步才更新已有节点名。

## Out of Scope

- 不按规则丢弃节点。
- 不下发时二次过滤。
- 不自动触发同步（保存后不强制 sync）。

## Acceptance Criteria

- [x] 编辑弹窗可增删多条规则并随源保存；fetch 回显。
- [x] 字面量替换（含空 replacement）正确。
- [x] 正则替换（含空 replacement 与 `$n`）正确。
- [x] 非法正则保存失败，提示可读。
- [ ] 同步后管理端节点列表与用户订阅名称一致反映过滤结果。（待部署后手动点验）
- [x] 单元测试覆盖字面量 / 正则 / 空替换 / 非法正则校验。

## Decisions

| # | Decision | Choice |
|---|----------|--------|
| 1 | 规则语义 | 匹配 → 替换，replacement 可空 |
| 2 | 生效时机 | 同步写库时改名 |
| 3 | 非法正则 | 保存时校验并拒绝 |
| 4 | 正则开关 | 每条独立 `regex` |
| 5 | 捕获组 | 启用（`replaceAll`） |

## Notes

- 跨仓：`v2board-java-api` + `v2board-ui`。
- 复杂任务：`design.md` + `implement.md`；批准后 `task.py start`。
