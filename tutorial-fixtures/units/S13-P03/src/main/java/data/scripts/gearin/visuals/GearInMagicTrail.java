package data.scripts.gearin.visuals;

import java.awt.Color;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.lwjgl.util.vector.Vector2f;
import org.magiclib.plugins.MagicTrailPlugin;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEngineLayers;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.input.InputEventAPI;

/**
 * 完整最小例：MagicLib 1.5.1 MagicTrailPlugin 的数据、创建、推进、渲染与清理。
 *
 * 由既有战斗插件在 init() 中 addPlugin 注册（S02-P01 模型），只在本场战斗内有效：
 * - 数据：尾迹 sprite 必须是 settings.json "fx" 分类注册的键（MagicLib 自带
 *   base_trail_smooth / base_trail_contrail 等 12 张，宽高为 2 的倍数、垂直长条）；
 *   本示例不新增 PNG 资产；
 * - 创建：每条尾迹从 MagicTrailPlugin.getUniqueID() 取一次 ID，此后每帧复用——
 *   每帧新 ID 会变成多条无法连接的短条（硬规则 5）；同一条 trail 中途换 sprite
 *   会拆成两条；段不足 2 段不渲染（硬规则 7，首帧无画面是正常现象）；
 * - 推进：addTrailMember* 只负责登记段，段的移动/淡出/移除由 MagicTrailPlugin.
 *   advance 完成（暂停时整条冻结，硬规则 8）；每段总寿命 = in + main + out；
 * - 渲染：渲染由 MagicTrailPlugin.init 注册的 MagicTrailRenderer（分层渲染插件，
 *   S07-P05 通道）完成；本示例两条尾迹分别用默认 CONTRAILS_LAYER 与显式
 *   ABOVE_SHIPS_AND_MISSILES_LAYER；
 * - 清理：舰船死亡/战斗结束时 cutTrailsOnEntity 切断旧段（旧段按自身寿命淡出，
 *   新段不再与之相连，硬规则 9）；战斗内全部状态随战斗结束释放，禁止跨战斗缓存
 *   （硬规则 10）。
 */
public class GearInMagicTrail extends BaseEveryFrameCombatPlugin {

    private static final Logger LOG = Global.getLogger(GearInMagicTrail.class);

    /** settings.json "fx" 分类的尾迹贴图键（MagicLib 自带，无需新增资产）。 */
    private static final String SPRITE_BODY = "base_trail_smooth";
    private static final String SPRITE_GLOW = "base_trail_contrail";

    /** 登记新段的时间间隔（秒）：每 0.05 秒在舰船当前位置登记一段。 */
    private static final float SEGMENT_INTERVAL = 0.05f;

    private CombatEngineAPI engine;
    private ShipAPI ship;
    private SpriteAPI bodySprite;
    private SpriteAPI glowSprite;

    /** 两条尾迹各自的 ID：getUniqueID() 只取一次，生命周期内跨帧复用（硬规则 5）。 */
    private float bodyTrailID;
    private float glowTrailID;

    private float segmentTimer;
    private float reportTimer;
    private int segmentCount;
    private boolean cut;

    @Override
    public void init(CombatEngineAPI engine) {
        this.engine = engine;
        this.ship = engine.getPlayerShip(); // 战斗内永不 null（S07-P01）
        this.bodySprite = Global.getSettings().getSprite("fx", SPRITE_BODY);
        this.glowSprite = Global.getSettings().getSprite("fx", SPRITE_GLOW);
        this.bodyTrailID = MagicTrailPlugin.getUniqueID();
        this.glowTrailID = MagicTrailPlugin.getUniqueID();
        LOG.info("GearIn trail init: body=" + SPRITE_BODY + " glow=" + SPRITE_GLOW
                + " bodyID=" + bodyTrailID + " glowID=" + glowTrailID);
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        if (engine == null || ship == null) {
            return; // init() 不保证先于 advance()（S07-P04），必须判空
        }
        if (engine.isPaused()) {
            return; // 暂停守卫（S02-P01）：暂停时 MagicTrailPlugin 不推进任何段（硬规则 8）
        }
        if (!engine.isEntityInPlay(ship) || !ship.isAlive()) {
            cutTrails();
            return;
        }
        segmentTimer += amount;
        if (segmentTimer >= SEGMENT_INTERVAL) {
            segmentTimer = 0f;
            spawnSegments();
        }
        reportTimer += amount;
        if (reportTimer >= 1f) {
            reportTimer = 0f;
            LOG.info("GearIn trail: segments=" + segmentCount
                    + " elapsed=" + Math.round(engine.getTotalElapsedTime(false)));
        }
        if (engine.isCombatOver()) {
            cutTrails(); // 战斗结束收尾：切断尾迹，旧段随各自寿命自然淡出（S07-P04 的收尾姿势）
        }
    }

