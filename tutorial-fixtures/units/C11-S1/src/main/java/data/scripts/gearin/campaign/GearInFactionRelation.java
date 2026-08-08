package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SectorAPI;

/**
 * 势力间关系 (FactionAPI 关系族)：setRelationship / adjustRelationship / ensureAtWorst 确定性序列 + 同帧回滚。
 */
public class GearInFactionRelation {

    private static final Logger LOG = Global.getLogger(GearInFactionRelation.class);

    public void run(SectorAPI sector) {
        FactionAPI hegemony = sector.getFaction("hegemony");
        FactionAPI tritachyon = sector.getFaction("tritachyon");
        float originalRel = hegemony.getRelationship("tritachyon");
        hegemony.setRelationship("tritachyon", 0f);
        LOG.info(String.format(
                "GearIn person lesson factionRel start: original=%.2f normalized=%.2f level=%s",
                originalRel, hegemony.getRelationship("tritachyon"),
                hegemony.getRelationshipLevel("tritachyon")));
        hegemony.adjustRelationship("tritachyon", 0.15f);
        LOG.info(String.format(
                "GearIn person lesson factionRel adjust: value=%.2f level=%s",
                hegemony.getRelationship("tritachyon"), hegemony.getRelationshipLevel("tritachyon")));
        boolean cappedChanged = hegemony.adjustRelationship("tritachyon", 0.9f, RepLevel.COOPERATIVE);
        LOG.info(String.format(
                "GearIn person lesson factionRel cap: value=%.2f level=%s changed=%s",
                hegemony.getRelationship("tritachyon"), hegemony.getRelationshipLevel("tritachyon"), cappedChanged));
        hegemony.setRelationship("tritachyon", RepLevel.HOSTILE);
        LOG.info(String.format(
                "GearIn person lesson factionRel hostile: value=%.2f level=%s hostileTo=%s",
                hegemony.getRelationship("tritachyon"), hegemony.getRelationshipLevel("tritachyon"),
                hegemony.isHostileTo("tritachyon")));
        hegemony.adjustRelationship("tritachyon", -0.2f, RepLevel.VENGEFUL);
        LOG.info(String.format(
                "GearIn person lesson factionRel vengefulCap: value=%.2f level=%s",
                hegemony.getRelationship("tritachyon"), hegemony.getRelationshipLevel("tritachyon")));
        boolean ensured = hegemony.ensureAtWorst("tritachyon", RepLevel.WELCOMING);
        LOG.info(String.format(
                "GearIn person lesson factionRel ensure: value=%.2f level=%s ensured=%s",
                hegemony.getRelationship("tritachyon"), hegemony.getRelationshipLevel("tritachyon"), ensured));
        hegemony.setRelationship("tritachyon", originalRel);
        LOG.info(String.format(
                "GearIn person lesson factionRel restore: value=%.2f level=%s",
                hegemony.getRelationship("tritachyon"), hegemony.getRelationshipLevel("tritachyon")));
    }
}
