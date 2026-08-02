package data.scripts.gearin.modcompat;

import com.fs.starfarer.api.Global;
import exerelin.campaign.SectorManager;
import exerelin.utilities.NexConfig;
import exerelin.world.ExerelinCorvusLocations;

/**
 * Nexerelin 软依赖适配器：把对 exerelin.* 的直接引用全部收敛在本类，
 * 工程里只有本类与特遣队命名器（GearInSpecialForcesNamer，仅被 Nexerelin
 * 按配置类名加载）出现 exerelin.* 类名。
 *
 * 调用链（软依赖组合判据）：
 *   isModEnabled 判定 MOD 启用状态 -> SectorManager.getManager() 判管理器
 *   是否已创建（null 守卫）-> isCorvusMode() 判当前模式。
 * NexConfig.getFactionConfig 读取本 MOD 势力配置（经 mod_factions.csv 注册）；
 * ExerelinCorvusLocations 读取本 MOD 的出生点行（corvus_spawnpoints.csv）。
 * Nexerelin 未启用时全部回落降级值，不抛异常。
 */
public class GearInNexCompat {

    /** Nexerelin 的 MOD id，与 Starsector\mods\Nexerelin\mod_info.json 的 id 字段逐字一致。 */
    public static final String NEX_MOD_ID = "nexerelin";

    /** 本 MOD 势力 id（教程势力章节注册）。 */
    public static final String GEAR_FACTION_ID = "gear_industry";

    private GearInNexCompat() {
    }

    /** 判据一：Nexerelin 是否在启用列表（启动器 MOD 管理器按 id 逐字比较，大小写敏感）。 */
    public static boolean isNexEnabled() {
        return Global.getSettings().getModManager().isModEnabled(NEX_MOD_ID);
    }

    /**
     * 组合判据：Nexerelin 已启用、SectorManager 已创建且处于 Corvus 模式。
     * SectorManager.getManager() 在 manager 尚未创建时返回 null（sector
     * persistent data 中无条目）——直接调用 isCorvusMode() 会 NPE；
     * manager 由 Nexerelin 的入口生成器创建，早于它的调用点必须靠这里守卫。
     */
    public static boolean isNexCorvusMode() {
        if (!isNexEnabled()) {
            return false;
        }
        SectorManager manager = SectorManager.getManager();
        return manager != null && manager.isCorvusMode();
    }

    /**
     * 读取本 MOD 势力配置的 marketSpawnWeight（随机星域模式的市场份额倍率）。
     * NexConfig.getFactionConfig 未注册时回退默认配置（neutral），不抛异常——
     * "读到的不是自己的配置"是该 API 的静默失败模式（mod_factions.csv 漏写时
     * playableFaction 读回 false）。
     */
    public static float getFactionMarketSpawnWeight(String factionId) {
        if (!isNexEnabled()) {
            return 1f;
        }
        return NexConfig.getFactionConfig(factionId).marketSpawnWeight;
    }

    /**
     * 读取本 MOD 势力在 corvus_spawnpoints.csv 里的生成点实体 id。
     * 漏写行时返回 null——玩家出生点回退 homeworld / 随机位置，无报错。
     */
    public static String getFactionSpawnPointEntityId(String factionId) {
        if (!isNexEnabled()) {
            return null;
        }
        ExerelinCorvusLocations.SpawnPointEntry entry = ExerelinCorvusLocations.getFactionSpawnPoint(factionId);
        return entry == null ? null : entry.entityId;
    }
}
