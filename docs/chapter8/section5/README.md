# 制作分层渲染插件

计算机图形学已经超脱了地狱的范畴，升级为了炼狱。

不过好在我们暂时还不需要进入到那些晦涩难懂的 OpenGL 环节，先用游戏自带的一些方法试试水也是好的。而为了精确地控制渲染的层级，普通的每帧插件是绝对不够的，所以需要一个分层每帧插件。

听上去像绕口令？没关系，当您看到 `CombatEngineLayers` 里的那些层次时，您会感到释然的。

---

_本节预期阅读时间：\~14 分钟。_

_本节预期操作时间：\~20 分钟。_

## 前置知识

- [C8-S1] 的 `isEntityInPlay(...)` 与暂停判断；
- [C8-S2] 的过热冶炉船插与 `advanceInCombat(...)`；

## 插件接口

接口叫 `CombatLayeredRenderingPlugin`，注意是 `Combat` 开头，不是 `LayeredRenderingPlugin`：

```java
public interface CombatLayeredRenderingPlugin {
	public void init(CombatEntityAPI entity);
	public void cleanup();
	public boolean isExpired();

	public void advance(float amount);
	public EnumSet<CombatEngineLayers> getActiveLayers();
	public float getRenderRadius();
	public void render(CombatEngineLayers layer, ViewportAPI viewport);
}
```

接口文件通篇没有一个字的注释，方法之间是空行。能依据的只有方法名、签名和基类的默认实现，所以每个方法的实际用法要从原版实现里读。

`init(...)` 收到的是 `CombatEntityAPI`，不是引擎。引擎为插件造一个**承载实体**，插件靠这个实体的位置在战场上定位。接口里没有 `getEntity()`，取回实体是基类额外提供的：

```java
protected CombatEntityAPI entity;
protected CombatEngineLayers layer = CombatEngineLayers.BELOW_INDICATORS_LAYER;

public void init(CombatEntityAPI entity) { this.entity = entity; }
public CombatEntityAPI getEntity() { return entity; }
public EnumSet<CombatEngineLayers> getActiveLayers() { return EnumSet.of(layer); }
public float getRenderRadius() { return 100; }
public boolean isExpired() { return false; }   // 永不自动消失
public void render(CombatEngineLayers layer, ViewportAPI viewport) { }   // 空方法
```

**基类的 `render(...)` 是空的**，子类不重写它，插件什么都不会画。

`isExpired()` 默认返回 `false`，插件不会自己消失。**每加入一个插件就要自己负责让它过期**，否则它会一直留在战斗里。

## 图层

`CombatEngineLayers` 是枚举，声明顺序就是绘制顺序，靠后盖住靠前：

```text
BELOW_PLANETS
PLANET_LAYER
ABOVE_PLANETS
CLOUD_LAYER
BELOW_SHIPS_LAYER
UNDER_SHIPS_LAYER
ASTEROIDS_LAYER
CAPITAL_SHIPS_LAYER
CRUISERS_LAYER
DESTROYERS_LAYER
FRIGATES_LAYER
BELOW_PHASED_SHIPS_LAYER
PHASED_SHIPS_LAYER
STATION_WEAPONS_LAYER
CONTRAILS_LAYER
FIGHTERS_LAYER
BELOW_INDICATORS_LAYER
FF_INDICATORS_LAYER
ABOVE_SHIPS_LAYER
ABOVE_SHIPS_AND_MISSILES_LAYER
ABOVE_PARTICLES_LOWER
ABOVE_PARTICLES
JUST_BELOW_WIDGETS
```

枚举里的常量几乎全无注释，含义靠名字和它们之间的相对位置判断。`PHASED_SHIPS_LAYER` 要在战机之下，否则相位结束时会留下画面残影。

`getActiveLayers()` 返回 `EnumSet`，可以一次声明多个层。引擎在每一个声明的层各调一次 `render(...)`，传进来的 `layer` 参数就是当前这一层，插件据此区分自己这次该画什么。

基类的默认层是 `BELOW_INDICATORS_LAYER`，那是所有舰船之上、指示器之下。要衬在舰体下面，得在构造时传 `BELOW_SHIPS_LAYER`。

## 加入战斗与剔除

`CombatEngineAPI` 上只有一个相关方法：

```java
	void removeObject(Object object);

	CombatEntityAPI addLayeredRenderingPlugin(CombatLayeredRenderingPlugin plugin);
```

**它返回的是承载实体，不是插件本身。** 原版的调用点几乎都写成 `CombatEntityAPI e = engine.addLayeredRenderingPlugin(...)`，紧接着 `e.getLocation().set(...)`。返回值丢掉，就没有位置可以设。

移除通道是上面那行 `removeObject(Object)`，把承载实体或者插件传进去。**没有 `removeLayeredRenderingPlugin` 这个方法**，照着名字去找会找不到。

**承载实体的位置决定插件是否被绘制。** 引擎拿这个位置和 `getRenderRadius()` 判断插件在不在镜头内，镜头外的不调用 `render(...)`。默认半径是 `100`，比这更大的图形必须自己给出真实尺寸，否则画面边缘会缺一块。

