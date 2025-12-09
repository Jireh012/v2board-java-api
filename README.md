# V2Board Java API

V2Board 订阅链接功能的 Java 实现，使用 Spring Boot 框架。完整实现了 PHP 版本的订阅信息获取功能。

## 功能特性

- ✅ Token 验证中间件
- ✅ 用户可用性检查
- ✅ 多协议服务器支持
  - VMess（支持多种网络类型：tcp、ws、grpc等）
  - VLESS（支持 Reality TLS、流控、多种网络类型）
  - Shadowsocks（支持 2022 加密方式）
  - Trojan
  - Hysteria
- ✅ 通用订阅格式（General）- Base64 编码
- ✅ Clash 协议支持
- ✅ 服务器组过滤
- ✅ 端口范围随机选择
- ✅ 订阅信息节点显示（剩余流量、重置天数、到期时间）
- ✅ 多种订阅方法支持（直接token、OTP、TOTP）
- ✅ sing-box 版本检测和处理器选择

## 技术栈

- **框架**: Spring Boot 3.1.5
- **数据库**: MySQL 8.0+
- **ORM**: MyBatis-Plus 3.5.4.1
- **缓存**: Redis（用于在线设备统计）
- **Java 版本**: 17+
- **JWT**: jjwt 0.11.5

## 快速开始

### 1. 环境要求

- JDK 17 或更高版本
- Maven 3.6+
- MySQL 8.0+
- Redis（用于在线设备统计和缓存）

### 2. 配置说明

项目使用环境变量来配置敏感信息，避免将密码等敏感信息提交到代码仓库。

#### 方式一：使用启动脚本（推荐）

**Windows:**
```bash
# 编辑 start-with-env.bat，修改其中的环境变量
start-with-env.bat
```

**Linux/Mac:**
```bash
# 编辑 start-with-env.sh，修改其中的环境变量
chmod +x start-with-env.sh
./start-with-env.sh
```

#### 方式二：手动设置环境变量

**Windows PowerShell:**
```powershell
$env:DB_HOST="localhost"
$env:DB_PORT="3306"
$env:DB_NAME="v2board"
$env:DB_USERNAME="root"
$env:DB_PASSWORD="your_password"
$env:REDIS_HOST="localhost"
$env:REDIS_PORT="6379"
$env:REDIS_PASSWORD="your_redis_password"
$env:REDIS_DATABASE="0"
$env:APP_KEY="base64:your-secret-key-here"
```

**Linux/Mac:**
```bash
export DB_HOST=localhost
export DB_PORT=3306
export DB_NAME=v2board
export DB_USERNAME=root
export DB_PASSWORD=your_password
export REDIS_HOST=localhost
export REDIS_PORT=6379
export REDIS_PASSWORD=your_redis_password
export REDIS_DATABASE=0
export APP_KEY=base64:your-secret-key-here
```

#### 环境变量列表

| 变量名 | 说明 | 默认值 |
|--------|------|--------|
| `DB_HOST` | MySQL 主机地址 | `localhost` |
| `DB_PORT` | MySQL 端口 | `3306` |
| `DB_NAME` | 数据库名称 | `v2board` |
| `DB_USERNAME` | 数据库用户名 | `root` |
| `DB_PASSWORD` | 数据库密码 | 空 |
| `DB_SSL` | 是否使用 SSL | `false` |
| `REDIS_HOST` | Redis 主机地址 | `localhost` |
| `REDIS_PORT` | Redis 端口 | `6379` |
| `REDIS_PASSWORD` | Redis 密码 | 空 |
| `REDIS_DATABASE` | Redis 数据库编号 | `0` |
| `REDIS_TIMEOUT` | Redis 连接超时 | `3000ms` |
| `REDIS_SSL` | Redis 是否使用 SSL | `false` |
| `APP_KEY` | JWT 密钥（需与 PHP 项目一致） | `base64:your-secret-key-here` |

> 📖 详细配置说明请参考 [ENV_CONFIG.md](ENV_CONFIG.md)

### 3. 编译运行

```bash
# 编译项目
mvn clean package

# 运行项目
java -jar target/v2board-java-api-1.0.0.jar

# 或者使用 Maven 直接运行
mvn spring-boot:run
```

### 4. 测试订阅链接

访问以下 URL 测试订阅功能：

```
http://localhost:8080/api/v1/client/subscribe?token=your_user_token
```

## API 接口

### 订阅接口

