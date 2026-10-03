using System.Collections.ObjectModel;
using Windows.ApplicationModel.DataTransfer;
using Windows.Storage.Pickers;
using DeltaStringBrowser.Models;
using DeltaStringBrowser.Services;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Input;
using Microsoft.UI.Dispatching;
using WinRT.Interop;

namespace DeltaStringBrowser;

public sealed partial class MainWindow : Window
{
    /// <summary>开发机默认目录。安装版优先使用安装目录自带的 Data\StringTables（安装包内置的数据）。</summary>
    private const string DevDefaultRoot = @"I:\新建文件夹 (19)\Exports\DeltaForce\Content\StringTables";
    private const int MaxShown = 60000; // 单次列表渲染上限, 用搜索/选表缩小范围

    private readonly StringTableDatabase _db = new();
    private readonly ObservableCollection<DomainItemVm> _domainItems = [];
    private readonly ObservableCollection<TableItemVm> _tableItems = [];
    private readonly Dictionary<DomainKind, int> _domainCounts = [];
    private readonly DispatcherQueueTimer _searchTimer;

    private List<StringEntry> _scope = [];   // 当前范围(域或表)的全部条目
    private string _scopeName = "";
    private DomainKind? _selectedDomain;
    private TableInfo? _selectedTable;
    private bool _busy;

    /// <summary>当前数据来源（文件夹或 ZIP）；「重新加载」按它重载。</summary>
    private ImportSource? _currentSource;

    public MainWindow()
    {
        InitializeComponent();
        try { AppWindow.Resize(new Windows.Graphics.SizeInt32(1520, 880)); } catch { /* 忽略 */ }
        ApplyWindowIcon();

        // 主题：先读设置落到 RootGrid 上，再订阅「手动切换」与「系统换肤」两个事件。
        // 必须在 EnsureAlphaOverlay() 之前 —— 校验覆盖层的配色是按当时的实际主题取的。
        ThemeService.Initialize();
        ThemeService.ModeChanged += _ => ApplyTheme();
        ThemeService.SystemThemeChanged += () => DispatcherQueue.TryEnqueue(ApplyTheme);
        ApplyTheme();

        _searchTimer = DispatcherQueue.CreateTimer();
        _searchTimer.Interval = TimeSpan.FromMilliseconds(220);
        _searchTimer.IsRepeating = false;
        _searchTimer.Tick += (_, _) => ApplyFilter();

        DomainList.ItemsSource = _domainItems;
        TableList.ItemsSource = _tableItems;
        FillFilterCombos();

#if ALPHA_EXPIRY
        // Alpha 定时过期版：先铺一层覆盖层（时间校验通过前界面不可用），并标注版本与过期日。
        // 校验复用本窗口的覆盖层，**不额外创建窗口**——先建后关一个窗口会让主窗口渲染后原生崩溃。
        Title = "三角洲行动 · StringTables 归类浏览器（Alpha测试版 · 2026-09-24 过期）";
        EnsureAlphaOverlay();
        RootGrid.Loaded += RootGrid_Loaded;
#endif

        _ = LoadSourceAsync(ResolveStartupSource());
    }

    /// <summary>
    /// 默认数据目录：① 安装目录旁的 Data\StringTables（安装包内置的数据）→ ② 开发机默认路径。
    /// 用户随时可以点「打开文件夹」换成自己的导出目录。
    /// </summary>
    private static string ResolveDefaultRoot()
    {
        try
        {
            var bundled = Path.Combine(AppContext.BaseDirectory, "Data", "StringTables");
            if (Directory.Exists(bundled)) return bundled;
        }
        catch
        {
            // 探测失败回落开发机路径
        }
        return DevDefaultRoot;
    }

    // ── Alpha 定时过期版 · 启动校验闸门 + 运行期时间看门狗 ────────────────────
#if ALPHA_EXPIRY

    private Grid? _alphaOverlay;
    private TextBlock? _alphaHeadline;
    private TextBlock? _alphaMessage;
    private TextBlock? _alphaDetail;
    private ProgressRing? _alphaRing;
    private StackPanel? _alphaButtons;
    private TaskCompletionSource<bool>? _alphaDecision;
    private DispatcherQueueTimer? _alphaWatchdog;
    private int _alphaWatchdogFailures;

