package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;

/**
 * 环节一：装配 -> stats 交接 (舰船 stats 基值与 setVariant 重算)。
 *
 * 注册入口：生涯回调 onGameLoad，persistent data 键
 * REGISTER_MARK 一次性门控 (每个注册项用各自的键)。
 *
 * 行为：挂载后第一次 advance() 执行一次演示并自停 (isDone()=true，引擎下一帧自动移除)：
 *   1. 基线：创建 wolf_Assault 成员 (未入队)，读取舰船 stats 基值 (幅能/速度/CR) 与
 *      stats.getVariant() / getFleetMember() 的对象关系；
 *   2. 装配 -> stats：克隆装配加 5 电容 + 5 散热后 setVariant(withRefit=false,
 *      withStatsUpdate=true)，幅能容量 +5*200、幅能散耗 +5*10(引擎保留 ID 的 flat 写入)，
 *      stats.getVariant() 与传入变体同一引用。
 *
 * 演示全程同帧回滚 (只留下 REGISTER_MARK 键)，不遗留任何持久状态。
 */
public class GearInVariantToStats implements EveryFrameScript {

    /** 存档持久化标记：本演示是否已在该存档执行过 (随存档保存)。 */
    public static final String REGISTER_MARK = "GearIn_VariantToStats";

    private static final Logger LOG = Global.getLogger(GearInVariantToStats.class);

    /** 演示是否已执行；置位后 isDone() 返回 true，引擎下一帧自动移除本脚本。 */
    private boolean executed = false;

    public GearInVariantToStats() {
        // 空构造：注册由调用方执行 (onGameLoad 的 persistent data 门内)
    }

    @Override
    public boolean isDone() {
        return executed;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        if (executed) {
            return;
        }
        executed = true;
        runDemo();
    }

    private void runDemo() {
        // --- 1. 基线：成员未入队，stats 已在构造器内按变体首次建立 ---
        FleetMemberAPI wolf = Global.getFactory().createFleetMember(FleetMemberType.SHIP, "wolf_Assault");
        ShipVariantAPI variant = wolf.getVariant();
        MutableShipStatsAPI stats = wolf.getStats();
        LOG.info(String.format(
                "GearIn variantToStats baseline: hull=%s specId=%s caps=%d vents=%d "
                        + "fluxCap base=%.0f mod=%.0f fluxDiss base=%.0f mod=%.0f speed=%.1f maxCR=%.3f "
                        + "variantShared=%s memberLinked=%s",
                wolf.getHullId(), wolf.getSpecId(), variant.getNumFluxCapacitors(), variant.getNumFluxVents(),
                stats.getFluxCapacity().getBaseValue(), stats.getFluxCapacity().getModifiedValue(),
                stats.getFluxDissipation().getBaseValue(), stats.getFluxDissipation().getModifiedValue(),
                stats.getMaxSpeed().getModifiedValue(),
                stats.getMaxCombatReadiness().getModifiedValue(),
                stats.getVariant() == variant,
                stats.getFleetMember() == wolf));

        // --- 2. 装配 -> stats：setVariant 触发重算；vents/caps 是引擎保留 ID 的 flat 写入 ---
        ShipVariantAPI refit = wolf.getVariant().clone();
        int capsOld = refit.getNumFluxCapacitors();
        int ventsOld = refit.getNumFluxVents();
        float fluxCapBefore = stats.getFluxCapacity().getModifiedValue();
        float fluxDissBefore = stats.getFluxDissipation().getModifiedValue();
        refit.setNumFluxCapacitors(capsOld + 5);
        refit.setNumFluxVents(ventsOld + 5);
        wolf.setVariant(refit, false, true); // withRefit=false 跳过 CR 影响计算；withStatsUpdate=true 立即重算
        MutableShipStatsAPI statsAfterRefit = wolf.getStats();
        LOG.info(String.format(
                "GearIn variantToStats refit: caps %d->%d vents %d->%d fluxCap %.0f->%.0f (+%.0f) "
                        + "fluxDiss %.0f->%.0f (+%.0f) sharedVariant=%s specId=%s",
                capsOld, refit.getNumFluxCapacitors(), ventsOld, refit.getNumFluxVents(),
                fluxCapBefore, statsAfterRefit.getFluxCapacity().getModifiedValue(),
                statsAfterRefit.getFluxCapacity().getModifiedValue() - fluxCapBefore,
                fluxDissBefore, statsAfterRefit.getFluxDissipation().getModifiedValue(),
                statsAfterRefit.getFluxDissipation().getModifiedValue() - fluxDissBefore,
                statsAfterRefit.getVariant() == refit,
                wolf.getSpecId()));
    }
}
