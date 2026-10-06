# SQLite Viewer

一个完全离线运行的 Android SQLite 数据库查看器，面向手机和平板设备。应用通过系统文件选择器、系统“打开方式”或分享接收 `.db` 文件，以只读方式打开并浏览数据库结构、表数据和 SQL 查询结果。

当前版本：`v0.3`
最低系统：Android 8.0（API 26）  
技术栈：Kotlin、Jetpack Compose、Material 3、Navigation Compose

## 功能重点

- 使用 Storage Access Framework 打开本地 SQLite 文件，支持 `ACTION_OPEN_DOCUMENT`、`ACTION_VIEW` 和 `ACTION_SEND`。
- 持久化文件 URI 权限，最近文件记录只保存 URI、名称和访问时间，不上传数据库内容。
- 对内容提供方以流式方式复制到应用缓存目录，再使用 Android 原生 SQLite 只读连接打开；关闭会话时释放连接并删除临时文件。
- 浏览表、视图、索引和触发器，查看列类型、NULL 约束、默认值、主键、索引字段、唯一性和 DDL。
- 数据表采用虚拟化网格，仅加载当前页；默认每页 100 行，可翻页查看百万级数据。
- 对有 INTEGER PRIMARY KEY 或可用 `rowid` 的表，首屏后使用 Keyset Pagination（`WHERE id > lastId`）进行前后翻页，避免大 OFFSET 扫描；无合适身份列的表自动回退到 LIMIT/OFFSET。
- Keyset 模式支持连续前进和后退；“最后一页”在该模式下不可用，切换到尾页时自动使用 OFFSET。
- 对整数主键或 `rowid` 使用稳定排序；BLOB 默认显示字节数，避免把大块二进制内容直接渲染到表格。
- 支持单元格详情、复制单元格、列宽自适应、横向滚动，以及当前页 CSV/JSON 导出。
- SQL 编辑器支持只读 `SELECT`、`WITH`/CTE 和受控 `PRAGMA` 查询，结果限制为最多展示 5,000 行，并显示耗时和截断状态。
- 查询历史可复用、复制或清空；查询和数据库 IO 在 `Dispatchers.IO` 执行。
- 深色主题、响应式布局、动态字体和无障碍语义；平板与横屏使用对象列表和内容区域的双栏布局。

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

生成 v0.3 Release APK：

```powershell
./gradlew.bat :app:assembleRelease
```

产物路径：

```text
app/build/outputs/apk/release/SQLiteViewer.apk
```

该 APK 使用项目的 release 构建配置，启用 R8 资源压缩，并包含 ProfileInstaller 与 Baseline Profile 集成。由于仓库未配置正式发布密钥，当前 v0.3 发布 APK 使用 Android debug keystore 签名，仅用于本项目的测试分发；安装前请确认已卸载旧的同包名版本，或使用相同签名的构建进行覆盖安装。

## 测试覆盖

当前单元测试覆盖：

- 只读 SQL 防护关键字和 `query_only` 配置。
- 数量、耗时、字节数、对象类型和视觉宽度格式化。
- CSV/JSON 转义、NULL、数字识别和导出文件名处理。

建议在真实设备或模拟器上继续验证 SAF 权限、分享打开、旋转恢复、损坏数据库、权限错误、加密数据库、磁盘空间不足和大规模数据库滚动性能。

## 性能优化（v0.3）

首屏和滚动过程只保留当前数据页及其 UI 虚拟化内容，BLOB 延迟为长度摘要，查询在后台线程串行执行。针对有 INTEGER PRIMARY KEY 或 `rowid` 的表启用 Keyset Pagination；无主键表、复合主键表和尾页跳转保留 LIMIT/OFFSET 回退。

v0.3 增加 Baseline Profile 和 Macrobenchmark 配置，覆盖应用启动、历史浏览、打开数据库、表切换、数据滚动、查询和导出等热路径。性能测试模块位于 [`benchmark/`](benchmark/)，详细命令、设备要求和结果说明见 [`benchmark/README.md`](benchmark/README.md)。Baseline Profile 需要连接 Android 设备或模拟器生成，Release 构建会通过 `profileinstaller` 安装已生成的 Profile。实际提升取决于设备、SQLite 数据规模和缓存状态，建议用真实数据库进行基准测试。

基准测试与 Profile 生成：

```powershell
# 连接 API 28+ 设备后生成 Profile
./gradlew.bat :benchmark:generateBaselineProfile

# 运行启动或表切换 Macrobenchmark
./gradlew.bat :benchmark:connectedCheck -Pandroid.testInstrumentationRunnerArguments.class=com.example.sqliteviewer.benchmark.StartupBenchmark
./gradlew.bat :benchmark:connectedCheck -Pandroid.testInstrumentationRunnerArguments.class=com.example.sqliteviewer.benchmark.TableSwitchBenchmark
```

## v0.3 发布

正式 APK 和校验文件发布在 GitHub Releases：
<https://github.com/lin04-24/SQLiteViewer-APP/releases/tag/v0.3>

## 隐私

应用不需要传统存储权限，不发起网络请求，不上传数据库或崩溃日志。数据库副本仅在应用缓存目录中用于本地读取，关闭数据库会话后清理。

## 许可证

项目当前未附带单独许可证文件。使用、分发和二次开发前请先确认仓库维护者的许可范围。