    /// <summary>当前是否深色（覆盖层配色跟随界面主题）。</summary>
    private static bool IsDarkTheme(FrameworkElement root)
    {
        if (root.RequestedTheme == ElementTheme.Dark) return true;
        if (root.RequestedTheme == ElementTheme.Light) return false;
        return Application.Current.RequestedTheme == ApplicationTheme.Dark;
    }

    /// <summary>创建铺满窗口的校验覆盖层（位于所有内容之上）。</summary>
    private void EnsureAlphaOverlay()
    {
        if (_alphaOverlay is not null) return;

        bool dark = IsDarkTheme(RootGrid);
        var foreground = new Microsoft.UI.Xaml.Media.SolidColorBrush(
            dark ? Microsoft.UI.ColorHelper.FromArgb(0xFF, 0xF2, 0xF2, 0xF7)
                 : Microsoft.UI.ColorHelper.FromArgb(0xFF, 0x1D, 0x1D, 0x1F));
        var secondary = new Microsoft.UI.Xaml.Media.SolidColorBrush(
            dark ? Microsoft.UI.ColorHelper.FromArgb(0xFF, 0xA9, 0xA9, 0xB2)
                 : Microsoft.UI.ColorHelper.FromArgb(0xFF, 0x6E, 0x6E, 0x73));
        var warn = new Microsoft.UI.Xaml.Media.SolidColorBrush(
            Microsoft.UI.ColorHelper.FromArgb(0xFF, 0xD9, 0x6A, 0x0B));

        _alphaHeadline = new TextBlock
        {
            Text = "正在校验版本有效期…",
            FontSize = 30,
            FontWeight = Microsoft.UI.Text.FontWeights.SemiBold,
            Foreground = foreground,
            HorizontalAlignment = HorizontalAlignment.Center,
            TextAlignment = TextAlignment.Center,
            TextWrapping = TextWrapping.Wrap,
        };
        _alphaRing = new ProgressRing
        {
            IsActive = true,
            Width = 44,
            Height = 44,
            HorizontalAlignment = HorizontalAlignment.Center,
            Foreground = foreground,
        };
        _alphaMessage = new TextBlock
        {
            FontSize = 19,
            LineHeight = 34,
            Foreground = warn,
            TextWrapping = TextWrapping.Wrap,
            TextAlignment = TextAlignment.Center,
            HorizontalAlignment = HorizontalAlignment.Center,
            Visibility = Visibility.Collapsed,
        };
        _alphaDetail = new TextBlock
        {
            Text = "通过 NTP 服务器获取网络时间（不信任本机时钟）",
            FontSize = 13,
            Foreground = secondary,
            TextWrapping = TextWrapping.Wrap,
            TextAlignment = TextAlignment.Center,
            HorizontalAlignment = HorizontalAlignment.Center,
        };

        var retryButton = new Button { Content = "重新校验" };
        retryButton.Click += (_, _) => _alphaDecision?.TrySetResult(true);
        var exitButton = new Button { Content = "退出程序" };
        exitButton.Click += (_, _) => _alphaDecision?.TrySetResult(false);

        _alphaButtons = new StackPanel
        {
            Orientation = Orientation.Horizontal,
            Spacing = 12,
            HorizontalAlignment = HorizontalAlignment.Center,
            Visibility = Visibility.Collapsed,
        };
        _alphaButtons.Children.Add(retryButton);
        _alphaButtons.Children.Add(exitButton);

        var panel = new StackPanel
        {
            Spacing = 18,
            MaxWidth = 760,
            Padding = new Thickness(48),
            HorizontalAlignment = HorizontalAlignment.Center,
            VerticalAlignment = VerticalAlignment.Center,
        };
        panel.Children.Add(_alphaHeadline);
        panel.Children.Add(_alphaRing);
        panel.Children.Add(_alphaMessage);
        panel.Children.Add(_alphaDetail);
        panel.Children.Add(_alphaButtons);

        _alphaOverlay = new Grid
        {
            Background = new Microsoft.UI.Xaml.Media.SolidColorBrush(
                dark ? Microsoft.UI.ColorHelper.FromArgb(0xFF, 0x1C, 0x1C, 0x1E) : Microsoft.UI.Colors.White),
        };
        _alphaOverlay.Children.Add(panel);
        Grid.SetRowSpan(_alphaOverlay, 100);
        Grid.SetColumnSpan(_alphaOverlay, 100);
        RootGrid.Children.Add(_alphaOverlay);
    }

