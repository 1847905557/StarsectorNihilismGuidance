# 使用 LazyLib 保存公共 JSON

直接调用原版的 `common` 文件接口时，每次读档与写回都得自己守好建档和异常处理；换用 LazyLib 的 `JSONUtils.loadCommonJSON(...)` 后，这一套建档、解析和离开 `try-with-resources` 时的自动写回都交给了工具类接管。

只是这份省心有个前提必须事先留心：一旦玩家把磁盘上的旧 JSON 改坏了格式，它在回落加载默认模板之后会把原先那份一并覆盖掉。

---

_本节预期阅读时间：\~15 分钟。_

_本节预期操作时间：\~20 分钟。_

## 前置知识

- [C7-S1] 的生涯加载回调与 `PersistentData`；
- [C10-S2] 的 `MemoryAPI` 与生涯作用域；
- [C17-S2] 的原版 common 文件路径和读写；
- [C18-S1] 的 LazyLib 运行时依赖与编译 classpath；
- 能够读取 JSON 对象中的字符串和整数。

## 准备默认 JSON

在 `data/config/GearIn_common_defaults.json` 中写入初始字段：

```json
{
    "load_count": 0,
    "last_player": ""
}
```

`load_count` 保存累计成功记录的加载次数，`last_player` 保存最近一次角色名。默认资源随 MOD 发布，运行时公共文件则由玩家的实际使用过程产生；两者使用相同字段，使首次加载和后续读取进入同一套代码。

## 确定文件的共享范围

Memory 随提供它的生涯对象使用和保存，Sector PersistentData 属于当前生涯；**common 属于用户数据空间，跨生涯共享。**存档目录由启动配置决定，标准安装的默认位置是 `Starsector/saves/common`。换用另一份存档仍读取同名公共文件，加载次数和最近角色名由此延续。

本节使用的逻辑文件名是 `GearIn_common_counter`。游戏为它追加 `.data` 后缀，最终写入：

```text
Starsector/saves/common/GearIn_common_counter.data
```

逻辑文件名应只包含当前 MOD 能稳定控制的名称。父目录跳转、绝对路径、反斜杠和驱动器路径会被 common 文件接口拒绝；传入 `GearIn_common_counter.json` 也不会得到普通 JSON 文件，而会生成 `GearIn_common_counter.json.data`。

common 文本单次最多写入 1,048,576 个 Java 字符。这个容量足够保存设置、计数器和事件状态，但公共文件并不适合承担日志归档或大规模对象快照。

## 从默认结构取得可修改对象

### 让默认资源接管首次加载

双参数重载先读取现有 common 文件。文件不存在、内容无法读取或 JSON 无法解析时，它改为加载默认资源，载入非空 JSON 后，包装对象在构造时保存一次；默认资源与已有文件都经过这一步：

```java
try (CommonDataJSONObject data = JSONUtils.loadCommonJSON(
        COMMON_FILE_NAME, DEFAULT_JSON_PATH)) {
    int previousLoadCount = data.optInt(LOAD_COUNT_KEY, 0);
    String previousPlayerName = data.optString(LAST_PLAYER_KEY, "");
}
```

`COMMON_FILE_NAME` 交给 common 文件接口，`DEFAULT_JSON_PATH` 交给 MOD 资源加载器。局部片段先取得旧次数和角色名，非空对象此时已保存一次，try-with-resources 结束时还会再次保存。这是读改写入口的实际行为，纯读取继续使用 [C17-S2] 的原版方法。

::: warning 默认资源也会接管解析失败的文件

现有公共文件无法读取或解析时，双参 `loadCommonJSON` 会采用默认资源，并将非空默认对象保存到同一个文件。原来的计数因此可能重新从 0 开始。

:::

对练习计数器而言，失败后重建初始状态可以接受；若保存的是无法重新取得的玩家记录，就应保留读取失败的处理机会，再决定如何处置原内容。默认模板方便建立文件，同时也决定了失败时采用什么数据。

单参数 `loadCommonJSON(filename)` 适合已经能够自行补齐字段的数据。读取失败时，它返回与该文件名关联的空 `CommonDataJSONObject`，不会寻找 MOD 内的默认模板。

### 修改原对象中的字段

读取旧值后，在同一个对象上更新需要改变的键：

```java
int currentLoadCount = previousLoadCount + 1;
String currentPlayerName = playerName;

data.put(LOAD_COUNT_KEY, currentLoadCount);
data.put(LAST_PLAYER_KEY, currentPlayerName);
```

`optInt(...)` 和 `optString(...)` 为缺失字段提供初始值，本节采用缺省 0 和空字符串初始化这两个字段。修改原对象还会保留当前代码并不认识的其他键，避免一次小改动顺手清空整份公共状态。

## 保存并关闭数据对象

`CommonDataJSONObject` 实现了 `AutoCloseable`。try-with-resources 离开作用域时调用 `close()`，而 `close()` 会调用 `save()`，因此载入非空数据后已保存原内容，正常修改路径在关闭时再保存更新：

