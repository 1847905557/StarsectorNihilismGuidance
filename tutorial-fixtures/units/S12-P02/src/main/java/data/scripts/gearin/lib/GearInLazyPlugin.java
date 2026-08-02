package data.scripts.gearin.lib;

import java.util.List;

import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.util.IntervalUtil;

/**
 * 薄载体：把四个工具族用法片段接成完整最小例的战斗插件。
 *
 * 由既有战斗插件在 init() 中 addPlugin 注册（S02-P01 模型），只在本场
 * 战斗内有效；每个工具族一个用法片段类（GearInLazyMathUsage /
 * GearInLazyCombatQuery / GearInLazyJsonStore / GearInLazyRuntimeProbe），
 * 本类只做接线与节奏控制（暂停守卫 + 报告周期），不含工具调用细节。
 */
public class GearInLazyPlugin extends BaseEveryFrameCombatPlugin {

    private CombatEngineAPI engine;
    private final IntervalUtil reportInterval = new IntervalUtil(2f, 2f);
    private int battleCount;
    private int reportCount;

    @Override
    public void init(CombatEngineAPI engine) {
        this.engine = engine;
        battleCount = GearInLazyJsonStore.readInt("battles");
        battleCount++;
        GearInLazyJsonStore.writeInt("battles", battleCount);
        GearInLazyRuntimeProbe.logInit(battleCount);
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        if (engine.isPaused()) {
            return; // 暂停守卫（S02-P01）：暂停时 advance 仍会被调用
        }
        reportInterval.advance(amount);
        if (reportInterval.intervalElapsed()) {
            reportCount++;
            GearInLazyMathUsage.report(engine);
            GearInLazyCombatQuery.report(engine, reportCount);
            GearInLazyJsonStore.writeInt("reports", reportCount);
        }
    }
}
