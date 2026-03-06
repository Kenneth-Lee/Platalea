# Git 同步功能设计文档

## 一、功能概述

改进现有 Git 配置界面，使其成为一个完整的 Git 仓库管理中心，支持配置应用、进度显示、本地仓库管理和公钥分享功能。

## 二、现有架构

- **GitHelper.kt**: 提供 `cloneToTree()` 函数，支持 clone/pull 到 `.sysgit` 目录
- **GitConfigDialog.kt**: 简单的配置输入对话框，仅保存配置
- **Preferences.kt**: 存储 `gitRepoUrl`, `gitUserName`, `gitUserEmail`, `gitHttpsPassword`
- **GpgKeys.kt**: 提供公钥/私钥的加载、保存、导入、导出功能

## 三、详细设计

### 3.1 配置状态管理

新增 Preferences 字段：
```kotlin
// 配置是否已成功同步（上次应用配置后 clone/pull 是否成功）
val gitConfigApplied: Flow<Boolean>  // 默认 false
suspend fun setGitConfigApplied(applied: Boolean)
```

状态逻辑：
- 配置变更时 → `gitConfigApplied = false`
- clone/pull 成功时 → `gitConfigApplied = true`
- 关闭对话框时，若 `gitConfigApplied == false` 且仓库地址非空 → 显示警告标记

### 3.2 GitConfigDialog 界面改造

```
┌─────────────────────────────────────────────────┐
│ Git 配置                                         │
│ 克隆/同步到根目录 .sysgit，仅支持 HTTPS           │
├─────────────────────────────────────────────────┤
│ 仓库地址    [_________________________]          │
│ HTTPS 密码  [_________________________]          │
│ Git 用户名  [_________________________]          │
│ Git 邮箱    [_________________________]          │
├─────────────────────────────────────────────────┤
│ 状态: ✓ 已同步 / ⚠ 配置未应用 / ✗ 同步失败       │
│                                                  │
│ [进度条 - 仅同步时显示]                           │
│ 正在克隆... / 正在拉取... / 正在写入...           │
├─────────────────────────────────────────────────┤
│ [应用配置]  [删除本地仓库]  [公钥分享]  [关闭]    │
└─────────────────────────────────────────────────┘
```

**按钮功能：**

1. **应用配置**: 立即执行 clone/pull，不关闭对话框
2. **删除本地仓库**: 弹出确认对话框，确认后删除 `.sysgit` 目录和缓存
3. **公钥分享**: 打开公钥分享子界面
4. **关闭**: 关闭对话框（若已应用则无需重新同步）

### 3.3 同步流程改进

```kotlin
// GitHelper.kt 新增带进度回调的函数
fun cloneToTreeWithProgress(
    context: Context,
    treeRootUri: String,
    repoUrl: String,
    userName: String?,
    userEmail: String?,
    httpsPassword: String?,
    onProgress: (stage: String, percent: Int?) -> Unit  // 阶段描述 + 可选百分比
): Result<String>
```

进度阶段：
1. "正在连接..." (0%)
2. "正在克隆..." 或 "正在拉取..." (10-70%)
3. "正在写入 .sysgit..." (70-100%)

### 3.4 公钥分享界面

```
┌─────────────────────────────────────────────────┐
│ ← 公钥分享                                       │
├─────────────────────────────────────────────────┤
│ [同步中...] 或 [同步成功 ✓]                      │
├─────────────────────────────────────────────────┤
│ 远程公钥 (.sysgit/pubkey/)                       │
│ ┌─────────────────────────────────────────────┐ │
│ │ ☐ user1@example.com (ABCD1234)   [导入本地] │ │
│ │ ☐ user2@example.com (EFGH5678)   [导入本地] │ │
│ │                                   [删除选中] │ │
│ └─────────────────────────────────────────────┘ │
├─────────────────────────────────────────────────┤
│ 本地公钥                                         │
│ ┌─────────────────────────────────────────────┐ │
│ │ ☐ mykey@local (IJKL9012)        [导出到远程]│ │
│ │ ☐ friend@other (MNOP3456)       [导出到远程]│ │
│ └─────────────────────────────────────────────┘ │
├─────────────────────────────────────────────────┤
│                                        [关闭]   │
└─────────────────────────────────────────────────┘
```

**公钥分享流程：**

1. 打开界面时自动同步 `.sysgit`（pull 最新）
2. 读取 `.sysgit/pubkey/` 目录下的 `.asc` 文件作为远程公钥
3. 读取本地 `pubring.gpg` 作为本地公钥
4. 用户可：
   - 将远程公钥导入本地（调用 `mergePublicKeyRing`）
   - 将本地公钥导出到远程（写入 `.sysgit/pubkey/{keyId}.asc`）
   - 删除远程公钥
5. 任何修改后立即执行 `git add . && git commit && git push`

### 3.5 GitHelper.kt 新增函数

```kotlin
// 删除本地缓存的 git 仓库
fun deleteLocalGitCache(context: Context, repoUrl: String): Boolean

// 删除文档树中的 .sysgit 目录
fun deleteSysgitFromTree(context: Context, treeRootUri: String): Boolean

// 提交并推送变更
fun commitAndPush(
    context: Context,
    treeRootUri: String,
    repoUrl: String,
    commitMessage: String,
    userName: String?,
    httpsPassword: String?,
    onProgress: ((String) -> Unit)?
): Result<String>

// 读取 .sysgit/pubkey/ 下的公钥列表
fun listRemotePubkeys(context: Context, treeRootUri: String): List<PubkeyInfo>

// 导出本地公钥到 .sysgit/pubkey/
fun exportPubkeyToSysgit(
    context: Context,
    treeRootUri: String,
    keyId: Long,
    armoredBytes: ByteArray
): Boolean

// 删除 .sysgit/pubkey/ 中的公钥文件
fun deleteRemotePubkey(context: Context, treeRootUri: String, filename: String): Boolean
```

## 四、数据流

```
用户操作                    状态变更                    Git 操作
─────────────────────────────────────────────────────────────────
修改配置字段         →   gitConfigApplied=false      (无)
点击「应用配置」     →   显示进度条                  → clone/pull
clone/pull 成功      →   gitConfigApplied=true       (无)
clone/pull 失败      →   显示错误，保持 false        (无)
点击「关闭」         →   检查 gitConfigApplied       (无需操作若已 true)
点击「删除本地仓库」 →   确认后删除缓存+.sysgit      (无)
公钥分享操作         →   修改后自动 commit+push      → add/commit/push
```

## 五、错误处理

| 场景 | 处理方式 |
|------|----------|
| 网络错误 | 显示错误信息，保持 `gitConfigApplied=false`，用户可重试 |
| 认证失败 | 提示检查密码/令牌 |
| 仓库不存在 | 提示检查仓库地址 |
| 本地冲突 | 提示用户通过「删除本地仓库」解决 |
| 推送冲突 | 提示先同步再操作 |

## 六、实现顺序

1. **Preferences 扩展**: 添加 `gitConfigApplied` 字段
2. **GitHelper 扩展**: 添加进度回调、删除缓存、commit/push 等函数
3. **GitConfigDialog 改造**: 添加「应用」「删除本地仓库」按钮和状态显示
4. **公钥分享界面**: 新增 `PubkeyShareScreen` composable
5. **集成测试**: 验证完整流程