承载实体不会自动跟着任何东西走。要让光环贴在舰船上，得在 `advance(...)` 里每帧同步位置。

## 视口透明度

`render(...)` 收到 `ViewportAPI`。上面有一个方法必须用上：

```java
	float getAlphaMult();
```

它是视口的整体透明度，战斗开场与结束的淡入淡出就靠它。原版插件一律把它乘进自己的绘制透明度里：

```java
float alphaMult = viewport.getAlphaMult();
alphaMult *= b;
sprite.setAlphaMult(alphaMult);
```

不乘的后果是：别的画面在淡出，这一段绘制稳稳地留在原地，整个转场只有它不跟着走。

`ViewportAPI` 上的坐标换算都是单值方法，没有整向量的版本：`convertWorldXtoScreenX(float)`、`convertScreenYToWorldY(float)` 各换一个坐标，要用一个点就调两次。

## 原版的实现分工

原版 `impl/combat/` 下没有一个类直接实现这个接口，全部继承基类：

```java
public class ShipExplosionFlareVisual extends BaseCombatLayeredRenderingPlugin {
```

它是结构最干净的一个，只画一次爆炸闪光：

```java
	@Override
	public EnumSet<CombatEngineLayers> getActiveLayers() {
		return EnumSet.of(CombatEngineLayers.ABOVE_PARTICLES_LOWER);
	}
```

它的 `advance(...)` 开头先判暂停，然后把承载实体的位置同步到被附着的对象；`isExpired()` 返回 `fader.isFadedOut()`，闪光淡完自己退场；渲染用 `SpriteAPI.renderAtCenter(x, y)`，不碰 GL11。

这套分工可以照着理解：**位置在 `advance(...)` 里跟，亮度在 `render(...)` 里读，退场看 `isExpired()`。**

## 热量光环

前面讲的是这一类的通用规则。现在给一个具体的用场：过热冶炉让射速跟着幅能往上走，可这个变化只写在状态栏里，混战中没有人会去盯着状态栏看。舰船底下那一圈越烧越亮的光，就是给不看状态栏的人准备的。

光环应当衬在舰船下方，不遮挡舰体与武器，所以用 `BELOW_SHIPS_LAYER`；只出现在装有过热冶炉的舰船上，所以由船插自己创建，而不是由一个全局插件遍历所有舰船。

**原版没有任何船插创建分层渲染插件。** `data/hullmods/` 下的文件、整个 `starsector-core/data/` 目录都搜不到 `addLayeredRenderingPlugin`，全部调用点都在武器效果与插件里。这一节的做法没有原版可抄，得自己决定什么时候创建、什么时候退场。

## 登记贴图

光环使用原版的 `graphics/fx/glow64.png`，一张中心亮、边缘透明的圆形光斑。在 `data/config/settings.json` 的 `graphics` 中，向已有的 `fx` 分类加入一行：

```json
		"fx":{
			"GearIn_heat_glow":"graphics/fx/glow64.png",
		},
```

代码通过 `Global.getSettings().getSprite("fx", "GearIn_heat_glow")` 取得贴图。分类与键由 MOD 自行决定，键带 `GearIn_` 前缀以免与其他 MOD 冲突。将来换成自制图片时，只需修改这一行的路径，代码不变。

## 渲染插件

`src/data/scripts/gearin/combat/GearInHeatGlowRenderer.java`：

```java
package data.scripts.gearin.combat;

import java.awt.Color;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseCombatLayeredRenderingPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEngineLayers;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;

/** 过热冶炉的热量光环：画在舰船下方，亮度随幅能升高。 */
public class GearInHeatGlowRenderer extends BaseCombatLayeredRenderingPlugin {

    private static final Color GLOW_COLOR = new Color(255, 110, 40);
    private static final float SIZE_MULT = 3f;

    private final ShipAPI ship;
    private final float size;
    private SpriteAPI sprite;
    private boolean expired;

    public GearInHeatGlowRenderer(ShipAPI ship) {
        super(CombatEngineLayers.BELOW_SHIPS_LAYER);
        this.ship = ship;
        this.size = ship.getCollisionRadius() * SIZE_MULT;
    }

    @Override
    public void init(CombatEntityAPI entity) {
        super.init(entity);
        sprite = Global.getSettings().getSprite("fx", "GearIn_heat_glow");
        sprite.setSize(size, size);
        sprite.setColor(GLOW_COLOR);
        sprite.setAdditiveBlend();
        entity.getLocation().set(ship.getLocation());
    }

    @Override
    public void advance(float amount) {
        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine.isPaused()) {
            return;
        }
        if (!engine.isEntityInPlay(ship) || !ship.isAlive()) {
            expired = true;
            return;
        }
        entity.getLocation().set(ship.getLocation());
    }

    @Override
    public float getRenderRadius() {
        return size;
    }

    @Override
    public void render(CombatEngineLayers layer, ViewportAPI viewport) {
        float level = ship.getFluxLevel();
        if (level <= 0f) {
            return;
        }
        sprite.setAlphaMult(level * viewport.getAlphaMult());
        sprite.renderAtCenter(entity.getLocation().x, entity.getLocation().y);
    }

    @Override
    public boolean isExpired() {
        return expired;
    }
}
```

