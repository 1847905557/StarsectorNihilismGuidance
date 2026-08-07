package data.scripts.gearin.rules;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.RuleBasedInteractionDialogPluginImpl;

/**
 * 规则对话打开脚本(EveryFrameScript)：记忆写入与过期验证完成(KEY_TAG 存在、
 * probe 已被移除)后，打开初始 trigger 为 GearInMemoryDialog 的对话 —— 对话文本、
 * 条件与选项全部来自 rules.csv 中本节拥有的 GearIn_memory_* 规则($global. /
 * $player. / 无点号回落 local 三种读法 + $player. 赋值 0 天过期)。
 */
public class GearInMemoryDialog implements EveryFrameScript {

    /** 对话初始 trigger：与 rules.csv 的 GearIn_memory_welcome 规则 trigger 一致。 */
    public static final String INITIAL_TRIGGER = "GearInMemoryDialog";

    private static final Logger LOG = Global.getLogger(GearInMemoryDialog.class);

    private boolean done = false;

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
        if (Global.getSector() == null || done) {
            return;
        }
        MemoryAPI sector = Global.getSector().getMemoryWithoutUpdate();
        if (!sector.contains(GearInMemoryKeys.KEY_TAG)
                || sector.contains(GearInMemoryKeys.KEY_PROBE)) {
            return; // 等写入与过期演示完成(probe 移除后)再弹对话，日志顺序可预期
        }
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        if (fleet == null) {
            LOG.warn("GearInMemoryDialog: player fleet is null, skip dialog");
            done = true;
            return;
        }
        CampaignUIAPI ui = Global.getSector().getCampaignUI();
        boolean shown = ui.showInteractionDialog(
                new RuleBasedInteractionDialogPluginImpl(INITIAL_TRIGGER), fleet);
        if (shown) {
            LOG.info("GearInMemoryDialog: dialog shown=true"
                    + " (initial trigger GearInMemoryDialog; 对话内容全部来自 rules.csv)");
            done = true;
        }
        // shown=false：正有其它对话/界面排队 —— 不置 done，下一个未暂停帧重试
    }
}
