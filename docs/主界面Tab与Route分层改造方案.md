# 主界面 Tab 与 Route 分层改造方案

本文讨论 `FileBrowserApp` 当前的结构问题，并给出一个增量改造方案。

目标不是推倒重写，而是在不破坏现有功能的前提下，把主界面重新收缩成“文件浏览根 + 全局编排层”，让每个 Tab 与独立功能回到自己的模块中维护状态。

本设计的起点是代码版本b56eabb60e5acba1a7247a62cac90e98ba961cd2，2026年9月10日。

---

## 1. 背景与目标

当前程序的入口链路是：

```text
MainActivity
  -> FileBrowserApp
```

`FileBrowserApp` 目前同时承担了：

- 主界面根目录与 Tab 切换；
- 文件浏览；
- 多种查看器/解释器状态；
- 压缩/解压、GPG、口令保护等独立功能；
- FTP / WebUI / 家庭网络 / Git / 播放器 / 配置等多个 Tab 的状态；
- 各类弹窗、进行中任务、跳转和副作用处理。

这导致“顶层编排”和“具体功能内部状态”混在一起，已经影响维护性。

本次改造希望恢复下面这条原则：

> **文件浏览是全应用设计的根。Tab 是可切换、可暂存状态的长期功能模块。独立功能可以临时弹出一个 Route 完成任务，结束后直接销毁状态。**

---

## 2. 当前问题判断

### 2.1 问题是存在的，而且不是视觉上的“不舒服”而已

目前 `FileBrowserApp` 内定义了大量 `remember { mutableStateOf(...) }` 状态，其中很多状态并不属于“主界面根”，而是明确属于某个 Tab 或某个独立功能。例如：

- 目录相关：`currentUri`、`fileBrowserBackStack`、`pendingList`、`dirCache`；
- 查看器相关：`htmlViewState`、`mdZipViewState`、`pdfViewState`、`epubViewState`；
- 加密相关：`gpgState`、`passViewTarget`、`passEditRequest`、`quickMaskOp`；
- 网络服务相关：`ftpPort`、`ftpPassword`、`familyNetworkUserName` 等；
- 播放器相关：`playbackState`、`playlists`、`playerShortcutAutoplayPending`；
- 配置/导入导出相关：若干 `showXxxDialog`、`pendingImport...` 状态。

这些状态被集中在同一层，会造成三个直接后果：

1. **状态所有权不清楚**：难以判断“谁拥有、谁修改、何时销毁”。
2. **功能隐式耦合**：不同 Tab 通过顶层共享变量间接通信，而不是通过明确接口。
3. **顶层难以维护**：任何功能变更都容易回到 `FileBrowserApp` 大函数里修改。

### 2.2 当前代码不是毫无结构，而是停在“过渡态”

从现有代码可以看到，工程已经出现了正确方向的雏形：

- `MainTabContentHost`
- `RecentTabRoute`
- `DirectoryTabRoute`
- `FtpTabRoute`
- `ConfigTabRoute`
- `GitShareTabRoute`
- `PlayerTabRoute`
- `BookNoteTabRoute`
- `QuickNoteTabRoute`

也就是说，**UI 分支已经开始分层，但状态所有权还没有一起迁走**。

因此这不是“必须重写”的问题，而是一个适合做**增量重构**的架构问题。

---

## 3. 改造原则

### 3.1 顶层只保留真正的 App 级职责

顶层 `FileBrowserApp` 最终应该只保留：

1. **文件浏览根上下文**
   - `rootUri`
   - 初始 `Intent` 带来的 `initialFileUri` / `initialLaunchTarget`
   - 目录根相关的最少公共信息

2. **Tab 切换与全局导航编排**
   - `currentMainTab`
   - `switchMainTab(...)`
   - 返回键高优先级决策

3. **少量必须跨 Tab 共享的全局服务**
   - `Preferences`
   - 少量单例管理器或系统服务引用

4. **明确建模的跨功能协调接口**
   - 例如“请求切到播放器并播放某个列表”
   - 例如“请求发起目录拣选并在完成后返回某个 Tab”

### 3.2 Tab 是长期存在的功能模块

Tab 应被视为一个**可切换但不必立刻销毁**的功能域：

- 切到别的 Tab 后，可短暂保留自己的状态；
- 恢复回来时，用户感知上仍在原来的工作上下文；
- 但它的状态应由 **Tab 自己的 Route / Controller / State** 持有，而不是由顶层散装保管。

### 3.3 独立功能用临时 Route 表达

某些功能不是“常驻 Tab”，而是一次性完成的工作流，例如：

- 选择文件后进行查看；
- 临时打开压缩包阅读器；
- 发起一次 GPG 解密/加密；
- 打开某个导入/导出向导；
- 弹出待处理列表、批量操作面板。

这类流程更适合建模成：

> **临时 Route / Feature Route**

特点是：

- 进入时创建状态；
- 进行中自洽地完成内部流程；
- 结束后直接销毁，不要求长期保留；
- 若要调用其他功能，应通过接口发起，而不是直接操作别的 Tab 的内部状态。

### 3.4 跨模块调用只走“命令/接口”，不共享细节状态

