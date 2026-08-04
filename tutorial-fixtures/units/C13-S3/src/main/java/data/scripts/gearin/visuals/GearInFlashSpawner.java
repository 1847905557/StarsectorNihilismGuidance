package data.scripts.gearin.visuals;

import java.awt.Color;

import org.apache.log4j.Logger;
import org.dark.shaders.light.LightShader;
import org.dark.shaders.light.StandardLight;
import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.util.IntervalUtil;

/**
 * 周期闪光生成器（光源实体类）：按固定间隔在玩家舰船周围随机位置生成一盏
 * fadeOut 自灭的短命点光源。闪光的间隔节奏与计数都归本类；由
 * GearInLightCombatPlugin 在非暂停时驱动 update。
 */
public class GearInFlashSpawner {

    private static final Logger LOG = Global.getLogger(GearInFlashSpawner.class);

    /** 周期闪光的间隔（秒）。 */
    private static final float FLASH_INTERVAL = 2f;

    private final IntervalUtil flashInterval = new IntervalUtil(FLASH_INTERVAL, FLASH_INTERVAL);
    private int flashCount;

    /** 每帧推进（须在非暂停时调用）：间隔到点即生成一盏闪光。 */
    public void update(float amount, ShipAPI player) {
        flashInterval.advance(amount);
        if (flashInterval.intervalElapsed()) {
            spawn(player);
        }
    }

    /** 在玩家舰船周围随机位置生成一盏 fadeOut(0.5f) 自灭的点光源（短命光源的标准姿势）。 */
    private void spawn(ShipAPI player) {
        if (player == null || !player.isAlive()) {
            return;
        }
        double angle = Math.random() * Math.PI * 2.0;
        float radius = 60f + (float) (Math.random() * 120f);
        Vector2f loc = new Vector2f(player.getLocation().x + (float) (Math.cos(angle) * radius),
                player.getLocation().y + (float) (Math.sin(angle) * radius));
        StandardLight flash = new StandardLight(loc, new Vector2f(), new Vector2f(), null, 0.8f, 80f);
        flash.setColor(new Color(120, 220, 255));
        flash.fadeOut(0.5f); // intensity 归零后 LightShader.advance 会从光源列表移除该光源
        LightShader.addLight(flash);
        flashCount++;
        LOG.info("GearIn light flash: #" + flashCount + " at (" + (int) loc.x + ", " + (int) loc.y + ")");
    }
}
