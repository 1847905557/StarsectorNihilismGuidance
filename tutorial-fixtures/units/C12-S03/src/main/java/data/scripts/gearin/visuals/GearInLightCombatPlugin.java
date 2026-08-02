package data.scripts.gearin.visuals;

import java.util.List;

import org.dark.shaders.light.LightData.LightDataType;

import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.input.InputEventAPI;

/**
 * 完整最小例：GraphicsLib 光源与纹理的最小消费面（战斗内）的接线插件。
 *
 * 适用边界（0.98a-RC8，GraphicsLib 1.10.0）：
 * - GraphicsLib 的初始化与 LightData/TextureData CSV 注册发生在 onApplicationLoad
 *   （本 MOD 的接入片段见页面正文，fixture 不含该片段）；
 * - ShaderHook（settings plugins 注册的 EveryFrameCombatPlugin）在每场战斗开始时
 *   创建 LightShader 的战斗 LocalData（engine customData 键 "shaderlib_LightShader"），
 *   并注册一个覆盖全部层的分层渲染钩子；LightShader 声明在
 *   ABOVE_SHIPS_AND_MISSILES_LAYER 层渲染（ShaderAPI.isCombat() == true）；
 * - LightShader.addLight 只在光源 shader 已启用且战斗 LocalData 已存在时把光源写进
 *   当前战斗的光源列表，其余情况静默跳过——动态光源必须在战斗内添加；
 * - 光源列表随战斗销毁；未永久化的光源在 superLifetime（60 秒）耗尽后被 failsafe
 *   移除；advance 返回 true（size 或 intensity 归零）的光源由 LightShader.advance
 *   逐帧移除，fadeOut 是标准自灭姿势；
 * - 暂停时 LightShader.advance 不推进光源（光源静止但保持可见）。
 *
 * 本类只做接线：光源实体（挂接光 / 周期闪光）与 GraphicsLib 状态报告分别在
 * GearInShipLight / GearInFlashSpawner / GearInLightReporter。
 */
public class GearInLightCombatPlugin extends BaseEveryFrameCombatPlugin {

    private CombatEngineAPI engine;
    private final GearInShipLight shipLight = new GearInShipLight();
    private final GearInFlashSpawner flashSpawner = new GearInFlashSpawner();

    @Override
    public void init(CombatEngineAPI engine) {
        this.engine = engine;
        GearInLightReporter.reportCapabilities();
        GearInLightReporter.reportLightData("lightac_shot", LightDataType.PROJECTILE);
        GearInLightReporter.reportLightData("pdlaser", LightDataType.BEAM);
        GearInLightReporter.reportShipTextures(engine);
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        if (engine == null) {
            return; // 防御性判空（C02-S01 同款姿势）
        }
        if (engine.isPaused()) {
            return; // 暂停守卫（C02-S01）：暂停时 advance 仍会被调用，光源静止但保持可见
        }
        ShipAPI player = engine.getPlayerShip();
        shipLight.update(player);
        flashSpawner.update(amount, player);
    }
}
