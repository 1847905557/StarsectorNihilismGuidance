package data.scripts.gearin.campaign;

import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.InstallableIndustryItemPlugin.InstallableItemDescriptionMode;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseInstallableItemEffect;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

/** 精炼核心装入 Element Works 后，产出提高 1 档。效果登记在静态表中，不保存任何生涯对象的引用。 */
public class GearInRefineryCoreEffect extends BaseInstallableItemEffect {

    public static final String ITEM_ID = "GearIn_refinery_core";

    private static final int SUPPLY_BONUS = 1;

    public GearInRefineryCoreEffect() {
        super(ITEM_ID);
    }

    @Override
    public void apply(Industry industry) {
        industry.getSupplyBonus().modifyFlat(spec.getId(), SUPPLY_BONUS, spec.getName());
    }

    @Override
    public void unapply(Industry industry) {
        industry.getSupplyBonus().unmodifyFlat(spec.getId());
    }

    @Override
    protected void addItemDescriptionImpl(Industry industry, TooltipMakerAPI text, SpecialItemData data,
            InstallableItemDescriptionMode mode, String pre, float pad) {
        text.addPara(pre + "Increases Element Works output by %s unit.",
                pad, Misc.getHighlightColor(), "" + SUPPLY_BONUS);
    }
}
