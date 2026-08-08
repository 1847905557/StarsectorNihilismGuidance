package data.scripts.gearin.rules;

import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;

/**
 * memoryMap 组装脚本 (EveryFrameScript)：落键后按 MemKeys 槽位组装与对话框插件
 * 同构的 memoryMap，解析 $global. / $player. / 无点号变量 (复现变量解析规则)。
 */
public class GearInMemoryMapAssembler implements EveryFrameScript {

    private static final Logger LOG = Global.getLogger(GearInMemoryMapAssembler.class);
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
        if (!sector.contains(GearInMemoryKeys.KEY_TAG)) {
            return; // 等写入脚本落键 (同帧顺序未定义，用键状态对齐)
        }
        Map<String, MemoryAPI> memoryMap = new HashMap<>();
        memoryMap.put(MemKeys.GLOBAL, sector);
        memoryMap.put(MemKeys.PLAYER, Global.getSector().getCharacterData().getMemoryWithoutUpdate());
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        if (fleet != null) {
            memoryMap.put(MemKeys.LOCAL, fleet.getMemoryWithoutUpdate());
        }
        LOG.info("GearInMemoryMapAssembler: memoryMap 槽位 = " + memoryMap.keySet()
                + "(与对话框插件组装一致：local = 交互目标记忆、global、player)");
        LOG.info("GearInMemoryMapAssembler: 解析 $global.GearIn_memory_tag = "
                + resolveVariable(memoryMap, "$global.GearIn_memory_tag"));
        LOG.info("GearInMemoryMapAssembler: 解析 $player.GearIn_memory_player = "
                + resolveVariable(memoryMap, "$player.GearIn_memory_player"));
        LOG.info("GearInMemoryMapAssembler: 解析 $GearIn_memory_fleetKey(无点号回落 local)= "
                + resolveVariable(memoryMap, "$GearIn_memory_fleetKey"));
        done = true;
    }

    /**
     * 复现 Rules 引擎的变量解析 (Misc.Token.getVarNameAndMemory)：
     * "$memkey.name" → 取 memoryMap 的 memkey 槽位，读其中的 $name 键；
     * 无点号 → 回落 local 槽位。memoryMap 中没有该槽位时同样回落 local。
     */
    private static Object resolveVariable(Map<String, MemoryAPI> memoryMap, String variable) {
        int dot = variable.indexOf('.');
        String memoryKey = null;
        String name = variable;
        if (dot > 0 && dot < variable.length() - 1) {
            memoryKey = variable.substring(1, dot);
            name = "$" + variable.substring(dot + 1);
        }
        MemoryAPI memory = memoryMap.get(memoryKey);
        if (memory == null) {
            memory = memoryMap.get(MemKeys.LOCAL);
            name = variable;
        }
        return memory == null ? null : memory.get(name);
    }
}
