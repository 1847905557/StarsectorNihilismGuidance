package data.scripts.gearin.visuals;

import java.awt.Color;

import org.apache.log4j.Logger;
import org.dark.shaders.light.LightShader;
import org.dark.shaders.light.StandardLight;
import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;

/**
 * 挂接到玩家舰船的常驻点光源实体（光源实体类）。
 *
 * 生命周期：战斗首帧由 GearInLightCombatPlugin 驱动 update 时创建（此时战斗
 * LocalData 已存在，addLight 不再静默跳过），makePermanent 关闭 60 秒 failsafe；
 * 玩家舰船死亡后 fadeOut(1f) 自灭，由 LightShader.advance 在 intensity 归零后移除。
 */
public class GearInShipLight {

    private static final Logger LOG = Global.getLogger(GearInShipLight.class);

    /** 挂接到玩家舰船的暖色点光源参数。 */
    private static final Vector2f OFFSET = new Vector2f(0f, 60f);
    private static final float BASE_INTENSITY = 0.45f;
    private static final float FIRING_INTENSITY = 1.5f;
    private static final float INTENSITY_CHANGE_PER_SECOND = 2f;
    private static final float SIZE = 150f;

    private StandardLight light;
    private boolean added;
    private float intensity = BASE_INTENSITY;

    /** 每帧推进：首帧创建挂接光，舰船死亡后主动 fadeOut 让引擎移除。 */
    public void update(float amount, ShipAPI player) {
        if (player == null) {
            return;
        }
        if (light == null) {
            if (!added && player.isAlive()) {
                add(player); // 延迟到战斗首帧：保证战斗 LocalData 已存在
            }
            return;
        }
        if (!player.isAlive() && !light.isFadingOut()) {
            light.fadeOut(1f);
            LOG.info("GearIn light ship: player destroyed, fading out attached light");
            return;
        }
        boolean firing = false;
        for (WeaponAPI weapon : player.getAllWeapons()) {
            firing |= weapon.isFiring();
        }
        float target = firing ? FIRING_INTENSITY : BASE_INTENSITY;
        float change = Math.min(Math.abs(target - intensity),
                amount * INTENSITY_CHANGE_PER_SECOND);
        intensity += Math.signum(target - intensity) * change;
        light.setIntensity(intensity);
    }

    /** 创建并挂接常驻点光源：跟随舰船移动，关闭 60 秒 failsafe。 */
    private void add(ShipAPI player) {
        added = true;
        light = new StandardLight(player.getLocation(), new Vector2f(), OFFSET, player,
                BASE_INTENSITY, SIZE);
        light.setColor(new Color(255, 210, 130));
        light.makePermanent(); // lifetime 与 superLifetime 均置 -1，不再受 failsafe 限制
        LightShader.addLight(light);
        LOG.info("GearIn light ship: attached light added offset=" + OFFSET
                + " size=" + SIZE + " intensity=" + BASE_INTENSITY);
    }
}
