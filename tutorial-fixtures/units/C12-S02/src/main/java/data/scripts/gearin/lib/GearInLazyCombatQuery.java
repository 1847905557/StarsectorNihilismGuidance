package data.scripts.gearin.lib;

import java.util.List;

import org.apache.log4j.Logger;
import org.lazywizard.lazylib.combat.AIUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;

/**
 * 战斗查询工具族的用法片段：CombatUtils / AIUtils 的最小消费面。
 *
 * 查询结果是当前帧引擎状态快照，实体仍归战斗引擎管理，结果禁止跨战斗
 * 缓存（C07-S01 同规）；@Nullable 返回值（getNearestEnemy /
 * getBestInterceptPoint）使用前必须判空。
 */
public final class GearInLazyCombatQuery {

    private static final Logger LOG = Global.getLogger(GearInLazyCombatQuery.class);

    private GearInLazyCombatQuery() {
        // 工具类：不实例化
    }

    /** 范围列表、可见性统计与拦截点各输出一行日志。 */
    public static void report(CombatEngineAPI engine, int reportCount) {
        ShipAPI player = engine.getPlayerShip();
        if (player == null) {
            return;
        }
        List<ShipAPI> ships = CombatUtils.getShipsWithinRange(player.getLocation(), 2000f);
        int alive = 0;
        int visible = 0;
        for (ShipAPI ship : ships) {
            if (ship.isAlive()) {
                alive++; // 快照可能含残骸，按 C07-S01 的 isAlive() 纪律过滤
            }
            if (CombatUtils.isVisibleToSide(ship, player.getOwner())) {
                visible++;
            }
        }
        ShipAPI enemy = AIUtils.getNearestEnemy(player);
        String intercept = "none";
        if (enemy != null) {
            Vector2f point = AIUtils.getBestInterceptPoint(player.getLocation(), 400f,
                    enemy.getLocation(), enemy.getVelocity());
            intercept = point == null ? "none" : "(" + point.getX() + ", " + point.getY() + ")";
        }
        LOG.info("GearIn lazy combat: inRange2000=" + ships.size()
                + " alive=" + alive + " visible=" + visible
                + " intercept=" + intercept
                + " reports=" + reportCount);
    }
}
