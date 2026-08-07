package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FleetDataAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;

/**
 * 舰长(captain)：默认舰长读取(steady/空名/level 1)、setCaptain 交接与 getMemberWithCaptain 反查。
 */
public class GearInCaptain {

    private static final Logger LOG = Global.getLogger(GearInCaptain.class);

    public void run(FleetMemberAPI wolf, PersonAPI personA, FleetDataAPI fleetData) {
        PersonAPI defaultCaptain = wolf.getCaptain();
        LOG.info(String.format(
                "GearIn person lesson captain default: personality=%s name='%s' level=%d",
                defaultCaptain.getPersonalityAPI().getId(), defaultCaptain.getNameString(),
                defaultCaptain.getStats().getLevel()));
        wolf.setCaptain(personA);
        LOG.info(String.format(
                "GearIn person lesson captain set: sameCaptain=%s memberWithCaptainFound=%s",
                wolf.getCaptain() == personA, fleetData.getMemberWithCaptain(personA) == wolf));
    }
}
