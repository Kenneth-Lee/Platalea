# LocalManager PC / mDNS 服务端

本目录提供 PC 端家庭网络留言板服务，以及与 Android 客户端兼容的 mDNS 发现、HTTPS API 和 TLS 证书工具。

## 目录说明

| 文件 | 作用 |
|------|------|
| `bulletin_server.py` | **主服务**：留言板 HTTPS API + mDNS 广播 |
| `bulletin_store.py` | 留言板 JSON 存储（与 Android 格式兼容） |
| `board_client.py` | 留言板 API 命令行客户端（本机/远程调试，见下文） |
| `family_common.py` | TLS / mDNS 共用工具 |
| `config.example.json` | 配置示例 |
| `mdns_server.py` | 早期 mDNS 原型（调试用，可保留） |
| `send_message.py` | 早期 `/message` 调试脚本（已过时） |
| `generate_tls_materials.sh` | 生成私有 CA、PC 证书、Android 资产 |

## 快速开始（双机测试）

### 1. 依赖

```bash
python3 -m venv server/.venv
source server/.venv/bin/activate
pip install -r server/requirements.txt
```

### 2. TLS 证书

若尚未生成，或 Android 端 TLS 握手失败：

```bash
bash server/generate_tls_materials.sh
```

脚本会把 CA 同步到 `app/src/main/assets/family_tls/`，**生成后需重新编译 Android APK**。

### 3. 配置

```bash
cp server/config.example.json server/config.json
# 编辑 guest_password / host_password / board_root 等
```

配置项说明：

| 字段 | 说明 |
|------|------|
| `port` | HTTPS 监听端口，默认 8765 |
| `board_root` | 留言板数据目录（相对配置文件路径） |
| `guest_password` | 普通接入密码：可读留言板、发消息（Android 连入时填此密码） |
| `host_password` | 宿主密码：还可创建/删除留言板，修改/删除留言 |
| `service_name` | mDNS 实例名，留空则用 `LocalManager-<主机名>` |
| `hostname` | mDNS 主机名，留空则用系统主机名 |

留空 `guest_password` 与 `host_password` 表示免密接入（不推荐在真实网络中使用）。

### AI Agent（可选，V1）

在 `config.json` 中配置 `agent` 段并设 `enabled: true`。Agent 随 `bulletin_server.py` 在后台运行：**发帖时若含 `@model_name` 会立即触发**（不再轮询留言板）。

| 字段 | 说明 |
|------|------|
| `enabled` | 是否启用 Agent |
| `board_ids` | 生效的留言板 ID 列表；`null` 或省略表示**全部留言板** |
| `models` | Ollama 模型名数组，每个模型均可通过 `@模型名` 触发（如 `gpt-oss:latest`） |
| `model_name` | （已废弃，兼容旧配置）单个模型名；请改用 `models` |
| `ollama_base_url` | Ollama API 地址，默认 `http://127.0.0.1:11434` |

协议扩展：

- `GET /agent`：返回 `{ enabled, models, board_ids }`（访客可读）
- `GET /boards/{id}/messages` 响应增加 `agents`（可 @ 的模型名）与 `participants`（板上发过言的用户名）

手机端输入 `@` 时会弹出上述列表供选择。

配置示例：

```json
"agent": {
  "enabled": true,
  "board_ids": null,
  "models": ["qwen2.5", "gpt-oss:latest"],
  "ollama_base_url": "http://127.0.0.1:11434"
}
```

测试步骤：

1. 本机安装并启动 Ollama，拉取 `models` 中列出的模型
2. 在 `config.json` 中设 `agent.enabled: true` 与 `models`
3. 启动 `bulletin_server.py`
4. 在留言板用 @ 菜单或手写 `@模型名 问题` 发帖（不同模型 @ 不同名字）
5. Agent 应异步回复；失败时会发一条含错误原因的回复

#### Agent 工具（V1）

Agent 可通过 Ollama 工具调用（tool calling）使用以下能力（需模型支持，如 `qwen2.5`、`llama3.1` 等）：

| 工具 | 说明 |
|------|------|
| `list_attachment_files` | 列出留言板附件及其中文件（可传 `attachment_id` 查单个） |
| `read_attachment_file` | 读取附件内文本文件（`.txt`/`.md`/`.json` 等） |
| `web_fetch` | 获取 http/https URL 的文本内容 |

配置段 `agent.tools`：

| 字段 | 说明 |
|------|------|
| `enabled` | 是否启用工具，默认 `true` |
| `attachments` | 附件列表/读取，默认 `true` |
| `web_fetch` | 网络访问，默认 `true` |
| `max_attachment_read_bytes` | 单次读取附件最大字节，默认 100000 |
| `max_web_fetch_bytes` | 单次网页抓取最大字节，默认 200000 |
| `web_fetch_timeout_seconds` | 网络超时秒数，默认 30 |
| `max_tool_rounds` | 单轮对话最多工具调用轮数，默认 10 |

测试附件总结：

1. 在留言板发帖并上传 `.txt` / `.md` 附件
2. `@模型名 请总结附件内容`
3. Agent 应先调用 `list_attachment_files` / `read_attachment_file`，再回复摘要

`GET /agent` 响应增加 `tools: { enabled, attachments, web_fetch }`。

### 4. 启动服务

```bash
python3 server/bulletin_server.py --config server/config.json
```

日志中应出现：

- `HTTPS 服务已监听`
- `mDNS 服务已注册`
- `fingerprint=...`（Android 调试时可核对）

### 5. Android 手机测试

1. PC 与手机在同一局域网
2. 手机打开「家庭网络」，应发现 PC 服务（带锁图标表示需要密码）
3. 点击进入，输入 **`guest_password`**（不是手机全局配置里的本机密码）
4. 进入默认留言板，可查看与发送消息

