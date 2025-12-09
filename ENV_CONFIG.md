# 环境变量配置说明

本项目使用环境变量来配置敏感信息，避免将数据库密码等敏感信息提交到代码仓库。

## 配置方式

### 方式一：使用系统环境变量（推荐）

在运行应用前，设置以下环境变量：

**Windows (PowerShell):**
```powershell
$env:DB_HOST="localhost"
$env:DB_PORT="3306"
$env:DB_NAME="v2board"
$env:DB_USERNAME="root"
$env:DB_PASSWORD="your_password"
$env:REDIS_HOST="localhost"
$env:REDIS_PORT="6379"
$env:REDIS_PASSWORD=""
$env:REDIS_DATABASE="0"
$env:APP_KEY="base64:your-secret-key-here"
$env:SUBSCRIBE_PATH=""  # 可选，留空则使用默认路径
```

**Windows (CMD):**
```cmd
set DB_HOST=localhost
set DB_PORT=3306
set DB_NAME=v2board
set DB_USERNAME=root
set DB_PASSWORD=your_password
set REDIS_HOST=localhost
set REDIS_PORT=6379
set REDIS_PASSWORD=
set REDIS_DATABASE=0
set APP_KEY=base64:your-secret-key-here
set SUBSCRIBE_PATH=  # 可选，留空则使用默认路径
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
export REDIS_PASSWORD=
export REDIS_DATABASE=0
export APP_KEY=base64:your-secret-key-here
export SUBSCRIBE_PATH=""  # 可选，留空则使用默认路径
```

### 方式二：使用 .env 文件（需要额外依赖）

如果需要使用 `.env` 文件，需要添加 `spring-dotenv` 依赖，或者使用 `jasypt-spring-boot` 进行加密配置。

### 方式三：使用 application-{profile}.yml

创建 `application-prod.yml` 文件（已在 .gitignore 中排除），在其中配置生产环境的具体值。

## 环境变量列表

### MySQL 数据库配置

| 变量名 | 说明 | 默认值 | 示例 |
|--------|------|--------|------|
| `DB_HOST` | 数据库主机地址 | `localhost` | `192.168.1.100` |
| `DB_PORT` | 数据库端口 | `3306` | `3306` |
| `DB_NAME` | 数据库名称 | `v2board` | `v2board` |
| `DB_USERNAME` | 数据库用户名 | `root` | `v2board_user` |
| `DB_PASSWORD` | 数据库密码 | 空 | `your_password` |
| `DB_SSL` | 是否使用SSL | `false` | `true` / `false` |

### Redis 配置

| 变量名 | 说明 | 默认值 | 示例 |
|--------|------|--------|------|
| `REDIS_HOST` | Redis主机地址 | `localhost` | `192.168.1.100` |
| `REDIS_PORT` | Redis端口 | `6379` | `6379` |
| `REDIS_PASSWORD` | Redis密码 | 空 | `your_redis_password` |
| `REDIS_DATABASE` | Redis数据库编号 | `0` | `0` |
| `REDIS_TIMEOUT` | 连接超时时间 | `3000ms` | `5000ms` |
| `REDIS_SSL` | 是否使用SSL | `false` | `true` / `false` |

### 应用配置

| 变量名 | 说明 | 默认值 | 示例 |
|--------|------|--------|------|
| `APP_KEY` | JWT密钥（需要与PHP项目的APP_KEY保持一致） | `base64:your-secret-key-here` | `base64:xxxxx` |
| `SUBSCRIBE_PATH` | 订阅路径（留空则使用默认路径 `/api/v1/client/subscribe`） | 空 | `/rss_subscribe` |

## 注意事项

1. **APP_KEY 配置**：必须与 PHP 项目的 `APP_KEY` 保持一致，否则 JWT 认证会失败
2. **数据库连接**：确保数据库服务器允许远程连接（如果使用远程数据库）
3. **Redis 连接**：如果 Redis 设置了密码，必须配置 `REDIS_PASSWORD`
4. **生产环境**：建议使用系统环境变量或加密配置文件，不要将敏感信息硬编码

## 验证配置

启动应用后，检查日志确认配置是否正确加载。如果环境变量未设置，将使用 `application.yml` 中的默认值。

