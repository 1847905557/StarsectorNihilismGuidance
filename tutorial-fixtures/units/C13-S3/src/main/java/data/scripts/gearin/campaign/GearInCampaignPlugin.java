package data.scripts.gearin.campaign;

import com.fs.starfarer.api.PluginPick;
import com.fs.starfarer.api.campaign.BaseCampaignPlugin;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;

/**
 * 完整最小例：生涯插件 (CampaignPlugin) 的注册、picker、优先级与生命周期。
 * 本类是 transient 生涯插件：数据不进存档，必须由调用点 (GearInModPlugin.onGameLoad)
 * 在每次读档后重新注册；registerPlugin 按 getId() 去重，重复注册只会替换旧实例。
 * pickInteractionDialogPlugin 对 "齿轮工业" 势力的舰队与市场实体返回
 * GearInInteractionDialog (PickPriority.MOD_SPECIFIC)，其他实体返回 null 把选择权
 * 留给其他插件；updateGlobalFacts 每次 sector.getMemory() 重新执行，写入键带过期 0。
 */
public class GearInCampaignPlugin extends BaseCampaignPlugin {

    /** 演示触发势力：齿轮工业势力 id。 */
    public static final String DEMO_FACTION_ID = "gear_industry";

    /** updateGlobalFacts 写入的演示事实键：MOD 专属前缀 + 过期时间 0。 */
    public static final String DEMO_GLOBAL_FACT = "$gearIn_demoFact";

    @Override
    public String getId() {
        // 非 null 且稳定：注册去重与 unregisterPlugin 都依赖它。
        return "GearIn_CampaignPlugin";
    }

    @Override
    public boolean isTransient() {
        // transient：不随存档保存，每次读档后必须重新注册。
        return true;
    }

    @Override
    public PluginPick<InteractionDialogPlugin> pickInteractionDialogPlugin(SectorEntityToken interactionTarget) {
        if (interactionTarget == null) {
            return null;
        }
        if (interactionTarget instanceof CampaignFleetAPI fleet) {
            if (fleet.getFaction() != null && DEMO_FACTION_ID.equals(fleet.getFaction().getId())) {
                return new PluginPick<>(new GearInInteractionDialog(), PickPriority.MOD_SPECIFIC);
            }
        }
        if (interactionTarget.getMarket() != null
                && DEMO_FACTION_ID.equals(interactionTarget.getMarket().getFactionId())) {
            return new PluginPick<>(new GearInInteractionDialog(), PickPriority.MOD_SPECIFIC);
        }
        return null; // 不处理：把选择权留给其他插件 (原版 CORE_GENERAL 对话)
    }

    @Override
    public void updateGlobalFacts(MemoryAPI memory) {
        // 每次 sector.getMemory() 都会重新执行本方法；0 过期防止事实滞留污染记忆。
        memory.set(DEMO_GLOBAL_FACT, true, 0f);
    }
}