> 手机端使用 **guest_password** 可读写留言；使用 **host_password** 连入后界面显示「远程宿主」，可修改/删除 PC 留言板上的消息。

## PC 命令行调试（board_client.py）

本机启动 `bulletin_server.py` 后，可用 `board_client.py` 通过 HTTPS API 完成留言板的查看与管理，无需手机。

### 典型流程

1. `list-boards` — 列出所有留言板，记下 **board_id**（如 `default`、`kitchen`）
2. `get-messages <board_id>` — 查看该板消息（输出含 message id，供改删用）
3. `post <board_id> "内容"` — 发帖；可 `@模型名` 触发 Agent
4. 需要管理时：`create-board`、`delete-board`、`put`、`delete`（需 host 密码）

### 全局选项

| 选项 | 说明 |
|------|------|
| `--host` | 目标主机，默认 `127.0.0.1`（本机服务） |
| `--port` | HTTPS 端口；未指定时从 `--config` 读取，否则 8765 |
| `--config` | 读取 `config.json` 中的 `port`、`guest_password`、`host_password` |
| `--password` | guest 密码（读板、发帖）；未指定时从 config 读取 |
| `--host-password` | host 密码（创建/删除板、改删消息）；未指定时从 config 读取 |
| `--ca-cert` | TLS CA 证书，默认 `server/tls/ca_cert.pem` |
| `--tls-fingerprint` | 可选，服务端证书 SHA-256 指纹固定 |
| `--json` | 输出原始 JSON；默认为人可读格式 |

### 子命令

| 命令 | 参数 | 权限 | 对应 API |
|------|------|------|----------|
| `list-boards` | — | guest | `GET /boards` |
| `get-agent` | — | guest | `GET /agent` |
| `get-messages` | `<board_id>` | guest | `GET /boards/{id}/messages` |
| `post` | `<board_id> <content>` | guest | `POST /boards/{id}/messages` |
| `create-board` | `<name>` | host | `POST /boards` |
| `delete-board` | `<board_id>` | host | `DELETE /boards/{id}` |
| `put` | `<board_id> <message_id> <content>` | host | `PUT /boards/{id}/messages/{msgId}` |
| `delete` | `<board_id> <message_id>` | host | `DELETE /boards/{id}/messages/{msgId}` |

`post` 可选 `--author <名称>` 指定显示名，默认 `pc-cli`。

**凡操作具体留言板，必须在命令中显式传入 `board_id`**（第一个 positional 参数），不再使用隐藏的默认板。

### 示例

```bash
# 使用 config.json（推荐本机调试）
python3 server/board_client.py --config server/config.json list-boards
python3 server/board_client.py --config server/config.json get-messages default
python3 server/board_client.py --config server/config.json get-agent
python3 server/board_client.py --config server/config.json post default "@qwen2.5 总结一下"
python3 server/board_client.py --config server/config.json post default "普通留言" --author kenny
python3 server/board_client.py --config server/config.json create-board "厨房留言"
python3 server/board_client.py --config server/config.json delete-board kitchen

# 本机、手动指定密码（不读 config）
python3 server/board_client.py list-boards --password guest
python3 server/board_client.py get-messages default --password guest

# 连接局域网内另一台 PC
python3 server/board_client.py --host 192.168.1.100 --password guest list-boards
python3 server/board_client.py --host 192.168.1.100 --password guest get-messages default

# 改/删消息（host 密码）
python3 server/board_client.py --config server/config.json \
  put default <message_id> "新内容"
python3 server/board_client.py --config server/config.json \
  delete default <message_id>

# 原始 JSON（脚本集成）
python3 server/board_client.py list-boards --password guest --json
```

`get-messages` 的人可读输出会列出每条消息的 `id=`，复制后用于 `put` / `delete`。

可选 `--tls-fingerprint <sha256>` 做证书指纹固定（与 mDNS TXT `tls_fp_sha256` 一致）。

## HTTPS API（与 Android 一致）

认证头：`X-Network-Service-Password`

| 方法 | 路径 | 权限 |
|------|------|------|
| GET | `/agent` | guest / host |
| GET | `/boards` | guest / host |
| POST | `/boards` | host（创建留言板） |
| DELETE | `/boards/{id}` | host（删除留言板） |
| GET | `/boards/{id}/messages` | guest / host |
| POST | `/boards/{id}/messages` | guest / host |
| PUT | `/boards/{id}/messages/{msgId}` | host |
| DELETE | `/boards/{id}/messages/{msgId}` | host |

mDNS TXT 属性：`app`, `proto=https`, `version=0.2`, `instance_id`, `platform`, `tls=1`, `tls_fp_sha256`, 有密码时 `auth=1`。

## 存储格式

```
board_root/
  default/
    meta.json       # id, name, revision
    messages.json   # 消息数组
```

与 Android `filesDir/family_boards/` 结构相同，便于日后互拷数据。

## 旧原型

`mdns_server.py` / `send_message.py` 为早期 mDNS 与 `/message` 调试链路，已被 `bulletin_server.py` 取代。若只需验证 mDNS 发现，仍可使用旧脚本。

## 常见问题

1. **手机看不到 PC**：检查防火墙是否放行 TCP `port` 与 UDP 5353；确认同一网段。
2. **TLS 失败**：重新运行 `generate_tls_materials.sh` 并重启 PC 服务、重装 Android APK。
3. **401 密码错误**：连 PC 时填 `config.json` 里的 `guest_password`，不是手机「网络服务密码」。
4. **改证书后连不上**：重启 `bulletin_server.py`，旧进程仍会用旧证书。
