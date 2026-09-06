package data.scripts.gearin.campaign;

import java.util.Map;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.BaseBarEvent;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.BarEventManager;

/**
 * 完整最小例：酒吧事件 (PortsideBarEvent) —— 酒吧选项、事件对话与生命周期收尾。
 * 事件 ID 默认为类简单名 "GearInBarEvent"：creator 抽取按 ID 去重，BarCMD 展示后
 * 把 ID 写入市场内存，下次进入同一市场按 ID 复现同一组事件。生命周期：creator
 * 创建实例并入 active 跟踪器；玩家进酒吧时经 shouldShowAtMarket 过滤并写入选项
 * (optionData 必须是事件自身)；点选进入事件对话；交互后必须 notifyWasInteractedWith
 * 收尾，把事件移出并把 creator 置入 accepted 超时。演示：花 500 信用点买补给
 * 藏匿点情报、婉拒或离开，三个分支都验证 accepted 超时路径。
 */
public class GearInBarEvent extends BaseBarEvent {

    private static final Logger LOG = Global.getLogger(GearInBarEvent.class);

    /** 对话选项 id(仅事件对话内部使用；optionData 用常量比较，不用文本)。 */
    public static final String OPTION_ACCEPT = "gearIn_barAccept";
    public static final String OPTION_DECLINE = "gearIn_barDecline";
    public static final String OPTION_LEAVE = "gearIn_barLeave";

    /** 买下情报的信用点价格 (真实从玩家舰队 cargo 扣减)。 */
    public static final int INFO_PRICE = 500;

    @Override
    public boolean shouldShowAtMarket(MarketAPI market) {
        // BaseBarEvent 默认：shownAt == null 或 == 当前市场才显示 (首次展示后锁定单一市场)。
        boolean ok = super.shouldShowAtMarket(market);
        if (!ok) {
            LOG.info("GearIn bar event: skip " + market.getName()
                    + " (shownAt locked to " + getShownAt() + ")");
            return false;
        }
        // 演示市场条件：只出现在有太空港的市场 (新档可访问的核心市场基本满足)。
        if (!market.hasSpaceport()) {
            LOG.info("GearIn bar event: skip " + market.getName() + " (no spaceport)");
            return false;
        }
        LOG.info("GearIn bar event: eligible at " + market.getName());
        return true;
    }

    @Override
    public void addPromptAndOption(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        super.addPromptAndOption(dialog, memoryMap);
        MarketAPI market = dialog.getInteractionTarget().getMarket();
        LOG.info(String.format("GearIn bar event: prompt at %s (event id=%s)",
                market.getName(), getBarEventId()));

        TextPanelAPI text = dialog.getTextPanel();
        text.addPara("A disheveled spacer at the bar is telling anyone who will listen about a cache "
                + "of supplies drifting somewhere in this system.");

        // 关键机制：选项的 optionData 必须是事件自身 (this)。BarCMD.optionSelected()
        // 只对 optionData instanceof PortsideBarEvent 才创建 BarEventDialogPlugin，
        // 进入事件对话；换成字符串或其他对象则落入规则对话框分支。
        dialog.getOptionPanel().addOption("Listen to the spacer's story", this);
    }

    @Override
    public void init(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        super.init(dialog, memoryMap);
        LOG.info("GearIn bar event: dialog init (event id=" + getBarEventId() + ")");
        // 对话每次进入都重置完成标记 (BaseBarEvent.init 不清 done，需要自行重置)。
        done = false;
        showRumor();
    }

    private void showRumor() {
        TextPanelAPI text = dialog.getTextPanel();
        OptionPanelAPI options = dialog.getOptionPanel();
        text.addPara("You sit down across from the spacer and buy them a drink. Once the drink is "
                + "half-gone, they lower their voice and tell you about a cargo pod that drifted off "
                + "a burning freighter - still full of supplies, just waiting to be found. "
                + "\"I'd sell you the coordinates for a song,\"they say.");
        options.clearOptions();
        options.addOption("Buy the coordinates (" + INFO_PRICE + " credits)", OPTION_ACCEPT);
        options.addOption("Hear them out but decline", OPTION_DECLINE);
        options.addOption("Leave the spacer to their drink", OPTION_LEAVE);
    }

    @Override
    public void optionSelected(String optionText, Object optionData) {
        TextPanelAPI text = dialog.getTextPanel();
        if (OPTION_ACCEPT.equals(optionData)) {
            text.addPara("The spacer scribbles coordinates on a napkin and slides it over. You pay "
                    + "them " + INFO_PRICE + " credits and pocket the napkin.");
            CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
            cargo.getCredits().subtract(INFO_PRICE);
            LOG.info("GearIn bar event: bought coordinates, credits -" + INFO_PRICE);
            finish();
        } else if (OPTION_DECLINE.equals(optionData)) {
            text.addPara("The spacer shrugs. \"Suit yourself. The void isn't going anywhere.\"");
            // noContinue = true：结束时不显示 "Continue"，直接回到酒吧选项。
            noContinue = true;
            LOG.info("GearIn bar event: declined");
            finish();
        } else if (OPTION_LEAVE.equals(optionData)) {
            text.addPara("You finish your drink and get up. The spacer returns to telling the story "
                    + "to nobody in particular.");
            noContinue = true;
            LOG.info("GearIn bar event: left");
            finish();
        } else {
            LOG.info("GearIn bar event: unknown option data " + optionData);
        }
    }

    /** 对话收尾：置完成标记并通知 manager 移除本事件 (creator 进入 accepted 超时)。 */
    private void finish() {
        done = true;
        BarEventManager.getInstance().notifyWasInteractedWith(this);
        LOG.info("GearIn bar event: finished, notified manager (done=" + isDialogFinished() + ")");
    }

    @Override
    public void wasShownAtMarket(MarketAPI market) {
        super.wasShownAtMarket(market);
        LOG.info("GearIn bar event: shown at " + market.getName());
    }

    @Override
    public void advance(float amount) {
        // 事件在活动列表中每帧被 PortsideBarData.advance 推进 (未暂停时)；
        // 本示例没有时间逻辑，保留空实现。需要限时消失的事件在这里推进
        // 倒计时并在到期时由 shouldRemoveEvent() 返回 true。
    }
}