    private async void RootGrid_Loaded(object sender, RoutedEventArgs e)
    {
        // Alpha 定时过期版：必须先用公网 NTP 校验网络时间（系统时间与 NTP 不一致 / 已过期 / 无法校时都不放行）
        if (!await RunAlphaStartupCheckAsync())
            return;
        StartAlphaWatchdog();
    }

    /// <summary>
    /// 启动闸门：通过公网 NTP 服务器校验网络时间（不看本机时钟）。
    /// 通过 → 返回 true 继续加载；未通过 → 停留在统一提示上，只允许「重新校验 / 退出程序」。
    /// </summary>
    private async Task<bool> RunAlphaStartupCheckAsync()
    {
        ShowAlphaChecking();
        while (true)
        {
            TimeCheckResult result;
            try
            {
                result = await TimeGuard.CheckAsync();
            }
            catch (Exception ex)
            {
                TimeGuard.LogSink?.Invoke("ALPHA 校时异常：" + ex);
                result = new TimeCheckResult
                {
                    Status = TimeCheckStatus.Unreachable,
                    LocalUtc = DateTimeOffset.UtcNow,
                    Detail = "时间校验过程发生异常：" + ex.Message,
                };
            }

            if (result.IsOk)
            {
                TimeGuard.LogSink?.Invoke("ALPHA 启动校验通过，继续加载界面");
                HideAlphaOverlay();
                UpdateAlphaBadge();
                return true;
            }

            TimeGuard.LogSink?.Invoke($"ALPHA 启动拦截：status={result.Status} detail={result.Detail}");
            ShowAlphaBlocked(result);
            _alphaDecision = new TaskCompletionSource<bool>(TaskCreationOptions.RunContinuationsAsynchronously);
            bool retry = await _alphaDecision.Task;
            _alphaDecision = null;
            if (!retry)
            {
                TimeGuard.LogSink?.Invoke("ALPHA 用户选择退出程序");
                Environment.Exit(0);
                return false;
            }
            ShowAlphaChecking();
        }
    }

    /// <summary>状态栏/标题旁标注 Alpha 版本与剩余天数。</summary>
    private void UpdateAlphaBadge()
    {
        var ntpNow = TimeGuard.LastResult?.NtpUtc;
        int daysLeft = ntpNow is null
            ? -1
            : (int)Math.Floor(TimeGuard.ExpiryUtc.Subtract(ntpNow.Value).TotalDays);
        AlphaBadge.Text = daysLeft >= 0
            ? $"Alpha测试版 · 2026-09-24 过期（剩余 {daysLeft} 天）"
            : "Alpha测试版 · 2026-09-24 过期";
        AlphaBadge.Visibility = Visibility.Visible;
    }

    /// <summary>校验中：转圈 + 说明文案。</summary>
    private void ShowAlphaChecking()
    {
        if (_alphaOverlay is null) return;
        _alphaOverlay.Visibility = Visibility.Visible;
        _alphaHeadline!.Text = "正在校验版本有效期…";
        _alphaHeadline.FontSize = 30;
        _alphaRing!.Visibility = Visibility.Visible;
        _alphaRing.IsActive = true;
        _alphaMessage!.Visibility = Visibility.Collapsed;
        _alphaDetail!.Text = "通过 NTP 服务器获取网络时间（不信任本机时钟）";
        _alphaButtons!.Visibility = Visibility.Collapsed;
    }

    /// <summary>拦截：显示统一过期文案 + 失败原因 + 两个按钮。</summary>
    private void ShowAlphaBlocked(TimeCheckResult result)
    {
        if (_alphaOverlay is null) return;
        _alphaOverlay.Visibility = Visibility.Visible;
        _alphaHeadline!.Text = "版本校验未通过";
        _alphaHeadline.FontSize = 24;
        _alphaRing!.IsActive = false;
        _alphaRing.Visibility = Visibility.Collapsed;
        _alphaMessage!.Text = TimeGuard.ExpiryMessage;
        _alphaMessage.Visibility = Visibility.Visible;
        _alphaDetail!.Text = result.Detail;
        _alphaButtons!.Visibility = Visibility.Visible;
    }

