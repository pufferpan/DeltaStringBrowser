package com.dfm.stringtables.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dfm.stringtables.data.AppDb
import com.dfm.stringtables.data.EntryRow
import com.dfm.stringtables.data.FavHub
import com.dfm.stringtables.data.TableRow
import com.dfm.stringtables.data.fmt
import com.dfm.stringtables.ui.theme.Shape8
import kotlinx.coroutines.launch

/** 单张表的条目浏览。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TableScreen(
    tblId: Long,
    db: AppDb,
    favHub: FavHub,
    onOpenEntry: (Long) -> Unit,
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
    showSnack: (String, String?, (() -> Unit)?) -> Unit,
) {
    val s = MaterialTheme.colorScheme
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val favMap by favHub.map.collectAsState()
    var table by remember { mutableStateOf<TableRow?>(null) }
    var entriesTotal by remember { mutableIntStateOf(0) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(tblId) {
        table = db.tableById(tblId)
        entriesTotal = db.countEntriesOfTable(tblId)
        loaded = true
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
                title = {
                    Text(
                        table?.file ?: "加载中…",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
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
            if (table != null) {
                // 表信息：Namespace / 相对路径 / 条数
                Surface(
                    shape = Shape8,
                    color = s.surfaceContainerLow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                ) {
                    Row(
                        Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = Shape8,
                            color = s.primaryContainer,
                            modifier = Modifier.size(44.dp),
                        ) {
                            Column(
                                Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                            ) {
                                SymbolIcon(Sym.TABLE_FILE, size = 24.dp, tint = s.onPrimaryContainer)
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            if (table!!.ns.isNotBlank()) {
                                Text(
                                    table!!.ns,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = s.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(2.dp))
                            }
                            Text(
                                table!!.rel,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = s.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "${fmt(entriesTotal)} 条",
                            style = MaterialTheme.typography.titleSmall,
                            color = s.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            EntriesPane(
                placeholder = if (loaded) "在「${table?.file ?: ""}」中搜索" else "搜索…",
                scopeTotal = entriesTotal,
                search = { p, limit, off -> db.search(p, limit, off) },
                count = { db.searchCount(it) },
                tbl = tblId,
                onOpenEntry = onOpenEntry,
                isFav = isFav,
                onToggleFav = toggleFav,
                onCopyKey = copyKey,
            )
        }
    }
}