```java
try (CommonDataJSONObject data = JSONUtils.loadCommonJSON(
        COMMON_FILE_NAME, DEFAULT_JSON_PATH)) {
    data.put(LOAD_COUNT_KEY, currentLoadCount);
    data.put(LAST_PLAYER_KEY, currentPlayerName);
}
```

`save()` 用于需要在作用域结束前立即写盘的场景。读改写在同一作用域完成时，载入非空对象、显式 `save()` 与关闭分别保存；省略显式调用，仍会保存原内容和关闭后的修改。

直接调用 `new CommonDataJSONObject(filename)` 得到与文件名关联的空对象，适合明确创建或整体覆盖。增量更新继续加载现有对象，才能保留原字段；新建对象后关闭，保存的便只有这个对象的内容。所谓 "重新整理结构"，如果只剩自己刚写的几个键，整理掉的还有用户原来的值。

## 接到生涯加载入口

保存本节的 `GearInCommonDataCounter` 到 `src/data/scripts/gearin/lib/GearInCommonDataCounter.java`。在既有 `GearInModPlugin.onGameLoad(...)` 追加局部调用，保留原初始化，并导入计数器及其嵌套类型 `GearInCommonDataCounter.LoadSnapshot`：

```java
@Override
public void onGameLoad(boolean newGame) {
    try {
        String playerName = Global.getSector().getPlayerPerson().getNameString();
        LoadSnapshot snapshot = GearInCommonDataCounter.recordLoad(playerName);
        String previousPlayer = snapshot.getPreviousPlayerName().isEmpty()
                ? "no record"
                : snapshot.getPreviousPlayerName();

        Global.getSector().getCampaignUI().addMessage(
                "Common loads: " + snapshot.getPreviousLoadCount()
                        + " -> " + snapshot.getCurrentLoadCount()
                        + "; current: " + snapshot.getCurrentPlayerName()
                        + "; previous: " + previousPlayer);
    } catch (Exception ex) {
        Global.getLogger(GearInModPlugin.class).error(
                "Failed to update GearIn common data", ex);
    }
}
```

`recordLoad(...)` 读回旧值、更新字段并完成关闭保存，返回的 `LoadSnapshot` 同时保留旧值与本次值，入口据此生成消息。第一次成功记录显示 `0 -> 1` 和 `previous: no record`；载入另一份生涯后，当前角色改变，旧角色与累计次数继续来自公共文件。

保存等操作抛出的异常由入口记录，当前处理块结束后，其余生涯初始化继续执行。LazyLib 已捕获并转向默认资源的读取错误，则按前面的初始结构处理。

## 重置公共状态

重置需要清除未知字段，再恢复默认结构。`JSONUtils.clear(...)` 原地移除 `JSONObject` 的全部键，随后重新写入两个默认值：

```java
public static void reset() throws Exception {
    try (CommonDataJSONObject data = JSONUtils.loadCommonJSON(
            COMMON_FILE_NAME, DEFAULT_JSON_PATH)) {
        JSONUtils.clear(data);
        data.put(LOAD_COUNT_KEY, 0);
        data.put(LAST_PLAYER_KEY, "");
    }
}
```

通过 Console Commands 执行以下命令，可以在不切换存档的情况下恢复公共计数器：

```text
runcode data.scripts.gearin.lib.GearInCommonDataCounter.reset();
```

命令结束时对象已经关闭并保存。下一次载入生涯时，旧次数为 0，本次次数变为 1，上次角色名为空。

## 读取颜色数组

`JSONUtils.toColor(...)` 把 JSON 整数数组转换为 `Color`。这个方法适合读取数据驱动的界面色或特效色，不要求把颜色拆成四个独立字段：

```java
Color opaque = JSONUtils.toColor(new JSONArray("[80, 200, 255]"));
Color translucent = JSONUtils.toColor(new JSONArray("[80, 200, 255, 160]"));
```

三个整数依次表示红、绿、蓝，alpha 默认为 255；数组恰有四个分量时采用第四个整数作为 alpha。少于三个分量时无法组成颜色，方法会抛出 `JSONException`。

## 常用方法速查表

| 方法                                    | 读取或写入结果                                        | 适用场景                       |
| --------------------------------------- | ----------------------------------------------------- | ------------------------------ |
| `loadCommonJSON(filename, defaultPath)` | 读取公共对象，失败时采用默认 JSON；非空对象构造时保存 | 拥有固定初始结构的数据         |
| `loadCommonJSON(filename)`              | 读取公共对象；非空对象构造时保存，失败时返回空对象    | 代码能够自行补齐全部字段的数据 |
| `new CommonDataJSONObject(filename)`    | 创建与文件名关联的空对象                              | 明确创建或覆盖全部内容         |
| `save()`                                | 立即把当前对象写入 common 文件                        | 作用域尚未结束但需要立即写盘   |
| `close()`                               | 调用 `save()` 并结束当前对象的使用                    | try-with-resources 自动保存    |
| `clear(object)`                         | 原地移除对象中的全部键                                | 重置公共状态                   |
| `toColor(array)`                        | 把 RGB 或 RGBA 整数数组转换为 `Color`                 | 数据驱动颜色                   |

