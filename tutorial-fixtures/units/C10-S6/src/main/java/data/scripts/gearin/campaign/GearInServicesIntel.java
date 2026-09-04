package data.scripts.gearin.campaign;

import java.util.Set;

import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

/**
 * 完整最小例 (情报插件)：自定义情报 (IntelInfoPlugin 的默认实现 BaseIntelPlugin 子类)。
 *
 * 绑定位置：由 GearInIntelServicesScript 经 IntelManagerAPI.addIntel(plugin) 登记
 * (addIntel 绕过可见性检查，立即置玩家可见时间戳并弹出左下消息)。
 *
 * 契约要点：getIcon() 返回 null 时 Intel 页面不显示图标 (IntelInfoPlugin
 * "40x40, no icon if null.")；getIntelTags(map) 必须处理 map 为 null
 * ("Method NEEDS to handle map being null gracefully.") —— 本类先调
 * super.getIntelTags(map)，基类默认实现内部已做 null 判断，再追加自定义 tag。
 */
public class GearInServicesIntel extends BaseIntelPlugin {

    @Override
    public String getName() {
        return "GearIn 服务速览 (自定义情报演示)";
    }

    @Override
    public String getIcon() {
        return null; // null = 无图标，Intel 页面使用默认图标
    }

    @Override
    public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
        info.addPara("这是一条由 GearInIntelServicesScript 创建并立即移除的演示情报。", Misc.getTextColor(), 0f);
        info.addPara("登记入口 addIntel(plugin) 会直接置玩家可见时间戳并弹出左下消息；"
                + "本演示在同一帧调用 removeIntel(plugin) 移除，不留存档残留。", Misc.getGrayColor(), 10f);
    }

    @Override
    public Set<String> getIntelTags(SectorMapAPI map) {
        // 基类默认实现已处理 map == null(map 非 null 时才计算 INTEL_LOCAL 距离)
        Set<String> tags = super.getIntelTags(map);
        tags.add(Tags.INTEL_EXPLORATION);
        return tags;
    }
}