    private void HideAlphaOverlay()
    {
        if (_alphaOverlay is null) return;
        _alphaOverlay.Visibility = Visibility.Collapsed;
    }

    /// <summary>
    /// 运行期复核：每 15 分钟用 NTP 重新取一次网络时间。
    /// 已过期 / 系统时间与 NTP 不一致 → 立即拦截；网络暂时不可达 → 连续两次失败才拦截（容忍单次抖动）。
    /// </summary>
    private void StartAlphaWatchdog()
    {
        _alphaWatchdog = DispatcherQueue.CreateTimer();
        _alphaWatchdog.Interval = TimeSpan.FromMinutes(15);
        _alphaWatchdog.IsRepeating = true;
        _alphaWatchdog.Tick += async (_, _) => await AlphaWatchdogTickAsync();
        _alphaWatchdog.Start();
        TimeGuard.LogSink?.Invoke("ALPHA 看门狗已启动（每 15 分钟复核一次网络时间）");
    }

    private async Task AlphaWatchdogTickAsync()
    {
        try
        {
            var result = await TimeGuard.CheckAsync();
            if (result.IsOk)
            {
                _alphaWatchdogFailures = 0;
                return;
            }

            bool hardFail = result.Status is TimeCheckStatus.Expired or TimeCheckStatus.ClockMismatch;
            _alphaWatchdogFailures++;
            TimeGuard.LogSink?.Invoke($"ALPHA 看门狗校验失败（第 {_alphaWatchdogFailures} 次）：{result.Status} {result.Detail}");
            if (!hardFail && _alphaWatchdogFailures < 2)
                return;

            _alphaWatchdog?.Stop();
            if (await RunAlphaRuntimeBlockAsync(result))
                return;

            Environment.Exit(0);
        }
        catch (Exception ex)
        {
            TimeGuard.LogSink?.Invoke("ALPHA 看门狗异常：" + ex);
        }
    }

    /// <summary>运行期拦截：覆盖层显示统一提示；返回 true 表示重试后校验恢复通过。</summary>
    private async Task<bool> RunAlphaRuntimeBlockAsync(TimeCheckResult result)
    {
        while (true)
        {
            ShowAlphaBlocked(result);
            _alphaDecision = new TaskCompletionSource<bool>(TaskCreationOptions.RunContinuationsAsynchronously);
            bool retry = await _alphaDecision.Task;
            _alphaDecision = null;
            if (!retry) return false;

            ShowAlphaChecking();
            result = await TimeGuard.CheckAsync();
            if (result.IsOk)
            {
                _alphaWatchdogFailures = 0;
                HideAlphaOverlay();
                UpdateAlphaBadge();
                _alphaWatchdog?.Start();
                TimeGuard.LogSink?.Invoke("ALPHA 重新校验通过，继续运行");
                return true;
            }
        }
    }
#endif

    /// <summary>设置窗口 / 任务栏图标（exe 已内嵌图标，这里显式再设一次，保证 WinUI 窗口标题栏也用同一张图）。</summary>
    private void ApplyWindowIcon()
    {
        try
        {
            var icon = System.IO.Path.Combine(AppContext.BaseDirectory, "Assets", "app.ico");
            if (System.IO.File.Exists(icon)) AppWindow.SetIcon(icon);
        }
        catch
        {
            // 图标设置失败不影响启动
        }
    }

    private void FillFilterCombos()    {
        TagFilter.Items.Add("全部主题");
        foreach (var (name, _) in Categorizer.Tags) TagFilter.Items.Add(name);
        TagFilter.SelectedIndex = 0;

        FieldFilter.Items.Add("全部字段");
        FieldFilter.Items.Add("名称"); FieldFilter.Items.Add("简称");
        FieldFilter.Items.Add("描述"); FieldFilter.Items.Add("详情/规则");
        FieldFilter.Items.Add("备注"); FieldFilter.Items.Add("提示");
        FieldFilter.Items.Add("标题"); FieldFilter.Items.Add("正文");
        FieldFilter.Items.Add("公告"); FieldFilter.Items.Add("横幅/页签");
        FieldFilter.Items.Add("按钮/界面"); FieldFilter.Items.Add("说话人");
        FieldFilter.Items.Add("字幕"); FieldFilter.Items.Add("台词");
        FieldFilter.Items.Add("其他");
        FieldFilter.SelectedIndex = 0;
    }

