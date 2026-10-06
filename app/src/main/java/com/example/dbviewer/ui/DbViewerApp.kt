package com.example.dbviewer.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.sqliteviewer.BuildConfig
import com.example.dbviewer.data.DbRepository
import com.example.dbviewer.data.PreferencesStore
import com.example.dbviewer.data.RecentFile
import com.example.dbviewer.data.RecentFilesStore
import com.example.dbviewer.data.UpdateChecker
import com.example.dbviewer.presentation.DbViewModel
import com.example.dbviewer.presentation.UpdateState
import com.example.dbviewer.presentation.UpdateViewModel
import com.example.dbviewer.ui.components.FirstTimeUpdateDialog
import com.example.dbviewer.ui.components.UpdateDialog
import com.example.dbviewer.ui.theme.DbColors
import com.example.dbviewer.ui.theme.DbViewerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first

private val DATABASE_MIME_TYPES = arrayOf("application/octet-stream", "application/x-sqlite3", "application/vnd.sqlite3", "*/*")

private enum class ViewerSection(val label: String, val icon: ImageVector) {
    Browse("浏览", Icons.Default.Folder),
    Query("查询", Icons.Default.Code),
    History("历史", Icons.Default.History),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DbViewerApp(initialUri: Uri?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val recentStore = remember(context) { RecentFilesStore(context) }
    var recent by remember { mutableStateOf(recentStore.list()) }
    val preferencesStore = remember(context) { PreferencesStore(context) }
    val vm: DbViewModel = viewModel(
        factory = DbViewModel.factory(DbRepository(context), preferencesStore)
    )
    val state by vm.state.collectAsState()

    val updateViewModel = remember(context) {
        UpdateViewModel(
            updateChecker = UpdateChecker(),
            preferencesStore = preferencesStore,
            currentVersion = BuildConfig.VERSION_NAME
        )
    }
    val updateState by updateViewModel.updateState.collectAsState()
    val showFirstTimeDialog by updateViewModel.showFirstTimeDialog.collectAsState()
    val autoCheckEnabled by updateViewModel.autoCheckEnabled.collectAsState()

    // Theme state
    val themeModeFlow = preferencesStore.themeMode.collectAsState(initial = "DARK")
    var currentThemeMode by remember { mutableStateOf(com.example.dbviewer.ui.theme.ThemeMode.DARK) }

    LaunchedEffect(themeModeFlow.value) {
        currentThemeMode = when (themeModeFlow.value) {
            "LIGHT" -> com.example.dbviewer.ui.theme.ThemeMode.LIGHT
            "HIGH_CONTRAST" -> com.example.dbviewer.ui.theme.ThemeMode.HIGH_CONTRAST
            "SYSTEM" -> com.example.dbviewer.ui.theme.ThemeMode.SYSTEM
            else -> com.example.dbviewer.ui.theme.ThemeMode.DARK
        }
    }

    var section by remember { mutableStateOf(ViewerSection.Browse) }
    var pendingExport by remember { mutableStateOf<ExportRequest?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    var showSettings by remember { mutableStateOf(false) }

    // Check for updates on startup if enabled
    LaunchedEffect(Unit) {
        if (preferencesStore.autoCheckUpdates.first()) {
            updateViewModel.checkForUpdates()
        } else if (preferencesStore.lastUpdateCheck.first() == 0L) {
            updateViewModel.showFirstTimeDialog()
        }
    }

    fun openDatabase(uri: Uri) {
        runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        recentStore.remember(uri, uri.lastPathSegment?.substringAfterLast('/') ?: "Database")
        recent = recentStore.list()
        section = ViewerSection.Browse
        vm.open(uri)
    }

    fun finishExport(uri: Uri?) {
        val request = pendingExport
        pendingExport = null
        if (uri == null || request == null) return
        scope.launch {
            val failure = withContext(Dispatchers.IO) {
                runCatching {
                    val stream = context.contentResolver.openOutputStream(uri)
                    requireNotNull(stream) { "无法写入所选文件" }
                    stream.use { it.write(request.content.toByteArray()) }
                }.exceptionOrNull()
            }
            snackbarHostState.showSnackbar(if (failure == null) "已导出 ${request.fileName}" else "导出失败：${failure.message ?: "未知错误"}")
        }
    }

    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { openDatabase(it) } }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> finishExport(uri) }
    val jsonLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> finishExport(uri) }

    fun requestExport(request: ExportRequest) {
        pendingExport = request
        if (request.mimeType == "application/json") jsonLauncher.launch(request.fileName) else csvLauncher.launch(request.fileName)
    }

    LaunchedEffect(initialUri) { initialUri?.let { openDatabase(it) } }

    // Update Dialogs
    if (showFirstTimeDialog) {
        FirstTimeUpdateDialog(
            onEnableAutoCheck = {
                updateViewModel.setAutoCheckEnabled(true)
                updateViewModel.dismissFirstTimeDialog()
                updateViewModel.checkForUpdates()
            },
            onDismiss = {
                updateViewModel.dismissFirstTimeDialog()
            }
        )
    }

    if (updateState is UpdateState.Available) {
        val updateInfo = (updateState as UpdateState.Available).info
        UpdateDialog(
            updateInfo = updateInfo,
            onDismiss = { updateViewModel.dismissUpdate() },
            onDownload = { url ->
                updateViewModel.dismissUpdate()
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse(url)
                }
                context.startActivity(intent)
            }
        )
    }

    DbViewerTheme(themeMode = currentThemeMode) {
        if (showSettings) {
            SettingsScreen(
                updateViewModel = updateViewModel,
                currentThemeMode = currentThemeMode,
                onThemeModeChange = { mode -> scope.launch { preferencesStore.setThemeMode(mode.name) } },
                onNavigateBack = { showSettings = false },
                onOpenGitHub = {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse("https://github.com/lin04-24/SQLiteViewer-APP")
                    }
                    context.startActivity(intent)
                }
            )
        } else Scaffold(
            containerColor = DbColors.Background,
            topBar = {
                ViewerTopBar(
                    state = state,
                    onOpen = { openLauncher.launch(DATABASE_MIME_TYPES) },
                    onBack = { if (state.selectedTable != null) vm.clearTableSelection() else vm.closeDatabase() },
                    onClose = { section = ViewerSection.Browse; vm.closeDatabase() },
                    onOpenSettings = { showSettings = true }
                )
            },
            bottomBar = { if (state.session != null) ViewerBottomBar(section) { section = it } },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize()) {
                state.error?.let { message -> ErrorBanner(message, vm::dismissError) }
                if (state.session == null) {
                    HomeScreen(
                        loading = state.loading,
                        recent = recent,
                        onOpen = { openLauncher.launch(DATABASE_MIME_TYPES) },
                        onRecent = { uri -> openDatabase(uri) },
                        onForget = { file -> recentStore.forget(file.uri); recent = recentStore.list() },
                        onClearRecent = { recentStore.clear(); recent = emptyList() },
                    )
                } else {
                    when (section) {
                        ViewerSection.Browse -> BrowserScreen(vm, state, { request -> requestExport(request) }) { sql ->
                            vm.useHistory(sql)
                            section = ViewerSection.Query
                        }
                        ViewerSection.Query -> QueryScreen(vm, state, onExport = { request -> requestExport(request) })
                        ViewerSection.History -> HistoryScreen(
                            state = state,
                            onUse = { sql ->
                                vm.useHistory(sql)
                                section = ViewerSection.Query
                            },
                            onClear = vm::clearHistory,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ViewerTopBar(state: DbViewModel.UiState, onOpen: () -> Unit, onBack: () -> Unit, onClose: () -> Unit, onOpenSettings: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    var about by remember { mutableStateOf(false) }
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = DbColors.Surface,
            titleContentColor = DbColors.TextPrimary,
            navigationIconContentColor = DbColors.TextSecondary,
            actionIconContentColor = DbColors.TextSecondary,
        ),
        navigationIcon = {
            if (state.session != null) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
            }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Storage, contentDescription = null, modifier = Modifier.size(17.dp), tint = DbColors.Accent)
                Spacer(Modifier.width(9.dp))
                Column {
                    Text(
                        text = if (state.session == null) "SQLite 查看器" else state.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (state.session == null) "只读浏览 .db 文件" else "SQLite 只读会话",
                        fontSize = 10.sp,
                        color = DbColors.TextMuted,
                        maxLines = 1,
                    )
                }
            }
        },
        actions = {
            IconButton(onClick = onOpen) { Icon(Icons.Default.FolderOpen, contentDescription = "打开数据库") }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "更多") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("打开其他数据库", fontSize = 13.sp) }, onClick = { menu = false; onOpen() })
                    if (state.session != null) {
                        DropdownMenuItem(text = { Text("关闭数据库", fontSize = 13.sp) }, onClick = { menu = false; onClose() })
                    }
                    DropdownMenuItem(text = { Text("设置", fontSize = 13.sp) }, onClick = { menu = false; onOpenSettings() })
                    DropdownMenuItem(text = { Text("关于", fontSize = 13.sp) }, onClick = { menu = false; about = true })
                }
            }
        },
    )
    if (about) {
        AlertDialog(
            onDismissRequest = { about = false },
            confirmButton = { TextButton(onClick = { about = false }) { Text("知道了") } },
            title = { Text("SQLite 查看器") },
            text = {
                Text(
                    "以只读方式打开本地 SQLite 数据库文件。\n\n" +
                        "· 浏览表与视图，支持分页、每页行数与导出 CSV / JSON\n" +
                        "· 查看列定义、索引与建表 DDL\n" +
                        "· 执行只读 SQL（SELECT / CTE / PRAGMA）\n\n" +
                        "写入、修改结构的语句会被拒绝执行。",
                    fontSize = 13.sp,
                )
            },
        )
    }
}

