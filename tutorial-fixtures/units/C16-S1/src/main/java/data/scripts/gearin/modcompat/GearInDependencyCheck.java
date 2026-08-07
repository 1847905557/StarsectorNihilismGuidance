package data.scripts.gearin.modcompat;

import java.util.List;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ModDependencyAPI;
import com.fs.starfarer.api.ModManagerAPI;
import com.fs.starfarer.api.ModSpecAPI;
import com.fs.starfarer.api.VersionInfoAPI;

/**
 * 依赖的运行时检测：把 mod_info.json 声明的 dependencies 与启动器 MOD 管理器
 * 的运行时状态对齐，逐项输出 id、名称、是否启用与安装版本。
 *
 * 声明式硬依赖由启动器把关(缺失或版本不符时无法勾选)；本类只负责
 * 在代码里复核声明与事实，并把不一致暴露成明确的日志行。
 */
public class GearInDependencyCheck {

    public static final String SELF_MOD_ID = "gear_in";

    private GearInDependencyCheck() {
    }

    /**
     * 在 MOD 插件的 onApplicationLoad() 中调用一次(接入方式见页面正文)。
     */
    public static void reportDeclaredDependencies() {
        ModManagerAPI modManager = Global.getSettings().getModManager();
        ModSpecAPI self = modManager.getModSpec(SELF_MOD_ID);
        if (self == null) {
            Global.getLogger(GearInDependencyCheck.class).warn(
                "GearIn dependency check: mod [" + SELF_MOD_ID + "] is not enabled; cannot read declared dependencies");
            return;
        }
        List<ModDependencyAPI> declared = self.getDependencies();
        if (declared.isEmpty()) {
            Global.getLogger(GearInDependencyCheck.class).info(
                "GearIn dependency check: no dependencies declared in mod_info.json");
            return;
        }
        for (ModDependencyAPI dep : declared) {
            boolean enabled = modManager.isModEnabled(dep.getId());
            String installedVersion = "not installed";
            if (enabled) {
                ModSpecAPI installed = modManager.getModSpec(dep.getId());
                VersionInfoAPI versionInfo = installed.getVersionInfo();
                if (versionInfo != null) {
                    installedVersion = versionInfo.getString();
                }
            }
            Global.getLogger(GearInDependencyCheck.class).info(
                "GearIn dependency check: " + dep.getName() + " (" + dep.getId()
                    + ") enabled=" + enabled
                    + " installedVersion=" + installedVersion);
        }
    }
}
