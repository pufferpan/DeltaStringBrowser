using System.Diagnostics;
using System.IO.Compression;
using System.Text;
using System.Text.Json;
using System.Text.Json.Nodes;

namespace DeltaStringBrowser.Services;

/// <summary>导入来源：一个文件夹，或者一个 ZIP 包。</summary>
public enum ImportKind
{
    Folder,
    Zip
}

/// <summary>导入来源的解析结果：真正交给 <see cref="StringTableDatabase.Load"/> 的目录。</summary>
public sealed record ImportSource(string OriginalPath, ImportKind Kind, string LoadRoot, bool FromCache, string Detail)
{
    /// <summary>给设置文件用的类型名。</summary>
    public string KindName => Kind == ImportKind.Zip ? "zip" : "folder";

    /// <summary>给界面用的简短描述。</summary>
    public string Describe() => Kind == ImportKind.Zip
        ? $"ZIP: {OriginalPath}"
        : $"文件夹: {OriginalPath}";
}

/// <summary>
/// ZIP 包导入：把 .zip 解压到本地缓存目录，然后把那个目录当作数据根交给数据库解析。
///
/// 为什么要解压而不是直接在内存里读 ZIP：`StringTableDatabase.Load` 走的是「递归枚举 *.json 再读文件」，
/// 导出目录里还可能有同名/同层结构，解压后复用同一条路径最省事，也不会因为压缩包内部结构不同而分叉出第二套解析逻辑。
///
/// 缓存策略：`%LOCALAPPDATA%\DeltaStringBrowser\imports\&lt;包名&gt;-&lt;长度&gt;-&lt;写入时间&gt;\`。
/// 戳记写进 `_import.json`（记录 zip 路径 / 长度 / 写入时间 / 文件数）；三者一致就直接复用，包变了才重新解压，
/// 同时把同一个包名的旧目录删掉（不会越攒越多）。
/// </summary>
public static class ZipBundle
{
    /// <summary>安全上限：解压后总大小不超过 4GB、条目数不超过 20 万（防止误选了一个巨大的压缩包把磁盘塞满）。</summary>
    private const long MaxTotalBytes = 4L * 1024 * 1024 * 1024;
    private const int MaxEntries = 200_000;

    private const string StampFileName = "_import.stamp";

