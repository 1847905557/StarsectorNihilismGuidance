package data.scripts.gearin.campaign;

import java.awt.Color;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.characters.CharacterStatsSkillEffect;
import com.fs.starfarer.api.characters.DescriptionSkillEffect;
import com.fs.starfarer.api.characters.FleetStatsSkillEffect;
import com.fs.starfarer.api.characters.FleetTotalItem;
import com.fs.starfarer.api.characters.FleetTotalSource;
import com.fs.starfarer.api.characters.LevelBasedEffect.ScopeDescription;
import com.fs.starfarer.api.characters.MutableCharacterStatsAPI;
import com.fs.starfarer.api.characters.ShipSkillEffect;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.fleet.MutableFleetStatsAPI;
import com.fs.starfarer.api.impl.campaign.skills.BaseSkillEffectDescription;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipCreator;
import com.fs.starfarer.api.util.Misc;

/**
 * 完整最小例：SkillSpecAPI、效果作用域、技能等级与 fleet total。
 * 本类只是效果脚本的容器 (效果类必须无参可实例化)：.skill 文件与 skill_data.csv
 * 把每个 effect 的 type 分派到下面的嵌套类，type 决定作用对象集合 (与接口一一对应)。
 * 等级只有 0/1/2 (UI 加点钳制到 1/2，setSkillLevel 不钳制)，effectGroup 的
 * requiredSkillLevel 是生效门槛 (默认 governingSkill = 本技能 id)；apply() 收到的
 * level 是当前等级而非增量，引擎组合出的 effect id 必须原样用作 modifier id；
 * Level1 同时实现 FleetTotalSource，整备界面展示一行 fleet total 汇总。
 */
public class GearInGunnerySkill {

    private static final Logger LOG = Global.getLogger(GearInGunnerySkill.class);

    /** 数据侧技能 id(skill_data.csv 行 + GearIn_gunnery.skill 的 id 字段，三者必须逐字一致)。 */
    public static final String SKILL_ID = "GearIn_gunnery";

    /** 数值常量：与 .skill 的 effectGroups、skill_data.csv 行、页面描述文本保持一致。 */
    public static final float DAMAGE_BONUS = 15f;
    public static final float DAMAGE_BONUS_ELITE = 5f;
    public static final float AIM_BONUS = 100f;
    public static final float FIGHTER_SPEED_BONUS = 10f;
    public static final float FLEET_BURN_BONUS = 2f;
    public static final float COMMAND_POINTS_BONUS = 1f;

    /** DESCRIPTION：纯文本行；数值写入由其它 effect type 承担。 */
    public static class Level0 implements DescriptionSkillEffect {
        @Override
        public Color getTextColor() {
            return null;
        }

        @Override
        public String getString() {
            return "Precision gunnery drills: ballistic and energy weapon damage up by 15%, "
                    + "autofire accuracy up by 100%, fighters' top speed up by 10%, "
                    + "fleet burn up by 2, and command points up by 1.";
        }

        @Override
        public String[] getHighlights() {
            return new String[] {"15%", "100%", "10%", "2", "1"};
        }

        @Override
        public Color[] getHighlightColors() {
            Color h = Misc.getHighlightColor();
            return new Color[] {h, h, h, h, h};
        }
    }

    /** SHIP：被驾驶舰船 (角色为舰长 / 指挥官时作用于其旗舰)，同时提供 fleet total 行。 */
    public static class Level1 extends BaseSkillEffectDescription implements ShipSkillEffect, FleetTotalSource {

        @Override
        public void apply(MutableShipStatsAPI stats, HullSize hullSize, String id, float level) {
            stats.getBallisticWeaponDamageMult().modifyPercent(id, DAMAGE_BONUS);
            stats.getEnergyWeaponDamageMult().modifyPercent(id, DAMAGE_BONUS);
            stats.getAutofireAimAccuracy().modifyFlat(id, AIM_BONUS * 0.01f);
            LOG.info(String.format("GearIn gunnery ship apply: id=%s level=%.1f hull=%s damage=+%.0f%% aim=+%.0f%%",
                    id, level, hullSize, DAMAGE_BONUS, AIM_BONUS));
        }

        @Override
        public void unapply(MutableShipStatsAPI stats, HullSize hullSize, String id) {
            stats.getBallisticWeaponDamageMult().unmodify(id);
            stats.getEnergyWeaponDamageMult().unmodify(id);
            stats.getAutofireAimAccuracy().unmodify(id);
        }

        @Override
        public String getEffectDescription(float level) {
            return "+" + (int) DAMAGE_BONUS + "% ballistic and energy weapon damage, "
                    + "+" + (int) AIM_BONUS + "% autofire accuracy";
        }

        @Override
        public String getEffectPerLevelDescription() {
            return null;
        }

        @Override
        public ScopeDescription getScopeDescription() {
            return ScopeDescription.PILOTED_SHIP;
        }

