# -*- coding: utf-8 -*-
"""
build_db.py — DeltaForce StringTables 分类快照生成器（离线、确定性）

读取 UE 导出的本地化 JSON 目录（如 StringTables），把归类规则（移植自
F:\arg\DeltaStringBrowser\Services\Categorizer.cs 与 StringTableDatabase.cs）
应用到每张表/每条文本，输出一个 SQLite 数据库：

  tables : id / rel(相对路径,\分隔) / file(文件名) / ns(TableNamespace) / domain / cnt
  entries: id / tbl / key / text / locid / domain / field / tags / pos

索引：(tbl,pos) 唯一,(domain,pos),key,text,pos。
应用端只读查询；收藏等用户数据由应用在自己的数据库中另行保存。

用法: python build_db.py <源目录> <输出.db>
"""
import json
import os
import sqlite3
import sys
import time
from pathlib import Path

# ---------------------------------------------------------------- 归类规则
# 与 C# 枚举顺序一致：Models.cs DomainKind / FieldKind / Categorizer.Tags

DOMAIN_LABELS = [
    "UI 界面文案", "Lua 界面逻辑文案", "剧情 / 字幕台词", "活动 / 赛事",
    "成就 / 徽章", "玩法 / 模式 / 地图", "物品 / 道具 / 经济",
    "英雄 / 角色外观", "任务 / 目标", "邮件 / 公告", "提示 / 引导 / 手册",
    "系统 / 错误码", "其他数据表",
]

FIELD_LABELS = [
    "名称", "简称", "描述", "详情/规则", "备注", "提示", "标题", "正文",
    "公告", "横幅/页签", "按钮/界面", "说话人", "字幕", "台词", "其他",
]

TAGS = [
    ("周年洲年", ["周年", "洲年", "周年代币"]),
    ("彩蛋", ["彩蛋"]),
    ("庆典活动", ["庆典", "活动纪念", "赛事", "竞猜", "邀请函"]),
    ("新春红包", ["新春", "红包", "元宵", "恭喜发财"]),
    ("赛季", ["赛季", "通行证", "S8赛季", "S9赛季"]),
    ("黑鹰坠落", ["黑鹰", "BHD", "E.R.I"]),
    ("隐秘协议", ["隐秘协议", "协议箱"]),
    ("曼德尔砖", ["曼德尔", "图灵砖"]),
    ("经济代币", ["哈夫币", "代币", "补给包", "礼包", "金条", "兑换", "购买"]),
    ("武器装备", ["武器", "枪械", "SCAR", "弹药", "头盔", "护甲", "防弹", "装备", "步枪"]),
    ("测试占位", ["测试", "占位", "暂不上"]),
    ("剧情台词", []),  # 由字段类型派生
]


def utf16key(s: str):
    """C# string.CompareOrdinal 按 UTF-16 码元比较，这里编码成字节做排序键。"""
    return s.encode("utf-16-le", errors="surrogatepass")


def classify_table(rel_dir: str, file_name: str) -> int:
    """目录 + 表名前缀 → 业务域（与 Categorizer.ClassifyTable 一致）。"""
    d = rel_dir.replace("/", "\\")
    n = file_name.upper()
    dl = d.lower()
    if dl.startswith("stforlua"): return 1
    if dl.startswith("stformaps"): return 2
    if dl.startswith("uistringtables"): return 0
    if dl.startswith("oversea"): return 0
    if dl.startswith("stforcodes"): return 11
    if "SUBTITLE" in n: return 2
    if any(k in n for k in ("EVENT", "ACTIVITY", "SURVEY", "MAJOREVENT", "QUESTION")): return 3
    if any(k in n for k in ("ACHIEVEMENT", "BADGE")): return 4
    if any(k in n for k in ("MATCH", "GAMEMODE", "GAMERULE", "SUBMODE", "MODEDATA", "RAID")): return 5
    if any(k in n for k in ("GAMEITEM", "ITEMASSETS", "LOTTERY", "LUCKYNEST", "PENDANT",
                            "CABINET", "MARKET", "RESOURCECOMMERCIAL", "REWARD", "STORE", "BOX")): return 6
    if any(k in n for k in ("HERO", "SPRAYPAINT", "AVATAR", "SKIN", "SOCIAL", "SPRAY")): return 7
    if any(k in n for k in ("TASK", "QUEST", "MISSION")): return 8
    if "MAIL" in n: return 9
    if any(k in n for k in ("TIP", "TEACHING", "MANUAL", "INTERACTOR", "GUIDE")): return 10
    if any(k in n for k in ("ERRCODE", "ERROR", "CODE")): return 11
    return 12


