#  StringTables 归类浏览器
本文本中出现的有关于三角洲行动游戏的字样仅用于演示
WinUI 3 (Fluent) / .NET 10 桌面工具：把 UE 导出的 `StringTables` 全部 JSON 本地化文本读入内存，
按 **业务域 → 表 → 条目** 组织，支持关键词搜索、主题标签过滤、字段类型过滤与详情查看。

默认读取目录：`I:\新建文件夹 (19)\Exports\DeltaForce\Content\StringTables`（不存在时点「打开文件夹」自行选择）。

## 功能

- **全量加载**：852 张表 / 约 60 万条文本并行解析（约 2–5 秒），单文件异常自动跳过并在状态栏提示。
- **三维归类**：
  1. **业务域**（按目录 + 表名前缀）——UI 界面文案 / Lua 界面逻辑文案 / 剧情字幕台词 / 活动赛事 /
     成就徽章 / 玩法模式 / 物品道具经济 / 英雄角色外观 / 任务目标 / 邮件公告 / 提示引导 / 系统错误码 / 其他数据表；
  2. **字段语义**（按键名后缀）——名称 / 简称 / 描述 / 详情 / 备注 / 提示 / 标题 / 正文 / 公告 / 横幅页签 /
     按钮界面 / 说话人 / 字幕 / 台词；
  3. **内容主题标签**（按文本关键词）——周年洲年 / 彩蛋 / 庆典活动 / 新春红包 / 赛季 / 黑鹰坠落 /
     隐秘协议 / 曼德尔砖 / 经济代币 / 武器装备 / 测试占位 / 剧情台词。
- **浏览**：左侧选业务分类（不选表 = 浏览整类）或选具体表；中间条目列表（Key / 字段徽章 / 文本预览）；
  右侧详情（Key、业务域、字段、主题标签、本地化 ID、所属表、全文），支持复制 Key / 复制全文，双击条目快速复制 Key。
- **搜索**：Key 或文本子串（不区分大小写，220ms 防抖），可与标签、字段过滤叠加；单次列表最多渲染 60,000 条，
  超出会在标题栏提示缩小范围。
- **主题**：右上角「外观」三选（跟随系统 / 浅色 / 深色，见下方「外观」一节）。
- **导入**：工具栏「导入」可以换数据源 —— 选文件夹，或者**直接选一个 .zip 包**（不用先手动解压）；
  导入成功后会记住这个来源，下次启动自动用它。

## 导入自己的数据（文件夹 / ZIP 包）

工具栏「导入」下拉三项：

| 菜单项 | 说明 |
| --- | --- |
| 导入文件夹… | 选一个解压好的 StringTables 导出目录（和原来的「打开文件夹」一样） |
| 导入 ZIP 包… | 选一个 `.zip`：自动解压到缓存目录后再解析，**不需要先手动解压**，也支持「包里套一层同名文件夹」的包 |
| 清理解压缓存 | 删掉 `%LOCALAPPDATA%\DeltaStringBrowser\imports`（会先告诉你要释放多少 MB） |

**ZIP 是怎么处理的**

1. 解压到 `%LOCALAPPDATA%\DeltaStringBrowser\imports\<包名>-<字节数>-<写入时间>\`，
   并在里面写一个 `_import.stamp` 戳记（记录原包路径 / 长度 / 写入时间 / JSON 个数）；
2. 下次导入同一个包时先比对戳记，**没变就直接复用缓存**（不用再解压）；包改过则重新解压，
   同一包名的旧缓存目录会被删掉，不会越攒越多；
3. 如果包根目录没有 json、只有一层子目录（很常见：`xxx.zip` 里是 `StringTables/...`），
   会自动往下钻一层再当数据根，表名前缀不会多出那一层；
4. 安全上限：解压后不超过 4GB、条目不超过 20 万个，超了直接拒绝并说明原因（防止误选了一个巨大的包）；
   包里一个 json 都没有会明确报错，而不是给你一个「0 张表」的空界面；
5. 包内文件名不是 UTF-8 时（老工具打的包）会在状态栏提示「表名可能显示异常，建议先解压再用导入文件夹」。

**记住上次的来源**：导入成功后把来源写进 `%LOCALAPPDATA%\DeltaStringBrowser\settings.json`
（`LastSource` + `LastSourceKind`）。下次启动直接加载它；如果那个文件夹/压缩包已经不在了，
自动回落到「安装目录旁的 Data\StringTables」或开发机默认路径，并在状态栏说明。

**自检（不用进界面）**：

```powershell
# 走的是和界面「导入」完全同一条路径（ZIP 会真解压），输出表数 / 条数 / 业务域分布 / 样例后退出
DeltaStringBrowser.exe --importtest "D:\导出\StringTables.zip" report.txt
DeltaStringBrowser.exe --importtest "D:\导出\StringTables"      report.txt
```

实测（本机 852 个 json / 16.8MB 的导出）：

| 来源 | 结果 |
| --- | --- |
| 文件夹 | 841 张表 / 124,588 条 · 解析 0.57s |
| 同一个目录打的 zip（首次） | 841 张表 / 124,588 条 · 解压 1.4s + 解析 0.59s |
| 同一个 zip（第二次） | 缓存命中 · 合计 0.69s |
| 外面套一层同名文件夹的 zip | 自动下钻到内层 · 19 张表 / 557 条 |
| 只含 txt 的 zip | 明确报错「压缩包里没有找到 .json 文件」，不留缓存 |

## 构建

要求：Windows 10 1809+、.NET SDK 10（本机已验证 10.0.301）。

```bash
dotnet build -c Release
```

产物（自包含 Windows App SDK，免装运行时，免 MSIX 部署）：
`bin\Release\net10.0-windows10.0.19041.0\win-x64\DeltaStringBrowser.exe`

运行：

```bash
dotnet run -c Release   # 或直接双击上面的 exe
```

## 代码结构

```
DeltaStringBrowser/
├─ App.xaml(.cs)                    # 应用入口 (XamlControlsResources)
├─ MainWindow.xaml(.cs)             # 三栏主界面 + 全部交互逻辑
├─ Models.cs                        # DomainKind/FieldKind/TableInfo/StringEntry/列表 VM
└─ Services/
   ├─ Categorizer.cs                # 归类引擎：域规则 / 键后缀语义 / 关键词标签(12 位掩码)
   └─ StringTableDatabase.cs        # 并行解析 JSON → 域→表→条目索引
