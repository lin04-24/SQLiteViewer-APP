package com.example.sqliteviewer.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 启动性能基准测试
 * 测试冷启动、温启动和热启动场景下的性能表现
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    /**
     * 冷启动基准测试 - 无编译优化
     * 模拟首次安装后的启动性能
     */
    @Test
    fun startupNoCompilation() = startup(CompilationMode.None())

    /**
     * 冷启动基准测试 - Baseline Profile 优化
     * 验证 Baseline Profile 对启动速度的提升效果（预期提升 15-30%）
     */
    @Test
    fun startupWithBaselineProfile() = startup(
        CompilationMode.Partial(BaselineProfileMode.Require)
    )

    /**
     * 冷启动基准测试 - 完全 AOT 编译
     * 对比完全编译后的最佳性能
     */
    @Test
    fun startupFullCompilation() = startup(CompilationMode.Full())

    private fun startup(compilationMode: CompilationMode) = benchmarkRule.measureRepeated(
        packageName = "com.example.sqliteviewer",
        metrics = listOf(StartupTimingMetric()),
        iterations = 10,
        startupMode = StartupMode.COLD,
        compilationMode = compilationMode,
        setupBlock = {
            pressHome()
        }
    ) {
        startActivityAndWait()
        waitForAppStart()
    }
}
