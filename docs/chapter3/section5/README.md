# strings.json 本地化：分类与代码取值

对象风味文本（`descriptions.csv`）已在前置讲解中讲完，本节补上文本链路的最后一环：`data/strings/strings.json`——**代码驱动的界面文本**入口，用两级键（分类 → 键名）组织、由代码经 `Global.getSettings().getString(category, id)` 取值、跨 MOD 按键合并，并以 `GearIn_text_*` 演示键走完 "文件 → 合并 → 取值 → 可观察结果" 的完整流程。与 `descriptions.csv` 不同，strings.json 的错误绝大多数**不炸启动**：键写错只表现为界面/日志出现 `Missing string: [...]` 字面量；只有 JSON 语法错误（括号不配对、引号不闭合）和 BOM 污染才会在加载期中止启动。

*本节预期阅读时间：~12 分钟。*

*本节预期操作时间：~20 分钟。*

## 学习目标

完成后，您将能够：

- 说出 RC8 `strings.json` 的两级结构（分类 → 键名）与全部 4 个原版分类，以及每个分类的主要使用方；
- 用 `Global.getSettings().getString(category, id)` 取值，并说清它与单参 `getString(key)`（读取 `settings.json`）的区别；
- 说出跨 MOD 合并语义（对象按键合并、标量后者覆盖、数组追加）与 "不要重定义既有键" 的原因；
- 说出缺失键的可观察结果：返回字面量 `Missing string: [<id>]` 并在 `starsector.log` 记 ERROR，**不抛异常、不崩溃**；
- 说清 `$token` 不会被引擎自动替换、`%s` 需要取值侧自行 `String.format`；
- 为 `gear_in` 组织 `GearIn_text_*` 键（分类 `GearIn_ui` / `GearIn_dialog`），并通过日志与界面观察验证完整流程。

## 前置知识

本节建立在以下基础上：

- **MOD 目录结构、`mod_info.json` 与 JSON 文件加载规则**（C01-S01）：`#` 注释与尾逗号被接受、UTF-8 无 BOM 必须；日志锚点的读法。
- **`descriptions.csv` 的键规则与使用方**（C01-S07、C03-S04）：对象风味文本 —— 本节是它的对照面。
- **术语约定**：生涯（campaign）；交互对话（interaction dialog，如舰队遭遇、跳跃点、行星接近时的对话界面）；令牌（token，指 `$xxx` 形式的占位符）；规则命令（rule command）。

## 文件/API 链路

### 文件与注册入口

数据文件是 MOD 内的 `data/strings/strings.json`。**这个路径本身就是注册入口**：不需要 `mod_info.json` 里的任何字段，也不需要代码注册 —— 启动加载时游戏自动遍历原版与全部已启用 MOD 的同名文件并合并。

加载发生在启动资源加载状态内、数据表加载之前（本地核对）：

1. 启动资源加载状态的初始化流程触发字符串表加载；
2. 字符串表加载先打日志 `Loading strings`，然后用 JSON 读取工具读取 `data/strings/tooltips.json` 与 `data/strings/strings.json`，最后读取 `data/strings/tips.json`（三个文件属于同一加载点，见 "边界"）；
3. `readJSON` 即 `readJSONMerged`：每个被读取的文件（原版与各 MOD 各一份）在日志里各出现一行 `Loading JSON from [...]`，路径片段包含 `data/strings/strings.json`。

### 合并语义（跨 MOD）

`readJSONMerged` 的规则（本地核对）：

- 第一个非排除文件（即原版）作为 "主档"，其后每个 MOD 的文件依次合并进主档；
- **对象值**递归按键合并 —— 每个键单独处理；
- **标量值**（字符串等）后来者覆盖前者 ——MOD 可以覆盖原版或其他 MOD 的同一个键；
- **数组值**默认追加；键名小写以 `music_` 开头、键名含 `color`/`button` 且数组长度为 4、或数组首元素为 `"core_clearArray"` 时整体替换；
- `mod_info.json` 的 `fullOverrides` 可以整体替换某个文件（细节由后续文件加载相关单元讲解）。

