package data.scripts.gearin.combat;

import java.awt.Color;
import java.util.List;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.input.InputEventAPI;

/**
 * 每帧战斗插件：演示暂停守卫与状态归属。
 *
 * <p>注册：MOD 的 data/config/settings.json 的 plugins 段写入本类完整类名。
 * 游戏启动时把核心与所有 MOD 的 settings.json 深合并 (文本读取工具.readJSONMerged)，
 * 并把 plugins 段全部类名注册为脚本类；每场战斗开始时为其中每个
 * EveryFrameCombatPlugin 类创建新实例并附加 (脚本类加载器.loadScriptInstance 的
 * newInstance)，战斗结束实例随引擎销毁，不得缓存到下一场战斗。</p>
 *
 * <p>advance() 在战斗暂停期间仍然每帧被调用，amount 也仍然非零；是否推进状态
 * 由插件自己决定：本例只用非暂停帧累加 elapsed，暂停帧只累计调用次数 calls。
 * 初始状态在构造器设置，不依赖已废弃的 init()(不保证在第一次 advance() 之前调用)。</p>
 */
public class GearInPauseAwarePlugin extends BaseEveryFrameCombatPlugin {

    /** 非暂停时间每累计多少秒输出一次报告。 */
    public static final float REPORT_INTERVAL = 10f;

    private CombatEngineAPI engine;
    private float elapsed;
    private float timeSinceLastReport;
    private int calls;
    private int pausedCalls;

    public GearInPauseAwarePlugin() {
        // 初始状态在这里设置；init() 已废弃，不保证在第一次 advance() 之前被调用
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        calls++;
        if (engine == null) {
            engine = Global.getCombatEngine();
            if (engine == null) {
                return;
            }
        }
        if (engine.isPaused()) {
            pausedCalls++;
            return;
        }
        elapsed += amount;
        timeSinceLastReport += amount;
        if (timeSinceLastReport >= REPORT_INTERVAL) {
            timeSinceLastReport = 0f;
            report(engine);
        }
    }

    private void report(CombatEngineAPI engine) {
        String text = "GearIn: elapsed " + Math.round(elapsed) + "s, calls " + calls
                + " (paused " + pausedCalls + ")";
        Global.getLogger(GearInPauseAwarePlugin.class).info(text);
        if (engine.getPlayerShip() != null) {
            engine.addFloatingText(engine.getPlayerShip().getLocation(), text,
                    14f, Color.WHITE, engine.getPlayerShip(), 0f, 3f);
        }
    }
}
