package data.scripts.gearin.lib;

import org.apache.log4j.Logger;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lazywizard.lazylib.combat.AIUtils;
import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;

/**
 * 数学与几何工具族的用法片段：MathUtils / VectorUtils 的最小消费面。
 *
 * 无持久状态的纯静态工具，角度一律使用度（degrees）；AIUtils 只用于
 * 获取演示目标（最近可见敌舰），查询结果按当前帧快照使用（C07-S01 同规）。
 */
public final class GearInLazyMathUsage {

    private static final Logger LOG = Global.getLogger(GearInLazyMathUsage.class);

    private GearInLazyMathUsage() {
        // 工具类：不实例化
    }

    /** 距离、范围、角度、最短旋转、向量定长与圆周取点各输出一行日志。 */
    public static void report(CombatEngineAPI engine) {
        ShipAPI player = engine.getPlayerShip();
        if (player == null) {
            return; // 防御性判空：getPlayerShip() 战斗内实际永不返回 null（C07-S01）
        }
        Vector2f playerLoc = player.getLocation();
        ShipAPI enemy = AIUtils.getNearestEnemy(player);
        if (enemy == null) {
            LOG.info("GearIn lazy math: no enemy in sight");
            return;
        }
        Vector2f enemyLoc = enemy.getLocation();
        float angleTo = VectorUtils.getAngle(playerLoc, enemyLoc);

        // 原地修改语义：getDirectionalVector 返回新向量；resize 原地改写并返回同一引用
        Vector2f dirToEnemy = VectorUtils.getDirectionalVector(playerLoc, enemyLoc);
        Vector2f spot = VectorUtils.resize(dirToEnemy, 100f); // dirToEnemy 被改写为长度 100
        Vector2f.add(spot, playerLoc, spot);

        LOG.info("GearIn lazy math: dist=" + MathUtils.getDistance(player, enemy)
                + " inRange1200=" + MathUtils.isWithinRange(player, enemy, 1200f)
                + " angleTo=" + angleTo
                + " clampAngle=" + MathUtils.clampAngle(angleTo + 360f)
                + " facing=" + VectorUtils.getFacing(player.getVelocity())
                + " shortest=" + MathUtils.getShortestRotation(VectorUtils.getFacing(player.getVelocity()), angleTo)
                + " spot=(" + spot.getX() + ", " + spot.getY() + ")"
                + " circumference=(" + MathUtils.getPointOnCircumference(playerLoc, 100f, angleTo).getX() + ", "
                + MathUtils.getPointOnCircumference(playerLoc, 100f, angleTo).getY() + ")"
                + " random=" + MathUtils.getRandomNumberInRange(1, 6));
    }
}
