# 创建 GraphicsLib 动态光源

命中回调提供这次事件的位置、目标与护盾状态，动态光源可以直接使用这些信息。本节为测试弹体创建两种命中光：护盾光固定在世界坐标，船体光保存目标上的本地偏移。位置关系建立后，再配置淡入与淡出，提交后的推进交给 GraphicsLib。

_本节预期阅读时间：\~18 分钟。_

_本节预期操作时间：\~25 分钟。_

## 学习目标

完成后，您将能够：

- 区分 light CSV 自动光源与 Java 动态光源；
- 设置点光源的位置、颜色、尺寸、强度、高度与镜面反射；
- 组合直接淡出以及 "淡入 → 保持 → 自动淡出" 两种生命周期；
- 把光源附着到战斗实体，并把世界命中点换算为目标本地偏移；
- 向 `LightShader` 提交一次光源，并判断重复提交造成的结果。

## 前置知识

- [C18-S1] 已接入 GraphicsLib 与 LazyLib；
- [C18-S5] 已在 `onApplicationLoad()` 中完成 `ShaderLib.init()`；
- [C1-S5] 的 projectile 配置；
- [C8-S3] 的 `OnHitEffectPlugin` 与命中回调；
- 能够在战斗中分别制造护盾命中与船体命中。

## 登记命中效果

复制 `data/weapons/proj/GearIn_durin_shot.proj` 为同目录的 `GearIn_light_hit_shot.proj`，把副本的 `id` 改为 `GearIn_light_hit_shot`。测试时将 `GearIn_durin.wpn` 的 `projectileSpecId` 改为这个新 ID；副本的命中字段使用：

```json
"onHitEffect": "data.scripts.gearin.combat.GearInHitLightEffect",
```

保存本节完整类到 `src/data/scripts/gearin/combat/GearInHitLightEffect.java`，构建后重启。弹体命中护盾或船体时，游戏调用该效果的 `onHit(...)`。`shieldHit` 决定使用哪组参数，`point` 和 `target` 提供本次命中的空间关系。

## 区分数据光源与动态光源

light CSV 适合 "只要这个 projectile 或 beam 出现，就按同一组数据产生光" 的关系。GraphicsLib 按登记的 ID 自动创建 standard、hit 和 flash 光源，Java 不需要追踪这些对象。

动态光源从一个具体回调开始。代码在事件发生时创建 `StandardLight`，因此可以使用本次命中的位置、命中的是护盾还是船体、目标朝向等上下文。`StandardLight` 还可以由舰船系统、武器效果或战斗插件创建；选择哪一个入口，取决于光源所表达的事件。

本节使用无参数构造器。它建立的是 `type = 0` 的点光源，初始尺寸和强度都为 `0`；仅仅调用 `new StandardLight()` 不会产生可见亮光，位置和显示参数需要继续设置。

## 创建并配置点光源

在命中方法内先创建光源，再设置两条分支共用的高度和镜面反射。以下为局部片段：

```java
StandardLight light = new StandardLight();
light.setHeight(100f);
light.setSpecularMult(0.8f);
```

`setHeight(100f)` 把光源放在战斗平面上方 100 su；使用 normal map 时，高度会参与表面受光方向的计算。`setSpecularMult(0.8f)` 设置点光源的镜面反射乘数，数值越高，高光越明显。无参数构造器已经选择点光源，因此不需要再调用 `setType(0)`。

位置、颜色、尺寸和强度共同决定画面中的亮光。`setLocation(...)` 接受世界坐标，`setColor(...)` 接受 `java.awt.Color`，`setSize(...)` 使用 su 表示光源尺寸，`setIntensity(...)` 写入当前亮度。尺寸或强度保持为默认的 `0` 时，点光源仍然不可见；下一段的护盾分支会一次设置这四项。

## 让护盾命中光直接淡出

