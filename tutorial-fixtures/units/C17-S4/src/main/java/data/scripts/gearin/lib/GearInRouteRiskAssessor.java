package data.scripts.gearin.lib;

import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.campaign.CampaignUtils;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;

public final class GearInRouteRiskAssessor {
    public float assess(SectorEntityToken waypoint, float radius) {
        float risk = 0f;
        for (CampaignFleetAPI fleet : CampaignUtils.getNearbyHostileFleets(waypoint, radius)) {
            float distance = Math.max(500f, MathUtils.getDistance(waypoint, fleet));
            risk += fleet.getFleetPoints() * radius / distance;
        }
        return risk;
    }
}
