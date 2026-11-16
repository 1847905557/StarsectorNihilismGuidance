# Memory 进阶

前几节的规则里，有的键不带前缀，有的带着 global，有的带着 entity。

这一节把对话里能读到的 Memory 对一遍，再补上 [C10-S1] 没讲到的几种用法。这里没有新的成品，只有片段，查表用。

---

_本节预期阅读时间：\~10 分钟。_

_本节预期操作时间：\~5 分钟。_

## 前置知识

- [C10-S1] 的三层 Memory、`set(...)` 与限时值；
- [C15-S2] 的条件与赋值写法；
- [C15-S4] 中命令收到的 `memoryMap`。

## 对话里的几块 Memory

打开对话时，原版把相关对象的 Memory 收进一张表，就是 [C15-S4] 中命令收到的 `memoryMap`。表的键就是规则里的前缀，原版把它们定义在 `MemKeys`：

| 前缀 | 读的是 |
| ---- | ------ |
| `$global.` | 全局 Memory，与 `sector.getMemoryWithoutUpdate()` 是同一块 |
| `$player.` | 玩家角色 |
| `$market.` | 当前市场 |
| `$sourceMarket.` | 交互对象所属的市场 |
| `$faction.` | 交互对象所属的势力 |
| `$entity.` | 交互的实体或舰队 |
| `$personFaction.` | 正在对话的人物所属的势力 |
| 无前缀 | `local`，当前交互对象自己的 Memory |

不是每次对话都有全部这些，与舰队通讯就没有市场。前缀对应的那块不存在时，条件按不成立处理。

**没有前缀时读的是 `local`。** 与人物对话时，`local` 是那个人物自己的 Memory，[C15-S1] 的 `$GearIn_asterMet` 就记在 Aster 身上。换一个人物，这个标记就不存在，原版的 `$contact_printedFirstReturnGreeting` 也是这样记在每个人物自己身上的。

一个标记该记在谁身上，按 [C10-S1] 的原则判断：属于整个存档的写 `$global.`，只属于这个人物的不加前缀。记仇是齿轮工业对玩家的态度，所以在全局；见没见过面只关乎 Aster，所以在她身上。

## 在 Java 中按前缀取 Memory

命令里取某一块 Memory，用 `MemKeys` 的常量作键：

```java
MemoryAPI local = memoryMap.get(MemKeys.LOCAL);
MemoryAPI market = memoryMap.get(MemKeys.MARKET);
```

取不到时返回 `null`，使用前要判断。`BaseCommandPlugin.getEntityMemory(memoryMap)` 优先取 `entity`，没有时退回 `local`，原版的许多命令都用它取交互对象的 Memory。

全局 Memory 不必从表里取，[C15-S4] 直接用了 `Global.getSector().getMemoryWithoutUpdate()`，两者是同一块。

## 带更新与不带更新

实体有两种取 Memory 的方法：

| 方法 | 区别 |
| ---- | ---- |
| `getMemory()` | 先刷新这个实体的动态事实，再返回 |
| `getMemoryWithoutUpdate()` | 直接返回，不刷新 |

动态事实是原版每次重新计算、写进 Memory 的值，比如舰队与玩家的关系、市场当前的状态。原版注释提醒，`getMemory()` 的返回值应当保存下来反复使用，不要每次都调用。自己写入、自己读取的键都用不着刷新，前几章一律用了 `getMemoryWithoutUpdate()`。

## 期限

`set(key, value, days)` 的第三个参数是生涯天数。已经写入的键，可以单独改期限，或者查询剩余天数：

```java
memory.expire("$GearIn_patrol_grudge", 10f);
float left = memory.getExpire("$GearIn_patrol_grudge");
```

规则脚本里的写法是在值后面跟一个数字，`$key = true 30` 表示 30 天后过期，跟 `0` 表示永不过期。原版的规则几乎都写成 `= true 0`，这里的 0 不是 "立刻过期"。

## 跟着另一个键一起消失

`addRequired(key, requiredKey)` 让一个键依附于另一个键：被依附的键全部消失之后，这个键也跟着被删除。

```java
memory.set("$GearIn_grudgeWarned", true);
memory.addRequired("$GearIn_grudgeWarned", "$GearIn_patrol_grudge");
```

记仇标记过期或被 [C15-S4] 清掉时，"已经警告过" 这个标记会自动消失，下次记仇时又能重新警告一次，用不着另外去清理它。同一个键可以依附多个键，要等它们全部消失才会被删除。

## Memory 常用方法

| 方法 | 作用 |
| ---- | ---- |
| `set(key, value)` | 写入，永不过期 |
| `set(key, value, days)` | 写入，指定天数后过期 |
| `unset(key)` | 删除 |
| `contains(key)` | 是否存在 |
| `is(key, value)` | 是否等于某个值 |
| `getBoolean`、`getFloat`、`getInt`、`getString` | 按类型读取，键不存在时返回默认值 |
| `expire(key, days)` | 改写期限 |
| `getExpire(key)` | 剩余天数 |
| `addRequired(key, requiredKey)` | 依附另一个键 |
| `getKeys()` | 全部键 |

## 常见问题

- **规则的条件从不成立**：前缀写错，读的是另一块 Memory。
    - **解决方案**：按上表核对前缀；没有前缀时读的是交互对象自己的 Memory。

- **换一个人物，"见过面" 的标记就没了**：标记没有前缀，记在了上一个人物身上。
    - **解决方案**：只属于这个人物的标记就该如此；要对整个存档生效，写 `$global.`。

- **命令里取到的 Memory 为空**：这次对话没有这一块，比如舰队通讯中取 `market`。
    - **解决方案**：使用前判断是否为 `null`。

## 建议参考项

- `com.fs.starfarer.api.campaign.rules.MemKeys`：对话中各块 Memory 的键名。
- `com.fs.starfarer.api.campaign.rules.MemoryAPI`：期限与依附的注释。
- `com.fs.starfarer.api.campaign.SectorEntityToken`：`getMemory()` 与 `getMemoryWithoutUpdate()` 的注释。
- `com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin`：`getEntityMemory(...)`。
