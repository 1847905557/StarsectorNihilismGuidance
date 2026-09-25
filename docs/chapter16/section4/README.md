# 编写 Rules 命令

本节让 `rules.csv` 通过 `BaseCommandPlugin` 调用一段 MOD Java 代码。命令从 `List<Token>` 读取字符串、数字、布尔值、颜色和对象，把解析结果写入当前实体的 Memory，并把布尔结果返回给规则条件。

*本节预期阅读时间：~20 分钟。*

*本节预期操作时间：~15 分钟。*

## 学习目标

完成后，您将能够：

- 使用 `BaseCommandPlugin` 实现一个 Rules 命令；
- 区分 Java 导入路径与运行时命令搜索路径；
- 读取 `execute(...)` 的调用信息、参数与 Memory 槽位；
- 使用 `Misc.Token` 解析字符串、数值、布尔值、颜色和对象；
- 根据条件列、脚本列和日志结果定位命令故障。

## 前置知识

- [C6-S1] 的 JAR 构建、`mod_info.json` 的 JAR 声明与 API 依赖；
- [C16-S1] 的 `rules.csv` 七列、加载流程与数据语法；
- [C16-S2] 的条件表达式、脚本执行与 Memory 槽位；
- [C16-S5] 的 `MemoryAPI` 过期、持久化与记忆键维护。

## 从规则文本找到命令类

Rules 命令是 `CommandPlugin` 的实现类。条件列或脚本列的一行以简单类名开头，后续文本经过分词后成为命令参数；例如 `GearInRuleCommand "扫描 $playerName" 2.4 true highlight` 会寻找名为 `GearInRuleCommand` 的类，并把其余内容传给 `execute(...)`。

### 区分两种路径

Java 源码中的导入路径是 `com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin`。它说明基类位于 API 的 `impl.campaign.rulecmd` 包，与 `CommandPlugin` 所在的 `campaign.rules` 包不同。

运行时搜索路径来自 `data/config/settings.json` 的 `ruleCommandPackages`。原版列表包含 `com.fs.starfarer.api.impl.campaign.rulecmd` 及其 `salvage`、`newgame`、`missions`、`academy` 子包；MOD 自己的命令位于 `data.scripts.gearin.rules`，因此需要把这个包追加到同名数组。加载器按数组中的包依次组合 `<包名>.<简单类名>`，直到找到可实例化的类。

::: warning 导入路径不会自动成为搜索路径

Java 可以正确编译只证明 import 成立。`ruleCommandPackages` 没有 `data.scripts.gearin.rules` 时，`rules.csv` 仍然无法通过简单类名找到 `GearInRuleCommand`，游戏会在加载规则时报告命令不存在。
:::

## 认识 BaseCommandPlugin

`CommandPlugin` 定义 Rules 命令与引擎之间的三个入口：

```java
public interface CommandPlugin {
    boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap);
    boolean doesCommandAddOptions();
    int getOptionOrder(List<Token> params, Map<String, MemoryAPI> memoryMap);
}
```

`BaseCommandPlugin` 已经实现后两个方法：`doesCommandAddOptions()` 返回 false，`getOptionOrder(...)` 返回 0。普通命令继承它后只需实现 `execute(...)`；需要生成选项的特殊命令才覆写另外两个方法。

它还提供静态 `getEntityMemory(memoryMap)`。该方法优先返回 `MemKeys.ENTITY`，没有 ENTITY 槽位时返回 `MemKeys.LOCAL`。命令用它写入当前交互对象的状态，不需要自行重复槽位选择逻辑。

规则加载时会尝试实例化命令，确认类存在且无参构造可用；运行时每次调用也会取得新的实例。跨调用数据应写入 `memoryMap` 指向的 `MemoryAPI`，命令对象的普通字段只属于当前实例。

### execute 的四个参数

- `ruleId` 是当前规则 ID，日志可以用它定位调用来源；
- `dialog` 是当前对话对象，非对话规则调用可以传入 null；
- `params` 是命令名之后经过分词的 `Token` 列表；
- `memoryMap` 保存 LOCAL、ENTITY、PLAYER 等 Memory 槽位。

条件列调用会使用 `execute(...)` 的布尔返回值决定该条件是否成立。脚本列只按顺序执行命令，返回值不会停止下一行脚本。因此示例返回解析出的 `enabled`：同一命令写在条件列时能够筛选规则，写在脚本列时仍会完成 Memory 写入。

## 解析 Token 参数

`Misc.tokenize(...)` 按空白切分普通参数，引号把包含空格的文本保留为一个参数，反斜杠转义下一个字符，表达式操作符会独立成为 token。命令收到的是切分完成的 `List<Token>`，因此先检查参数数量，再检查各位置允许的类型。

