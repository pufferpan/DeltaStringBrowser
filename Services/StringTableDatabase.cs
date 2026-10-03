using System.Collections.Concurrent;
using System.Text.Json;
using DeltaStringBrowser.Models;

namespace DeltaStringBrowser.Services;

/// <summary>
/// 加载 StringTables 目录下全部 .json,为每条文本归类(业务域/字段语义/主题标签),
/// 并建立"域 → 表 → 条目"索引。加载在后台线程执行。
/// </summary>
public sealed class StringTableDatabase
{
    public List<TableInfo> Tables { get; } = [];
    public List<string> Errors { get; } = [];
    public string RootPath { get; private set; } = "";
    public int TotalEntries { get; private set; }
    public double LoadSeconds { get; private set; }

    public bool IsLoaded => Tables.Count > 0;

    /// <summary>加载目录(不存在/为空时返回 false,不抛异常)。</summary>
    public bool Load(string root, Action<string>? progress = null)
    {
        Reset();
        if (string.IsNullOrWhiteSpace(root) || !Directory.Exists(root)) return false;

        RootPath = root;
        var files = Directory.EnumerateFiles(root, "*.json", SearchOption.AllDirectories).ToList();
        if (files.Count == 0) return false;

        var sw = System.Diagnostics.Stopwatch.StartNew();
        var tables = new ConcurrentBag<TableInfo>();
        var errors = new ConcurrentBag<string>();
        long done = 0;

        Parallel.ForEach(files, file =>
        {
            try
            {
                var table = ParseFile(file, root);
                if (table.Entries.Count > 0) tables.Add(table);
            }
            catch (Exception ex)
            {
                errors.Add($"{Path.GetFileName(file)}: {ex.Message}");
            }
            var d = Interlocked.Increment(ref done);
            if (progress != null && d % 60 == 0)
                progress($"正在解析 {d}/{files.Count} …");
        });

        sw.Stop();
        Errors.AddRange(errors);
        Tables.AddRange(tables);
        Tables.Sort((a, b) => string.CompareOrdinal(a.RelPath, b.RelPath));
        TotalEntries = Tables.Sum(t => t.Count);
        LoadSeconds = sw.Elapsed.TotalSeconds;
        return true;
    }

    /// <summary>某业务域下所有条目(跨表拼接,加载完成后构建一次)。</summary>
    public IEnumerable<StringEntry> EntriesOf(DomainKind domain) =>
        Tables.Where(t => t.Domain == domain).SelectMany(t => t.Entries);

    public void Reset()
    {
        Tables.Clear();
        Errors.Clear();
        RootPath = "";
        TotalEntries = 0;
        LoadSeconds = 0;
    }

    private static TableInfo ParseFile(string file, string root)
    {
        var rel = Path.GetRelativePath(root, file);
        using var doc = JsonDocument.Parse(File.ReadAllBytes(file), new JsonDocumentOptions
        {
            AllowTrailingCommas = true,
            CommentHandling = JsonCommentHandling.Skip,
        });

        var ns = "";
        if (doc.RootElement.TryGetProperty("TableNamespace", out var nsEl) && nsEl.ValueKind == JsonValueKind.String)
            ns = nsEl.GetString() ?? "";

        var domain = Categorizer.ClassifyTable(Path.GetDirectoryName(rel) ?? "", Path.GetFileName(rel));
        var table = new TableInfo
        {
            RelPath = rel,
            FileName = Path.GetFileName(rel),
            Namespace = ns,
            Domain = domain,
        };

        if (doc.RootElement.TryGetProperty("KeysToEntries", out var map) && map.ValueKind == JsonValueKind.Object)
        {
            foreach (var prop in map.EnumerateObject())
            {
                if (prop.Value.ValueKind != JsonValueKind.Object) continue;
                var name = "";
                if (prop.Value.TryGetProperty("Name", out var nameEl))
                {
                    name = nameEl.ValueKind == JsonValueKind.String ? nameEl.GetString() ?? "" : nameEl.ToString();
                }
                if (name.Length == 0) continue; // 空文本不收录

                long id = 0;
                if (prop.Value.TryGetProperty("Id", out var idEl) && idEl.ValueKind == JsonValueKind.Number)
                    idEl.TryGetInt64(out id);

                var field = Categorizer.ClassifyField(prop.Name);
                table.Entries.Add(new StringEntry
                {
                    Key = prop.Name,
                    Text = name,
                    Id = id,
                    Domain = domain,
                    Field = field,
                    Table = table,
                    TagMask = Categorizer.ClassifyTags(name, prop.Name, field),
                });
            }
        }

        // 键排序,便于浏览
        table.Entries.Sort((a, b) => string.CompareOrdinal(a.Key, b.Key));
        return table;
    }
}
