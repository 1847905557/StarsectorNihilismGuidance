package data.scripts.gearin.lib;

import org.magiclib.util.MagicUI;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;

/** 战斗 HUD 状态条(MagicUI)：以固定 key 刷新玩家舰状态条。
 * 只注册数据，绘制由 MagicLib 的 MagicRenderPlugin(settings plugins 注册的
 * 战斗插件)每帧完成；状态条约 5 秒不被刷新就会被移除(含暂停时间)，因此
 * 刷新调用必须放在暂停守卫之前。 */
public class GearInHudBar {

    /** 战斗 HUD 状态条 key：MagicLib 要求对本 MOD 唯一；换 key 会生成另一条状态条。 */
    private static final String HUD_BAR_KEY = "gear_in_beam_charge";

    private final GearInBeamCaster beam;
    private final GearInMagicSettings settings;

    public GearInHudBar(GearInBeamCaster beam, GearInMagicSettings settings) {
        this.beam = beam;
        this.settings = settings;
    }

    /** 刷新状态条：填充进度 = 光束充能，颜色复用光束颜色(改配置 → 光束与状态条一起变色)。 */
    public void refresh(CombatEngineAPI engine) {
        ShipAPI player = engine.getPlayerShip();
        if (player == null) {
            return;
        }
        MagicUI.drawInterfaceStatusBar(
                player,
                HUD_BAR_KEY,            // 固定 key：改变文本不会生成新条(无 key 重载用文本兼作 key)
                beam.getBeamCharge(),   // 0..1 的填充进度
                settings.beamCoreColor,   // 填充色；null 时用原版 HUD 绿
                settings.beamFringeColor, // 边框色；null 时用原版 HUD 绿
                0f,                     // 第二段填充(类硬幅能)，0 即关闭
                "Beam charge",
                beam.getBeamCount());   // 右侧数字，0..999999
    }
}