护盾命中只需要一次短促反馈。命中点保持为世界坐标，蓝色光从设定强度立即开始淡出：

```java
if (shieldHit) {
    light.setLocation(point);
    light.setColor(SHIELD_COLOR);
    light.setSize(220f);
    light.setIntensity(0.8f);
    light.fadeOut(0.3f);
}
```

`fadeOut(0.3f)` 记录当前强度 `0.8`，随后在 0.3 秒内把实际强度降到 `0`。光源没有附着对象，目标舰船继续移动或转向时，蓝光仍停留在原命中坐标；这正好表现护盾表面被击中的瞬间。

`fadeOut(0f)` 或负数会让光立即消失。短时光仍应给出正的淡出时间，否则提交后几乎看不到结果。

## 把船体命中位置保存为本地偏移

船体上的亮斑应该跟随舰船运动。`attachTo(target)` 让光源取得目标的位置与朝向，`setOffset(...)` 则指定光源相对目标中心的本地坐标：

```java
Vector2f localOffset = Vector2f.sub(point, target.getLocation(), null);
VectorUtils.rotate(localOffset, -target.getFacing());

light.attachTo(target);
light.setOffset(localOffset);
```

`Vector2f.sub(point, target.getLocation(), null)` 得到世界坐标中的位移。目标已经旋转了 `target.getFacing()` 度，因此再旋转负的朝向角，才能把位移还原到目标本地坐标。GraphicsLib 每帧把这份本地偏移按目标的新朝向旋转，再加到目标的新位置上；橙色光由此停留在船体原命中区域。

这样设置的是船体分支的空间关系，光源仍需要下面的颜色、尺寸和时间参数才能显示。附着期间，`setLocation(...)` 改的是独立保存的世界坐标，显示位置继续由目标与偏移计算。

若需要把光留在解除附着时的位置，保存计算后的世界坐标，再解除附着并写回：

```java
Vector2f worldLocation = new Vector2f(light.getLocation());
light.unattach();
light.setLocation(worldLocation);
```

`unattach()` 会清除实体、beam 与 beam link 附着关系。先保存 `getLocation()` 的结果，能够避免光源回到附着前保存的旧世界坐标。

## 为船体光配置完整时间变化

继续在船体分支设置橙色、尺寸与目标强度，再安排淡入、保持和淡出：

```java
light.setColor(HULL_COLOR);
light.setSize(280f);
light.setIntensity(1f);
light.fadeIn(0.06f);
light.setLifetime(0.08f);
light.setAutoFadeOutTime(0.45f);
```

`fadeIn(0.06f)` 记录调用时的强度 `1`，把当前强度降为 `0`，随后在 0.06 秒内升回目标值。淡入期间寿命保持不变；淡入结束以后消耗 `setLifetime(0.08f)` 的 0.08 秒，耗尽时再按 `setAutoFadeOutTime(0.45f)` 淡出。

**强度先确定淡入目标，寿命从淡入结束后开始消耗。** 总可见时间约为 `0.06 + 0.08 + 0.45 = 0.59` 秒，实际转换发生在相应战斗帧。`fadeIn(...)` 调用以后再改变当前强度，也不会重新设置已经记录的淡入目标。

## 提交光源并理解生命周期

两条命中分支完成参数设置后，共用一次提交：

```java
LightShader.addLight(light);
```

`addLight(...)` 把对象加入当前战斗的光源列表。光照 shader 未启用时，这个调用直接返回，因此武器命中流程仍能继续。成功加入后，GraphicsLib 在战斗未暂停时调用光源的 `advance(amount)`；淡入、寿命和淡出都由这一步推进，结束的光源会从列表移除。

本节每次 `onHit(...)` 创建一个新对象，完成设置后提交一次。光源后续的移动关系、亮度与寿命已经保存在对象里，GraphicsLib 会继续推进它。

