## PHP 接口基线：登录 / 仪表盘 / 套餐 / 下单

> 本文档根据 `d:/repos/v2board` 中 Laravel 源码梳理，作为 Java 版对齐的参考基线。

### 1. Passport 认证相关接口（/api/v1/passport）

- **POST `/api/v1/passport/auth/login`**
  - 控制器：`V1\Passport\AuthController@login`
  - 请求体（`application/x-www-form-urlencoded`）：
    - `email`：邮箱
    - `password`：密码
  - 关键业务规则：
    - 校验密码错误次数，超过阈值（默认 5 次）一段时间内禁止再次尝试。
    - 校验邮箱是否存在。
    - 校验密码是否正确，错误时统一提示“邮箱或密码错误”。
    - 校验账号是否被封禁（`banned` 字段）。
  - 响应（成功，HTTP 200）：
    - JSON：`{"data": {"token": string, "is_admin": bool, "auth_data": string}}`
      - `token`：用户自身 token，用于订阅链接等。
      - `is_admin`：是否管理员。
      - `auth_data`：JWT 字符串，后续作为 `auth_data` 参数或 `Authorization` 头传回。
  - 响应（失败）：
    - HTTP 500，body 为错误信息字符串（例如“邮箱或密码错误”、“账号被封禁”等）。

- **POST `/api/v1/passport/auth/register`**
  - 控制器：`V1\Passport\AuthController@register`
  - 关键点：
    - 可选：IP 注册限频、reCAPTCHA 校验、邮箱后缀白名单、Gmail 别名限制。
    - 可选：邀请码强制、邮件验证码校验。
    - 可选：试用套餐自动开通（`try_out_plan_id`）。
  - 响应（成功）：
    - JSON：`{"data": {"token": string, "is_admin": bool, "auth_data": string}}`

- **POST `/api/v1/passport/auth/forget`**
  - 控制器：`V1\Passport\AuthController@forget`
  - 功能：通过邮箱 + 邮件验证码重置密码，并清空所有 session。
  - 响应（成功）：`{"data": true}`

### 2. 用户信息 / 仪表盘相关（/api/v1/user）

用户路由前缀：`/api/v1/user`，通过 `User` 中间件强制登录，`$request->user` 为当前用户信息数组。

- **GET `/api/v1/user/info`**
  - 控制器：`V1\User\UserController@info`
  - 主要字段：
    - `email`、`transfer_enable`、`device_limit`、`last_login_at`、`created_at`、
      `banned`、`auto_renewal`、`remind_expire`、`remind_traffic`、`expired_at`、
      `balance`、`commission_balance`、`plan_id`、`discount`、`commission_rate`、
      `telegram_id`、`uuid`
    - 额外字段：`avatar_url`
  - 响应：`{"data": { ...上述字段... }}`。

- **GET `/api/v1/user/getStat`**
  - 控制器：`V1\User\UserController@getStat`
  - 返回数组 `[未支付订单数, 未解决工单数, 邀请用户数]`。
  - 响应：`{"data": [int, int, int]}`

- **GET `/api/v1/user/getSubscribe`**
  - 控制器：`V1\User\UserController@getSubscribe`
  - 字段：
    - 用户基础：`plan_id`、`token`、`expired_at`、`u`、`d`、`transfer_enable`、
      `device_limit`、`email`、`uuid`
    - 若 `plan_id` 存在，则附带 `plan` 对象。
    - 统计字段：`alive_ip`（Redis 中在线设备数）。
    - 订阅链接：`subscribe_url`（由 `Helper::getSubscribeUrl(token)` 生成）。
    - 其它：`reset_day`、`allow_new_period`。
  - 响应：`{"data": { ... }}`。

- **GET `/api/v1/user/getActiveSession` / POST `/api/v1/user/removeActiveSession`**
  - 控制器：`V1\User\UserController@getActiveSession/removeActiveSession`
  - 依赖 `AuthService::getSessions/removeSession`，管理当前账号在不同设备的登录会话。

- **POST `/api/v1/user/changePassword`**
  - 控制器：`V1\User\UserController@changePassword`
  - 逻辑：
    - 校验旧密码（支持多种加密算法）。
    - 更新密码后，清空所有 session，要求重新登录。
  - 响应：`{"data": true}`。

