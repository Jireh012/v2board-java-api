# Design: 管理端订阅规则

见父 `design.md`：表结构、Redis、SanitizePipeline、Admin API（fetch/save/sync/restore）、UI。

API 草图（最终以 PHP/Java 管理端风格对齐）：

- `GET /api/v1/admin/subscribe-rule/fetch?format=`
- `POST /api/v1/admin/subscribe-rule/save`
- `POST /api/v1/admin/subscribe-rule/sync`
- `POST /api/v1/admin/subscribe-rule/restore`
