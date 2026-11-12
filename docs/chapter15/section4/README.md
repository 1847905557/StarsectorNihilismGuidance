# 编写 Rules 命令

Rules 命令把规则中的一行交给 Java 处理。本节从独立舰队通讯调用 `GearInRuleCommand`，解析文字、数值、布尔值与可选颜色、对象，再把结果保存到当前实体 Memory。同一实现分别用于条件和脚本，借此说明返回值与状态写入何时被使用。

_本节预期阅读时间：\~20 分钟。_

_本节预期操作时间：\~15 分钟。_

## 学习目标

完成后，您将能够：

- 使用 `BaseCommandPlugin` 实现一个 Rules 命令；
- 区分 Java 导入路径与运行时命令搜索路径；
- 读取 `execute(...)` 的调用信息、参数与 Memory 槽位；
- 使用 `Misc.Token` 解析字符串、数值、布尔值、颜色和对象；
- 根据条件列、脚本列和日志结果定位命令故障。

## 前置知识

- [C6-S4] 的构建、JAR 声明与加载；
- [C16-S1] 的 `rules.csv` 七列、加载流程与数据语法；
- [C16-S2] 的条件表达式、脚本执行与 Memory 槽位；

## 保存命令，并登记搜索包

保存 `src/data/scripts/gearin/rules/GearInRuleCommand.java`，继承 `BaseCommandPlugin`。命令行用简单类名开头，后面的文本经分词后传入 `execute(...)`；本例接受文字、数值、是否启用，以及可选颜色与对象。

Java 类所在包还要进入 Rules 的搜索配置。在 MOD 的 `data/config/settings.json` 合并以下局部数组：

```json
{
  "ruleCommandPackages": [
    "data.scripts.gearin.rules"
  ]
}
```

同名数组按现有设置合并，原版包继续保留。规则写 `GearInRuleCommand`，搜索器用包名组成 `data.scripts.gearin.rules.GearInRuleCommand`，找到无参构造可用的实现。

### 区分两种路径

Java 源码中的导入路径是 `com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin`。它说明基类位于 API 的 `impl.campaign.rulecmd` 包，与 `CommandPlugin` 所在的 `campaign.rules` 包不同。

原版搜索列表包括 `rulecmd` 基础包与 `salvage`、`newgame`、`missions`、`academy` 子包。数组按顺序组合简单类名，找到后使用该实现；本节的前缀让自有类名保持独立。

::: warning 导入路径不会自动成为搜索路径

Java 可以正确编译只证明 `import` 成立。`ruleCommandPackages` 没有 `data.scripts.gearin.rules` 时，`rules.csv` 仍然无法通过简单类名找到 `GearInRuleCommand`，游戏会在加载规则时报告命令不存在。
:::

## 从通讯分别触发条件与脚本调用

成品菜单接到非敌对独立舰队的 `fleetConvMain`，两条选项分别 fire 自有结果 trigger。以下局部记录展示实际命令参数：

```csv
id,trigger,conditions,script,text,options,notes
GearIn_rules_cmd_condition,GearInRulesCommandCondition,"GearInRuleCommand ""扫描 $playerName"" 2.4 true highlight
$entity.GearIn_rulecmd_last >= 2",,命令返回 true 后，这条规则进入候选。,,条件列调用
GearIn_rules_cmd_script,GearInRulesCommandScript,,"GearInRuleCommand ""补给"" 3.0 false good $entity.GearIn_rulecmd_object",命令把参数写入实体 Memory。,,脚本列调用
```

条件调用返回 `true` 后，还要通过数值条件，规则才参加选择。脚本调用则执行写入，返回 `false` 也不会停止后续脚本。通讯菜单与完整回流记录在成品中组合，两个 trigger 都有实际调用。

## 用 execute 处理本次请求

`CommandPlugin` 定义 Rules 命令与引擎之间的三个入口：

```java
public interface CommandPlugin {
    boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap);
    boolean doesCommandAddOptions();
    int getOptionOrder(List<Token> params, Map<String, MemoryAPI> memoryMap);
}
```

