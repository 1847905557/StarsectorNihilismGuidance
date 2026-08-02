package data.scripts.gearin.lib;

import java.util.List;

import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.input.InputEventAPI;

/** 薄载体接线类：把设置、伪光束与 HUD 状态条三个工具族接进一场战斗。
 * 由既有战斗插件在 init() 中 addPlugin 注册（S02-P01 模型），本身不含工具
 * 逻辑，只做接线：创建三个工具类、把引擎注入光束发射器、按帧转发 advance
 * 并执行暂停守卫。生涯工具不在这里接线（调用方是生涯脚本）。 */
public class GearInMagicWiring extends BaseEveryFrameCombatPlugin {

    private final GearInMagicSettings settings = new GearInMagicSettings();
    private final GearInBeamCaster beam = new GearInBeamCaster(settings);
    private final GearInHudBar hud = new GearInHudBar(beam, settings);

    private CombatEngineAPI engine;

    @Override
    public void init(CombatEngineAPI engine) {
        this.engine = engine;
        beam.init(engine);
        GearInCampaignTools.logCampaignInfo(); // 战役（mission）场景：sector 为 null → "not in campaign" 分支
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        if (engine == null) {
            return; // 防御性判空（S02-P01 同款姿势）
        }
        if (settings.hudEnabled) {
            hud.refresh(engine); // 放在暂停守卫之前：暂停期间也刷新，状态条才不会超时被移除
        }
        if (engine.isPaused()) {
            return; // 暂停守卫（S02-P01）：光束与充能只在非暂停推进
        }
        beam.advance(amount);
    }
}
