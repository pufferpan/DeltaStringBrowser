// 图标：androidx Material Icons —— 原生 ImageVector（Rounded 风格），
// 由 material-icons-core / material-icons-extended 库直接编译进包，
// 不依赖任何字体文件，杜绝“图标不显示”问题。
package com.dfm.stringtables.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.HeartBroken
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Report
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.TipsAndUpdates
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 全部图标集中在此（原生 ImageVector），便于统一替换。 */
object Sym {
    // —— 主导航（选中/未选中用 tint + 字形区分）——
    val BROWSE: ImageVector = Icons.Rounded.Category
    val BROWSE_OFF: ImageVector = Icons.Rounded.Category
    val SEARCH: ImageVector = Icons.Rounded.Search
    val SEARCH_OFF: ImageVector = Icons.Rounded.Search
    val FAVORITE: ImageVector = Icons.Rounded.Favorite
    val FAV_OFF: ImageVector = Icons.Rounded.FavoriteBorder
    val SETTINGS: ImageVector = Icons.Rounded.Settings
    val SETTINGS_OFF: ImageVector = Icons.Rounded.Settings

    // —— 通用操作 ——
    val BACK: ImageVector = Icons.Rounded.ArrowBack
    val CHEVRON: ImageVector = Icons.Rounded.ChevronRight
    val COPY: ImageVector = Icons.Rounded.ContentCopy
    val SHARE: ImageVector = Icons.Rounded.Share
    val DELETE: ImageVector = Icons.Rounded.Delete
    val CLEAR_ALL: ImageVector = Icons.Rounded.DeleteSweep
    val CLOSE: ImageVector = Icons.Rounded.Close
    val EXPAND_MORE: ImageVector = Icons.Rounded.ExpandMore
    val INFO: ImageVector = Icons.Rounded.Info
    val REFRESH: ImageVector = Icons.Rounded.Refresh
    val CHECK: ImageVector = Icons.Rounded.Check
    val EMPTY_SEARCH: ImageVector = Icons.Rounded.SearchOff
    val EMPTY_FAV: ImageVector = Icons.Rounded.HeartBroken
    val KEY: ImageVector = Icons.Rounded.VpnKey
    val PERSON: ImageVector = Icons.Rounded.Person

    // —— 业务域 ——
    val DOM_ALL: ImageVector = Icons.Rounded.Storage
    val DOM_UI: ImageVector = Icons.Rounded.Smartphone
    val DOM_LUA: ImageVector = Icons.Rounded.Code
    val DOM_STORY: ImageVector = Icons.Rounded.Subtitles
    val DOM_ACTIVITY: ImageVector = Icons.Rounded.Event
    val DOM_ACHIEVE: ImageVector = Icons.Rounded.EmojiEvents
    val DOM_MODE: ImageVector = Icons.Rounded.SportsEsports
    val DOM_ITEM: ImageVector = Icons.Rounded.ShoppingBag
    val DOM_HERO: ImageVector = Icons.Rounded.Face
    val DOM_QUEST: ImageVector = Icons.Rounded.TaskAlt
    val DOM_MAIL: ImageVector = Icons.Rounded.Email
    val DOM_GUIDE: ImageVector = Icons.Rounded.TipsAndUpdates
    val DOM_SYSTEM: ImageVector = Icons.Rounded.Report
    val DOM_GENERIC: ImageVector = Icons.Rounded.Folder

    // —— 其他 ——
    val TABLE_FILE: ImageVector = Icons.Rounded.TableChart
    val FILTER: ImageVector = Icons.Rounded.FilterList
    val LANG: ImageVector = Icons.Rounded.Translate
    val PALETTE: ImageVector = Icons.Rounded.Palette
    val FOLDER_OPEN: ImageVector = Icons.Rounded.FolderOpen
    val CALENDAR: ImageVector = Icons.Rounded.CalendarMonth
}

/**
 * 绘制原生 Material Icons（ImageVector，Rounded 风格）。
 *
 * @param glyph Sym 中的任意图标
 * @param size 显示尺寸
 * @param tint 着色（默认继承 LocalContentColor）
 * @param filled 保留参数（ImageVector 无实心/空心之分，兼容旧调用；外观由字形常量决定）
 * @param contentDescription 无障碍描述；null 表示纯装饰
 */
@Composable
fun SymbolIcon(
    glyph: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color = LocalContentColor.current,
    filled: Boolean = true,
    contentDescription: String? = null,
) {
    Icon(
        imageVector = glyph,
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier.size(size),
    )
}
