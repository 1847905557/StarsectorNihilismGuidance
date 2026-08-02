package data.scripts.gearin.lib;

import java.awt.Color;
import java.util.List;

import org.apache.log4j.Logger;
import org.magiclib.util.MagicSettings;

import com.fs.starfarer.api.Global;

/** 设置接入（MagicSettings）：从 data/config/modSettings.json 的 "gear_in" 段读取
 * 光束与 HUD 配置，构造时缓存一次、不在每帧解析（MagicSettings javadoc 建议）。
 * 键缺失返回类型缺省值（false / 0 / Color.RED / 空集合）并写 warn 日志，不抛
 * 异常——缺省值会参与后续运行语义，业务开关不要依赖缺省值。 */
public class GearInMagicSettings {

    private static final Logger LOG = Global.getLogger(GearInMagicSettings.class);

    /** data/config/modSettings.json 中本 MOD 的段名（= MOD id "gear_in"）。 */
    private static final String CONFIG_SECTION = "gear_in";

    /** HUD 状态条总开关。 */
    public final boolean hudEnabled;
    /** 光束宽度 / 射程 / 伤害 / EMP（数值键缺省 0）。 */
    public final float beamWidth;
    public final float beamRange;
    public final float beamDamage;
    public final float beamEmp;
    /** 全亮与淡出时长（秒）：只控制视觉，不影响伤害结算。 */
    public final float beamFull;
    public final float beamFading;
    /** 命中光斑尺寸。 */
    public final float beamImpactSize;
    /** 光束核心 / 边缘颜色（缺省 Color.RED）。 */
    public final Color beamCoreColor;
    public final Color beamFringeColor;
    /** 光束标签列表（缺省空集合）。 */
    public final List<String> beamTags;

    public GearInMagicSettings() {
        hudEnabled = MagicSettings.getBoolean(CONFIG_SECTION, "hud_enabled");
        beamWidth = MagicSettings.getFloat(CONFIG_SECTION, "beam_width");
        beamRange = MagicSettings.getFloat(CONFIG_SECTION, "beam_range");
        beamDamage = MagicSettings.getFloat(CONFIG_SECTION, "beam_damage");
        beamEmp = MagicSettings.getFloat(CONFIG_SECTION, "beam_emp");
        beamFull = MagicSettings.getFloat(CONFIG_SECTION, "beam_full");
        beamFading = MagicSettings.getFloat(CONFIG_SECTION, "beam_fading");
        beamImpactSize = MagicSettings.getFloat(CONFIG_SECTION, "beam_impact_size");
        beamCoreColor = MagicSettings.getColorRGB(CONFIG_SECTION, "beam_color_core");
        beamFringeColor = MagicSettings.getColorRGB(CONFIG_SECTION, "beam_color_fringe");
        beamTags = MagicSettings.getList(CONFIG_SECTION, "beam_tags");
        LOG.info("GearIn magic settings: hud=" + hudEnabled
                + " width=" + beamWidth + " range=" + beamRange
                + " damage=" + beamDamage + " emp=" + beamEmp
                + " full=" + beamFull + " fading=" + beamFading
                + " impact=" + beamImpactSize
                + " core=(" + beamCoreColor.getRed() + "," + beamCoreColor.getGreen() + "," + beamCoreColor.getBlue() + ")"
                + " tags=" + beamTags);
    }
}
