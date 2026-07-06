# 修复记录

## 2026-07-06

### 1. 修复Windows上platalea stop命令错误

**文件:** `pc_tools/platalea/daemon.py`

**问题:**
- Windows上不支持`os.kill(pid, 0)`来检查进程是否存在
- 导致`platalea stop`命令报错：`OSError: [WinError 87] 参数错误`

**修复:**
- 在`is_process_alive()`函数中添加Windows平台特殊处理
- 使用Windows API (`kernel32.OpenProcess` 和 `GetExitCodeProcess`) 检查进程状态
- Unix/Linux/Mac平台继续使用`os.kill(pid, 0)`

**测试:**
```bash
cd pc_tools
platalea stop    # 正常停止
platalea status  # 显示已停止
platalea start   # 正常启动
```

### 2. 修复pip install -e .版本号错误

**文件:** `pc_tools/VERSION`

**问题:**
- 文件内容为`../VERSION`，是相对路径引用
- 导致setuptools无法正确读取版本号
- 错误：`packaging.version.InvalidVersion: Invalid version: '../VERSION'`

**修复:**
- 将内容改为实际版本号`0.21`
- 与根目录VERSION文件内容保持一致

**测试:**
```bash
cd pc_tools
pip install -e .    # 正常安装
python -c "from platalea import __version__; print(__version__)"  # 输出: 0.21
```

### 3. 密码验证问题（已确认是客户端版本问题）

**问题描述:**
- 旧版Android客户端在访问留言板时密码未正确发送到服务器
- 服务器收到空密码header

**根本原因:**
- Android客户端版本过旧，密码发送逻辑存在bug

**解决方案:**
- 升级Android客户端到最新版本
- 无需修改服务端代码

**已清理的调试代码:**
- 删除了临时添加的密码验证DEBUG日志
- 恢复bulletin_server.py为原始代码