## 完整最小例

默认 JSON 提供首次加载结构，静态工具类负责读取、更新、保存和重置同一个公共文件。

```json
{
    "load_count": 0,
    "last_player": ""
}
```

::: details 完整成品：GearInCommonDataCounter

```java
package data.scripts.gearin.lib;

import org.lazywizard.lazylib.JSONUtils;
import org.lazywizard.lazylib.JSONUtils.CommonDataJSONObject;

public final class GearInCommonDataCounter {
    private static final String COMMON_FILE_NAME = "GearIn_common_counter";
    private static final String DEFAULT_JSON_PATH =
            "data/config/GearIn_common_defaults.json";
    private static final String LOAD_COUNT_KEY = "load_count";
    private static final String LAST_PLAYER_KEY = "last_player";

    private GearInCommonDataCounter() {
    }

    public static LoadSnapshot recordLoad(String playerName) throws Exception {
        try (CommonDataJSONObject data = JSONUtils.loadCommonJSON(
                COMMON_FILE_NAME, DEFAULT_JSON_PATH)) {
            int previousLoadCount = data.optInt(LOAD_COUNT_KEY, 0);
            String previousPlayerName = data.optString(LAST_PLAYER_KEY, "");
            int currentLoadCount = previousLoadCount + 1;
            String currentPlayerName = playerName;

            data.put(LOAD_COUNT_KEY, currentLoadCount);
            data.put(LAST_PLAYER_KEY, currentPlayerName);

            return new LoadSnapshot(
                    previousLoadCount,
                    currentLoadCount,
                    previousPlayerName,
                    currentPlayerName);
        }
    }

    public static void reset() throws Exception {
        try (CommonDataJSONObject data = JSONUtils.loadCommonJSON(
                COMMON_FILE_NAME, DEFAULT_JSON_PATH)) {
            JSONUtils.clear(data);
            data.put(LOAD_COUNT_KEY, 0);
            data.put(LAST_PLAYER_KEY, "");
        }
    }

    public static final class LoadSnapshot {
        private final int previousLoadCount;
        private final int currentLoadCount;
        private final String previousPlayerName;
        private final String currentPlayerName;

        private LoadSnapshot(int previousLoadCount, int currentLoadCount,
                String previousPlayerName, String currentPlayerName) {
            this.previousLoadCount = previousLoadCount;
            this.currentLoadCount = currentLoadCount;
            this.previousPlayerName = previousPlayerName;
            this.currentPlayerName = currentPlayerName;
        }

        public int getPreviousLoadCount() {
            return previousLoadCount;
        }

        public int getCurrentLoadCount() {
            return currentLoadCount;
        }

        public String getPreviousPlayerName() {
            return previousPlayerName;
        }

        public String getCurrentPlayerName() {
            return currentPlayerName;
        }
    }
}
```

:::

### 进游戏核对

执行 `gradle jar` 确认默认 JSON 位于 `data/config/`，并准备两份角色名不同的生涯存档：

- 首次载入第一份存档时，从默认模板建立公共文件并显示 `0 -> 1`；同一存档再次载入，次数连续递增且上次与本次角色名一致；
- 切换第二份不同角色名的存档，累计次数继续延续，且 `previous` 展示上一份存档的角色名；
- 打开 `saves/common/GearIn_common_counter.data` 检查 `load_count` 与 `last_player` 为最新值；执行 `runcode data.scripts.gearin.lib.GearInCommonDataCounter.reset();` 后两字段重置为 `0` 与空串，下次加载从 `0 -> 1` 重新起算；
- 分别用 RGB、RGBA 与少于 3 分量的数组测试 `JSONUtils.toColor(...)`：前两者分别产出 `alpha = 255` 与指定 alpha，不足 3 分量抛出 `JSONException`。

## 常见问题

- **每次载入都显示 `0 -> 1`**：数据对象没有正常关闭，或每次调用使用了不同的公共文件名。
    - **解决方案**：使用固定的 `GearIn_common_counter`，并让 try-with-resources 完成 `close()` 与保存。

## 建议参考项

- `org.lazywizard.lazylib.JSONUtils`：加载、清空和转换公共 JSON 数据；
- `org.lazywizard.lazylib.JSONUtils.CommonDataJSONObject`：关联公共文件并在关闭时保存对象；
- `com.fs.starfarer.api.SettingsAPI`：LazyLib 公共 JSON 最终使用的 common 文件读写入口；
- `Starsector/saves/common`：运行时存档公共数据目录；
- `GearIndustry/data/config/GearIn_common_defaults.json`：公共计数器的首次加载结构。
