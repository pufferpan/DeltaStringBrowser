using DeltaStringBrowser.Models;

namespace DeltaStringBrowser.Services;

/// <summary>文本归类引擎：域名(表) + 字段语义(键后缀) + 内容主题(关键词)。</summary>
public static class Categorizer
{
    /// <summary>内容主题标签定义。掩码位 = 索引。</summary>
    public static readonly (string Name, string[] Keywords)[] Tags =
    [
        ("周年洲年",   ["周年", "洲年", "周年代币"]),
        ("彩蛋",       ["彩蛋"]),
        ("庆典活动",   ["庆典", "活动纪念", "赛事", "竞猜", "邀请函"]),
        ("新春红包",   ["新春", "红包", "元宵", "恭喜发财"]),
        ("赛季",       ["赛季", "通行证", "S8赛季", "S9赛季"]),
        ("黑鹰坠落",   ["黑鹰", "BHD", "E.R.I"]),
        ("隐秘协议",   ["隐秘协议", "协议箱"]),
        ("曼德尔砖",   ["曼德尔", "图灵砖"]),
        ("经济代币",   ["哈夫币", "代币", "补给包", "礼包", "金条", "兑换", "购买"]),
        ("武器装备",   ["武器", "枪械", "SCAR", "弹药", "头盔", "护甲", "防弹", "装备", "步枪"]),
        ("测试占位",   ["测试", "占位", "暂不上"]),
        ("剧情台词",   []), // 由键后缀判定(台词/字幕/说话人), 不按关键词
    ];

    public static string TagName(int bit) => Tags[bit].Name;

    /// <summary>表 → 业务域。规则按序匹配,命中即停。</summary>
    public static DomainKind ClassifyTable(string relDir, string fileName)
    {
        var dir = relDir.Replace('/', '\\');
        var name = fileName.ToUpperInvariant();

        if (dir.StartsWith("STForLua", StringComparison.OrdinalIgnoreCase)) return DomainKind.Lua;
        if (dir.StartsWith("STForMaps", StringComparison.OrdinalIgnoreCase)) return DomainKind.Story;
        if (dir.StartsWith("UIStringTables", StringComparison.OrdinalIgnoreCase)) return DomainKind.Ui;
        if (dir.StartsWith("Oversea", StringComparison.OrdinalIgnoreCase)) return DomainKind.Ui;
        if (dir.StartsWith("STForCodes", StringComparison.OrdinalIgnoreCase)) return DomainKind.System;

        if (name.Contains("SUBTITLE")) return DomainKind.Story;
        if (name.Contains("EVENT") || name.Contains("ACTIVITY") || name.Contains("SURVEY")
            || name.Contains("MAJOREVENT") || name.Contains("QUESTION")) return DomainKind.Activity;
        if (name.Contains("ACHIEVEMENT") || name.Contains("BADGE")) return DomainKind.Achievement;
        if (name.Contains("MATCH") || name.Contains("GAMEMODE") || name.Contains("GAMERULE")
            || name.Contains("SUBMODE") || name.Contains("MODEDATA") || name.Contains("RAID")) return DomainKind.Mode;
        if (name.Contains("GAMEITEM") || name.Contains("ITEMASSETS") || name.Contains("LOTTERY")
            || name.Contains("LUCKYNEST") || name.Contains("PENDANT") || name.Contains("CABINET")
            || name.Contains("MARKET") || name.Contains("RESOURCECOMMERCIAL") || name.Contains("REWARD")
            || name.Contains("STORE") || name.Contains("BOX")) return DomainKind.Item;
        if (name.Contains("HERO") || name.Contains("SPRAYPAINT") || name.Contains("AVATAR")
            || name.Contains("SKIN") || name.Contains("SOCIAL") || name.Contains("SPRAY")) return DomainKind.Hero;
        if (name.Contains("TASK") || name.Contains("QUEST") || name.Contains("MISSION")) return DomainKind.Quest;
        if (name.Contains("MAIL")) return DomainKind.Mail;
        if (name.Contains("TIP") || name.Contains("TEACHING") || name.Contains("MANUAL")
            || name.Contains("INTERACTOR") || name.Contains("GUIDE")) return DomainKind.Guide;
        if (name.Contains("ERRCODE") || name.Contains("ERROR") || name.Contains("CODE")) return DomainKind.System;
        return DomainKind.Generic;
    }

    /// <summary>键后缀 → 字段语义。</summary>
    public static FieldKind ClassifyField(string key)
    {
        var up = key.ToUpperInvariant();
        if (up.Contains("SPEAKER")) return FieldKind.Speaker;
        if (up.Contains("SUBTITLE")) return FieldKind.Subtitle;
        if (up.Contains("LINES")) return FieldKind.Lines;

        var seg = up[(up.LastIndexOf('_') + 1)..];
        return seg switch
        {
            "NAME" => FieldKind.Name,
            "SHORTNAME" => FieldKind.ShortName,
            "DESC" or "DESCRIPTION" => FieldKind.Description,
            "DETAIL" or "DETAILS" or "RULE" or "RULES" => FieldKind.Detail,
            "REMARK" or "REMARKS" => FieldKind.Remark,
            "TIP" or "TIPS" or "HINT" or "HINTS" or "TOOLTIP" or "UNLOCKTIP" => FieldKind.Tip,
            "TITLE" => FieldKind.Title,
            "TEXT" or "TEXTS" or "CONTENT" or "BODY" => FieldKind.Text,
            "ANNOUNCE" or "ANNOUNCEMENT" or "NOTICE" => FieldKind.Announce,
            "BANNER" or "SUBNAME" or "TABNAME" or "TAB" => FieldKind.Banner,
            "BTN" or "BUTTON" or "LABEL" or "BUTTONNAME" or "BUTTONTEXT" => FieldKind.Button,
            _ => FieldKind.Other,
        };
    }

    /// <summary>文本 → 主题标签掩码。剧情台词位由字段类型决定,其余按关键词。</summary>
    public static ushort ClassifyTags(string text, string key, FieldKind field)
    {
        ushort mask = 0;
        for (int i = 0; i < Tags.Length; i++)
        {
            var (name, keywords) = Tags[i];
            if (name == "剧情台词")
            {
                if (field is FieldKind.Lines or FieldKind.Subtitle or FieldKind.Speaker) mask |= (ushort)(1 << i);
                continue;
            }
            foreach (var kw in keywords)
                if (text.Contains(kw, StringComparison.OrdinalIgnoreCase))
                {
                    mask |= (ushort)(1 << i);
                    break;
                }
        }
        return mask;
    }

    public static string TagNamesOf(ushort mask)
    {
        if (mask == 0) return "无";
        var list = new List<string>();
        for (int i = 0; i < Tags.Length; i++)
        {
            if ((mask & (1 << i)) != 0) list.Add(Tags[i].Name);
        }
        return string.Join("、", list);
    }
}