**请求**
```
GET /api/v1/client/subscribe?token=xxxxx&flag=xxxxx
```

**参数**
- `token` (必需): 用户 token
- `flag` (可选): 协议标识，用于选择特定的协议处理器。如果不提供，将从 `User-Agent` 头中获取

**支持的 flag 值**
- `clash` - Clash 客户端
- `sing-box` - sing-box 客户端（会根据版本自动选择新/旧版本处理器）
- 其他协议标识（根据注册的 ProtocolHandler 决定）

**响应**
- 成功: 返回订阅内容（文本格式）
- 失败: 返回空字符串或错误信息（403）

**示例**
```bash
# 通用订阅格式
curl "http://localhost:8080/api/v1/client/subscribe?token=abc123"

# Clash 格式
curl -H "User-Agent: Clash" "http://localhost:8080/api/v1/client/subscribe?token=abc123"

# 指定 flag
curl "http://localhost:8080/api/v1/client/subscribe?token=abc123&flag=clash"
```

## 项目结构

```
v2board-java-api/
├── src/main/java/com/v2board/api/
│   ├── controller/          # 控制器
│   │   └── ClientController.java  # 订阅接口控制器
│   ├── service/             # 服务层
│   │   ├── UserService.java        # 用户服务（可用性检查、重置天数计算）
│   │   ├── ServerService.java      # 服务器服务（获取可用服务器列表）
│   │   ├── CacheService.java       # 缓存服务（Redis 操作）
│   │   └── AuthService.java        # 认证服务（JWT 解密）
│   ├── model/               # 实体类
│   │   ├── User.java
│   │   ├── Plan.java
│   │   ├── ServerVmess.java
│   │   ├── ServerVless.java
│   │   ├── ServerShadowsocks.java
│   │   ├── ServerTrojan.java
│   │   └── ServerHysteria.java
│   ├── mapper/              # 数据访问层（MyBatis-Plus）
│   │   ├── UserMapper.java
│   │   ├── PlanMapper.java
│   │   └── Server*Mapper.java
│   ├── protocol/            # 协议处理器
│   │   ├── ProtocolHandler.java    # 协议处理器接口
│   │   ├── GeneralHandler.java     # 通用格式处理器
│   │   └── ClashHandler.java       # Clash 格式处理器
│   ├── middleware/         # 中间件
│   │   └── ClientTokenInterceptor.java  # Token 验证拦截器
│   ├── config/              # 配置类
│   │   ├── WebConfig.java          # Web 配置（拦截器注册）
│   │   ├── RedisConfig.java        # Redis 配置
│   │   └── JacksonConfig.java      # JSON 配置
│   └── util/                # 工具类
│       └── Helper.java             # 工具方法（Base64、流量转换、订阅URL生成等）
├── src/main/resources/
│   ├── application.yml             # 主配置文件（使用环境变量）
│   └── application.yml.example      # 配置示例文件
├── start.bat                        # Windows 启动脚本
├── start.sh                         # Linux/Mac 启动脚本
├── start-with-env.bat               # Windows 启动脚本（带环境变量）
├── start-with-env.sh                # Linux/Mac 启动脚本（带环境变量）
├── ENV_CONFIG.md                    # 环境变量配置说明
└── README.md                        # 本文件
```

## 核心功能说明

### 1. Token 验证

通过 `ClientTokenInterceptor` 拦截器验证用户 token：
- 从请求参数获取 `token`
- 支持多种订阅方法：
  - 方法 0：直接使用 token
  - 方法 1：OTP（一次性密码）
  - 方法 2：TOTP（基于时间的密码）
- 查询数据库验证用户是否存在
- 将用户信息存储到 request 属性中

### 2. 用户可用性检查

检查用户是否可用：
- 用户未被封禁 (`banned = 0`)
- 用户有流量配额 (`transfer_enable > 0`)
- 用户未过期 (`expired_at > 当前时间` 或 `expired_at IS NULL`)

### 3. 服务器过滤

根据以下规则过滤服务器：
- 服务器显示状态 (`show = 1`)
- 用户组匹配 (`group_id` 包含用户的 `group_id`)
- 按 `sort` 字段排序

### 4. 协议处理

根据 `User-Agent` 或 `flag` 参数选择协议处理器：
- **General**: 默认格式，Base64 编码的通用订阅
  - 支持 VMess、VLESS、Shadowsocks、Trojan 等协议
  - VLESS 完整支持 Reality TLS、流控、多种网络类型