```

## 归类规则速览（Categorizer.cs，可自行增改）

- 业务域：目录命中 `STForLua`→Lua、`UIStringTables/Oversea`→UI、`STForMaps`→剧情，其余按文件名关键词
  （`EVENT/ACTIVITY`→活动、`GAMEITEM/LOTTERY/PENDANT`→物品、`MATCH/GAMEMODE`→玩法、
  `HERO/SPRAYPAINT/AVATAR`→英雄、`ACHIEVEMENT/BADGE`→成就 …）。
- 字段语义：优先 `SPEAKER/SUBTITLE/LINES` 片段，其次取末段后缀 `_Name/_Desc/_Detail/_Remark/_Text…`。
- 主题标签：12 组关键词逐条 `Contains`（OrdinalIgnoreCase），命中置位；「剧情台词」由字段类型派生。

## 说明

- 只读工具，不修改源 JSON。
- 空文本条目不收录；`Id` 缺失显示为 `-`。

## 外观（浅色 / 深色主题）

工具栏右侧的「外观」按钮里三选一，立即生效、立即保存：

| 模式 | 说明 |
| --- | --- |
| 跟随系统 | **默认**。跟着 Windows 的浅色/深色走，运行中系统换主题也会立刻跟上（监听 `UISettings.ColorValuesChanged`） |
| 浅色 | 强制浅色 |
| 深色 | 强制深色 |

- 持久化位置：`%LOCALAPPDATA%\DeltaStringBrowser\settings.json` 的 `theme` 字段（`System` / `Light` / `Dark`），
  写盘由 `Services/ThemeService.cs` 负责，读不到 / 值非法时按「跟随系统」处理。
- 实现方式：模式 → `RootGrid.RequestedTheme`（`ElementTheme.Default/Light/Dark`）。界面里没有写死的色值，
  全部走 `{ThemeResource …}`（`DividerStrokeColorDefaultBrush` 等）与控件默认主题色，所以换主题即整体变色。
- `System` 模式下 `ElementTheme.Default` 只在进程启动那一刻取一次系统主题，所以额外订阅系统颜色变化事件，
  变化时用系统背景色算感知亮度、解析成具体的浅/深再赋一次（否则跑着的窗口不会跟着系统换肤）。
- 之前那个只能「浅色 ↔ 深色」来回切的按钮已由这个三选菜单取代。

## 数据目录的查找顺序

程序启动时按下面顺序决定「默认数据目录」（也可以在界面里点「打开文件夹」随时换成别的目录）：

1. **安装目录旁的 `Data\StringTables`**（安装包内置的数据，安装版首次启动即可直接浏览）；
2. 开发机默认路径 `I:\新建文件夹 (19)\Exports\DeltaForce\Content\StringTables`。

## Alpha 定时过期版（独立发布的版本）

除正式版外，工程还能编译出一个 **限时 Alpha 测试版**，与正式版是两套独立产物，可同时安装：

- **过期时间**：北京时间 **2026 年 9 月 24 日 00:00** 正式过期（= 2026-09-23 16:00 UTC）。
- **校时方式**：启动时不信任本机时钟，向 8 个公网 NTP 服务器并发取时（SNTP/RFC 4330，国内优先），
  取往返延迟最小的一份作为网络时间，并校验响应来源与回显时间戳（防伪造）。
- **拦截规则**（任一命中即拦截，不进入主界面）：
  1. 系统时间与 NTP 时间相差超过 **5 分钟**（判定为改过系统时间）；
  2. 网络时间已达到/超过 2026-09-24 00:00；
  3. 所有 NTP 服务器都无有效响应（离线 / 防火墙拦截 UDP 123）。
- **统一提示文案**：

  > 此版本为Alpha测试版本。于2026年9月24日正式过期，请等待正式版本发布

  下面还有一行说明与「重新校验 / 退出程序」两个按钮；退出即关闭程序。
- **校验位置**：覆盖层直接铺在主窗口 `RootGrid` 上，**不额外创建窗口**
  （先建独立校验窗口再关掉、之后才建主窗口的写法会让主窗口渲染后原生崩溃，模型查看器已踩过这个坑）。
  数据解析仍在后台并行进行，校验通过后立刻可见。
- **运行期复核**：每 15 分钟用 NTP 再复核一次；已过期或时钟被改立即拦截，NTP 暂时不可达则连续两次失败才拦截。
- **界面标识**：窗口标题与左上角标题下都会显示「Alpha测试版 · 2026-09-24 过期（剩余 N 天）」。
- **日志**：`%LOCALAPPDATA%\DeltaStringBrowser\logs\alpha-guard.log`。

诊断与自测（不会进入主界面）：

```powershell
# 真实校时结果，退出码 0=通过 3=未通过
DeltaStringBrowser.exe --timecheck report.txt