对 MOD 的含义：**只需要在文件里写自己的键**，原版与其余 MOD 的键自动保留；反过来，"覆盖" 顺序取决于 MOD 启用顺序，**不是约定**。

::: caution 禁止重定义既有键

不要重定义原版或其他 MOD 已有的 `(category, id)` 组合：重定义键的文本会被启用顺序靠后的文件静默覆盖，没有任何报错（症状与排查见实例驱动故障分析的 "取到的是别的 MOD 的文本" 条目）。

:::

### 语法与编码（必须遵守的规则）

加载前文本先经 `parseJSONStrippingComments`（本地核对）处理：**引号之外的 `#` 到行尾整段剥离**（原版文件本身就大量使用 `#` 注释块），随后交给 JSON 解析器；**尾逗号被接受**（RC8 原版 `campaignHelp` 分类最后一个键后面就有逗号）。

::: caution 文件必须 UTF-8 无 BOM，JSON 语法必须正确

违反任一条，加载期都会抛 JSONException（异常消息会拼上文件路径），启动在加载状态中止：BOM 使 `\ufeff` 粘在文件开头、整个文件解析失败；语法错误来自括号不配对或字符串引号不闭合。

:::

1. **文件必须 UTF-8 无 BOM。** 违反后果：`readText` 不剥离 BOM，`\ufeff` 粘在文件开头使整个文件解析失败，启动在加载状态中止，日志出现带文件路径的 JSONException。
2. **JSON 语法必须正确**（括号配对、字符串引号闭合）。违反后果：加载期抛 JSONException（异常消息会拼上文件路径），启动中止。注意 `#` 注释只剥引号外的，引号内的 `#` 是普通字符。
3. **键与分类名必须逐字符一致（大小写敏感）。** 违反后果：取不到值，见下一条 "缺失行为"—— 不报错，但界面与日志出现 `Missing string: [...]`。

### 取值 API

运行期取值入口是公开 API：`Global.getSettings().getString(String category, String id)`（`SettingsAPI`，RC8 API 解包，方法 javadoc 原文："Gets a string from a given category in strings.json"）。

实现链（本地核对）：`SettingsAPI` 实现最终从合并后的字符串表取值 —— 先取 `category` 分类对象，再取其中的 `id` 键。

**缺失行为（必须遵守的规则）**：分类或键任一不存在，内部捕获 `JSONException`，先打日志 `logger.error("Missing string: [" + id + "]")`，再**返回字面量 `Missing string: [<id>]`**。也就是说：

- 调用方**不会**收到异常，游戏**不会**崩溃；
- 界面会把这个字面量原样渲染出来；
- `starsector.log` 会出现一条 ERROR（注意日志里只有 id，没有 category）。

::: warning 单参与双参：本主题最经典的混淆点

`SettingsAPI` 里还有一个单参 `getString(String key)`，它读取的是 `data/config/settings.json`，与 strings.json 无关。

**想读 strings.json 必须用双参重载**；误用单参会取到 settings.json 里的同名配置，表现为 "取到的值完全不对"。

:::

### 令牌与格式化

strings.json 的值里有两种占位符，**引擎对两者都不做代换**：

- `$token` 形式（如 `$faction`、`$fleetName`、`$planetName`、`$yourForcesWereOrYourSideWas`）：由**使用方**负责替换。原版 `FleetInteractionDialogPluginImpl.getString` 取到文本后逐个执行 `str.replaceAll("\\$faction", faction)` 手工替换（公开 API 解包）。MOD 自己的 `$token` 文本如果没人替换，界面会原样显示 `$faction`。
- `%s` 形式：由**取值侧**负责 `String.format`。游戏引擎不做格式化；不匹配时运行期抛 `IllegalFormatException`（成熟实例以 try/catch 捕获后返回 `[NULL]`，见 "本地化组织"）。

### RC8 原版分类总览

键数统计自 `Starsector/starsector-core/data/strings/strings.json`（该文件共 4 个顶层分类）：