普通命令继承基类只需实现 `execute(...)`，生成选项时再覆写 `doesCommandAddOptions()` 与 `getOptionOrder(...)`。当前沿用默认 `false` 与 0，表示它没有提前收集的选项结果。

基类的 `getEntityMemory(memoryMap)` 选择 ENTITY，缺少该槽位时使用 LOCAL。舰队通讯把指挥官作为当前人物，同时保留背后的舰队实体，所以本节写入 ENTITY，再用 `$entity.*` 读回结果。

搜索命令时会尝试实例化，运行调用也创建当前实例。**跨调用结果保存在实际 Memory 中。** 当前类解析这次参数，日志用规则 ID 定位来源；命令字段不承担长期状态。

### `execute` 的四个参数

- `ruleId` 是当前规则 ID，日志可以用它定位调用来源；
- `dialog` 是当前对话对象，非对话规则调用可以传入 `null`；
- `params` 是命令名之后经过分词的 `Token` 列表；
- `memoryMap` 保存 LOCAL、ENTITY、PLAYER 等 Memory 槽位。

本例最后返回解析出的 `enabled`。条件列使用这个值筛选，脚本列使用已经完成的写入结果，两条通讯路径因而能直接比较同一方法的不同调用。

## 解析 `Token` 参数

`params` 已经是 `Misc.tokenize(...)` 得到的 `List<Token>`。空白分隔普通参数，引号保留含空格的字面量，反斜杠处理转义，操作符独立。本例的三个必需参数来自规则与可能的变量值，当前命令按这份约定解析。

### 区分变量、字面量和操作符

Token 分为 `VARIABLE`、`LITERAL` 与 `OPERATOR`，判断方法读取各自类型。文字接受字面量或变量，数值与布尔参数检查当前取值，局部处理如下：

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

数值检查用于这次参数的可解析值，布尔检查接受 `true` 或 `false`。不符合约定时返回 `false` 并记录规则 ID；有效输入继续进入实际取值。

### 读取字符串并替换文本 `token`

`getString(...)` 取得原字面量或变量字符串，带 token replacement 的版本继续替换 `$playerName` 等文本字段。后者需要当前对话目标，局部选择如下：

```java
        String label = dialog == null
                ? labelToken.getString(memoryMap)
                : labelToken.getStringWithTokenReplacement(ruleId, dialog, memoryMap);
```

通讯中的文字参数变为 "扫描 <玩家名>"。非对话调用直接读取原字符串，当前请求有哪些界面对象，决定采用哪种文本处理。

### 读取数值和布尔值

`getFloat(memoryMap)` 返回浮点数，`getInt(memoryMap)` 对同一个浮点结果执行四舍五入。`getBoolean(memoryMap)` 使用 `Boolean.parseBoolean`，只有忽略大小写的 `true` 会得到 `true`；`1` 与 `yes` 都会得到 `false`。

```java
        float amount = amountToken.getFloat(memoryMap);
        int count = amountToken.getInt(memoryMap);
        boolean enabled = enabledToken.getBoolean(memoryMap);
```

输入 `2.4 true` 得到浮点 2.4、四舍五入后的整数 2 和布尔真值。最终返回布尔结果，数值则保存到 Memory，供后一条条件或其他脚本读取。

### 读取颜色和对象

第四参数用 `getColor(...)` 解析，可以来自 Color 对象、RGBA 文本、设置颜色 ID、势力 ID，或 `bad`、`good`、`highlight`、`story`、`gray` 等简写。省略时用高亮色，局部取值如下：

```java
        Color color = params.size() > 3
                ? params.get(3).getColor(memoryMap)
                : Misc.getHighlightColor();
```

第五参数是已有对象的变量。`getObject(...)` 返回 Memory 中的原对象，字面量没有对应对象，本节将可用结果写回当前实体：

```java
        Object object = params.size() > 4 ? params.get(4).getObject(memoryMap) : null;
```

### 找到变量对应的 Memory

