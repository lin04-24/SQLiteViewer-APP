# Baseline Profile 配置说明

## 什么是 Baseline Profile？

Baseline Profile 是 Android 的一项性能优化技术，它通过预编译应用的关键代码路径来提升启动速度和运行时性能。

### 工作原理
- 记录应用的热路径代码（启动和核心功能）
- 在安装时或首次运行时对这些代码进行 AOT（Ahead-of-Time）编译
- 减少 JIT（Just-in-Time）编译开销
- **预期提升冷启动速度 15-30%**

## 项目配置

### 1. 模块结构
```
SQLiteViewer/
├── app/                          # 主应用模块
│   ├── build.gradle.kts          # 已添加 androidx.baselineprofile 插件
│   └── src/main/baseline-prof.txt # 生成的 Baseline Profile（自动创建）
└── benchmark/                     # 性能测试模块
    ├── build.gradle.kts          # Macrobenchmark 配置
    └── src/main/java/.../benchmark/
        ├── BaselineProfileGenerator.kt  # Profile 生成器
        ├── StartupBenchmark.kt          # 启动性能测试
        ├── TableSwitchBenchmark.kt      # 表切换性能测试
        └── BenchmarkUtils.kt            # 通用工具函数
```

### 2. 依赖配置
已添加以下关键依赖：
- `androidx.baselineprofile` - Baseline Profile 插件
- `androidx.profileinstaller` - Profile 安装器
- `androidx.benchmark:benchmark-macro-junit4` - Macrobenchmark 框架

## 使用方法

### 生成 Baseline Profile

1. **连接真实设备或模拟器**（推荐使用 API 28+ 的真实设备）

2. **生成 Profile**
   ```bash
   ./gradlew :benchmark:generateBaselineProfile
   ```

3. **生成的文件**
   - 文件路径：`app/src/main/baseline-prof.txt`
   - 包含预编译的方法和类列表
   - 会自动打包到 APK 中

### 运行性能基准测试

#### 启动性能测试
```bash
# 测试无编译优化的启动性能
./gradlew :benchmark:connectedCheck -P android.testInstrumentationRunnerArguments.class=com.example.sqliteviewer.benchmark.StartupBenchmark#startupNoCompilation

# 测试 Baseline Profile 优化后的启动性能
./gradlew :benchmark:connectedCheck -P android.testInstrumentationRunnerArguments.class=com.example.sqliteviewer.benchmark.StartupBenchmark#startupWithBaselineProfile

# 测试完全编译后的启动性能
./gradlew :benchmark:connectedCheck -P android.testInstrumentationRunnerArguments.class=com.example.sqliteviewer.benchmark.StartupBenchmark#startupFullCompilation
```

#### 表切换性能测试
```bash
# 测试表切换渲染性能
./gradlew :benchmark:connectedCheck -P android.testInstrumentationRunnerArguments.class=com.example.sqliteviewer.benchmark.TableSwitchBenchmark
```

### 查看测试结果

测试结果会输出到：
```
benchmark/build/outputs/connected_android_test_additional_output/
```

结果包含：
- 启动时间指标（毫秒）
- 帧渲染时间
- 性能对比数据

## 性能测试场景

### 1. StartupBenchmark
测试三种编译模式下的冷启动性能：
- **None**：无编译优化（JIT）
- **Baseline Profile**：预编译热路径
- **Full**：完全 AOT 编译

### 2. TableSwitchBenchmark
测试表切换场景的渲染性能：
- 模拟用户切换数据库表
- 测量帧渲染时间
- 验证 UI 流畅度

### 3. BaselineProfileGenerator
记录关键用户流程：
- 应用启动
- 浏览历史记录
- 打开数据库
- 浏览数据表
- 切换到查询界面
- 导出操作

## 注意事项

### 设备要求
- **推荐**：API 28+ 的真实物理设备
- **避免**：模拟器可能产生不准确的结果
- **确保**：设备充电并关闭省电模式

### Release 构建
应用的 `build.gradle.kts` 已配置 `benchmark` 构建类型：
```kotlin
buildTypes {
    create("benchmark") {
        initWith(buildTypes.getByName("release"))
        signingConfig = signingConfigs.getByName("debug")
        matchingFallbacks += listOf("release")
    }
}
```

### CI/CD 集成
可以将 Baseline Profile 生成集成到发布流程：
```bash
# 在发布前生成 Profile
./gradlew :benchmark:generateBaselineProfile

# 提交生成的 baseline-prof.txt
git add app/src/main/baseline-prof.txt
git commit -m "Update Baseline Profile"
```

## 性能优化优先级

根据优化目标，Baseline Profile 在以下场景最有效：

### 高优先级场景
✅ **应用启动** - 减少首屏加载时间  
✅ **表切换** - 预编译 Compose 重组代码  
✅ **数据渲染** - 优化 LazyColumn 绘制路径

### 中优先级场景
⚡ **查询执行** - 数据库操作主要受 I/O 限制  
⚡ **导出操作** - 文件写入为主要瓶颈

## 验证优化效果

生成 Profile 后，可以通过以下方式验证效果：

1. **对比基准测试**
   ```bash
   # 先测试无 Profile 的性能
   ./gradlew :benchmark:connectedCheck
   
   # 再测试有 Profile 的性能
   # 查看 startupWithBaselineProfile 与 startupNoCompilation 的差异
   ```

2. **查看编译状态**
   ```bash
   adb shell cmd package compile -m speed-profile -f com.example.sqliteviewer
   adb shell dumpsys package dexopt | grep com.example.sqliteviewer
   ```

3. **实际体验**
   - 卸载应用
   - 安装包含 Baseline Profile 的新版本
   - 测量首次启动时间

## 参考资源

- [Baseline Profiles 官方文档](https://developer.android.com/topic/performance/baselineprofiles)
- [Macrobenchmark 指南](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview)
- [性能优化最佳实践](https://developer.android.com/topic/performance)
