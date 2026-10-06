package com.example.sqliteviewer.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 表切换性能基准测试
 * 测试在不同编译模式下切换数据库表时的渲染性能
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class TableSwitchBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    /**
     * 表切换基准测试 - 无编译优化
     */
    @Test
    fun tableSwitchNoCompilation() = tableSwitch(CompilationMode.None())

    /**
     * 表切换基准测试 - Baseline Profile 优化
     * 验证热路径预编译对表切换流畅度的提升
     */
    @Test
    fun tableSwitchWithBaselineProfile() = tableSwitch(
        CompilationMode.Partial(BaselineProfileMode.Require)
    )

    private fun tableSwitch(compilationMode: CompilationMode) = benchmarkRule.measureRepeated(
        packageName = "com.example.sqliteviewer",
        metrics = listOf(FrameTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.WARM,
        compilationMode = compilationMode,
        setupBlock = {
            pressHome()
        }
    ) {
        startActivityAndWait()
        waitForAppStart()

        // 模拟表切换操作
        switchTables()
        scrollDataGrid()
    }
}
