# 读写 common 数据

存档里的东西只属于那一份存档。可有些记录是玩家这个人的，不是某一局的：比如他到底打掉过齿轮工业多少支巡逻队，这个数字换了存档也该接着往上加。

这种跨存档的记录，游戏给了一个专门的地方放。

---

_本节预期阅读时间：\~10 分钟。_

_本节预期操作时间：\~10 分钟。_

## 前置知识

- [C14-S2] 的记仇监听器，本节在它记账的地方顺手写一笔；
- [C14-S4] 的巡逻情报，本节在它的详情里显示这个数字；
- [C17-S1] 中用 `org.json` 读取 JSON 的写法。

## common 目录

`SettingsAPI` 提供了几个读写 common 目录的方法：

| 方法 | 作用 |
| ---- | ---- |
| `fileExistsInCommon(name)` | 文件是否存在 |
| `readTextFileFromCommon(name)` | 读出整个文件的文字 |
| `writeTextFileToCommon(name, text)` | 整份写入，原版注释写明上限 1MB |
| `deleteTextFileFromCommon(name)` | 删除 |

文件放在游戏目录的 `saves/common/` 下，文件名后面会多一个 `.data`。LunaLib 等 MOD 的设置也放在那里，打开看一眼就知道是什么样子。

**文件名带上 MOD 的名字。** 这个目录是所有 MOD 共用的，叫 `stats.json`，迟早会和别人的文件撞上。这里用 `GearIndustry_stats.json`。

## 读出来，改一下，整份写回

common 里只有文字，没有 "往文件里加一项" 这种操作。要改一个数字，就得读出整份、改掉那一项、整份写回：

```java
JSONObject stats = readStats();
stats.put(DEFEATED, stats.optInt(DEFEATED, 0) + count);
Global.getSettings().writeTextFileToCommon(FILE, stats.toString(4));
```

文件还不存在时，从一个空的 `JSONObject` 开始：

```java
if (!settings.fileExistsInCommon(FILE)) {
    return new JSONObject();
}
return new JSONObject(settings.readTextFileFromCommon(FILE));
```

`optInt(key, 0)` 在键不存在时返回 0，不抛异常。这里与 [C17-S1] 的选择正好相反：配置文件随 MOD 发布，缺了键是错误；common 文件是玩家机器上自己长出来的，第一次运行时什么都没有，缺了键再正常不过。

`toString(4)` 写出带缩进的 JSON，手动打开也能读。

## 出错时不要让游戏崩

读写 common 会碰到磁盘，文件也可能被玩家手动改坏。这些都不该让一场战斗结算之后游戏直接崩掉：

```java
try {
    return readStats().optInt(DEFEATED, 0);
} catch (Exception e) {
    Global.getLogger(GearInCommonStats.class).warn("读取 " + FILE + " 失败", e);
    return 0;
}
```

读不出来就当作 0，写不进去就放弃这一次，两种情况都在日志里留一行警告。一个跨存档的战绩数字，丢了也就丢了，不值得为它中断玩家的游戏。

## 在哪里写，在哪里读

[C14-S2] 的记仇监听器在记下仇之后加一行：

```java
GearInCommonStats.addPatrolsDefeated(defeated);
```

[C14-S4] 的巡逻情报在详情末尾显示这个数字：

```java
int total = GearInCommonStats.getPatrolsDefeated();
if (total > 0) {
    info.addPara("在您所有的存档里，齿轮工业一共折损了 %s 支巡逻队。", opad, Misc.getHighlightColor(), "" + total);
}
```

每次写都是一次完整的读改写。击败巡逻队这种事一场战斗才发生一次，这样写没有问题；要是每帧都要更新的数据，就该先记在内存里，隔一阵再写一次。

## LazyLib 的写法

LazyLib 的 `JSONUtils` 把读出、修改、写回包成了一个对象，省掉了自己处理文件不存在与异常的那几行。依赖库的接入与它的用法见 [C18-S4]。

## 完整的 common 读写

`src/data/scripts/gearin/campaign/GearInCommonStats.java`：

```java
package data.scripts.gearin.campaign;

import org.json.JSONObject;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;

/** 跨存档的战绩：所有存档里累计击败的齿轮工业巡逻队，保存在 common 目录的一个 JSON 文件里。 */
public final class GearInCommonStats {

    public static final String FILE = "GearIndustry_stats.json";

    private static final String DEFEATED = "patrolsDefeated";

    private GearInCommonStats() {
    }

    /** 读取累计数，文件不存在或损坏时返回 0。 */
    public static int getPatrolsDefeated() {
        try {
            return readStats().optInt(DEFEATED, 0);
        } catch (Exception e) {
            Global.getLogger(GearInCommonStats.class).warn("读取 " + FILE + " 失败", e);
            return 0;
        }
    }

    /** 读出、加上本次的数量、整份写回。 */
    public static void addPatrolsDefeated(int count) {
        try {
            JSONObject stats = readStats();
            stats.put(DEFEATED, stats.optInt(DEFEATED, 0) + count);
            Global.getSettings().writeTextFileToCommon(FILE, stats.toString(4));
        } catch (Exception e) {
            Global.getLogger(GearInCommonStats.class).warn("写入 " + FILE + " 失败", e);
        }
    }

    private static JSONObject readStats() throws Exception {
        SettingsAPI settings = Global.getSettings();
        if (!settings.fileExistsInCommon(FILE)) {
            return new JSONObject();
        }
        return new JSONObject(settings.readTextFileFromCommon(FILE));
    }
}
```

### 进游戏核对

执行 `gradle jar`，重启游戏。

- 在任意一份存档中击败一支齿轮工业的巡逻队，游戏目录的 `saves/common/` 下出现 `GearIndustry_stats.json.data`，内容中 `patrolsDefeated` 为 1；
- 打开巡逻情报，详情末尾显示累计折损 1 支；
- **新建另一份存档**，接触齿轮工业后打开巡逻情报，累计数仍是 1；
- 把文件内容改成乱码再打开情报，累计数一行不显示，日志中出现读取失败的警告，游戏照常运行。

页面中的现象依据 API 推出，编者尚未逐项在游戏内实测。

## 常见问题

- **`saves/common/` 下没有文件**：监听器里没有调用写入，或者写入时抛出了异常。
    - **解决方案**：核对 [C14-S2] 中的调用，在日志里搜索 `GearIndustry_stats.json`。

- **数字只增不减，想清零**：common 文件不随存档删除。
    - **解决方案**：删除 `saves/common/GearIndustry_stats.json.data`，或调用 `deleteTextFileFromCommon(...)`。

- **改坏文件之后游戏崩溃**：读取时没有捕获异常。
    - **解决方案**：读写都包在 `try` 里，失败时返回默认值。

## 建议参考项

- `com.fs.starfarer.api.SettingsAPI`：common 目录的读写方法与 1MB 上限的注释。
- `org.json.JSONObject`：`optInt(...)` 与 `toString(int)`。
