package com.dfm.stringtables.ui

import android.content.Intent
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dfm.stringtables.data.EntryRow
import com.dfm.stringtables.data.FavHub
import com.dfm.stringtables.data.Lbl
import com.dfm.stringtables.ui.theme.Shape12
import com.dfm.stringtables.ui.theme.Shape8
import kotlinx.coroutines.launch

/**
 * 条目详情：Key / 全文 / 业务域 / 字段 / 主题标签 / 本地化 ID / 所属表。
 * 支持复制 Key、复制全文、分享、收藏切换、跳转所在表。
 *
 * @param load      按 id 加载条目（entry 表或收藏快照）
 * @param fromFavDetail true 时取消收藏会返回上一页
 * @param onOpenTable 可空：收藏快照不提供“所在表”跳转
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryDetailScreen(
    load: suspend () -> EntryRow?,
    fromFavDetail: Boolean,
    favHub: FavHub,
    onBack: () -> Unit,
    onOpenTable: ((Long) -> Unit)?,
    snackbarHostState: SnackbarHostState,
    showSnack: (String, String?, (() -> Unit)?) -> Unit,
) {
    val s = MaterialTheme.colorScheme
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val favMap by favHub.map.collectAsState()
    var entry by remember { mutableStateOf<EntryRow?>(null) }
    var missing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        entry = load()
        if (entry == null) missing = true
    }

    val loaded = entry
    val isFav = loaded != null && loaded.id in favMap
    val toggleFav: () -> Unit = {
        val e = entry
        if (e != null) {
            scope.launch {
                val nowFav = favHub.toggle(e)
                showSnack(if (nowFav) "已收藏" else "已取消收藏", null, null)
                if (!nowFav && fromFavDetail) onBack()
            }
        }
    }

    Scaffold(
        containerColor = s.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("条目详情", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        SymbolIcon(Sym.BACK, contentDescription = "返回")
                    }
                },
                actions = {
                    if (entry != null) {
                        IconButton(onClick = {
                            clipboard.setText(AnnotatedString(entry!!.key))
                            scope.launch { showSnack("已复制 Key", null, null) }
                        }) {
                            SymbolIcon(Sym.COPY, contentDescription = "复制 Key")
                        }
                        IconButton(onClick = { shareEntry(context, entry!!) }) {
                            SymbolIcon(Sym.SHARE, contentDescription = "分享")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = s.surface,
                    titleContentColor = s.onSurface,
                ),
            )
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when {
                missing -> EmptyState(
                    glyph = Sym.EMPTY_SEARCH,
                    title = "条目不存在",
                    subtitle = "该条目可能已被移除。",
                    action = {
                        OutlinedButton(onClick = onBack, shape = Shape12) { Text("返回") }
                    },
                )

                entry == null -> Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(72.dp))
                    androidx.compose.material3.CircularProgressIndicator(color = s.primary)
                }

                else -> DetailBody(
                    entry = entry!!,
                    isFav = isFav,
                    onToggleFav = toggleFav,
                    onCopyKey = {
                        clipboard.setText(AnnotatedString(entry!!.key))
                        scope.launch { showSnack("已复制 Key", null, null) }
                    },
                    onCopyText = {
                        clipboard.setText(AnnotatedString(entry!!.text))
                        scope.launch { showSnack("已复制全文", null, null) }
                    },
                    onOpenTable = onOpenTable,
                )
            }
        }
    }
}

@Composable
private fun DetailBody(
    entry: EntryRow,
    isFav: Boolean,
    onToggleFav: () -> Unit,
    onCopyKey: () -> Unit,
    onCopyText: () -> Unit,
    onOpenTable: ((Long) -> Unit)?,
) {
    val s = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // —— 正文卡（Key + 全文）——
        Surface(
            shape = Shape8,
            color = s.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = Shape8,
                        color = s.primaryContainer,
                    ) {
                        Box(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                            Text(
                                "Key",
                                style = MaterialTheme.typography.labelMedium,
                                color = s.onPrimaryContainer,
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        entry.key,
                        style = MaterialTheme.typography.labelLarge,
                        fontFamily = FontFamily.Monospace,
                        color = s.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onCopyKey) {
                        SymbolIcon(
                            Sym.COPY, size = 20.dp, tint = s.onSurfaceVariant,
                            contentDescription = "复制 Key",
                        )
                    }
                }
                HorizontalDivider(
                    color = s.outlineVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(vertical = 10.dp),
                )
                SelectionContainer {
                    Text(
                        entry.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = s.onSurface,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        // —— 属性卡 ——
        Surface(
            shape = Shape8,
            color = s.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp)) {
                DetailRow("业务域", null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DomainTile(domain = entry.domain, size = 24.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(Lbl.domain(entry.domain), style = MaterialTheme.typography.bodyMedium,
                            color = s.onSurface)
                    }
                }
                DetailRow("字段语义", null) { FieldBadge(entry.field) }
                DetailRow("本地化 ID", entry.locIdText) {}
                DetailRow("所属表", entry.file) {
                    Text(entry.ns.ifBlank { entry.rel }, style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace, color = s.onSurfaceVariant)
                }
                if (entry.rel.isNotBlank()) {
                    DetailRow("相对路径", null) {
                        SelectionContainer {
                            Text(entry.rel, style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace, color = s.onSurfaceVariant)
                        }
                    }
                }
                val tagBits = Lbl.tagNames(entry.tags)
                if (tagBits.isNotEmpty()) {
                    DetailRow("主题标签", null) {
                        Row(Modifier.horizontalScroll(rememberScrollState())) {
                            tagBits.forEach { bit ->
                                TagChip(bit)
                                Spacer(Modifier.width(6.dp))
                            }
                        }
                    }
                } else {
                    DetailRow("主题标签", "无") {}
                }
            }
        }

        // —— 操作 ——
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilledTonalButton(
                onClick = onToggleFav,
                modifier = Modifier.weight(1f),
                shape = Shape12,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = if (isFav) s.primaryContainer else s.secondaryContainer,
                    contentColor = if (isFav) s.onPrimaryContainer else s.onSecondaryContainer,
                ),
            ) {
                SymbolIcon(
                    if (isFav) Sym.FAVORITE else Sym.FAV_OFF,
                    size = 18.dp,
                    filled = isFav,
                    tint = if (isFav) s.onPrimaryContainer else s.onSecondaryContainer,
                )
                Spacer(Modifier.width(6.dp))
                Text(if (isFav) "已收藏" else "收藏")
            }
            OutlinedButton(
                onClick = onCopyText,
                modifier = Modifier.weight(1f),
                shape = Shape12,
            ) {
                SymbolIcon(Sym.COPY, size = 18.dp, tint = s.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                Text("复制全文")
            }
        }
        if (onOpenTable != null && entry.tbl > 0) {
            OutlinedButton(
                onClick = { onOpenTable(entry.tbl) },
                modifier = Modifier.fillMaxWidth(),
                shape = Shape12,
            ) {
                SymbolIcon(Sym.TABLE_FILE, size = 18.dp, tint = s.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                Text("打开所在表（${entry.file}）", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String?,
    valueContent: @Composable () -> Unit,
) {
    val s = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = s.onSurfaceVariant,
            modifier = Modifier.width(92.dp),
        )
        if (value != null) {
            SelectionContainer {
                Text(value, style = MaterialTheme.typography.bodyMedium, color = s.onSurface)
            }
        } else {
            valueContent()
        }
    }
}

private fun shareEntry(context: android.content.Context, e: EntryRow) {
    val text = """
        ${e.key}
        ————————
        ${e.text}

        [${Lbl.domain(e.domain)}] [${Lbl.field(e.field)}]
        表: ${e.file}    ID: ${e.locIdText}
    """.trimIndent()
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, e.key)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, "分享条目").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