::: warning 重复提交也会重复推进
`addLight(...)` 不检查重复引用。同一个对象多次进入列表后，每次遍历都会推进这份对象，淡入、寿命和淡出随之加快，绘制也会重复。事件光在创建时提交一次。
:::

`StandardLight` 还支持线光、锥形光和方向光：三者分别需要第二位置、弧角或三维方向。`attachToBeam(...)` 可以跟随 beam 源端，带 `BeamAPI` 的构造器还能把位置或亮度连接到 beam 端点；命中亮斑只需要默认点光源。

## 常用方法速查表

| 方法                               | 读取或改变的状态                         | 本节结果               |
| ---------------------------------- | ---------------------------------------- | ---------------------- |
| `setLocation(...)`                 | 未附着光源的世界坐标                     | 护盾光固定在命中点     |
| `setColor(...)`                    | RGB 光色                                 | 蓝色护盾光或橙色船体光 |
| `setSize(...)`                     | 光源尺寸                                 | 220 su 或 280 su       |
| `setIntensity(...)`                | 当前亮度，随后调用 `fadeIn` 时记录为目标 | 0.8 或 1.0             |
| `setHeight(...)`                   | 光源相对战斗平面的高度                   | 100 su                 |
| `setSpecularMult(...)`             | 点光源镜面反射乘数                       | 0.8                    |
| `fadeIn(...)`                      | 从 0 到已设强度的淡入时间                | 船体光 0.06 秒         |
| `fadeOut(...)`                     | 从当前强度到 0 的淡出时间                | 护盾光 0.3 秒          |
| `setLifetime(...)`                 | 淡入结束后的剩余寿命                     | 船体光保持 0.08 秒     |
| `setAutoFadeOutTime(...)`          | 寿命耗尽后的淡出时间                     | 船体光 0.45 秒         |
| `attachTo(...)`／`getAttachment()` | 实体附着关系                             | 船体光跟随目标         |
| `setOffset(...)`／`getOffset()`    | 旋转前的目标本地偏移                     | 保持原命中区域         |
| `unattach()`                       | 清除全部附着关系                         | 光源恢复使用世界坐标   |
| `getLocation()`                    | 考虑附着后的当前世界坐标                 | 解除附着前保存位置     |
| `isFadingIn()`／`isFadingOut()`    | 当前淡入或淡出状态                       | 调试生命周期           |
| `getRemainingLifetime()`           | 当前剩余保持时间                         | 调试自动淡出时机       |

## 完整最小例

完整类实现护盾与船体两条分支，共用一次光源提交：

::: details 完整成品：GearInHitLightEffect

```java
package data.scripts.gearin.combat;

import java.awt.Color;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.OnHitEffectPlugin;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;

import org.dark.shaders.light.LightShader;
import org.dark.shaders.light.StandardLight;
import org.lazywizard.lazylib.VectorUtils;
import org.lwjgl.util.vector.Vector2f;

/**
 * Creates a finite GraphicsLib point light for each projectile hit.
 * Shield hits fade at the world-space impact point; hull hits follow the
 * target at the original impact offset.
 */
public class GearInHitLightEffect implements OnHitEffectPlugin {
    private static final Color SHIELD_COLOR = new Color(120, 200, 255);
    private static final Color HULL_COLOR = new Color(255, 120, 70);

    @Override
    public void onHit(DamagingProjectileAPI projectile, CombatEntityAPI target, Vector2f point,
            boolean shieldHit, ApplyDamageResultAPI damageResult, CombatEngineAPI engine) {
        StandardLight light = new StandardLight();
        light.setHeight(100f);
        light.setSpecularMult(0.8f);

        if (shieldHit) {
            light.setLocation(point);
            light.setColor(SHIELD_COLOR);
            light.setSize(220f);
            light.setIntensity(0.8f);
            light.fadeOut(0.3f);
        } else {
            Vector2f localOffset = Vector2f.sub(point, target.getLocation(), null);
            VectorUtils.rotate(localOffset, -target.getFacing());

            light.attachTo(target);
            light.setOffset(localOffset);
            light.setColor(HULL_COLOR);
            light.setSize(280f);
            light.setIntensity(1f);
            light.fadeIn(0.06f);
            light.setLifetime(0.08f);
            light.setAutoFadeOutTime(0.45f);
        }

        LightShader.addLight(light);
    }
}
```

