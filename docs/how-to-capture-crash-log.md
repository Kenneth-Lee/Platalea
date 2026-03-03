# 如何抓取崩溃日志

## 方法一：电脑接数据线 + adb（推荐）

1. 手机开启**开发者选项** → **USB 调试**，用数据线连电脑。
2. 电脑安装 [Android Platform Tools](https://developer.android.com/studio/releases/platform-tools)（含 `adb`），或使用 Android Studio 自带的 adb。
3. 终端执行：

```bash
adb logcat -c
# 在手机上复现问题后：
adb logcat -d > logcat.txt
```

只保存当前应用相关日志（包名 `com.kenny.localmanager`）：

```bash
adb logcat -c
# 复现问题后：
adb logcat -d --pid=$(adb shell pidof com.kenny.localmanager) > logcat.txt
```

## 方法二：Android Studio

1. 手机用 USB 连接电脑，在 Android Studio 中运行 **Run → Run 'app'** 安装 debug 版本。
2. 底部打开 **Logcat**，在过滤框输入：`package:com.kenny.localmanager`。
3. 在手机上复现崩溃或异常。
4. 在 Logcat 里查看崩溃栈；可右键复制或导出。

## 方法三：无电脑时

安装「MatLog」「Logcat Reader」等应用，授予权限后录日志，再触发崩溃，把录到的日志发出来（可只保留崩溃前后几秒）。

---

把包含 **FATAL EXCEPTION** 或相关异常的那几段日志贴给开发者，即可快速定位原因。
