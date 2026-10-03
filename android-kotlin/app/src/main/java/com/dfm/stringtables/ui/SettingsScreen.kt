package com.dfm.stringtables.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dfm.stringtables.BuildConfig
import com.dfm.stringtables.data.AppDb
import com.dfm.stringtables.data.Lbl
import com.dfm.stringtables.data.SnapshotMeta
import com.dfm.stringtables.data.fmt
import com.dfm.stringtables.ui.theme.Shape8
import com.dfm.stringtables.ui.theme.ThemeController
import com.dfm.stringtables.ui.theme.ThemeMode
import kotlinx.coroutines.launch

/** 设置（第四个 Tab）：数据信息 / 主题说明 / 归类规则 / 数据维护。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    meta: SnapshotMeta,
    db: AppDb,
    snackbarHostState: SnackbarHostState,
    showSnack: (String, String?, (() -> Unit)?) -> Unit,
    onReloadDatabase: () -> Unit,
    bottomBar: @Composable (() -> Unit)? = null,
) {
    val s = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var dbBytes by remember { mutableLongStateOf(0L) }
    var askReset by remember { mutableStateOf(false) }
    var showRules by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        dbBytes = db.databaseFileBytes()
    }

    Scaffold(
        containerColor = s.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = { bottomBar?.invoke() },
        topBar = {
            TopAppBar(
                title = { Text("设置", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = s.surface,
                    titleContentColor = s.onSurface,
                ),
            )
        },
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // —— 外观 / 主题卡 ——
            SettingsCard("外观") {
                ThemeModeRow()
            }

            // —— 数据快照卡 ——
            SettingsCard("数据快照") {
                SettingsRow(Sym.TABLE_FILE, "数据表", fmt(meta.tables), null)
                SettingsRow(Sym.DOM_ALL, "条目文本", fmt(meta.entries), null)
                SettingsRow(Sym.CALENDAR, "分类构建", "%.2f s".format(meta.buildSeconds), null)
                SettingsRow(Sym.FOLDER_OPEN, "离线库大小", formatBytes(dbBytes), null)
                SettingsRow(Sym.LANG, "数据来源", "StringTables\n(三角洲行动 UE 本地化导出)", null, twoLineValue = true)
            }

            // —— 分类体系卡 ——
            SettingsCard("分类体系") {
                SettingsRow(Sym.DOM_ALL, "业务域", "${Lbl.DOMAINS.size} 个（按目录/表名前缀）", null)
                SettingsRow(Sym.KEY, "字段语义", "${Lbl.FIELDS.size} 类（按键名后缀）", null)
                SettingsRow(Sym.FILTER, "内容主题标签", "${Lbl.TAGS.size} 组关键词", null)
                SettingsRow(Sym.INFO, "归类规则说明", null, { showRules = true })
            }

            // —— 数据维护卡 ——
            SettingsCard("数据维护") {
                SettingsRow(
                    Sym.REFRESH, "重建本地数据库",
                    "重新从内置资产拷贝分类快照\n（会清空你的收藏）",
                    { askReset = true },
                    twoLineValue = true,
                )
            }

            // —— 关于 ——
            SettingsCard("关于") {
                SettingsRow(Sym.PERSON, "软件制作", "河豚潘PufferPan", null)
                SettingsRow(Sym.INFO, "版本", "v${BuildConfig.VERSION_NAME}", null)
                SettingsRow(Sym.KEY, "包名", BuildConfig.APPLICATION_ID, null)
                SettingsRow(Sym.CHECK, "内容", "无内置示例数据；空列表为真实空状态", null)
            }
        }
    }

    if (askReset) {
        ConfirmDialog(
            title = "重建本地数据库？",
            text = "将删除本机缓存的分类快照并重新从应用资产复制（约 ${formatBytes(dbBytes)}）。收藏记录会一并清空，无法撤销。",
            confirmLabel = "重建",
            destructive = true,
            onConfirm = {
                askReset = false
                scope.launch {
                    db.resetDatabase()
                    showSnack("数据库已重建", null, null)
                    onReloadDatabase()
                }
            },
            onDismiss = { askReset = false },
        )
    }

    if (showRules) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showRules = false },
            shape = com.dfm.stringtables.ui.theme.Shape12,
            containerColor = glassContainer,
            title = {
                Box {
                    DialogBackdropBlur()
                    Text("归类规则速览")
                }
            },
            text = {
                Text(
                    """
• 业务域：STForLua→Lua；STForMaps→剧情；UIStringTables/Oversea→UI；
  STForCodes→系统；其余按文件名关键词（EVENT/ACTIVITY→活动、
  GAMEITEM/LOTTERY→物品、HERO/SKIN/AVATAR→英雄、TASK/QUEST→任务、
  ACHIEVEMENT/BADGE→成就、MATCH/GAMEMODE/RAID→玩法 …）。
• 字段语义：优先 SPEAKER/SUBTITLE/LINES 片段，其次取键末段后缀
  （_Name/_Desc/_Detail/_Tip/_Title/_Text/_Btn …）。
• 内容主题标签：12 组关键词逐条匹配；“剧情台词”由字段类型派生。
                    """.trimIndent(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = s.onSurfaceVariant,
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { showRules = false }) {
                    Text("知道了", color = s.primary)
                }
            },
        )
    }
}

/** 主题：跟随系统 / 浅色 / 深色 三选一（沿用 ModeChip 的选中态：primaryContainer）。 */
@Composable
private fun ThemeModeRow() {
    val s = MaterialTheme.colorScheme
    val current = ThemeController.mode
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                SymbolIcon(Sym.PALETTE, size = 22.dp, tint = s.primary)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "主题",
                    style = MaterialTheme.typography.bodyLarge,
                    color = s.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "当前：${current.label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = s.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeMode.entries.forEach { m ->
                ModeOptionChip(
                    text = m.label,
                    selected = current == m,
                    onClick = { ThemeController.setMode(m) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "「跟随系统」随系统深浅色自动切换；选择「浅色 / 深色」后固定使用该配色。",
            style = MaterialTheme.typography.bodySmall,
            color = s.outline,
        )
    }
}