    /** 每帧向两条尾迹各登记一个新段：旧段留在原地、新段在舰船当前位置 → 连续条带。 */
    private void spawnSegments() {
        Vector2f location = ship.getLocation();
        float facing = ship.getFacing();
        // 简单版：默认渲染层 CONTRAILS_LAYER（MagicTrailPlugin 源码）
        MagicTrailPlugin.addTrailMemberSimple(
                ship,            // linkedEntity：cutTrailsOnEntity 的切断对象（硬规则 9）
                bodyTrailID,     // 同一尾迹复用同一 ID（硬规则 5）
                bodySprite,      // 中途不得换 sprite（硬规则 5）
                location,
                0f,              // speed：段留在生成点，靠"每帧新段 + 舰船移动"连成尾迹
                facing,          // angle（度）：段的移动方向与宽度测量方向
                14f,             // startSize（SU）
                3f,              // endSize（SU）
                new Color(120, 220, 255),
                0.8f,            // opacity 0..1（透明度上限）
                0.15f,           // inDuration（渐入秒）
                0.35f,           // mainDuration（满亮秒）
                1.2f,            // outDuration（渐出秒）
                true);           // additive：叠加混合（规则 14）
        // 进阶版：显式层 + 纹理循环长度/滚动速度 + 宽度脉动选项
        Map<String, Object> options = new HashMap<>();
        options.put("SIZE_PULSE_WIDTH", 4f);   // Float：每次脉动增加的宽度（SU）
        options.put("SIZE_PULSE_COUNT", 2);    // Integer：整条寿命内的脉动次数
        MagicTrailPlugin.addTrailMemberAdvanced(
                ship,
                glowTrailID,
                glowSprite,
                location,
                0f, 0f,          // startSpeed / endSpeed
                facing,          // angle（度）
                0f, 0f,          // startAngularVelocity / endAngularVelocity
                22f, 6f,         // startSize / endSize
                new Color(60, 120, 255),
                new Color(180, 230, 255),
                0.6f,            // opacity
                0.1f, 0.2f, 0.8f, // in / main / out（总寿命 1.1 秒）
                true,            // additive
                120f,            // textureLoopLength（SU）：纹理循环长度，≤0 则整条拉伸
                200f,            // textureScrollSpeed：200 = 每秒滚动整张纹理的 1/5
                0f,              // textureOffset：0 无偏移；-1 为每条 tracker 固定随机偏移
                null,            // offsetVelocity：附加恒定速度
                options,         // advancedOptions（SIZE_PULSE_WIDTH/COUNT/FORWARD_PROPAGATION）
                CombatEngineLayers.ABOVE_SHIPS_AND_MISSILES_LAYER, // 显式层（中途不可改，硬规则 12）
                1f);             // frameOffsetMult：每帧速度偏移倍率
        segmentCount++;
    }

    /** 切断两条尾迹：旧段不再与新段相连，按自身寿命淡出（传送/死亡/战斗结束前调用）。 */
    private void cutTrails() {
        if (cut) {
            return;
        }
        cut = true;
        MagicTrailPlugin.cutTrailsOnEntity(ship);
        LOG.info("GearIn trail cut: segments=" + segmentCount);
    }
}
