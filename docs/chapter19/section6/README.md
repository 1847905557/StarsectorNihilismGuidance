# 用 BoxUtil 为舰船与弹体添加动态光照

BoxUtil 提供点、线、聚光、区域与无限光五种形态，它们的选择应由发光对象决定。本节用点光表现弹体、线光表现光束、聚光表现舰首，并检查实体材质是否已登记。

*本节预期阅读时间：~18 分钟。*

*本节预期操作时间：~30 分钟。*

## 学习目标

完成后，您将能够：

- 根据发光对象的几何选择点、线、聚光、区域或无限光；
- 为局部光源配置世界位置、颜色、强度、衰减半径与寿命；
- 为有方向的光源同步朝向，为无限光关闭距离衰减；
- 在创建照明前核对舰船、导弹、弹体和光束的注册数据；
- 从长度、锥角、材质集合和入队结果定位照明故障。

## 前置知识

- [C7-S1] 理解 Java 类、方法、字段与入口插件；
- [C8-S3] 理解每帧回调与暂停判断；[C8-S2] 理解战斗对象读取与对象边界；
- 能够用 Gradle 构建示例 MOD，并从 `starsector.log` 定位类名。

## 明确当前需求

弹体掠过船体时产生局部亮点，光束沿长度照亮装甲，舰首聚光随 facing 转动；关闭实体光照后，玩法状态保持不变。

## 文件与 API 链路

五类 Light 都继承 `BaseIlluminantData`，共享位置、颜色、强度、衰减和全局计时；`LinearLight`、`SpotLight` 与 `AreaLight` 再增加各自几何状态。`EntityShadingDataManager` 分别查询舰船、导弹材质集合，以及弹体、光束、引擎的发光注册。

### 光源选型速查表

| 发光对象 | 光源类型 | 关键几何参数 |
|---|---|---|
| 弹体 | `PointLight` | 各向发散，衰减半径 180 su |
| 光束 | `LinearLight` | 长度 240 su，柱半径 18 su |
| 舰首 | `SpotLight` | 锥角 18°，朝向随舰首同步 |
| 大面积面板 | `AreaLight` | 尺寸 180 × 70 su |
| 全局环境 | `InfiniteLight` | 低强度，关闭距离衰减 |

### 为弹体选择 PointLight

`GearInLightFactory.createFiveKinds()` 创建各向发散的短程光源，并写入通用局部光参数：

```java
PointLight projectileLight = new PointLight();
configure(projectileLight, location, new Color(90, 220, 255), 1.2f, 180f);
```

弹体附近船体得到局部照明。

### 为光束选择 LinearLight

`createFiveKinds()` 按光束长度和柱半径设置线形光源，再复用通用配置：

```java
LinearLight beamLight = new LinearLight();
beamLight.setLength(240f);
beamLight.setCylinderRadius(18f);
```

整段光束附近都有照明，而非只有起点。

### 为舰首选择 SpotLight

`createFiveKinds()` 接着设置锥角并同步舰首朝向：

```java
SpotLight spot = new SpotLight();
spot.setCone(18f);
spot.setFacingScale(facing, 1f, 1f);
```

舰首前方出现有边界的锥形照明。

### 按对象选择 Area 与 Infinite

`createFiveKinds()` 在返回前配置区域光覆盖面板；无限光使用低强度并关闭距离衰减，避免把局部参数误套到全局光：

```java
AreaLight area = new AreaLight();
area.setSize(180f, 70f);
InfiniteLight ambient = new InfiniteLight();
ambient.setStrength(0.12f);
ambient.setNoneAttenuation();
```

大面积对象不会被单点热点破坏。

### 在创建光源前确认照明数据

`GearInShadingProbe` 先按舰船与导弹 ID 查询材质集合，再按弹体或光束 ID 查询发光注册：

```java
boolean shipReady = EntityShadingDataManager.haveEntityTextureSet(hullId);
boolean missileReady = EntityShadingDataManager.haveMissileTextureSet(missileId);
boolean projectileReady = EntityShadingDataManager.haveProjectileIlluminantData(projectileId);
boolean beamReady = EntityShadingDataManager.haveBeamIlluminantData(weaponId);
```

缺少数据时，跳过光源并给出具体 ID。

## 完整最小例

工厂返回 5 个已经配置位置、颜色、强度和 1 秒寿命的 `BaseIlluminantData`；调用方逐项入队。探针只回答注册状态，不创建 GPU 对象。

::: details 完整成品：GearInLightFactory

