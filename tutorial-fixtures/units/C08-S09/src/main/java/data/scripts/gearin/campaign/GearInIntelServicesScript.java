package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.comm.IntelManagerAPI;
import com.fs.starfarer.api.impl.campaign.intel.MessageIntel;
import com.fs.starfarer.api.util.Misc;

/**
 * 完整最小例（情报段）：IntelManagerAPI 常用入口演示脚本。
 *
 * 注册入口：生涯回调 onGameLoad（调用点片段见页面正文），persistent data 键 REGISTER_MARK
 * 一次性门控（每个注册项用各自的键，不挂进插件的 CLASS_MARK 门内）。
 *
 * 行为：创建自定义情报 GearInServicesIntel（extends BaseIntelPlugin）addIntel 登记、
 * hasIntel 验证、removeIntel 移除（不留存档残留）；再用 MessageIntel 发一条纯消息
 * （左下消息区）。演示全程不遗留持久状态。
 */
public class GearInIntelServicesScript extends GearInOneShotScript {

    /** 存档持久化标记：本脚本是否已在该存档执行过（随存档保存）。 */
    public static final String REGISTER_MARK = "GearIn_IntelServices";

    private static final Logger LOG = Global.getLogger(GearInIntelServicesScript.class);

    @Override
    protected void runDemo() {
        SectorAPI sector = Global.getSector();
        IntelManagerAPI intelManager = sector.getIntelManager();
        GearInServicesIntel intel = new GearInServicesIntel();
        intelManager.addIntel(intel);
        boolean present = intelManager.hasIntel(intel);
        int intelCount = intelManager.getIntel(GearInServicesIntel.class).size();
        LOG.info(String.format(
                "GearIn campaign services intel: added present=%s count=%d (campaign message posted)",
                present, intelCount));
        intelManager.removeIntel(intel);
        boolean absent = !intelManager.hasIntel(intel);
        LOG.info(String.format(
                "GearIn campaign services intel: removed absent=%s count=%d",
                absent, intelManager.getIntel(GearInServicesIntel.class).size()));

        MessageIntel message = new MessageIntel(
                "GearIn 生涯服务速览：纯消息情报（MessageIntel）演示", Misc.getTextColor());
        message.addLine("addLine 追加的第二行：消息不绑定情报页面，只在左下消息区显示。",
                Misc.getGrayColor());
        CampaignUIAPI ui = sector.getCampaignUI();
        ui.addMessage(message);
        LOG.info("GearIn campaign services intel: message-only intel posted");
    }
}