/** 单个主题选项片（8dp 圆角，非胶囊；选中用 primaryContainer 反显）。 */
@Composable
private fun ModeOptionChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val s = MaterialTheme.colorScheme
    Surface(
        shape = Shape8,
        color = if (selected) s.primaryContainer else s.surfaceContainer,
        modifier = Modifier.pressScale(),
    ) {
        Row(
            Modifier
                .clip(Shape8)
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selected) {
                SymbolIcon(Sym.CHECK, size = 16.dp, tint = s.onPrimaryContainer)
                Spacer(Modifier.width(4.dp))
            }
            Text(
                text,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) s.onPrimaryContainer else s.onSurface,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable () -> Unit,
) {
    val s = MaterialTheme.colorScheme
    Column {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = s.onSurface,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        )
        Surface(
            shape = Shape8,
            color = s.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                content()
            }
        }
    }
}

@Composable
private fun SettingsRow(
    glyph: ImageVector,
    label: String,
    value: String?,
    onClick: (() -> Unit)?,
    twoLineValue: Boolean = false,
) {
    val s = MaterialTheme.colorScheme
    val base = Modifier
        .fillMaxWidth()
        .then(if (onClick != null) Modifier.pressScale().clickable(onClick = onClick) else Modifier)
        .padding(horizontal = 14.dp, vertical = 10.dp)
    Row(
        base,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
            SymbolIcon(glyph, size = 22.dp, tint = s.primary)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                color = s.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (value != null) {
                Text(
                    value,
                    style = MaterialTheme.typography.bodySmall,
                    color = s.onSurfaceVariant,
                    maxLines = if (twoLineValue) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (onClick != null) {
            SymbolIcon(Sym.CHEVRON, size = 20.dp, tint = s.outline)
        }
    }
}

private fun formatBytes(n: Long): String = when {
    n >= 1024 * 1024 -> "%.1f MB".format(n / 1024.0 / 1024.0)
    n >= 1024 -> "%.0f KB".format(n / 1024.0)
    else -> "$n B"
}
