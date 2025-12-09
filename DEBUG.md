# 调试指南

## 问题：无法返回订阅内容

### 检查步骤

1. **查看日志输出**
   
   重新启动应用并访问订阅链接，查看控制台日志：
   ```
   http://localhost:8080/api/v1/client/subscribe?token=d5e35e8b28fbb44d566c29df7b4810d5
   ```

   应该能看到类似以下日志：
   - `Processing subscribe request for user: xxx`
   - `Found X available servers for user xxx`
   - `Using protocol handler: GeneralHandler`
   - `Generated X bytes of subscribe content`

2. **检查可能的问题**

   **问题1：用户不可用**
   - 日志：`User xxx is not available`
   - 检查：用户是否被封禁、是否过期、是否有流量配额

   **问题2：没有可用服务器**
   - 日志：`No available servers found for user xxx` 或 `Found 0 available servers`
   - 检查：
     - 数据库中是否有服务器记录
     - 服务器的 `show` 字段是否为 1
     - 用户的 `group_id` 是否在服务器的 `group_id` 列表中

   **问题3：服务器类型不匹配**
   - 日志：`Unknown server type: xxx, skipping`
   - 检查：服务器类型必须是 `vmess`、`shadowsocks` 或 `trojan`

   **问题4：构建 URI 时出错**
   - 日志：`Error building URI for server: xxx`
   - 检查：服务器配置是否完整（host、port、cipher 等）

3. **手动检查数据库**

   ```sql
   -- 检查用户
   SELECT * FROM v2_user WHERE token = 'd5e35e8b28fbb44d566c29df7b4810d5';
   
   -- 检查 Shadowsocks 服务器
   SELECT * FROM v2_server_shadowsocks WHERE `show` = 1;
   
   -- 检查 VMess 服务器
   SELECT * FROM v2_server_vmess WHERE `show` = 1;
   
   -- 检查 Trojan 服务器
   SELECT * FROM v2_server_trojan WHERE `show` = 1;
   
   -- 检查用户组是否匹配（假设用户 group_id = 1）
   SELECT * FROM v2_server_shadowsocks WHERE `show` = 1 AND JSON_CONTAINS(group_id, '1');
   SELECT * FROM v2_server_vmess WHERE `show` = 1 AND JSON_CONTAINS(group_id, '1');
   SELECT * FROM v2_server_trojan WHERE `show` = 1 AND JSON_CONTAINS(group_id, '1');
   ```

4. **测试步骤**

   a. 确保数据库中有至少一个服务器记录
   b. 确保服务器的 `show` 字段为 1
   c. 确保用户的 `group_id` 在服务器的 `group_id` JSON 数组中
   d. 确保服务器配置完整（host、port 等字段不为空）

5. **常见问题解决**

   **问题：服务器 group_id 格式不正确**
   
   `group_id` 字段应该是 JSON 数组格式，例如：`[1, 2, 3]`
   
   如果格式不正确，可以修复：
   ```sql
   UPDATE v2_server_shadowsocks SET group_id = '[1]' WHERE group_id IS NULL OR group_id = '';
   UPDATE v2_server_vmess SET group_id = '[1]' WHERE group_id IS NULL OR group_id = '';
   UPDATE v2_server_trojan SET group_id = '[1]' WHERE group_id IS NULL OR group_id = '';
   ```

6. **启用详细日志**

   在 `application.yml` 中添加：
   ```yaml
   logging:
     level:
       com.v2board.api: DEBUG
       com.v2board.api.service: DEBUG
       com.v2board.api.controller: DEBUG
       com.v2board.api.protocol: DEBUG
   ```

