package data.scripts.gearin.lib;

import java.util.List;

import org.apache.log4j.Logger;
import org.boxutil.BoxUtilModPlugin;
import org.boxutil.config.BoxConfigs;
import org.boxutil.manager.ShaderCore;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.input.InputEventAPI;

/**
 * 完整最小例的接线载体：BoxUtil 初始化（initLater 守卫）、可用性判据与生成
 * 计时在这里，实体生成与字体文本分别委托给 GearInBoxSpawner / GearInTextField；
 * 由既有战斗插件在 init() 中 addPlugin 注册（C07-S01 模型），只在本场战斗内有效。
 */
public class GearInBoxEntity extends BaseEveryFrameCombatPlugin {

    private static final Logger LOG = Global.getLogger(GearInBoxEntity.class);

    /** 生成间隔（秒）。 */
    private static final float SPAWN_INTERVAL = 2f;

    private CombatEngineAPI engine;

    /** 初始化可用性：配置开关 && GPU 资源有效，二者共同决定 shader 渲染入口可用性。 */
    private boolean boxAvailable;

    private GearInTextField textField;
    private GearInBoxSpawner spawner;
    private float spawnTimer;

    @Override
    public void init(CombatEngineAPI engine) {
        this.engine = engine;
        // initLater 在 OpenGL context 创建后调用；isGlobalInitialized() 守卫保证只初始化一次
        if (!BoxUtilModPlugin.isGlobalInitialized()) {
            BoxUtilModPlugin.initLater();
        }
        boxAvailable = BoxConfigs.isShaderEnable() && ShaderCore.isValid();
        textField = new GearInTextField();
        spawner = new GearInBoxSpawner(engine, boxAvailable, textField);
        LOG.info("GearIn box init: shaderEnable=" + BoxConfigs.isShaderEnable()
                + " shaderCoreValid=" + ShaderCore.isValid()
                + " available=" + boxAvailable
                + " font=" + textField.getFontInfo());
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        if (engine == null) {
            return; // 防御性判空（C02-S01 同款姿势）
        }
        if (engine.isPaused()) {
            return; // 暂停守卫：实体生成只在非暂停推进
        }
        spawnTimer += amount;
        if (spawnTimer >= SPAWN_INTERVAL) {
            spawnTimer = 0f;
            spawner.spawnBox();
        }
    }

    @Override
    public void renderInUICoords(ViewportAPI viewport) {
        if (textField == null) {
            return; // 防御性判空（init 未执行时不绘制）
        }
        // 屏幕空间文本的 GL 命令只能在渲染回调里发出（directDraw 责任在调用方）
        textField.drawAt(20f, Global.getSettings().getScreenHeightPixels() - 40f);
    }
}