@Composable
private fun ViewerBottomBar(current: ViewerSection, onSelect: (ViewerSection) -> Unit) {
    NavigationBar(containerColor = DbColors.Surface, tonalElevation = 0.dp) {
        ViewerSection.entries.forEach { item ->
            NavigationBarItem(
                selected = current == item,
                onClick = { onSelect(item) },
                icon = { Icon(item.icon, contentDescription = item.label, modifier = Modifier.size(20.dp)) },
                label = { Text(item.label, fontSize = 10.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = DbColors.Accent,
                    selectedTextColor = DbColors.Accent,
                    indicatorColor = DbColors.AccentSoft,
                    unselectedIconColor = DbColors.TextMuted,
                    unselectedTextColor = DbColors.TextMuted,
                ),
            )
        }
    }
}

@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    Surface(color = DbColors.Error.copy(alpha = 0.14f)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.ErrorOutline, contentDescription = null, modifier = Modifier.size(15.dp), tint = DbColors.Error)
            Spacer(Modifier.width(8.dp))
            Text(message, modifier = Modifier.weight(1f), fontSize = 12.sp, color = DbColors.Error, maxLines = 3, overflow = TextOverflow.Ellipsis)
            IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Close, contentDescription = "关闭", modifier = Modifier.size(15.dp), tint = DbColors.Error)
            }
        }
    }
}

