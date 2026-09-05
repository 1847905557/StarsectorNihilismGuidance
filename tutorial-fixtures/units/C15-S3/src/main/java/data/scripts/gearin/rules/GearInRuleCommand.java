package data.scripts.gearin.rules;

import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.impl.campaign.rulecmd.FireBest;
import com.fs.starfarer.api.util.Misc.Token;

/**
 * 完整最小例：自定义规则命令 (rulecmd)。
 * 注册入口：data/campaign/rules.csv 脚本列按简单类名调用 (如
 * "GearInRuleCommand GearInRuleCmdResult 2 true")，类经 ruleCommandPackages
 * (data/config/settings.json 追加的 data.scripts.gearin.rules) 定位。每次执行经
 * 脚本类加载器重新 newInstance 后调用 execute —— 实例字段不可跨调用使用。
 * 行为：守卫后解析 <trigger> [<amount>] [<keepOptions>]，把 amount 写入实体记忆
 * $GearIn_rulecmd_last，再 FireBest 再入同一 dialog 与 memoryMap；返回值在脚本列
 * 被丢弃、在条件列作为布尔判真。
 */
public class GearInRuleCommand extends BaseCommandPlugin {

    /** 命令写入的记忆键 (无过期)。 */
    public static final String MEM_KEY_LAST = "$GearIn_rulecmd_last";

    private static final Logger LOG = Global.getLogger(GearInRuleCommand.class);

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog,
                           List<Token> params, Map<String, MemoryAPI> memoryMap) {
        // 参数守卫：FireBest 自身都不判空直接 params.get(0)(越界即红字异常)，
        // 自定义命令必须先守卫。
        if (params.isEmpty()) {
            LOG.warn("GearInRuleCommand: expected params <trigger> [<amount>] [<keepOptions>]");
            return false;
        }

        String trigger = params.get(0).getString(memoryMap);
        float amount = params.size() > 1 ? params.get(1).getFloat(memoryMap) : 1f;
        boolean keepOptions = params.size() > 2 && params.get(2).getBoolean(memoryMap);

        MemoryAPI memory = getEntityMemory(memoryMap);
        memory.set(MEM_KEY_LAST, amount);

        // dialog 可能为 null(非对话触发，如面板再入)：先判空再再入。
        if (dialog == null) {
            LOG.warn("GearInRuleCommand: dialog is null; cannot fire trigger " + trigger);
            return false;
        }

        boolean fired = FireBest.fire(ruleId, dialog, memoryMap,
                trigger + (keepOptions ? " true" : ""));
        LOG.info("GearInRuleCommand: ruleId=" + ruleId + " trigger=" + trigger
                + " amount=" + amount + " keepOptions=" + keepOptions + " fired=" + fired);
        return fired;
    }
}