理想交互方式：

```text
DirectoryTab 发出请求
  -> AppShell 负责路由
    -> PlayerTab / FamilyNetworkTab / 某个临时 Route 处理
```

而不是：

```text
DirectoryTab 直接修改多个顶层变量
  -> 顶层 LaunchedEffect 接力
    -> 另一个 Tab 再去消费
```

后者能工作，但会形成难以维护的隐式协议。

---

## 4. 目标架构

建议把 `FileBrowserApp` 一层的角色明确为 **AppShell**。不一定现在就重命名，但设计上应这样理解。

```text
MainActivity
  -> FileBrowserApp / AppShell
      ├── AppState（少量全局状态）
      ├── AppNavigator / AppCommandDispatcher
      ├── MainTabContentHost
      │    ├── DirectoryTabRoute
      │    ├── PlayerTabRoute
      │    ├── FamilyNetworkTabRoute
      │    ├── ConfigTabRoute
      │    └── ...
      └── TransientRouteHost
           ├── ViewerRoute
           ├── ArchiveRoute
           ├── GpgRoute
           ├── PendingListRoute
           └── ...
```

### 4.1 AppShell 负责什么

- 保存当前激活的 `MainTab`
- 保存根目录和启动参数
- 维护少量全局服务引用
- 响应跨模块命令并决定切 Tab 或弹临时 Route
- 处理顶层返回键优先级

### 4.2 TabRoute 负责什么

每个 TabRoute 负责：

- 自己的 UI 状态；
- 自己的副作用（`LaunchedEffect`、收集 Flow、局部 Toast/对话框）；
- 自己对配置的读写封装；
- 向外暴露尽量少的回调接口。

### 4.3 TransientRoute 负责什么

每个临时 Route 负责：

- 一个短生命周期流程的完整状态；
- 进入时所需参数；
- 完成/取消后的返回结果；
- 自身退出时清理状态。

---

## 5. 状态所有权规则

后续拆分时，建议所有状态都强制回答一句话：

> **如果这个状态消失了，用户会认为是哪个功能“丢了上下文”？**

回答属于谁，状态就应该归谁。

### 5.1 应留在顶层的状态

- `currentMainTab`
- `rootUri`
- `initialFileUri`
- `initialLaunchTarget`
- 目录根级别的少量公共导航能力
- 全局返回策略 / 顶层弹层优先级
- 已文档化的跨 Tab 会话（如 `activeDirectoryPick` 这类真正的全局工作流）

### 5.2 应归属于 Directory 根域的状态

因为“文件浏览是整个设计的根”，以下状态允许保留在目录根域，但应尽量聚成专门的 `DirectoryRootState`，而不是散落在总函数里：

- `currentUri`
- `initialDirUri`
- `fileBrowserBackStack`
- `refreshTrigger`
- `dirCache`
- `pendingList`
- 与目录拣选模式相关的全局会话状态（见 `docs/目录拣选模式.md`）

这里的关键不是“都要移出主界面”，而是：

> **它们属于目录根域，不应与播放器、家庭网络、GPG、压缩查看器等状态混放。**

### 5.3 应归属于各 Tab 的状态

示例：

- Player：`playbackState`、`playlists`、`playerShortcutAutoplayPending`
- Config：配置页展示状态、配置编辑状态、导入导出向导入口
- FamilyNetwork：家庭网络显示状态、参数编辑状态、留言板会话入口
- FTP / WebUI：服务端口、密码、服务开关、运行中信息
- QuickNote / BookNote：控制器与编辑会话

### 5.4 应归属于临时 Route 的状态

示例：

- Viewer：`viewingFile`、`markdownViewFile`、`htmlViewState`、`pdfViewState`
- Archive：`zipUnzipTarget`、`zipCompressTarget`、`mdZipTarget`、`htmlZipTarget`、`epubTarget`
- GPG：`gpgState`、`gpgPassword`、`gpgDecryptMode`
- PassProtect：`passProtectTarget`、`passViewTarget`、`passEditRequest`
- PendingList：`showPendingList`、`showPendingDeleteConfirm`

它们的共同特征是：**工作流结束后没有必要在 App 顶层继续记住这些状态。**

---

## 6. 建议的模块边界

### 6.1 顶层保留一个统一的命令面

建议在 AppShell 内抽出一组明确动作，而不是任由各功能直接改顶层变量：

```kotlin
interface AppNavigator {
    fun switchMainTab(tab: MainTab)
    fun startDirectoryPick(request: DirectoryPickRequest)
    fun openViewer(request: ViewerRequest)
    fun openArchiveViewer(request: ArchiveViewerRequest)
    fun openGpgFlow(request: GpgFlowRequest)
    fun requestExitApp()
}
```

不要求一开始就做成真正接口类型，但设计上要把“顶层允许外部做什么”收敛成这类动作。

### 6.2 Tab 对外暴露“请求”，不暴露内部状态

例如 DirectoryTab 不应该知道播放器内部如何维护播放状态，只需要知道：

- “创建目录播放列表并切到播放器”
- “发起留言板附件拣选并切到家庭网络”
- “请求打开某个查看器”

