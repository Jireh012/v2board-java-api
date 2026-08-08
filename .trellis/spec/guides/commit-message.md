# Commit Message Convention

> 本仓库提交消息使用 **Conventional Commits + 中文**。Phase 3.4 起草 commit 时必须遵守。

---

## Format

```text
<type>(<scope>): <摘要>

<正文>
```

| 段 | 要求 |
|----|------|
| `type` | 小写英文：`feat` / `fix` / `docs` / `refactor` / `test` / `chore` / `perf` 等 |
| `scope` | 小写英文短标识（模块/域），如 `order`、`passport`、`subscribe` |
| 摘要 | **中文**，一句话说明「做了什么 / 为何」，不以句号结尾；建议 ≤ 50 字 |
| 正文 | **中文**，空一行后写；补充动机、兼容性、影响面；可多行 |

HEREDOC 示例：

```bash
git commit -m "$(cat <<'EOF'
feat(subscribe): 订阅信息节点按管理端配置展示

开关读 DB；展示方式过滤节点；提前天数驱动「即将到期」徽章，不再误接订阅 URL。
EOF
)"
```

---

## Type 选用

| type | 何时用 |
|------|--------|
| `feat` | 新能力或用户可感知行为 |
| `fix` | 修复错误 / 未生效配置 |
| `docs` | 仅文档或 `.trellis/spec` |
| `refactor` | 无行为变化的结构调整 |
| `test` | 仅测试 |
| `chore` | 构建、杂项、工具链 |
| `perf` | 性能 |

---

## Good / Bad

#### Good

```text
feat(order): 开通时按开关清零已用流量

新购/续费/变更事件改为 0/1 开关；对齐 PHP openEvent(1)。
```

```text
fix(passport): 密码错误限次按邮箱计数

与 PHP 一致；修正管理端「同 IP」文案误导。
```

#### Bad

```text
完善订阅与订单配置
```

缺 `type(scope):` 前缀。

```text
feat(order): add open event toggle
```

摘要/正文应用中文（scope/type 保持英文）。

```text
feat(order): 开通清零。加了测试。改了 UI。
```

摘要应短；细节放到正文，不要堆在一行。

---

## Rules

1. 一个逻辑变更一个 commit；勿把无关改动塞进同一条。
2. 起草前可 `git log --oneline -5` 对照 scope 命名，但**格式以本文为准**（中文正文优先于历史英文习惯）。
3. 勿在消息里写密钥、`.env`、token。
4. Trellis 工具链噪音（hooks / template-hashes）默认不进业务 commit，除非本次就是改 Trellis。