带点变量 `$player.name` 会把 `player` 作为槽位名，把 `$name` 作为键。槽位不存在时，`getVarNameAndMemory(...)` 会把完整变量名交给 LOCAL；LOCAL 也不存在时抛出 `RuleException`。没有点号的 `$option` 与 `$menuState` 直接读取 LOCAL。

字符串、对象与数值都采用这套槽位解析。实际 `memoryMap` 提供当前上下文，市场、人物与交易面板能提供的槽位并不相同，变量前缀随调用场景选择。

## 写入当前实体并读取结果

解析完成后，通过基类选择的 Memory 保存五项结果，局部操作如下：

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

对象参数存在时写入原对象，缺席时保留原键。命令日志记录当前规则与解析值，随后返回 `enabled`。条件示例的后一行读取 `$entity.GearIn_rulecmd_last`，可以立即取得这次刚写入的 2.4。

**条件命令在候选匹配时就执行。** 当前候选是否最终胜出，是后面的分数选择。因此本例演示状态写入与布尔结果，正式条件中的副作用要另行决定。

::: warning 条件通过与结果获选是不同时间
候选条件中的命令已经可以改写 Memory，即使当前规则随后落选。编者建议把费用扣除、货物转移与奖励写入放在结果脚本中，让这些动作跟随实际获选结果执行。
:::

需要验证对象参数时，在已取得目标实体的 Java 调用点，向它的 Memory 保存现有对象：

```java
target.getMemoryWithoutUpdate().set(GearInRuleCommand.MEM_KEY_OBJECT, target);
```

脚本再通过 `$entity.GearIn_rulecmd_object` 取得同一对象。结果属于舰队实体，当前人物 LOCAL 保留自己的状态，读取时继续使用对应前缀。

## 完整最小例

保存 `src/data/scripts/gearin/rules/GearInRuleCommand.java`，设置文件追加命令包，规则文件保留原表头并加入本节入口。

```json
{
  "ruleCommandPackages": [
    "data.scripts.gearin.rules"
  ]
}
```

