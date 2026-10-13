# 读取设置

设置文件提供功能代码使用的参数，本节从已合并的 settings.json 读取字符串、数值、布尔与颜色，再比较必填读取、可选值和缓存。读取放在应用加载回调，日志把输入和实际结果对应起来。

*本节预期阅读时间：\~15 分钟。*

*本节预期操作时间：\~25 分钟。*

## 学习目标

完成后，您将能够：

- 使用合并后的 settings 对象；
- 按键与类型读取自有配置；
- 区分整数截断、缺省值和缺键异常；
- 观察并刷新数值缓存；
- 区分内存设置与跨启动持久化。

## 前置知识

- [C6-S4] 的构建与加载；
- [C7-S1] 的应用加载回调；
- [C7-S2] 的 Global 设置服务；
- [C1-S1] 的同路径合并；
- Java 方法、JSON 对象与异常。

## 在 settings.json 定义自有键

在 MOD 的 `data/config/settings.json` 合入下面的键：

```json
{
    "GearIn_loop_volume": 0.8,
    "GearIn_service_name": "齿轮工业服务",
    "GearIn_feature_enabled": true,
    "GearIn_example_count": 3.75,
    "GearIn_accent_color": [180, 120, 60, 255]
}
```

保留已有 plugins 等对象，只添加这些自有内容。键名采用 GearIn_前缀，避免与原版或其他功能共用同一名字。当前这些值用于观察读取结果，功能代码按自己的用途消费它们。

**设置键的归属与实际读取位置要对应。** 原版和 MOD 配置按对象键合并，启动器读取的窗口、开发模式与进程配置有各自入口，当前 MOD 参数在游戏设置服务中使用。

## 按类型读取

在 GearInModPlugin.onApplicationLoad 中加入局部读取：

```java
com.fs.starfarer.api.SettingsAPI settings = Global.getSettings();
float volume = settings.getFloat("GearIn_loop_volume");
String serviceName = settings.getString("GearIn_service_name");
boolean enabled = settings.optBoolean("GearIn_feature_enabled", false);
```

getFloat 和 getString 要求能取得对应值，可选开关用 optBoolean 并提供 false 作为缺省。默认配置返回 0.8、齿轮工业服务与 true。

### 设置读取方法速查表

| 方法 | 当前用途与异常／缺省 |
|---|---|
| getString | 字符串，缺失或无法按要求读取时抛 RuntimeException |
| getFloat | 浮点数，缺失或转换失败时抛 RuntimeException |
| getInt | 按浮点读取再截断，3.75 返回 3 |
| getBoolean | 布尔值，缺失或转换失败时抛 RuntimeException |
| optBoolean | 读取不到有效布尔时采用给定缺省值 |
| getColor | RGBA，缺键采用并缓存 Color.white，格式错误产生异常 |
| getSettingsJSON | 当前合并对象，供直接查询与修改 |

完整方法也读取 example_count 和 accent_color。需要小数时使用 getFloat，getInt 的截断与四舍五入是不同操作；颜色保持四项 RGBA。

## 比较内存对象与缓存

getFloat、getBoolean 与 getInt 使用进程缓存。直接修改 getSettingsJSON 返回的对象后，旧缓存可能仍被读取，resetCached 用于刷新这些缓存。

下面的局部实验读取原音量，改变对象，再比较缓存和刷新值，最后恢复本键：

```java
org.json.JSONObject json = settings.getSettingsJSON();
float original = settings.getFloat("GearIn_loop_volume");
json.put("GearIn_loop_volume", 0.25f);
float cached = settings.getFloat("GearIn_loop_volume");
settings.resetCached();
float refreshed = settings.getFloat("GearIn_loop_volume");
json.put("GearIn_loop_volume", original);
settings.resetCached();
```

**JSON 对象和 getter 缓存分别观察。** 本例原值 0.8，刷新前仍读 0.8，刷新后为 0.25，恢复后再读原值。getColor 有独立缓存，resetCached 不会清除它，颜色要随设置重载刷新。

