# LocalManager PC 工具 (platalea)

本目录是 **LocalManager PC 端本地工具**（命令 `platalea`）：家庭网络留言板 HTTPS 服务、API 客户端，以及与 Android 兼容的快速混淆（`.qx`）工具。未来配合手机端使用的 PC 侧能力也会集中在此。

## 安装与使用（推荐：`platalea`）

```bash
cd pc_tools
pip install .          # 或 pip install -e . 开发模式
platalea config init   # 写入 ~/.localmanager/config.json
platalea start         # 后台守护进程，日志 ~/.localmanager/server.log
platalea stop          # 停止服务
```

安装后统一使用 **`platalea`** 命令。配置与数据目录（Mac / Linux / Windows 相同）：

| 路径 | 内容 |
|------|------|
| `~/.localmanager/config.json` | 服务配置 |
| `~/.localmanager/boards/` | 留言板数据 |
| `~/.localmanager/tls/` | TLS 证书 |
| `~/.localmanager/server.log` | 后台模式日志 |

**留言板 API 子命令**（`--board` 指定留言板，默认读 config.json 的 `default_board`；连接本机时若服务未运行会自动后台启动）：

```bash
platalea list-boards
platalea get-agent
platalea get-messages
platalea post "Hello"
platalea post --attach ./report.pdf
platalea post "说明" --attach ./report.pdf --board kitchen
platalea modify <message_id> "更新后的正文"
platalea delete <message_id>
platalea download-attachment <attachment_id> -o ./downloads/
platalea create-board "厨房留言"    # 需 admin 密码
platalea delete-board --board kitchen
platalea export-boardpack ./kitchen.boardpack --board kitchen
platalea import-boardpack ./kitchen.boardpack --name "厨房留言(导入)"
```

**本地文件工具**（与 Android「快速混淆」`.qx` 格式兼容，原地修改文件头）：

```bash
platalea file obfuscate secret.txt -p mypass
platalea file deobfuscate secret.txt.qx -p mypass
```

**GPG**（需本机安装 `gpg`；密钥默认放在 `~/.localmanager/gnupg/pubring.gpg` 与 `secring.gpg`，可从手机导出）：

```bash
platalea gpg list-keys
platalea gpg pass-encrypt notes.md -r RECIPIENT
platalea gpg pass-decrypt notes.md.pass -p KEYPASS
platalea gpg quick-encrypt "hello" -r RECIPIENT
platalea gpg quick-decrypt "$BASE64" -p KEYPASS
```

**配置导入**（Android 设置 → 导出配置，或根目录 `local_manager_config.json`）：

```bash
platalea config import ~/Downloads/local_manager_config.json --list
platalea config import ~/Downloads/local_manager_config.json
platalea config import mobile.json --categories gpg,git --skip-keys
```

默认仅导入 PC 侧有直接用途的分类（`gpg,git`）。如需导入其它分类（如 `music/recent/epub/other`）可显式传 `--categories`。

**系统服务控制**（Phase 1：先落地 macOS `launchd` 骨架；其它平台后续补齐）：

```bash
platalea service status
platalea service install
platalea service uninstall
```

这组命令的设计目标是为无头主机提供“当前用户 owner + 单机单实例 + 后装覆盖前装 + 可卸载”的系统控制面。当前版本已提供 CLI、状态文件与 macOS `launchctl` 接入（需在 macOS 上以 sudo 执行 install/uninstall）；远程关机的特权 broker 链路后续继续接入。

**电源控制（通过本机 broker）**：

```bash
platalea power status
platalea power shutdown --yes
```

写入 `~/.localmanager/gnupg/`（密钥）、`~/.localmanager/imported/`（Git/播放列表等存档）、`config.json` 的 `imported_from_mobile`（家庭网络显示名等）。

连接远程设备时指定 `--host`，不会自动启动本机服务：

```bash
platalea --host 192.168.1.10 list-boards
```

版本号来自仓库根目录的 **`VERSION`** 文件（Android 与 `platalea` 各自在构建/打包时读取，互不读对方源码）。

其它管理命令：`platalea stop`、`platalea status`、`platalea help`（或 `platalea help board|gpg|file|service|power` 查看分组说明）。

### Shell 自动补全（bash / zsh）

安装后执行一次（或写入 `~/.bashrc` / `~/.zshrc`）：

```bash
# 开发目录（pip install -e .）
source /path/to/local_manager/pc_tools/platalea-completion.sh

# pip 安装到虚拟环境时
source "$(dirname "$(dirname "$(command -v platalea)")")/share/platalea/platalea-completion.sh"
```

可选：指定非默认数据目录

```bash
export PLATALEA_HOME=/path/to/.localmanager
source .../platalea-completion.sh
```

补全内容：子命令（含 `gpg`/`file`/`config` 分组）、服务/API 选项、本地留言板 ID（来自 `boards/`）、`modify`/`delete` 的消息 ID、附件/配置文件路径。

