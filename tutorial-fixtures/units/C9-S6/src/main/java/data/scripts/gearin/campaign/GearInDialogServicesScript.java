package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignUIAPI;

/**
 * 完整最小例(对话框段)：InteractionDialogAPI 常用入口演示脚本。
 *
 * 注册入口：生涯回调 onGameLoad(调用点片段见页面正文)，persistent data 键 REGISTER_MARK
 * 一次性门控(每个注册项用各自的键，不挂进插件的 CLASS_MARK 门内)。
 *
 * 行为：用 CampaignUIAPI.showInteractionDialog(plugin, null) 打开演示对话框
 * (GearInServicesDialog implements InteractionDialogPlugin)，记录返回的 shown 布尔值
 * (已有对话框显示时返回 false，见 javadoc)；对话框由玩家点选选项后自行关闭，
 * 本脚本不留持久状态。
 */
public class GearInDialogServicesScript extends GearInOneShotScript {

    /** 存档持久化标记：本脚本是否已在该存档执行过(随存档保存)。 */
    public static final String REGISTER_MARK = "GearIn_DialogServices";

    private static final Logger LOG = Global.getLogger(GearInDialogServicesScript.class);

    @Override
    protected void runDemo() {
        CampaignUIAPI ui = Global.getSector().getCampaignUI();
        boolean shown = ui.showInteractionDialog(new GearInServicesDialog(), null);
        LOG.info("GearIn campaign services dialog: shown=" + shown
                + " (interactionTarget=null; 玩家在对话框内点选选项后由选项回调关闭)");
    }
}
