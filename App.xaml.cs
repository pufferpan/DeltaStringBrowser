using DeltaStringBrowser.Services;
using Microsoft.UI.Xaml;

namespace DeltaStringBrowser;

public partial class App : Application
{
    private Window? _window;

    public App()
    {
        InitializeComponent();
    }

    protected override void OnLaunched(LaunchActivatedEventArgs args)
    {
        var cmdArgs = Environment.GetCommandLineArgs();

        // ── 导入自检（正式版与 Alpha 版都能用，不进入界面）──────────────────────
        //   DeltaStringBrowser.exe --importtest <文件夹或 .zip> [报告文件]
        // 走的是和界面「导入」完全同一条路径（ZIP 会真解压到缓存），输出表数/条数/样例后退出。
        int importTest = Array.IndexOf(cmdArgs, "--importtest");
        if (importTest >= 0 && importTest + 1 < cmdArgs.Length)
        {
            string source = cmdArgs[importTest + 1];
            string reportPath = importTest + 2 < cmdArgs.Length && !cmdArgs[importTest + 2].StartsWith("--")
                ? cmdArgs[importTest + 2]
                : Path.Combine(Path.GetTempPath(), "dsb_importtest.txt");

            string text;
            try
            {
                text = RunImportTest(source);
            }
            catch (Exception ex)
            {
                text = "导入自检失败：" + ex;
            }

            try { File.WriteAllText(reportPath, text); } catch { /* 报告写不出来也不影响退出 */ }
            Environment.Exit(text.StartsWith("导入自检失败") ? 2 : 0);
            return;
        }

#if ALPHA_EXPIRY
        // ── Alpha 定时过期版 · 命令行诊断 ──────────────────────────────────────
        // DeltaStringBrowser.exe --timecheck [报告文件] [--at ISO时间] [--drift 秒数]
        // 输出 NTP 校时结果后退出，退出码 0=通过 3=未通过（--at/--drift 只复算结论，不改变实际校验行为）。
        int timecheck = Array.IndexOf(cmdArgs, "--timecheck");
        if (timecheck >= 0)
        {
            string? reportPath = timecheck + 1 < cmdArgs.Length && !cmdArgs[timecheck + 1].StartsWith("--")
                ? cmdArgs[timecheck + 1]
                : null;

            DateTimeOffset? simulateAt = null;
            int atIndex = Array.IndexOf(cmdArgs, "--at");
            if (atIndex >= 0 && atIndex + 1 < cmdArgs.Length &&
                DateTimeOffset.TryParse(cmdArgs[atIndex + 1], out var parsedAt))
                simulateAt = parsedAt;

            TimeSpan? simulateDrift = null;
            int driftIndex = Array.IndexOf(cmdArgs, "--drift");
            if (driftIndex >= 0 && driftIndex + 1 < cmdArgs.Length &&
                double.TryParse(cmdArgs[driftIndex + 1], out var driftSeconds))
                simulateDrift = TimeSpan.FromSeconds(driftSeconds);

            // 丢到线程池再同步等待：UI 线程上直接等异步校时会死锁
            int code = Task.Run(() => TimeGuard.RunDiagnosticAsync(reportPath, simulateAt, simulateDrift))
                           .GetAwaiter().GetResult();
            Environment.Exit(code);
            return;
        }
#endif

        _window = new MainWindow();
        _window.Activate();
    }

    /// <summary>
    /// 导入自检：解析数据源 → 载入数据库 → 输出统计与样例（与界面「导入」共用 ZipBundle + StringTableDatabase）。
    /// </summary>
    private static string RunImportTest(string source)
    {
        var lines = new List<string>();
        var sw = System.Diagnostics.Stopwatch.StartNew();

        lines.Add("来源：" + source);
        lines.Add("存在：" + (File.Exists(source) || Directory.Exists(source)));

        var resolved = ZipBundle.Resolve(source, ZipBundle.GuessKind(source), msg => lines.Add("  " + msg));
        lines.Add($"类型：{resolved.KindName}  缓存命中：{resolved.FromCache}");
        lines.Add($"解压/加载目录：{resolved.LoadRoot}");
        lines.Add($"来源详情：{resolved.Detail}");

        var db = new StringTableDatabase();
        bool ok = db.Load(resolved.LoadRoot, msg => lines.Add("  " + msg));
        sw.Stop();

        lines.Add($"Load 返回：{ok}");
        lines.Add($"表数：{db.Tables.Count:N0}");
        lines.Add($"条目数：{db.TotalEntries:N0}");
        lines.Add($"解析耗时：{db.LoadSeconds:F2}s（含解压共 {sw.Elapsed.TotalSeconds:F2}s）");
        lines.Add($"跳过文件：{db.Errors.Count}");
        foreach (var e in db.Errors.Take(5)) lines.Add("  ! " + e);

        lines.Add("业务域分布：");
        foreach (var g in db.Tables.GroupBy(t => t.Domain).OrderByDescending(g => g.Sum(t => t.Count)).Take(8))
            lines.Add($"  {g.Key}: {g.Count()} 张表 / {g.Sum(t => t.Count):N0} 条");

        lines.Add("前 5 张表：");
        foreach (var t in db.Tables.Take(5))
            lines.Add($"  [{t.Domain}] {t.RelPath} — {t.Count:N0} 条");

        var sample = db.Tables.SelectMany(t => t.Entries).FirstOrDefault(e => !string.IsNullOrWhiteSpace(e.Text));
        lines.Add("样例条目：" + (sample is null ? "(无)" : $"{sample.Key} = {sample.Text}"));

        return string.Join(Environment.NewLine, lines) + Environment.NewLine;
    }
}