```csv
id,trigger,conditions,script,text,options,notes
GearIn_rules_cmd_menu,PopulateOptions,"$menuState == fleetConvMain
$faction.id == independent
!$entity.isHostile",,,"GearIn_rules_cmd_open_condition:解析命令参数
GearIn_rules_cmd_open_script:记录命令参数",通讯命令入口
GearIn_rules_cmd_open_condition,DialogOptionSelected,$option == GearIn_rules_cmd_open_condition,FireBest GearInRulesCommandCondition,,,条件调用
GearIn_rules_cmd_open_script,DialogOptionSelected,$option == GearIn_rules_cmd_open_script,FireBest GearInRulesCommandScript,,,脚本调用
GearIn_rules_cmd_condition,GearInRulesCommandCondition,"GearInRuleCommand ""扫描 $playerName"" 2.4 true highlight
$entity.GearIn_rulecmd_last >= 2",,扫描参数已经解析。,"GearIn_rules_cmd_back:返回通讯",条件结果
GearIn_rules_cmd_script,GearInRulesCommandScript,,"GearInRuleCommand ""补给"" 3.0 false good $entity.GearIn_rulecmd_object",参数已经记录到当前实体。,"GearIn_rules_cmd_back:返回通讯",脚本结果
GearIn_rules_cmd_back,DialogOptionSelected,$option == GearIn_rules_cmd_back,EndConversation,,,返回
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

/** 解析当前规则参数，写入实体 Memory 并返回布尔条件结果。 */
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

前置数据：已加入命令包、Java 类与本节实际通讯入口记录，能够与非敌对独立舰队通讯。

1. 在 MOD 根目录执行 `gradle jar`，再用 `jar tf jars/GearIndustry.jar` 查找 `data/scripts/gearin/rules/GearInRuleCommand.class`。
2. 启动游戏，在 `starsector.log` 中确认两条 `GearIn_rules_cmd_*` 规则完成加载。
3. 在独立舰队通讯选择 "解析命令参数"，查看条件结果与命令日志，再选择返回通讯。
4. 在同一通讯选择 "记录命令参数"，读取实体中的 `label`、`last`、`count` 与 `color`；对象参数按该实体已有键读取。
5. 把数值参数改为非数字文本并重启，确认命令记录类型错误；随后恢复为 `2.4`。
6. 分别把布尔参数改为 `false` 与 `1`，确认两次条件规则都不会进入候选；随后恢复为 `true`。
7. 通过变量参数传入一个已有对象，确认 `getObject(...)` 把同一个对象写入 `$GearIn_rulecmd_object`。

预期结果：

- JAR 包含完整路径的 `GearInRuleCommand.class`；
- 条件调用解析出 `label=扫描 <玩家名>`、`amount=2.4`、`count=2`、`enabled=true`，并显示条件规则文本；
- 脚本调用返回 `false` 后仍写入 `label`、数值、颜色和可用的对象；
- 非数字参数被 `isFloat(...)` 拒绝，日志出现 `invalid parameter type`；
- `false` 与 `1` 都不能让条件规则成立，只有 `true` 返回 `true`；
- 每次日志包含当前 `ruleId`，多次调用之间的结果由 Memory 保存。

## 故障分析

- **启动时报告命令找不到**：`ruleCommandPackages` 缺少 `data.scripts.gearin.rules`，或 JAR 中没有对应完整类路径。
    - **解决方案**：核对设置数组、`data/scripts/gearin/rules/GearInRuleCommand.class` 和 MOD 的 JAR 声明。

- **规则条件始终不成立**：布尔参数不是 `true`，或命令被参数数量与类型检查提前拒绝。
    - **解决方案**：核对前三个参数依次为字符串、可解析数值和 `true` / `false`。

- **对话出现参数转换异常**：代码在检查 `isFloat(...)` 或 `isBoolean(...)` 之前直接调用了取值方法。
    - **解决方案**：先检查参数数量和格式，再调用 `getFloat(...)`、`getInt(...)` 或 `getBoolean(...)`。

- **脚本列返回 `false` 后仍继续执行**：脚本执行只使用命令的写入结果，不使用 `execute(...)` 的返回值控制后续行。
    - **解决方案**：把需要参与匹配的调用放在 `conditions`，把状态变更放在 `script`。

- **变量读取了错误的值**：变量前缀没有对应 Memory 槽位，解析转而从 LOCAL 读取完整键名。
    - **解决方案**：核对 `memoryMap` 提供的槽位，并让参数中的前缀与槽位名称一致。

- **对象参数始终为 `null`**：`getObject(...)` 收到的是字面量，或目标 Memory 没有保存该键。
    - **解决方案**：传入指向现有对象的变量 `token`，并确认对象位于该变量指定的 Memory。

- **非对话调用在替换字符串时异常**：`getStringWithTokenReplacement(...)` 需要非 `null` 的 `dialog`。
    - **解决方案**：先检查 `dialog`，非对话上下文使用 `getString(...)`。

## 总结

通讯规则通过搜索包找到命令，参数 token 取得当前输入，Java 将解析结果写到实体 Memory。条件使用返回值，脚本执行写入，两个入口分别确认这项行为。

命令处理当前请求，实际对象保存跨调用结果。搜索配置、参数来源与保存槽位对应起来，Rules 就能继续使用这次 Java 操作产生的状态。

## 建议参考项

- `com.fs.starfarer.api.campaign.rules.CommandPlugin`：Rules 命令接口与三个调用入口；
- `com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin`：普通命令的默认实现与实体 Memory 选择；
- `com.fs.starfarer.api.util.Misc`：`tokenize` 与 `Token` 参数解析方法；
- `com.fs.starfarer.api.campaign.rules.MemoryAPI`：命令参数来源与解析结果保存位置；
- `com.fs.starfarer.api.campaign.InteractionDialogAPI`：文本 `token` `replacement` 使用的对话目标；
- `data/config/settings.json`：`ruleCommandPackages` 搜索包配置；
- `data/campaign/rules.csv`：命令简单类名、参数和调用列。
