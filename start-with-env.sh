#!/bin/bash
# 启动脚本 - 使用环境变量配置
# 请根据实际情况修改以下环境变量

# 设置MySQL数据库配置
export DB_HOST=localhost
export DB_PORT=3306
export DB_NAME=v2board
export DB_USERNAME=root
export DB_PASSWORD=your_password
export DB_SSL=false

# 设置Redis配置
export REDIS_HOST=localhost
export REDIS_PORT=6379
export REDIS_PASSWORD=
export REDIS_DATABASE=0
export REDIS_TIMEOUT=3000ms
export REDIS_SSL=false

# 设置应用配置
export APP_KEY=base64:your-secret-key-here
# 设置订阅路径（可选，留空则使用默认路径 /api/v1/client/subscribe）
export SUBSCRIBE_PATH=""

# 启动应用
echo "Starting V2Board Java API..."
echo "DB_HOST=$DB_HOST"
echo "DB_NAME=$DB_NAME"
echo "REDIS_HOST=$REDIS_HOST"
echo ""
mvn spring-boot:run