    private async Task LoadRootAsync(string root)
    {
        await LoadSourceAsync(new ImportSource(root, ImportKind.Folder, root, false, "本地文件夹"));
    }

    /// <summary>
    /// 导入并加载一个数据源（文件夹或 ZIP 包）。
    ///
    /// ZIP 的处理：先在后台线程解压到缓存目录（戳记命中则直接复用），再把那个目录交给
    /// <see cref="StringTableDatabase.Load"/> —— 解析逻辑只有一套，压缩包不额外分叉。
    /// 加载成功后才把来源记进设置，下次启动直接用它。
    /// </summary>
    private async Task LoadSourceAsync(ImportSource source)
    {
        if (_busy) return;
        _busy = true;
        SetBusyUi(true);

        var db = _db;
        ImportSource? resolved = null;
        string? failure = null;

        await Task.Run(() =>
        {
            try
            {
                resolved = source.Kind == ImportKind.Folder && source.LoadRoot == source.OriginalPath
                    ? source
                    : ZipBundle.Resolve(source.OriginalPath, source.Kind, msg =>
                        _ = DispatcherQueue.TryEnqueue(() => StatusText.Text = msg));
            }
            catch (Exception ex)
            {
                failure = ex.Message;
            }
        });

        if (failure is not null || resolved is null)
        {
            _busy = false;
            SetBusyUi(false);
            StatusText.Text = "导入失败：" + failure;
            SummaryText.Text = "导入失败：" + failure;
            AppSettings.ClearSource();
            return;
        }

        _currentSource = resolved;
        StatusText.Text = "正在解析 " + resolved.LoadRoot;

        var ok = await Task.Run(() => db.Load(resolved.LoadRoot, msg =>
        {
            _ = DispatcherQueue.TryEnqueue(() => StatusText.Text = msg);
        }));

        _busy = false;
        SetBusyUi(false);

        // 注意：Load 在「一个 json 都没有」时也会返回 true，所以这里要连表数一起看
        if (!ok || db.Tables.Count == 0)
        {
            StatusText.Text = $"加载失败或没有解析出任何表: {resolved.LoadRoot} — 请用「导入」换一个数据源";
            SummaryText.Text = "没有可显示的数据（0 张表）";
            if (_currentSource is not null && ReferenceEquals(_currentSource, resolved)) _currentSource = null;
            return;
        }

        // 加载成功才记住来源（失败的下次不会再自动加载一遍）
        AppSettings.SaveSource(resolved.OriginalPath, resolved.KindName);

        RootPathText.Text = resolved.Describe()
                            + (resolved.Kind == ImportKind.Zip ? $" → 缓存 {resolved.LoadRoot}" : "");
        ToolTipService.SetToolTip(RootPathText, RootPathText.Text);

        RebuildDomainList();
        SummaryText.Text = $"{db.Tables.Count:N0} 张表 / {db.TotalEntries:N0} 条文本 / 解析 {db.LoadSeconds:F1}s"
                           + (db.Errors.Count > 0 ? $" / 跳过 {db.Errors.Count} 个异常文件" : "");
        StatusText.Text = (db.Errors.Count == 0 ? "加载完成" : "加载完成(部分文件被跳过)")
                          + $" · 来源：{resolved.Describe()}";
    }

    /// <summary>
    /// 启动时决定加载哪个数据源：① 上次成功导入的（文件夹还在 / ZIP 还在）→
    /// ② 安装目录旁的 Data\StringTables → ③ 开发机默认路径。
    /// </summary>
    private ImportSource ResolveStartupSource()
    {
        var last = AppSettings.LastSource;
        if (!string.IsNullOrWhiteSpace(last))
        {
            try
            {
                var kind = string.Equals(AppSettings.LastSourceKind, "zip", StringComparison.OrdinalIgnoreCase)
                    ? ImportKind.Zip
                    : ZipBundle.GuessKind(last);

                if (kind == ImportKind.Zip && File.Exists(last))
                    return new ImportSource(last, ImportKind.Zip, last, false, "上次导入的 ZIP");
                if (kind == ImportKind.Folder && Directory.Exists(last))
                    return new ImportSource(last, ImportKind.Folder, last, false, "上次导入的文件夹");
            }
            catch
            {
                // 上次的来源不可用了，往下走默认逻辑
            }

            StatusText.Text = $"上次的数据源已不存在，改用默认目录（{last}）";
            AppSettings.ClearSource();
        }

        var fallback = ResolveDefaultRoot();
        return new ImportSource(fallback, ImportKind.Folder, fallback, false, "默认目录");
    }

