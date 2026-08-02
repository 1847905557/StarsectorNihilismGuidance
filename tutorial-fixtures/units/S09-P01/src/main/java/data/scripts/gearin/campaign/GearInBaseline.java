package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetDataAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;

/**
 * 基线快照（演示前置观察）：玩家舰队成员数/玩家势力/玩家人物，及玩家旗舰 getCaptain() 特例。
 */
public class GearInBaseline {

    private static final Logger LOG = Global.getLogger(GearInBaseline.class);

    /** 记录基线并返回初始成员数，供协调器在清理后对比。 */
    public int run(SectorAPI sector, FleetDataAPI fleetData) {
        int initialCount = fleetData.getNumMembers();
        LOG.info(String.format(
                "GearIn person lesson start: members=%d playerFaction=%s playerPerson=%s",
                initialCount, sector.getPlayerFaction().getId(), sector.getPlayerPerson().getNameString()));
        CampaignFleetAPI playerFleet = sector.getPlayerFleet();
        FleetMemberAPI flagship = playerFleet.getFlagship();
        boolean flagshipCaptainIsPlayer = flagship != null && flagship.getCaptain() == sector.getPlayerPerson();
        LOG.info(String.format(
                "GearIn person lesson flagship: name=%s captainIsPlayerPerson=%s",
                flagship == null ? "none" : flagship.getShipName(), flagshipCaptainIsPlayer));
        return initialCount;
    }
}
