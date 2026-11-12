package data.scripts.gearin.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.SpecialItemPlugin;
import com.fs.starfarer.api.campaign.SpecialItemSpecAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

public class GearInSpecialItemProbe {
    public void log(MarketAPI market) {
        inspectSpec("GearIn_refinery_core");
        inspectSpec("GearIn_salvage_token");

        CargoAPI cargo = Global.getFactory().createCargo(true);
        SpecialItemData coreData = new SpecialItemData("GearIn_refinery_core", "batch-01");
        SpecialItemData tokenData = new SpecialItemData("GearIn_salvage_token", "salvage-cache");
        cargo.addSpecial(coreData, 1f);
        cargo.addSpecial(tokenData, 1f);

        for (CargoStackAPI stack : cargo.getStacksCopy()) {
            if (stack.isSpecialStack()) {
                SpecialItemData data = stack.getSpecialDataIfSpecial();
                SpecialItemPlugin plugin = stack.getPlugin();
                Global.getLogger(GearInSpecialItemProbe.class).info(
                        "special=" + data.getId() + ", data=" + data.getData() +
                        ", plugin=" + plugin.getClass().getSimpleName());
            }
        }

        Industry industry = market.getIndustry("GearIn_element_works");
        SpecialItemData previous = industry.getSpecialItem();
        SpecialItemSpecAPI coreSpec = Global.getSettings().getSpecialItemSpec(coreData.getId());
        GearInRefineryCorePlugin corePlugin = (GearInRefineryCorePlugin) coreSpec.getNewPluginInstance(null);
        float before = industry.getSupply("rare_metals").getQuantity().getModifiedValue();
        float during;
        try {
            industry.setSpecialItem(coreData);
            corePlugin.applyToIndustry(industry);
            during = industry.getSupply("rare_metals").getQuantity().getModifiedValue();
        } finally {
            corePlugin.unapplyFromIndustry(industry);
            industry.setSpecialItem(previous);
        }
        float after = industry.getSupply("rare_metals").getQuantity().getModifiedValue();
        Global.getLogger(GearInSpecialItemProbe.class).info(
                "rare_metals before=" + before + ", during=" + during + ", after=" + after);
    }

    private void inspectSpec(String id) {
        SpecialItemSpecAPI spec = Global.getSettings().getSpecialItemSpec(id);
        if (spec == null) {
            Global.getLogger(GearInSpecialItemProbe.class).warn("missing special item spec=" + id);
            return;
        }
        Global.getLogger(GearInSpecialItemProbe.class).info(
                "spec=" + spec.getId() + ", name=" + spec.getName() +
                ", price=" + spec.getBasePrice() + ", stack=" + spec.getStackSize() +
                ", space=" + spec.getCargoSpace() + ", tags=" + spec.getTags());
    }

}