```java
package data.scripts.gearin.lib;

import java.awt.Color;

import org.boxutil.base.BaseIlluminantData;
import org.boxutil.units.standard.light.AreaLight;
import org.boxutil.units.standard.light.InfiniteLight;
import org.boxutil.units.standard.light.LinearLight;
import org.boxutil.units.standard.light.PointLight;
import org.boxutil.units.standard.light.SpotLight;
import org.lwjgl.util.vector.Vector2f;

public final class GearInLightFactory {
    public BaseIlluminantData[] createFiveKinds(Vector2f location, float facing) {
        PointLight point = new PointLight();
        configure(point, location, new Color(90, 220, 255), 1.2f, 180f);
        AreaLight area = new AreaLight();
        area.setSize(180f, 70f);
        configure(area, location, new Color(80, 170, 255), 0.7f, 260f);
        LinearLight linear = new LinearLight();
        linear.setLength(240f);
        linear.setCylinderRadius(18f);
        configure(linear, location, new Color(100, 230, 255), 1f, 220f);
        SpotLight spot = new SpotLight();
        spot.setCone(18f);
        spot.setFacingScale(facing, 1f, 1f);
        configure(spot, location, new Color(180, 235, 255), 0.9f, 320f);
        InfiniteLight infinite = new InfiniteLight();
        infinite.setColor(new Color(90, 110, 150));
        infinite.setStrength(0.12f);
        infinite.setFacingScale(facing, 1f, 1f);
        infinite.setNoneAttenuation();
        infinite.setGlobalTimer(0.1f, 0.3f, 0.6f);
        return new BaseIlluminantData[] {point, linear, spot, area, infinite};
    }

    private void configure(BaseIlluminantData light, Vector2f location,
            Color color, float strength, float radius) {
        light.setLocation(location);
        light.setColor(color);
        light.setStrength(strength);
        light.setAttenuationRadius(radius);
        light.setGlobalTimer(0.1f, 0.3f, 0.6f);
    }
}
```

:::

::: details 完整成品：GearInShadingProbe

```java
package data.scripts.gearin.lib;

import org.boxutil.manager.EntityShadingDataManager;

public final class GearInShadingProbe {
    public boolean hasShipTextures(String hullId) {
        return EntityShadingDataManager.haveEntityTextureSet(hullId);
    }

    public boolean hasMissileTextures(String missileId) {
        return EntityShadingDataManager.haveMissileTextureSet(missileId);
    }

    public boolean hasProjectileLight(String projectileId) {
        return EntityShadingDataManager.haveProjectileIlluminantData(projectileId);
    }

    public boolean hasBeamLight(String weaponId) {
        return EntityShadingDataManager.haveBeamIlluminantData(weaponId);
    }
}
```

:::

## 验证方案

前置数据：BoxUtil 实体照明已启用，测试舰船和导弹已在 shading 数据表登记，战斗接线能逐项入队工厂结果。

1. 先调用探针，记录舰船材质、导弹材质、弹体发光与光束发光 4 个布尔结果。
2. 在同一命中点创建五类光源，核对返回数组长度为 5，局部光总寿命均为 1 秒。
3. 让弹体点光经过船体，观察 180 su 衰减范围；让 240 su 线光横跨大型舰。
4. 改变传入 `facing`，确认聚光和无限光方向同步变化。
5. 移除舰船 normal 材质注册并重启，比较光源存在时的船体响应。

预期结果：

- 探针分别反映 2 类材质集合和 2 类发光数据的真实注册状态；
- 点光、线光、聚光、区域光和无限光使用不同几何参数；
- 局部光具有明确衰减半径，无限光使用低强度且不随距离衰减；
- 聚光与无限光方向随传入朝向改变；
- 材质集合缺失时能区分 "光源未创建" 和 "表面没有材质响应"；
- 完整配置下战斗没有未处理异常。

## 故障分析

- **光束存在但船体没有沿线照明**：线光长度为零，照明体没有有效覆盖范围。
    - **解决方案**：设置非零长度并核对目标舰材质。

- **光源存在但船体没有材质响应**：`EntityShadingDataManager` 中没有目标纹理集合。
    - **解决方案**：注册材质数据。

- **聚光方向保持固定**：每帧没有同步宿主舰船的 facing。
    - **解决方案**：推进时更新方向。

- **无限光覆盖了全部颜色**：强度和衰减没有按场景限制。
    - **解决方案**：降低强度并设置合适的颜色贡献。

## 总结

五种光源解决不同几何问题。先按发光对象选形态，再确认材质与照明数据已经注册，最后同步位置和朝向；把所有效果都塞进 PointLight 只会得到错误的亮度分布。

## 建议参考项

- `org.boxutil.units.standard.light.PointLight`：点光源，局部动态光；
- `org.boxutil.units.standard.light.LinearLight`：线性光源，局部动态光；
- `org.boxutil.units.standard.light.SpotLight`：聚光灯，局部动态光；
- `org.boxutil.units.standard.light.AreaLight`：区域照明；
- `org.boxutil.units.standard.light.InfiniteLight`：全局方向照明；
- `org.boxutil.manager.EntityShadingDataManager`：查询材质和照明注册数据。
