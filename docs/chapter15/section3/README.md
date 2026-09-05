# 制作自定义 Rules 命令

本节创建并注册自定义规则命令 `GearInRuleCommand`，让 `rules.csv` 能够执行 MOD 代码。示例会解析命令参数并再次触发规则；错误的再入条件可能造成重复执行或卡死。

*本节预期阅读时间：~20 分钟。*

*本节预期操作时间：~15 分钟。*

## 学习目标

完成后，您将能够：

- 创建自定义规则命令类，并完成 `ruleCommandPackages` 追加与 rules.csv 脚本列注册；
- 解释 rulecmd 的实例化生命周期：每次调用新建实例、`execute(ruleId, dialog, params, memoryMap)` 四参签名、条件列与脚本列对返回值的不同处理、`dialog` 为 null 时异常的红字/重抛处理；
- 解析 `Misc.Token` 参数，并核对 `getString` / `getFloat` / `getBoolean` / `getInt` 等方法的语义与陷阱；
- 解释 `FireBest` / `FireAll` 的再入机制与约束：防自再入、终止性责任、`$fireAllTrigger` 与 `FireAllIntercept` 拦截，以及 Java 侧 (非规则上下文) 的再入调用点；
- 验证本节最小示例的注册、对话、再入与读档行为；
- 从症状定位故障，沿**数据文件 → 注册入口 → 运行使用方 → 可观察结果**四个位置复核。

## 前置知识

- [C6-S1] 的 JAR 构建、`mod_info.json` 的 JAR 声明与 API 依赖；
- [C7-S1] 的 `modPlugin`、`onGameLoad` 与持久数据一次性判断；
- [C15-S1] 的 `rules.csv` 七列、匹配流程、条件脚本语法、结果执行与 `$option` 回流；
- [C15-S4] 的 `MemoryAPI` 过期、持久化与记忆键维护。

## 术语

按术语约定，本节首次完整讲解以下词；API 类名保留源码大小写：

- **规则命令 (rule command)**：API 类名保留 `BaseCommandPlugin`(接口为 `CommandPlugin`)。rules.csv 条件/脚本列里第一个 token 不是变量的行即命令调用；命令类按 `ruleCommandPackages` 里的包名加简单类名定位。
- **Rules 内存 (memory)**：见术语约定，归属 [C15-S4] (API 名保留 `MemoryAPI`)；本节只使用 `MemoryAPI.set/get` 的最小面。
- **触发器 (trigger)**、**条件 (condition)**、**结果 (result)**、**规则 (rule)**：语义见规则系统页术语节，本节沿用。

## 注册与实例化

命令类被找到需要包名与类名两条信息，包名来自 `settings.json` 的 `ruleCommandPackages`，类名来自 rules.csv 条件/脚本列的第一个 token。加载期解析到命令调用时会立即实例化一次来校验注册；运行期每次执行都会重新实例化，因此命令实例上不能保存任何跨调用的状态。这两条结论决定了命令类的最小形态，即继承 `BaseCommandPlugin`、提供可用的无参构造、只覆写 `execute`。

### 命令解析链 (加载期)

规则行在资源加载期被解析成规则表达式 (`Rules.loadRules` 拆条件/脚本行并构造表达式)。解析到命令调用时，会定位命令类：

1. 命令包列表来自 `settings.json` 的 `ruleCommandPackages`(`Starsector/starsector-core/data/config/settings.json`，原版 5 个包：`com.fs.starfarer.api.impl.campaign.rulecmd` 及其 `salvage` / `newgame` / `missions` / `academy` 子包)。
    - 命令包列表在加载期只读取一次并缓存。
2. MOD 可以在自己的 `data/config/settings.json` 里声明同名数组追加自己的包：`settings.json` 经文本读取工具的 `readJSONMerged` 合并，**数组追加** (`mergeJSON`；仅 `core_clearArray` 标记或 color/button/`music_*` 键走整体替换)。
    - 合并发生在设置加载阶段 (读取 settings 的 JSON，由游戏启动器在资源加载前调用)，早于 `Rules.loadRules`，所以追加的包在命令解析时已就位。
3. 命令类解析会逐包尝试 `Class.forName(pkg + "." + name, false, 脚本类加载器父加载器)` 并 `newInstance()`，**加载期即实例化一次**，验证类存在、可实例化、无参构造可用；全部包都找不到时抛 `RuntimeException("Command [...] not found in packages:...")` → 资源加载崩溃、游戏启动中止。
4. 脚本列的命令类还会被脚本类加载器记录，供 Janino 即时编译 MOD 根下以源码形式提供的命令类；jar 内类不依赖该机制。

### 实例化与执行链速查表

执行期命令类由脚本类加载器实例化：类名缓存 (`Class.forName` 仅在缓存未命中时执行)、**每次调用都执行 `newInstance()`**，rulecmd 实例是 "每次调用一个"，任何字段状态都不可跨调用使用。想在字段里攒点状态的话，下次调用拿到的是全新实例。调用点有三处，各自消费不同的方法：

