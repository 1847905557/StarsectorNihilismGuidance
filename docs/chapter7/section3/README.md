# 读取设置

本节处理 `data/config/settings.json` 的读取，从 `Global.getSettings()` 取得 `SettingsAPI`，按键读取字符串、数值、布尔值和颜色，并观察缺失键、类型不符与缓存造成的结果。

*本节预期阅读时间：~15 分钟。*

*本节预期操作时间：~25 分钟。*

## 学习目标

完成后，您将能够：

- 使用 `Global.getSettings().getSettingsJSON()` 观察合并后的 settings 对象；
- 使用 `getString`、`getFloat`、`getInt`、`getBoolean`、`getColor` 和 `optBoolean` 读取设置；
- 解释 settings.json 的对象键合并和启动器关键键边界；
- 解释 `getInt` 的截断、缺失键异常和可选键缺省值；
- 识别 settings 缓存、内存修改与存档持久化之间的区别。

## 前置知识

- [C6-S1] 的 Java 工具链、入口类和 `mod_info.json` 结构；
- [C7-S1] 的生命周期回调；
- [C7-S2] 的 `Global.getSettings()` 和日志入口；
- [C1-S1] 的 MOD 数据文件编码要求。

## 读取设置数据

`data/config/settings.json` 是设置读取入口。原版与已启用 MOD 的对象键合并后，`SettingsAPI` 从合并对象读取键值。MOD 只提交自己的键；启动器管理的窗口、开发模式和内存上限等关键键由启动器处理，MOD 写入它们不会改变启动器行为。

```json
{
  "GearIn_loop_volume": 0.8,
  "GearIn_service_name": "齿轮工业服务",
  "GearIn_feature_enabled": true
}
```

### 读取设置值速查表

| 方法 | 类型 | 缺失或类型不符 |
|---|---|---|
| `getString(String key)` | 字符串 | 抛 `RuntimeException` |
| `getFloat(String key)` | 浮点数 | 抛 `RuntimeException` |
| `getInt(String key)` | 整数 | 先按浮点读取，再截断小数；缺失抛 `RuntimeException` |
| `getBoolean(String key)` | 布尔值 | 抛 `RuntimeException` |
| `getColor(String key)` | 4 元素 RGBA 数组 | 键缺失返回并缓存 `Color.white`；类型不符时抛 `RuntimeException` |
| `optBoolean(String key, boolean fallback)` | 布尔值 | 返回传入的缺省值 |
| `getSettingsJSON()` | 合并后的 JSON 对象 | 直接访问对象；类型由调用侧判断 |

### 刷新设置缓存

`getFloat`、`getBoolean`、`getInt` 使用进程级缓存，修改设置对象后需调用 `resetCached()`。`getColor` 的缓存不被 `resetCached()` 清除，只能重载设置刷新。

`setFloat` 和 `setBoolean` 只修改内存中的 settings 对象，不会写回 MOD 目录，也不会形成存档持久化。需要保存跨启动数据时，使用 Chapter 16 的 common 写入入口。

## 接入设置读取

设置已经在资源加载期合并完成，因此把读取放进 `GearInModPlugin.onApplicationLoad()`。以下片段依次增加严格读取、可选开关和缓存复验；每次只加入当前片段，看到对应日志后再继续。

### 读取必填设置

```java
// 插入位置：GearInModPlugin.onApplicationLoad() 的版本检查之后
com.fs.starfarer.api.SettingsAPI settings = Global.getSettings();
float volume = settings.getFloat("GearIn_loop_volume");
String serviceName = settings.getString("GearIn_service_name");
Global.getLogger(GearInModPlugin.class)
        .info("GearIn settings required: name=" + serviceName + ", volume=" + volume);
```

新增行为：启动时同时验证字符串与浮点键。完成信号：日志出现 `name=齿轮工业服务, volume=0.8`；任一键缺失或类型错误时，启动在对应 getter 处中止并留下堆栈。

### 接受可选开关缺席

```java
// 插入位置：紧接必填设置日志之后
boolean enabled = settings.optBoolean("GearIn_feature_enabled", false);
Global.getLogger(GearInModPlugin.class)
        .info("GearIn settings optional: enabled=" + enabled);
```

新增行为：配置未声明功能开关时使用关闭状态。完成信号：保留该键时日志为 `enabled=true`；临时删除该键并重启后日志为 `enabled=false`，启动继续完成。

### 观察并恢复缓存

