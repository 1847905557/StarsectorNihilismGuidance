package data.scripts.gearin.rules;

import org.lwjgl.util.vector.Vector2f;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;

/** Writes typed Memory values and observes an expire-zero key. */
public class GearInMemoryWriter implements EveryFrameScript {

    private static final Logger LOG = Global.getLogger(GearInMemoryWriter.class);
    private int phase = 0;

    @Override
    public boolean isDone() {
        return phase >= 2;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        if (Global.getSector() == null || phase >= 2) {
            return;
        }
        MemoryAPI sector = Global.getSector().getMemoryWithoutUpdate();
        if (phase == 0) {
            writeValues(sector);
            phase = 1;
            return;
        }
        if (sector.contains(GearInMemoryKeys.KEY_PROBE)) {
            return;
        }
        sector.set(GearInMemoryKeys.KEY_READY, true);
        LOG.info("GearInMemoryWriter: expire-zero probe removed; keys=" + sector.getKeys());
        phase = 2;
    }

    private void writeValues(MemoryAPI sector) {
        sector.set(GearInMemoryKeys.KEY_GLOBAL, "sector-ready");
        sector.set(GearInMemoryKeys.KEY_NUMBER, 2.5f, 30f);
        sector.set(GearInMemoryKeys.KEY_INTEGER, 3);
        sector.set(GearInMemoryKeys.KEY_LONG, 4L);
        sector.set(GearInMemoryKeys.KEY_VECTOR, new Vector2f(10f, 20f));
        sector.set(GearInMemoryKeys.KEY_PROBE, true, 0f);
        sector.set(GearInMemoryKeys.KEY_REARM, "temporary", 10f);
        sector.expire(GearInMemoryKeys.KEY_REARM, -1f);

        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        if (fleet != null) {
            sector.set(GearInMemoryKeys.KEY_FLEET, fleet);
            sector.set(GearInMemoryKeys.KEY_OBJECT, fleet);
            fleet.getMemoryWithoutUpdate().set(GearInMemoryKeys.KEY_ENTITY, "fleet-ready");
        }
        Global.getSector().getCharacterData().getMemoryWithoutUpdate().set(
                GearInMemoryKeys.KEY_PLAYER, true);

        PersonAPI person = Global.getSector().getPlayerPerson();
        person.getMemoryWithoutUpdate().set(GearInMemoryKeys.KEY_PERSON, "person-ready");
        FactionAPI faction = person.getFaction();
        faction.getMemoryWithoutUpdate().set(
                GearInMemoryKeys.KEY_FACTION, "person-faction-ready");

        MarketAPI market = Global.getSector().getEconomy().getMarket("jangala");
        if (market != null && market.getPrimaryEntity() != null) {
            market.getMemoryWithoutUpdate().set(GearInMemoryKeys.KEY_MARKET, "market-ready");
            market.getFaction().getMemoryWithoutUpdate().set(
                    GearInMemoryKeys.KEY_FACTION, "faction-ready");
            SectorEntityToken entity = market.getPrimaryEntity();
            entity.getMemoryWithoutUpdate().set(GearInMemoryKeys.KEY_ENTITY, "entity-ready");
            entity.getMemoryWithoutUpdate().set(GearInMemoryKeys.KEY_SOURCE_MARKET, "jangala");
        }

        MemoryAPI scratch = Global.getFactory().createMemory();
        scratch.set("$temporary", true);
        scratch.unset("$temporary");
        scratch.set("$clearMe", true);
        scratch.clear();

        Global.getSector().getMemory();
        LOG.info("GearInMemoryWriter: string=" + sector.getString(GearInMemoryKeys.KEY_GLOBAL)
                + " number=" + sector.getFloat(GearInMemoryKeys.KEY_NUMBER)
                + " int=" + sector.getInt(GearInMemoryKeys.KEY_INTEGER)
                + " long=" + sector.getLong(GearInMemoryKeys.KEY_LONG)
                + " vector=" + sector.getVector2f(GearInMemoryKeys.KEY_VECTOR));
        LOG.info("GearInMemoryWriter: contains=" + sector.contains(GearInMemoryKeys.KEY_GLOBAL)
                + " is=" + sector.is(GearInMemoryKeys.KEY_PROBE, true)
                + " between=" + sector.between(GearInMemoryKeys.KEY_NUMBER, 2f, 3f)
                + " entity=" + sector.getEntity(GearInMemoryKeys.KEY_FLEET)
                + " fleet=" + sector.getFleet(GearInMemoryKeys.KEY_FLEET)
                + " object=" + sector.get(GearInMemoryKeys.KEY_OBJECT));
        LOG.info("GearInMemoryWriter: probeExpire=" + sector.getExpire(GearInMemoryKeys.KEY_PROBE)
                + " rearmExpire=" + sector.getExpire(GearInMemoryKeys.KEY_REARM)
                + " scratchEmpty=" + scratch.isEmpty());
    }
}
