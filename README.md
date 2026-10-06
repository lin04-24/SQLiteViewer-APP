# SQLite Viewer

一个完全离线运行的 Android SQLite 数据库查看器，面向手机和平板设备。应用通过系统文件选择器、系统“打开方式”或分享接收 `.db` 文件，以只读方式打开并浏览数据库结构、表数据和 SQL 查询结果。

当前版本：`v1.1`
最低系统：Android 8.0（API 26）  
技术栈：Kotlin、Jetpack Compose、Material 3、Navigation Compose

## 功能重点

- 使用 Storage Access Framework 打开本地 SQLite 文件，支持 `ACTION_OPEN_DOCUMENT`、`ACTION_VIEW` 和 `ACTION_SEND`。
- 持久化文件 URI 权限，最近文件记录只保存 URI、名称和访问时间，不上传数据库内容。
- 对内容提供方以流式方式复制到应用缓存目录，再使用 Android 原生 SQLite 只读连接打开；关闭会话时释放连接并删除临时文件。
- 浏览表、视图、索引和触发器，查看列类型、NULL 约束、默认值、主键、索引字段、唯一性和 DDL。
- 数据表采用虚拟化网格，仅加载当前页；默认每页 100 行，可翻页查看百万级数据。
- 数据表滚动接近当前页底部时在后台预加载下一页，切换到已加载页面可直接从 LRU 缓存显示。
- 页缓存最多保留前一页、当前页和后一页；切换表、关闭数据库、刷新或修改页大小时自动失效。
- 对有 INTEGER PRIMARY KEY 或可用 `rowid` 的表，首屏后使用 Keyset Pagination（`WHERE id > lastId`）进行前后翻页，避免大 OFFSET 扫描；无合适身份列的表自动回退到 LIMIT/OFFSET。
- Keyset 模式支持连续前进和后退；“最后一页”在该模式下不可用，切换到尾页时自动使用 OFFSET。
- 对整数主键或 `rowid` 使用稳定排序；BLOB 默认显示字节数，避免把大块二进制内容直接渲染到表格。
- 支持单元格详情、复制单元格、列宽自适应、横向滚动，以及当前页 CSV/JSON 导出。
- SQL 编辑器支持只读 `SELECT`、`WITH`/CTE 和受控 `PRAGMA` 查询，结果限制为最多展示 5,000 行，并显示耗时和截断状态。
- 查询历史可复用、复制或清空；查询和数据库 IO 在 `Dispatchers.IO` 执行。
- 深色主题、响应式布局、动态字体和无障碍语义；平板与横屏使用对象列表和内容区域的双栏布局。
- 设置页面支持自动更新检查开关、手动检查、版本信息和 GitHub 项目入口；更新检查默认关闭。
- 应用内更新检查仅访问 GitHub Releases API，通过 HTTPS 获取版本号、更新日志和 APK 下载地址，不上传用户数据。

## 只读保护

数据库通过 Android `SQLiteDatabase.OPEN_READONLY` 打开，并在会话初始化时启用并验证 `PRAGMA query_only=ON`，同时设置忙等待超时。

SQL 执行前会进行语句分类和校验：

- 默认只允许一条语句。
- 拒绝 `INSERT`、`UPDATE`、`DELETE`、`REPLACE`、`CREATE`、`DROP`、`ALTER`、`VACUUM`、`REINDEX`、事务控制语句、`ATTACH`/`DETACH` 和扩展加载。
- 只开放用于读取的 `PRAGMA` 白名单。
- 查询结果通过外层 `LIMIT` 限制展示规模，避免无限制结果集占用内存。

## 工程结构

```text
app/src/main/java/com/example/dbviewer/
├── data/          SQLite 会话、仓储、最近文件
├── presentation/  ViewModel 和页面状态
└── ui/             Compose 页面、主题和可复用组件
```

主要页面包括最近文件、数据库浏览、表结构、表数据、SQL 查询、查询历史和单元格详情。

## 构建与运行

环境要求：Android Studio、JDK 17、Android SDK 35。项目使用 Gradle Wrapper：

```powershell
./gradlew.bat testDebugUnitTest
./gradlew.bat assembleDebug
```

生成的 Debug APK：

```text
app/build/outputs/apk/debug/SQLiteViewer.apk
```

生成 v1.1 Release APK：

```powershell
./gradlew.bat :app:assembleRelease
```

产物路径：

```text
app/build/outputs/apk/release/SQLiteViewer.apk
```

该 APK 使用项目的 release 构建配置，启用 R8 资源压缩，并包含 ProfileInstaller 与 Baseline Profile 集成。