| 分类 | 内容 | 键风格 | 主要使用方 | 键数 |
|---|---|---|---|---|
| `fleetInteractionDialog` | 生涯舰队遭遇对话：交战/追击/撤退/接舷/打捞/友军增援等 | 大量 `$faction` `$fleetName` `$fleetOrShip` `$yourForcesWereOrYourSideWas` 等令牌 | `FleetInteractionDialogPluginImpl.getString`：`Global.getSettings().getString("fleetInteractionDialog", id)` 后逐个 `replaceAll` 替换令牌 | 160 |
| `jumpPointInteractionDialog` | 跳跃点交互：`approach`/`starAnchor`/`noFuel`/`noExits` 等 | `$fleetOrShip` | `JumpPointInteractionDialogPluginImpl`（同一 `getString` 模式）| 5 |
| `planetInteractionDialog` | 行星接近文本：`approach` | `$fleetOrShip` `$planetName` | `PlanetInteractionDialogPluginImpl`（同一 `getString` 模式）| 1 |
| `campaignHelp` | 首次生涯提示弹窗：`chmNewgame`/`chmDeployCritCR`/`chmLowFuel`/`chmEnteredHyper` 等 | 键后缀约定 `_text`/`_dismiss`/`_action` | 弹窗文本按 `id + "_text"` 等约定拼接键名后双参取值（本地核对）；触发方是 `chm*` 状态机（首次满足条件时弹窗一次）| 63 |

要点：

- **`(category, id)` 两级键都区分大小写、逐字符匹配**；分类和键名都不存在 "模糊匹配"。
- 原版键可以被 MOD 覆盖（标量后者覆盖），但依赖启用顺序的覆盖不是约定 —— 教程一律要求**不重定义既有键**。
- 对话类值靠 `$token` 注入动态内容；帮助类值里是 `\n` 多段文本。
- 这四个分类是原版的自有分类；MOD 完全可以（且更建议）使用自己的分类名（见下）。

### 边界：同目录的其他文件与单参 API

- `data/strings/tooltips.json`：同一加载点读取，但**与 strings.json 不合并、键空间独立**，走独立的悬停提示取值入口（本地核对）：值可以是纯字符串，也可以是 `{"title":..., "body":...}` 对象；主要被船员列表等界面的悬停提示消费。**不要试图用 `getString(category, id)` 读它。**
- `data/strings/tips.json`：同一加载点读取，是加载界面随机显示的小贴士。
- `data/config/settings.json`：单参 `getString(key)` 的读取目标（含 `defaultFont` 等全局配置），不是文本层。

## 本地化组织

- **两级键即命名空间**：键的唯一性只在 `(category, id)` 层面。两个 MOD 各写各的分类或各写各的键，互不干扰；只有分类与键都相同才构成冲突。
- **分类按功能域命名，键带 MOD 前缀**是成熟惯例。实例佐证：
  - 有的 MOD 用语义分类（如 `Intel`/`Event`/`HullMod`/`ShipSystem`/`Misc`/`CMD`/`Perk`），键全部带 MOD 前缀（如 `<前缀>_distress_call_title`、`<前缀>_commission_text_1`），值里混用 `%s` 与内部约定的 `{%s}`；消费封装在 `I18nSection` 工具类： `Global.getSettings().getString(category, keyPrefix + key)` + `String.format`，异常时返回默认值 `[NULL]`。
  - 也有的 MOD 分类精简、键全部带 MOD 前缀，整文件直接写中文（本地化 = 直接改值，不改键）。
  - 这些实例都没有使用原版分类名，也没有重定义原版键。
- **gear_in 的组织**：分类用 `GearIn_ui`（界面文本）与 `GearIn_dialog`（对话文本），键统一 `GearIn_text_*`（本节所有权）。新文本只往这两个分类加键。
- **键是约定，值是内容**：本地化只改值，永不改键。代码里的 `getString(category, id)` 参数是编译期约定，改名必须代码与文件同步改。
- **与 `descriptions.csv` 的分工**：对象风味文本（舰船/武器/行星/势力……）走 `descriptions.csv`（C01-S07、C03-S04）；代码驱动的界面标签、按钮、弹窗、对话行走 strings.json。同一段文本如果既按对象查又按代码查，两者各放各的文件。

## 完整最小例

以下内容全部放在虚构示例 MOD（`gear_in`，见 C01-S01）中。本节只声明 `GearIn_text_*` 键；若 MOD 尚无 `data/strings/strings.json` 则新建该文件，整个文件只需要一个顶层对象：

