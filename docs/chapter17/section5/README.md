# 实现本地化机制

工坊文字已经能通过分类和键取值，这次把按钮、对话与动态句子组织到同一组代码入口。沿用 `GearIn_ui`，增加对话分类，再让 `GearInI18n` 分别处理普通取值、格式化和令牌替换。日志先比较实际返回值，界面继续使用同样的方法。

*本节预期阅读时间：\~12 分钟。*

*本节预期操作时间：\~20 分钟。*

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

## 本地化组织

两级键由 `(category, id)` 组成。分类按功能域命名，键使用 MOD 前缀，代码和文件只要保持这两个值一致，多个界面就可以共享同一份文本。

沿用 [C17-S2] 定义的 `GearIn_ui` 与 `GearIn_text_hello`，在同一对象追加界面键，再定义 `GearIn_dialog` 保存对话。两类键采用 `GearIn_text_*` 前缀，当前分类常量与 JSON 一起对应。

`descriptions.csv` 负责舰船、武器、行星和势力等对象的风味文本；`strings.json` 负责代码主动取用的界面标签、按钮、弹窗和对话行。同一个对象也可以同时有风味描述与代码提示，按实际显示入口分别保存文本。

## 扩展文本表，再保存取值类

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

三种值形态分别演示纯文本、`%s` 模板和 `$token` 文本。

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

`get` 集中调用双参取值入口；`ui` 和 `dialog` 只固定分类，调用方只传键名。`format` 负责填入 `%s` 参数，`replaceToken` 负责填入 `$token`。

## 调用示例速查表

| 调用 | 返回 |
| --- | --- |
| `GearInI18n.ui("GearIn_text_hello")` | `你好，工坊。` |
| `GearInI18n.format("GearIn_ui", "GearIn_text_ship_name", "工坊之锤", "Chicomoztoc")` | `工坊之锤 正在向 Chicomoztoc 靠近。` |
| `GearInI18n.dialog("GearIn_text_approach")` | `您的舰队正在接近 $planetName。` |
| `GearInI18n.replaceToken(GearInI18n.dialog("GearIn_text_approach"), "planetName", "Jangala")` | `您的舰队正在接近 Jangala。` |
| `GearInI18n.ui("GearIn_text_does_not_exist")` | `Missing string: [GearIn_text_does_not_exist]`，日志出现 ERROR |

## 在应用入口比较实际文本

在 `GearInModPlugin.onApplicationLoad(...)` 添加 `GearInI18n` 的 import，并追加局部观察：

```java
Global.getLogger(GearInModPlugin.class).info("GearIn i18n hello: "
        + GearInI18n.ui("GearIn_text_hello"));
Global.getLogger(GearInModPlugin.class).info("GearIn i18n format: "
        + GearInI18n.format("GearIn_ui", "GearIn_text_ship_name", "工坊之锤", "Chicomoztoc"));
Global.getLogger(GearInModPlugin.class).info("GearIn i18n token: "
        + GearInI18n.replaceToken(GearInI18n.dialog("GearIn_text_approach"), "planetName", "Jangala"));
```

**模板取值与动态内容分别完成。** `format(...)` 对传入模板执行格式化，参数不足会报告当前格式问题；`replaceToken(...)` 只替换指定的字面令牌。日志确认后，面板与按钮沿同样的入口取得文本。

编者建议先把模板与调用实参一起看，再做翻译。占位符的顺序和类型也是文本用途的一部分，换了措辞以后，当前调用仍需要填入正确内容。

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

当前文本表按界面与对话分类，取值类将普通文字、格式参数和字面令牌分别接到调用方。对象描述继续按 descriptions 注册，代码提示从 strings 取得。

新增文字时沿功能分类与稳定键安排，模板和使用位置一起对应。这样界面、日志和翻译文件都能找到当前这句话的实际用途。

## 建议参考项

- `data/strings/strings.json`：本节示例的分类和键；
- `com.fs.starfarer.api.SettingsAPI`：`getString(category, id)` 提供双参取值入口；
- `data/strings/descriptions.csv`：对象风味文本的独立数据入口。