setFloat 和 setBoolean 同样操作内存设置；文件内容、运行缓存和存档是不同范围。需要保存跨启动数据时，通过 [C17-S4] 的 common 入口写入，再在相应阶段读取。

编者建议把明确固定的配置放在资源文件，运行状态按所属对象保存。为了调一个设置把资源、缓存与存档一起改，往往会把原本简单的问题变得难以判断。

## 用固定诊断方法核对参数

把下面的局部方法加入 GearInModPlugin 方法区，并在 onApplicationLoad 调用 logSettings：

```java
private void logSettings() throws org.json.JSONException {
    com.fs.starfarer.api.SettingsAPI settings = Global.getSettings();
    float volume = settings.getFloat("GearIn_loop_volume");
    String serviceName = settings.getString("GearIn_service_name");
    boolean enabled = settings.optBoolean("GearIn_feature_enabled", false);
    int count = settings.getInt("GearIn_example_count");
    java.awt.Color color = settings.getColor("GearIn_accent_color");
    org.json.JSONObject json = settings.getSettingsJSON();

    json.put("GearIn_loop_volume", 0.25f);
    float cached = settings.getFloat("GearIn_loop_volume");
    settings.resetCached();
    float refreshed = settings.getFloat("GearIn_loop_volume");
    json.put("GearIn_loop_volume", volume);
    settings.resetCached();

    Global.getLogger(GearInModPlugin.class).info(
            "GearIn settings: name=" + serviceName + ", volume=" + volume
            + ", enabled=" + enabled + ", count=" + count + ", color=" + color
            + ", cached=" + cached + ", refreshed=" + refreshed);
}
```

这段方法在同一次应用加载中记录必填值、可选值、整数、颜色和缓存差异，并在结束前恢复自有音量。日志器沿用入口类，当前方法用于本节参数核对。JSON 写入声明 JSONException，调用它的 onApplicationLoad 保留接口允许的 throws Exception。

## 验证方案

1. 保存自有设置键和读取方法，构建 JAR，重启游戏。
2. 搜索 GearIn settings，核对名称、音量、开关、整数和颜色。
3. 比较 cached 与 refreshed，再读取原键确认恢复值。
4. 将 feature_enabled 暂时省略，保存并重启，比较 optBoolean 的 false 缺省。
5. 恢复当前功能开关，核对 MOD 资源文件保持手动保存的内容。

预期结果：

- 默认名称与音量为齿轮工业服务和 0.8，开关为 true。
- example_count 截断为 3，颜色读取当前 RGBA。
- 缓存实验为 cached0.8、refreshed0.25，恢复后音量 0.8。
- 省略可选开关时返回 false，读取按给定缺省进行。
- 方法记录运行结果，内存设置修改按进程范围生效。

## 故障分析

- **严格读取抛 RuntimeException**：键未合入或值不能按所需类型读取。
    - **解决方案**：核对自有键、JSON 值与 getter，保存并重启。

- **整数读取丢失小数**：getInt 采用截断。
    - **解决方案**：需要小数时使用 getFloat。

- **对象修改后读取旧值**：对应 getter 缓存尚未刷新。
    - **解决方案**：对数值与布尔使用 resetCached，颜色通过设置重载核对。

- **重启后内存设置恢复文件值**：修改只作用于运行对象。
    - **解决方案**：固定配置保存到资源，需要跨启动运行数据时使用 common。

- **启动器配置未按 MOD 键变化**：实际读取位置属于启动器或进程参数。
    - **解决方案**：按该参数的实际入口设置，功能参数使用独立自有键。

## 总结

设置先按键合并，再由类型 getter 读取。必填值、可选缺省、整数截断和颜色有各自的读取条件，缓存则决定内存修改何时进入下一次结果。

配置文件、运行对象与持久数据分别服务不同范围。确认读取方、修改位置和刷新时机后，参数变化就能从日志和实际功能中核对。

## 建议参考项

- `com.fs.starfarer.api.SettingsAPI`：类型 getter、getSettingsJSON、setFloat、setBoolean 和 resetCached。
- `data/config/settings.json`：自有键和对象合并输入。
