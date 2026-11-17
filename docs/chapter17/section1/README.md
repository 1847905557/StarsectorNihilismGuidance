# 读取 MOD 内数据文件

并非所有数字都要请出代码：只归单个功能内部使用的算法常数和逻辑阈值，直接留在源码常量里由编译器检查即可，非必要绝不拆成外部文件；只有确实打算留给集中调参或允许后续覆写的配置项，才需要随包放进只读数据文件。`SettingsAPI` 提供了读取包内 JSON、CSV 与纯文本的入口：不带 `modId` 时按模组加载顺序搜索合并，带上 `modId` 则精确锁定自家目录下那一份私有配置，避免被同名文件意外覆盖。

---

_本节预期阅读时间：\~10 分钟。_

_本节预期操作时间：\~10 分钟。_

## 前置知识

- [C7-S2] 的设置文件与 `onApplicationLoad()` 中的集中读取；
- [C11-S1] 与 [C11-S2] 巡逻管理器中的点数与军官等级；
- JSON 的基本写法与 [C1-S1] 提到的宽松 JSON。

## 一个自己的配置文件

新建 `data/config/gearin/patrol.json`：

```json
{
	# 巡逻舰队的点数，决定工厂抽出多少艘船
	"combatPoints":30,

	# 指挥官与僚舰军官的等级
	"commanderLevel":4,
	"officerLevel":2,
}
```

文件可以照原版数据文件的写法加 `#` 注释和尾逗号，游戏读取 MOD 内的 JSON 时都按宽松格式解析。

放在 `data/config/gearin/` 下，是为了与原版和别的 MOD 的配置分开。路径就是这个文件的身份，带上 `gearin` 这一层，别人很难恰好放出一个同名的文件。

## 用 SettingsAPI 读取

读取 MOD 内的文件都走 `Global.getSettings()`：

| 方法 | 返回 |
| ---- | ---- |
| `loadJSON(path, modId)` | `JSONObject` |
| `loadCSV(path, modId)` | `JSONArray`，每一行是一个以列名为键的 `JSONObject` |
| `loadText(path, modId)` | 整个文件的文字 |

三个方法都有不带 `modId` 的版本：不传 `modId` 时会按加载顺序在基础游戏与各启用 MOD 间查找覆盖，适合允许他人改写的公共配置；带上 `modId`，则固定读取指定 MOD 目录里的那一份文件，防止冲突。路径一律从 `data/` 起写并以 `/` 分隔，不要用 `\`，以免路径校验失败或跨平台不兼容。

配置类在启动时读一次：

```java
JSONObject json = Global.getSettings().loadJSON(PATH, MOD_ID);
combatPoints = (float) json.getDouble("combatPoints");
commanderLevel = json.getInt("commanderLevel");
officerLevel = json.getInt("officerLevel");
```

`MOD_ID` 是 `mod_info.json` 里的 `GearIndustry`。`getDouble(...)`、`getInt(...)` 在键不存在时抛出异常，配置文件随 MOD 一起发布，缺了键说明文件被改坏了，这时启动失败比悄悄用一个默认值更容易发现问题，这与 [C7-S2] 对必填设置的处理相同。

在 `GearInModPlugin.onApplicationLoad()` 中，紧接在设置之后加入 (`BaseModPlugin.onApplicationLoad()` 本身声明了 `throws Exception`，可以直接向上抛出校验异常)：

```java
GearInPatrolConfig.load();
```

## 管理器与编制改读配置

[C11-S1] 的巡逻管理器 `GearInPatrolFleetManager` 把 `COMBAT_POINTS` 常量换成配置类的读取：

```java
FleetParamsV3 params = new FleetParamsV3(
        null,
        source.getLocationInHyperspace(),
        GearInModPlugin.FACTION_ID,
        1f,
        FleetTypes.PATROL_SMALL,
        GearInPatrolConfig.getCombatPoints(),
        0f, 0f, 0f, 0f, 0f,
        0f);
```

[C11-S2] 的 `GearInPatrolRoster` 里的指挥官与军官等级同样改读 `getCommanderLevel()` 与 `getOfficerLevel()`。

这些参数都在每次生成舰队时读取，因此改完配置重启游戏，下一支新生成的巡逻队就会按新数值出炉，旧存档也生效。而在 [C11-S1] 管理器构造时传给父类的舰队数量与重生天数则不同：它们在创建管理器时写进对象并随存档序列化，改配置管不到已经存在的管理器。**一个参数在什么时候被读取，决定改了它之后什么时候生效。**

## CSV 与纯文本

一份配置有很多行、每行格式相同时，用 CSV 更合适。`loadCSV(...)` 返回的每一行以列名为键：

```java
JSONArray rows = Global.getSettings().loadCSV("data/config/gearin/rumors.csv", MOD_ID);
for (int i = 0; i < rows.length(); i++) {
    JSONObject row = rows.getJSONObject(i);
    String text = row.getString("text");
}
```

CSV 的单元格读出来都是字符串，需要数字时自己转换。`loadText(...)` 则原样返回整个文件，适合读一段长说明或者自定义格式。

## 完整的配置类

`src/data/scripts/gearin/campaign/GearInPatrolConfig.java`：

```java
package data.scripts.gearin.campaign;

import org.json.JSONObject;

import com.fs.starfarer.api.Global;

/** 巡逻队的配置表：启动时从 MOD 自己的 data/config/gearin/patrol.json 读一次，巡逻管理器生成舰队时取用。 */
public final class GearInPatrolConfig {

    public static final String MOD_ID = "GearIndustry";

    public static final String PATH = "data/config/gearin/patrol.json";

    private static float combatPoints;
    private static int commanderLevel;
    private static int officerLevel;

    private GearInPatrolConfig() {
    }

    public static void load() throws Exception {
        JSONObject json = Global.getSettings().loadJSON(PATH, MOD_ID);
        combatPoints = (float) json.getDouble("combatPoints");
        commanderLevel = json.getInt("commanderLevel");
        officerLevel = json.getInt("officerLevel");
    }

    public static float getCombatPoints() {
        return combatPoints;
    }

    public static int getCommanderLevel() {
        return commanderLevel;
    }

    public static int getOfficerLevel() {
        return officerLevel;
    }
}
```

### 进游戏核对

执行 `gradle jar`，重启游戏，载入一份有巡逻队的存档。

- 把 `combatPoints` 改为 60，重启，等一支新的巡逻队生成，它的规模明显大于之前；
- 把 `commanderLevel` 改为 6，新巡逻队的指挥官等级随之提高；
- 删掉 `officerLevel` 这一行再启动，游戏在加载阶段报错，日志中指出找不到 `officerLevel` 这个键。

## 常见问题

- **启动时报找不到文件**：路径写错，或者用了 `\`。
    - **解决方案**：路径从 `data/` 起写，用 `/` 分隔。

- **改了配置，巡逻队没有变化**：改的是舰队数量或重生天数，它们在管理器创建时就定下了；或者还没有新的巡逻队生成。
    - **解决方案**：只有生成时读取的参数会立即生效；舰队数量的改动需要新档。

- **启动时报 JSON 解析错误**：注释写在了字符串里，或者少了逗号、冒号。
    - **解决方案**：对照报错的行号检查。

## 建议参考项

- `com.fs.starfarer.api.SettingsAPI`：`loadJSON(...)`、`loadCSV(...)`、`loadText(...)` 的各个重载与路径格式要求。
- `org.json.JSONObject`：`getInt(...)`、`getDouble(...)` 与带默认值的 `opt...` 方法。