### 区分变量、字面量和操作符

`Token` 使用 VARIABLE、LITERAL 和 OPERATOR 三种类型。`isVariable()`、`isLiteral()` 与 `isOperator()` 只判断类型；示例的第一个参数允许变量或字面量，因此先拒绝操作符：

```java
        Token labelToken = params.get(0);
        Token amountToken = params.get(1);
        Token enabledToken = params.get(2);
        if ((!labelToken.isLiteral() && !labelToken.isVariable())
                || !amountToken.isFloat(memoryMap)
                || !enabledToken.isBoolean(memoryMap)) {
            LOG.warn("GearInRuleCommand: invalid parameter type for rule " + ruleId);
            return false;
        }
```

`isFloat(memoryMap)` 与 `isBoolean(memoryMap)` 会读取变量值后判断格式。数值检查失败时提前返回，可以避免 `getFloat(...)` 抛出格式异常；布尔检查只接受文本 `true` 或 `false`。

### 读取字符串并替换文本 token

`getString(memoryMap)` 对字面量返回原文本，对变量则从对应 Memory 取得字符串。`getStringWithTokenReplacement(...)` 在此基础上继续替换 `$playerName` 等对话文本 token；它需要 `dialog.getInteractionTarget()`，所以非对话上下文要改用普通 `getString(...)`：

```java
        String label = dialog == null
                ? labelToken.getString(memoryMap)
                : labelToken.getStringWithTokenReplacement(ruleId, dialog, memoryMap);
```

命令参数写成 `"扫描 $playerName"` 时，对话调用会得到包含玩家姓名的 label；没有对话对象时保留原字符串。

### 读取数值和布尔值

`getFloat(memoryMap)` 返回浮点数，`getInt(memoryMap)` 对同一个浮点结果执行四舍五入。`getBoolean(memoryMap)` 使用 `Boolean.parseBoolean`，只有忽略大小写的 `true` 会得到 true；`1` 与 `yes` 都会得到 false。

```java
        float amount = amountToken.getFloat(memoryMap);
        int count = amountToken.getInt(memoryMap);
        boolean enabled = enabledToken.getBoolean(memoryMap);
```

参数 `2.4 true` 会产生 `amount=2.4`、`count=2` 和 `enabled=true`。命令最后返回 `enabled`，因此该布尔参数直接决定条件列能否通过。

### 读取颜色和对象

`getColor(memoryMap)` 接受 Color 对象、RGBA 文本、设置中的颜色 ID、势力 ID，以及 `bad`、`good`、`highlight`、`story`、`gray` 等简写。示例把第四个参数解析为 Color；省略时使用高亮色：

```java
        Color color = params.size() > 3
                ? params.get(3).getColor(memoryMap)
                : Misc.getHighlightColor();
```

`getObject(memoryMap)` 只对变量 token 返回 Memory 中保存的原对象，字面量会得到 null。第五个参数可以传入 `$entity.someObject`，命令取得对象后再写到当前实体 Memory：

```java
        Object object = params.size() > 4 ? params.get(4).getObject(memoryMap) : null;
```

### 找到变量对应的 Memory

带点变量 `$player.name` 会把 `player` 作为槽位名，把 `$name` 作为键。槽位不存在时，`getVarNameAndMemory(...)` 会把完整变量名交给 LOCAL；LOCAL 也不存在时抛出 `RuleException`。没有点号的 `$option` 与 `$menuState` 直接读取 LOCAL。

`getObject(...)`、`getString(...)` 与数值方法都沿用这套槽位解析。调用方传入的 `memoryMap` 决定可用槽位，命令不应自行假定每种场景都提供 MARKET、FACTION 或 ENTITY。

## 组装 Rules 命令

前面的路径、接口和 Token 关系现在合并为一个最小命令：规则文本传入字符串、浮点数、布尔值、颜色和对象，命令把解析结果写入实体 Memory，并用 enabled 的值决定条件是否成立。

### 注册命令包

```json
{
  "ruleCommandPackages": [
    "data.scripts.gearin.rules"
  ]
}
```

这个数组追加 MOD 包。Rules 数据使用简单类名 `GearInRuleCommand`，加载器会从该包拼出 `data.scripts.gearin.rules.GearInRuleCommand`。

### 让条件列和脚本列调用命令

```csv
id,trigger,conditions,script,text,options,notes
GearIn_rules_cmd_condition,GearInRulesCommandCondition,"GearInRuleCommand ""扫描 $playerName"" 2.4 true highlight
$GearIn_rulecmd_last >= 2",,命令返回 true 后，这条规则进入候选。,,条件列调用
GearIn_rules_cmd_script,GearInRulesCommandScript,,"GearInRuleCommand ""补给"" 3.0 false good $entity.GearIn_rulecmd_object",命令把参数写入实体 Memory。,,脚本列调用
```

