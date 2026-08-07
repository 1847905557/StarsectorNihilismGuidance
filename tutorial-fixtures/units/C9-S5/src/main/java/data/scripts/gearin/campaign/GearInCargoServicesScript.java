package data.scripts.gearin.campaign;

import java.util.List;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;

/**
 * 完整最小例(货物段)：CargoAPI 常用入口演示脚本。
 *
 * 注册入口：onGameLoad(调用点片段见页面正文)，persistent data 键 REGISTER_MARK 一次性门控。
 *
 * 行为：读取玩家舰队补给/燃料/陆战队员/信用/空间/栈数基线；addSupplies(100)/addFuel(50)/addMarines(10)
 * 后读取中间值，再 remove 同量回滚并读取终值(验证数值精确复原)；读取重型机械商品量与栈列表
 * 前几条(CargoStackAPI 的 type/data/size/isNull)。不遗留持久状态。
 */
public class GearInCargoServicesScript extends GearInOneShotScript {

    /** 存档持久化标记：本脚本是否已在该存档执行过(随存档保存)。 */
    public static final String REGISTER_MARK = "GearIn_CargoServices";

    private static final Logger LOG = Global.getLogger(GearInCargoServicesScript.class);

    @Override
    protected void runDemo() {
        SectorAPI sector = Global.getSector();
        CargoAPI cargo = sector.getPlayerFleet().getCargo();
        float suppliesBefore = cargo.getSupplies();
        float fuelBefore = cargo.getFuel();
        int marinesBefore = cargo.getMarines();
        float creditsBefore = cargo.getCredits().get();
        float spaceBefore = cargo.getSpaceUsed();
        float capacityBefore = cargo.getMaxCapacity();
        int stacksBefore = cargo.getStacksCopy().size();
        float machineryBefore = cargo.getCommodityQuantity(Commodities.HEAVY_MACHINERY);

        cargo.addSupplies(100f);
        cargo.addFuel(50f);
        cargo.addMarines(10);
        int stacksDuring = cargo.getStacksCopy().size();
        float suppliesDuring = cargo.getSupplies();
        float fuelDuring = cargo.getFuel();
        int marinesDuring = cargo.getMarines();

        cargo.removeSupplies(100f);
        cargo.removeFuel(50f);
        cargo.removeMarines(10);
        int stacksAfter = cargo.getStacksCopy().size();
        int nullStacksAfter = 0;
        for (CargoStackAPI stack : cargo.getStacksCopy()) {
            if (stack.isNull()) {
                nullStacksAfter++;
            }
        }
        float suppliesAfter = cargo.getSupplies();
        float fuelAfter = cargo.getFuel();
        int marinesAfter = cargo.getMarines();

        LOG.info(String.format(
                "GearIn campaign services cargo write-rollback: supplies %.0f -> %.0f -> %.0f | fuel %.0f -> %.0f -> %.0f | marines %d -> %d -> %d | machinery %.0f | credits %.0f | space %.0f/%.0f",
                suppliesBefore, suppliesDuring, suppliesAfter, fuelBefore, fuelDuring, fuelAfter,
                marinesBefore, marinesDuring, marinesAfter, machineryBefore, creditsBefore, spaceBefore, capacityBefore));
        LOG.info(String.format("GearIn campaign services cargo stacks: before=%d during=%d after=%d nullAfter=%d",
                stacksBefore, stacksDuring, stacksAfter, nullStacksAfter));

        // 栈列表前三条(CargoStackAPI：type / data / size / isNull)
        List<CargoStackAPI> stacks = cargo.getStacksCopy();
        int stacksShown = Math.min(3, stacks.size());
        for (int i = 0; i < stacksShown; i++) {
            CargoStackAPI stack = stacks.get(i);
            LOG.info(String.format("GearIn campaign services cargo stack[%d]: type=%s data=%s size=%.0f null=%s",
                    i, stack.getType(), stack.getData(), stack.getSize(), stack.isNull()));
        }
    }
}
