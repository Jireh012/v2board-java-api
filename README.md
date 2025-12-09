# V2Board Java API

V2Board 订阅链接功能的 Java 实现，使用 Spring Boot 框架。

## 功能特性

- ✅ Token 验证中间件
- ✅ 用户可用性检查
- ✅ 多协议服务器支持（VMess、Shadowsocks、Trojan、Hysteria）
- ✅ 通用订阅格式（General）
- ✅ Clash 协议支持（基础实现）
- ✅ 服务器组过滤
- ✅ 端口范围随机选择

## 技术栈

- **框架**: Spring Boot 3.1.5
- **数据库**: MySQL 8.0+
- **ORM**: MyBatis-Plus 3.5.4.1
- **缓存**: Redis（可选）
- **Java 版本**: 17+

## 快速开始

### 1. 环境要求

- JDK 17 或更高版本
- Maven 3.6+
- MySQL 8.0+
- Redis（可选）

### 2. 配置数据库

编辑 `src/main/resources/application.yml`，修改数据库连接信息：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/v2board?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai
    username: your_username
    password: your_password
```

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
GET /api/v1/client/subscribe?token=xxxxx
```

**参数**
- `token` (必需): 用户 token

**响应**
- 成功: 返回 Base64 编码的订阅内容
- 失败: 返回错误信息（403）

**示例**
```bash
curl "http://localhost:8080/api/v1/client/subscribe?token=abc123"
```

## 项目结构

```
v2board-java-api/
├── src/main/java/com/v2board/api/
│   ├── controller/          # 控制器
│   │   └── ClientController.java
│   ├── service/             # 服务层
│   │   ├── UserService.java
│   │   └── ServerService.java
│   ├── model/               # 实体类
│   │   ├── User.java
│   │   ├── ServerVmess.java
│   │   ├── ServerShadowsocks.java
│   │   ├── ServerTrojan.java
│   │   └── ServerHysteria.java
│   ├── mapper/              # 数据访问层
│   │   ├── UserMapper.java
│   │   └── ...
│   ├── protocol/            # 协议处理器
│   │   ├── ProtocolHandler.java
│   │   ├── GeneralHandler.java
│   │   └── ClashHandler.java
│   ├── middleware/         # 中间件
│   │   └── ClientTokenInterceptor.java
│   ├── config/              # 配置类
│   │   ├── WebConfig.java
│   │   └── JacksonConfig.java
│   └── util/                # 工具类
│       └── Helper.java
└── src/main/resources/
    └── application.yml      # 配置文件
```

## 核心功能说明

### 1. Token 验证

通过 `ClientTokenInterceptor` 拦截器验证用户 token：
- 从请求参数获取 `token`
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
- **Clash**: Clash 客户端格式（需要完善 YAML 生成）

## 数据库表结构

项目使用以下数据库表（与 V2Board PHP 版本相同）：

- `v2_user`: 用户表
- `v2_server_vmess`: VMess 服务器表
- `v2_server_shadowsocks`: Shadowsocks 服务器表
- `v2_server_trojan`: Trojan 服务器表
- `v2_server_hysteria`: Hysteria 服务器表

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

## 注意事项

1. **端口范围处理**: 如果端口是范围格式（如 `1000-2000`），会自动随机选择
2. **特殊加密方式**: Shadowsocks 2022 加密方式需要特殊处理（已实现）
3. **时区问题**: 注意时间戳的时区转换
4. **字符编码**: URI 编码使用 UTF-8
5. **Base64 编码**: 使用 URL 安全的 Base64 编码（`+` → `-`, `/` → `_`, `=` 去除）

## 待完善功能

- [ ] 完整的 Clash YAML 配置生成
- [ ] Surge 协议支持
- [ ] Shadowrocket 协议支持
- [ ] QuantumultX 协议支持
- [ ] Redis 缓存集成
- [ ] 服务器在线状态检查
- [ ] 订阅信息节点添加（流量、到期时间等）

## 许可证

MIT License

## 贡献

欢迎提交 Issue 和 Pull Request！