- **Clash**: Clash 客户端格式（YAML 配置）
- **sing-box**: 根据版本自动选择新/旧版本处理器

### 5. 订阅信息节点

对于非 sing-box 客户端，可以在订阅内容前添加信息节点：
- 剩余流量
- 距离下次重置剩余天数
- 套餐到期时间

通过配置 `v2board.show-info-to-server-enable=true` 启用。

## VLESS 协议支持

完整实现了 PHP 版本的 VLESS 订阅信息生成，包括：

### TLS 支持
- **None** (`tls=0`): 无 TLS
- **TLS** (`tls=1`): 标准 TLS
- **Reality** (`tls=2`): Reality TLS
  - 支持 `pbk` (public_key) 和 `sid` (short_id) 参数
  - 支持 `fp` (fingerprint) 和 `sni` (server_name) 参数

### 流控支持
- 支持 `flow` 字段（如 `xtls-rprx-vision`）

### 网络类型支持
- **TCP**: 支持 HTTP 伪装头
- **WebSocket**: 支持 path 和 headers
- **gRPC**: 支持 serviceName
- **KCP**: 支持 header 和 seed
- **HTTP Upgrade**: 支持 path 和 host
- **XHTTP**: 支持 path、host、mode 和 extra

### 加密支持
- 支持 `mlkem768x25519plus` 加密方式

## 数据库表结构

项目使用以下数据库表（与 V2Board PHP 版本相同）：

- `v2_user`: 用户表
- `v2_plan`: 套餐表
- `v2_server_vmess`: VMess 服务器表
- `v2_server_vless`: VLESS 服务器表
- `v2_server_shadowsocks`: Shadowsocks 服务器表
- `v2_server_trojan`: Trojan 服务器表
- `v2_server_hysteria`: Hysteria 服务器表

## 配置说明

### application.yml 配置项

```yaml
v2board:
  show-info-to-server-enable: true  # 是否在订阅中显示信息节点
  reset-traffic-method: 0            # 默认流量重置方式
  show-subscribe-method: 0           # 订阅方法：0-直接token，1-OTP，2-TOTP
  subscribe-path: /api/v1/client/subscribe  # 订阅路径
  subscribe-url:                     # 订阅URL列表（逗号分隔）
  show-subscribe-expire: 5           # TOTP过期时间（分钟）
  allow-new-period: 0                # 是否允许新周期
```

## 开发说明

### 添加新的协议处理器

1. 实现 `ProtocolHandler` 接口
2. 使用 `@Component` 注解注册为 Spring Bean
3. 在 `getFlag()` 方法中返回协议标识
4. 在 `handle()` 方法中实现订阅内容生成逻辑

示例：
```java
@Component
public class MyProtocolHandler implements ProtocolHandler {
    @Override
    public String getFlag() {
        return "myprotocol";
    }
    
    @Override
    public String handle(User user, List<Map<String, Object>> servers) {
        // 实现订阅内容生成
        return "订阅内容";
    }
}
```

### 流量重置方式说明

- `0`: 每月第一天重置
- `1`: 到期日重置
- `2`: 不重置
- `3`: 每年第一天重置
- `4`: 每年到期日重置

## 注意事项

1. **APP_KEY 配置**: 必须与 PHP 项目的 `APP_KEY` 保持一致，否则 JWT 认证会失败
2. **端口范围处理**: 如果端口是范围格式（如 `1000-2000`），会自动随机选择
3. **特殊加密方式**: Shadowsocks 2022 加密方式需要特殊处理（已实现）
4. **时区问题**: 注意时间戳的时区转换
5. **字符编码**: URI 编码使用 UTF-8
6. **Base64 编码**: 使用 URL 安全的 Base64 编码（`+` → `-`, `/` → `_`, `=` 去除）
7. **IPv6 地址**: 自动识别 IPv6 地址并添加 `[]` 括号
8. **环境变量**: 生产环境建议使用环境变量配置敏感信息，不要硬编码

## 与 PHP 版本的兼容性

- ✅ 完全兼容 PHP 版本的数据库结构
- ✅ 完全兼容 PHP 版本的订阅格式
- ✅ 支持 PHP 版本的所有协议类型
- ✅ 支持 PHP 版本的所有订阅方法
- ✅ 订阅信息节点格式与 PHP 版本一致

## 许可证

MIT License

## 贡献

欢迎提交 Issue 和 Pull Request！