**亮度在 `render(...)` 中读取，位置在 `advance(...)` 中更新。** 暂停时 `advance(...)` 直接返回，`render(...)` 仍然逐帧调用。幅能在暂停时不变，光环保持原样；若把亮度的计算也放进 `advance(...)`，结果相同，但读取放在绘制处更容易看出画面由什么决定。

`setAdditiveBlend()` 让贴图的颜色叠加到背景上，而不是覆盖背景。光斑的透明边缘因此不会在深色背景上留下灰边，这是原版光效普遍采用的混合方式。

`isExpired()` 返回 `true` 后，引擎移除承载实体并调用 `cleanup()`。本插件不持有需要释放的资源，沿用基类的空实现。

## 由船插创建光环

在 `GearInOverheatForgeHullMod` 的 `advanceInCombat(...)` 开头、存活判断之后加入：

```java
if (!ship.getCustomData().containsKey(GLOW_KEY)) {
    ship.setCustomData(GLOW_KEY, true);
    Global.getCombatEngine().addLayeredRenderingPlugin(new GearInHeatGlowRenderer(ship));
}
```

并在常量区加入：

```java
private static final String GLOW_KEY = "GearIn_overheat_forge_glow";
```

还需要在文件开头导入 `data.scripts.gearin.combat.GearInHeatGlowRenderer`。

`advanceInCombat(...)` 每帧调用，光环只应创建一次。**这个标记不能放在船插的字段里。** 船插的效果类整个应用只实例化一次，所有装有过热冶炉的舰船共用它，字段记下的 "已创建" 会让第二艘舰船永远得不到光环。舰船的 `setCustomData(...)` 把标记存在这艘舰船自己身上，每艘舰船各记各的。舰船对象只存在于一场战斗中，下一场战斗的舰船没有这个标记，光环会重新创建。

`getCustomData()` 返回的映射只能读取，写入必须通过 `setCustomData(...)`。

### 进游戏核对

执行 `gradle jar`，重启游戏，进入测试战役。

- 工匠装有过热冶炉。开火使幅能升高，舰船下方出现橙色光环，幅能越高光环越亮；幅能排空后光环消失。
- 光环在舰体之下，不遮挡武器与舰体。
- 移动镜头使工匠位于画面边缘，光环不会先于舰船消失。
- 暂停战斗，光环保持当前亮度。
- 工匠被摧毁后光环消失。
- 其他没有过热冶炉的舰船不出现光环。

## 常见问题

- **光环盖在舰体之上**：构造时没有传入图层，基类使用默认的 `BELOW_INDICATORS_LAYER`。
    - **解决方案**：调用 `super(CombatEngineLayers.BELOW_SHIPS_LAYER)`。

- **舰船移到画面边缘时光环先消失**：承载实体没有跟随舰船，或 `getRenderRadius()` 小于光环尺寸。
    - **解决方案**：在 `advance(...)` 中同步位置，并让半径不小于光环的绘制尺寸。

- **调用 `removeLayeredRenderingPlugin(...)` 找不到方法**：这个方法不存在。
    - **解决方案**：移除用 `removeObject(...)`，传入承载实体或插件。

- **只有第一艘舰船有光环**：创建标记写在了船插的字段里。
    - **解决方案**：船插实例由所有舰船共用，标记应通过 `ship.setCustomData(...)` 存在舰船上。

- **光环越来越亮，帧数下降**：每帧都创建了一个新的渲染插件。
    - **解决方案**：创建前检查 `getCustomData()` 中的标记。

- **启动时报贴图不存在**：`getSprite(...)` 的分类或键与 `settings.json` 不一致，或新的一行写在了 `fx` 以外的分类中。
    - **解决方案**：核对分类 `fx` 与键 `GearIn_heat_glow`。

## 建议参考项

- `com.fs.starfarer.api.combat.CombatLayeredRenderingPlugin`：接口方法与 `init(...)` 收到的承载实体。
- `com.fs.starfarer.api.combat.BaseCombatLayeredRenderingPlugin`：默认实现、默认图层与默认半径。
- `com.fs.starfarer.api.combat.CombatEngineLayers`：各图层的声明顺序。
- `com.fs.starfarer.api.combat.ViewportAPI`：`getAlphaMult()` 与单值坐标换算。
- `com.fs.starfarer.api.graphics.SpriteAPI`：尺寸、颜色、透明度与混合方式。
- `com.fs.starfarer.api.impl.combat.ShipExplosionFlareVisual`：结构最干净的原版实现，位置、亮度与退场三者分工清楚。
- `com.fs.starfarer.api.impl.combat.CryoblasterEffect`：原版的分层渲染效果，使用叠加混合与视口透明度。