    private void SetBusyUi(bool busy)
    {
        BusyRing.IsActive = busy;
        ImportButton.IsEnabled = !busy;
        ReloadButton.IsEnabled = !busy;
        SearchBox.IsEnabled = !busy;
        TagFilter.IsEnabled = !busy;
        FieldFilter.IsEnabled = !busy;
        DomainList.IsEnabled = !busy;
        TableList.IsEnabled = !busy;
        EntryList.IsEnabled = !busy;
    }

    private void RebuildDomainList()
    {
        _domainCounts.Clear();
        foreach (var t in _db.Tables)
            _domainCounts[t.Domain] = _domainCounts.GetValueOrDefault(t.Domain) + t.Count;

        _domainItems.Clear();
        _domainItems.Add(new DomainItemVm { Kind = null, Name = "全部业务域", CountText = _db.TotalEntries.ToString("N0") });
        foreach (var (kind, label) in DomainInfo.All)
        {
            if (_domainCounts.TryGetValue(kind, out var c) && c > 0)
                _domainItems.Add(new DomainItemVm { Kind = kind, Name = label, CountText = c.ToString("N0") });
        }
        DomainTotalText.Text = $"{_domainItems.Count - 1:N0} 类";
        DomainList.SelectedIndex = 0; // 可能触发 DomainList_SelectionChanged(幂等)
        // 若重载前 SelectedIndex 已是 0 则不会触发事件, 这里显式重建一次
        _selectedDomain = null;
        _selectedTable = null;
        RebuildTableList(null);
        RebuildScope();
    }

    private void RebuildTableList(DomainKind? domain)
    {
        _tableItems.Clear();
        var tables = domain is null
            ? _db.Tables
            : _db.Tables.Where(t => t.Domain == domain);
        foreach (var t in tables)
            _tableItems.Add(new TableItemVm { Table = t });
        TableTotalText.Text = $"{_tableItems.Count:N0} 张表";
        TableList.SelectedIndex = -1; // 触发 TableList_SelectionChanged? 无选择则不触发, 手动重建范围
    }

    // ---------- 事件 ----------

