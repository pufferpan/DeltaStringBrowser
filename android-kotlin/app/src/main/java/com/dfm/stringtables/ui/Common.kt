package com.dfm.stringtables.ui

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import com.dfm.stringtables.data.EntryRow
import com.dfm.stringtables.data.FavRow
import com.dfm.stringtables.data.Lbl
import com.dfm.stringtables.ui.theme.Shape8
import com.dfm.stringtables.ui.theme.Shape12

/** 行 / 卡片轻微缩放反馈（M3 标准运动令牌：快速位移/缩放 300ms 强调曲线；按压取更快档）。 */
fun Modifier.pressScale(): Modifier = composed {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.975f else 1f,
        animationSpec = tween(150, easing = FastOutSlowInEasing),
        label = "pressScale",
    )
    Modifier
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                pressed = true
                waitForUpOrCancellation()
                pressed = false
            }
        }
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
}

/** 卡片：圆角 + 涟漪 + 缩放 + 可选长按。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ClickableCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    shape: Shape = Shape8,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    onLongClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val base = modifier
        .pressScale()
        .let { m ->
            if (onLongClick == null) m.clickable(onClick = onClick)
            else m.combinedClickable(onClick = onClick, onLongClick = onLongClick)
        }
        .clip(shape)
        .background(containerColor)
    Column(modifier = base) {
        Column(
            modifier = Modifier.padding(contentPadding),
        ) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                content()
            }
        }
    }
}

@Composable
fun FieldBadge(field: Int, modifier: Modifier = Modifier) {
    val s = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = Shape8,
        color = s.secondaryContainer,
        contentColor = s.onSecondaryContainer,
    ) {
        Text(
            text = Lbl.field(field),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            maxLines = 1,
        )
    }
}

@Composable
fun TagChip(tagIndex: Int, modifier: Modifier = Modifier) {
    val s = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = Shape8,
        color = s.tertiaryContainer,
        contentColor = s.onTertiaryContainer,
    ) {
        Text(
            text = Lbl.tag(tagIndex),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            maxLines = 1,
        )
    }
}

/** 空状态。 */
@Composable
fun EmptyState(
    glyph: ImageVector,
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    val s = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(92.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(s.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            SymbolIcon(glyph = glyph, size = 46.dp, tint = s.onSurfaceVariant)
        }
        Text(title, style = MaterialTheme.typography.titleMedium, color = s.onSurface)
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = s.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        action?.invoke()
    }
}

/** 下拉筛选按钮（“全部”/字段/标签）。current 为 null 表示全部。 */
@Composable
fun DropdownFilter(
    label: String,
    current: Int?,
    options: List<String>,
    onPick: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = MaterialTheme.colorScheme
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(
            onClick = { open = true },
            shape = Shape8,
            border = androidx.compose.foundation.BorderStroke(1.dp, s.outlineVariant),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
            modifier = Modifier.height(40.dp),
            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                containerColor = s.surface,
                contentColor = s.onSurface,
            ),
        ) {
            Text(
                text = current?.let { idx -> options.getOrNull(idx) ?: label } ?: label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(2.dp))
            SymbolIcon(Sym.EXPAND_MORE, size = 18.dp, tint = s.onSurfaceVariant)
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = Shape12,
            containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.96f),
        ) {
            DropdownMenuItem(
                text = { Text("全部") },
                onClick = { open = false; onPick(null) },
            )
            options.forEachIndexed { idx, name ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = { open = false; onPick(idx) },
                )
            }
        }
    }
}

/**
 * 毛玻璃：给所在对话框/弹层窗口开启系统级背景模糊（Android 12+，自动降级）。
 * 需在 AlertDialog 的内容槽（title/text 等）内调用。
 */
@Composable
fun DialogBackdropBlur(radiusDp: Dp = 14.dp) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val view = LocalView.current
    val radiusPx = with(LocalDensity.current) { radiusDp.toPx() }.toInt().coerceAtLeast(1)
    LaunchedEffect(Unit) {
        runCatching {
            val window = (view.parent as? DialogWindowProvider)?.window
            if (window != null) window.setBackgroundBlurRadius(radiusPx)
        }
    }
}

/** 半透明毛玻璃容器色（配合系统背景模糊时呈现磨砂感）。 */
val glassContainer: Color
    @Composable get() = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.86f)