| 调用点 | 位置 | 消费的方法 | 返回值用途 |
|---|---|---|---|
| 条件求值 | 规则求值阶段调用 `execute` | `execute` | 布尔结果参与匹配 (`!` 取反后判真)；`execute` 抛异常 → `dialog` 非 null 时文本面板加红字段落 `<异常类>: <消息>` 并返回 null，`dialog` 为 null 时重抛 |
| 脚本执行 | `Rule.runScript` | `execute` | **返回值被丢弃** (只执行不收集) |
| 选项收集 | `FireBest`/`FireAll` 的选项组装 (`com.fs.starfarer.api.impl.campaign.rulecmd.FireBest` / `FireAll`) | `doesCommandAddOptions()`、`getOptionOrder(params, memoryMap)` | 判定是否选项型命令、排序用 order |

公开 API 面 (`com.fs.starfarer.api.campaign.rules.CommandPlugin`)：

```java
public interface CommandPlugin {
    boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap);
    boolean doesCommandAddOptions();
    int getOptionOrder(List<Token> params, Map<String, MemoryAPI> memoryMap);
}
```

`BaseCommandPlugin`(`com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin`) 提供默认实现与工具方法：

- `doesCommandAddOptions()` 默认 false；
- `getOptionOrder` 默认 0；
- 静态 `getEntityMemory(memoryMap)` (优先 `MemKeys.ENTITY` 否则 `MemKeys.LOCAL`)。

自定义命令只需继承它并覆写 `execute`。

命令接口的执行入口只有 `execute` —— 生命周期由 "每次调用 newInstance + 四参 execute" 构成。命令类拿到的是解析后的 `Token` 列表而不是原始字符串，接下来看它怎么切分和取值。

## Token 参数解析