        /** fleet total：整备界面 (refit) 的舰队构成表按 label+value 去重后展示这一行。 */
        @Override
        public FleetTotalItem getFleetTotalItem() {
            FleetTotalItem item = new FleetTotalItem();
            item.label = "Gunnery damage bonus";
            item.value = "+" + (int) DAMAGE_BONUS + "%";
            item.sortOrder = 50;
            item.tooltipCreator = new TooltipCreator() {
                @Override
                public boolean isTooltipExpandable(Object tooltipParam) {
                    return false;
                }

                @Override
                public float getTooltipWidth(Object tooltipParam) {
                    return TOOLTIP_WIDTH;
                }

                @Override
                public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                    tooltip.addPara("Ballistic and energy weapon damage bonus granted to the piloted ship "
                            + "while this skill is learned.", 0f);
                }
            };
            return item;
        }
    }

    /** SHIP(精英组)：技能到 2 级 (精英) 后追加的加成。 */
    public static class Level1A extends BaseSkillEffectDescription implements ShipSkillEffect {

        @Override
        public void apply(MutableShipStatsAPI stats, HullSize hullSize, String id, float level) {
            stats.getBallisticWeaponDamageMult().modifyPercent(id, DAMAGE_BONUS_ELITE);
            stats.getEnergyWeaponDamageMult().modifyPercent(id, DAMAGE_BONUS_ELITE);
            LOG.info(String.format("GearIn gunnery elite apply: id=%s level=%.1f hull=%s damage=+%.0f%%",
                    id, level, hullSize, DAMAGE_BONUS_ELITE));
        }

        @Override
        public void unapply(MutableShipStatsAPI stats, HullSize hullSize, String id) {
            stats.getBallisticWeaponDamageMult().unmodify(id);
            stats.getEnergyWeaponDamageMult().unmodify(id);
        }

        @Override
        public String getEffectDescription(float level) {
            return "+" + (int) DAMAGE_BONUS_ELITE + "% ballistic and energy weapon damage";
        }

        @Override
        public String getEffectPerLevelDescription() {
            return null;
        }

        @Override
        public ScopeDescription getScopeDescription() {
            return ScopeDescription.PILOTED_SHIP;
        }
    }

    /** SHIP_FIGHTERS：本舰 (被驾驶舰船) 起飞的战机，起飞时由 FighterLaunchBayAPI 应用。 */
    public static class LevelShipFighters extends BaseSkillEffectDescription implements ShipSkillEffect {

        @Override
        public void apply(MutableShipStatsAPI stats, HullSize hullSize, String id, float level) {
            stats.getMaxSpeed().modifyPercent(id, FIGHTER_SPEED_BONUS);
            LOG.info(String.format("GearIn gunnery fighters apply: id=%s level=%.1f hull=%s speed=+%.0f%%",
                    id, level, hullSize, FIGHTER_SPEED_BONUS));
        }

        @Override
        public void unapply(MutableShipStatsAPI stats, HullSize hullSize, String id) {
            stats.getMaxSpeed().unmodify(id);
        }

        @Override
        public String getEffectDescription(float level) {
            return "+" + (int) FIGHTER_SPEED_BONUS + "% top speed for fighters launched by the piloted ship";
        }

        @Override
        public String getEffectPerLevelDescription() {
            return null;
        }

        @Override
        public ScopeDescription getScopeDescription() {
            return ScopeDescription.SHIP_FIGHTERS;
        }
    }

    /** FLEET：角色指挥舰队的 MutableFleetStatsAPI，由 refreshCharacterStatsEffects 统一重放。 */
    public static class LevelFleet extends BaseSkillEffectDescription implements FleetStatsSkillEffect {

        @Override
        public void apply(MutableFleetStatsAPI stats, String id, float level) {
            stats.getFleetwideMaxBurnMod().modifyFlat(id, FLEET_BURN_BONUS);
            LOG.info(String.format("GearIn gunnery fleet apply: id=%s level=%.1f burn=+%.0f",
                    id, level, FLEET_BURN_BONUS));
        }

        @Override
        public void unapply(MutableFleetStatsAPI stats, String id) {
            stats.getFleetwideMaxBurnMod().unmodifyFlat(id);
        }

        @Override
        public String getEffectDescription(float level) {
            return "+" + (int) FLEET_BURN_BONUS + " fleet burn level";
        }

        @Override
        public String getEffectPerLevelDescription() {
            return null;
        }

        @Override
        public ScopeDescription getScopeDescription() {
            return ScopeDescription.FLEET;
        }
    }

    /** CHARACTER_STATS：角色自身数值，由 refreshCharacterStatsEffects 统一重放。 */
    public static class LevelStats extends BaseSkillEffectDescription implements CharacterStatsSkillEffect {

        @Override
        public void apply(MutableCharacterStatsAPI stats, String id, float level) {
            stats.getCommandPoints().modifyFlat(id, COMMAND_POINTS_BONUS);
            LOG.info(String.format("GearIn gunnery stats apply: id=%s level=%.1f commandPoints=+%.0f",
                    id, level, COMMAND_POINTS_BONUS));
        }

        @Override
        public void unapply(MutableCharacterStatsAPI stats, String id) {
            stats.getCommandPoints().unmodify(id);
        }

        @Override
        public String getEffectDescription(float level) {
            return "+" + (int) COMMAND_POINTS_BONUS + " command point";
        }

        @Override
        public String getEffectPerLevelDescription() {
            return null;
        }

        @Override
        public ScopeDescription getScopeDescription() {
            return ScopeDescription.FLEET;
        }
    }
}
