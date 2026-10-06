package com.example.dbviewer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dbviewer.ui.theme.DbColors

data class SqlTemplate(
    val name: String,
    val description: String,
    val template: String,
    val category: String
)

val SQL_TEMPLATES = listOf(
    // Basic Queries
    SqlTemplate(
        name = "基础查询",
        description = "简单的 SELECT 查询",
        template = "SELECT * FROM table_name LIMIT 100;",
        category = "基础"
    ),
    SqlTemplate(
        name = "条件查询",
        description = "带 WHERE 条件的查询",
        template = "SELECT * FROM table_name\nWHERE column = 'value'\nLIMIT 100;",
        category = "基础"
    ),
    SqlTemplate(
        name = "排序查询",
        description = "ORDER BY 排序",
        template = "SELECT * FROM table_name\nORDER BY column DESC\nLIMIT 100;",
        category = "基础"
    ),

    // Joins
    SqlTemplate(
        name = "内连接",
        description = "INNER JOIN 两表关联",
        template = "SELECT a.*, b.*\nFROM table_a a\nINNER JOIN table_b b ON a.id = b.a_id\nLIMIT 100;",
        category = "连接"
    ),
    SqlTemplate(
        name = "左连接",
        description = "LEFT JOIN 保留左表全部记录",
        template = "SELECT a.*, b.*\nFROM table_a a\nLEFT JOIN table_b b ON a.id = b.a_id\nLIMIT 100;",
        category = "连接"
    ),

    // Aggregation
    SqlTemplate(
        name = "分组统计",
        description = "GROUP BY 聚合查询",
        template = "SELECT column, COUNT(*) as count\nFROM table_name\nGROUP BY column\nORDER BY count DESC;",
        category = "聚合"
    ),
    SqlTemplate(
        name = "多列分组",
        description = "多列 GROUP BY",
        template = "SELECT col1, col2, COUNT(*) as count, AVG(value) as avg_value\nFROM table_name\nGROUP BY col1, col2\nHAVING count > 10\nORDER BY count DESC;",
        category = "聚合"
    ),

    // Window Functions
    SqlTemplate(
        name = "行号窗口函数",
        description = "ROW_NUMBER() 排序编号",
        template = "SELECT *,\n  ROW_NUMBER() OVER (PARTITION BY category ORDER BY value DESC) as row_num\nFROM table_name\nLIMIT 100;",
        category = "窗口函数"
    ),
    SqlTemplate(
        name = "累计求和",
        description = "SUM() OVER 累计计算",
        template = "SELECT *,\n  SUM(amount) OVER (ORDER BY date) as running_total\nFROM table_name\nLIMIT 100;",
        category = "窗口函数"
    ),

    // Subqueries
    SqlTemplate(
        name = "子查询",
        description = "IN 子查询",
        template = "SELECT * FROM table_name\nWHERE id IN (\n  SELECT id FROM other_table WHERE condition\n)\nLIMIT 100;",
        category = "子查询"
    ),
    SqlTemplate(
        name = "EXISTS 子查询",
        description = "检查存在性",
        template = "SELECT * FROM table_a a\nWHERE EXISTS (\n  SELECT 1 FROM table_b b WHERE b.a_id = a.id\n)\nLIMIT 100;",
        category = "子查询"
    ),

    // Advanced
    SqlTemplate(
        name = "CASE 表达式",
        description = "条件分支逻辑",
        template = "SELECT *,\n  CASE\n    WHEN value > 100 THEN '高'\n    WHEN value > 50 THEN '中'\n    ELSE '低'\n  END as level\nFROM table_name\nLIMIT 100;",
        category = "高级"
    ),
    SqlTemplate(
        name = "UNION 联合",
        description = "合并多个查询结果",
        template = "SELECT id, name FROM table_a\nUNION\nSELECT id, name FROM table_b\nORDER BY name;",
        category = "高级"
    ),
)

@Composable
fun SqlTemplateMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onSelectTemplate: (SqlTemplate) -> Unit,
    modifier: Modifier = Modifier
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = modifier.width(280.dp),
    ) {
        Text(
            text = "SQL 模板",
            fontSize = 13.sp,
            color = DbColors.TextSecondary,
            modifier = Modifier.padding(12.dp, 8.dp)
        )

        val categories = SQL_TEMPLATES.groupBy { it.category }
        categories.forEach { (category, templates) ->
            HorizontalDivider()
            Text(
                text = category,
                fontSize = 11.sp,
                color = DbColors.TextMuted,
                modifier = Modifier.padding(12.dp, 6.dp, 12.dp, 4.dp)
            )
            templates.forEach { template ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                text = template.name,
                                fontSize = 13.sp,
                                color = DbColors.TextPrimary
                            )
                            Text(
                                text = template.description,
                                fontSize = 11.sp,
                                color = DbColors.TextMuted
                            )
                        }
                    },
                    onClick = {
                        onSelectTemplate(template)
                        onDismiss()
                    }
                )
            }
        }
    }
}