脚本会在 source 时检测 shell 类型：bash 用 `complete`，zsh 用原生 `compdef`（不再依赖 `mapfile` / `bashcompinit`）。非交互式 shell 会跳过并提示写入 rc 文件。

---

当前运行时代码使用 **roles** 角色配置。下列设计文档描述相关协议与能力：

| 文档 | 内容 |
|------|------|
| [docs/家庭网络留言板角色与权限.md](../docs/家庭网络留言板角色与权限.md) | 多角色密码、一板多 `role_ids`、Agent 按角色、按模型上下文预算 |
| [docs/家庭网络留言板导入导出.md](../docs/家庭网络留言板导入导出.md) | `.boardpack` 包、本机↔服务器导入、上传到服务器 |
| [docs/家庭网络共享策略.md](../docs/家庭网络共享策略.md) | 总览与 AI 助手 |

## 目录说明

| 路径 | 作用 |
|------|------|
| `platalea/` | **pip 包**：CLI、服务、存储、Agent、文件工具 |
| `pyproject.toml` | pip 安装入口（包名 `platalea`，命令 `platalea`） |
| `config.example.json` | 配置示例（`config init` 会基于此生成用户目录配置） |
| `generate_tls_materials.sh` | 生成私有 CA、PC 证书、Android 资产 |

## 快速开始（双机测试）

### 1. 依赖

需要 **Python 3.9+**（考虑现在macOS 自带的版本是 3.9.6 ，以这个版本为基准起点）

```bash
python3 -m venv pc_tools/.venv
source pc_tools/.venv/bin/activate
pip install -r pc_tools/requirements.txt
```

### 2. TLS 证书

若尚未生成，或 Android 端 TLS 握手失败：

```bash
bash pc_tools/generate_tls_materials.sh
```

脚本会把 CA 同步到 `app/src/main/assets/family_tls/`，**生成后需重新编译 Android APK**。

### 3. 配置

```bash
cp pc_tools/config.example.json pc_tools/config.json
# 编辑 roles / board_root 等
```

配置项说明：

| 字段 | 说明 |
|------|------|
| `port` | HTTPS 监听端口，默认 8765 |
| `board_root` | 留言板数据目录（相对配置文件路径） |
| `roles` | 多角色配置（须含 `admin`）；见 [角色与权限文档](../docs/家庭网络留言板角色与权限.md) |
| `service_name` | mDNS 实例名，留空则用 `LocalManager-<主机名>` |
| `hostname` | mDNS 主机名，留空则用系统主机名 |

### AI Agent（可选，V1）

在 `config.json` 中配置 `agent` 段并设 `enabled: true`。Agent 随 `platalea start` 启动的服务在后台运行：**发帖时若含 `@model_name` 会立即触发**（不再轮询留言板）。

| 字段 | 说明 |
|------|------|
| `enabled` | 是否启用 Agent |
| `board_ids` | 生效的留言板 ID 列表；`null` 或省略表示**全部留言板** |
| `models` | Ollama 模型名数组，每个模型均可通过 `@模型名` 触发（如 `gpt-oss:latest`） |
| `model_name` | 单个模型名字符串；**多模型请用 `models`**。也兼容把 `model_name` 写成数组（不推荐） |
| `ollama_base_url` | Ollama API 地址，默认 `http://127.0.0.1:11434` |
| `max_board_context_chars` | **过渡项**：留言板上下文最大字符数，默认 12000；未来将改为每模型 `context_chars`（见角色文档） |

发给 LLM 的上下文不含 `ai_status` 状态行；过长时从最新留言往回取、整条不拆（见策略文档 §2.3）。

协议扩展：

- `GET /agent`：返回 `{ enabled, models, commands, board_ids }`（访客可读）
- `GET /boards/{id}/messages` 响应增加 `agents`、`participants`、`commands`（可 @ 的模型、内部 / 命令）

手机端输入 `@` 或 `/` 时会弹出上述列表供选择。

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
3. 启动 `platalea start`
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

#### AI 状态与内部命令

长时间处理（如 `web_fetch`）时，Agent 会写入 **`ai_status` 消息**（内容以 `/ai status` 开头），在留言板以单独样式显示，**不计入留言数、不参与对话上下文、不导出 Markdown**。完成后自动清除状态行。

用户内部命令（**不产生普通留言**）：

| 命令 | 作用 |
|------|------|
| `/ai status` | 查询当前 Agent 是否在忙及进度 |
| `/ai stop` / `/stopai` | 请求停止进行中的任务 |

`GET /agent` 响应增加 `tools: { enabled, attachments, web_fetch }`。

### 4. 启动服务

```bash
python3 pc_tools/bulletin_server.py --config pc_tools/config.json
```

日志中应出现：

- `HTTPS 服务已监听`
- `mDNS 服务已注册`
- `fingerprint=...`（Android 调试时可核对）

### 5. Android 手机测试

1. PC 与手机在同一局域网
2. 手机打开「家庭网络」，应发现 PC 服务（带锁图标表示需要密码）
3. 点击进入，输入 **管理员密码**（即 `config.json` 里的 `roles.admin.password`，不是手机全局配置里的本机密码）
4. 进入默认留言板，可查看与发送消息