### 签名配置

**正式发布**需要配置签名密钥：

1. 在项目根目录创建 `keystore.properties`（参考 `keystore.properties.example`）
2. 配置密钥库路径、密码、别名等信息
3. 执行 `./gradlew assembleRelease` 将使用正式签名

详细配置步骤见 [SIGNING.md](SIGNING.md)。

如果未配置 `keystore.properties`，Release 构建会自动使用 debug 签名（仅用于测试分发）。安装前请确认已卸载旧的同包名版本，或使用相同签名的构建进行覆盖安装。

## 测试覆盖

当前单元测试覆盖：

- 只读 SQL 防护关键字和 `query_only` 配置。
- 数量、耗时、字节数、对象类型和视觉宽度格式化。
- CSV/JSON 转义、NULL、数字识别和导出文件名处理。

建议在真实设备或模拟器上继续验证 SAF 权限、分享打开、旋转恢复、损坏数据库、权限错误、加密数据库、磁盘空间不足和大规模数据库滚动性能。

## 性能优化

首屏和滚动过程只保留当前数据页及其 UI 虚拟化内容，BLOB 延迟为长度摘要，查询在后台线程串行执行。列宽计算在后台协程中完成，并限制为最多 50 行样本；网格行使用稳定 content type，单元格绘制使用缓存。针对有 INTEGER PRIMARY KEY 或 `rowid` 的表启用 Keyset Pagination；无主键表、复合主键表和尾页跳转保留 LIMIT/OFFSET 回退。v1.1 增加最多 3 页的 LRU 页面缓存和滚动预加载，减少连续翻页等待。

v1.0 包含 Baseline Profile 和 Macrobenchmark 配置，覆盖应用启动、历史浏览、打开数据库、表切换、数据滚动、查询和导出等热路径。性能测试模块位于 [`benchmark/`](benchmark/)，详细命令、设备要求和结果说明见 [`benchmark/README.md`](benchmark/README.md)。Baseline Profile 需要连接 Android 设备或模拟器生成，Release 构建会通过 `profileinstaller` 安装已生成的 Profile。实际提升取决于设备、SQLite 数据规模和缓存状态，建议用真实数据库进行基准测试。

基准测试与 Profile 生成：

```powershell
# 连接 API 28+ 设备后生成 Profile
./gradlew.bat :benchmark:generateBaselineProfile

# 运行启动或表切换 Macrobenchmark
./gradlew.bat :benchmark:connectedCheck -Pandroid.testInstrumentationRunnerArguments.class=com.example.sqliteviewer.benchmark.StartupBenchmark
./gradlew.bat :benchmark:connectedCheck -Pandroid.testInstrumentationRunnerArguments.class=com.example.sqliteviewer.benchmark.TableSwitchBenchmark
```

## v1.1 发布

正式 APK 和校验文件发布在 GitHub Releases：
<https://github.com/lin04-24/SQLiteViewer-APP/releases/tag/v1.1>

v1.1 包含以下发布内容：

- DataGrid 列宽计算异步化，并将样本限制为最多 50 行。
- LazyColumn 行 content type、单元格绘制缓存和滚动位置监听。
- 页面 LRU 缓存与下一页后台预加载，分页栏显示预加载状态。
- Release APK 版本号为 `versionCode 5`、`versionName 1.1`。
- APK 发布资产包含 `SQLiteViewer.apk` 和 `SHA256SUMS` 校验文件。

## v1.0 发布

正式 APK 和校验文件发布在 GitHub Releases：
<https://github.com/lin04-24/SQLiteViewer-APP/releases/tag/v1.0>

v1.0 包含以下发布内容：

- 应用内 GitHub Releases 更新检查，支持首次启动引导和设置页控制。
- 正式 Release 构建使用 RSA 2048 发布签名，并启用 R8 资源压缩。
- APK 发布资产包含 `SQLiteViewer.apk` 和 `SHA256SUMS` 校验文件。

## 隐私

应用默认不发起网络请求，不上传数据库或崩溃日志。数据库副本仅在应用缓存目录中用于本地读取，关闭数据库会话后清理。

**更新检查功能（可选）**：v1.0 提供应用内更新检查功能，默认禁用。用户可在设置中启用自动更新检查，启用后应用仅在启动时向 GitHub Releases API (api.github.com) 发起单次 HTTPS 请求以检查版本更新，不上传任何用户数据、数据库内容或使用信息。该功能完全可选，用户可随时在设置中禁用。

## 许可证

项目当前未附带单独许可证文件。使用、分发和二次开发前请先确认仓库维护者的许可范围。
