# 读取设置

将参数放入 `settings.json`，便能在不修改 Java 源码的情况下调整功能配置。本节写入自有设置键，在应用加载时读取字符串、数值、开关与颜色，再通过一次音量修改解释设置缓存。文件中的值、内存对象与 getter 的结果将在这个实验中分别呈现。

_本节预期阅读时间：\~15 分钟。_

_本节预期操作时间：\~25 分钟。_

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

## 写入功能配置

打开 MOD 的 `data/config/settings.json`，保留已有的 `plugins` 等内容，加入下面的自有键：

```json
{
    "GearIn_loop_volume": 0.8,
    "GearIn_service_name": "齿轮工业服务",
    "GearIn_feature_enabled": true,
    "GearIn_example_count": 3.75,
    "GearIn_accent_color": [180, 120, 60, 255]
}
```

这些键采用 `GearIn_` 前缀，合并时与原版和其他功能区分。游戏将同路径配置按对象键合并，Java 代码随后通过 `Global.getSettings()` 读取结果。本例中的音量、名称和开关都由功能代码使用。

启动器的窗口、开发模式与进程配置有各自的读取入口。即使把同名键写进 MOD 的文件，也要确认使用方确实从游戏设置服务读取它。这里配置的是我们自己的功能参数。

## 在应用加载时取得参数

在 `GearInModPlugin.onApplicationLoad()` 中取得设置服务，按配置值的类型选择 getter。音量读为 `float`，名称读为字符串，开关读为布尔值：

```java
com.fs.starfarer.api.SettingsAPI settings = Global.getSettings();
float volume = settings.getFloat("GearIn_loop_volume");
String serviceName = settings.getString("GearIn_service_name");
boolean enabled = settings.optBoolean("GearIn_feature_enabled", false);
```

按本例配置，结果是 `0.8`、`齿轮工业服务` 与 `true`。`getFloat` 和 `getString` 要求存在能按对应类型读取的值；可选开关使用 `optBoolean`，没有有效布尔值时采用给定的 `false`。

**必填值缺失时暴露配置错误，可选值缺失时采用默认行为。** 选择 getter 时就确定了这个区别。把音量这样的必填参数也一律读成默认值，会掩盖键名或合并问题，功能却仍可能带着错误参数运行。

### 设置读取方法速查表

| 方法            | 当前用途与异常／缺省                               |
| --------------- | -------------------------------------------------- |
| getString       | 字符串，缺失或无法按要求读取时抛 RuntimeException  |
| getFloat        | 浮点数，缺失或转换失败时抛 RuntimeException        |
| getInt          | 按浮点读取再截断，3.75 返回 3                      |
| getBoolean      | 布尔值，缺失或转换失败时抛 RuntimeException        |
| optBoolean      | 读取不到有效布尔时采用给定缺省值                   |
| getColor        | RGBA，缺键采用并缓存 Color.white，格式错误产生异常 |
| getSettingsJSON | 当前合并对象，供直接查询与修改                     |

完整方法还读取 `GearIn_example_count` 与 `GearIn_accent_color`。`getInt` 将 `3.75` 截断为 `3`；需要保留小数时使用 `getFloat`，需要四舍五入时另行处理。颜色使用四项 RGBA，格式错误会产生异常，缺键则采用并缓存 `Color.white`。

## 修改了设置，为什么仍读到旧值

`getSettingsJSON()` 返回当前合并后的内存对象，可以直接修改其中的键。然而 `getFloat`、`getBoolean` 与 `getInt` 使用进程缓存，再次读取时未必立即使用刚写入的值。

用音量做一次局部实验：保存原值，将内存对象中的音量改为 `0.25`，读取一次，再调用 `resetCached()` 并重新读取。最后恢复原值与缓存：

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

原音量为 `0.8`。JSON 对象改为 `0.25` 后，第一次 getter 仍读到缓存中的 `0.8`；刷新后才得到 `0.25`。恢复对象并再次刷新，结果回到 `0.8`。**内存对象已经改变与 getter 已经采用新值，是两个不同的时机。**

::: warning 颜色缓存的刷新范围

`getColor` 使用独立缓存，`resetCached()` 不会清除它。上面的音量刷新流程不能直接用于颜色，颜色需要随设置重载刷新。

:::

`setFloat` 与 `setBoolean` 同样修改运行中的设置。它们也不会将结果写回 MOD 的资源文件；重启之后，文件仍提供原来的配置。

固定参数保存到配置文件，需要随对象延续的运行状态则由对应对象保存。跨启动公共数据使用 [C17-S4] 的 common 入口。这里的缓存实验只修改当前进程中的设置，结束后恢复原值。

## 汇总读取与缓存实验

将下面的局部方法加入 `GearInModPlugin`，在 `onApplicationLoad()` 调用 `logSettings()`。它在同一次应用加载中记录参数与缓存结果，结束前恢复音量：

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

方法保留 `JSONException` 声明，调用它的 `onApplicationLoad()` 沿用接口允许的 `throws Exception`。日志器来自入口类，输出中的 `volume` 是原音量，`cached` 是修改后尚未刷新的结果，`refreshed` 是刷新后的结果。整数、颜色与开关也保留在同一条记录中。

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

配置按键合并后，由功能代码使用类型 getter 取得。必填参数、可选开关、整数与颜色各有读取方式，选择方法也就选择了缺失或错误值怎样表现。

音量实验还说明了文件、内存对象与缓存的关系：修改运行对象后要处理对应缓存，跨启动保留结果则需要真正的写入入口。一次 getter 返回旧值，原因并不总在配置文件里。

## 建议参考项

- `com.fs.starfarer.api.SettingsAPI`：类型 getter、getSettingsJSON、setFloat、setBoolean 和 resetCached。
- `data/config/settings.json`：自有键和对象合并输入。