    public static string ImportsRoot
    {
        get
        {
            var dir = Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
                "DeltaStringBrowser",
                "imports");
            return dir;
        }
    }

    /// <summary>
    /// 确保 ZIP 已经解压好，返回可以交给数据库加载的目录。
    /// 失败抛 <see cref="InvalidDataException"/>（调用方把 Message 直接显示给用户）。
    /// </summary>
    public static ImportSource ResolveZip(string zipPath, Action<string>? progress = null)
    {
        if (string.IsNullOrWhiteSpace(zipPath) || !File.Exists(zipPath))
            throw new InvalidDataException("压缩包不存在：" + zipPath);

        var info = new FileInfo(zipPath);
        var safeName = SafeName(Path.GetFileNameWithoutExtension(zipPath));
        var stampTag = $"{info.Length}-{info.LastWriteTimeUtc.Ticks:x}";
        var targetDir = Path.Combine(ImportsRoot, $"{safeName}-{stampTag}");

        // ① 缓存命中：目录在、戳记对得上、里面确实有 json
        if (TryReadStamp(targetDir, out var stamp)
            && stamp is not null
            && string.Equals(stamp.ZipPath, zipPath, StringComparison.OrdinalIgnoreCase)
            && stamp.Length == info.Length
            && stamp.WriteTicks == info.LastWriteTimeUtc.Ticks
            && Directory.Exists(targetDir)
            && CountJson(targetDir) > 0)
        {
            progress?.Invoke($"使用上次解压的缓存：{targetDir}");
            return new ImportSource(zipPath, ImportKind.Zip, EffectiveRoot(targetDir), true,
                $"{stamp.Files} 个 JSON（缓存）");
        }

        // ② 需要解压：先看条目数与解压后大小，超限直接拒绝
        progress?.Invoke($"正在检查压缩包：{Path.GetFileName(zipPath)}");
        long totalBytes;
        int fileCount;
        using (var archive = ZipFile.OpenRead(zipPath))
        {
            if (archive.Entries.Count > MaxEntries)
                throw new InvalidDataException($"压缩包里条目太多（{archive.Entries.Count:N0} 个），疑似选错了文件");

            totalBytes = 0;
            fileCount = 0;
            foreach (var e in archive.Entries)
            {
                if (string.IsNullOrEmpty(e.Name)) continue;   // 目录项
                totalBytes += e.Length;
                fileCount++;
                if (totalBytes > MaxTotalBytes)
                    throw new InvalidDataException($"压缩包解压后超过 {MaxTotalBytes / 1024 / 1024 / 1024} GB，已中止（不想把你的 C 盘塞满）");
            }

            if (fileCount == 0) throw new InvalidDataException("压缩包是空的");

            if (HasMojibakeName(archive))
                progress?.Invoke("提示：压缩包内文件名不是 UTF-8 编码，表名可能显示异常，建议先解压再用「导入文件夹」");
        }

        var sw = Stopwatch.StartNew();
        progress?.Invoke($"正在解压 {fileCount:N0} 个文件（{totalBytes / 1024.0 / 1024.0:F1} MB）到缓存…");

        // ③ 清理同一包的旧版本目录，再解压
        CleanupOldVersions(safeName, targetDir);
        Directory.CreateDirectory(targetDir);

        try
        {
            ZipFile.ExtractToDirectory(zipPath, targetDir, overwriteFiles: true);
        }
        catch (Exception ex)
        {
            // 解压失败就把半成品删掉，避免下次误当成有效缓存
            TryDeleteDirectory(targetDir);
            throw new InvalidDataException("解压失败：" + ex.Message, ex);
        }

        sw.Stop();

        var extracted = CountJson(targetDir);
        if (extracted == 0)
        {
            // 解压出来一个 json 都没有：删掉缓存并明确报错，别让用户面对一个"0 张表"的空界面
            TryDeleteDirectory(targetDir);
            throw new InvalidDataException(
                $"压缩包里没有找到 .json 文件（{fileCount:N0} 个文件都不是 JSON）——请确认导出的是 StringTables 目录");
        }

        var root = EffectiveRoot(targetDir);
        WriteStamp(targetDir, new Stamp
        {
            ZipPath = zipPath,
            Length = info.Length,
            WriteTicks = info.LastWriteTimeUtc.Ticks,
            Files = extracted,
            ExtractedUtc = DateTime.UtcNow.ToString("o"),
        });

        progress?.Invoke($"解压完成：{extracted:N0} 个 JSON → {targetDir}（{sw.Elapsed.TotalSeconds:F1}s）");
        return new ImportSource(zipPath, ImportKind.Zip, root, false,
            $"{extracted} 个 JSON / 解压 {sw.Elapsed.TotalSeconds:F1}s");
    }

    /// <summary>文件夹来源：直接用，不做任何搬运。</summary>
    public static ImportSource ResolveFolder(string folder)
    {
        if (string.IsNullOrWhiteSpace(folder) || !Directory.Exists(folder))
            throw new InvalidDataException("文件夹不存在：" + folder);

        var count = Directory.EnumerateFiles(folder, "*.json", SearchOption.AllDirectories).Take(1).Count();
        if (count == 0)
            throw new InvalidDataException("这个文件夹里没有找到任何 .json：" + folder);

        return new ImportSource(folder, ImportKind.Folder, folder, false, "本地文件夹");
    }

    /// <summary>
    /// 压缩包常见的「外面再套一层同名文件夹」：如果根目录没有 json、只有一个子目录，就往下钻一层，
    /// 这样表名（rel）起点更自然，也不会多出一层无意义的前缀。
    /// </summary>
    private static string EffectiveRoot(string dir)
    {
        var current = dir;
        for (int depth = 0; depth < 3; depth++)
        {
            if (Directory.EnumerateFiles(current, "*.json").Any()) return current;

            var subs = Directory.GetDirectories(current);
            if (subs.Length != 1) return current;
            current = subs[0];
        }
        return current;
    }

    /// <summary>目录里的 JSON 文件数（不含我们自己的戳记文件）。</summary>
    private static int CountJson(string dir)
        => Directory.EnumerateFiles(dir, "*.json", SearchOption.AllDirectories)
                    .Count(f => !Path.GetFileName(f).Equals(StampFileName, StringComparison.OrdinalIgnoreCase));

    /// <summary>把数据源（文件夹或 zip）解析成可加载的目录。</summary>
    public static ImportSource Resolve(string path, ImportKind kind, Action<string>? progress = null)
        => kind == ImportKind.Zip ? ResolveZip(path, progress) : ResolveFolder(path);

    /// <summary>按扩展名猜类型（.zip → ZIP，其它 → 文件夹）。</summary>
    public static ImportKind GuessKind(string path)
        => Path.GetExtension(path).Equals(".zip", StringComparison.OrdinalIgnoreCase) ? ImportKind.Zip : ImportKind.Folder;

    // ---------------------------------------------------------------- 戳记

    private sealed class Stamp
    {
        public string? ZipPath { get; set; }
        public long Length { get; set; }
        public long WriteTicks { get; set; }
        public int Files { get; set; }
        public string? ExtractedUtc { get; set; }
    }

    private static bool TryReadStamp(string dir, out Stamp? stamp)
    {
        stamp = null;
        try
        {
            var file = Path.Combine(dir, StampFileName);
            if (!File.Exists(file)) return false;
            stamp = JsonSerializer.Deserialize<Stamp>(File.ReadAllText(file));
            return stamp is not null;
        }
        catch
        {
            return false;
        }
    }

    private static void WriteStamp(string dir, Stamp stamp)
    {
        try
        {
            File.WriteAllText(
                Path.Combine(dir, StampFileName),
                JsonSerializer.Serialize(stamp, new JsonSerializerOptions { WriteIndented = true }));
        }
        catch
        {
            // 戳记写不上只是下次会重新解压一次，不影响使用
        }
    }

    private static void CleanupOldVersions(string safeName, string keepDir)
    {
        try
        {
            if (!Directory.Exists(ImportsRoot)) return;
            foreach (var dir in Directory.GetDirectories(ImportsRoot, safeName + "-*"))
            {
                if (string.Equals(dir, keepDir, StringComparison.OrdinalIgnoreCase)) continue;
                TryDeleteDirectory(dir);
            }
        }
        catch
        {
            // 清理失败不影响导入
        }
    }

    private static void TryDeleteDirectory(string dir)
    {
        try { if (Directory.Exists(dir)) Directory.Delete(dir, recursive: true); } catch { /* 忽略 */ }
    }

    /// <summary>压缩包里的文件名出现替换字符，说明解码用的编码不对（大概率是 GBK 名字）。</summary>
    private static bool HasMojibakeName(ZipArchive archive)
    {
        foreach (var e in archive.Entries)
        {
            if (e.FullName.Contains('\uFFFD')) return true;
        }
        return false;
    }

    /// <summary>目录名里不能出现的字符换成下划线，并截断过长名字。</summary>
    private static string SafeName(string raw)
    {
        var sb = new StringBuilder(raw.Length);
        foreach (var ch in raw)
            sb.Append(Array.IndexOf(Path.GetInvalidFileNameChars(), ch) >= 0 ? '_' : ch);

        var s = sb.ToString().Trim();
        if (s.Length == 0) s = "bundle";
        if (s.Length > 40) s = s[..40];
        return s;
    }

    /// <summary>缓存目录总占用（设置页/日志用）。</summary>
    public static long CacheBytes()
    {
        try
        {
            if (!Directory.Exists(ImportsRoot)) return 0;
            long sum = 0;
            foreach (var f in Directory.EnumerateFiles(ImportsRoot, "*", SearchOption.AllDirectories))
            {
                try { sum += new FileInfo(f).Length; } catch { /* 忽略单个文件 */ }
            }
            return sum;
        }
        catch
        {
            return 0;
        }
    }

    /// <summary>清空导入缓存（下次导入会重新解压）。</summary>
    public static void ClearCache()
    {
        try
        {
            if (Directory.Exists(ImportsRoot)) Directory.Delete(ImportsRoot, recursive: true);
        }
        catch
        {
            // 忽略
        }
    }
}