```json
{
	# 本节拥有的分类：界面文本
	"GearIn_ui": {
		"GearIn_text_hello": "你好，工坊。",
		"GearIn_text_ship_name": "%s 正在向 %s 靠近。",
		"GearIn_text_dismiss": "关闭",
		"GearIn_text_unknown": "未知对象",
	},
	# 本节拥有的分类：对话文本
	"GearIn_dialog": {
		"GearIn_text_approach": "你的舰队正在接近 $planetName。",
		"GearIn_text_dock": "停靠请求已接受。",
	}
}

```

要点：

- `#` 注释与尾逗号（`GearIn_text_unknown` 与 `GearIn_text_dock` 后的逗号）都是加载器接受的；注释不进入运行期。**严格 JSON 工具不接受这两者**，用工具静态检查时要注意。
- 所有键为 `GearIn_text_*`（本节所有权）；分类 `GearIn_ui` / `GearIn_dialog` 为语义命名；任何地方都不出现工作单元编号。
- 三种值形态各演示一条：纯文本、含 `%s`、含 `$token`。
- 该文件不依赖任何注册入口 —— 放对路径即被读取并合并。

代码侧是取值封装（完整源码，与本单元 fixture 中的 `GearInI18n` 类逐字一致）：

```java
package data.scripts.gearin.campaign;

import com.fs.starfarer.api.Global;

/**
 * strings.json 的两级取值封装（示例 MOD gear_in 的完整最小例）。
 *
 * 事实依据（Starsector 0.98a-RC8）：
 * - Global.getSettings().getString(category, id) 读取 data/strings/strings.json；
 * - 分类或键不存在时返回字面量 "Missing string: [<id>]" 并在 starsector.log 记
 *   ERROR，不抛异常、不崩溃；
 * - "$xxx" 令牌不会被引擎替换，由消费方自行处理（见 replaceToken）；
 * - "%s" 模板由取值方用 String.format 填充（见 format）。
 */
public final class GearInI18n {

	/** gear_in 的界面文本分类。 */
	public static final String CATEGORY_UI = "GearIn_ui";
	/** gear_in 的对话文本分类。 */
	public static final String CATEGORY_DIALOG = "GearIn_dialog";

	private GearInI18n() {
	}

	/** 两级取值：category 与 id 均区分大小写、逐字符匹配。 */
	public static String get(String category, String id) {
		return Global.getSettings().getString(category, id);
	}

	public static String ui(String id) {
		return get(CATEGORY_UI, id);
	}

	public static String dialog(String id) {
		return get(CATEGORY_DIALOG, id);
	}

	/** %s 模板代换：先取值，再 String.format。占位符与实参不匹配会抛 IllegalFormatException。 */
	public static String format(String category, String id, Object... args) {
		String template = get(category, id);
		if (args == null || args.length == 0) {
			return template;
		}
		return String.format(template, args);
	}

	/** $token 手动替换：strings.json 的 $xxx 不会自动替换（原版对话插件逐个 replaceAll 处理）。 */
	public static String replaceToken(String text, String token, String value) {
		return text.replace("$" + token, value);
	}
}

```

调用示例与预期返回值：

| 调用 | 返回 |
|---|---|
| `GearInI18n.ui("GearIn_text_hello")` | `你好，工坊。` |
| `GearInI18n.format("GearIn_ui", "GearIn_text_ship_name", "工坊之锤", "Chicomoztoc")` | `工坊之锤 正在向 Chicomoztoc 靠近。` |
| `GearInI18n.dialog("GearIn_text_approach")` | `你的舰队正在接近 $planetName。`（令牌未替换时的原样输出）|
| `GearInI18n.replaceToken(GearInI18n.dialog("GearIn_text_approach"), "planetName", "Jangala")` | `你的舰队正在接近 Jangala。` |
| `GearInI18n.ui("GearIn_text_does_not_exist")` | `Missing string: [GearIn_text_does_not_exist]`，同时 `starsector.log` 出现 ERROR `Missing string: [GearIn_text_does_not_exist]` |