第一行把 `GearInRuleCommand` 当作条件命令，只有 `enabled=true` 且随后条件成立时才进入候选。第二行只关心命令的写入结果，`execute(...)` 返回 false 不会停止脚本列。

### 保存解析结果

```java
        MemoryAPI memory = getEntityMemory(memoryMap);
        memory.set(MEM_KEY_LABEL, label);
        memory.set(MEM_KEY_LAST, amount);
        memory.set(MEM_KEY_COUNT, count);
        memory.set(MEM_KEY_COLOR, color);
        if (object != null) {
            memory.set(MEM_KEY_OBJECT, object);
        }
```

`getEntityMemory(...)` 选择 ENTITY 或 LOCAL 后，五个结果都写入同一个 Memory。对象参数不存在时不覆盖旧对象；命令最后记录 rule ID 与解析值，并返回 `enabled`。

## 完整最小例

下面的 JSON、Rules 数据和 Java 类组成一个可加载的命令示例。

```json
{
  "ruleCommandPackages": [
    "data.scripts.gearin.rules"
  ]
}
```

```csv
id,trigger,conditions,script,text,options,notes
GearIn_rules_cmd_condition,GearInRulesCommandCondition,"GearInRuleCommand ""扫描 $playerName"" 2.4 true highlight
$GearIn_rulecmd_last >= 2",,命令返回 true 后，这条规则进入候选。,,条件列调用
GearIn_rules_cmd_script,GearInRulesCommandScript,,"GearInRuleCommand ""补给"" 3.0 false good $entity.GearIn_rulecmd_object",命令把参数写入实体 Memory。,,脚本列调用
```

::: details 完整成品：GearInRuleCommand

```java
package data.scripts.gearin.rules;

import java.awt.Color;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Misc.Token;

/**
 * Parses Rules command parameters and writes the result to entity memory.
 * The command returns the parsed boolean so it can also be used in a
 * conditions cell.
 */
public class GearInRuleCommand extends BaseCommandPlugin {

    public static final String MEM_KEY_LAST = "$GearIn_rulecmd_last";
    public static final String MEM_KEY_LABEL = "$GearIn_rulecmd_label";
    public static final String MEM_KEY_COUNT = "$GearIn_rulecmd_count";
    public static final String MEM_KEY_COLOR = "$GearIn_rulecmd_color";
    public static final String MEM_KEY_OBJECT = "$GearIn_rulecmd_object";

    private static final Logger LOG = Global.getLogger(GearInRuleCommand.class);

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog,
                           List<Token> params, Map<String, MemoryAPI> memoryMap) {
        if (params.size() < 3) {
            LOG.warn("GearInRuleCommand: expected <label> <amount> <enabled> [<color>] [<object>]");
            return false;
        }

        Token labelToken = params.get(0);
        Token amountToken = params.get(1);
        Token enabledToken = params.get(2);
        if ((!labelToken.isLiteral() && !labelToken.isVariable())
                || !amountToken.isFloat(memoryMap)
                || !enabledToken.isBoolean(memoryMap)) {
            LOG.warn("GearInRuleCommand: invalid parameter type for rule " + ruleId);
            return false;
        }

        String label = dialog == null
                ? labelToken.getString(memoryMap)
                : labelToken.getStringWithTokenReplacement(ruleId, dialog, memoryMap);
        float amount = amountToken.getFloat(memoryMap);
        int count = amountToken.getInt(memoryMap);
        boolean enabled = enabledToken.getBoolean(memoryMap);
        Color color = params.size() > 3
                ? params.get(3).getColor(memoryMap)
                : Misc.getHighlightColor();
        Object object = params.size() > 4 ? params.get(4).getObject(memoryMap) : null;

        MemoryAPI memory = getEntityMemory(memoryMap);
        memory.set(MEM_KEY_LABEL, label);
        memory.set(MEM_KEY_LAST, amount);
        memory.set(MEM_KEY_COUNT, count);
        memory.set(MEM_KEY_COLOR, color);
        if (object != null) {
            memory.set(MEM_KEY_OBJECT, object);
        }

        LOG.info("GearInRuleCommand: ruleId=" + ruleId + " label=" + label
                + " amount=" + amount + " count=" + count + " enabled=" + enabled);
        return enabled;
    }
}

```

:::

## 验证方案

前置数据：MOD 已把 `data.scripts.gearin.rules` 追加到 `ruleCommandPackages`，并在 `data/campaign/rules.csv` 中加入本节两条规则；使用任一 Rules 入口分别触发 `GearInRulesCommandCondition` 与 `GearInRulesCommandScript`。