这样 Tab 之间就通过 AppShell 协作，而不是彼此穿透。

### 6.3 临时 Route 通过 Host 管理生命周期

建议顶层维护一个显式的临时 Route Host，例如：

```text
transientRoute: AppTransientRoute?
```

其中可能的路由有：

- `Viewer(...)`
- `ArchiveViewer(...)`
- `Gpg(...)`
- `PassView(...)`
- `PendingList(...)`

显示时创建，关闭时设为 `null`。这会比几十个分散布尔值和目标对象更清楚。

---

## 7. 分阶段改造方案

本次不建议一次性大拆，而建议分四期做。

### Phase 1：先收敛概念，不改行为

目标：把“状态属于谁”先写清楚、命名清楚。

建议动作：

1. 在 `FileBrowserApp.kt` 内先按域给状态分组并加注释：
   - AppShell
   - DirectoryRoot
   - PlayerTab
   - ConfigTab
   - FamilyNetworkTab
   - ViewerRoute
   - ArchiveRoute
   - GpgRoute
   - PendingListRoute
2. 把明显是同一功能的一组 `remember` + `LaunchedEffect` 连在一起。
3. 抽出统一命令函数，减少到处直接写 `currentMainTab = ...` 或多点修改状态。

这一期结束标准：

- 功能不变；
- 但阅读代码时已经能看出各状态的归属域。

### Phase 2：先拆“最容易独立”的 Tab 与 Route

建议优先拆：

1. `FamilyNetwork`
2. `Config`
3. `Player`
4. `Viewer / Archive / GPG` 这类临时 Route

原因：

- 它们本身已经有 `Route` / `Manager` / `Controller` 雏形；
- 对目录根的耦合相对较低；
- 拆出来后会显著减轻顶层复杂度。

这一期结束标准：

- 顶层不再直接保存这几个域的大部分内部状态；
- 对应副作用一起迁走；
- 顶层只保留参数传递与路由切换。

### Phase 3：为临时功能建立统一 `TransientRouteHost`

把散落的“打开某某弹层/查看器/流程”的状态，整合成一个或少数几个显式 Route：

- `ViewerRoute`
- `ArchiveRoute`
- `CryptoRoute`
- `PendingListRoute`

这一期的重点是：

- 不再使用大量 `showXxx + targetXxx + passwordXxx + inProgressXxx` 的散装变量；
- 而是把它们收成一个自洽状态对象。

### Phase 4：最后再处理 DirectoryRoot 的进一步纯化

目录根是全应用设计的根，因此不建议最先大拆。

这一期要做的不是“把目录功能赶出主界面”，而是把它变成：

- 一个明确命名的 `DirectoryRootState` / `DirectoryRootController`
- 保留在主界面编排层中
- 但与其他 Tab/Route 的状态彻底分开

这一步完成后，`FileBrowserApp` 就可以接近 AppShell 的角色：

- 顶层编排清楚；
- 目录根域清楚；
- 其他功能域清楚。

---

## 8. 与现有目录拣选设计的关系

这次改造与 `docs/目录拣选模式.md` 不冲突，反而是一致的。

`目录拣选模式` 已经明确了一条正确路线：

- 目录是根域；
- 某些跨功能任务可以借目录完成一次临时工作流；
- 发起方只发请求，不直接接管目录内部状态；
- 完成后返回原来的 Tab。

这正是本方案希望推广到其他功能上的基本模式。

---

## 9. 本方案的非目标

为了避免过度设计，本次改造 **不以以下事项为目标**：

1. 不要求一次性引入完整 Navigation 框架；
2. 不要求所有状态都改成 `ViewModel`；
3. 不要求把 `FileBrowserApp.kt` 一次拆成很多文件；
4. 不要求彻底消灭所有顶层状态；
5. 不要求先动目录根再动别的模块。

真正目标只有一个：

> **把状态所有权理顺，让顶层回到编排层，把各功能的内部自洽性还给各自模块。**

---

## 10. 建议的首轮执行清单

当本设计文档达成一致后，建议首轮实际改造按下面顺序进行：

1. 先在 `FileBrowserApp.kt` 中补充按域分组的注释与小型封装函数；
2. 抽出统一的 App 级命令入口（先函数，后接口也可）；
3. 把 `FamilyNetwork` 与 `Config` 的状态/副作用进一步迁出顶层；
4. 建一个最小版 `TransientRouteHost`，先容纳 1~2 个独立流程（建议从查看器或压缩查看器开始）；
5. 再评估 `Player` 与 `PendingList` 是否继续外迁；
6. 最后清理目录根域和其他域之间残留的耦合。

---

## 11. 一句话结论

`FileBrowserApp` 当前的问题，核心不是“Tab 还没拆”，而是：

> **Tab 和独立功能的状态所有权没有拆干净，导致顶层函数变成了全应用状态中枢。**

这件事是可以救的，而且非常适合做增量重构：

- 文件浏览继续作为设计的根；
- Tab 作为长期功能域各自持有状态；
- 独立功能通过临时 Route 自洽完成；
- 顶层只负责全局编排和少量共享能力。

这就是后续重构应坚持的主线。