静态验证（写完后、进游戏前）：键全文件唯一（`GearIn_text_*` 前缀天然保证）、JSON 语法用严格工具检查时剔除注释与尾逗号、文件保存为 UTF-8 无 BOM。

### 验证方案（基于本例）

验证入口为生涯（舰队遭遇对话、跳跃点/行星接近文本、`campaignHelp` 提示弹窗）与 `starsector.log`，对象为本节的 `GearIn_text_*` 键与 `GearInI18n` 类。

- **前置数据**：`gear_in` MOD（C01-S01）与对象风味文本数据已就绪；本节 `data/strings/strings.json`（`GearIn_text_*` 键）与 `GearInI18n` 已放置就位。
- **操作**：
  1. 启动游戏进入生涯，确认日志出现 `Loading strings` 且无 ERROR；随后应看到包含 `data/strings/strings.json` 的 `Loading JSON from [...]` 行（原版与 gear_in 各一行）。
  2. 在 gear_in 的 `modPlugin`（C01-S01）临时加日志探针：`Global.getLogger(GearInI18n.class).info(GearInI18n.ui("GearIn_text_hello"));`，进生涯后核对输出为 `你好，工坊。`。
  3. 把探针键名改为 `GearIn_TEXT_hello` 重启：日志出现 ERROR `Missing string: [GearIn_TEXT_hello]`，探针打印字面量 `Missing string: [...]`—— 确认缺失行为不崩溃；改回后恢复。
  4. 修改 `GearIn_text_hello` 的值后重启，探针输出随之变化 —— 证明文件确实被读取并合并（不是缓存）。
  5. 故障复现（语法）：把 `GearIn_text_hello` 的值去掉一个引号后启动，确认加载在启动状态中止、日志出现带文件路径的 JSONException；改回后恢复。再把文件加 UTF-8 BOM 保存后启动，确认同类解析失败；恢复无 BOM。
  6. 回归：`descriptions.csv` 的对象风味文本照旧显示 —— 两套文本互不干扰。
- **通过信号**：日志与界面文本与文件内容一致；日志无 ERROR；键缺失与语法两种故障均能复现出所述结果；探针输出与 "调用示例与预期返回值" 表一致。
- **清理**：删除探针行；strings.json 恢复原状（含 UTF-8 无 BOM）。

## 预期结果

启动游戏进入生涯，按以下位置核对：

- **启动日志**：出现 `Loading strings`；随后出现包含 `data/strings/strings.json` 的 `Loading JSON from [...]` 行（原版与 gear_in 各一行）；`starsector.log` 无 ERROR。
- **探针取值**：按 "验证方案" 接入探针后，取值输出与 "调用示例与预期返回值" 表一致；把键名改为大小写错误的 `GearIn_TEXT_hello` 后，日志出现 ERROR `Missing string: [GearIn_TEXT_hello]`，探针打印字面量 `Missing string: [...]`——"键写错不报错、只给字面量" 的直接证明。
- **原版使用方观察**（不修改原版文件）：
  - 生涯中与舰队遭遇：对话文本里的 `$faction`/`$fleetName`/`$fleetOrShip` 等已被替换成实际值；
  - 悬停或接近跳跃点：`jumpPointInteractionDialog` 的 `approach` 文本（含 `$fleetOrShip` 替换结果）；
  - 接近行星：`planetInteractionDialog` 的 `approach` 文本（含行星名）；
  - 首次进入生涯或首次触发低 CR/低补给等条件：弹出 `campaignHelp` 的 `chm*` 提示，按钮文本来自对应 `_dismiss`/`_action` 键。

## 实例驱动故障分析

按 "症状 → 原因 → 修正" 逐条排查；前三条可用本节示例数据直接复现。

