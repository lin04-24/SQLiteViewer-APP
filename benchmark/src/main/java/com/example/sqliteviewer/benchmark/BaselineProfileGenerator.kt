package com.example.sqliteviewer.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Baseline Profile 生成器
 *
 * 该测试会记录应用的关键用户流程，生成 Baseline Profile 文件。
 * Baseline Profile 包含了启动和核心功能的热路径代码，
 * 这些代码会在安装时或首次运行时被 AOT 编译，从而提升启动和运行性能。
 *
 * 运行命令：
 * ./gradlew :benchmark:generateBaselineProfile
 *
 * 生成的文件位置：
 * app/src/main/baseline-prof.txt
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generate() = baselineProfileRule.collect(
        packageName = "com.example.sqliteviewer",
        maxIterations = 15,
        stableIterations = 3,
        includeInStartupProfile = true
    ) {
        // 1. 应用启动流程
        pressHome()
        startActivityAndWait()

        // 等待主界面加载完成
        device.wait(Until.hasObject(By.pkg(packageName)), 5_000)
        device.waitForIdle()

        // 2. 浏览历史记录（如果有）
        val historyButton = device.findObject(By.desc("History"))
        if (historyButton != null && historyButton.isClickable) {
            historyButton.click()
            device.waitForIdle()

            // 返回主界面
            device.pressBack()
            device.waitForIdle()
        }

        // 3. 模拟打开数据库操作
        // 注意：实际测试时需要预置测试数据库文件或模拟文件选择
        val openButton = device.findObject(By.textContains("Open").clickable(true))
        if (openButton != null) {
            openButton.click()
            device.waitForIdle()
        }

        // 4. 模拟浏览数据表
        // 等待数据加载
        device.wait(Until.hasObject(By.clickable(true)), 3_000)
        device.waitForIdle()

        // 滚动浏览数据
        repeat(2) {
            device.swipe(
                device.displayWidth / 2,
                device.displayHeight * 2 / 3,
                device.displayWidth / 2,
                device.displayHeight / 3,
                10
            )
            device.waitForIdle()
        }

        // 5. 模拟切换到查询界面
        val queryButton = device.findObject(By.desc("Query"))
        if (queryButton != null && queryButton.isClickable) {
            queryButton.click()
            device.waitForIdle()
        }

        // 6. 模拟导出操作
        val exportButton = device.findObject(By.desc("Export"))
        if (exportButton != null && exportButton.isClickable) {
            exportButton.click()
            device.waitForIdle()
            device.pressBack()
        }
    }
}
