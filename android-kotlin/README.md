# 三角洲行动 · StringTables 分类浏览器（Android）

把 UE 导出的 `DeltaForce/Content/StringTables` 本地化 JSON（**852 个文件 / 841 张表 /
124,588 条文本**）在**构建期**按桌面版
`F:\arg\DeltaStringBrowser` 的归类规则离线分类，打包为 SQLite 快照资产；
App 首次启动把快照复制到设备数据库目录并持久化，此后完全离线浏览。

- 技术栈：Kotlin + Jetpack Compose **Material 3（1.4.0，含 Expressive 稳定 API）**
- 主题：**Blue 浅色**（配色全部经 colorScheme 角色引用；仅浅色模式）
- 形态：竖屏手机（412×892dp 为目标），单 Activity + Navigation Compose
- 图标：**原生 Material Icons**（androidx `material-icons-core/extended` 的
  ImageVector，Rounded 风格，随库编译进包，无任何字体依赖）
- 数据：收藏（用户数据）与快照同库持久化，重启保留；重建数据库时收藏自动迁移

## 交付物

| 产物 | 路径 |
| --- | --- |
| 已签名 Release APK | `app/build/outputs/apk/release/app-release.apk` |
| 调试 APK | `app/build/outputs/apk/debug/app-debug.apk` |
| 离线分类快照 | `app/src/main/assets/strings.db`（由脚本生成） |

## 构建

环境：JDK 17+（本机 21）、Android SDK（platform 36 + build-tools 36）、
Gradle 8.14.3（`E:\Gradle\gradle-8.14.3`，可改 `gradle-wrapper.properties` 用官方源）。

```powershell
$env:JAVA_HOME = '<JDK>'
$env:ANDROID_HOME = 'E:\Android\Sdk'
$env:GRADLE_USER_HOME = 'E:\GradleHome'   # 可选：把 ~2GB 依赖缓存放到 E 盘

E:\Gradle\gradle-8.14.3\bin\gradle.bat -p . :app:assembleRelease
```

签名：`keystore.properties` + `keystore/release.jks`（自生成，仅本工具使用）。

## 重新生成数据快照（改规则或换数据源后）

```bash
python tools/build_db.py "I:\新建文件夹 (19)\Exports\DeltaForce\Content\StringTables" app/src/main/assets/strings.db
```

规则与桌面版一致（Categorizer.cs 的移植）：

- **业务域（13 个）**：目录命中 `STForLua`→Lua、`STForMaps`→剧情、
  `UIStringTables`/`Oversea`→UI、`STForCodes`→系统；其余按文件名关键词
  （EVENT/ACTIVITY→活动、GAMEITEM/LOTTERY→物品、HERO/SKIN/AVATAR→英雄、
  TASK/QUEST→任务、ACHIEVEMENT/BADGE→成就、MATCH/GAMEMODE/RAID→玩法、MAIL→邮件、
  TIP/TEACHING/MANUAL→引导、ERRCODE/ERROR/CODE→系统，未命中→其他数据表）。
- **字段语义（15 类）**：优先 `SPEAKER/SUBTITLE/LINES` 片段，其次键末段后缀
  （`_Name/_Desc/_Detail/_Tip/_Title/_Text/_Btn/_Banner…`）。
- **内容主题标签（12 组）**：文本关键词（周年/彩蛋/庆典/新春红包/赛季/黑鹰坠落/
  隐秘协议/曼德尔砖/经济代币/武器装备/测试占位）；“剧情台词”由字段类型派生。

## 界面（4 Tab + 详情流）

1. **分类**：总览统计 + “全部条目” + 13 个业务域（表数/条数），进入后
   “数据表 ⇄ 条目”双模式（条目模式内可搜索 + 字段/标签过滤）。
2. **全局搜索**：Key / 文本子串（不区分大小写），支持业务域快速片、
   字段语义与主题标签叠加，结果自动分页。
