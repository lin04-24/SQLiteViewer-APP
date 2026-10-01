package com.example.dbviewer.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dbviewer.data.GridRow
import com.example.dbviewer.ui.buildJson
import com.example.dbviewer.ui.theme.DbColors
import java.net.URL

/** Compact icon + label action used by the browser and query toolbars. */
@Composable
fun ToolbarAction(icon: ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    val tint = if (enabled) DbColors.TextSecondary else DbColors.TextMuted.copy(alpha = 0.4f)
    Row(
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(15.dp), tint = tint)
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 12.sp, color = tint, maxLines = 1)
    }
}

@Composable
fun ReadOnlyBadge() {
    Surface(color = DbColors.SurfaceHigh, shape = RoundedCornerShape(20.dp), modifier = Modifier.padding(horizontal = 6.dp)) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(11.dp), tint = DbColors.TextMuted)
            Spacer(Modifier.width(4.dp))
            Text("只读", fontSize = 10.sp, color = DbColors.TextMuted)
        }
    }
}

@Composable
fun MetaChip(text: String, accent: Boolean = false) {
    Surface(
        color = if (accent) DbColors.AccentSoft else DbColors.SurfaceHigh,
        shape = RoundedCornerShape(5.dp),
        modifier = Modifier.padding(end = 6.dp),
    ) {
        Text(
            text = text,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            color = if (accent) DbColors.Accent else DbColors.TextSecondary,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            maxLines = 1,
        )
    }
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = DbColors.Accent, strokeWidth = 2.dp)
            Spacer(Modifier.height(8.dp))
            Text("读取中…", color = DbColors.TextMuted, fontSize = 12.sp)
        }
    }
}

@Composable
fun EmptyHint(text: String) {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = DbColors.TextMuted, fontSize = 12.5.sp)
    }
}

/** Full row contents, shown when a grid row is tapped. Every value can be copied. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RowDetailSheet(columns: List<GridColumn>, row: GridRow, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var linkMessage by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    val links = row.values.mapIndexedNotNull { index, value ->
        val normalized = value?.trim()?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
        normalized?.let { index to it }
    }
    var imageUrl by remember { mutableStateOf<String?>(null) }
    if (imageUrl != null) {
        ImagePreviewDialog(imageUrl = imageUrl!!, onDismiss = { imageUrl = null })
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = DbColors.Surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("行详情", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = DbColors.TextPrimary)
                Spacer(Modifier.weight(1f))
                Text(
                    text = row.id?.let { "rowid $it" } ?: "第 ${row.position + 1} 行",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = DbColors.TextMuted,
                )
                Spacer(Modifier.width(6.dp))
                TextButton(onClick = { clipboard.setText(AnnotatedString(buildJson(columns.map { it.title }, listOf(row.values)))) }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("复制整行", fontSize = 12.sp)
                }
            }
            if (links.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = {
                        val link = links.first().second
                        linkMessage = runCatching {
                            URL(link).toURI()
                            "链接格式有效"
                        }.getOrElse { "链接格式无效" }
                    }) {
                        Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("检查链接", fontSize = 12.sp)
                    }
                    TextButton(onClick = {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(links.first().second))) }
                    }) {
                        Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("打开链接", fontSize = 12.sp)
                    }
                }
                linkMessage?.let { Text(it, fontSize = 11.sp, color = DbColors.TextSecondary, modifier = Modifier.padding(bottom = 4.dp)) }
            }
            HorizontalDivider(color = DbColors.Divider)
            Spacer(Modifier.height(6.dp))
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                itemsIndexed(columns) { index, column ->
                    DetailField(column, row.values.getOrNull(index), onCopy = {
                        clipboard.setText(AnnotatedString(row.values.getOrNull(index) ?: "NULL"))
                    }, onOpenLink = { url ->
                        if (isImageUrl(url)) imageUrl = url else runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        }
                    })
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun DetailField(column: GridColumn, value: String?, onCopy: () -> Unit, onOpenLink: (String) -> Unit) {
    val url = value?.takeIf(::isHttpUrl)
    Column(Modifier.fillMaxWidth().combinedClickable(onClick = { if (url != null) onOpenLink(url) else onCopy() }, onLongClick = onCopy).padding(vertical = 9.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(column.title, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = DbColors.TextSecondary)
            if (column.primaryKey) {
                Spacer(Modifier.width(5.dp))
                Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(10.dp), tint = DbColors.Key)
            }
            column.type?.let {
                Spacer(Modifier.width(6.dp))
                Text(it.uppercase(), fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = DbColors.TextMuted)
            }
        }
        Spacer(Modifier.height(3.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = value ?: "NULL",
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
                color = when {
                    value == null -> DbColors.Null
                    url != null -> DbColors.Accent
                    else -> DbColors.TextPrimary
                },
            )
            if (url != null) {
                Spacer(Modifier.width(5.dp))
                Icon(Icons.Default.OpenInNew, contentDescription = "打开链接", modifier = Modifier.size(13.dp), tint = DbColors.Accent)
            }
        }
    }
    HorizontalDivider(color = DbColors.Divider)
}

private fun isHttpUrl(value: String): Boolean = value.startsWith("http://", ignoreCase = true) || value.startsWith("https://", ignoreCase = true)

private fun isImageUrl(value: String): Boolean = isHttpUrl(value) && value.substringBefore('?').substringBefore('#').lowercase().let {
    it.endsWith(".jpg") || it.endsWith(".jpeg") || it.endsWith(".png") || it.endsWith(".gif") || it.endsWith(".webp") || it.endsWith(".bmp")
}

@Composable
private fun ImagePreviewDialog(imageUrl: String, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(onDismissRequest = onDismiss, confirmButton = {
        TextButton(onClick = onDismiss) { Text("关闭") }
    }, title = { Text("图片预览") }, text = {
        androidx.compose.foundation.Image(
            painter = coil.compose.rememberAsyncImagePainter(imageUrl),
            contentDescription = "图片预览",
            modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
        )
    })
}
