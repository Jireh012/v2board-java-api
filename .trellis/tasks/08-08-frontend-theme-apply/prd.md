# 个性化主题配置生效

## Goal

管理端「个性化」中侧栏/头部/主题色/背景图保存后，用户端（含登录页）实际应用；PHP 主题包名仅兼容存库。

## Requirements

1. 公开配置 SM4 下发：`frontend_theme_sidebar`、`frontend_theme_header`、`frontend_theme_color`、`frontend_background_url`。  
2. 用户壳：头部亮/暗、侧栏亮/暗、主题色（default/darkblue/black/green）、背景图 URL。  
3. 登录/注册/忘记密码页应用主题色与背景图。  
4. `frontend_theme`（如 v2board）继续可保存，管理端文案标明「旧主题包名，本前端不切换」。  
5. 管理端布局不受影响。

## Acceptance Criteria

| ID | Criterion |
|----|-----------|
| AC1 | 头部改暗色保存后用户顶栏为暗色风格 |
| AC2 | 主题色绿色后主色/强调色变为绿色系 |
| AC3 | 背景图 URL 有效时用户壳与登录页可见背景 |
| AC4 | 管理端保存仍成功；admin 布局不变 |

## Out of Scope

- 多套完整主题包切换（frontend_theme）  
- 管理端跟随个性化  