# 边界自测（只复算结论，不改变实际校验行为）
DeltaStringBrowser.exe --timecheck report.txt --at "2026-09-24T00:00:00+08:00"   # → Expired
DeltaStringBrowser.exe --timecheck report.txt --drift 3600                       # → ClockMismatch

# 界面自测：强制走拦截界面（只拦截、不放行，不是绕过手段）
$env:DMV_ALPHA_FORCE_BLOCK="Expired"; .\DeltaStringBrowser.exe
```

编译 / 发布 / 打包：

```powershell
# 发布（自包含 .NET + WindowsAppSDK，免装运行时）
dotnet publish -c Alpha -r win-x64 -p:SelfContained=true

# 安装包（Inno Setup 7；含程序本体 + 内置 StringTables 数据，约 17MB / 852 个 JSON）
#   先把 publish 目录内容拷到 installer\app，数据放到 installer\Data\StringTables，再编译脚本
ISCC.exe installer\DeltaStringBrowser_Alpha.iss
# → installer\DeltaStringBrowser_Alpha_Setup.exe
```

要点：WinUI 3 的 `dotnet publish` 不会自动把 XAML 编译产物（`*.xbf` 与 `DeltaStringBrowser.pri`）带进 publish 目录，
少了它们运行时会报 `Cannot locate resource from 'ms-appx:///MainWindow.xaml'`，工程里已加
`CopyXamlResourcesAfterPublish` 目标自动补齐。Release 正式版不定义 `ALPHA_EXPIRY`，
`Services/TimeGuard.cs` 整文件被 `#if` 排除，行为与原先一致。

## 应用图标

图标由仓库里的 `Assets\app.ico` 提供（exe 内嵌 + 运行时 `AppWindow.SetIcon` 设窗口/任务栏图标），
安装包用同一个 ico（`installer\app.ico` → `SetupIconFile`）。
生成工具在 `E:\新建文件夹 (8)\tools\`（`icon-gen.ps1` + `AppIconMaker.cs`）：
把一张「纯黑背景 + 居中图形」的 PNG 自动抠掉黑底（从四边泛洪填充，图形内部的黑色保留）、
按内容包围盒裁切补成正方形，再输出 16/24/32/48/64/128/256 七种尺寸的 ICO 与各尺寸 PNG。
再次生成：

```powershell
powershell -File "E:\新建文件夹 (8)\tools\icon-gen.ps1" -Source 3.png -IcoOut Assets\app.ico `
    -PngDir android\Resources\png -PngSizes 48,72,96,144,192,512
```

## 安卓端（android\）