:::

## 验证方案

前置数据：GraphicsLib 已安装并启用，`GearInHitLightEffect` 已进入 MOD JAR，测试武器引用的 projectile ID 为 `GearIn_light_hit_shot`。

1. 保存弹体与武器引用，构建 JAR 并重启。用都林命中护盾，观察命中点出现 `(120, 200, 255)` 的蓝光。
2. 让目标在命中后立即移动，确认蓝色光停留在原世界坐标，并在 0.3 秒内淡出。
3. 绕过护盾命中船体，观察 `(255, 120, 70)` 的橙色光先淡入、短暂保持，再自动淡出。
4. 在橙色光存在时移动目标，确认光源跟随目标平移。
5. 在橙色光存在时旋转目标，确认光源保持在原命中区域对应的本地坐标。
6. 连续命中多个船体位置，确认每次命中各自产生一个独立光源。
7. 关闭 GraphicsLib 光照能力后重新进入战斗，确认命中流程保持正常且动态光源不显示。
8. 恢复光照能力，确认护盾与船体两条光源分支重新出现。
9. 临时在逐帧入口提交同一个光源，观察重复光源累积；随后恢复命中回调中的单次提交。

预期结果：

- 护盾光固定在世界命中点，从 0.8 强度直接淡出；
- 船体光使用目标本地偏移，随目标平移和旋转；
- 船体光依次经历 0.06 秒淡入、0.08 秒保持和 0.45 秒自动淡出；
- 每次命中只向 GraphicsLib 提交一个新光源；
- 光照能力关闭时，`LightShader.addLight(...)` 安全返回。

## 故障分析

- **命中后完全没有亮光**：`StandardLight` 的尺寸或强度仍为默认的 `0`，或 GraphicsLib 光照能力未启用。
    - **解决方案**：确认两项显示参数已经设置，并检查 GraphicsLib 的 shader 与 buffer 能力。

- **护盾光跟着目标移动**：护盾分支错误地调用了 `attachTo(target)`。
    - **解决方案**：护盾分支只使用世界命中点调用 `setLocation(point)`。

- **船体光绕着目标落在错误位置**：世界位移没有按目标朝向的负值旋转成本地偏移。
    - **解决方案**：使用 `Vector2f.sub(...)` 求出世界位移，再调用 `VectorUtils.rotate(offset, -target.getFacing())`。

- **船体光只闪一下，没有完整的三阶段变化**：`fadeIn(...)` 之前没有设置目标强度，或保持时间与自动淡出时间没有写入。
    - **解决方案**：依次设置强度、淡入时间、寿命和自动淡出时间。

- **持续战斗后光源数量异常增长**：同一个对象在逐帧入口被重复传给 `addLight(...)`。
    - **解决方案**：在命中事件中创建并提交一次光源。

## 总结

命中回调建立光源的空间关系与时间变化：蓝光固定在世界命中点，橙光把命中位移转换为目标本地坐标，淡入结束后再消耗保持时间。光源只提交一次，后续位置与亮度由库推进；目标移动和转向时，两种位置关系便能分别表现出来。

## 建议参考项

- `org.dark.shaders.light.StandardLight`：配置点光源参数、附着关系和有限生命周期；
- `org.dark.shaders.light.LightShader`：向当前战斗提交或移除光源；
- `com.fs.starfarer.api.combat.OnHitEffectPlugin`：取得弹体命中点、目标与护盾命中状态；
- `org.lazywizard.lazylib.VectorUtils`：把世界位移旋转到目标本地坐标。