```java
// 插入位置：onApplicationLoad() 末尾；仅用于本节缓存实验，复验后删除
org.json.JSONObject json = settings.getSettingsJSON();
float original = settings.getFloat("GearIn_loop_volume");
json.put("GearIn_loop_volume", 0.25f);
float cached = settings.getFloat("GearIn_loop_volume");
settings.resetCached();
float refreshed = settings.getFloat("GearIn_loop_volume");
json.put("GearIn_loop_volume", original);
settings.resetCached();
Global.getLogger(GearInModPlugin.class).info(
        "GearIn settings cache: cached=" + cached + ", refreshed=" + refreshed);
```

新增行为：同一次启动内证明 JSON 对象与 getter 缓存是两层状态，并在日志前恢复原值。完成信号：日志显示 `cached=0.8, refreshed=0.25`；随后再次读取 `GearIn_loop_volume` 得到 `0.8`。

### 汇总读取三个设置键

在 `data/config/settings.json` 添加三个带 MOD 前缀的键，在入口类中读取：

```java
com.fs.starfarer.api.SettingsAPI settings = Global.getSettings();
float volume = settings.getFloat("GearIn_loop_volume");
String serviceName = settings.getString("GearIn_service_name");
boolean enabled = settings.optBoolean("GearIn_feature_enabled", false);
Global.getLogger(GearInModPlugin.class)
        .info("GearIn settings: " + serviceName + ", volume=" + volume + ", enabled=" + enabled);
```

读取代码只消费设置值。设置文件的文本内容和结构属于资源输入，不在本节运行期写回。

## 排查关键故障

直接改动 `getSettingsJSON()` 后继续读取带缓存的 getter。

**局部故障片段：**

```java
// 故障片段
float before = settings.getFloat("GearIn_loop_volume");
settings.getSettingsJSON().put("GearIn_loop_volume", 0.25f);
float stale = settings.getFloat("GearIn_loop_volume");
```

**局部修正片段：**

```java
// 修正片段
float before = settings.getFloat("GearIn_loop_volume");
settings.getSettingsJSON().put("GearIn_loop_volume", 0.25f);
settings.resetCached();
float refreshed = settings.getFloat("GearIn_loop_volume");
```

复验：重启游戏并同时观察 JSON 键和日志中的读取值。

## 验证方案

前置数据：`gear_in` MOD、`settings.json` 中的三个设置键和入口类读取片段已就绪。

1. 启动游戏，进入入口类读取方法并观察 `starsector.log`。
2. 读取 `GearIn_loop_volume`，观察日志中的浮点值。
3. 读取 `GearIn_service_name`，观察日志中的字符串。
4. 删除 `GearIn_feature_enabled` 后重新启动，观察 `optBoolean` 返回的缺省值。
5. 把 `GearIn_loop_volume` 改为字符串后重新启动，观察取值调用点的异常。

预期结果：

- 日志显示 `volume=0.8`、`serviceName=齿轮工业服务` 和 `enabled=true`；
- 设置键缺失时，`optBoolean` 返回调用方提供的缺省值；
- 类型不符时，严格读取方法在调用点抛出异常；
- settings 读取不会产生 common 文件或修改 MOD 资源文件。

## 故障分析

- **严格读取抛出 `RuntimeException`**：键不存在或 JSON 类型不符。

    - **解决方案**：核对 `settings.json` 的键名和类型，修正后重新载入配置。

- **`getInt` 的结果丢失小数**：该方法通过 `getFloat` 读取后执行整数截断。

    - **解决方案**：需要小数时改用 `getFloat`。

- **修改 JSON 后读取仍是旧值**：直接修改 `getSettingsJSON()` 返回对象却没有调用 `resetCached()`，缓存仍然有效。`getColor` 例外，其缓存只能重载设置刷新。

    - **解决方案**：调用 `resetCached()` 清理缓存后再读取。

- **设置在重启后消失**：`setFloat` 和 `setBoolean` 只修改内存。

    - **解决方案**：跨启动数据写入 common，重启后重新读取 common。

- **提交启动器关键键后数值没有变化**：这些键由启动器管理，MOD 中的同名键不属于可覆盖设置。

    - **解决方案**：改用启动器提供的入口或换一个自有键，重启。

## 总结

settings 读取有清晰的边界：对象键先合并，取值方法再按类型解析；严格方法在键缺失或类型不符时抛异常，可选方法返回调用方提供的缺省值。内存修改、缓存刷新和 common 持久化各自承担不同职责，不能混用。

## 建议参考项

- `com.fs.starfarer.api.SettingsAPI`：`getSettingsJSON`、`getString`、`getFloat`、`getInt`、`getBoolean`、`getColor`、`optBoolean`、`resetCached`；
- `data/config/settings.json`：设置键和值的来源；
