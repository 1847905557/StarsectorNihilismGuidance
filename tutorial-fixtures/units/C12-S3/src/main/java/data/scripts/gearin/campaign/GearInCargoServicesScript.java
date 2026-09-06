package data.scripts.gearin.campaign;

import java.util.List;
import org.apache.log4j.Logger;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;

public class GearInCargoServicesScript implements EveryFrameScript {
    public static final String REGISTER_MARK = "GearIn_CargoServices";
    private static final Logger LOG = Global.getLogger(GearInCargoServicesScript.class);
    private boolean done;
    @Override public boolean isDone() { return done; }
    @Override public boolean runWhilePaused() { return false; }
    @Override public void advance(float amount) {
        if (done) return;
        SectorAPI sector = Global.getSector();
        CargoAPI cargo = sector.getPlayerFleet().getCargo();
        float suppliesBefore = cargo.getSupplies();
        float fuelBefore = cargo.getFuel();
        int marinesBefore = cargo.getMarines();
        float machineryBefore = cargo.getCommodityQuantity(Commodities.HEAVY_MACHINERY);
        cargo.addSupplies(100f); cargo.addFuel(50f); cargo.addMarines(10);
        float suppliesDuring = cargo.getSupplies();
        cargo.removeSupplies(100f); cargo.removeFuel(50f); cargo.removeMarines(10);
        int nullStacks = 0;
        List<CargoStackAPI> stacks = cargo.getStacksCopy();
        for (CargoStackAPI stack : stacks) if (stack.isNull()) nullStacks++;
        LOG.info(String.format("GearIn cargo write-rollback: supplies %.0f -> %.0f -> %.0f | fuel %.0f -> %.0f -> %.0f | marines %d -> %d -> %d | machinery %.0f | nullStacks=%d",
                suppliesBefore, suppliesDuring, cargo.getSupplies(), fuelBefore, cargo.getFuel(), cargo.getFuel(),
                marinesBefore, cargo.getMarines(), cargo.getMarines(), machineryBefore, nullStacks));
        done = true;
    }
}
