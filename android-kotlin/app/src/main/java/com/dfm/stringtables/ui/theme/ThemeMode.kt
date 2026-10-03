package com.dfm.stringtables.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 三态主题模式：
 * SYSTEM 跟随系统（默认）、LIGHT 强制浅色、DARK 强制深色。
 */
enum class ThemeMode(val label: String) {
    SYSTEM("跟随系统"),
    LIGHT("浅色"),
    DARK("深色");

    companion object {
        /** 存储值反解（未知/缺失一律回落默认「跟随系统」）。 */
        fun fromKey(key: String?): ThemeMode = entries.firstOrNull { it.name == key } ?: SYSTEM
    }
}

/**
 * 主题模式持久化：SharedPreferences。
 * 文件名 `stringtables_prefs`，key `theme_mode`（存 ThemeMode.name）。
 */
object ThemeStore {
    const val PREFS_NAME = "stringtables_prefs"
    const val KEY_THEME_MODE = "theme_mode"

    @Volatile
    private var prefs: SharedPreferences? = null

    /** 应用启动时调用一次（MainActivity.onCreate），读取上次选择。 */
    fun init(context: Context) {
        val sp = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = sp
        ThemeController.restore(ThemeMode.fromKey(sp.getString(KEY_THEME_MODE, null)))
    }

    /** 已保存的模式；未初始化时返回默认值「跟随系统」。 */
    fun read(): ThemeMode = ThemeMode.fromKey(prefs?.getString(KEY_THEME_MODE, null))

    /** 写入用户选择。 */
    fun write(mode: ThemeMode) {
        prefs?.edit()?.putString(KEY_THEME_MODE, mode.name)?.apply()
    }
}

/** 当前主题模式：以 Compose 状态暴露，供 AppTheme 与设置界面观察/修改。 */
object ThemeController {
    /** 默认「跟随系统」。 */
    private var state by mutableStateOf(ThemeMode.SYSTEM)

    /** 当前模式（读取即参与 Compose 订阅，切换后界面自动重组）。 */
    val mode: ThemeMode get() = state

    /** 启动时恢复已保存的值（不写盘）。 */
    internal fun restore(m: ThemeMode) {
        state = m
    }

    /** 用户切换：更新状态并持久化。 */
    fun setMode(m: ThemeMode) {
        if (state == m) return
        state = m
        ThemeStore.write(m)
    }
}
