# 实现本地化机制

界面文字外置以后，调用方仍要选择分类、取得模板并填入动态内容。本节沿用 `GearIn_ui`，增加对话分类，用一个简短的 `GearInI18n` 集中这些调用。重点是让文本与用途保持对应，使修改和翻译文字时仍能保留参数的实际含义。

_本节预期阅读时间：\~12 分钟。_

_本节预期操作时间：\~20 分钟。_

## 学习目标

完成后，您将能够：

- 为 MOD 组织界面文本与对话文本两类分类；
- 使用带 MOD 前缀的统一前缀命名自有键；
- 实现取值封装类的分类取值、`%s` 格式化和 `$token` 替换；
- 区分代码驱动文本与 `descriptions.csv` 的对象描述文本；
- 通过界面或日志观察本地化文本和缺失键结果。

## 前置知识

- [C17-S2] 的 `strings.json` 固定入口、双参 `getString`、缺失键和占位符规则；
- [C1-S1] 的 MOD 目录结构与 `gear_in` 示例 MOD；
- [C1-S7] 的基础对象描述注册；
- [C3-S5] 的描述类型、字段用途与对象绑定。

## 按文字用途安排分类和键

一条 strings 文本由 `(category, id)` 定位。分类按功能组织，键使用 MOD 前缀；同一条文字可以供多个界面使用，它的身份由分类和键确定。

我们沿用 [C17-S2] 的 `GearIn_ui` 与 `GearIn_text_hello`，在原分类增加按钮和模板，再建立 `GearIn_dialog` 保存对话。两类键都采用 `GearIn_text_*` 前缀，稍后的代码常量固定这两个分类。

文字的显示入口仍要区分。舰船、武器、行星和势力等对象的风味文本由 `descriptions.csv` 注册，代码主动取用的标签、按钮、弹窗和对话行放在 `strings.json`。同一个对象可以同时使用两者，分别服务对象描述与代码提示。

## 准备纯文本与动态模板

把以下键合入 `data/strings/strings.json`，现有分类与前文键继续保留。完整例包含当前使用的六个键：

```json
{
	# 界面文本
	"GearIn_ui": {
		"GearIn_text_hello": "你好，工坊。",
		"GearIn_text_ship_name": "%s 正在向 %s 靠近。",
		"GearIn_text_dismiss": "关闭",
		"GearIn_text_unknown": "未知对象",
	},
	# 对话文本
	"GearIn_dialog": {
		"GearIn_text_approach": "您的舰队正在接近 $planetName。",
		"GearIn_text_dock": "停靠请求已接受。",
	}
}
```

`hello`、`dismiss`、`unknown` 与 `dock` 是纯文本；`ship_name` 的两个 `%s` 依次接受舰船名和目的地；`approach` 的 `$planetName` 保留给对话调用方替换。文字的形式由使用方式决定，模板里的参数也属于这句话的含义。

翻译如果需要调换语序，格式模板可以使用 `%2$s`、`%1$s` 指定既有参数的位置；调用方传入的舰船与目的地仍各有原来的含义。字面令牌则通过名称指定内容。**句子可以重新组织，参数与对象的对应关系要保留。**

## 集中取值与模板处理

保存 `src/data/scripts/gearin/campaign/GearInI18n.java`，这个类把常用分类固定下来，调用方仍按方法传入文本键和动态内容：

::: details 完整成品：GearInI18n

```java
package data.scripts.gearin.campaign;

import com.fs.starfarer.api.Global;

public final class GearInI18n {

    public static final String CATEGORY_UI = "GearIn_ui";
    public static final String CATEGORY_DIALOG = "GearIn_dialog";

    private GearInI18n() {
    }

    public static String get(String category, String id) {
        return Global.getSettings().getString(category, id);
    }

    public static String ui(String id) {
        return get(CATEGORY_UI, id);
    }

    public static String dialog(String id) {
        return get(CATEGORY_DIALOG, id);
    }

    public static String format(String category, String id, Object... args) {
        String template = get(category, id);
        return String.format(template, args);
    }

    public static String replaceToken(String text, String token, String value) {
        return text.replace("$" + token, value);
    }
}
```

:::

`ui` 和 `dialog` 固定常用分类，实际取值集中到 `get` 的双参 API 调用。`format` 取得模板后执行 `String.format`，`replaceToken` 则替换指定的字面令牌。调用方据此选择方法，并传入本次显示所需的动态内容。

