package com.dfm.stringtables.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dfm.stringtables.data.AppDb
import com.dfm.stringtables.data.EntryRow
import com.dfm.stringtables.data.FavHub
import com.dfm.stringtables.data.SnapshotMeta
import com.dfm.stringtables.data.fmt
import kotlinx.coroutines.launch

/** 全局搜索（第二个 Tab）：域筛选 + 条目面板。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    meta: SnapshotMeta,
    db: AppDb,
    favHub: FavHub,
    onOpenEntry: (Long) -> Unit,
    snackbarHostState: SnackbarHostState,
    showSnack: (String, String?, (() -> Unit)?) -> Unit,
    bottomBar: @Composable (() -> Unit)? = null,
) {
    val s = MaterialTheme.colorScheme
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val favMap by favHub.map.collectAsState()
    var domainSel by rememberSaveable { mutableStateOf<Int?>(null) }

    val domains = meta.domains
    val totalEntries = meta.entries
    val scopeTotal = domainSel?.let { d -> domains.firstOrNull { it.id == d }?.entries ?: 0 }
        ?: totalEntries

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
        bottomBar = { bottomBar?.invoke() },
        topBar = {
            TopAppBar(
                title = {
                    Text("全局搜索", maxLines = 1, overflow = TextOverflow.Ellipsis)
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
            Text(
                "Key 或文本子串 · 支持业务域 / 字段 / 主题标签叠加筛选",
                style = MaterialTheme.typography.bodySmall,
                color = s.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(6.dp))
            ChipSelectorRow(
                options = domains,
                selected = domainSel?.let { d -> domains.firstOrNull { it.id == d } },
                labelOf = { it.label },
                iconOf = { domainGlyph(it.id) },
                onSelect = { domainSel = it?.id },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            EntriesPane(
                placeholder = "搜索全部内容（Key / 文本）",
                scopeTotal = scopeTotal,
                domain = domainSel,
                search = { p, limit, off -> db.search(p, limit, off) },
                count = { db.searchCount(it) },
                onOpenEntry = onOpenEntry,
                isFav = isFav,
                onToggleFav = toggleFav,
                onCopyKey = copyKey,
            )
        }
    }
}
