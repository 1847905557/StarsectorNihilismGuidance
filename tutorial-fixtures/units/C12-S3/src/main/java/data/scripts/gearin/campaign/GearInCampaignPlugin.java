package data.scripts.gearin.campaign;

import com.fs.starfarer.api.PluginPick;
import com.fs.starfarer.api.campaign.BaseCampaignPlugin;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;

/**
 * 完整最小例：生涯插件 (CampaignPlugin) 的注册、picker、优先级与生命周期。
 *
 * 本类是 transient 生涯插件：数据不进存档，必须由调用点 (GearInModPlugin.onGameLoad，
 * 见页面正文) 在每次读档后重新注册；registerPlugin 按 getId() 去重，重复注册只会
 * 替换旧实例，不会叠加。
 *
 * pickInteractionDialogPlugin：对 "齿轮工业"(C3-S2 注册的势力 id) 的舰队与市场实体
 * 返回 GearInInteractionDialog(PickPriority.MOD_SPECIFIC)；对其他实体返回 null，即放弃本次
 * 选择权，由原版 CORE_GENERAL 对话继续生效。
 *
 * updateGlobalFacts：每次 sector.getMemory() 都会重新执行；写入的演示事实使用 MOD
 * 专属前缀且过期时间为 0，符合 API 对 facts 写法的要求。
 *
 * 被 picker 选中的对话框由同包类 GearInInteractionDialog 承担 (一文件一角色，见该文件)。
 */
public class GearInCampaignPlugin extends BaseCampaignPlugin {

    /** 演示触发势力：齿轮工业势力 id(C3-S2 定义，本节只消费不定义)。 */
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
        // transient：不随存档保存，每次读档后必须重新注册 (见页面调用点片段)。
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
