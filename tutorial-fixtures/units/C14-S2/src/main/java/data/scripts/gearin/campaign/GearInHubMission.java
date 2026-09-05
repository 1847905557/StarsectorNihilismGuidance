package data.scripts.gearin.campaign;

import java.awt.Color;
import java.util.Map;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithBarEvent;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

/**
 * 完整最小例：枢纽任务 (HubMission) —— Intel、阶段、目标与事件链。
 * 任务 ID gearInHub (bar_events.csv 的 mission id 列)：消费者含规则分类前缀
 * gearInHub_blurbBar / gearInHub_optionBar、内存键 $gearInHub_stage / $gearInHub_ref。
 * 注册入口：data/campaign/bar_events.csv 一行 (plugin 列 = 本类全名) + rules.csv
 * 六条规则 (blurbBar / optionBar / startBar / 任务文本 / 数据收讫 / 交付结算)。
 * accept 时 addIntel + addScript，结束 endAfterDelay；Stage 四态切换时登记/解除
 * makeImportant 标记；完成检测三通道 (rules 写标志、帧轮询、60 天时限)；两个一次性
 * 触发器写全局标记；奖励为信用点 (按距离与质量折算) 与双声望。
 */
public class GearInHubMission extends HubMissionWithBarEvent {

    public static final float MISSION_DAYS = 60f;

    /** 阶段图：CONTACT(去目标市场找联系人)-> RETURN(回任务人处交货)-> 终态。 */
    public enum Stage {
        CONTACT,
        RETURN,
        COMPLETED,
        FAILED,
    }

    private static final Logger LOG = Global.getLogger(GearInHubMission.class);

    private PersonAPI giver;
    private MarketAPI sourceMarket;
    private MarketAPI targetMarket;
    private PersonAPI sourceContact;
    private int reward = 0;

    @Override
    public boolean shouldShowAtMarket(MarketAPI market) {
        // 酒吧路线：create() 之前被调用 (HubMissionBarEventWrapper.shouldShowAtMarket)。
        // 本示例任何有太空港的市场都可出现。
        return market.hasSpaceport();
    }