`android\` 是一个 .NET for Android 工程，**直接链接复用桌面版的纯 C# 逻辑**
（`Models.cs` / `Services\Categorizer.cs` / `Services\StringTableDatabase.cs` / `Services\TimeGuard.cs`），
所以归类规则、搜索行为、时效校验与桌面版完全一致：

- 数据：852 个 StringTables JSON 作为 APK 资源打包（`android\Assets\StringTables`，约 17MB），
  首次启动复制到应用私有目录（`filesDir\StringTables`）后按普通文件加载，之后启动不再复制。
- 界面：搜索框 + 业务域/表两级下拉过滤 + 条目列表（Key + 文本预览）；点条目弹窗看全文，
  可「复制全文 / 复制 Key」（长文本用系统剪贴板）。
- 时效：与 Windows 版同一套 NTP 校时闸门（默认开启，过期 2026-09-24 00:00 北京时间，
  提示同一句文案，带「重新校验 / 退出程序」）。需要不带时效的构建：加 `-p:EnableAlphaExpiry=false`。
- 需要 `INTERNET` 权限（仅用于 NTP 校时，不联网传任何数据）。

构建（本机已装好用户级 .NET 10 SDK 于 `E:\dotnet10`、Android SDK 于 `E:\Android\Sdk`、JDK 21）：

```powershell
# 一键脚本（自动定位 SDK/JDK，产出 android\dist\*.apk）
powershell -File android\build-apk.ps1              # 带时效闸门
powershell -File android\build-apk.ps1 -NoExpiry     # 不带时效闸门

# 或手动：
& E:\dotnet10\dotnet.exe publish android\DeltaStringBrowser.Android.csproj -c Release -f net10.0-android `
    -p:AndroidSdkDirectory=E:\Android\Sdk -p:JavaSdkDirectory="C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot" `
    -p:AndroidPackageFormat=apk
```

产物：`android\dist\StringTablesBrowser-Android-Alpha-20260924.apk`（约 16.7 MB，四套 ABI，含 852 个 JSON）。
已在 Redmi（代号 rodin / Android 16 / arm64-v8a）真机验证：安装 → 启动 → 加载 124,588 条文本 → 搜索/过滤/复制均正常，
时效闸门在手机上同样按 NTP 网络时间判定（实测 7 个服务器响应、偏差 0.36s、校验通过）。

### 构建踩过的坑（已在工程里修正，改工程时别踩回去）

| 现象 | 根因 | 正确做法 |
|---|---|---|
| **装上后一点图标就闪退**，logcat：`UnsatisfiedLinkError: No implementation found for ... MainActivity.n_onCreate` | csproj 里写了 `PublishTrimmed=false`：.NET 10 的 Android 要靠链接器（ILLink）产出 JNI marshal 方法表与 typemap，关掉裁剪后 Activity 的 native 方法**根本不注册** | **不要**设置 `PublishTrimmed=false`（Release 默认裁剪即可，本工程还顺带把包体从 83MB 降到 16.7MB） |
| 启动崩 `Invalid compressed assembly descriptor index` | 命令行传了 `-p:DefineConstants=...`：它是**全局属性**，会让工程里 `$(DefineConstants);ALPHA_EXPIRY` 的追加失效，还会污染增量构建的 assembly store | 不要用命令行改 `DefineConstants`；需要切换时效开关请用 `-p:EnableAlphaExpiry=false` |
| 手动装 Debug 版闪退，logcat：`No assemblies found in .../.__override__/… Assuming this is part of Fast Deployment` | Debug 默认走 Fast Deployment（程序集由 IDE 推送，不在 APK 里） | 手动安装用 Release，或加 `-p:EmbedAssembliesIntoApk=true` |
| 启动器里应用名显示成包名 `com.pufferpan.stringtables` | 去掉了自定义 `Application` 类后，应用级 label 没人设置 | 用 `android\Properties\AndroidManifest.xml` 声明 `android:label` / `android:icon`（工程里已加） |
| 手机 ABI 不匹配（32 位 ARM） | 默认只打 arm64-v8a + x86_64 | csproj 里 `RuntimeIdentifiers` 写明四套 ABI（工程里已加） |

---

## 本仓库包含 / 不包含

**包含**：Windows 端全部 C#/XAML 源码、安装脚本；安卓端 Kotlin 源码（`android-kotlin/`）。

**不包含**（版权或个人凭据原因）：

- `installer/Data/StringTables/**`：**游戏本地化数据**（约 17MB JSON），版权归原厂商，不随仓库分发。
  Windows 端请用自己的导出目录（程序会记住上次的数据源）；安卓端用「导入」功能导入你自己的导出（文件夹或 ZIP）。
- `keystore.properties`、`keystore/*.jks`：签名密钥属个人凭据。`android-kotlin/` 里是**可选**读取，没有它照样能构建 debug 包。
- `android/`：早期 .NET-for-Android 试验工程，非交付物，已从仓库排除（正式安卓端是 `android-kotlin/`）。
- 构建产物与已打包的安装包/APK。

## 安装包 / APK

见本仓库的 **Releases**。

## 许可证

MIT（见 [LICENSE](LICENSE)）。第三方组件：Windows App SDK（MIT）、Jetpack Compose / AndroidX（Apache-2.0）。