3. **收藏**：条目收藏持久化在设备，支持删除（可撤销）、清空（需确认）。
4. **设置**：数据快照信息、分类体系说明、数据维护（重建本地库需确认）；
   「关于」注明“软件制作：河豚潘PufferPan”。

条目详情页：Key（复制）、全文（可选中/复制/分享）、业务域、字段徽章、
本地化 ID、所属表（可跳转）、主题标签、收藏切换。

动效：页面转场 `slide + fade`，位移 300ms / 透明度 260ms（FastOutSlowIn），返回键/返回手势与
返回按钮播放同一套反向动画；点击项带涟漪 + 0.975 轻缩放。
玻璃质感：确认/信息对话框（Android 12+ 系统级背景模糊 + 半透明容器，低版本自动降级）、
下拉菜单与底部导航条为半透明磨砂面板（发丝线描边）。

## 主题（跟随系统 / 浅色 / 深色）

设置页最上方「外观」卡里三选一，立即生效；默认**跟随系统**。

| 模式 | 说明 |
| --- | --- |
| 跟随系统 | 默认。用 `isSystemInDarkTheme()` 决定，系统切换深色模式时整个界面跟着变 |
| 浅色 | 强制 `LightColors` |
| 深色 | 强制 `DarkColors`（同一 Blue 系推导出的 M3 深色角色，primary `#A8C7FA` / 背景 `#101418`） |

- 持久化：`SharedPreferences` 文件 `stringtables_prefs`，key `theme_mode`，存枚举名 `SYSTEM` / `LIGHT` / `DARK`；
  值缺失或非法一律回落「跟随系统」。实现在 `ui/theme/ThemeMode.kt`（`ThemeMode` + `ThemeStore` + `ThemeController`）。
- 界面里所有颜色都走 `MaterialTheme.colorScheme.*` 角色（不写死色值），所以换 scheme 即整体变色；
  `AppTheme(mode)` 包住了 Alpha 校时闸门与主界面，两者一起变。
- 系统栏（状态栏/导航栏）图标明暗也跟着主题走：`MainActivity.applySystemBarStyle(...)`；
  `values-night/` 里另给了夜间窗口底色，避免 Compose 首帧之前闪白底。
- 已知小瑕疵：若系统是深色而用户强制选「浅色」，Compose 首帧**之前**的窗口底色仍会是深色（极短暂闪一下），
  随后即按用户选择绘制。

> 备注：按规格优先使用 `MotionScheme.standard()`，但 material3 **1.4.0 稳定版**将其标为
> `internal`（类/工厂对应用不可见），故按同一标准令牌实现（见 `ui/theme/Theme.kt` 注释），
> 待官方公开后可直接替换。

## 目录结构

```
app/src/main/
├─ assets/strings.db           # 离线分类快照（sqlite，工具生成）
├─ java/com/dfm/stringtables/
│  ├─ MainActivity.kt          # 浅色 edge-to-edge + AppTheme
│  ├─ data/                    # AppDb(SQLite 导入/查询/收藏) · Labels · Models · FavHub
│  └─ ui/
│     ├─ App.kt                # 导航 + 底部栏 + 全局 Snackbar + 转场
│     ├─ theme/Theme.kt        # M3 Blue 浅色 colorScheme + 形状
│     ├─ Symbols.kt            # 原生 Material Icons（ImageVector）常量与渲染
│     ├─ Common.kt / EntriesPane.kt
│     ├─ HomeScreen / DomainScreen / TableScreen
│     ├─ SearchScreen / FavoritesScreen / DetailScreen / SettingsScreen
└─ res/ …                      # 主题、启动图标（adaptive）
tools/
└─ build_db.py                 # 分类 + SQLite 快照生成（确定性排序）
```

> 说明：目录名“StringTables 浏览器 / com.dfm.stringtables”为本地工具命名，
> 与三角洲行动官方无隶属关系；数据为 UE 导出的本地化文本，仅供学习研究。

## Alpha 定时过期版（NTP 校时闸门）

启动时**不信任本机时钟**，直接向公网 NTP 服务器取时（SNTP / RFC 4330），校验通过才进主界面：

