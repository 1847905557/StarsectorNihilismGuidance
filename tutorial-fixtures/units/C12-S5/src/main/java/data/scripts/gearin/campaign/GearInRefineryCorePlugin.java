package data.scripts.gearin.campaign;

import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.impl.items.BaseSpecialItemPlugin;

public class GearInRefineryCorePlugin extends BaseSpecialItemPlugin {
    public static final String ITEM_ID = "GearIn_refinery_core";
    public static final String MOD_ID = "GearIn_refinery_core";

    public void applyToIndustry(Industry industry) {
        if (!"GearIn_element_works".equals(industry.getId())) {
            return;
        }
        industry.getSupply("rare_metals").getQuantity().modifyFlat(MOD_ID, 3f, getName());
    }

    public void unapplyFromIndustry(Industry industry) {
        if (!"GearIn_element_works".equals(industry.getId())) {
            return;
        }
        industry.getSupply("rare_metals").getQuantity().unmodifyFlat(MOD_ID);
    }
}