1. 在 MOD 根目录执行 `gradle jar`，再用 `jar tf jars/GearIndustry.jar` 查找 `data/scripts/gearin/rules/GearInRuleCommand.class`。
2. 启动游戏，在 `starsector.log` 中确认两条 `GearIn_rules_cmd_*` 规则完成加载。
3. 触发 `GearInRulesCommandCondition`，观察条件规则文本与 `GearInRuleCommand` 日志。
4. 触发 `GearInRulesCommandScript`，读取当前实体的 `$GearIn_rulecmd_label`、`$GearIn_rulecmd_last`、`$GearIn_rulecmd_count` 和 `$GearIn_rulecmd_color`。
5. 把数值参数改为非数字文本并重启，确认命令记录类型错误；随后恢复为 `2.4`。
6. 分别把布尔参数改为 `false` 与 `1`，确认两次条件规则都不会进入候选；随后恢复为 `true`。
7. 通过变量参数传入一个已有对象，确认 `getObject(...)` 把同一个对象写入 `$GearIn_rulecmd_object`。

预期结果：

- JAR 只包含本节的 `GearInRuleCommand.class`；
- 条件调用解析出 `label=扫描 <玩家名>`、`amount=2.4`、`count=2`、`enabled=true`，并显示条件规则文本；
- 脚本调用返回 false 后仍写入 label、数值、颜色和可用的对象；
- 非数字参数被 `isFloat(...)` 拒绝，日志出现 `invalid parameter type`；
- `false` 与 `1` 都不能让条件规则成立，只有 `true` 返回 true；
- 每次日志包含当前 `ruleId`，多次调用之间的结果由 Memory 保存。

## 故障分析

- **启动时报告 command not found**：`ruleCommandPackages` 缺少 `data.scripts.gearin.rules`，或 JAR 中没有对应完整类路径。
    - **解决方案**：核对设置数组、`data/scripts/gearin/rules/GearInRuleCommand.class` 和 MOD 的 JAR 声明。

- **规则条件始终不成立**：布尔参数不是 `true`，或命令被参数数量与类型检查提前拒绝。
    - **解决方案**：核对前三个参数依次为字符串、可解析数值和 `true` / `false`。

- **对话出现参数转换异常**：代码在检查 `isFloat(...)` 或 `isBoolean(...)` 之前直接调用了取值方法。
    - **解决方案**：先检查参数数量和格式，再调用 `getFloat(...)`、`getInt(...)` 或 `getBoolean(...)`。

- **脚本列返回 false 后仍继续执行**：脚本执行只使用命令的写入结果，不使用 `execute(...)` 的返回值控制后续行。
    - **解决方案**：把需要参与匹配的调用放在 conditions，把状态变更放在 script。

- **变量读取了错误的值**：变量前缀没有对应 Memory 槽位，解析转而从 LOCAL 读取完整键名。
    - **解决方案**：核对 `memoryMap` 提供的槽位，并让参数中的前缀与槽位名称一致。

- **对象参数始终为 null**：`getObject(...)` 收到的是字面量，或目标 Memory 没有保存该键。
    - **解决方案**：传入指向现有对象的变量 token，并确认对象位于该变量指定的 Memory。

- **非对话调用在替换字符串时异常**：`getStringWithTokenReplacement(...)` 需要非 null 的 `dialog`。
    - **解决方案**：先检查 `dialog`，非对话上下文使用 `getString(...)`。

## 总结

Rules 命令从脚本中的简单类名开始，经 `ruleCommandPackages` 找到完整 Java 类，再把分词后的参数交给 `execute(...)`。Java import 决定源码引用哪个基类，运行时搜索包决定规则加载器能否找到 MOD 命令；两条路径需要分别配置。

`BaseCommandPlugin` 为普通命令提供默认选项行为和实体 Memory 选择。命令使用 `Token` 区分变量、字面量与操作符，按目标类型读取字符串、数值、布尔值、颜色或对象，并把跨调用状态放入 Memory。条件列使用布尔返回值，脚本列使用命令产生的状态变化。

## 建议参考项

- `com.fs.starfarer.api.campaign.rules.CommandPlugin`：Rules 命令接口与三个调用入口；
- `com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin`：普通命令的默认实现与实体 Memory 选择；
- `com.fs.starfarer.api.util.Misc`：`tokenize` 与 `Token` 参数解析方法；
- `com.fs.starfarer.api.campaign.rules.MemoryAPI`：命令参数来源与解析结果保存位置；
- `com.fs.starfarer.api.campaign.InteractionDialogAPI`：文本 token replacement 使用的对话目标；
- `data/config/settings.json`：`ruleCommandPackages` 搜索包配置；
- `data/campaign/rules.csv`：命令简单类名、参数和调用列。