- **过期时刻**：北京时间 **2026-09-24 00:00**（`TimeGuard.expiry = 2026-09-23T16:00:00Z`）。
- **服务器**：`ntp.aliyun.com` / `ntp1.aliyun.com` / `ntp.tencent.com` / `cn.pool.ntp.org` /
  `ntp.ntsc.ac.cn` / `time.windows.com` / `time.apple.com` / `pool.ntp.org`（并发查询，取往返延迟最小的样本；
  校验 mode / stratum / 回显时间戳防伪造）。
- **拦截条件**（任一命中，只显示提示，不进入主界面）：
  1. 系统时间与 NTP 相差超过 **5 分钟**（判定为改过系统时间）；
  2. 网络时间已过 2026-09-24 00:00；
  3. 所有 NTP 服务器都无有效响应（离线 / 防火墙拦 UDP 123）。
- **统一提示文案**：`此版本为Alpha测试版本。于2026年9月24日正式过期，请等待正式版本发布`
  （下面一行灰字说明失败原因，另有「重新校验 / 退出程序」两个按钮）。
- **运行期复核**：通过后每 15 分钟再取一次网络时间；已过期 / 时钟被改立即拦截，
  NTP 暂时不可达则连续两次（约 30 分钟）才拦截，避免单次网络抖动打断使用。
- **日志**：logcat 标签 `DSB-Alpha`（每次校时的服务器、stratum、往返、偏差都会打印）。

相关文件：

| 文件 | 作用 |
| --- | --- |
| `data/TimeGuard.kt` | SNTP 取时与判定（纯逻辑，`check()` / `evaluate()`） |
| `ui/TimeGateScreen.kt` | 闸门界面（校验中 / 校验未通过）+ `AlphaTimeGate` 接线（启动闸门 + 运行期看门狗） |
| `MainActivity.kt` | `AlphaTimeGate(onExit = { finish() }) { AppRoot() }` |
| `AndroidManifest.xml` | 增加 `android.permission.INTERNET`（仅用于校时，不上传任何数据） |

自定义过期日 / 版本号：`TimeGuard.expiry`（UTC Instant）与 `app/build.gradle.kts` 的
`versionCode` / `versionName`。自测拦截界面时可临时把 `expiry` 改成一个过去的时间再构建。

构建（Release 已签名，可直接覆盖安装商店外的同签名版本）：

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot'
$env:ANDROID_HOME='E:\Android\Sdk'
$env:GRADLE_USER_HOME='E:\GradleHome'      # 依赖缓存（约 1.5GB）
E:\Gradle\gradle-8.14.3\bin\gradle.bat -p . :app:assembleRelease
# 产物：app\build\outputs\apk\release\app-release.apk
```

## 启动图标（3.png）

启动图标已按 `3.png`（黑底 + 居中图形）重新生成，不再是原来的蓝底表格线稿：

| 资源 | 内容 |
| --- | --- |
| `mipmap-<density>/ic_launcher.png`（48/72/96/144/192） | 传统图标：纯黑底 + 原 logo 构图 |
| `mipmap-<density>/ic_launcher_foreground.png`（108/162/216/324/432） | 自适应图标前景：透明底，图形居中占画布 60% |
| `mipmap-anydpi-v26/ic_launcher.xml` | background = `@color/ic_launcher_background`（`#000000`，logo 的黑底）；foreground = 上面的前景 PNG；monochrome 仍用表格线稿剪影（Android 13+ 主题图标） |
| `values-v31/themes.xml` | Android 12+ 启动画面：黑底 + 新图形 |

重新生成（换 logo 或调图形占比时）：

```powershell
powershell -File "E:\新建文件夹 (8)\tools\make-android-icons.ps1" `
  -Source 3.png -ResDir app\src\main\res -ForegroundRatio 0.60
```

> 提示：部分启动器（含 MIUI）会缓存图标，装完后若还显示旧图标，下拉刷新桌面或重启一次即可。
