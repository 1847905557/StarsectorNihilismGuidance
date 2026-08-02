package data.scripts.gearin.combat;

import java.awt.Color;
import java.util.EnumSet;

import org.apache.log4j.Logger;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseCombatLayeredRenderingPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEngineLayers;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ViewportAPI;

/**
 * 完整最小例：分层渲染插件（CombatLayeredRenderingPlugin）的层级、视口与清理。
 *
 * 渲染管线事实（0.98a-RC8）：
 * - engine.addLayeredRenderingPlugin(plugin) 用自定义战斗实体包装插件并加入引擎；
 *   executeAdds 时调用 plugin.init(entity) 并按 getActiveLayers() 注册进 LayeredRenderer；
 * - 每帧（非暂停分支）推进 entity.advance(amount)，随后 isExpired() 为 true 的实体
 *   进移除队列；移除时引擎调用 entity.cleanup() -> plugin.cleanup()——
 *   cleanup() 是释放 GL 资源的正常时机；
 * - 战斗结束 destroyInstance 只清空对象表、不调用 cleanup()——
 *   插件必须在 advance() 里主动失效（isExpired() -> true），不能等"战斗结束回调"；
 * - 渲染阶段：自定义战斗实体的 render 先判 viewport.isNearViewport(entity.getLocation(),
 *   plugin.getRenderRadius())，通过才调 plugin.render(layer, viewport)——
 *   剔除依据是承载实体的位置与 getRenderRadius()，位置必须同步到 entity；
 * - 渲染回调在渲染阶段被调用，绘制必须在这里用 GL 原语完成；advance() 属于推进阶段，
 *   不做任何绘制；
 * - 暂停时引擎不推进实体（advanceInner 的暂停分支只 advance 地形与玩家舰船），
 *   但渲染循环照常执行（渲染不暂停）；isPaused() 守卫为防御性写法（成熟实例一致做法）；
 * - 插件、引擎与舰船引用禁止跨战斗缓存（C06-S03 / C07-S04 同规）。
 */
public class GearInLayeredRenderer extends BaseCombatLayeredRenderingPlugin {

    private static final Logger LOG = Global.getLogger(GearInLayeredRenderer.class);

    /** 半透明方框的边长（世界单位）。 */
    private static final float MARKER_SIZE = 120f;

    /** 被追踪的舰船：每帧读取它的实时位置，不缓存坐标副本。 */
    private final ShipAPI trackedShip;

    private final Color color;

    private CombatEngineAPI engine;
    private boolean valid = true;
    private float elapsed = 0f;

    public GearInLayeredRenderer(ShipAPI trackedShip, Color color) {
        this.trackedShip = trackedShip;
        this.color = color;
        this.layer = CombatEngineLayers.ABOVE_SHIPS_AND_MISSILES_LAYER;
    }

    @Override
    public void init(CombatEntityAPI entity) {
        super.init(entity);
        engine = Global.getCombatEngine();
        advance(0f); // 立即同步一次位置，避免首帧画在战场原点
        LOG.info("GearIn layered renderer init: layers=" + getActiveLayers());
    }

    @Override
    public EnumSet<CombatEngineLayers> getActiveLayers() {
        return EnumSet.of(layer);
    }

    @Override
    public float getRenderRadius() {
        return MARKER_SIZE;
    }

    @Override
    public void advance(float amount) {
        if (engine == null) {
            return; // 防御：init() 先于 advance() 被引擎调用，保留与成熟实例一致的判空
        }
        if (engine.isPaused()) {
            return; // 暂停守卫（防御性；RC8 引擎的暂停分支本就不推进实体）
        }
        if (!engine.isEntityInPlay(trackedShip) || !trackedShip.isAlive()) {
            valid = false; // 失效：下一轮 executeRemoves 移除实体并调用 cleanup()
            return;
        }
        elapsed += amount;
        // 渲染剔除依据的是承载实体的位置：必须与被追踪位置保持同步
        entity.getLocation().set(trackedShip.getLocation());
    }

    @Override
    public boolean isExpired() {
        return !valid;
    }

    @Override
    public void cleanup() {
        LOG.info("GearIn layered renderer cleanup: elapsed=" + elapsed);
    }

    @Override
    public void render(CombatEngineLayers layer, ViewportAPI viewport) {
        if (engine == null) {
            return;
        }
        float alpha = viewport.getAlphaMult() * color.getAlpha() / 255f;
        if (alpha <= 0f) {
            return;
        }
        // 渲染回调内用 GL 原语绘制：视口变换已由渲染器应用，直接提交世界坐标
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4ub((byte) color.getRed(), (byte) color.getGreen(), (byte) color.getBlue(),
                (byte) (alpha * 255f));

        Vector2f loc = entity.getLocation();
        float half = MARKER_SIZE * 0.5f;
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(loc.x - half, loc.y - half);
        GL11.glVertex2f(loc.x - half, loc.y + half);
        GL11.glVertex2f(loc.x + half, loc.y + half);
        GL11.glVertex2f(loc.x + half, loc.y - half);
        GL11.glEnd();
    }
}