命令行的参数由 `Misc.tokenize` 切成 `Token` 列表 (`com.fs.starfarer.api.util.Misc`)：空白切分；`"` 引号成组 (引号内含空格/特殊字符，token 类型按内容判定)；`\` 转义下一个字符；`=`、`==`、`!` 等操作符独立成 OPERATOR token。`Token` 有 VARIABLE(`$` 开头)、LITERAL、OPERATOR 三类。

### Token 常用方法速查表

常用方法：

| 方法 | 语义 | 陷阱 |
|---|---|---|
| `getString(memoryMap)` | 变量 token → `getVarNameAndMemory` 取记忆值；字面量 token → 原样字符串 | 变量解析失败抛 `RuleException`(`getVarNameAndMemory`：无 `.` 的 token 回落 `MemKeys.LOCAL`，local 也不存在即抛 "`No memory found for keys`") |
| `getStringWithTokenReplacement(ruleId, dialog, memoryMap)` | 先 `getString` 再做规则 token 替换 (`$playerName` 等) | 内部调 `dialog.getInteractionTarget()`，`dialog` 为 null 会 NPE，必须先判空 |
| `getFloat(memoryMap)` | 变量 → `memory.getFloat`；字面量 → `Float.parseFloat` | 非数字字面量抛 `NumberFormatException` → 红字段落/重抛 |
| `getBoolean(memoryMap)` | 变量 → 记忆字符串；字面量 → `Boolean.parseBoolean` | **只有 `"true"`(忽略大小写) 为 true，`"0"`/`"1"`/`"yes"` 全是 false** |
| `getInt(memoryMap)` | `Math.round(getFloat)` | 同上 getFloat 的异常 |
| `getObject(memoryMap)` | 变量 → `memory.get` 原对象；字面量 → null | 读对象型记忆 (如列表) 用这个 |
| `isVariable()` / `isLiteral()` / `isBoolean` / `isFloat` | 类型/取值预检 | 先用 `isFloat` 预检可避免 getFloat 抛异常 |

参数检查是 rulecmd 的标准写法：`FireBest` 自己都直接 `params.get(0)`(空参数 → `IndexOutOfBoundsException` → 红字)，所以自定义命令必须先判 `params.isEmpty()` / `params.size() > n`。

原版命令的 trigger 参数还支持变量形式：`params.get(0).isVariable()` 时经 `getVarNameAndMemory` 从记忆取值。

参数检查是自定义命令必须满足的要求

命令体第一件事就是先做 `params.isEmpty()` / `params.size() > n` 检查。内置 `FireBest` / `FireAll` 自身都不检查，越界即异常：对话上下文红字段落，非对话上下文直接重抛。连原版命令都直接 `params.get(0)`，自定义命令更没有理由省掉这一步。

常见坑：布尔参数只认 "true"

`getBoolean` 底层是 `Boolean.parseBoolean`，只有字面量 `"true"`(忽略大小写) 解析为 true；`"0"`、`"1"`、`"yes"` 全部解析为 false。CSV 里布尔位写数字不会报错，只会 "不生效"。

## FireBest / FireAll 与再入

脚本列要触发另一批规则时，用内置命令 `FireBest` / `FireAll`。`FireBest` / `FireAll` 本身是 `BaseCommandPlugin` 子类 (`com.fs.starfarer.api.impl.campaign.rulecmd` 包)，它们的 `execute` 从脚本列被调用时会**再入**规则引擎：以同一 `dialog` 与 `memoryMap` 对目标触发器重新匹配并执行命中规则。再入有两条边界需要记住，引擎只跳过正在执行的规则 id 本身，同触发器的其他规则照常命中；嵌套执行与当前调用共享同一个 `dialog` 与 `memoryMap`。

链路 (匹配与结果执行已在规则系统页讲过，这里补再入视角)：

1. 静态入口 `FireBest.fire(ruleId, dialog, memoryMap, params)` 与 `FireAll.fire(...)` 把参数字符串 `Misc.tokenize` 后交给 `execute`。
2. `execute` 解析 `params[0]` 为触发器名 (变量则取记忆值) 后调用 `rules.getBestMatching(ruleId, trigger, dialog, memoryMap)`(`FireBest`) 或 `rules.getAllMatching(...)`(`FireAll`)，**`ruleId` 原样传入**。
3. 匹配时引擎跳过 id 等于 currentRule 的规则 (`Rules` 的 `getRulesFor` / `getBestMatchingRule`)，防**自再入**机制由此生效：正在执行的规则不能在同一触发器的再入匹配中命中自己。注意只跳过同 id：同触发器内其他规则可以命中。
4. 命中规则的 `applyRule`(`FireBest`) 在**同一个** `dialog` 上清空/添加选项 (`keepOptions` 为真时不清空)、追加文本、`runScript`；`FireAll` 还会先写 `$fireAllTrigger` 再 `FireBest` 触发 `FireAllIntercept`(`FireAll`) —— 拦截规则命中则本次 `FireAll` 整体跳过 (原版仅 1 条拦截规则，`data/campaign/rules.csv`，条件是 `$fireAllTrigger == PopulateOptions` 等，不影响自定义触发器)。

为什么需要 "防自再入"

脚本列命令再入同一触发器是常规操作：若引擎不跳过当前规则 id，`FireBest` 的调用本身就会命中正在执行的规则，形成必然的无限循环。只跳过同 id 既保住终止性，又保留同触发器其他规则在再入中的命中能力。

再入约束：

- **共享上下文**：嵌套的匹配/执行使用同一 `memoryMap` 与同一 `dialog` —— 内层写记忆外层可见；内层非 keepOptions 的 `FireBest` 会清空外层对话的选项面板。
- **终止性责任在编者**：引擎只跳过当前规则 id。
    - 同一触发器内两条规则互相 `FireBest` 对方会无限递归 (A 命中 B，B 再入时跳过 B 命中 A，A 再入时跳过 A 命中 B...)，表现为游戏卡死/栈溢出；`dialog` 为 null 的上下文里异常会直接重抛。
    - 再入时把 `ruleId` 原样透传给 `getAllMatching(ruleId, params.get(0).string, ...)` 是保留 currentRule 的标准写法。
- **随机源单次**：`RulesAPI.setRandomForNextRulePick` 只影响下一次 pick，用后即清 —— 多层再入里多个 pick 依次消费该随机源，不能当全局随机源。

::: caution 再入链不允许出现环

再入链必须设计为有向无环，目标触发器不要反向指回来源触发器。两条规则一旦互相 `FireBest`，游戏就会无响应、进程占满 CPU、日志尾部反复滚同一条规则；没有红字，栈溢出之前很难察觉。

:::

### 代码侧再入调用点速查表

规则引擎也可以从非规则上下文被 Java 直接再入 (此时 `currentRule` 传 null，无规则被跳过)：

| 调用点 | 位置 | 方式 |
|---|---|---|
| 规则驱动对话 | `com.fs.starfarer.api.impl.campaign.RuleBasedInteractionDialogPluginImpl` | `fireBest(trigger)` / `fireAll(trigger)` 包装；`optionSelected` 里写 `$option` 后 `fireBest("DialogOptionSelected")` |
| 贸易面板提示文本 | 贸易面板类 | `updateFlavorText` 组装 memoryMap(其中的 `local` 即实体记忆等) 后 `FireBest.fire(null, createFakeDialog(...), memories, "TradePanelFlavorText")`；`createFakeDialog` 把文本路由进面板的 `TextPanelAPI` |
| 通讯录面板 | 通讯录面板类 | 选中人物条目后 `FireBest.fire(null, parentDialog, plugin.getMemoryMap(), "OpenCDE")` |
| 通用面板 | 通用面板类 | `populateMemoryMap` + `fireRule(trigger)` |

共同点：`dialog` 是伪造的或真实的对话上下文；`dialog` 为 null 时规则命令内部任何对 `dialog.get*` 的调用都会 NPE —— `RuleAPI.runScript` 的 javadoc 明确写 "dialog can be null"(`com.fs.starfarer.api.campaign.rules.RuleAPI`)，所以命令里访问 `dialog` 前必须判空。

命令内访问 dialog 前必须判空

`dialog` 只在对话流程中非 null：面板再入 (贸易面板 / 通用面板等) 传的是伪造实例或 null。

判空检查必须写在任何 `dialog.get*`(含 `getStringWithTokenReplacement` 内部的 `getInteractionTarget`) 之前；漏判时非对话上下文异常被重抛而非红字，更难定位。

## 规则强度速查表

以上机制每条都带可观察的后果，整理成下面的规则清单，编写与排错时对照使用：

| 规则 | 强度 | 违反后的可观察结果 |
|---|---|---|
| 命令类必须位于 `ruleCommandPackages` 之一 (原版 5 包) | 必须 | 资源加载期 `RuntimeException("Command [...] not found in packages:")`，游戏启动中止 |
| 命令类必须无参可实例化 (命令解析与每次执行都 `newInstance()`，经脚本类加载器) | 必须 | 无参构造缺失 → 当作 "命令找不到" 加载崩溃；实例字段状态跨调用不可依赖 |
| 命令在 `params` 越界、`getFloat` 非数字字面量等情况下抛出的 RuntimeException，被规则执行阶段捕获：`dialog` 非 null → 红字段落 `<异常类>: <消息>`，`dialog` 为 null → 重抛 | 事实 | 对话里出现红字段落；非对话触发 (如面板再入) 直接异常冒泡 |
| `execute` 返回值双语义：条件列经 `isTrueFor` 判真、脚本列被 `runScript` 丢弃 | 事实 | 在脚本列写 `return false` 不能阻断后续脚本行 |
| `Token.getBoolean` 是 `Boolean.parseBoolean`：只有 `"true"`(忽略大小写) 为 true | 事实 | CSV 里传 `1` / `0` 当布尔参数，永远解析为 false |
| 再入匹配只跳过 id 等于 currentRule 的规则 | 事实 | 同触发器内两条规则互相 `FireBest` 会无限递归 (卡死/栈溢出)；不同 id 的同触发器规则在再入中仍可命中 |
| 选项型命令 (`doesCommandAddOptions()` 为 true) 在脚本阶段被跳过，由 `FireBest`/`FireAll` 提前执行 | 必须 | 选项型命令写在脚本列不会在脚本阶段重复执行 |
| `FireAll` 先写 `$fireAllTrigger` 再 `FireBest` 触发 `FireAllIntercept` | 事实 | 拦截规则命中时本次 `FireAll` 整体跳过 (原版仅 1 条，条件 `$fireAllTrigger == PopulateOptions` 等，自定义触发器不受影响) |
| 目标规则带选项且 `FireBest` 非 keepOptions 时，先清空选项面板再加目标规则选项 (整块在 `!options.isEmpty()` 内) | 事实 | 再入的内层 `FireBest` 命中带选项的规则时会清掉外层对话已有的选项；目标规则无选项则不动面板 |
| 命令内访问 `dialog`(含 `getStringWithTokenReplacement` 内部的 `getInteractionTarget`) 前必须判 null(`RuleAPI.runScript` 的 javadoc 声明 `dialog` 可为 null) | 必须 | 非对话触发 (贸易面板 / 通用面板等面板再入) 时 NPE，且 `dialog` 为 null 时异常被重抛 |
| `settings.json` 的 `ruleCommandPackages` 数组按合并语义追加 (文本读取工具的 `mergeJSON`；`core_clearArray` 可整体清空) | 事实 | 不声明则 MOD 自定义包的命令 "找不到"；`core_clearArray` 会抹掉原版 5 包 (全部命令找不到) |

## 组装 Rules 命令实现

机制与边界都明确了，现在把它们组合成一套可运行的最小例。最小例覆盖数据文件 (rules.csv 行与记忆键)、注册入口 (settings.json 的包与脚本列简单类名)、运行使用方 (规则执行的 `execute` 调用与 `FireBest` 再入)、可观察结果 (命令日志行、写入的记忆键、再入命中的文本) 四个位置。

在先修单元建立起的 MOD 布局与 `data/campaign/rules.csv` 之上新增：

- 一份追加行 (本节 `GearIn_rules_cmd_*` 前缀)；
- `data/config/settings.json` 追加 `ruleCommandPackages` 键；
- 两个 Java 类 (`GearInRuleCommand` 与入口支撑 `GearInRuleCmdOpener`)。

不新增其它资源、不修改任何既有文件：

```text
Starsector/mods/GearIndustry/
├── build.gradle
├── mod_info.json
├── data/
│   ├── campaign/
│   │   └── rules.csv                     # 追加本节 GearIn_rules_cmd_* 行 (本节示例所有权)
│   └── config/
│       └── settings.json                 # 追加 ruleCommandPackages 键 (本节新增键)
├── src/
│   └── data/scripts/gearin/rules/
│       ├── GearInRuleCommand             # 本节新增：自定义规则命令 (示例所有权)
│       └── GearInRuleCmdOpener           # 本节新增：一次性对话入口 (EveryFrameScript)
└── jars/
```

### 注册 Rules 命令

```json
{
  "ruleCommandPackages": [
    "data.scripts.gearin.rules"
  ]
}
```

数组追加语义：合并后的包列表 = 原版 5 个 + 本包；`GearInRuleCommand` 以简单类名出现在 rules.csv 时按 `data.scripts.gearin.rules.GearInRuleCommand` 定位。

### 配置 Rules 数据

```csv
id,trigger,conditions,script,text,options,notes
GearIn_rules_cmd_welcome,GearInRuleCmdOpener,,"GearInRuleCommand GearInRuleCmdResult 2 true
SetShortcut GearIn_rules_cmd_leave ""ESCAPE""","欢迎，$playerName。本对话框的 script 列调用了本节自定义规则命令 GearInRuleCommand：参数为触发器名 GearInRuleCmdResult、浮点 2、布尔 true。命令先写记忆再 FireBest 再入目标触发器。",GearIn_rules_cmd_more:FireBest 再入
5:GearIn_rules_cmd_all:FireAll 再入,"本节示例所有权 GearIn_rules_cmd_*"
GearIn_rules_cmd_result,GearInRuleCmdResult,$GearIn_rulecmd_last >= 2,,再入命中文本：命令把 amount=2 写入 $GearIn_rulecmd_last，FireBest 再入本触发器后条件成立。,,本节示例所有权 GearIn_rules_cmd_*
GearIn_rules_cmd_more,DialogOptionSelected,$option == GearIn_rules_cmd_more,"GearInRuleCommand GearInRuleCmdResult 3 false",再次调用命令 (amount=3) 并再入同一触发器：FireBest 只执行一条最佳规则。,,本节示例所有权 GearIn_rules_cmd_*
GearIn_rules_cmd_all,DialogOptionSelected,$option == GearIn_rules_cmd_all,"FireAll GearInRuleCmdResult",FireAll 再入同一触发器：执行全部命中规则。,,本节示例所有权 GearIn_rules_cmd_*
GearIn_rules_cmd_leave,DialogOptionSelected,$option == GearIn_rules_cmd_leave,DismissDialog,,,本节示例所有权 GearIn_rules_cmd_*
```

### 编写 Rules 命令

```java
package data.scripts.gearin.rules;

import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.impl.campaign.rulecmd.FireBest;
import com.fs.starfarer.api.util.Misc.Token;

/**
 * 完整最小例：自定义规则命令 (rulecmd)。
 *
 * 注册入口：data/campaign/rules.csv 脚本列按简单类名调用 (如
 * "GearInRuleCommand GearInRuleCmdResult 2 true")，类经 ruleCommandPackages
 * (data/config/settings.json 追加的 data.scripts.gearin.rules) 定位。
 *
 * 生命周期：规则加载期 getCommandClass 用 Class.forName + newInstance
 * 校验一次；每次执行经脚本类加载器的 loadScriptInstance 重新 newInstance 后调用
 * execute(ruleId, dialog, params, memoryMap) —— 实例字段不可跨调用使用。
 *
 * 行为 (参数 <trigger> [<amount>] [<keepOptions>])：
 * - params.get(0).getString：触发器名 (字面量或变量皆可)；
 * - params.get(1).getFloat：浮点参数，写入实体记忆 $GearIn_rulecmd_last(无过期，
 *   过期/持久化语义归 C15-S4)，供条件列 $GearIn_rulecmd_last >= 2 读取；
 * - params.get(2).getBoolean：布尔参数 (Token.getBoolean = Boolean.parseBoolean，
 *   只有 "true" 为真)，透传给 FireBest 的 keepOptions；
 * - 再入：FireBest.fire(ruleId, dialog, memoryMap, trigger) 用同一 dialog 与
 *   memoryMap 对目标触发器重新匹配；ruleId 原样透传，引擎跳过同 id 规则防自再入；
 * - 返回值：脚本列消费时被丢弃 (Rule.runScript 只执行不收集)；条件列消费时
 *   作为布尔判真 (isTrueFor)。
 *
 * 验证闭环：注册入口 (rules.csv 脚本列 + ruleCommandPackages)→ 数据文件 (记忆键
 * $GearIn_rulecmd_last)→ 运行消费者 (规则表达式的 execute / FireBest 再入)→
 * 可观察结果 (日志行、再入命中文本)。
 */
public class GearInRuleCommand extends BaseCommandPlugin {

    /** 命令写入的记忆键 (无过期；深度语义归 C15-S4)。 */
    public static final String MEM_KEY_LAST = "$GearIn_rulecmd_last";

    private static final Logger LOG = Global.getLogger(GearInRuleCommand.class);

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog,
                           List<Token> params, Map<String, MemoryAPI> memoryMap) {
        // 参数守卫：FireBest 自身都不判空直接 params.get(0)(越界即红字异常)，
        // 自定义命令必须先守卫。
        if (params.isEmpty()) {
            LOG.warn("GearInRuleCommand: expected params <trigger> [<amount>] [<keepOptions>]");
            return false;
        }

        String trigger = params.get(0).getString(memoryMap);
        float amount = params.size() > 1 ? params.get(1).getFloat(memoryMap) : 1f;
        boolean keepOptions = params.size() > 2 && params.get(2).getBoolean(memoryMap);

        MemoryAPI memory = getEntityMemory(memoryMap);
        memory.set(MEM_KEY_LAST, amount);

        // dialog 可能为 null(非对话触发，如面板再入)：先判空再再入。
        if (dialog == null) {
            LOG.warn("GearInRuleCommand: dialog is null; cannot fire trigger " + trigger);
            return false;
        }

        boolean fired = FireBest.fire(ruleId, dialog, memoryMap,
                trigger + (keepOptions ? " true" : ""));
        LOG.info("GearInRuleCommand: ruleId=" + ruleId + " trigger=" + trigger
                + " amount=" + amount + " keepOptions=" + keepOptions + " fired=" + fired);
        return fired;
    }
}
```

### 调用 Rules 命令

```java
package data.scripts.gearin.rules;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.impl.campaign.RuleBasedInteractionDialogPluginImpl;

/**
 * 完整最小例：对话入口 (支撑类，示例所有权主类为 GearInRuleCommand)。
 *
 * 注册入口：生涯回调 onGameLoad(调用点片段见页面正文)，persistent data 键
 * REGISTER_MARK 一次性门控 (沿用 C10-S1 / C15-S1 的模式，每个注册项用各自的键)。
 *
 * 行为：挂载后第一次 advance() 用 RuleBasedInteractionDialogPluginImpl 打开一个
 * 初始 trigger 为 GearInRuleCmdOpener 的对话；对话的文本、脚本与选项全部来自
 * data/campaign/rules.csv 中本节拥有的 GearIn_rules_cmd_* 规则，其中脚本列调用
 * GearInRuleCommand 演示自定义命令、Token 解析与 FireBest/FireAll 再入。
 */
public class GearInRuleCmdOpener implements EveryFrameScript {

    /** 存档持久化标记：本演示是否已在该存档执行过 (随存档保存)。 */
    public static final String REGISTER_MARK = "GearIn_RuleCmdOpener";

    private static final Logger LOG = Global.getLogger(GearInRuleCmdOpener.class);

    /** 演示是否已执行；置位后 isDone() 返回 true，引擎下一帧自动移除本脚本。 */
    private boolean executed = false;

    public GearInRuleCmdOpener() {
        // 空构造：注册由调用方执行 (onGameLoad 的 persistent data 门内，见页面调用点片段)
    }

    @Override
    public boolean isDone() {
        return executed;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        if (executed) {
            return;
        }
        executed = true;
        CampaignUIAPI ui = Global.getSector().getCampaignUI();
        boolean shown = ui.showInteractionDialog(
                new RuleBasedInteractionDialogPluginImpl("GearInRuleCmdOpener"),
                Global.getSector().getPlayerFleet());
        LOG.info("GearIn rulecmd opener dialog shown=" + shown
                + " (initial trigger GearInRuleCmdOpener; 对话内容全部来自 rules.csv)");
    }
}
```

对应关系：

- `extends BaseCommandPlugin`：只需覆写 `execute`；`doesCommandAddOptions()` 默认 false，所以本命令是**非选项型**命令，在脚本阶段由 `runScript` 正常执行。
- `MEM_KEY_LAST = "$GearIn_rulecmd_last"`：命令写入的记忆键，与 rules.csv 条件列 `$GearIn_rulecmd_last >= 2` 逐字一致 (含 `$` 前缀；无过期，深度语义归 "Rules 内存" 单元)。
    - `getEntityMemory(memoryMap)` 在无活动人物时返回 `MemKeys.LOCAL`，即对话交互目标的实体记忆 (`RuleBasedInteractionDialogPluginImpl` 的 `updateMemory`)。
- 参数解析三连：`getString`(触发器名，字面量直取)、`getFloat`(`2` → 2.0f)、`getBoolean`(`true` → true；若写成 `1`/`0` 则恒为 false)。
- 再入：`FireBest.fire(ruleId, dialog, memoryMap, trigger + (keepOptions ? " true" : ""))`，ruleId 原样透传 (引擎跳过同 id 规则)，`" true"` 成为 FireBest 的第二个参数 (keepOptions)。
    - keepOptions 只在目标规则带选项时可见，本节目标规则无选项，仅经日志观察参数解析与传递。
- `dialog == null` 检查：非对话触发 (如面板再入) 时 `dialog` 为 null，直接再入会在 `performTokenReplacement` 的 `getInteractionTarget` 处 NPE。
- `GearInRuleCmdOpener`：与规则系统页的 `GearInRulesIntro` 同款的一次性 `EveryFrameScript`；`REGISTER_MARK` 键 `"GearIn_RuleCmdOpener"` 与调用点片段逐字一致；`new RuleBasedInteractionDialogPluginImpl("GearInRuleCmdOpener")` 自定义初始 trigger，候选池只有本节规则。
- welcome 规则文本里的 `$playerName` 由 token 替换链换成本档人物名；`SetShortcut` 把 ESC 绑定到 `GearIn_rules_cmd_leave`。
- `GearIn_rules_cmd_more` 脚本列再次调用命令 (amount=3) 演示 FireBest 再入；`GearIn_rules_cmd_all` 用内置 `FireAll` 再入同一触发器演示 "全部命中规则" (`FireAllIntercept` 只拦截 `$fireAllTrigger == PopulateOptions` 等，本触发器不受影响)。
- 记忆键 `$GearIn_rulecmd_last` / `$option` 的过期、持久化与维护语义归 "Rules 内存" 单元；本节只保证演示流程内的取值逻辑。

调用点 (工程片段，方法所在类由前置单元所有，本节只演示调用，不改动那些文件)：

```java
    // GearInModPlugin(由前置单元所有)onGameLoad(boolean newGame) 内新增本节注册块。
    // 每个注册项用各自的 persistent data 键，不要挂进插件已有的 CLASS_MARK 门。
    if (!Global.getSector().getPersistentData().containsKey(GearInRuleCmdOpener.REGISTER_MARK)) {
        Global.getSector().getPersistentData().put(GearInRuleCmdOpener.REGISTER_MARK, true);
        Global.getSector().addScript(new GearInRuleCmdOpener());
    }
```

构建命令 (在 `Starsector/mods/GearIndustry/` 执行，沿用编译工具链单元)：

```text
gradle jar
```

产物 `jars/GearIndustry.jar` 应包含 `data/scripts/gearin/rules/GearInRuleCommand.class` 与 `data/scripts/gearin/rules/GearInRuleCmdOpener.class`(可用 `jar tf` 核对)；

`data/campaign/rules.csv` 追加行与 `data/config/settings.json` 的 `ruleCommandPackages` 是 MOD 数据文件，随 MOD 目录被加载器合并读取。

注意示例路径 `Starsector/mods/GearIndustry` 是教程虚构路径，页面内路径互相自洽即可。

示例就绪后，按下面的方案验证注册、对话与再入行为。

## 验证方案

前置数据：编译工具链的 MOD 布局 [C6-S1]；`GearInModPlugin` 与 persistent data 一次性注册检查 ([C7-S1]，含本节调用点片段)；规则系统页的 `data/campaign/rules.csv` 与 `GearInRulesIntro` [C15-S1]；本节 `data/config/settings.json` 追加键、`GearIn_rules_cmd_*` 行、`GearInRuleCommand` 与 `GearInRuleCmdOpener`。

1. 游戏构建：在 `Starsector/mods/GearIndustry/` 执行 `gradle jar`，随后用 `jar tf jars/GearIndustry.jar` 核对产物。
2. 规则注册 (加载期)：启动游戏，检查 `starsector.log` 与启动器控制台标准输出。
3. 对话完整流程 (新档第一帧)：新建生涯，确认对话弹出，核对文本面板与选项面板；按 ESC 后核对日志。
4. FireBest 再入：点击 "FireBest 再入"，核对追加文本与日志行。
5. FireAll 再入：点击 "FireAll 再入"，核对追加文本与日志行。
6. 结束与读档：点击 "结束"(或按 ESC) 关闭对话；保存后读档。

预期结果：

- **游戏构建**：`BUILD SUCCESSFUL`；`jar tf` 列出 `data/scripts/gearin/rules/GearInRuleCommand.class` 与 `data/scripts/gearin/rules/GearInRuleCmdOpener.class`。
- **规则注册**：`starsector.log` 出现 `Loading CSV data from [...rules.csv]` 与本节 5 条 `Loading rule: GearIn_rules_cmd_*` 行 (由 `Rules.loadRules` 打印)；标准输出出现 `WARNING: variable [$GearIn_rulecmd_last] only used once; rule id: [GearIn_rules_cmd_result]`(`System.out.println`，非日志；单次使用变量属正常现象)；加载期无 `Command [...] not found in packages` / `Duplicate key` 等规则加载异常。
- **对话完整流程**：日志出现 `GearIn rulecmd opener dialog shown=true`，对话弹出；文本面板按顺序显示两段：welcome 文本 (含玩家名) 与 "再入命中文本：命令把 amount=2 写入 $GearIn\_rulecmd\_last，FireBest 再入本触发器后条件成立。"(welcome 的 `FireBest` 再入在 `applyRule` 的 `addText` 之后、`runScript` 阶段执行)；选项面板按 order 显示 "FireBest 再入"(order 0) 与 "FireAll 再入"(order 5)；日志出现 `GearInRuleCommand: ruleId=GearIn_rules_cmd_welcome trigger=GearInRuleCmdResult amount=2.0 keepOptions=true fired=true`；按 ESC 等效 "结束"(`SetShortcut` 生效)。
- **FireBest 再入**：`$option` 回流 `DialogOptionSelected`，`more` 规则命中，其脚本列再次调用命令 (amount=3) 再入同一触发器，`result` 规则再次命中追加一段 "再入命中文本"；日志出现 `GearInRuleCommand: ruleId=GearIn_rules_cmd_more trigger=GearInRuleCmdResult amount=3.0 keepOptions=false fired=true`。
- **FireAll 再入**：`all` 规则命中；`FireAll GearInRuleCmdResult` 再入执行全部命中规则，`result` 规则再次命中追加一段 "再入命中文本"；日志无 `GearInRuleCommand` 行 (FireAll 是内置命令)。
- **结束与读档**：`DismissDialog` 关闭对话，游戏恢复；读档后不出现 `GearIn rulecmd opener dialog shown=` 日志、不再弹对话 (`REGISTER_MARK` 门已关；对话本身不随存档保存)。
- 全程无 `gear_in` 引起的 `ERROR` 日志。

## 故障分析

- **启动时报告 rule command 未找到**：`ruleCommandPackages` 缺少包名、类未进入 jar，或 MOD jar 未启用。

    - **解决方案**：核对设置数组和 jar 内类路径，重启。

- **日志显示规则已加载但命令没有运行**：命令放在了错误列，或选项型命令被普通 `runScript` 跳过。

    - **解决方案**：状态写入放到 script，选项型命令交给 `FireBest` 或 `FireAll`。

- **对话出现红色异常段落**：参数无法转换、索引越界，或命令访问了 null dialog。

    - **解决方案**：先检查参数数量与类型，再判空访问对话对象。

- **匹配规则时 memory 被重复修改**：写状态命令放在 conditions，每次候选求值都会执行。

    - **解决方案**：将其移到 script。

- **规则执行导致卡死或栈溢出**：两个 trigger 通过 `FireBest` 相互调用，没有终止路径。

    - **解决方案**：把再入关系改为单向并透传当前 rule ID。

- **再入后外层选项被清空**：内层 `FireBest` 使用了默认 `keepOptions=false`。

    - **解决方案**：需要保留时传入 true。

- **布尔参数始终为 false**：参数使用了 `1`、`yes` 等字面量，`getBoolean` 只把 `true` 解析为真。

    - **解决方案**：改用 `true` 或 `false`。

- **`FireAll` 没有追加内容**：目标 trigger 无匹配项，或命中了 `FireAllIntercept`。

    - **解决方案**：核对 trigger 与拦截条件，再执行一次。

- **重建 jar 后命令行为未更新**：MOD 中仍有同名源码，或运行的 jar 不是最新产物。

    - **解决方案**：核对 jar 时间和类路径并移除重复来源，重启。

- **日志显示 `shown=false`**：现有对话或新手引导占用了交互界面。

    - **解决方案**：只在 CAMPAIGN 状态且界面空闲时触发。

- **非对话面板执行命令时卡死**：命令在 dialog 为 null 的上下文抛出异常，错误直接向外传播。

    - **解决方案**：命令内先检查 dialog 和参数。

## 总结

本节把规则命令讲成了一条完整的数据链：rules.csv 脚本列的简单类名经 `ruleCommandPackages` 定位到类，加载期实例化一次校验注册，运行期每次调用重新实例化并执行 `execute`；参数经 `Misc.Token` 解析后，命令写入记忆、再入目标触发器，最后以日志行、对话文本与记忆键作为可观察结果。

这条链上值得反复使用的判断有四处：注册成败看启动是否崩溃与 `Loading rule:` 日志；参数语义以 `Token` 方法为准 (`getBoolean` 只认 `"true"`，`getFloat` 遇非数字即红字)；再入必须设计成有向无环，引擎只跳过当前规则 id，终止性责任在编者；`dialog` 可能为 null，访问前必须判空。命令类本身没有任何可依赖的实例状态，每次调用都是新实例。

规则系统页的规则表与匹配流程是本节底座；本节的 `GearInRuleCommand` / `GearInRuleCmdOpener` 与 `GearIn_rules_cmd_*` 规则表将被 "Rules 内存" 单元的过期、持久化与记忆键维护语义继续消费与展开。命令行为异常时，沿**数据文件 → 注册入口 → 运行使用方 → 可观察结果**四个位置复核，通常比对着红字猜测更快。

## 建议参考项

- `com.fs.starfarer.api.campaign.rules.CommandPlugin`：rulecmd 接口三方法 (`execute` / `doesCommandAddOptions` / `getOptionOrder`)。
- `com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin`：默认实现与静态 `getEntityMemory`。
- `com.fs.starfarer.api.impl.campaign.rulecmd.FireBest`：再入命令 (静态 `fire`、`applyRule`、keepOptions 语义)。
- `com.fs.starfarer.api.impl.campaign.rulecmd.FireAll`：再入命令 (`$fireAllTrigger` 与 `FireAllIntercept` 拦截)。
- `com.fs.starfarer.api.util.Misc`：`tokenize` 与 `Token` 参数解析方法族。
- `com.fs.starfarer.api.campaign.rules.MemoryAPI`：Rules 记忆读写 (本节只使用 `set` / `get` 最小面)。
- `com.fs.starfarer.api.campaign.rules.RulesAPI`：`getAllMatching` / `getBestMatching` / `setRandomForNextRulePick`。
- `com.fs.starfarer.api.campaign.rules.RuleAPI`：`runScript`(javadoc 声明 `dialog` 可为 null)。
- `com.fs.starfarer.api.impl.campaign.RuleBasedInteractionDialogPluginImpl`：规则驱动对话插件 (`fireBest` / `fireAll` / `optionSelected`)。
- `com.fs.starfarer.api.campaign.InteractionDialogAPI`：对话面板接口 (`getOptionPanel` / `getTextPanel` / `dismiss`)。
- `data/config/settings.json`(MOD 侧)：追加 `ruleCommandPackages` 数组 (本节示例新增键)。
- `data/campaign/rules.csv`(MOD 侧)：本节 `GearIn_rules_cmd_*` 行 (示例所有权)。
- `Starsector/starsector-core/data/config/settings.json`：`ruleCommandPackages` 原版 5 包。
- `Starsector/starsector-core/data/campaign/rules.csv`：原版规则表 (`FireAllIntercept` 唯一拦截规则所在)。