def classify_field(key: str) -> int:
    """键后缀 → 字段语义（与 Categorizer.ClassifyField 一致）。"""
    u = key.upper()
    if "SPEAKER" in u: return 11
    if "SUBTITLE" in u: return 12
    if "LINES" in u: return 13
    seg = u[u.rfind("_") + 1:]
    return {
        "NAME": 0, "SHORTNAME": 1,
        "DESC": 2, "DESCRIPTION": 2,
        "DETAIL": 3, "DETAILS": 3, "RULE": 3, "RULES": 3,
        "REMARK": 4, "REMARKS": 4,
        "TIP": 5, "TIPS": 5, "HINT": 5, "HINTS": 5, "TOOLTIP": 5, "UNLOCKTIP": 5,
        "TITLE": 6,
        "TEXT": 7, "TEXTS": 7, "CONTENT": 7, "BODY": 7,
        "ANNOUNCE": 8, "ANNOUNCEMENT": 8, "NOTICE": 8,
        "BANNER": 9, "SUBNAME": 9, "TABNAME": 9, "TAB": 9,
        "BTN": 10, "BUTTON": 10, "LABEL": 10, "BUTTONNAME": 10, "BUTTONTEXT": 10,
    }.get(seg, 14)


def classify_tags(text: str, key: str, field: int) -> int:
    mask = 0
    for i, (_name, kws) in enumerate(TAGS):
        if i == 11:  # 剧情台词位：按字段类型
            if field in (11, 12, 13):
                mask |= 1 << i
            continue
        if any(kw.lower() in text.lower() for kw in kws):
            mask |= 1 << i
    return mask


# ---------------------------------------------------------------- 解析
def parse_file(path: str, root: str):
    rel = os.path.relpath(path, root).replace("/", "\\")
    with open(path, "rb") as f:
        raw = f.read()
    doc = json.loads(raw.decode("utf-8-sig"))
    ns = doc.get("TableNamespace", "") or ""
    if not isinstance(ns, str):
        ns = str(ns)
    rel_dir = rel.rsplit("\\", 1)[0] if "\\" in rel else ""
    file_name = os.path.basename(rel)
    domain = classify_table(rel_dir, file_name)
    entries = []
    kmap = doc.get("KeysToEntries")
    if isinstance(kmap, dict):
        for k, v in kmap.items():
            if not isinstance(v, dict):
                continue
            name = v.get("Name")
            if isinstance(name, str):
                text = name
            elif name is not None:
                text = str(name)
            else:
                text = ""
            if text == "":
                continue
            locid = 0
            i = v.get("Id")
            if isinstance(i, (int, float)) and not isinstance(i, bool):
                locid = int(i)
            field = classify_field(k)
            entries.append((k, text, locid, domain, field, classify_tags(text, k, field)))
    entries.sort(key=lambda e: utf16key(e[0]))  # 与 C# 每表键排序一致
    return ns, domain, rel, file_name, entries