### 3. 套餐列表（/api/v1/user/plan/fetch）

- **GET `/api/v1/user/plan/fetch`**
  - 控制器：`V1\User\PlanController@fetch`
  - 行为：
    - 若带 `id` 参数：
      - 返回单个套餐，检查 `show` / `renew` 等字段，避免返回不可见套餐。
    - 否则：
      - 查询所有 `show=1` 的套餐，按 `sort ASC` 排序。
      - 结合 `PlanService::countActiveUsers()` 动态计算剩余容量（capacity_limit）。
  - 响应：
    - 单个：`{"data": { ...plan... }}`
    - 列表：`{"data": [{ ...plan... }, ...]}`。

### 4. 订单 / 下单 / 支付（/api/v1/user/order, /api/v1/guest/payment）

- **GET `/api/v1/user/order/fetch`**
  - 控制器：`V1\User\OrderController@fetch`
  - 返回当前用户订单列表，可按 `status` 过滤。
  - 数据中附带对应的 `plan` 信息，并隐藏部分字段（如 `id`, `user_id`）。

- **GET `/api/v1/user/order/detail`**
  - 控制器：`V1\User\OrderController@detail`
  - 根据 `trade_no` 返回订单详情，若为充值订单（`plan_id=0`）则附带赠送金额等。

- **POST `/api/v1/user/order/save`**
  - 控制器：`V1\User\OrderController@save`
  - 用途：
    - 创建充值订单（`plan_id=0` + `deposit_amount`）。
    - 创建购买/续费/重置流量订单（`plan_id>0` + `period` + 可选 `coupon_code`）。
  - 核心业务：
    - 校验是否存在未完成订单。
    - 校验套餐是否存在、是否售罄、是否允许当前周期购买。
    - 校验用户当前订阅状态，决定是否允许续费或购买重置包。
    - 处理余额抵扣和优惠券。
  - 响应：`{"data": "<trade_no>"}`。

- **POST `/api/v1/user/order/checkout`**
  - 控制器：`V1\User\OrderController@checkout`
  - 入参：
    - `trade_no`：订单号。
    - `method`：支付方式 ID。
    - 可选：`token`（Stripe token 等）。
  - 行为：
    - 若 `total_amount<=0`，直接调用 `OrderService::paid` 完成支付逻辑并返回：
      - `{"type": -1, "data": true}`
    - 否则：
      - 读取对应 `Payment` 配置，计算手续费，调用 `PaymentService::pay`。
      - 返回：
        - `{"type": <int>, "data": <string|object>}`，由具体支付驱动决定。

- **GET `/api/v1/user/order/check`**
  - 根据 `trade_no` 返回订单 `status`：`{"data": <int>}`。

- **GET `/api/v1/user/order/getPaymentMethod`**
  - 返回可用支付方式列表：`{"data": [{id, name, payment, icon, handling_fee_*}, ...]}`。

- **POST `/api/v1/user/order/cancel`**
  - 逻辑：
    - 仅允许取消 `status=0`（待支付）的订单。
    - 调用 `OrderService::cancel` 处理余额/佣金回滚等。

- **GET|POST `/api/v1/guest/payment/notify/{method}/{uuid}`**
  - 控制器：`V1\Guest\PaymentController@notify`
  - 行为：
    - 根据 `{method, uuid}` 构造 `PaymentService`，校验回调签名。
    - 调用私有 `handle(trade_no, callback_no)`：\n
      - 查找订单，若已处理直接返回 true。\n
      - 调用 `OrderService::paid` 完成支付逻辑（更新用户套餐/流量/佣金等）。\n
      - 发送 Telegram 收款通知。
    - 返回结果：
      - 成功：`"success"` 或支付驱动定义的 `custom_result` 字符串。
      - 失败：HTTP 500 + `"fail"`。

---

以上为“登录 + 仪表盘 + 套餐/下单”链路中最关键的 PHP 原版接口基线，Java 版应在路径、请求参数和响应结构上与之保持 1:1 对齐，内部实现可根据技术栈优化。

