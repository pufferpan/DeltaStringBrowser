using Microsoft.UI.Xaml;

namespace DeltaStringBrowser.Services;

/// <summary>主题模式：跟随系统 / 浅色 / 深色。</summary>
public enum ThemeMode
{
    System = 0,
    Light = 1,
    Dark = 2
}

/// <summary>
/// 主题：唯一事实来源 + 持久化 + 系统换肤监听。
///
/// · 模式存在 %LOCALAPPDATA%\DeltaStringBrowser\settings.json 的 Theme 字段（读写由 <see cref="AppSettings"/> 负责）；
/// · 窗口把 <c>RootGrid.RequestedTheme</c> 设成 <see cref="ToElementTheme"/> 的结果；
/// · System 模式下 WinUI 的 ElementTheme.Default 只在进程启动时取一次系统主题，
///   所以这里额外监听系统颜色变化，变化时重新解析成具体的浅/深并重设。
/// </summary>
public static class ThemeService
{
    private static bool _initialized;
    private static Windows.UI.ViewManagement.UISettings? _uiSettings;

    /// <summary>当前模式。</summary>
    public static ThemeMode Current { get; private set; } = ThemeMode.System;

    /// <summary>模式变化（用户手动切换，已写盘）。</summary>
    public static event Action<ThemeMode>? ModeChanged;

    /// <summary>系统浅色/深色变了（System 模式下有意义）。可能在后台线程触发。</summary>
    public static event Action? SystemThemeChanged;

    private sealed class SettingsFile
    {
        public string? Theme { get; set; }
    }

    /// <summary>从设置文件读一次并挂上系统主题监听（幂等）。窗口构造时调用。</summary>
    public static ThemeMode Initialize()
    {
        if (_initialized) return Current;
        _initialized = true;

        // 设置文件的读写统一交给 AppSettings（它还会顺带把上次导入的数据源读出来）
        AppSettings.Load();
        Current = Parse(AppSettings.Theme);

        HookSystemWatcher();
        return Current;
    }

    /// <summary>设置文件里的字符串 → 模式（认不出来按跟随系统）。</summary>
    public static ThemeMode Parse(string? raw) => raw?.Trim().ToLowerInvariant() switch
    {
        "light" => ThemeMode.Light,
        "dark" => ThemeMode.Dark,
        _ => ThemeMode.System
    };

    /// <summary>模式 → 设置文件字符串。</summary>
    public static string ToSettingValue(ThemeMode mode) => mode switch
    {
        ThemeMode.Light => "Light",
        ThemeMode.Dark => "Dark",
        _ => "System"
    };

    /// <summary>模式 → 中文名。</summary>
    public static string ToDisplayName(ThemeMode mode) => mode switch
    {
        ThemeMode.Light => "浅色",
        ThemeMode.Dark => "深色",
        _ => "跟随系统"
    };

    /// <summary>模式 → 根元素 RequestedTheme。</summary>
    public static ElementTheme ToElementTheme(ThemeMode mode) => mode switch
    {
        ThemeMode.Light => ElementTheme.Light,
        ThemeMode.Dark => ElementTheme.Dark,
        _ => ElementTheme.Default
    };

    /// <summary>切换模式（写盘 + 广播）。</summary>
    public static void SetMode(ThemeMode mode)
    {
        Initialize();
        Current = mode;
        AppSettings.SaveTheme(ToSettingValue(mode));

        try { ModeChanged?.Invoke(mode); }
        catch { /* 主题应用失败不影响使用 */ }
    }

    /// <summary>
    /// System 模式下系统到底是深色还是浅色（取系统背景色算感知亮度）。
    /// </summary>
    public static bool IsSystemDark()
    {
        try
        {
            var ui = _uiSettings ??= new Windows.UI.ViewManagement.UISettings();
            var bg = ui.GetColorValue(Windows.UI.ViewManagement.UIColorType.Background);
            int luma = (5 * bg.R + 9 * bg.G + 2 * bg.B) / 16;   // BT.601 近似
            return luma < 128;
        }
        catch
        {
            // 取不到就按应用级主题兜底
        }

        try { return Application.Current.RequestedTheme == ApplicationTheme.Dark; }
        catch { return true; }
    }

    /// <summary>把当前模式解析成具体主题（System 时给出确定的浅/深，保证系统换肤能立刻跟上）。</summary>
    public static ElementTheme ResolveFor(FrameworkElement root)
    {
        var mode = ToElementTheme(Current);
        if (mode != ElementTheme.Default) return mode;
        return IsSystemDark() ? ElementTheme.Dark : ElementTheme.Light;
    }

    private static void HookSystemWatcher()
    {
        try
        {
            _uiSettings = new Windows.UI.ViewManagement.UISettings();
            _uiSettings.ColorValuesChanged += (_, _) =>
            {
                if (Current != ThemeMode.System) return;
                SystemThemeChanged?.Invoke();
            };
        }
        catch
        {
            // 监听不上就算了：手动切换仍然可用
        }
    }
}
