package data.scripts.gearin.campaign;

import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.impl.items.BaseSpecialItemPlugin;
import com.fs.starfarer.api.campaign.econ.Industry;

public class GearInRefineryCorePlugin extends BaseSpecialItemPlugin {
    public static final String ITEM_ID = "GearIn_refinery_core";
    public static final String MOD_ID = "GearIn_refinery_core";

    @Override
    public void setId(String id) {
        super.setId(id);
    }

    @Override
    public void init(CargoStackAPI stack) {
        super.init(stack);
    }

    public void applyToIndustry(Industry industry) {
        if (industry == null || !"GearIn_element_works".equals(industry.getId())) return;
        industry.getSupply("rare_metals").getQuantity().modifyFlat(MOD_ID, 3f, getName());
    }

    public void unapplyFromIndustry(Industry industry) {
        if (industry == null || !"GearIn_element_works".equals(industry.getId())) return;
        industry.getSupply("rare_metals").getQuantity().unmodifyFlat(MOD_ID);
    }
}
