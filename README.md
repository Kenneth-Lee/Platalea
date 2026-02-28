# Local Manager

这是一个 Android 应用程序，主要用作 Android 手机的信息管理。它首先呈现为一个文件浏览器，可以查看手机上有哪些文件，并支持**打开、重命名、移动、拷贝、删除**文件。另外，它支持 **GnuPG 兼容**的加密和解密（对称加密）。内置简单的**文本**和**十六进制**查看功能，可快速以文本或十六进制方式查看文件内容。未来还会增加更多功能。

## 功能概览

- **文件浏览**：通过“选择根目录”授权后，以树形方式浏览目录、进入子目录、返回上级。
- **文件操作**：点击文件用内置查看器打开；长按文件可重命名；拷贝/移动/删除可通过后续版本或菜单扩展。
- **内置查看器**：支持文本视图与十六进制视图切换；大文件仅预览前 512KB。
- **GnuPG**：对 `.gpg` 文件尝试解密后查看（对称加密需在应用内输入密码，当前版本解密失败时会提示“需要密码”）。

## 开发与构建

### 环境要求

- **JDK 17+**（推荐 21）
- **Android SDK**（含 Platform 34、Build-Tools 34.0.0）
- **Gradle**（已安装时可直接用 `gradle`；否则使用项目自带的 `./gradlew`，需能下载 Gradle 发行包）

### 必须完成的配置

1. **设置 Android SDK 路径**（若未设置）  
   例如：
   ```bash
   export ANDROID_HOME=$HOME/Android/Sdk
   export PATH=$PATH:$ANDROID_HOME/platform-tools
   ```
2. **接受 SDK 许可**（未接受时构建会报 “licences have not been accepted”）  
   ```bash
   sdkmanager --licenses
   ```
   按提示输入 `y` 接受全部许可。

### 构建与安装

```bash
# 使用系统 Gradle（你已安装时）
gradle assembleDebug

# 或使用项目 Wrapper（会下载 Gradle，需网络）
./gradlew assembleDebug
```

安装到已连接设备或模拟器：

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 若构建失败

- **“Could not determine the dependencies” / “licences have not been accepted”**  
  执行 `sdkmanager --licenses` 并接受所有许可后重试。
- **“sdkmanager: command not found”**  
  安装 Android 命令行工具（如通过 Android Studio SDK Manager 安装 “Android SDK Command-line Tools”），或确保 `$ANDROID_HOME/cmdline-tools/latest/bin` 在 `PATH` 中。
- **Gradle 下载慢或超时**  
  可配置代理或使用国内镜像（如修改 `gradle/wrapper/gradle-wrapper.properties` 中的 `distributionUrl` 为阿里云等镜像），或使用已安装的系统 `gradle` 进行构建。
