# 前端抗探测：假落地与构建指纹收敛

## Goal

降低面板前端被浅扫识别为「默认 V2Board SPA」的概率：未授权访问 decoy 路径时展示中性**云/科技假官网**且不请求 `/config`；生产构建减少静态指纹。真实登录/注册/后台入口保持可用。

## Background

- R1（延后 `/config` + decoy 路由 + `adminSeg`）与 R2（构建指纹）已实现；根路径当前为极简 404。
- 用户增量要求：将 decoy 升级为完整假官网（中性云/科技落地页）。
- 固定 `/config` 路径不改；VPN/机场话术禁止。

## Key Decisions

| 决策 | 选择 |
|------|------|
| R1/R2 | 已实现（延后配置、adminSeg、hash 构建等） |
| 假官网类型 | 中性云/科技公司落地页 |
| 路径覆盖 | 安全模式开启时：未登录 `/` + catch-all → 假官网；关闭 → `/login` |
| 品牌 | 假官网固定 **「苍穹云」 / Aether Cloud**；登录相关页隐藏 `app_name` |
| 文案语言 | 中文为主，品牌可带英文副标 |
| CTA | 假官网展示「成员登录」→ `/login`；无注册 CTA |
| `/config` | 路由需读 `safe_mode_enable`；`DecoyView` 本身不拉配置 |

## Requirements

### R1 / R2

保持已实现行为与 AC1–AC7（AC1/AC2「中性壳」升级为「假官网」观感，Network 约束不变）。

### R3 假官网

1. 仅当 `safe_mode_enable=1` 时，未登录 `/` 与未知路径渲染假官网；关闭时直达 `/login`。
2. 假官网固定「苍穹云」，不使用 `app_name`；视图不 import `siteBrand`（路由可 `ensureSiteBrand` 读开关）。
3. 文案与视觉不得暗示 VPN、代理、机场、订阅、翻墙；可有「成员登录」。
4. 已登录访问 `/` 仍进用户中心；`/login` 等真实入口不变。
5. 安全模式开启时未知路径与 `/` 同一假官网组件。

## Out of Scope

- CMS / 后台可编辑文案
- 隐藏 `/login` `/register` `/forget` 或改 `/config` 路径
- 表单提交到后端、真实客服/工单
- 多页面路由站（关于我们独立 URL 等）；锚点滚动可接受
- 重型混淆、节点/DPI

## Acceptance Criteria

| ID | Criterion |
|----|-----------|
| AC1 | `safe_mode_enable=1` 时隐私窗口 `/`：假官网；`=0` 时 `/` → `/login` |
| AC2 | 安全模式开启时 `/random-scan` 与 `/` 同为假官网；关闭时进登录 |
| AC3 | `/login` 等真实入口可用，允许 `/config` |
| AC4 | 已登录 `/` → 用户中心 |
| AC5 | 清存储后 `secure_path` 可进后台登录 |
| AC6 | 生产无 `.map`；资产 hash 名（R2，回归） |
| AC7 | 假官网可见文案无字面量 `V2Board`；可有「成员登录」链到 `/login`，无注册 CTA |
| AC8 | 假官网无 VPN/机场/订阅类话术 |

## Risks

- SPA 仍下载完整 JS；假官网只改善首屏与配置探测面。
- 假官网/登录故意不用真实 `app_name`，与登录后用户壳品牌可能不一致——可接受。
