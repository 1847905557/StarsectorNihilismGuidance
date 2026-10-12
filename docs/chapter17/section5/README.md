# 实现本地化机制

本节使用 [C17-S2] 已建立的 strings 规则，在 `gear_in` MOD 中组织分类和键，并写出 `GearInI18n` 取值封装。完成后，静态文本、`%s` 参数和 `$token` 都通过同一组调用入口进入界面或日志。

*本节预期阅读时间：~12 分钟。*

*本节预期操作时间：~20 分钟。*

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

本节为 `gear_in` 使用 `GearIn_ui` 与 `GearIn_dialog` 两个分类，前者保存界面文本，后者保存对话文本；两类键统一使用 `GearIn_text_*` 前缀。新增文本只写入这两个分类，避免与原版或其他 MOD 的键冲突。

`descriptions.csv` 负责舰船、武器、行星和势力等对象的风味文本；`strings.json` 负责代码主动取用的界面标签、按钮、弹窗和对话行。同一段文本如果同时按对象和代码取用，两份文件分别保存对应版本。

## 实现 GearInI18n

以下文件放在 `data/strings/strings.json`。它只声明本节的两个分类和 `GearIn_text_*` 键：

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
		"GearIn_text_approach": "你的舰队正在接近 $planetName。",
		"GearIn_text_dock": "停靠请求已接受。",
	}
}
```

三种值形态分别演示纯文本、`%s` 模板和 `$token` 文本。

代码侧封装分类常量、双参取值、格式化和令牌替换：

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
        if (args == null || args.length == 0) {
            return template;
        }
        return String.format(template, args);
    }

    public static String replaceToken(String text, String token, String value) {
        return text.replace("$" + token, value);
    }
}
```

`get` 是唯一的双参取值入口；`ui` 和 `dialog` 只固定分类，调用方只传键名。`format` 负责填入 `%s` 参数，`replaceToken` 负责填入 `$token`。

## 调用示例速查表

| 调用 | 返回 |
| --- | --- |
| `GearInI18n.ui("GearIn_text_hello")` | `你好，工坊。` |
| `GearInI18n.format("GearIn_ui", "GearIn_text_ship_name", "工坊之锤", "Chicomoztoc")` | `工坊之锤 正在向 Chicomoztoc 靠近。` |
| `GearInI18n.dialog("GearIn_text_approach")` | `你的舰队正在接近 $planetName。` |
| `GearInI18n.replaceToken(GearInI18n.dialog("GearIn_text_approach"), "planetName", "Jangala")` | `你的舰队正在接近 Jangala。` |
| `GearInI18n.ui("GearIn_text_does_not_exist")` | `Missing string: [GearIn_text_does_not_exist]`，日志出现 ERROR |

## 验证方案

前置数据：`gear_in` MOD、`data/strings/strings.json` 中的 `GearIn_text_*` 键、`GearInI18n` 和一个可调用这些方法的界面或日志入口已就绪。

1. 进入对应界面或触发日志入口，调用 `GearInI18n.ui("GearIn_text_hello")`。
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

本节把 `strings.json` 的两级键落成了一个可复用的 `GearInI18n` 入口：分类常量限制取值范围，`format` 处理 `%s`，`replaceToken` 处理 `$token`。以后新增代码驱动文本时，只需沿用分类、键前缀和封装入口，页面与日志就能共享同一套本地化组织。

## 建议参考项

- `data/strings/strings.json`：本节示例的分类和键；
- `com.fs.starfarer.api.SettingsAPI.getString(String category, String id)`：双参取值入口；
- `descriptions.csv`：对象风味文本的独立数据入口。
