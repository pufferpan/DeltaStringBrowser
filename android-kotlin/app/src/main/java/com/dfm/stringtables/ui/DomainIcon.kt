package com.dfm.stringtables.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dfm.stringtables.ui.theme.Shape12

/** 业务域 → 图标。domain = -1 表示“全部”。 */
fun domainGlyph(domain: Int): ImageVector = when (domain) {
    -1 -> Sym.DOM_ALL
    0 -> Sym.DOM_UI
    1 -> Sym.DOM_LUA
    2 -> Sym.DOM_STORY
    3 -> Sym.DOM_ACTIVITY
    4 -> Sym.DOM_ACHIEVE
    5 -> Sym.DOM_MODE
    6 -> Sym.DOM_ITEM
    7 -> Sym.DOM_HERO
    8 -> Sym.DOM_QUEST
    9 -> Sym.DOM_MAIL
    10 -> Sym.DOM_GUIDE
    11 -> Sym.DOM_SYSTEM
    else -> Sym.DOM_GENERIC
}

private data class TilePair(val container: Color, val content: Color)

/** 取色：全部角色均来自 scheme（按域 id 在 4 组容器色间轮换）。 */
@Composable
private fun tilePairFor(domain: Int): TilePair {
    val s = androidx.compose.material3.MaterialTheme.colorScheme
    val pairs = listOf(
        TilePair(s.primaryContainer, s.onPrimaryContainer),
        TilePair(s.secondaryContainer, s.onSecondaryContainer),
        TilePair(s.tertiaryContainer, s.onTertiaryContainer),
        TilePair(s.surfaceContainerHighest, s.onSurfaceVariant),
    )
    val idx = if (domain == -1) 0 else domain % pairs.size
    return pairs[idx]
}

/** 业务域方形图标瓦片。 */
@Composable
fun DomainTile(
    domain: Int,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
) {
    val pair = tilePairFor(domain)
    Box(
        modifier = modifier
            .size(size)
            .background(pair.container, Shape12),
        contentAlignment = Alignment.Center,
    ) {
        SymbolIcon(glyph = domainGlyph(domain), size = size * 0.55f, tint = pair.content)
    }
}