    @Override
    protected boolean create(MarketAPI createdAt, boolean barEvent) {
        if (barEvent) {
            // 酒吧路线需要自己生成给任务者 (联系人路线由 hub 的 getPerson() 提供)。
            setGiverRank(Ranks.CITIZEN);
            setGiverPost(Ranks.POST_TRADER);
            setGiverImportance(pickImportance());
            setGiverFaction(Factions.INDEPENDENT);
            setGiverTags(Tags.CONTACT_TRADE);
            findOrCreateGiver(createdAt, true, false);
        }

        giver = getPerson();
        if (giver == null) {
            return false;
        }
        sourceMarket = giver.getMarket();
        if (sourceMarket == null) {
            return false;
        }

        // mission ref：rules 侧 Call $gearInHub_ref <action> 打到本任务的 callEvent。
        // 键已被其他任务占用时返回 false —— create 失败、本次不提供该任务。
        if (!setPersonMissionRef(giver, "$gearInHub_ref")) {
            return false;
        }

        // ---- 目标选择：HubMissionWithSearch 的要求系统 ----
        requireMarketIsNot(sourceMarket);
        requireMarketFaction(sourceMarket.getFactionId());
        requireMarketFactionNotHostileTo(Factions.PLAYER);
        preferMarketInDirectionOfOtherMissions();
        targetMarket = pickMarket();
        if (targetMarket == null) {
            return false;
        }

        sourceContact = findOrCreateTrader(targetMarket.getFactionId(), targetMarket, true);
        if (sourceContact == null || !setPersonMissionRef(sourceContact, "$gearInHub_ref")) {
            return false;
        }

        // ---- 阶段图 ----
        setStartingStage(Stage.CONTACT);
        setSuccessStage(Stage.COMPLETED);
        setFailureStage(Stage.FAILED);

        // ---- 目标完成检测：内存标志连接 (对话驱动，rules 置位 + Call updateStage)----
        setStageOnMemoryFlag(Stage.RETURN, sourceContact, "$gearInHub_dataReceived");
        setStageOnMemoryFlag(Stage.COMPLETED, giver, "$gearInHub_delivered");

        // ---- 目标时限：60 游戏天超时进 FAILED ----
        setTimeLimit(Stage.FAILED, MISSION_DAYS, null);

        // ---- 地图目标标记：进入对应阶段时点亮 ----
        makeImportant(sourceContact, "$gearInHub_hasData", Stage.CONTACT);
        makeImportant(giver, "$gearInHub_returnMark", Stage.RETURN);

        // ---- 事件链：一次性触发器 ----
        // 进入 RETURN 阶段即写全局标记 (AlwaysTrueChecker 恒真，进阶段即触发)。
        beginCustomTrigger(new AlwaysTrueChecker(), Stage.RETURN);
        triggerSetGlobalMemoryValue("$gearInHub_enteredReturn", true);
        endTrigger();
        // 任务进行满 30 游戏天 (无论停在哪个阶段) 写半程标记。
        // 注意：beginDaysElapsedTrigger(float, Object...) 与 (float, Object, Object...)
        // 两个重载在三个实参时会歧义，这里直接用它内部构造的 DaysElapsedChecker。
        beginCustomTrigger(new DaysElapsedChecker(MISSION_DAYS / 2f, this), Stage.CONTACT, Stage.RETURN);
        triggerSetGlobalMemoryValue("$gearInHub_halfTime", true);
        endTrigger();

        // ---- 奖励与声望 ----
        reward = getRoundNumber(30000f + getDistanceLY(targetMarket) * 3000f + getQuality() * 20000f);
        creditReward = reward;
        setRepPersonChangesMedium();
        setRepFactionChangesLow();

        LOG.info(String.format("GearIn hub mission: created at %s, target %s, reward %d",
                createdAt.getName(), targetMarket.getName(), reward));
        return true;
    }

    @Override
    protected void updateInteractionDataImpl() {
        // 写规则变量 (LOCAL 人物内存，随档持久；$gearInHub_stage 由基类自动写)。
        set("$gearInHub_reward", Misc.getWithDGS(reward));
        set("$gearInHub_targetMarketName", targetMarket.getName());
        set("$gearInHub_dist", getDistanceLY(targetMarket));
        set("$gearInHub_contactName", sourceContact.getNameString());
    }

    @Override
    public void acceptImpl(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        LOG.info("GearIn hub mission: accepted, currentStage=" + getCurrentStage());
    }

    @Override
    public String getBaseName() {
        return "Lost Shipment Data";
    }

    @Override
    public void addDescriptionForNonEndStage(TooltipMakerAPI info, float width, float height) {
        float opad = 10f;
        Color h = Misc.getHighlightColor();
        if (currentStage == Stage.CONTACT) {
            info.addPara("A shipment of factory data went missing while a convoy passed through %s. "
                    + "Talk to %s there to retrieve the data cache.",
                    opad, h, targetMarket.getName(), sourceContact.getNameString());
        } else if (currentStage == Stage.RETURN) {
            info.addPara("Return to %s and hand the recovered data over to %s.",
                    opad, h, sourceMarket.getName(), giver.getNameString());
        }
    }

    @Override
    public boolean addNextStepText(TooltipMakerAPI info, Color tc, float pad) {
        Color h = Misc.getHighlightColor();
        if (currentStage == Stage.CONTACT) {
            info.addPara("Go to %s and talk to %s",
                    pad, tc, h, targetMarket.getName(), sourceContact.getNameString());
            return true;
        }
        if (currentStage == Stage.RETURN) {
            info.addPara("Return to %s and talk to %s",
                    pad, tc, h, sourceMarket.getName(), giver.getNameString());
            return true;
        }
        return false;
    }
}
