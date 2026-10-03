package com.dfm.stringtables.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dfm.stringtables.data.AppDb
import com.dfm.stringtables.data.EntryRow
import com.dfm.stringtables.data.FavHub
import com.dfm.stringtables.data.Lbl
import com.dfm.stringtables.data.SnapshotMeta
import com.dfm.stringtables.data.TableRow
import com.dfm.stringtables.data.fmt
import com.dfm.stringtables.ui.theme.Shape8
import kotlinx.coroutines.launch

private const val MODE_TABLES = 0
private const val MODE_ENTRIES = 1

/**
 * 业务域详情：头部统计 + “数据表 / 条目”切换。
 * domain = -1 表示“全部业务域 / 全部条目”。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DomainScreen(
    domain: Int,
    meta: SnapshotMeta,
    db: AppDb,
    favHub: FavHub,
    onOpenTable: (Long) -> Unit,
    onOpenEntry: (Long) -> Unit,
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
    showSnack: (String, String?, (() -> Unit)?) -> Unit,
) {
    val s = MaterialTheme.colorScheme
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val favMap by favHub.map.collectAsState()
    val domainLabel = if (domain == -1) "全部业务域" else Lbl.domain(domain)
    val domainDesc = if (domain == -1) "所有本地化文本，跨表浏览"
    else "${Lbl.domain(domain)}中的文本，可下钻到具体表"

    var mode by rememberSaveable { mutableIntStateOf(MODE_TABLES) }
    var tables by remember { mutableStateOf<List<TableRow>?>(null) }
    var entriesTotal by remember { mutableIntStateOf(
        if (domain == -1) meta.entries
        else meta.domains.firstOrNull { it.id == domain }?.entries ?: 0
    ) }
    var tableCount by remember { mutableIntStateOf(
        if (domain == -1) meta.tables
        else meta.domains.firstOrNull { it.id == domain }?.tables ?: 0
    ) }

    LaunchedEffect(domain) {
        tables = db.tablesOfDomain(if (domain == -1) null else domain)
        tables?.let { tableCount = it.size }
        if (domain != -1) {
            entriesTotal = db.countEntriesOfDomain(domain)
        }
    }

    val isFav: (Long) -> Boolean = { id -> favMap.containsKey(id) }
    val toggleFav: (EntryRow) -> Unit = { row ->
        scope.launch {
            val nowFav = favHub.toggle(row)
            showSnack(if (nowFav) "已收藏（可在「收藏」中查看）" else "已取消收藏", null, null)
        }
    }
    val copyKey: (EntryRow) -> Unit = { row ->
        clipboard.setText(AnnotatedString(row.key))
        scope.launch { showSnack("已复制 Key", null, null) }
    }

    Scaffold(
        containerColor = s.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(domainLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        SymbolIcon(Sym.BACK, contentDescription = "返回")
                    }
                },
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
                .padding(pad),
        ) {
            // 头部：图标 + 说明
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DomainTile(domain = domain, size = 52.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(domainLabel, style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold, color = s.onSurface,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(domainDesc, style = MaterialTheme.typography.bodySmall,
                        color = s.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }

            // 数据表 / 条目 切换
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ModeChip(
                    text = "数据表",
                    sub = "${fmt(tableCount)} 张",
                    selected = mode == MODE_TABLES,
                    onClick = { mode = MODE_TABLES },
                )
                ModeChip(
                    text = "条目",
                    sub = "${fmt(entriesTotal)} 条",
                    selected = mode == MODE_ENTRIES,
                    onClick = { mode = MODE_ENTRIES },
                )
            }

            when (mode) {
                MODE_TABLES -> TablesPane(domain, tables, onOpenTable)
                else -> EntriesPane(
                    placeholder = if (domain == -1) "搜索全部条目（Key / 文本）"
                    else "在「$domainLabel」中搜索",
                    scopeTotal = entriesTotal,
                    search = { p, limit, off -> db.search(p, limit, off) },
                    count = { db.searchCount(it) },
                    domain = if (domain == -1) null else domain,
                    onOpenEntry = onOpenEntry,
                    isFav = isFav,
                    onToggleFav = toggleFav,
                    onCopyKey = copyKey,
                )
            }
        }
    }
}

/** 数据表 / 条目 二选一切换片（8dp 圆角，非胶囊）。 */
@Composable
private fun ModeChip(
    text: String,
    sub: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val s = MaterialTheme.colorScheme
    Surface(
        shape = Shape8,
        color = if (selected) s.primaryContainer else s.surfaceContainerLow,
        modifier = Modifier.pressScale(),
    ) {
        Column(
            Modifier
                .clip(Shape8)
                .clickable(onClick = onClick)
                .padding(horizontal = 18.dp, vertical = 8.dp),
        ) {
            Text(
                text,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) s.onPrimaryContainer else s.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                sub,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) s.onPrimaryContainer.copy(alpha = 0.75f) else s.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TablesPane(
    domain: Int,
    tables: List<TableRow>?,
    onOpenTable: (Long) -> Unit,
) {
    val s = MaterialTheme.colorScheme
    when (tables) {
        null -> Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(56.dp))
            CircularProgressIndicator(color = s.primary)
        }

        else -> {
            if (tables.isEmpty()) {
                EmptyState(
                    glyph = Sym.EMPTY_SEARCH,
                    title = "该分类下没有数据表",
                    subtitle = if (domain == -1) "没有可展示的内容" else "试试其它分类。",
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(key = "count") {
                        Text(
                            "共 ${fmt(tables.size)} 张表 · 点击进入条目",
                            style = MaterialTheme.typography.bodySmall,
                            color = s.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 6.dp),
                        )
                    }
                    items(tables, key = { it.id }) { t ->
                        TableRowCard(t) { onOpenTable(t.id) }
                    }
                }
            }
        }
    }
}

/** 表行卡片：文件 + 目录 + 条数。 */
@Composable
fun TableRowCard(table: TableRow, onClick: () -> Unit) {
    val s = MaterialTheme.colorScheme
    ClickableCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        containerColor = s.surfaceContainerLow,
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = Shape8,
                color = s.secondaryContainer,
                modifier = Modifier.size(40.dp),
            ) {
                Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    SymbolIcon(Sym.TABLE_FILE, size = 22.dp, tint = s.onSecondaryContainer)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    table.file,
                    style = MaterialTheme.typography.titleMedium,
                    color = s.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    table.dirName,
                    style = MaterialTheme.typography.bodySmall,
                    color = s.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(fmt(table.cnt), style = MaterialTheme.typography.titleSmall, color = s.primary)
                Text("条", style = MaterialTheme.typography.labelSmall, color = s.onSurfaceVariant)
            }
            Spacer(Modifier.width(2.dp))
            SymbolIcon(Sym.CHEVRON, size = 20.dp, tint = s.onSurfaceVariant)
        }
    }
}
