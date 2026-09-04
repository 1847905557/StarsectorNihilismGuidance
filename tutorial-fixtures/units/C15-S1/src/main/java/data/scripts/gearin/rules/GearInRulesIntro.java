package data.scripts.gearin.rules;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.impl.campaign.RuleBasedInteractionDialogPluginImpl;

/**
 * 完整最小例：rules.csv 数据驱动对话的 Java 消费入口。
 *
 * 注册入口：生涯回调 onGameLoad(调用点片段见页面正文)，persistent data 键 REGISTER_MARK
 * 一次性门控 (沿用 C10-S1 / C10-S5 的既有模式，每个注册项用各自的键)。
 *
 * 行为：挂载后第一次 advance() 用 RuleBasedInteractionDialogPluginImpl 打开一个初始
 * trigger 为 GearInRulesIntro 的对话；对话的文本、脚本与选项全部来自 data/campaign/rules.csv
 * 中本节拥有的 GearIn_rules_intro_* 规则 —— Java 侧不写任何文本与选项。
 * 验证闭环：注册入口 (本类 + onGameLoad 门控)→ 数据文件 (rules.csv)→ 运行消费者
 * (RuleBasedInteractionDialogPluginImpl → Rules 引擎)→ 可观察结果 (对话文本与日志)。
 */
public class GearInRulesIntro implements EveryFrameScript {

    /** 存档持久化标记：本演示是否已在该存档执行过 (随存档保存)。 */
    public static final String REGISTER_MARK = "GearIn_RulesIntro";

    private static final Logger LOG = Global.getLogger(GearInRulesIntro.class);

    /** 演示是否已执行；置位后 isDone() 返回 true，引擎在置位同一次推进中自动移除本脚本。 */
    private boolean executed = false;

    public GearInRulesIntro() {
        // 空构造：注册由调用方执行 (onGameLoad 的 persistent data 门内，见页面调用点片段)
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
                new RuleBasedInteractionDialogPluginImpl("GearInRulesIntro"),
                Global.getSector().getPlayerFleet());
        LOG.info("GearIn rules intro dialog shown=" + shown
                + " (initial trigger GearInRulesIntro; 对话内容全部来自 rules.csv)");
    }
}
