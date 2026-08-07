package data.scripts.gearin.lib;

import java.util.List;

import org.apache.log4j.Logger;
import org.boxutil.BoxUtilModPlugin;
import org.boxutil.config.BoxConfigs;
import org.boxutil.manager.ShaderCore;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.input.InputEventAPI;

/**
 * 完整最小例的接线载体：BoxUtil 初始化(initLater 守卫)、可用性判据与生成
 * 计时在这里，实体生成委托给 GearInBoxSpawner；
 * 由既有战斗插件在 init() 中 addPlugin 注册(C7-S1 模型)，只在本场战斗内有效。
 */
public class GearInBoxEntity extends BaseEveryFrameCombatPlugin {

    private static final Logger LOG = Global.getLogger(GearInBoxEntity.class);

    /** 生成间隔(秒)。 */
    private static final float SPAWN_INTERVAL = 2f;

    private CombatEngineAPI engine;

    /** 初始化可用性：配置开关 && GPU 资源有效，二者共同决定 shader 渲染入口可用性。 */
    private boolean boxAvailable;

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
        spawner = new GearInBoxSpawner(engine, boxAvailable);
        LOG.info("GearIn box init: shaderEnable=" + BoxConfigs.isShaderEnable()
                + " shaderCoreValid=" + ShaderCore.isValid()
                + " available=" + boxAvailable);
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        if (engine == null) {
            return; // 防御性判空(C2-S1 同款姿势)
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

}
