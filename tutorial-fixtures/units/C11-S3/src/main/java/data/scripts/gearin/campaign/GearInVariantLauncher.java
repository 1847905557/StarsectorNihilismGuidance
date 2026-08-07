package data.scripts.gearin.campaign;

import com.fs.starfarer.api.campaign.SectorAPI;

/**
 * 角色：注册载体(薄接线类)。onGameLoad 的一次性门控通过后调用 register()，
 * 把本节五个角色脚本(读取 / 克隆改装 / 武器组 / 模块覆盖 / 序列化)逐一挂进生涯。
 *
 * 注册入口：生涯回调 onGameLoad(调用点片段见页面正文)，注册前用 sector persistent
 * data 键 REGISTER_MARK 做一次性门控(C6-S2 的 CLASS_MARK 模式，模式同 C9-S1
 * 的 GearInSectorClockScript)。每个角色脚本首次 advance() 后 isDone() 置 true，
 * 引擎下一帧自动移除 —— 整套演示在首个生涯帧内顺序完成，读档后不再重复。
 */
public final class GearInVariantLauncher {

    /** 存档持久化标记：本组演示脚本是否已在该存档注册过(随存档保存)。 */
    public static final String REGISTER_MARK = "GearIn_VariantLauncher";

    private GearInVariantLauncher() {
    }

    /** 注册本节全部变体演示脚本；每个脚本跑一次即自停。 */
    public static void register(SectorAPI sector) {
        sector.addScript(new GearInVariantReader());
        sector.addScript(new GearInVariantRefitter());
        sector.addScript(new GearInWeaponGroupBuilder());
        sector.addScript(new GearInModuleOverrider());
        sector.addScript(new GearInVariantSerializer());
    }
}
