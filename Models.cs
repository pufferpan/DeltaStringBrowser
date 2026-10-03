namespace DeltaStringBrowser.Models;

/// <summary>业务域：按目录与表名前缀把 852 张表归类。</summary>
public enum DomainKind
{
    Ui,          // UI 界面文案
    Lua,         // Lua 界面逻辑文案
    Story,       // 剧情 / 字幕 / 台词表
    Activity,    // 活动 / 赛事
    Achievement, // 成就 / 徽章
    Mode,        // 玩法 / 模式 / 地图
    Item,        // 物品 / 道具 / 经济
    Hero,        // 英雄 / 角色外观
    Quest,       // 任务 / 目标
    Mail,        // 邮件 / 公告
    Guide,       // 提示 / 引导 / 手册
    System,      // 系统 / 错误码
    Generic      // 其他数据表
}

/// <summary>字段语义：按键名后缀把每一条文本归类。</summary>
public enum FieldKind
{
    Name,        // 名称
    ShortName,   // 简称
    Description, // 描述
    Detail,      // 详情 / 规则
    Remark,      // 备注 / 文案批注
    Tip,         // 提示
    Title,       // 标题
    Text,        // 正文文本
    Announce,    // 公告
    Banner,      // 横幅 / 页签
    Button,      // 按钮 / 界面元素
    Speaker,     // 说话人
    Subtitle,    // 字幕 / 台词
    Lines,       // 语音台词
    Other        // 其他
}

public static class DomainInfo
{
    public static readonly (DomainKind Kind, string Label)[] All =
    [
        (DomainKind.Ui,          "UI 界面文案"),
        (DomainKind.Lua,         "Lua 界面逻辑文案"),
        (DomainKind.Story,       "剧情 / 字幕台词"),
        (DomainKind.Activity,    "活动 / 赛事"),
        (DomainKind.Achievement, "成就 / 徽章"),
        (DomainKind.Mode,        "玩法 / 模式 / 地图"),
        (DomainKind.Item,        "物品 / 道具 / 经济"),
        (DomainKind.Hero,        "英雄 / 角色外观"),
        (DomainKind.Quest,       "任务 / 目标"),
        (DomainKind.Mail,        "邮件 / 公告"),
        (DomainKind.Guide,       "提示 / 引导 / 手册"),
        (DomainKind.System,      "系统 / 错误码"),
        (DomainKind.Generic,     "其他数据表"),
    ];

    public static string Label(DomainKind k)
    {
        foreach (var (kind, label) in All)
            if (kind == k) return label;
        return "其他";
    }
}

public sealed class TableInfo
{
    public required string RelPath { get; init; }   // 相对 StringTables 根目录
    public required string FileName { get; init; }  // 文件名
    public required string Namespace { get; init; } // TableNamespace
    public required DomainKind Domain { get; init; }
    public List<StringEntry> Entries { get; } = [];

    public string DirName => RelPath.Contains('\\') ? RelPath[..RelPath.LastIndexOf('\\')] : "(根目录)";
    public int Count => Entries.Count;
    public string CountText => Count.ToString("N0");
}

/// <summary>单条本地化文本。</summary>
public sealed class StringEntry
{
    public required string Key { get; init; }
    public required string Text { get; init; }
    public long Id { get; init; }
    public required DomainKind Domain { get; init; }
    public required FieldKind Field { get; init; }
    public required TableInfo Table { get; init; }
    public ushort TagMask { get; init; }

    public string IdText => Id == 0 ? "-" : Id.ToString();
    public string DomainLabel => DomainInfo.Label(Domain);
    public string FieldLabel => FieldLabelOf(Field);

    public string Preview
    {
        get
        {
            var t = Text.Replace("\r", " ").Replace("\n", " ");
            return t.Length <= 150 ? t : t[..150] + "…";
        }
    }

    internal static string FieldLabelOf(FieldKind f) => f switch
    {
        FieldKind.Name => "名称",
        FieldKind.ShortName => "简称",
        FieldKind.Description => "描述",
        FieldKind.Detail => "详情/规则",
        FieldKind.Remark => "备注",
        FieldKind.Tip => "提示",
        FieldKind.Title => "标题",
        FieldKind.Text => "正文",
        FieldKind.Announce => "公告",
        FieldKind.Banner => "横幅/页签",
        FieldKind.Button => "按钮/界面",
        FieldKind.Speaker => "说话人",
        FieldKind.Subtitle => "字幕",
        FieldKind.Lines => "台词",
        _ => "其他",
    };
}

/// <summary>左侧"业务域"列表项(Kind=null 表示"全部业务域")。</summary>
public sealed class DomainItemVm
{
    public DomainKind? Kind { get; init; }
    public required string Name { get; init; }
    public required string CountText { get; init; }
}

/// <summary>左侧"表"列表项。</summary>
public sealed class TableItemVm
{
    public required TableInfo Table { get; init; }
    public string FileName => Table.FileName;
    public string CountText => Table.CountText;
    public string PathText => Table.RelPath;
}