    private void DomainList_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (DomainList.SelectedItem is not DomainItemVm item) return;
        _selectedDomain = item.Kind;
        RebuildTableList(item.Kind);
        RebuildScope();
    }

    private void TableList_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (TableList.SelectedItem is TableItemVm vm) _selectedTable = vm.Table;
        else _selectedTable = null; // 未选表 = 浏览整个分类
        RebuildScope();
    }

    private void RebuildScope()
    {
        var all = _domainItems.Count > 0 && DomainList.SelectedIndex == 0 && _selectedTable is null;
        if (_selectedTable is not null)
        {
            _scope = _selectedTable.Entries;
            _scopeName = DomainInfo.Label(_selectedTable.Domain) + " · " + _selectedTable.FileName;
        }
        else if (_selectedDomain is { } d)
        {
            _scope = [.. _db.EntriesOf(d)];
            _scopeName = DomainInfo.Label(d);
        }
        else
        {
            _scope = all ? [.. _db.Tables.SelectMany(t => t.Entries)] : [];
            _scopeName = all ? "全部业务域" : "—";
        }
        ScopeHeader.Text = _scopeName;
        ApplyFilter();
    }

    private void SearchBox_TextChanged(object sender, TextChangedEventArgs e)
    {
        _searchTimer.Stop();
        _searchTimer.Start();
    }

    private void Filter_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (_db.IsLoaded) ApplyFilter();
    }

    private void ApplyFilter()
    {
        if (!_db.IsLoaded) return;

        var q = SearchBox.Text.Trim();
        int tagBit = TagFilter.SelectedIndex - 1;      // -1 = 全部
        int fieldIdx = FieldFilter.SelectedIndex - 1;  // -1 = 全部

        var sw = System.Diagnostics.Stopwatch.StartNew();
        int matched = 0;
        var shown = new List<StringEntry>(Math.Min(MaxShown, _scope.Count));

        foreach (var en in _scope)
        {
            bool okTag = tagBit < 0 || (en.TagMask & (1 << tagBit)) != 0;
            if (!okTag) continue;
            bool okField = fieldIdx < 0 || (int)en.Field == fieldIdx;
            if (!okField) continue;
            if (q.Length > 0)
            {
                bool hit = en.Key.Contains(q, StringComparison.OrdinalIgnoreCase)
                           || en.Text.Contains(q, StringComparison.OrdinalIgnoreCase);
                if (!hit) continue;
            }
            matched++;
            if (shown.Count < MaxShown) shown.Add(en);
        }
        sw.Stop();

        EntryList.ItemsSource = shown;
        EntryList.SelectedIndex = -1;
        ClearDetail();

        MatchInfo.Text = $"显示 {shown.Count:N0} / 匹配 {matched:N0} · 范围 {_scope.Count:N0} 条 · 过滤 {sw.ElapsedMilliseconds} ms"
                         + (matched > MaxShown ? $" (列表仅展示前 {MaxShown:N0} 条, 请细化搜索)" : "");
    }

    private void EntryList_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        if (EntryList.SelectedItem is StringEntry en) ShowDetail(en);
        else ClearDetail();
    }

    private void EntryList_DoubleTapped(object sender, DoubleTappedRoutedEventArgs e)
    {
        if (EntryList.SelectedItem is StringEntry en)
        {
            CopyToClipboard(en.Key);
            DetailHint.Text = "已复制 Key";
        }
    }

    private void ShowDetail(StringEntry en)
    {
        DetailKey.Text = en.Key;
        DetailDomainField.Text = $"{en.DomainLabel}  /  {en.FieldLabel}";
        DetailTagsId.Text = $"{Categorizer.TagNamesOf(en.TagMask)}  /  {en.IdText}";
        DetailTable.Text = $"{en.Table.RelPath}\nNamespace: {en.Table.Namespace}";
        DetailBody.Text = en.Text;
        DetailHint.Text = "";
    }

    private void ClearDetail()
    {
        DetailKey.Text = "(未选择条目)";
        DetailDomainField.Text = "—";
        DetailTagsId.Text = "—";
        DetailTable.Text = "—";
        DetailBody.Text = "(未选择条目)";
    }

    private void CopyKeyButton_Click(object sender, RoutedEventArgs e)
    {
        if (EntryList.SelectedItem is StringEntry en)
        {
            CopyToClipboard(en.Key);
            DetailHint.Text = "已复制 Key";
        }
    }

    private void CopyTextButton_Click(object sender, RoutedEventArgs e)
    {
        if (EntryList.SelectedItem is StringEntry en)
        {
            CopyToClipboard(en.Text);
            DetailHint.Text = "已复制全文";
        }
    }

    private static void CopyToClipboard(string text)
    {
        try
        {
            var pkg = new DataPackage { RequestedOperation = DataPackageOperation.Copy };
            pkg.SetText(text);
            Clipboard.SetContent(pkg);
            Clipboard.Flush();
        }
        catch { /* 剪贴板不可用时忽略 */ }
    }

    /// <summary>导入 → 文件夹…</summary>
    private async void ImportFolder_Click(object sender, RoutedEventArgs e)
    {
        var picker = new FolderPicker { SuggestedStartLocation = Windows.Storage.Pickers.PickerLocationId.DocumentsLibrary };
        picker.FileTypeFilter.Add("*");
        InitializeWithWindow.Initialize(picker, WindowNative.GetWindowHandle(this));

        StatusText.Text = "正在等待选择文件夹…";
        var folder = await picker.PickSingleFolderAsync();
        if (folder is null)
        {
            StatusText.Text = "已取消导入。";
            return;
        }

        try
        {
            await LoadSourceAsync(ZipBundle.ResolveFolder(folder.Path));
        }
        catch (Exception ex)
        {
            StatusText.Text = "导入失败：" + ex.Message;
            SummaryText.Text = "导入失败：" + ex.Message;
        }
    }

    /// <summary>导入 → ZIP 包…（解压到缓存后照常解析，不用先手动解压）</summary>
    private async void ImportZip_Click(object sender, RoutedEventArgs e)
    {
        var picker = new FileOpenPicker { SuggestedStartLocation = Windows.Storage.Pickers.PickerLocationId.DocumentsLibrary };
        picker.FileTypeFilter.Add(".zip");
        InitializeWithWindow.Initialize(picker, WindowNative.GetWindowHandle(this));

        StatusText.Text = "正在等待选择压缩包…";
        var file = await picker.PickSingleFileAsync();
        if (file is null)
        {
            StatusText.Text = "已取消导入。";
            return;
        }

        try
        {
            // 解压可能要点时间（大包几十秒），放到线程池里做，别把界面卡住
            ImportSource? source = null;
            string? failure = null;
            StatusText.Text = "正在解压 " + Path.GetFileName(file.Path) + " …";
            SetBusyUi(true);
            await Task.Run(() =>
            {
                try { source = ZipBundle.ResolveZip(file.Path); }
                catch (Exception ex) { failure = ex.Message; }
            });
            SetBusyUi(false);

            if (failure is not null || source is null)
            {
                StatusText.Text = "导入失败：" + failure;
                SummaryText.Text = "导入失败：" + failure;
                return;
            }

            await LoadSourceAsync(source);
        }
        catch (Exception ex)
        {
            SetBusyUi(false);
            StatusText.Text = "导入失败：" + ex.Message;
            SummaryText.Text = "导入失败：" + ex.Message;
        }
    }

    /// <summary>清理解压缓存（删掉 %LOCALAPPDATA%\DeltaStringBrowser\imports）。</summary>
    private async void ClearImportCache_Click(object sender, RoutedEventArgs e)
    {
        long before = ZipBundle.CacheBytes();
        if (before <= 0)
        {
            StatusText.Text = "解压缓存是空的，没有需要清理的东西。";
            return;
        }

        var dlg = new ContentDialog
        {
            Title = "清理解压缓存",
            Content = new TextBlock
            {
                Text = $"将删除 {before / 1024.0 / 1024.0:F1} MB 的解压缓存（{ZipBundle.ImportsRoot}）。\n" +
                       "当前已加载的数据不受影响；下次导入 ZIP 时会重新解压。",
                TextWrapping = TextWrapping.Wrap,
            },
            PrimaryButtonText = "清理",
            CloseButtonText = "取消",
            DefaultButton = ContentDialogButton.Close,
            XamlRoot = Content.XamlRoot,
        };

        if (await dlg.ShowAsync() != ContentDialogResult.Primary) return;

        ZipBundle.ClearCache();
        StatusText.Text = $"解压缓存已清理（释放 {before / 1024.0 / 1024.0:F1} MB）";
    }

    private async void ReloadButton_Click(object sender, RoutedEventArgs e)
    {
        // 优先按「当前来源」重载：ZIP 会顺便检查包有没有更新（变了就重新解压）
        if (_currentSource is not null)
        {
            await LoadSourceAsync(_currentSource);
            return;
        }

        var path = _db.RootPath.Length > 0 ? _db.RootPath : ResolveDefaultRoot();
        await LoadRootAsync(path);
    }

    /// <summary>外观菜单：跟随系统 / 浅色 / 深色（选择写进 %LOCALAPPDATA%\DeltaStringBrowser\settings.json）。</summary>
    private void ThemeItem_Click(object sender, RoutedEventArgs e)
    {
        if (sender is not FrameworkElement { Tag: string tag }) return;
        ThemeService.SetMode(ThemeService.Parse(tag));
    }

    /// <summary>把当前主题模式落到根元素，并同步菜单选中态与图标。</summary>
    private void ApplyTheme()
    {
        RootGrid.RequestedTheme = ThemeService.ResolveFor(RootGrid);

        ThemeSystemItem.IsChecked = ThemeService.Current == ThemeMode.System;
        ThemeLightItem.IsChecked = ThemeService.Current == ThemeMode.Light;
        ThemeDarkItem.IsChecked = ThemeService.Current == ThemeMode.Dark;

        bool dark = RootGrid.ActualTheme == ElementTheme.Dark;
        ThemeIcon.Glyph = dark ? "\uE708" : "\uE706";
    }
}