@Composable
private fun HomeScreen(
    loading: Boolean,
    recent: List<RecentFile>,
    onOpen: () -> Unit,
    onRecent: (Uri) -> Unit,
    onForget: (RecentFile) -> Unit,
    onClearRecent: () -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        item {
            Column(Modifier.fillMaxWidth().padding(top = 32.dp, bottom = 20.dp)) {
                Box(Modifier.size(52.dp).background(DbColors.AccentSoft, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Storage, contentDescription = null, modifier = Modifier.size(26.dp), tint = DbColors.Accent)
                }
                Spacer(Modifier.height(16.dp))
                Text("SQLite 查看器", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = DbColors.TextPrimary)
                Spacer(Modifier.height(6.dp))
                Text("以只读方式打开本地 .db 文件，浏览数据、结构与 DDL。", fontSize = 13.sp, color = DbColors.TextSecondary)
                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = onOpen,
                    enabled = !loading,
                    shape = RoundedCornerShape(9.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DbColors.Accent, contentColor = Color(0xFF08101F)),
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (loading) "正在打开…" else "打开数据库", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
        if (recent.isNotEmpty()) {
            item {
                Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("最近文件", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = DbColors.TextSecondary)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onClearRecent) { Text("清空", fontSize = 12.sp, color = DbColors.TextMuted) }
                }
            }
            items(recent, key = { it.uri }) { file ->
                RecentRow(file, onClick = { onRecent(Uri.parse(file.uri)) }, onForget = { onForget(file) })
            }
        }
    }
}

@Composable
private fun RecentRow(file: RecentFile, onClick: () -> Unit, onForget: () -> Unit) {
    Surface(
        color = DbColors.Surface,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Storage, contentDescription = null, modifier = Modifier.size(16.dp), tint = DbColors.TextMuted)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(file.name, fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = DbColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(formatTime(file.lastAccess), fontSize = 10.sp, color = DbColors.TextMuted)
            }
            IconButton(onClick = onForget, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Default.Close, contentDescription = "移除", modifier = Modifier.size(15.dp), tint = DbColors.TextMuted)
            }
        }
    }
}
