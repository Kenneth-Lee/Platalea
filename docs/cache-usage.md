# LocalManager 缓存使用说明

本文档总结当前版本中所有主要缓存的落盘位置、用途和清理策略，便于排查空间占用与数据残留风险。

## 1. 缓存根目录

- Android 应用缓存根目录：`context.cacheDir`
- 典型物理路径：`/data/user/0/com.kenny.localmanager/cache/`

说明：下文提到的相对路径，均相对于 `context.cacheDir`。

## 2. 配置里的缓存管理（三类）

缓存管理入口当前只展示三类（与代码一致）：

1. 普通浏览缓存
2. 加密图片缓存
3. 其他临时缓存

### 2.1 普通浏览缓存

包含目录：

- `md_zip_cache/`
- `html_zip_cache/`
- `epub_cache/`
- `pic_zip_cache/`（仅非加密子目录）

用途：

- 非加密压缩包的解压浏览缓存（`.md.zip/.rst.zip/.html.zip/.pic.zip`）
- 阅读缓存（`.epub/.txt/.llm/.llm.zip` 的解析结果）

典型子文件：

- `content/`：解压或生成后的可读内容
- `.cache_ts`：缓存时间戳
- `.image_list`：图片列表（仅 `pic_zip_cache`）
- `.last_index`：图片阅读位置（仅 `pic_zip_cache`）
- `.txt_source` / `.llm_source`：来源标记（`epub_cache`）

清理策略：

- 可在“缓存管理 -> 普通浏览缓存”手动清理。
- 删除后会在下次打开时自动重新生成。

### 2.2 加密图片缓存

包含目录：

- `pic_zip_cache/` 下带 `.encrypted` 标记的子目录

用途：

- 加密 `.pic.zip` 的解压缓存（为大图场景保留“可选择保留缓存”的能力）

典型子文件：

- `.encrypted`：标记该子缓存来自加密包
- `content/`、`.image_list`、`.last_index`

清理策略：

- 可在“缓存管理 -> 加密图片缓存”手动清理。
- 退出加密 `.pic.zip` 阅读时，用户可选择删除或保留。

### 2.3 其他临时缓存

用途：

- 除前两类之外，所有落在 `cacheDir` 的临时文件/目录
- 主要是过程型临时文件与异常中断残留

典型前缀：

- `zip_unzip_*`
- `zip_compress_*`
- `7z_compress_*`
- `zip_check_*`
- `zip_list_*`
- `pic_zip_verify_*`
- `rar_ver_*`
- `stardict_import_*`
- `git_repo_*`（位于 cacheDir，但逻辑上是 Git 本地工作副本）

清理策略：

- 可在“缓存管理 -> 其他临时缓存”统一清理。
- 大部分会在流程结束后自动删除；若崩溃/中断可能残留。

## 3. 各缓存目录详细说明

### 3.1 `md_zip_cache/<key>/`

用途：`.md.zip/.rst.zip` 解压后的渲染缓存。

关键文件：

- `content/`
- `.cache_ts`
- `.encrypted`（仅加密来源）

生命周期：

- 非加密：可长期复用，进入“普通浏览缓存”。
- 加密：阅读退出时会删除，不进入可保留集合。

### 3.2 `html_zip_cache/<key>/`

用途：`.html.zip` 解压后的网页浏览缓存。

关键文件：

- `content/`
- `.cache_ts`
- `.encrypted`（仅加密来源）

生命周期：

- 非加密：进入“普通浏览缓存”。
- 加密：退出即删。

### 3.3 `pic_zip_cache/<key>/`

用途：`.pic.zip` 图片包解压缓存。

关键文件：

- `content/`
- `.image_list`
- `.last_index`
- `.cache_ts`
- `.encrypted`（仅加密来源）

生命周期：

- 非加密：进入“普通浏览缓存”。
- 加密：进入“加密图片缓存”，可选择保留。

### 3.4 `epub_cache/<key>/`

用途：统一阅读缓存目录，承载：

- `.epub`
- `.txt`（转换为 EPUB 结构）
- `.llm`（转换为 EPUB 结构）
- `.llm.zip`（解压并多章节转换）

处理方式（统一“书本文件”管线）：

- 上述格式都会先转换/解压到统一兼容缓存结构（EPUB 结构）
- 若源内容不是 HTML（如 txt/llm），会先在缓存中生成 HTML 章节
- 之后统一由 EPUB 渲染器按同一结构渲染、保存进度和收藏

关键文件：

- `content/`
- `.cache_ts`
- `.encrypted`（仅加密来源）
- `.txt_source` / `.llm_source`
- `raw/`（`.llm.zip` 转换中间目录）

加密阅读附加缓存（仅加密场景）：

- `.epub_bookmarks.json`：收藏夹（缓存域）
- `.epub_progress.json`：阅读进度（缓存域）

生命周期：

- 非加密：进入“普通浏览缓存”。
- 加密：退出即删（包括上面的收藏和进度缓存）。

## 4. 安全相关说明

### 4.1 加密文件的缓存策略

- 除加密 `.pic.zip` 外，其余加密压缩阅读内容退出时都应删除缓存。
- 加密 EPUB 阅读相关收藏与进度已迁移到缓存域，不再长期持久化。

### 4.2 不属于磁盘缓存的“内存缓存”

- `SecretKeyPasswordCache`：仅进程内存保存私钥密码，不落盘，不参与导出。

## 5. 与缓存容易混淆的持久化数据

以下数据不在 `cacheDir`，属于持久化配置/数据（通常位于 `filesDir` 或 DataStore）：

- 应用配置（DataStore）
- 播放列表、播放书签、播放恢复状态（DataStore）
- 根目录书签（`filesDir/root_bookmarks.json`）
- GPG 密钥文件（`filesDir` 下密钥目录）

## 6. 维护建议

1. 新增任何落盘临时目录时，优先统一到 `cacheDir` 的明确前缀目录。
2. 新增加密内容缓存时，默认采用“退出即删”，除非有明确的性能例外。
3. 若新增缓存需要进入配置里的“缓存管理”，同步更新 `CacheManagement.kt` 与本文档。
