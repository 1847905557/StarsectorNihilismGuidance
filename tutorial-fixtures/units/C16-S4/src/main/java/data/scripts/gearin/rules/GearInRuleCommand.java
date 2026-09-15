package data.scripts.gearin.rules;

import java.awt.Color;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Misc.Token;

/**
 * Parses Rules command parameters and writes the result to entity memory.
 * The command returns the parsed boolean so it can also be used in a
 * conditions cell.
 */
public class GearInRuleCommand extends BaseCommandPlugin {

    public static final String MEM_KEY_LAST = "$GearIn_rulecmd_last";
    public static final String MEM_KEY_LABEL = "$GearIn_rulecmd_label";
    public static final String MEM_KEY_COUNT = "$GearIn_rulecmd_count";
    public static final String MEM_KEY_COLOR = "$GearIn_rulecmd_color";
    public static final String MEM_KEY_OBJECT = "$GearIn_rulecmd_object";

    private static final Logger LOG = Global.getLogger(GearInRuleCommand.class);

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog,
                           List<Token> params, Map<String, MemoryAPI> memoryMap) {
        if (params.size() < 3) {
            LOG.warn("GearInRuleCommand: expected <label> <amount> <enabled> [<color>] [<object>]");
            return false;
        }

        Token labelToken = params.get(0);
        Token amountToken = params.get(1);
        Token enabledToken = params.get(2);
        if ((!labelToken.isLiteral() && !labelToken.isVariable())
                || !amountToken.isFloat(memoryMap)
                || !enabledToken.isBoolean(memoryMap)) {
            LOG.warn("GearInRuleCommand: invalid parameter type for rule " + ruleId);
            return false;
        }

        String label = dialog == null
                ? labelToken.getString(memoryMap)
                : labelToken.getStringWithTokenReplacement(ruleId, dialog, memoryMap);
        float amount = amountToken.getFloat(memoryMap);
        int count = amountToken.getInt(memoryMap);
        boolean enabled = enabledToken.getBoolean(memoryMap);
        Color color = params.size() > 3
                ? params.get(3).getColor(memoryMap)
                : Misc.getHighlightColor();
        Object object = params.size() > 4 ? params.get(4).getObject(memoryMap) : null;

        MemoryAPI memory = getEntityMemory(memoryMap);
        memory.set(MEM_KEY_LABEL, label);
        memory.set(MEM_KEY_LAST, amount);
        memory.set(MEM_KEY_COUNT, count);
        memory.set(MEM_KEY_COLOR, color);
        if (object != null) {
            memory.set(MEM_KEY_OBJECT, object);
        }

        LOG.info("GearInRuleCommand: ruleId=" + ruleId + " label=" + label
                + " amount=" + amount + " count=" + count + " enabled=" + enabled);
        return enabled;
    }
}
