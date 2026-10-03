package com.dfm.stringtables

import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.SideEffect
import com.dfm.stringtables.ui.AlphaTimeGate
import com.dfm.stringtables.ui.AppRoot
import com.dfm.stringtables.ui.theme.AppTheme
import com.dfm.stringtables.ui.theme.ThemeController
import com.dfm.stringtables.ui.theme.ThemeStore
import com.dfm.stringtables.ui.theme.isDarkTheme
import com.dfm.stringtables.ui.theme.resolveDarkTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 主题模式：启动时从 SharedPreferences 读取（默认「跟随系统」）
        ThemeStore.init(this)
        // 首帧之前先按已保存的模式设置系统栏（Compose 首帧后会按当前主题再设置一次）
        applySystemBarStyle(resolveDarkTheme(ThemeStore.read(), isSystemDark()))
        setContent {
            // 读取 Compose 状态：切换主题会触发这里与整个界面重组
            val mode = ThemeController.mode
            val dark = isDarkTheme(mode)
            // 系统栏图标随主题反色：浅色→深色图标，深色→浅色图标
            SideEffect { applySystemBarStyle(dark) }
            AppTheme(mode) {
                // Alpha 定时过期版：先做 NTP 网络时间校验，通过才进主界面
                AlphaTimeGate(onExit = { finish() }) {
                    AppRoot()
                }
            }
        }
    }

    /** 系统当前是否为深色外观（「跟随系统」模式下使用）。 */
    private fun isSystemDark(): Boolean =
        resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES

    /** 系统栏：透明 + 图标反色，全屏内容延伸到系统栏之下。 */
    private fun applySystemBarStyle(dark: Boolean) {
        val style = if (dark) {
            SystemBarStyle.dark(Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        }
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }
}
