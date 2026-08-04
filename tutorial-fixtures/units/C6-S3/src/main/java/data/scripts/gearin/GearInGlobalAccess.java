package data.scripts.gearin;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.combat.CombatEngineAPI;

/**
 * 角色：Global 静态访问器观测与阶段判断工具。
 *
 * 全部方法是静态工具，可从任何已注册的脚本/插件调用：
 * - 资源加载期（C6-S2 的 onApplicationLoad）：getSector()/getCombatEngine() 为 null，
 *   只能读 getSettings()/getSoundPlayer()/getLogger()；
 * - 生涯回调与生涯脚本：getSector() 可用；
 * - 战斗插件：getCombatEngine() 是当前战斗引擎，必须每帧现取，不得跨战斗缓存。
 *
 * 声音播放入口见 GearInSoundPlayer（声音 ID 由 C5-S1 声明、C5-S2 注册，本单元只消费）。
 */
public class GearInGlobalAccess {

    /** 日志器：进程级，任何阶段可用（org.apache.log4j.Logger，输出到 starsector.log）。 */
    private static final Logger LOG = Global.getLogger(GearInGlobalAccess.class);

    private GearInGlobalAccess() {
        // 工具类：不实例化
    }

    /** 当前是否处于战斗：必须用 getCurrentState() 判断，不能依赖 getCombatEngine() 是否为 null。 */
    public static boolean isInCombat() {
        return Global.getCurrentState() == GameState.COMBAT;
    }

    /** 访问器巡览：记录当前阶段与各入口的可用性（注意资源加载期 sector/engine 为 null）。 */
    public static void logAccessorState() {
        GameState state = Global.getCurrentState();
        SectorAPI sector = Global.getSector();
        CombatEngineAPI engine = Global.getCombatEngine();
        LOG.info("GearIn state=" + state
                + " settings=" + (Global.getSettings() != null)
                + " soundPlayer=" + (Global.getSoundPlayer() != null)
                + " sector=" + (sector != null)
                + " engine=" + (engine != null)
                + " loadingSave=" + Global.LOADING_SAVE);
        if (sector == null) {
            LOG.warn("GearIn getSector() is null: 资源加载期访问生涯对象会空指针");
        }
        if (engine == null) {
            LOG.warn("GearIn getCombatEngine() is null: 资源加载期访问战斗对象会空指针");
        }
    }
}
