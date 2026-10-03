package com.dfm.stringtables.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dfm.stringtables.data.AppDb
import com.dfm.stringtables.data.EntryRow
import com.dfm.stringtables.data.FavHub
import com.dfm.stringtables.data.FavRow
import com.dfm.stringtables.data.fmt
import kotlinx.coroutines.launch

/** 收藏（第三个 Tab）：用户保存的条目，持久化在设备上。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    db: AppDb,
    favHub: FavHub,
    onOpenFavorite: (Long) -> Unit,
    snackbarHostState: SnackbarHostState,
    showSnack: (String, String?, (() -> Unit)?) -> Unit,
    bottomBar: @Composable (() -> Unit)? = null,
) {
    val s = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val favMap by favHub.map.collectAsState()
    var favs by remember { mutableStateOf<List<FavRow>?>(null) }
    var removing by remember { mutableStateOf<FavRow?>(null) }
    var askClear by remember { mutableStateOf(false) }

    LaunchedEffect(favMap) {
        favs = db.listFavorites()
    }

    fun remove(row: FavRow, undo: Boolean = true) {
        scope.launch {
            favHub.removeByRowid(row.rowid)
            showSnack(
                "已删除收藏",
                if (undo) "撤销" else null,
                if (undo) {
                    {
                        scope.launch {
                            db.addFavorite(favToEntry(row))
                            favHub.refresh()
                            showSnack("已恢复收藏", null, null)
                        }
                    }
                } else {
                    null
                },
            )
        }
    }

    Scaffold(
        containerColor = s.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = { bottomBar?.invoke() },
        topBar = {
            TopAppBar(
                title = {
                    val n = favs?.size
                    Text(
                        if (n == null) "收藏" else "收藏（${fmt(n)}）",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                actions = {
                    if (!favs.isNullOrEmpty()) {
                        androidx.compose.material3.TextButton(onClick = { askClear = true }) {
                            Text(
                                "清空",
                                color = s.error,
                                style = MaterialTheme.typography.labelLarge,
                            )
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
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad),
        ) {
            val list = favs
            when {
                list == null -> Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(72.dp))
                    CircularProgressIndicator(color = s.primary)
                }

                list.isEmpty() -> EmptyState(
                    glyph = Sym.EMPTY_FAV,
                    title = "还没有收藏",
                    subtitle = "在条目列表或详情页点 ♥，即可把常用文本\n保存到这台设备。",
                    modifier = Modifier.fillMaxWidth(),
                )

                else -> LazyColumn(
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    items(list, key = { it.rowid }) { row ->
                        FavListRow(
                            fav = row,
                            onOpen = { onOpenFavorite(row.rowid) },
                            onRemove = { removing = row },
                        )
                        DividerH(Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
        }
    }

    // 单条删除确认
    removing?.let { row ->
        ConfirmDialog(
            title = "删除收藏？",
            text = "将从本机移除：\n${row.key}",
            confirmLabel = "删除",
            destructive = true,
            onConfirm = {
                removing = null
                remove(row, undo = true)
            },
            onDismiss = { removing = null },
        )
    }

    // 清空确认
    if (askClear) {
        ConfirmDialog(
            title = "清空全部收藏？",
            text = "将删除全部 ${favs?.size ?: 0} 条收藏，此操作不可恢复。",
            confirmLabel = "全部删除",
            destructive = true,
            onConfirm = {
                askClear = false
                scope.launch {
                    favHub.clear()
                    showSnack("已清空收藏", null, null)
                }
            },
            onDismiss = { askClear = false },
        )
    }
}

/** 收藏快照 -> 条目（用于“撤销删除”回写）。 */
internal fun favToEntry(f: FavRow): EntryRow = EntryRow(
    id = f.entryId,
    tbl = 0L,
    key = f.key,
    text = f.text,
    locid = f.locid,
    domain = f.domain,
    field = f.field,
    tags = f.tags,
    file = f.file,
    ns = f.ns,
    rel = f.rel,
)
