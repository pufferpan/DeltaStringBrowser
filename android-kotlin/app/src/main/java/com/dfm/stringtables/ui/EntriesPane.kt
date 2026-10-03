package com.dfm.stringtables.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dfm.stringtables.data.EntryRow
import com.dfm.stringtables.data.Lbl
import com.dfm.stringtables.data.SearchParams
import com.dfm.stringtables.data.fmt
import com.dfm.stringtables.ui.theme.Shape12
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 单页取数大小。 */
private const val PAGE = 300

/**
 * 条目浏览面板（含范围内搜索、字段/标签过滤、自动分页加载、空态）。
 * 用于：业务域、单表、全局搜索、收藏以外的所有条目列表。
 *
 * @param placeholder 搜索框占位文案
 * @param scopeTotal  当前范围的总条目数（未过滤）
 * @param domain      固定业务域过滤（null = 不限制）
 * @param tbl         固定表过滤（null = 不限制）
 * @param search      (params, limit, offset) -> 条目
 * @param count       (params) -> 匹配数
 */
@Composable
fun EntriesPane(
    placeholder: String,
    scopeTotal: Int,
    search: suspend (SearchParams, Int, Int) -> List<EntryRow>,
    count: suspend (SearchParams) -> Int,
    onOpenEntry: (Long) -> Unit,
    isFav: (Long) -> Boolean,
    onToggleFav: (EntryRow) -> Unit,
    onCopyKey: (EntryRow) -> Unit,
    domain: Int? = null,
    tbl: Long? = null,
    modifier: Modifier = Modifier,
) {
    val s = MaterialTheme.colorScheme
    var query by rememberSaveable { mutableStateOf("") }
    var fieldSel by rememberSaveable { mutableStateOf<Int?>(null) }
    var tagSel by rememberSaveable { mutableStateOf<Int?>(null) }

    var rows by remember { mutableStateOf<List<EntryRow>>(emptyList()) }
    var matched by remember { mutableIntStateOf(0) }
    var loadingFirst by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var moreFailed by remember { mutableStateOf(false) }
    var allLoaded by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    fun params() = SearchParams(
        query = query.trim(),
        domain = domain,
        field = fieldSel,
        tagBit = tagSel,
        table = tbl,
    )

    // 查询/过滤/范围变化 → 防抖重查
    LaunchedEffect(query, fieldSel, tagSel, domain, tbl, scopeTotal) {
        loadingFirst = true
        delay(220)
        val p = params()
        if (p.query.isNotEmpty()) {
            matched = runCatching { count(p) }.getOrDefault(0)
        } else {
            matched = scopeTotal
        }
        val first = runCatching { search(p, PAGE, 0) }.getOrDefault(emptyList())
        rows = first
        allLoaded = first.size < PAGE || first.size >= (if (p.query.isEmpty()) scopeTotal else matched)
        if (first.size >= PAGE) {
            // 只有一页时已加载完
            val total = if (p.query.isEmpty()) scopeTotal else matched
            allLoaded = first.size >= total
        }
        loadingFirst = false
        listState.scrollToItem(0)
    }

    // 自动加载更多：滚动接近末尾
    val loadMore: () -> Unit = {
        if (!loadingMore && !allLoaded && !loadingFirst) {
            scope.launch {
                loadingMore = true
                moreFailed = false
                try {
                    val p = params()
                    val next = search(p, PAGE, rows.size)
                    rows = rows + next
                    val total = if (p.query.isEmpty()) scopeTotal else matched
                    allLoaded = rows.size >= total
                } catch (_: Exception) {
                    moreFailed = true
                } finally {
                    loadingMore = false
                }
            }
        }
    }

    LaunchedEffect(listState, rows, allLoaded, loadingFirst) {
        if (loadingFirst || allLoaded || loadingMore) return@LaunchedEffect
        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return@LaunchedEffect
        if (lastVisible >= rows.size - 6) loadMore()
    }

    Column(modifier.fillMaxSize().imePadding()) {
        // 搜索框 + 过滤（同一行：字段 / 标签两个下拉）
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(placeholder, maxLines = 1) },
                leadingIcon = {
                    SymbolIcon(Sym.SEARCH, size = 20.dp, tint = s.onSurfaceVariant)
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            SymbolIcon(Sym.CLOSE, size = 18.dp, tint = s.onSurfaceVariant,
                                contentDescription = "清除")
                        }
                    }
                },
                singleLine = true,
                shape = Shape12,
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = s.primary,
                    unfocusedBorderColor = s.outlineVariant,
                    cursorColor = s.primary,
                ),
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DropdownFilter(
                    label = "字段",
                    current = fieldSel,
                    options = Lbl.FIELDS,
                    onPick = { fieldSel = it },
                )
                DropdownFilter(
                    label = "标签",
                    current = tagSel,
                    options = Lbl.TAGS,
                    onPick = { tagSel = it },
                )
            }
        }

        // 结果计数
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (matched == 0 && loadingFirst.not())
                    "没有匹配内容"
                else
                    "匹配 ${fmt(matched)} 条 · 范围 ${fmt(scopeTotal)} 条",
                style = MaterialTheme.typography.bodySmall,
                color = s.onSurfaceVariant,
            )
            if (loadingFirst) {
                Spacer(Modifier.width(10.dp))
                CircularProgressIndicator(Modifier.height(12.dp).width(12.dp), strokeWidth = 2.dp,
                    color = s.primary)
            }
        }

        // 列表
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                loadingFirst -> Unit // 上方已有指示
                rows.isEmpty() -> {
                    val hasFilter = fieldSel != null || tagSel != null || query.isNotBlank()
                    Column(Modifier.fillMaxSize()) {
                        if (hasFilter) {
                            EmptyState(
                                glyph = Sym.EMPTY_SEARCH,
                                title = "没有匹配结果",
                                subtitle = "试试更短的关键词，或清除筛选条件。",
                                modifier = Modifier.weight(1f),
                                action = {
                                    TextButtonX("清除筛选") {
                                        query = ""
                                        fieldSel = null
                                        tagSel = null
                                    }
                                },
                            )
                        } else {
                            EmptyState(
                                glyph = Sym.EMPTY_SEARCH,
                                title = "这里还没有内容",
                                subtitle = "当前范围内没有收录文本。",
                            )
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(bottom = 16.dp),
                    ) {
                        items(count = rows.size, key = { rows[it].id }) { i ->
                            val row = rows[i]
                            EntryListRow(
                                entry = row,
                                isFav = isFav(row.id),
                                onOpen = { onOpenEntry(row.id) },
                                onToggleFav = { onToggleFav(row) },
                                onCopyKey = { onCopyKey(row) },
                            )
                            DividerH(Modifier.padding(horizontal = 16.dp))
                        }
                        if (!allLoaded || loadingMore || moreFailed) {
                            item(key = "__more__") {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 14.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (moreFailed) {
                                        TextButtonX("加载失败，点此重试") { loadMore() }
                                    } else {
                                        CircularProgressIndicator(
                                            Modifier.height(22.dp).width(22.dp),
                                            strokeWidth = 2.5.dp,
                                            color = s.primary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TextButtonX(text: String, onClick: () -> Unit) {
    val s = MaterialTheme.colorScheme
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = s.primary,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .clip(Shape12)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}
