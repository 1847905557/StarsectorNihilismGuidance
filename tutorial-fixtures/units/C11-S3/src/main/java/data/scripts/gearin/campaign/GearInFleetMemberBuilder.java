package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetDataAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;

/**
 * 创建一艘生涯舰队成员，并可选择把它设为旗舰。
 */
public class GearInFleetMemberBuilder implements EveryFrameScript {

    public static final String REGISTER_MARK = "GearIn_FleetMemberBuilder";

    private static final Logger LOG = Global.getLogger(GearInFleetMemberBuilder.class);

    private static final String VARIANT_ID = "wolf_Assault";

    private boolean done;

    /**
     * 创建成员、加入舰队并返回创建结果。
     *
     * @param fleet 目标舰队
     * @param variantId 船体装配 ID
     * @param makeFlagship 是否把新成员设为旗舰
     * @return 已加入目标舰队的成员
     */
    public static FleetMemberAPI createMember(
            CampaignFleetAPI fleet,
            String variantId,
            boolean makeFlagship) {
        FleetDataAPI data = fleet.getFleetData();
        FleetMemberAPI member = Global.getFactory().createFleetMember(
                FleetMemberType.SHIP,
                variantId);
        data.addFleetMember(member);

        if (makeFlagship) {
            data.setFlagship(member);
        }

        return member;
    }

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
        done = true;

        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        FleetMemberAPI member = createMember(
                fleet,
                VARIANT_ID,
                false);

        LOG.info("GearIn member builder: id=" + member.getId()
                + " hull=" + member.getHullId()
                + " variant=" + member.getVariant().getHullVariantId()
                + " fleet=" + fleet.getName()
                + " members=" + fleet.getFleetData().getNumMembers()
                + " flagship=" + member.isFlagship());
    }
}
