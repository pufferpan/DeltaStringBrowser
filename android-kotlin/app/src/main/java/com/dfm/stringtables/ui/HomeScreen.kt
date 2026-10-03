package com.dfm.stringtables.ui

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.dfm.stringtables.data.DomainStat
import com.dfm.stringtables.data.SnapshotMeta
import com.dfm.stringtables.data.fmt
import com.dfm.stringtables.ui.theme.Shape8
import com.dfm.stringtables.ui.theme.Shape16

/**
 * 分类首页（“浏览” Tab）：总览卡 + 业务域列表。
 * 点击分类 -> DomainScreen；点击“全部条目” -> DomainScreen(-1)。
 */
@Composable
fun HomeScreen(
    meta: SnapshotMeta,
    onOpenDomain: (Int) -> Unit,
    bottomBar: @Composable (() -> Unit)? = null,
    snackbarHostState: SnackbarHostState,
) {
    val s = MaterialTheme.colorScheme
    Scaffold(
        containerColor = s.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = { bottomBar?.invoke() },
    ) { pad ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "brand") { BrandHeader(meta) }
            item(key = "hdr") {
                SectionHeader("业务分类", extra = "${meta.domains.size} 个业务域")
            }

            item(key = "dom_all") {
                val all = DomainStat(-1, "全部条目", meta.tables, meta.entries)
                DomainRowCard(all, glyph = Sym.DOM_ALL) { onOpenDomain(-1) }
            }
            items(count = meta.domains.size, key = { meta.domains[it].id }) { i ->
                val d = meta.domains[i]
                DomainRowCard(d, glyph = domainGlyph(d.id)) { onOpenDomain(d.id) }
            }
        }
    }
}

@Composable
private fun BrandHeader(meta: SnapshotMeta) {
    val s = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f)
                .padding(end = 12.dp),
        ) {
            Column {
                Text(
                    "三角洲行动 · StringTables",
                    style = MaterialTheme.typography.titleLarge,
                    color = s.onSurface,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "本地化文本归类浏览器",
                    style = MaterialTheme.typography.bodyMedium,
                    color = s.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DomainRowCard(
    stat: DomainStat,
    glyph: ImageVector,
    onClick: () -> Unit,
) {
    val s = MaterialTheme.colorScheme
    val subtitle = "${fmt(stat.tables)} 张表 · ${fmt(stat.entries)} 条文本"
    ClickableCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        containerColor = s.surfaceContainerLow,
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DomainTile(domain = stat.id)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stat.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = s.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = s.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    fmt(stat.entries),
                    style = MaterialTheme.typography.titleMedium,
                    color = s.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "条文本",
                    style = MaterialTheme.typography.labelSmall,
                    color = s.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(4.dp))
            SymbolIcon(Sym.CHEVRON, size = 20.dp, tint = s.onSurfaceVariant)
        }
    }
}
