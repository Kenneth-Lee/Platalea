# LocalManager PC / mDNS 服务端

本目录提供 PC 端家庭网络留言板服务，以及与 Android 客户端兼容的 mDNS 发现、HTTPS API 和 TLS 证书工具。

## 目录说明

| 文件 | 作用 |
|------|------|
| `bulletin_server.py` | **主服务**：留言板 HTTPS API + mDNS 广播 |
| `bulletin_store.py` | 留言板 JSON 存储（与 Android 格式兼容） |
| `board_client.py` | 命令行客户端，便于 PC 侧测试 API |
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
| `host_password` | 宿主密码： additionally 可修改/删除留言 |
| `service_name` | mDNS 实例名，留空则用 `LocalManager-<主机名>` |
| `hostname` | mDNS 主机名，留空则用系统主机名 |

留空 `guest_password` 与 `host_password` 表示免密接入（不推荐在真实网络中使用）。

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

## PC 命令行测试

```bash
# 列出留言板（guest 密码）
python3 server/board_client.py 192.168.1.100 list-boards --password guest

# 读取消息
python3 server/board_client.py 192.168.1.100 get-messages --password guest

# 发消息
python3 server/board_client.py 192.168.1.100 post "你好 from PC" --password guest --author pc-test

# 改/删消息（host 密码）
python3 server/board_client.py 192.168.1.100 put <message_id> "新内容" --password host
python3 server/board_client.py 192.168.1.100 delete <message_id> --password host
```

可选 `--tls-fingerprint <sha256>` 做证书指纹固定（与 mDNS TXT `tls_fp_sha256` 一致）。

## HTTPS API（与 Android 一致）

认证头：`X-Network-Service-Password`

| 方法 | 路径 | 权限 |
|------|------|------|
| GET | `/boards` | guest / host |
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
