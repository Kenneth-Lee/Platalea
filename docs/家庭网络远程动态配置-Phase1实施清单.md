# 家庭网络远程动态配置：Phase 1 最小实施清单

## 1. 目标与范围

### 1.1 本阶段目标

在不改动底层监听与证书机制的前提下，先打通“手机可远程管理 PC 角色配置”的闭环，并保证多手机并发编辑时可控。

### 1.2 范围内

1. 服务端新增最小配置接口（读、改、变更订阅）。
2. 角色（roles）远程增删改（保留 admin 不可删除约束）。
3. 客户端草稿隔离、revision 冲突处理、增量刷新。
4. 审计与最小回滚能力（文件级备份）。

### 1.3 范围外

1. 动态修改 `listen_host/port/cert_file/key_file`。
2. SSE/WebSocket 推送（本期使用长轮询）。
3. 复杂三方合并 UI（本期先做“重放草稿 + 选择性覆盖”）。

## 2. 服务端最小接口

基于现有服务入口 [pc_tools/platalea/bulletin_server.py](pc_tools/platalea/bulletin_server.py) 增量扩展。

### 2.1 GET /system/config

1. 权限：admin。
2. 返回：脱敏配置快照 + `revision` + `updated_at`。
3. 脱敏规则：只返回 `password_set`，不返回明文密码。

示例响应：

```json
{
  "ok": true,
  "revision": 7,
  "updated_at": 1760000000000,
  "domains": {
    "auth": {
      "roles": {
        "admin": {
          "label": "管理员",
          "can_create_boards": true,
          "can_manage_boards": true,
          "password_set": true
        },
        "guest": {
          "label": "客人",
          "can_create_boards": false,
          "can_manage_boards": false,
          "password_set": true
        }
      }
    }
  }
}
```

### 2.2 PATCH /system/config

1. 权限：admin。
2. 请求头：`If-Match-Revision`（必填）。
3. 本期仅支持 `domains.auth.roles` 更新。
4. 成功后 `revision += 1`。

冲突响应：

```json
{
  "ok": false,
  "error": "revision_conflict",
  "server_revision": 8,
  "changed_paths": ["auth.roles.family"],
  "server_snapshot": {"...": "..."}
}
```

### 2.3 GET /system/config/changes

1. 权限：admin。
2. 参数：`since`、`timeout_ms`。
3. 语义：
   1. 有新版本立即返回。
   2. 无新版本则长轮询等待到超时。

示例响应：

```json
{
  "ok": true,
  "revision": 9,
  "changed_domains": ["auth"],
  "changed_paths": ["auth.roles.guest"],
  "updated_at": 1760000001234
}
```

## 3. 存储与并发模型

### 3.1 新增文件

1. `~/.localmanager/runtime_config.json`（运行时覆盖层）。
2. `~/.localmanager/runtime_config.json.bak.<ts>`（写前备份）。

### 3.2 合并策略

1. 启动时：`config.json` 为基线，`runtime_config.json` 覆盖可动态字段。
2. 运行时：内存配置对象原子替换（单锁写入）。

### 3.3 并发保证

1. 服务端单写锁保护“读 revision -> 校验 -> 写入 -> revision+1”。
2. 客户端必须携带 `If-Match-Revision`。
3. 冲突统一 409，不做隐式覆盖。

## 4. 手机端最小状态机

基于 [app/src/main/java/com/kenny/localmanager/family/FamilyNetworkManager.kt](app/src/main/java/com/kenny/localmanager/family/FamilyNetworkManager.kt) 和 [app/src/main/java/com/kenny/localmanager/ui/FamilyNetworkScreen.kt](app/src/main/java/com/kenny/localmanager/ui/FamilyNetworkScreen.kt) 新增“远程配置中心”流。

### 4.1 状态定义

1. `Idle`：未编辑，自动跟随服务器快照。
2. `Editing`：本地草稿中，记录 `draft_base_revision`。
3. `Saving`：提交中，禁重复提交但不锁死页面。
4. `Conflict`：收到 409，展示冲突信息。
5. `Synced`：保存成功并切换到新 revision。

### 4.2 关键行为

1. 编辑中收到 `/changes`：只提示“有新配置”，不覆盖输入。
2. 提交成功：清草稿，刷新为新 snapshot。
3. 提交冲突：保留草稿，进入 Conflict 选择。

### 4.3 防闪烁约束

1. 配置页采用分区状态，不整页 replace。
2. 高频变更 300 到 500ms 合并一次 UI 刷新。
3. 不得因后台刷新重置焦点、滚动、弹窗。

## 5. 任务拆分（可直接排期）

### 5.1 服务端

1. 新增 RuntimeConfigRepository（读写 runtime_config + revision）。
2. 在 HTTP 路由中新增 `/system/config`、`/system/config/changes`。
3. 增加 roles patch 校验器：
   1. 必须保留 admin。
   2. role_id 去重。
   3. 密码为空处理规则。
4. 增加审计日志：记录 actor、ip、changed_paths、revision。

### 5.2 Android 客户端

1. 新增远程配置数据模型（ConfigSnapshot、ChangeEvent、PatchRequest）。
2. 在 manager 层新增：
   1. 拉取配置快照。
   2. 提交带 revision 的 patch。
   3. 长轮询订阅变更。
3. 在 UI 新增“远程配置中心”入口与角色编辑页。
4. 实现冲突弹窗与草稿保留逻辑。

### 5.3 文档与回归

1. 更新 [docs/家庭网络远程动态配置设计.md](docs/家庭网络远程动态配置设计.md) 的实现状态。
2. 更新 [docs/家庭网络留言板角色与权限.md](docs/家庭网络留言板角色与权限.md) 的角色管理入口说明。

## 6. 验收标准（DoD）

### 6.1 功能验收

1. 手机 A 可新增/修改/删除（非 admin）远程角色。
2. 手机 B 在一个轮询周期内感知变更并可刷新。
3. 同时编辑同一角色时，一方成功后另一方收到 409 冲突并保留草稿。

### 6.2 体验验收

1. 高频改动期间，编辑框不丢焦点。
2. 页面不反复白屏或整页闪烁。
3. 后台刷新不打断用户当前操作。

### 6.3 安全验收

1. 非 admin 无法访问 `/system/config*`。
2. 配置查询接口不泄露明文密码。
3. 写入失败不破坏现有可用配置（有备份可恢复）。

## 7. 建议的实现顺序（两周节奏）

### 第 1 周

1. 服务端配置仓库 + `GET /system/config`。
2. `PATCH /system/config` + revision 冲突返回。
3. Android 读取与展示页面。

### 第 2 周

1. `GET /system/config/changes` 长轮询。
2. Android 草稿隔离 + 冲突处理 + 节流刷新。
3. 双机并发回归与文档更新。

## 8. 风险与降级策略

1. 若长轮询在部分网络环境不稳定：退化为每 5 到 10 秒轮询 `GET /system/config`。
2. 若冲突 UI 首版复杂度偏高：先提供“保留草稿并重试”最小交互，不做字段级可视化 diff。
3. 若服务端热更新路径风险高：先落盘 + 进程内重载，失败时回退到上一 revision。

## 9. 变更历史

| 日期 | 变更内容 | 原因 |
| --- | --- | --- |
| 2026-08-08 | 新增 Phase 1 最小实施清单（接口、状态机、验收、排期） | 将讨论稿转为可执行计划 |