/** 确认对话框（12dp 圆角，M3 组件，磨砂玻璃质感）。 */
@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    destructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val s = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = Shape12,
        containerColor = glassContainer,
        titleContentColor = s.onSurface,
        textContentColor = s.onSurfaceVariant,
        title = {
            Box {
                DialogBackdropBlur()
                Text(title)
            }
        },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    confirmLabel,
                    color = if (destructive) s.error else s.primary,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = s.onSurfaceVariant) }
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EntryListRow(
    entry: EntryRow,
    isFav: Boolean,
    onOpen: () -> Unit,
    onToggleFav: () -> Unit,
    onCopyKey: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = MaterialTheme.colorScheme
    val favColor by animateColorAsState(
        targetValue = if (isFav) s.primary else s.outline,
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "favColor",
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .pressScale()
            .combinedClickable(
                onClick = onOpen,
                onLongClick = { onCopyKey() },
            )
            .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = entry.key,
                style = MaterialTheme.typography.labelLarge,
                fontFamily = FontFamily.Monospace,
                color = s.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = entry.preview(140),
                style = MaterialTheme.typography.bodyMedium,
                color = s.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                FieldBadge(entry.field)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = entry.file,
                    style = MaterialTheme.typography.labelSmall,
                    color = s.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = onToggleFav, modifier = Modifier.size(40.dp)) {
            SymbolIcon(
                glyph = if (isFav) Sym.FAVORITE else Sym.FAV_OFF,
                size = 22.dp,
                tint = favColor,
                filled = isFav,
                contentDescription = if (isFav) "取消收藏" else "收藏",
            )
        }
    }
}

@Composable
fun FavListRow(
    fav: FavRow,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .pressScale()
            .clickable(onClick = onOpen)
            .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = fav.key,
                style = MaterialTheme.typography.labelLarge,
                fontFamily = FontFamily.Monospace,
                color = s.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = fav.preview(140),
                style = MaterialTheme.typography.bodyMedium,
                color = s.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                FieldBadge(fav.field)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = fav.file,
                    style = MaterialTheme.typography.labelSmall,
                    color = s.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = onRemove, modifier = Modifier.size(40.dp)) {
            SymbolIcon(
                Sym.DELETE,
                size = 20.dp,
                tint = s.onSurfaceVariant,
                contentDescription = "删除收藏",
            )
        }
    }
}

/** 区块标题（标题 + 可选计数说明，一行显示）。 */
@Composable
fun SectionHeader(title: String, extra: String? = null, modifier: Modifier = Modifier) {
    val s = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 16.dp, top = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = s.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (extra != null) {
            Spacer(Modifier.width(10.dp))
            Text(
                extra,
                style = MaterialTheme.typography.bodySmall,
                color = s.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/** 横向可滚动单选 Chip 行（非胶囊：8dp 圆角）。 */@Composable
fun <T> ChipSelectorRow(
    options: List<T>,
    selected: T?,
    labelOf: (T) -> String,
    onSelect: (T?) -> Unit,
    modifier: Modifier = Modifier,
    allLabel: String = "全部",
    iconOf: ((T) -> ImageVector)? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "__all__") {
            val sel = selected == null
            val s = MaterialTheme.colorScheme
            Surface(
                shape = Shape8,
                color = if (sel) s.secondaryContainer else s.surfaceContainer,
                modifier = Modifier.pressScale(),
            ) {
                Text(
                    text = allLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (sel) s.onSecondaryContainer else s.onSurfaceVariant,
                    modifier = Modifier
                        .clip(Shape8)
                        .clickable { onSelect(null) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    maxLines = 1,
                )
            }
        }
        items(options, key = { labelOf(it) }) { item ->
            val sel = selected == item
            val s = MaterialTheme.colorScheme
            Surface(
                shape = Shape8,
                color = if (sel) s.secondaryContainer else s.surfaceContainer,
                modifier = Modifier.pressScale(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(Shape8)
                        .clickable { onSelect(if (sel) null else item) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    iconOf?.let { g ->
                        SymbolIcon(
                            g(item), size = 16.dp,
                            tint = if (sel) s.onSecondaryContainer else s.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        text = labelOf(item),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (sel) s.onSecondaryContainer else s.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
fun DividerH(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
    )
}
