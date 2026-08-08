# 节点通讯配置完善

## Goal

管理端「节点」配置保存后完整作用于 UniProxy/v2node；通讯密钥与间隔有校验；管理端可一键生成密钥。

## Requirements

1. UniProxy `config` 的 `base_config` 除 push/pull 外，下发 `node_report_min_traffic`、`device_online_min_traffic`（对齐 V2Server / v2node）。  
2. `server_token` 空或 &lt;16 时节点鉴权失败；管理端保存时校验长度。  
3. pull/push 间隔保存时 ≥1；流量阈值 ≥0。  
4. 管理端：通讯密钥可一键生成（≥16 随机）、可选显示/隐藏。  
5. 设备限制模式保持现有 alive 逻辑（0 按节点 / 1 全局去重）。

## Acceptance Criteria

| ID | Criterion |
|----|-----------|
| AC1 | UniProxy config.base_config 含四项间隔/阈值字段 |
| AC2 | token 不足 16 位无法保存；空 token 节点请求失败 |
| AC3 | 管理端可生成并保存 token |
| AC4 | device_limit_mode 仍影响 alive 计数 |

## Out of Scope

- 改节点安装脚本域名  
- 服务端二次过滤 push 流量（由节点侧按阈值处理）  
