package data.scripts.gearin.rules;

import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc.Token;

import data.scripts.gearin.campaign.GearInPatrolGrudgeListener;

/**
 * GearInSettleGrudge &lt;每支巡逻队的价格&gt;
 * 玩家付钱请 Aster 撤掉敌意状态：价格按累计击败的巡逻队数量计算，付得起就清除敌意标记。
 */
public class GearInSettleGrudge extends BaseCommandPlugin {

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params,
            Map<String, MemoryAPI> memoryMap) {
        if (dialog == null || params.isEmpty()) {
            return false;
        }
        float pricePerPatrol = params.get(0).getFloat(memoryMap);
        MemoryAPI global = Global.getSector().getMemoryWithoutUpdate();
        int lost = Math.max(1, (int) global.getFloat(GearInPatrolGrudgeListener.LOST_KEY));
        int cost = (int) (pricePerPatrol * lost);

        CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
        TextPanelAPI text = dialog.getTextPanel();
        if (cargo.getCredits().get() < cost) {
            text.addPara("Aster 摇了摇头：这件事要 " + cost + " 星币，您的账上不够。");
            return true;
        }
        cargo.getCredits().subtract(cost);
        AddRemoveCommodity.addCreditsLossText(cost, text);
        global.unset(GearInPatrolGrudgeListener.GRUDGE_KEY);
        text.addPara("Aster 收下了钱：港务那边，她会去打招呼。");
        return true;
    }
}