本例保留原版的缺失键结果。编者认为在制作阶段，能从界面和日志追到具体键名，比掩盖缺失文本更有价值；文字是否齐全，也属于本地化交付的一部分。

## 调用示例速查表

| 调用                                                                                          | 返回                                                           |
| --------------------------------------------------------------------------------------------- | -------------------------------------------------------------- |
| `GearInI18n.ui("GearIn_text_hello")`                                                          | `你好，工坊。`                                                 |
| `GearInI18n.format("GearIn_ui", "GearIn_text_ship_name", "工坊之锤", "Chicomoztoc")`          | `工坊之锤 正在向 Chicomoztoc 靠近。`                           |
| `GearInI18n.dialog("GearIn_text_approach")`                                                   | `您的舰队正在接近 $planetName。`                               |
| `GearInI18n.replaceToken(GearInI18n.dialog("GearIn_text_approach"), "planetName", "Jangala")` | `您的舰队正在接近 Jangala。`                                   |
| `GearInI18n.ui("GearIn_text_does_not_exist")`                                                 | `Missing string: [GearIn_text_does_not_exist]`，日志出现 ERROR |

## 把文本送到使用位置

在 `GearInModPlugin.onApplicationLoad(...)` 添加 `GearInI18n` 的 import，并追加局部观察：

```java
Global.getLogger(GearInModPlugin.class).info("GearIn i18n hello: "
        + GearInI18n.ui("GearIn_text_hello"));
Global.getLogger(GearInModPlugin.class).info("GearIn i18n format: "
        + GearInI18n.format("GearIn_ui", "GearIn_text_ship_name", "工坊之锤", "Chicomoztoc"));
Global.getLogger(GearInModPlugin.class).info("GearIn i18n token: "
        + GearInI18n.replaceToken(GearInI18n.dialog("GearIn_text_approach"), "planetName", "Jangala"));
```

三条日志依次确认纯文本、格式模板和令牌文本的结果。面板和按钮沿用相同方法，取得的是已经适合本次显示的文字。`format(...)` 的参数不足会报告格式问题；`replaceToken(...)` 只处理指定的字面令牌，其他令牌继续保留在字符串中。

## 验证方案

前置数据：`gear_in` MOD、`data/strings/strings.json` 中的 `GearIn_text_*` 键、`GearInI18n` 和一个可调用这些方法的界面或日志入口已就绪。

1. 保存资源、取值类与应用调用，构建重启，搜索三条 `GearIn i18n` 日志。
2. 调用 `GearInI18n.format("GearIn_ui", "GearIn_text_ship_name", "工坊之锤", "Chicomoztoc")`，观察格式化文本。
3. 调用 `GearInI18n.dialog("GearIn_text_approach")`，再用 `replaceToken` 填入 `Jangala`。
4. 观察 `descriptions.csv` 提供的对象描述，确认它与代码取用的 strings 文本各自显示在对应位置。

预期结果：

- 界面或日志出现 `你好，工坊。`；
- `%s` 被依次替换为 `工坊之锤` 和 `Chicomoztoc`；
- `$planetName` 在调用 `replaceToken` 后显示为 `Jangala`；
- 对象描述继续来自 `descriptions.csv`，代码驱动文本来自 `strings.json`。

## 故障分析

- **界面显示 `Missing string`**：分类或键的大小写、拼写与文件不一致。
    - **解决方案**：逐字符核对 `category` 和 `id`，重新取值。

- **格式化抛出 `IllegalFormatException`**：模板占位符与实参数量或类型不一致。
    - **解决方案**：统一模板和 `format` 参数。

- **`$token` 原样显示**：调用方只取得模板，没有执行 `replaceToken`。
    - **解决方案**：显示前完成替换。

- **对象描述没有变化**：风味描述与代码文本使用了错误的数据入口。
    - **解决方案**：将对象描述放入 `descriptions.csv`，代码文本放入 `strings.json`。

- **封装类取不到新增文本**：`GearInI18n` 的分类常量或键名没有与 JSON 同步。
    - **解决方案**：先核对数据文件，再修正调用点。

## 总结

文本表按用途组织分类与稳定键，取值类集中调用，实际使用方再提供动态内容。翻译可以改变措辞和语序，分类、键与参数的含义继续对应到原来的显示职责；对象的风味描述则沿用 descriptions 注册。

## 建议参考项

- `data/strings/strings.json`：本节示例的分类和键；
- `com.fs.starfarer.api.SettingsAPI`：`getString(category, id)` 提供双参取值入口；
- `data/strings/descriptions.csv`：对象风味文本的独立数据入口。
