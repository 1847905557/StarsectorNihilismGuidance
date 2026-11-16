package data.scripts.gearin.campaign;

import java.awt.Color;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithBarEvent;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

/** Aster 委托的调查：去 Ashen Verge 读取灰烬信标的记录，再回 Foundry 向她报告。只在 Foundry 发出，完成后不再出现。 */
public class GearInBeaconSurveyMission extends HubMissionWithBarEvent {

    /** 全局 Memory：这项委托已经完成过。 */
    public static final String COMPLETED_KEY = "$GearIn_bsv_completed";

    private static final int REWARD = 30000;

    public static enum Stage {
        SURVEY,
        REPORT,
        COMPLETED
    }

    @Override
    public boolean shouldShowAtMarket(MarketAPI market) {
        return GearInFoundryGenerator.MARKET_ID.equals(market.getId())
                && !Global.getSector().getMemoryWithoutUpdate().getBoolean(COMPLETED_KEY);
    }

    @Override
    protected boolean create(MarketAPI createdAt, boolean barEvent) {
        PersonAPI aster = Global.getSector().getImportantPeople().getPerson(GearInAsterContact.PERSON_ID);
        SectorEntityToken beacon = Global.getSector().getEntityById(GearInAshenVergeGenerator.BEACON_ID);
        if (aster == null || beacon == null) {
            return false;
        }
        setPersonOverride(aster);

        if (!setGlobalReference("$GearIn_bsv_ref", "$GearIn_bsv_inProgress")) {
            return false;
        }

        makeImportant(beacon, "$GearIn_bsv_target", Stage.SURVEY);
        makeImportant(aster, "$GearIn_bsv_report", Stage.REPORT);

        setStartingStage(Stage.SURVEY);
        setSuccessStage(Stage.COMPLETED);

        setStageOnMemoryFlag(Stage.REPORT, beacon, "$GearIn_bsv_surveyed");
        setStageOnMemoryFlag(Stage.COMPLETED, aster, "$GearIn_bsv_reported");

        setCreditReward(REWARD);
        return true;
    }

    @Override
    protected void updateInteractionDataImpl() {
        set("$GearIn_bsv_reward", Misc.getWithDGS(getCreditsReward()));
    }

    @Override
    public void addDescriptionForNonEndStage(TooltipMakerAPI info, float width, float height) {
        float opad = 10f;
        if (currentStage == Stage.SURVEY) {
            info.addPara("前往 Ashen Verge，读取灰烬信标的记录。", opad);
        } else if (currentStage == Stage.REPORT) {
            info.addPara("回到 Nipher Foundry，向 Aster Vance 报告信标的记录。", opad);
        }
    }

    @Override
    public boolean addNextStepText(TooltipMakerAPI info, Color tc, float pad) {
        if (currentStage == Stage.SURVEY) {
            info.addPara("读取 Ashen Verge 的灰烬信标", tc, pad);
            return true;
        }
        if (currentStage == Stage.REPORT) {
            info.addPara("向 Nipher Foundry 的 Aster Vance 报告", tc, pad);
            return true;
        }
        return false;
    }

    @Override
    public String getBaseName() {
        return "灰烬信标的记录";
    }
}
