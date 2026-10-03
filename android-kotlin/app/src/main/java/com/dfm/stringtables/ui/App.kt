package com.dfm.stringtables.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.dfm.stringtables.data.AppDb
import com.dfm.stringtables.data.FavHub
import com.dfm.stringtables.data.SnapshotMeta
import kotlinx.coroutines.launch

object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val FAVORITES = "favorites"
    const val SETTINGS = "settings"

    const val DOMAIN = "domain/{d}"
    fun domain(d: Int) = "domain/$d"

    const val TABLE = "table/{t}"
    fun table(t: Long) = "table/$t"

    const val DETAIL = "entry/{id}"
    fun entry(id: Long) = "entry/$id"

    const val FAV_DETAIL = "favEntry/{rowid}"
    fun favEntry(rowid: Long) = "favEntry/$rowid"
}

private val TAB_ROUTES = setOf(Routes.HOME, Routes.SEARCH, Routes.FAVORITES, Routes.SETTINGS)

private data class TabSpec(
    val route: String,
    val label: String,
    val glyphOn: ImageVector,
    val glyphOff: ImageVector,
)

private val TABS = listOf(
    TabSpec(Routes.HOME, "分类", Sym.BROWSE, Sym.BROWSE_OFF),
    TabSpec(Routes.SEARCH, "搜索", Sym.SEARCH, Sym.SEARCH_OFF),
    TabSpec(Routes.FAVORITES, "收藏", Sym.FAVORITE, Sym.FAV_OFF),
    TabSpec(Routes.SETTINGS, "设置", Sym.SETTINGS, Sym.SETTINGS_OFF),
)

/** 应用根：数据就绪后建立导航。 */
@Composable
fun AppRoot() {
    val context = LocalContext.current
    val db = remember { AppDb(context.applicationContext) }
    val favHub = remember { FavHub(db) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var meta by remember { mutableStateOf<SnapshotMeta?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var epoch by remember { mutableIntStateOf(0) }

    val showSnack: (String, String?, (() -> Unit)?) -> Unit = { text, actionLabel, onAction ->
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = text,
                actionLabel = actionLabel,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) onAction?.invoke()
        }
    }

    LaunchedEffect(epoch) {
        meta = null
        loadError = null
        meta = try {
            db.ensureLoaded().also { favHub.refresh() }
        } catch (e: Exception) {
            loadError = e.message ?: "未知错误"
            null
        }
    }

    val current = meta
    if (current == null) {
        LoadingScreen(error = loadError) { epoch++ }
    } else {
        AppNav(
            db = db,
            favHub = favHub,
            meta = current,
            snackbarHostState = snackbarHostState,
            showSnack = showSnack,
            onReloadDatabase = { epoch++ },
        )
    }
}

/** 启动/重建期加载屏。 */
@Composable
private fun LoadingScreen(error: String?, onRetry: () -> Unit) {
    val s = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (error == null) {
                CircularProgressIndicator(color = s.primary, strokeWidth = 3.dp)
                Spacer(Modifier.height(20.dp))
                Text(
                    "正在建立离线索引…",
                    style = MaterialTheme.typography.bodyLarge,
                    color = s.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "首次启动需拷贝内置快照，请稍候",
                    style = MaterialTheme.typography.bodySmall,
                    color = s.outline,
                )
            } else {
                Text("加载失败", style = MaterialTheme.typography.titleLarge, color = s.onSurface)
                Spacer(Modifier.height(8.dp))
                Text(
                    error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = s.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 32.dp),
                )
                Spacer(Modifier.height(16.dp))
                androidx.compose.material3.Button(onClick = onRetry, shape = com.dfm.stringtables.ui.theme.Shape12) {
                    SymbolIcon(Sym.REFRESH, size = 18.dp)
                    Spacer(Modifier.width(6.dp))
                    Text("重试")
                }
            }
        }
    }
}

