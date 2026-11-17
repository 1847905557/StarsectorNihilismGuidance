# Memory 进阶

在规则脚本与 Java 命令之间传值时，两边面对的从来不是同一块平坦的内存表：弹窗打开瞬间，引擎按 `MemKeys` 把全局、玩家、市场、势力与目标实体装进各自命名空间，再凭前缀点号去找对应那一层。

这层作用域分错半格，往往就会出现命令里刚改完状态、对话下一行却读不到新值的怪象。除了 [C10-S2] 交代的基础写法，前缀解析、主动刷新与键过期机制也得一并弄清。

---

_本节预期阅读时间：\~10 分钟。_

_本节预期操作时间：\~5 分钟。_

## 前置知识

- [C10-S2] 的各层 Memory、`set(...)` 与限时值；
- [C15-S2] 的条件与赋值语法；
- [C15-S4] 中命令收到的 `memoryMap`。

## 对话中的前缀映射与 `$` 规则

打开对话时，引擎按 `MemKeys` 把当时能取到的各方 Memory 装入 `memoryMap`，在 `rules.csv` 里写下的变量前缀直接对应这张映射表：

| 前缀 | 实际读取的 `MemoryAPI` |
| ---- | ---------------------- |
| `$global.` | 全局 Memory，即 `sector.getMemoryWithoutUpdate()` |
| `$player.` | 玩家角色 |
| `$market.` | 当前交互所在市场 |
| `$sourceMarket.` | 交互对象所属来源市场 |
| `$faction.` | 交互对象所属势力 |
| `$entity.` | 当前交互的星图实体或舰队 |
| `$personFaction.` | 正在对话的具名人物所属势力 |
| 无前缀 | `local`，即当前正在交互对象自身的 Memory |

需要注意几个极易踩空的细节：

1. **并非每次对话都备齐全部槽位**：例如在太空中与巡逻队通讯时就没有 `$market.`。对应前缀在 `memoryMap` 里缺失时，依赖它的条件会按不成立处理；
2. **`$prefix.key` 会自动给后半段补 `$`**：`Misc.Token` 在解析变量名时，遇到第一个点号会切出前缀作为 `MemKeys` 的表键，然后**主动在点号后半段前面拼接一个 `"$"`**。因此 Java 里叫 `"$GearIn_patrol_grudge"` 和 `"$isPatrol"` 的键，在规则表里必须写成 **`$global.GearIn_patrol_grudge`** 与 **`$entity.isPatrol`**，千万不能手顺写成 `$global.$GearIn_patrol_grudge`；
3. **无前缀即 `local`**：与具名人物沟通时，`local` 指向这名人物自己的 Memory，[C15-S1] 的 `$GearIn_asterMet` 与原版常见的 `$contact_printedFirstReturnGreeting` 都记在这里 —— 换一个人对话，这个键天然隔离；反之如敌意状态这种全势力级记忆，就应当记到 `$global.` 里。

## Java 命令中按 MemKeys 读取

在 `BaseCommandPlugin` 内部取用某一块 Memory，直接使用 `MemKeys` 常量作为键即可：

```java
MemoryAPI local = memoryMap.get(MemKeys.LOCAL);
MemoryAPI market = memoryMap.get(MemKeys.MARKET);
```

取不到的块会返回 `null`，访问前要先判空。若想按原版常规习惯在实体通讯与人物对话间通用，可调父类辅助方法 `BaseCommandPlugin.getEntityMemory(memoryMap)`：它优先返回 `MemKeys.ENTITY`，不存在时退回 `MemKeys.LOCAL`。对于全局记忆，`Global.getSector().getMemoryWithoutUpdate()` 与 `memoryMap.get(MemKeys.GLOBAL)` 指向完全同一份对象。

## getMemory() 与 getMemoryWithoutUpdate()

实体与市场均提供两个取 Memory 的方法：

| 方法 | 作用 |
| ---- | ---- |
| `getMemory()` | 先重算并刷新一遍该对象的动态事实 (关系、状态等)，再返回 |
| `getMemoryWithoutUpdate()` | 不做刷新计算，直接返回内存映射 |

读取自己写入的自定义状态键并不需要触发动态事实重算，应当统一使用 `getMemoryWithoutUpdate()`；只有在准备把实体交给对话判定原版实时属性且之前尚未刷新时才用 `getMemory()`，并且避免在高频循环里反复调用。

## 过期时间与键值依附

除了写入时通过 `set(key, value, days)` 指定期限外，`MemoryAPI` 还能对已存在的键改写剩余时间，或查询距离自动清理还剩几天：

```java
memory.expire("$GearIn_patrol_grudge", 10f);
float left = memory.getExpire("$GearIn_patrol_grudge");
```

在 `rules.csv` 的脚本列里，`$key = true 30` 表示 `30` 个生涯天后自动失效，而原版最常见到的 `$key = true 0` 表示**永不过期**，切勿误解为 `"0 天后立刻过期"`。

另一个在多阶段剧情里非常省心的能力是 `addRequired(key, requiredKey)` —— 让一个从属键依附于若干前置键，只要依赖的所有前置键都从同一块 `MemoryAPI` 里消失或过期，这个从属键就会随之自动清除：

```java
memory.set("$GearIn_grudgeWarned", true);
memory.addRequired("$GearIn_grudgeWarned", "$GearIn_patrol_grudge");
```

这样不管是 60 天倒计时到期还是玩家在 [C15-S4] 花钱撤掉了敌意标记，`"$GearIn_grudgeWarned"` 都会一并清理干净，完全不用在多条分支里到处手动补 `unset`。

## MemoryAPI 常用方法速查

| 方法 | 作用 |
| ---- | ---- |
| `set(key, value)` / `set(key, value, days)` | 写入永久键或带生命周期的限时键 |
| `unset(key)` | 立即删除键 |
| `contains(key)` / `is(key, value)` | 判断存在性或值相等 |
| `between(key, min, max)` | 判断数值是否处于闭区间内 |
| `getBoolean`、`getFloat`、`getInt`、`getString` | 按类型读取，键缺失时返回对应默认零值 |
| `expire(key, days)` / `getExpire(key)` | 修改或查询剩余有效天数 |
| `addRequired(key, requiredKey)` | 声明前置依赖键，依附对象全部清零时自动移除 |
| `getKeys()` | 获取当前全部键名集合 |

## 常见问题

- **规则条件始终判假**：前缀写错或多余写了双 `$` (例如写成了 `$global.$GearIn_patrol_grudge`)。
    - **解决方案**：核对 `MemKeys` 前缀并确保点号后面不加 `$`。

- **换了一个 NPC 后之前的交互进度全没了**：写入时没加前缀，键只存在上一个人物的 `local` 里。
    - **解决方案**：跨角色共享的状态改为写入 `$global.`。

- **Java 命令取 `MemKeys.MARKET` 抛出空指针**：玩家正在太空中和舰队对话，当前交互上下文不存在市场。
    - **解决方案**：先对 `memoryMap.get(...)` 做空值判断。

## 建议参考项

- `com.fs.starfarer.api.campaign.rules.MemKeys`：规则中可用的全部 Memory 槽位常量。
- `com.fs.starfarer.api.campaign.rules.MemoryAPI`：期限、`between` 与 `addRequired` 完整接口注释。
- `com.fs.starfarer.api.campaign.SectorEntityToken`：`getMemory()` 与 `getMemoryWithoutUpdate()` 注释说明。
- `com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin`：`getEntityMemory(...)` 辅助取法。
