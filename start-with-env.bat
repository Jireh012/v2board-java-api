@echo off
REM 启动脚本 - 使用环境变量配置
REM 请根据实际情况修改以下环境变量

REM 设置MySQL数据库配置
set DB_HOST=localhost
set DB_PORT=3306
set DB_NAME=v2board
set DB_USERNAME=root
set DB_PASSWORD=your_password
set DB_SSL=false

REM 设置Redis配置
set REDIS_HOST=localhost
set REDIS_PORT=6379
set REDIS_PASSWORD=
set REDIS_DATABASE=0
set REDIS_TIMEOUT=3000ms
set REDIS_SSL=false

REM 设置应用配置
set APP_KEY=base64:your-secret-key-here
REM 设置订阅路径（可选，留空则使用默认路径 /api/v1/client/subscribe）
set SUBSCRIBE_PATH=

REM 启动应用
echo Starting V2Board Java API...
echo DB_HOST=%DB_HOST%
echo DB_NAME=%DB_NAME%
echo REDIS_HOST=%REDIS_HOST%
echo.
mvn spring-boot:run