def main():
    src = sys.argv[1]
    out = sys.argv[2]
    t0 = time.perf_counter()
    files = []
    for dp, _dn, fn in os.walk(src):
        for f in fn:
            if f.lower().endswith(".json"):
                files.append(os.path.join(dp, f))
    files.sort(key=lambda p: utf16key(os.path.relpath(p, src).replace("/", "\\")))

    tables = []          # (ns, domain, rel, file, entries)
    errors = []
    for p in files:
        try:
            ns, domain, rel, file_name, entries = parse_file(p, src)
            if entries:
                tables.append((ns, domain, rel, file_name, entries))
        except Exception as ex:  # noqa: BLE001
            errors.append(f"{os.path.basename(p)}: {ex}")

    # 表按相对路径 ordinal 排序 → 与桌面版一致
    tables.sort(key=lambda t: utf16key(t[2]))

    conn = sqlite3.connect(out)
    cur = conn.cursor()
    cur.executescript("""
    PRAGMA journal_mode=OFF;
    DROP TABLE IF EXISTS tables;
    DROP TABLE IF EXISTS entries;
    CREATE TABLE tables(
        id INTEGER PRIMARY KEY, rel TEXT NOT NULL, file TEXT NOT NULL,
        ns TEXT NOT NULL DEFAULT '', domain INTEGER NOT NULL, cnt INTEGER NOT NULL);
    CREATE TABLE entries(
        id INTEGER PRIMARY KEY, tbl INTEGER NOT NULL, pos INTEGER NOT NULL,
        key TEXT NOT NULL, text TEXT NOT NULL, locid INTEGER NOT NULL,
        domain INTEGER NOT NULL, field INTEGER NOT NULL, tags INTEGER NOT NULL,
        UNIQUE(tbl, pos));
    CREATE INDEX idx_entries_tbl ON entries(tbl, pos);
    CREATE INDEX idx_entries_domain ON entries(domain, pos);
    """)
    entry_id = 1
    for ns, domain, rel, file_name, entries in tables:
        cur.execute("INSERT INTO tables(rel,file,ns,domain,cnt) VALUES(?,?,?,?,?)",
                    (rel, file_name, ns, domain, len(entries)))
        tbl_id = cur.lastrowid
        for pos, (k, text, locid, _dom, field, tags) in enumerate(entries):
            cur.execute(
                "INSERT INTO entries(id,tbl,pos,key,text,locid,domain,field,tags)"
                " VALUES(?,?,?,?,?,?,?,?,?)",
                (entry_id, tbl_id, pos, k, text, locid, domain, field, tags))
            entry_id += 1
    elapsed = time.perf_counter() - t0
    total_entries = entry_id - 1

    domain_rows = {}
    for d in range(13):
        domain_rows[d] = [0, 0]  # tables, entries
    cur.execute("SELECT domain, COUNT(*) FROM tables GROUP BY domain")
    for d, c in cur.fetchall():
        domain_rows[d][0] = c
    cur.execute("SELECT domain, COUNT(*) FROM entries GROUP BY domain")
    for d, c in cur.fetchall():
        domain_rows[d][1] = c

    cur.execute("CREATE TABLE IF NOT EXISTS meta(k TEXT PRIMARY KEY, v TEXT)")
    meta = {
        "source": "DeltaForce/Content/StringTables (UE 本地化 JSON 导出)",
        "tables": str(len(tables)),
        "entries": str(total_entries),
        "build_seconds": f"{elapsed:.2f}",
        "errors": "|".join(errors[:50]),
        "domains": json.dumps(
            [{"id": d, "label": DOMAIN_LABELS[d], "tables": domain_rows[d][0],
              "entries": domain_rows[d][1]} for d in range(13) if domain_rows[d][1] > 0],
            ensure_ascii=False),
        "fields": json.dumps([{"id": i, "label": l} for i, l in enumerate(FIELD_LABELS)], ensure_ascii=False),
        "tags": json.dumps([{"id": i, "label": t[0]} for i, t in enumerate(TAGS)], ensure_ascii=False),
    }
    for k, v in meta.items():
        cur.execute("INSERT OR REPLACE INTO meta(k,v) VALUES(?,?)", (k, v))
    conn.commit()

    print(f"tables={len(tables)} entries={total_entries} seconds={elapsed:.2f} errors={len(errors)}")
    for d, (tc, ec) in domain_rows.items():
        if ec:
            print(f"  [{d}] {DOMAIN_LABELS[d]:<18} tables={tc:<4} entries={ec}")
    for e in errors:
        print("  ERR", e)
    conn.close()


if __name__ == "__main__":
    main()