/** 底部导航（M3 NavigationBar：磨砂半透明 + 顶部发丝线，无胶囊指示器）。 */
@Composable
private fun BottomBar(currentRoute: String?, onSelect: (String) -> Unit) {
    val s = MaterialTheme.colorScheme
    Column {
        androidx.compose.material3.HorizontalDivider(
            color = s.outlineVariant.copy(alpha = 0.8f),
            thickness = androidx.compose.ui.unit.Dp.Hairline,
        )
        NavigationBar(
            containerColor = s.surfaceContainer.copy(alpha = 0.92f),
            tonalElevation = 0.dp,
        ) {
            TABS.forEach { tab ->
                val selected = currentRoute == tab.route
                NavigationBarItem(
                    selected = selected,
                    onClick = { onSelect(tab.route) },
                    icon = {
                        SymbolIcon(
                            glyph = if (selected) tab.glyphOn else tab.glyphOff,
                            filled = selected,
                            size = 24.dp,
                        )
                    },
                    label = {
                        Text(tab.label, style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = s.primary,
                        selectedTextColor = s.primary,
                        indicatorColor = Color.Transparent,
                        unselectedIconColor = s.onSurfaceVariant,
                        unselectedTextColor = s.onSurfaceVariant,
                    ),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppNav(
    db: AppDb,
    favHub: FavHub,
    meta: SnapshotMeta,
    snackbarHostState: SnackbarHostState,
    showSnack: (String, String?, (() -> Unit)?) -> Unit,
    onReloadDatabase: () -> Unit,
) {
    val nav: NavHostController = rememberNavController()
    val backStackEntry by nav.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route
    val isTabRoot = route in TAB_ROUTES

    val scope = rememberCoroutineScope()
    val bottomBar: @Composable (() -> Unit)? = if (isTabRoot) {
        {
            BottomBar(currentRoute = route) { target ->
                if (target != route) {
                    nav.navigate(target) {
                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            }
        }
    } else {
        null
    }

    // 转场：前进 slide+淡入；返回严格反向播放（系统返回键/手势一致）。
    // 按 MotionScheme.standard() 令牌取值：位移 300ms、透明度 260ms，FastOutSlowIn。
    val tweenSpec = tween<IntOffset>(300, easing = FastOutSlowInEasing)
    val fadeSpec = tween<Float>(260, easing = FastOutSlowInEasing)

    Box(Modifier.fillMaxSize()) {
        NavHost(
            navController = nav,
            startDestination = Routes.HOME,
            modifier = Modifier.fillMaxSize(),
            enterTransition = {
                if (targetState.destination.route in TAB_ROUTES) fadeIn(fadeSpec)
                else slideInHorizontally(tweenSpec) { it / 3 } + fadeIn(fadeSpec)
            },
            exitTransition = {
                if (targetState.destination.route in TAB_ROUTES) fadeOut(fadeSpec)
                else slideOutHorizontally(tweenSpec) { -it / 5 } + fadeOut(fadeSpec)
            },
            popEnterTransition = {
                slideInHorizontally(tweenSpec) { -it / 5 } + fadeIn(fadeSpec)
            },
            popExitTransition = {
                slideOutHorizontally(tweenSpec) { it / 3 } + fadeOut(fadeSpec)
            },
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    meta = meta,
                    onOpenDomain = { d -> nav.navigate(Routes.domain(d)) },
                    bottomBar = bottomBar,
                    snackbarHostState = snackbarHostState,
                )
            }
            composable(Routes.SEARCH) {
                SearchScreen(
                    meta = meta,
                    db = db,
                    favHub = favHub,
                    onOpenEntry = { id -> nav.navigate(Routes.entry(id)) },
                    snackbarHostState = snackbarHostState,
                    showSnack = showSnack,
                    bottomBar = bottomBar,
                )
            }
            composable(Routes.FAVORITES) {
                FavoritesScreen(
                    db = db,
                    favHub = favHub,
                    onOpenFavorite = { rowid -> nav.navigate(Routes.favEntry(rowid)) },
                    snackbarHostState = snackbarHostState,
                    showSnack = showSnack,
                    bottomBar = bottomBar,
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    meta = meta,
                    db = db,
                    snackbarHostState = snackbarHostState,
                    showSnack = showSnack,
                    onReloadDatabase = onReloadDatabase,
                    bottomBar = bottomBar,
                )
            }
            composable(
                route = Routes.DOMAIN,
                arguments = listOf(navArgument("d") { type = NavType.IntType }),
            ) { entry ->
                val d = entry.arguments?.getInt("d") ?: -1
                DomainScreen(
                    domain = d,
                    meta = meta,
                    db = db,
                    favHub = favHub,
                    onOpenTable = { t -> nav.navigate(Routes.table(t)) },
                    onOpenEntry = { id -> nav.navigate(Routes.entry(id)) },
                    onBack = { nav.popBackStack() },
                    snackbarHostState = snackbarHostState,
                    showSnack = showSnack,
                )
            }
            composable(
                route = Routes.TABLE,
                arguments = listOf(navArgument("t") { type = NavType.LongType }),
            ) { entry ->
                val t = entry.arguments?.getLong("t") ?: 0L
                TableScreen(
                    tblId = t,
                    db = db,
                    favHub = favHub,
                    onOpenEntry = { id -> nav.navigate(Routes.entry(id)) },
                    onBack = { nav.popBackStack() },
                    snackbarHostState = snackbarHostState,
                    showSnack = showSnack,
                )
            }
            composable(
                route = Routes.DETAIL,
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) { entry ->
                val id = entry.arguments?.getLong("id") ?: 0L
                EntryDetailScreen(
                    load = { db.entryById(id) },
                    fromFavDetail = false,
                    favHub = favHub,
                    onBack = { nav.popBackStack() },
                    onOpenTable = { t -> nav.navigate(Routes.table(t)) },
                    snackbarHostState = snackbarHostState,
                    showSnack = showSnack,
                )
            }
            composable(
                route = Routes.FAV_DETAIL,
                arguments = listOf(navArgument("rowid") { type = NavType.LongType }),
            ) { entry ->
                val rowid = entry.arguments?.getLong("rowid") ?: 0L
                EntryDetailScreen(
                    load = { db.favoriteByRowid(rowid)?.let { favToEntry(it) } },
                    fromFavDetail = true,
                    favHub = favHub,
                    onBack = { nav.popBackStack() },
                    onOpenTable = null,
                    snackbarHostState = snackbarHostState,
                    showSnack = showSnack,
                )
            }
        }

        // 顶层 Snackbar（浮在底栏之上）
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = if (isTabRoot) 84.dp else 12.dp),
        )
    }
}