- **界面/日志出现 `Missing string: [xxx]` 字面量**：分类或键拼写/大小写与文件不一致，或文件没放在 `data/strings/strings.json`，或 MOD 未启用。取值不抛异常、不崩溃，界面把字面量原样渲染，日志同时有 ERROR（只含 id 不含 category）。修正：核对 `(category, id)` 逐字符一致，确认路径与启用状态。
- **取到的是别的 MOD 的文本**：`(category, id)` 与其他 MOD（或原版）冲突，标量值被启用顺序靠后的文件覆盖。修正：**不要重定义既有键**；自己的键保持 MOD 前缀（`GearIn_text_*`），分类保持语义命名。
- **启动中止，日志含 JSONException（消息带文件路径）**：strings.json 语法错误 —— 括号不配对、字符串引号不闭合。`#` 注释只在引号外被剥离，引号内的 `#` 是普通字符。修正：对照原版文件结构修语法，重启。
- **启动中止，日志提示文本必须以 `{` 开头一类**：文件带 BOM（`readText` 不剥离，`\ufeff` 粘在开头）。修正：UTF-8 无 BOM 重新保存。
- **界面出现 `$faction` 等原样令牌**：使用方没有做令牌替换。修正：取值后自行 `replace`/`replaceAll`（本节 `GearInI18n.replaceToken` 即此职责）。
- **运行期 `IllegalFormatException`**：`%s` 占位符与 `String.format` 实参个数/类型不匹配。修正：对齐模板与实参；拿不准时先打印模板原文核对。
- **改动文本后没有任何变化**：游戏没重启、文件被保存成 GBK 等非 UTF-8、或改错了文件（另一个 MOD 的同名文件覆盖了本 MOD 的键）。修正：UTF-8 保存并重启，检查键是否与其余 MOD 冲突。

## 总结

strings.json 的全部机制可以收成一张表：

| 环节 | 规则（RC8）|
|---|---|
| 注册入口 | 路径 `data/strings/strings.json` 本身，无需任何注册 |
| 合并 | 跨 MOD 按键合并：对象递归合并、标量后者覆盖、数组追加（`music_*`/`color`/`button`/`core_clearArray` 例外）|
| 取值 | `Global.getSettings().getString(category, id)`（双参，读 strings.json）；单参 `getString(key)` 读的是 settings.json |
| 缺失 | 返回字面量 `Missing string: [<id>]` + 日志 ERROR，不崩溃；键/分类大小写敏感 |
| 占位符 | `$token` 由使用方替换；`%s` 由取值侧 `String.format`；引擎两者都不代换 |
| 组织 | 分类按功能域语义命名，键带 MOD 前缀（`GearIn_text_*`）；本地化只改值不改键 |
| 边界 | `tooltips.json`/`tips.json` 同加载点但键空间独立；对象风味文本走 `descriptions.csv`（C01-S07/C03-S04）|

至此，两条文本链路全部讲完：`descriptions.csv` 管 "对象" 的文本，strings.json 管 "代码" 的文本。

就逻辑上的后续而言，这套两级键机制是后续一切代码驱动文本的共同底座：交互对话、弹窗按钮、提示文本都可以先到 strings.json 定义键、再用双参取值；本节的键组织约定（分类按功能域、键带 MOD 前缀、只改值不改键）会在这些场景里直接复用，而 `$token` 与 `%s` 的职责划分决定了取值侧必须承担的替换与格式化责任。

## 建议参考项

- `data/strings/strings.json`：strings 文本表，路径即注册入口；本节示例文件与 `GearIn_text_*` 键所在。
- `data/strings/tooltips.json`、`data/strings/tips.json`：同一加载点的悬停提示与小贴士，键空间独立。
- `data/config/settings.json`：单参 `getString(key)` 的读取目标。
- `com.fs.starfarer.api.SettingsAPI`：双参 `getString(String category, String id)` 读 strings.json；单参 `getString(String key)` 读 settings.json。
- `com.fs.starfarer.api.Global`：`getSettings()` 取得 `SettingsAPI` 的全局入口。
- `com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl`：`fleetInteractionDialog` 分类使用方，取值后逐个 `replaceAll` 替换 `$token`。
- `com.fs.starfarer.api.impl.campaign.JumpPointInteractionDialogPluginImpl`、`com.fs.starfarer.api.impl.campaign.PlanetInteractionDialogPluginImpl`：`jumpPointInteractionDialog` / `planetInteractionDialog` 分类使用方。
- `data.scripts.gearin.campaign.GearInI18n`：本节示例的取值封装类（`get` / `ui` / `dialog` / `format` / `replaceToken`）。
