package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.ReputationActionResponsePlugin.ReputationAdjustmentResult;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.CustomRepImpact;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActionEnvelope;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActions;
import com.fs.starfarer.api.impl.campaign.ids.Factions;

public class GearInFactionRelationProbe implements EveryFrameScript {

    private static final Logger LOG = Global.getLogger(GearInFactionRelationProbe.class);

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
        FactionAPI player = sector.getPlayerFaction();
        FactionAPI target = sector.getFaction(Factions.HEGEMONY);
        String targetId = target.getId();

        float before = player.getRelationship(targetId);
        logRelation("before", player, target);

        player.adjustRelationship(targetId, 0.05f);
        float during = player.getRelationship(targetId);
        logRelation("direct", player, target);

        player.setRelationship(targetId, before);
        float after = player.getRelationship(targetId);
        logRelation("after", player, target);

        CustomRepImpact impact = new CustomRepImpact();
        impact.delta = 0.05f;
        RepActionEnvelope envelope = new RepActionEnvelope(RepActions.CUSTOM, impact);
        envelope.withMessage = false;
        ReputationAdjustmentResult reputationResult = sector.adjustPlayerReputation(
                envelope,
                targetId);
        float actionDuring = player.getRelationship(targetId);
        logRelation("action", player, target);
        player.setRelationship(targetId, before);
        float actionAfter = player.getRelationship(targetId);
        logRelation("actionRestored", player, target);
        LOG.info(String.format(
                "GearIn faction action: faction=%s delta=%.3f during=%.3f restored=%.3f",
                targetId,
                reputationResult.delta,
                actionDuring,
                actionAfter));
        LOG.info(String.format(
                "GearIn faction values: before=%.3f during=%.3f after=%.3f",
                before,
                during,
                after));
        done = true;
    }

    private void logRelation(String stage, FactionAPI source, FactionAPI target) {
        String targetId = target.getId();
        float forward = source.getRelationship(targetId);
        float reverse = target.getRelationship(source.getId());
        RepLevel level = source.getRelationshipLevel(targetId);
        boolean hostile = source.isHostileTo(targetId);
        LOG.info(String.format(
                "GearIn faction relation: stage=%s source=%s target=%s forward=%.3f reverse=%.3f level=%s hostile=%s",
                stage,
                source.getId(),
                targetId,
                forward,
                reverse,
                level,
                hostile));
    }
}