> 手机端使用 **user 角色密码** 可读写授权留言板；使用 **admin 密码** 连入后界面显示「远程管理员」，可管理 PC 留言板。

## PC 命令行调试（board_client.py）

本机启动 `platalea start` 后，可用 `platalea` 通过 HTTPS API 完成留言板的查看与管理，无需手机。

### 典型流程

1. `list-boards` — 列出所有留言板，记下 **board_id**（如 `default`、`kitchen`）
2. `get-messages` — 查看默认板消息（`--board kitchen` 指定其它板；输出含 message id，供改删用）
3. `post "内容"` — 发帖；可 `@模型名` 触发 Agent；`--attach` 可重复上传附件
4. 需要管理时：`create-board`、`delete-board`、`modify`、`delete`（需 host 密码）

### 全局选项

| 选项 | 说明 |
|------|------|
| `--host` | 目标主机，默认 `127.0.0.1`（本机服务） |
| `--port` | HTTPS 端口；未指定时从 `--config` 读取，否则 8765 |
| `--board` | 留言板 ID；未指定时读 config.json 的 `default_board`（默认 `default`） |
| `--config` | 读取 `config.json` 中的 `port`、`roles.admin.password`、`default_board` |
| `--password` | 接入密码；未指定时从 config 读取 `roles.admin.password` |
| `--ca-cert` | TLS CA 证书，默认 `pc_tools/tls/ca_cert.pem` |
| `--tls-fingerprint` | 可选，服务端证书 SHA-256 指纹固定 |
| `--json` | 输出原始 JSON；默认为人可读格式 |

### 子命令

| 命令 | 参数 | 权限 | 对应 API |
|------|------|------|----------|
| `list-boards` | — | guest | `GET /boards` |
| `get-agent` | — | guest | `GET /agent` |
| `get-messages` | — | guest | `GET /boards/{id}/messages` |
| `post` | `[content]` | guest | `POST /boards/{id}/messages` |
| `create-board` | `<name>` | host | `POST /boards` |
| `delete-board` | — | host | `DELETE /boards/{id}` |
| `modify` | `[content]` | host | `PUT /boards/{id}/messages/{msgId}` |
| `delete` | `<message_id>` | host | `DELETE /boards/{id}/messages/{msgId}` |
| `download-attachment` | `<attachment_id>` | guest | `GET /boards/{id}/attachments/{attId}/blob` |
| `export-boardpack` | `<output.boardpack>` | guest+可见 | `GET /boards/{id}/export.boardpack` |
| `import-boardpack` | `<input.boardpack>` | host | `POST /boards/import` |

`post` 可选 `--author <名称>` 指定显示名，默认 `pc-cli`；`--attach PATH` 可重复（正文与附件至少一项，仅附件时可省略正文）。

`modify` 无 `--attach` 时仅修改正文；带 `--attach` 时上传新附件并**替换**消息上的原有附件（正文可选，可只换附件）。

`download-attachment` 用 `get-messages` 输出中的 `attachment_id`；目录附件可用 `--file REL_PATH` 只取其中一个文件，或 `-o 目录/` 整包解压。

非默认留言板一律用 **`--board`** 指定，不再把 `board_id` 写在 positional 参数里。

### 示例

```bash
# 使用 config.json（推荐本机调试）
platalea --config pc_tools/config.json list-boards
platalea --config pc_tools/config.json get-messages
platalea --config pc_tools/config.json get-agent
platalea --config pc_tools/config.json post "@qwen2.5 总结一下"
platalea --config pc_tools/config.json post "普通留言" --author kenny
platalea --config pc_tools/config.json post --attach ./report.pdf
platalea --config pc_tools/config.json create-board "厨房留言"
platalea --config pc_tools/config.json delete-board --board kitchen

# 本机、手动指定密码（不读 config）
platalea list-boards --password guest
platalea get-messages --password guest

# 连接局域网内另一台 PC
platalea --host 192.168.1.100 --password guest list-boards
platalea --host 192.168.1.100 --password guest get-messages --board default

# 改/删消息（host 密码）
platalea --config pc_tools/config.json modify <message_id> "新内容"
platalea --config pc_tools/config.json modify <message_id> --attach ./new.pdf
platalea --config pc_tools/config.json delete <message_id>

# 下载附件
platalea download-attachment <attachment_id> -o ./saved.bin
platalea download-attachment <attachment_id> -o ./out/ --file docs/readme.md

# 原始 JSON（脚本集成）
platalea list-boards --password guest --json
```

`get-messages` 的人可读输出会列出每条消息的 `id=` 与 `attachment_id=`，复制后用于 `modify` / `delete` / `download-attachment`。

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
| GET | `/boards/{id}/export.boardpack` | 可见角色（ZIP 归档） |
| POST | `/boards/import` | admin（body 为 `.boardpack` ZIP） |

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
4. **改证书后连不上**：重启 `platalea start` 启动的服务，旧进程仍会用旧证书。
