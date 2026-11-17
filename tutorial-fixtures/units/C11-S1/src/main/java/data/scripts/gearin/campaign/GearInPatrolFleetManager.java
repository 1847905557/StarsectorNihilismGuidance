package data.scripts.gearin.campaign;

import java.util.Random;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.fleets.SourceBasedFleetManager;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;

import data.scripts.gearin.GearInModPlugin;

/** 齿轮工业巡逻舰队管理器：从源点派出，在所在星系巡逻，被击毁后按间隔补充。 */
public class GearInPatrolFleetManager extends SourceBasedFleetManager {

    /** 玩家离源点超过这个距离 (光年) 时，数量降到最小值。 */
    private static final float THRESHOLD_LY = 3f;

    private static final int MIN_FLEETS = 1;
    private static final int MAX_FLEETS = 2;

    /** 被击毁后补充一支舰队所需的生涯天数。 */
    private static final float RESPAWN_DAYS = 20f;

    private static final float COMBAT_POINTS = 30f;

    public GearInPatrolFleetManager(SectorEntityToken source) {
        super(source, THRESHOLD_LY, MIN_FLEETS, MAX_FLEETS, RESPAWN_DAYS);
    }

    @Override
    protected CampaignFleetAPI spawnFleet() {
        if (source == null) {
            return null;
        }
        Random random = new Random();
        FleetParamsV3 params = new FleetParamsV3(
                null,
                source.getLocationInHyperspace(),
                GearInModPlugin.FACTION_ID,
                1f,
                FleetTypes.PATROL_SMALL,
                COMBAT_POINTS,
                0f, 0f, 0f, 0f, 0f,
                0f);
        params.random = random;
        CampaignFleetAPI fleet = FleetFactoryV3.createFleet(params);
        if (fleet == null || fleet.isEmpty()) {
            return null;
        }

        source.getContainingLocation().addEntity(fleet);
        fleet.setLocation(source.getLocation().x, source.getLocation().y);
        fleet.setFacing(random.nextFloat() * 360f);

        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_PATROL_FLEET, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_ALLOW_LONG_PURSUIT, false);
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_NO_JUMP, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_HOLD_VS_STRONGER, true);

        fleet.addScript(new GearInPatrolAssignmentScript(fleet, source));
        return fleet;
    }
}
