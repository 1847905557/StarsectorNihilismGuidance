package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CustomCampaignEntityAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;

public class GearInLocationBuilder implements EveryFrameScript {

    public static final String REGISTER_MARK = "GearIn_LocationBuilder";

    private static final Logger LOG = Global.getLogger(GearInLocationBuilder.class);

    private boolean done;

    @Override
    public boolean isDone() {
        return done;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        if (done) {
            return;
        }

        SectorAPI sector = Global.getSector();
        StarSystemAPI system = sector.getStarSystem("GearIn_observation_system");
        if (system != null) {
            done = true;
            return;
        }
        system = sector.createStarSystem("GearIn_observation_system");
        PlanetAPI star = system.initStar(
                "GearIn_observation_star",
                "star_yellow",
                500f,
                250f,
                100f,
                3f,
                2f);
        system.getLocation().set(-12000f, 7000f);
        system.addPlanet(
                "GearIn_observation_planet",
                star,
                "观测行星",
                "terran",
                25f,
                140f,
                2400f,
                180f);
        CustomCampaignEntityAPI relay = system.addCustomEntity(
                "GearIn_observation_relay",
                "观测中继站",
                "comm_relay",
                Factions.INDEPENDENT);
        relay.addTag("GearIn_observation_tag");
        relay.setCircularOrbit(star, 70f, 3200f, 240f);
        SectorEntityToken marker = system.createToken(4200f, -900f);
        marker.setId("GearIn_observation_marker");
        marker.addTag("GearIn_observation_marker");
        system.addEntity(marker);
        system.autogenerateHyperspaceJumpPoints(false, true, true);
        LOG.info("GearIn location built: id=" + system.getId());
        done = true;
    }
}
