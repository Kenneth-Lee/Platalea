# LocalManager mDNS 原型

这个目录提供一个跨平台的 Python 原型，用来验证两件事：

1. 当前机器能否在局域网中注册一个 LocalManager 的 mDNS 服务。
2. 当前机器能否枚举并解析局域网中已有的 mDNS 服务，包括它自己刚注册的服务。

## 设计范围

当前阶段只验证“发现”能力，不实现客户端协议、消息传输、鉴权和队列。

现在额外补了一个最小调试消息链路：

1. `mdns_server.py` 支持接收 `POST /message` 并打印消息。
2. `send_message.py` 可以从 PC 主动向任意 LocalManager 调试服务发送一条消息。

脚本会做两件事：
1. `mdns_server.py` 通过 HTTPS 接收 `POST /message` 并打印消息。
2. `send_message.py` 可以从 PC 主动向任意 LocalManager 调试服务发送一条 HTTPS 消息。
3. `generate_tls_materials.sh` 用来生成私有 CA、PC 服务端证书，以及 Android 侧使用的开发身份文件。
2. 用 zeroconf 注册 `_localmanager._tcp.local.`，并持续扫描局域网内的 mDNS 服务类型和实例。

## 安装
1. 启动一个最小 HTTPS 服务，默认监听 8765 端口。
```bash
python3 -m venv .venv
source .venv/bin/activate
pip install -r server/requirements.txt
```

Windows PowerShell 可改为：

```powershell
python -m venv .venv
.venv\Scripts\Activate.ps1
pip install -r server/requirements.txt
```

## 运行

```bash
python3 server/mdns_server.py --port 8765
```

如果你刚执行过 `server/generate_tls_materials.sh` 重新生成了证书，务必先重启正在运行的 `mdns_server.py`。
否则旧进程仍会继续使用旧证书，PC 侧和 Android 侧都会出现 TLS 校验失败或握手异常。

如果只想做一次短时间验证：

```bash
python3 server/mdns_server.py --port 8765 --run-seconds 15 --log-level DEBUG
python3 server/send_message.py 192.168.3.74 "你好，这是一条来自 PC 的调试消息"

## 发送测试消息

向手机或另一台 PC 的调试服务发送消息：

```bash
python3 server/send_message.py 192.168.3.74 "你好，这是一条来自 PC 的调试消息"
python3 server/send_message.py kllt03.local "发给当前 PC 服务自身的测试消息"
```

注意：`send_message.py` 的第一个参数是目标主机，不是 mDNS 服务实例名。
如果你要从 PC 发给手机，请传手机的 IP 地址，例如 `192.168.3.74`；像 `kllt03.local` 这样的地址通常表示当前 PC 自己。

如果你已经从 mDNS 日志里拿到了对端证书指纹，可以一并传入，脚本会额外做 SHA-256 指纹校验：

```bash
python3 server/send_message.py 192.168.3.74 "带指纹验证的消息" \
	--port 8876 \
	--tls-fingerprint af85ad8071ef77e071546bf2e29a48825879109f37563d5b4b8a025f92693a1b
```
```

指定 HTTPS 超时秒数，默认 5 秒。

`--ca-cert`
指定私有 CA 根证书路径，默认 `server/tls/ca_cert.pem`。

`--tls-fingerprint`
指定对端 HTTPS 证书的 SHA-256 指纹；传入后会额外做证书固定校验。

```bash
1. `HTTPS 服务已监听`：本机已经开始监听加密消息接口。
2. `mDNS 服务已注册`：本机已经开始广播服务。
3. `开始监听服务类型`：脚本已经能从局域网拿到 mDNS 服务类型。
4. `发现服务` 或 `更新服务`：脚本已经解析到具体实例，通常会包含名称、端口、地址和 TXT 属性。

如果你想区分不同发送方，可以指定名字和实例 ID：

```bash
python3 server/send_message.py 192.168.3.74 "带身份的消息" \
	--sender-name kllt03-debug \
	--sender-instance-id pc-test-001
```

## 常用参数

`--service-name`
指定实例名，默认使用当前主机名。

`--hostname`
指定注册到 mDNS 的主机名。传裸主机名时会自动补成 `.local.`。

`--service-type`
指定服务类型，默认 `_localmanager._tcp.local.`。

`--type-scan-interval`
重新扫描服务类型的间隔，默认 15 秒。

`--discovery-timeout-ms`
解析具体服务详情的超时，默认 2000 毫秒。

`--run-seconds`
运行固定秒数后自动退出，便于脚本化验证。

`send_message.py` 还有这些常用参数：

`--sender-name`
指定发送方名称，默认 `pc-debug`。

`--sender-instance-id`
指定发送方实例 ID；不传时会自动生成一个临时 UUID。

`--sender-platform`
指定平台标识，默认 `python`。

`--timeout`
指定 HTTP 超时秒数，默认 5 秒。

## 结果判断

看到以下三类日志，说明最小闭环成立：

1. `mDNS 服务已注册`：本机已经开始广播服务。
2. `开始监听服务类型`：脚本已经能从局域网拿到 mDNS 服务类型。
3. `发现服务` 或 `更新服务`：脚本已经解析到具体实例，通常会包含名称、端口、地址和 TXT 属性。

如果一直看不到任何服务，优先检查：

1. 当前网络是否允许组播和 mDNS。
2. 本机防火墙是否阻止 UDP 5353。
3. 当前机器是否真的拿到了非回环地址。
4. 局域网里是否确实存在支持 mDNS 的设备或服务。