package com.example.sqliteviewer.benchmark

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until

/**
 * 等待应用启动完成，直到主界面元素可见
 */
fun MacrobenchmarkScope.waitForAppStart() {
    device.wait(Until.hasObject(By.pkg(packageName)), 5_000)
}

/**
 * 模拟打开数据库文件的操作
 * 注意：由于需要文件选择器交互，实际测试中可能需要预置数据库文件
 */
fun MacrobenchmarkScope.openDatabase() {
    // 等待"打开数据库"按钮出现
    val openButton = device.wait(
        Until.findObject(By.text("Open Database").clickable(true)),
        3_000
    )
    openButton?.click()

    // 等待文件选择器或数据库列表加载
    device.waitForIdle()
}

/**
 * 模拟在表之间切换
 */
fun MacrobenchmarkScope.switchTables() {
    // 等待表列表加载
    device.waitForIdle()

    // 查找并点击第一个表
    val firstTable = device.findObject(By.clickable(true))
    firstTable?.click()
    device.waitForIdle()

    // 如果有多个表，切换到下一个表
    val nextTable = device.findObject(By.clickable(true))
    nextTable?.click()
    device.waitForIdle()
}

/**
 * 模拟滚动数据表格
 */
fun MacrobenchmarkScope.scrollDataGrid() {
    repeat(3) {
        device.swipe(
            device.displayWidth / 2,
            device.displayHeight * 2 / 3,
            device.displayWidth / 2,
            device.displayHeight / 3,
            10
        )
        device.waitForIdle()
    }
}
