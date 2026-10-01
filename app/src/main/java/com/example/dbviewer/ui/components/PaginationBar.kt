package com.example.dbviewer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.LastPage
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.FirstPage
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dbviewer.ui.formatCount
import com.example.dbviewer.ui.theme.DbColors

/**
 * Footer of the data browser: visible range, page size and first/previous/next/last navigation,
 * mirroring the layout used by hosted database consoles.
 */
@Composable
fun PaginationBar(
    rangeStart: Int,
    rangeEnd: Int,
    totalRows: Long,
    totalExact: Boolean,
    pageIndex: Int,
    pageCount: Int,
    pageSize: Int,
    pageSizes: List<Int>,
    hasNext: Boolean,
    enabled: Boolean,
    onPageSize: (Int) -> Unit,
    onFirst: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onLast: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().height(46.dp).background(DbColors.Surface).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (totalRows > 0) "$rangeStart-$rangeEnd / ${if (totalExact) "" else "~"}${formatCount(totalRows)} 行" else "0 行",
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = DbColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        PageSizeMenu(pageSize, pageSizes, enabled, onPageSize)
        Spacer(Modifier.width(6.dp))
        NavButton(Icons.Default.FirstPage, "第一页", enabled && pageIndex > 0, onFirst)
        NavButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "上一页", enabled && pageIndex > 0, onPrevious)
        Text(
            text = "${pageIndex + 1} / $pageCount",
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = DbColors.TextPrimary,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        NavButton(Icons.AutoMirrored.Filled.KeyboardArrowRight, "下一页", enabled && hasNext, onNext)
        NavButton(Icons.AutoMirrored.Filled.LastPage, "最后一页", enabled && hasNext, onLast)
    }
}

@Composable
private fun NavButton(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(30.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            modifier = Modifier.size(17.dp),
            tint = if (enabled) DbColors.TextPrimary else DbColors.TextMuted.copy(alpha = 0.35f),
        )
    }
}

@Composable
private fun PageSizeMenu(pageSize: Int, options: List<Int>, enabled: Boolean, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Surface(
            color = DbColors.SurfaceHigh,
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.clickable(enabled = enabled) { expanded = true },
        ) {
            Row(
                modifier = Modifier.padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("$pageSize", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = DbColors.TextPrimary)
                Icon(Icons.Default.ArrowDropDown, contentDescription = "每页行数", modifier = Modifier.size(15.dp), tint = DbColors.TextSecondary)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text("$option 行 / 页", fontSize = 13.sp, color = if (option == pageSize) DbColors.Accent else DbColors.TextPrimary) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}
