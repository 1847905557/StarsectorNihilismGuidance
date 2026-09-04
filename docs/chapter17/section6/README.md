# 让 GraphicsLib 管理纹理资源

舰船灯光已经接通，但材质、法线和表面贴图的取得时机仍会改变首帧表现。本节把纹理登记、实际加载、法线生成和预载开关分开，沿用 [C17-S5] 的报告器观察每种选择的结果。

*本节预期阅读时间：~14 分钟。*

*本节预期操作时间：~20 分钟。*

## 学习目标

完成后，您将能够：

- 区分 `TextureData.getTextureData()` 与带自动生成参数的查询；
- 选择 `mapSpriteToMNSWithAutoGen()` 或 `autoGenNormalMap()` 的调用位置；
- 判断 `preloadAllMaps` 对首次使用和战斗驻留的影响；
- 用 [C17-S5] 的报告类核对 `TextureEntry` 是否真正可用。

## 前置知识

- [C17-S5] 已完成 GraphicsLib 初始化和舰船灯光接线；
- 了解舰船的 material、normal 和 surface 贴图用途；
- 能够读取 `starsector.log` 中的纹理查询结果。

## 先把三种贴图需求分开

灯光 shader 读取的不是同一个纹理对象。material 决定受光颜色，normal 决定法线方向，surface 提供额外表面信息；先确认缺的是哪一类，才能选择查询或生成路径。

### 纹理资源选择速查表

| 需求 | 入口 | 结果 |
|---|---|---|
| 只查询已登记条目 | `getTextureData()` | 不主动生成缺失的法线贴图 |
| 查询并允许自动补全 | `getTextureDataWithAutoGen()` | 按参数尝试取得或生成目标条目 |
| 从原始精灵建立三图关系 | `mapSpriteToMNSWithAutoGen()` | 登记 material、normal 和 surface 的映射 |
| 只生成法线贴图 | `autoGenNormalMap()` | 返回生成后的法线精灵 |

## 查询已经登记的条目

**插入位置：`GearInLightReporter.describeTexture()`。** 先使用普通查询确认登记表中是否已有条目。

```java
TextureEntry normal = TextureData.getTextureData(
        hullId, TextureDataType.NORMAL_MAP, ObjectType.SHIP, 0);
```

完成信号：日志能区分 `normal=none` 和拥有有效纹理 ID 的条目。

## 允许查询路径自动补全

**插入位置：需要兼容缺少 normal map 的消费入口。** 把宿主对象和 `unloadedOk` 一并交给带自动生成参数的查询。

```java
TextureEntry normal = TextureData.getTextureDataWithAutoGen(
        hullId, TextureDataType.NORMAL_MAP, ObjectType.SHIP, 0,
        ship, true);
```

完成信号：原始舰船没有独立 normal map 时，查询仍能得到可供 shader 使用的条目；`unloadedOk` 为 true 时缺失资源返回空条目 (`sprite == null`) 而非 `null`。

## 从精灵登记材质关系

**插入位置：MOD 的应用加载期资源准备入口。** 当同一舰船需要从原始精灵建立三图关系时，使用统一映射入口。

```java
TextureData.mapSpriteToMNSWithAutoGen(
        hullId, spriteName, ObjectType.SHIP, 0, true, false);
```

完成信号：后续 material、normal 和 surface 查询使用同一个 hull ID，并各自返回对应条目。

## 单独生成缺失的法线贴图

**插入位置：确认只缺 normal map 的资源分支。** 不重新登记已有 material 和 surface，只生成缺失的法线对象。

```java
SpriteAPI normalSprite = TextureData.autoGenNormalMap(
        spriteName, hullId, ObjectType.SHIP, 0);
```

完成信号：生成结果可以直接用于 shader 并改变舰船受光方向 (`autoGenNormalMap` 不登记条目，无法经 `TextureEntry` 查询)。

## 选择战斗前预载还是首次使用加载

**插入位置：GraphicsLib 的 `GRAPHICS_OPTIONS.ini`。** `preloadAllMaps` 决定已登记贴图是否在战斗前集中加载。

```json
{
  "preloadAllMaps": false
}
```

实际文件是带 `#` 注释的 JSON 格式 (如 `"preloadAllMaps": false, # 默认: false`)，不是 `key=value` 的 INI。

保持关闭时，首个使用点可能承担加载成本；打开时，GraphicsLib 会在预载路径集中处理已启用的贴图，代价是更早占用显存。

完成信号：切换设置后，首个使用点的日志和首帧耗时发生可观察变化，纹理条目仍能被 [C17-S5] 报告器查询。

## 排查关键故障

下面是**局部故障片段**：

```java
TextureEntry normal = TextureData.getTextureData(
        hullId, TextureDataType.NORMAL_MAP, ObjectType.SHIP, 0);
```

实际症状：舰船有 material 贴图，灯光却没有法线方向变化。

原因：普通查询只读取已登记条目，不会替缺失的 normal map 自动生成资源。

下面是**局部修正片段**：

```java
TextureEntry normal = TextureData.getTextureDataWithAutoGen(
        hullId, TextureDataType.NORMAL_MAP, ObjectType.SHIP, 0,
        ship, true);
```

复验：重新进入战斗，核对 `normal` 条目和舰船受光方向；如果条目为空 (`sprite == null`)，检查原始 sprite 路径和自动生成开关。

## 验证方案

1. 保持 [C17-S5] 的 `GearInLightReporter`，记录同一 hull ID 的 material、normal 和 surface 查询结果。
2. 对完整三图舰船使用普通查询，确认三类条目都可取得。
3. 对缺少 normal map 的舰船先使用普通查询，再改用自动生成查询，比较返回值和首帧日志。
4. 切换 `preloadAllMaps`，分别在战斗前和首次使用时记录纹理加载日志与首帧耗时。
5. 战斗结束后再次读取条目，确认预载策略没有让查询关系丢失。

预期结果：

- 普通查询只报告已登记的条目；
- 自动生成查询能够处理允许补全的缺失 normal map；
- 三图映射使用同一 hull ID；
- 预载开关改变加载时机，不改变条目之间的映射关系。

## 故障分析

- **`normal` 始终为 `null`**：查询使用了普通入口，且资源没有登记 normal map。

    - **解决方案**：改用自动生成查询或先建立三图映射。

- **首帧出现明显卡顿**：贴图在战斗首次使用时才加载。

    - **解决方案**：启用预载。

- **自动生成后灯光仍无方向变化**：生成的法线条目没有使用与舰船相同的 hull ID。

    - **解决方案**：核对映射键和 `ObjectType.SHIP`。

## 总结

纹理问题先判断条目是否登记，再选择普通查询、自动补全、三图映射或单独生成。`preloadAllMaps` 只改变加载时机和显存占用，不能替代资源登记；[C17-S5] 的报告日志负责确认最终消费侧拿到的条目。

## 建议参考项

- `TextureData.getTextureData`：查询已登记条目；
- `TextureData.getTextureDataWithAutoGen`：允许自动补全的查询；
- `TextureData.mapSpriteToMNSWithAutoGen`：建立三图映射；
- `GraphicsLibSettings.preloadAllMaps()`：读取预载开关。
