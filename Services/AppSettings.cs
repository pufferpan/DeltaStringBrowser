using System.Text.Json;
using System.Text.Json.Nodes;

namespace DeltaStringBrowser.Services;

/// <summary>
/// 应用设置：`%LOCALAPPDATA%\DeltaStringBrowser\settings.json`。
///
/// 目前存两样东西：
/// · <c>Theme</c>：外观模式（`System` / `Light` / `Dark`，见 <see cref="ThemeService"/>）；
/// · <c>LastSource</c> + <c>LastSourceKind</c>：上次成功导入的数据源（文件夹或 ZIP 包），
///   下次启动直接用它，省得每次重新选。
///
/// 用 <see cref="JsonObject"/> 读写而不是反序列化成固定类型：不认识的键原样保留，
/// 以后加设置项不会把旧文件里别的字段抹掉。
/// </summary>
public static class AppSettings
{
    private const string KeyTheme = "Theme";
    private const string KeyLastSource = "LastSource";
    private const string KeyLastSourceKind = "LastSourceKind";

    private static readonly object Gate = new();
    private static JsonObject _data = new();

    /// <summary>主题模式字符串（"System" / "Light" / "Dark"）。</summary>
    public static string Theme { get; private set; } = "System";

    /// <summary>上次成功导入的数据源路径（文件夹或 .zip）。</summary>
    public static string? LastSource { get; private set; }

    /// <summary>数据源类型："folder" / "zip"。</summary>
    public static string? LastSourceKind { get; private set; }

    public static string SettingsPath
    {
        get
        {
            var dir = Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
                "DeltaStringBrowser");
            return Path.Combine(dir, "settings.json");
        }
    }

    /// <summary>从磁盘读一次（幂等；窗口构造时调用）。</summary>
    public static void Load()
    {
        lock (Gate)
        {
            _data = new JsonObject();
            try
            {
                if (File.Exists(SettingsPath))
                {
                    var parsed = JsonNode.Parse(File.ReadAllText(SettingsPath));
                    if (parsed is JsonObject obj) _data = obj;
                }
            }
            catch
            {
                // 配置坏了就当空的：主题回落跟随系统、数据源回落默认目录
                _data = new JsonObject();
            }

            Theme = Normalize(ReadString(KeyTheme)) ?? "System";
            LastSource = ReadString(KeyLastSource);
            LastSourceKind = ReadString(KeyLastSourceKind);
        }
    }

    /// <summary>保存主题模式。</summary>
    public static void SaveTheme(string theme)
    {
        lock (Gate)
        {
            Theme = Normalize(theme) ?? "System";
            _data[KeyTheme] = Theme;
            Write();
        }
    }

    /// <summary>记住上次成功导入的数据源。</summary>
    public static void SaveSource(string path, string kind)
    {
        lock (Gate)
        {
            LastSource = string.IsNullOrWhiteSpace(path) ? null : path;
            LastSourceKind = string.IsNullOrWhiteSpace(kind) ? null : kind;
            _data[KeyLastSource] = LastSource;
            _data[KeyLastSourceKind] = LastSourceKind;
            Write();
        }
    }

    /// <summary>忘掉数据源（例如它已经不在了）。</summary>
    public static void ClearSource()
    {
        lock (Gate)
        {
            LastSource = null;
            LastSourceKind = null;
            _data.Remove(KeyLastSource);
            _data.Remove(KeyLastSourceKind);
            Write();
        }
    }

    private static string? ReadString(string key)
    {
        // 大小写不敏感：老版本/手写文件里可能写成 theme
        foreach (var kv in _data)
        {
            if (!string.Equals(kv.Key, key, StringComparison.OrdinalIgnoreCase)) continue;
            if (kv.Value is null) return null;
            var s = kv.Value.ToString();
            return string.IsNullOrWhiteSpace(s) ? null : s;
        }
        return null;
    }

    private static string? Normalize(string? raw)
    {
        if (string.IsNullOrWhiteSpace(raw)) return null;
        return raw.Trim() switch
        {
            var s when s.Equals("light", StringComparison.OrdinalIgnoreCase) => "Light",
            var s when s.Equals("dark", StringComparison.OrdinalIgnoreCase) => "Dark",
            var s when s.Equals("system", StringComparison.OrdinalIgnoreCase) => "System",
            _ => "System"
        };
    }

    private static void Write()
    {
        try
        {
            Directory.CreateDirectory(Path.GetDirectoryName(SettingsPath)!);
            // 不转义中文/反斜杠：这个文件用户会打开看，路径保持原样比较好读
            var options = new JsonSerializerOptions
            {
                WriteIndented = true,
                Encoder = System.Text.Encodings.Web.JavaScriptEncoder.UnsafeRelaxedJsonEscaping,
            };
            File.WriteAllText(SettingsPath, _data.ToJsonString(options));
        }
        catch
        {
            // 写不进去不影响本次会话
        }
    }
}
