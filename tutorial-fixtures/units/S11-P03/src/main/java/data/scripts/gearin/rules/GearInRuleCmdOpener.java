package data.scripts.gearin.rules;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.impl.campaign.RuleBasedInteractionDialogPluginImpl;

/**
 * 完整最小例：对话入口（支撑类，示例所有权主类为 GearInRuleCommand）。
 *
 * 注册入口：生涯回调 onGameLoad（调用点片段见页面正文），persistent data 键
 * REGISTER_MARK 一次性门控（沿用 S08-P01 / S11-P01 的模式，每个注册项用各自的键）。
 *
 * 行为：挂载后第一次 advance() 用 RuleBasedInteractionDialogPluginImpl 打开一个
 * 初始 trigger 为 GearInRuleCmdOpener 的对话；对话的文本、脚本与选项全部来自
 * data/campaign/rules.csv 中本页拥有的 GearIn_rules_cmd_* 规则，其中脚本列调用
 * GearInRuleCommand 演示自定义命令、Token 解析与 FireBest/FireAll 再入。
 */
public class GearInRuleCmdOpener implements EveryFrameScript {

    /** 存档持久化标记：本演示是否已在该存档执行过（随存档保存）。 */
    public static final String REGISTER_MARK = "GearIn_RuleCmdOpener";

    private static final Logger LOG = Global.getLogger(GearInRuleCmdOpener.class);

    /** 演示是否已执行；置位后 isDone() 返回 true，引擎下一帧自动移除本脚本。 */
    private boolean executed = false;

    public GearInRuleCmdOpener() {
        // 空构造：注册由调用方执行（onGameLoad 的 persistent data 门内，见页面调用点片段）
    }

    @Override
    public boolean isDone() {
        return executed;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        if (executed) {
            return;
        }
        executed = true;
        CampaignUIAPI ui = Global.getSector().getCampaignUI();
        boolean shown = ui.showInteractionDialog(
                new RuleBasedInteractionDialogPluginImpl("GearInRuleCmdOpener"),
                Global.getSector().getPlayerFleet());
        LOG.info("GearIn rulecmd opener dialog shown=" + shown
                + " (initial trigger GearInRuleCmdOpener; 对话内容全部来自 rules.csv)");
    }
}
