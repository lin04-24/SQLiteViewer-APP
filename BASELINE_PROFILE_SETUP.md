# Baseline Profile 和 Macrobenchmark 优化配置总结

## 已完成的配置

### 1. 项目依赖配置

#### 根级 build.gradle.kts
添加了性能测试相关插件：
```kotlin
plugins {
    id("androidx.baselineprofile") version "1.3.0" apply false
    id("com.android.test") version "8.5.2" apply false
}
```

#### app/build.gradle.kts
- 添加 `androidx.baselineprofile` 插件
- 添加 `testInstrumentationRunner` 配置
- 新增 `benchmark` 构建类型（基于 release，使用 debug 签名）
- 添加 `profileinstaller` 依赖
- 配置 `baselineProfile` 依赖关系

#### settings.gradle.kts
- 添加 `benchmark` 模块引用

### 2. Benchmark 模块结构

创建了完整的 `benchmark/` 模块，包含：

#### benchmark/build.gradle.kts
- 配置 `com.android.test` 插件
- 设置目标项目为 `:app`
- 启用 self-instrumenting 实验性功能
- 添加 Macrobenchmark 和 UIAutomator 依赖

#### benchmark/src/main/AndroidManifest.xml
- 配置 `WRITE_EXTERNAL_STORAGE` 权限
- 启用 `profileable` 标记以支持性能分析

### 3. 性能测试代码

#### BaselineProfileGenerator.kt
**核心功能**：生成应用的 Baseline Profile
- 记录应用启动流程
- 记录历史浏览操作
- 记录数据库打开流程
- 记录数据表浏览和滚动
- 记录查询和导出操作
- 配置 15 次迭代，3 次稳定迭代

#### StartupBenchmark.kt
**测试场景**：应用冷启动性能
- `startupNoCompilation()` - 无编译优化基准
- `startupWithBaselineProfile()` - Baseline Profile 优化
- `startupFullCompilation()` - 完全 AOT 编译
- 使用 `StartupTimingMetric` 测量启动时间
- 10 次迭代获取稳定数据

#### TableSwitchBenchmark.kt
**测试场景**：表切换和数据渲染性能
- `tableSwitchNoCompilation()` - 无优化基准
- `tableSwitchWithBaselineProfile()` - Profile 优化
- 使用 `FrameTimingMetric` 测量帧渲染性能
- 模拟表切换和数据滚动操作

#### BenchmarkUtils.kt
**通用工具函数**：
- `waitForAppStart()` - 等待应用启动完成
- `openDatabase()` - 模拟打开数据库
- `switchTables()` - 模拟表切换
- `scrollDataGrid()` - 模拟数据滚动

### 4. ProGuard 配置

#### app/proguard-rules.pro
- 保护 ProfileInstaller 相关类不被混淆
- 确保 Baseline Profile 在 release 构建中正常工作

### 5. 文档

#### benchmark/README.md
完整的使用文档，包含：
- Baseline Profile 原理说明
- 项目配置详解
- 生成和测试命令
- 性能优化场景分析
- CI/CD 集成建议
- 验证效果方法

## 使用方法

### 生成 Baseline Profile
```bash
./gradlew :benchmark:generateBaselineProfile
```

生成的文件位置：`app/src/main/baseline-prof.txt`

### 运行性能测试
```bash
# 启动性能测试
./gradlew :benchmark:connectedCheck -P android.testInstrumentationRunnerArguments.class=com.example.sqliteviewer.benchmark.StartupBenchmark

# 表切换性能测试
./gradlew :benchmark:connectedCheck -P android.testInstrumentationRunnerArguments.class=com.example.sqliteviewer.benchmark.TableSwitchBenchmark
```

## 预期性能提升

根据 Android 官方数据和行业实践：
- **冷启动速度提升 15-30%**
- **首屏渲染时间减少**
- **表切换更流畅**
- **减少 JIT 编译带来的卡顿**

## 优化原理

Baseline Profile 通过以下方式提升性能：

1. **预编译热路径代码**
   - 应用启动时的关键代码
   - Compose UI 渲染代码
   - 数据加载和处理逻辑

2. **减少 JIT 编译开销**
   - 首次运行时已是编译后的机器码
   - 避免运行时编译导致的延迟

3. **优化代码布局**
   - 将热路径代码放在内存的连续区域
   - 提升 CPU 缓存命中率

## 注意事项

由于当前环境磁盘空间不足，建议在实际设备上完成以下步骤：

1. **同步项目**：在 Android Studio 中同步 Gradle
2. **连接设备**：连接 API 28+ 的真实设备（推荐）
3. **生成 Profile**：运行 `generateBaselineProfile` 任务
4. **提交代码**：将生成的 `baseline-prof.txt` 提交到版本控制

## 文件清单

已创建/修改的文件：
- ✅ `build.gradle.kts` - 添加插件
- ✅ `settings.gradle.kts` - 添加 benchmark 模块
- ✅ `app/build.gradle.kts` - 配置 Baseline Profile
- ✅ `app/proguard-rules.pro` - ProGuard 规则
- ✅ `benchmark/build.gradle.kts` - Benchmark 模块配置
- ✅ `benchmark/src/main/AndroidManifest.xml` - 权限配置
- ✅ `benchmark/src/main/java/.../BaselineProfileGenerator.kt` - Profile 生成器
- ✅ `benchmark/src/main/java/.../StartupBenchmark.kt` - 启动性能测试
- ✅ `benchmark/src/main/java/.../TableSwitchBenchmark.kt` - 表切换测试
- ✅ `benchmark/src/main/java/.../BenchmarkUtils.kt` - 工具函数
- ✅ `benchmark/README.md` - 完整文档

所有配置已完成，可以在实际 Android 环境中进行构建和测试。
