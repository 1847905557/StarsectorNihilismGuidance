package data.scripts.gearin.lib;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;
import org.boxutil.base.api.InstanceDataAPI;
import org.boxutil.define.BoxEnum;
import org.boxutil.manager.CombatRenderingManager;
import org.boxutil.units.standard.attribute.Instance2Data;
import org.boxutil.units.standard.entity.SpriteEntity;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEngineLayers;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;

/**
 * 方框实体生成（BoxUtil Entity / 实例数据工具族）：SpriteEntity + Instance2Data，
 * 写入实例字段只是客户端状态，必须 setInstanceDataRefreshAllFromCurrentIndex() +
 * submitInstanceData() + setRenderingCount() 完成提交（addInstanceData 成功
 * 不等于 GPU 已上传）；addEntity 返回非 STATE_SUCCESS 时显式 delete()；
 * BoxUtil 不可用时回退原版粒子（降级分支）。
 */
public class GearInBoxSpawner {

    private static final Logger LOG = Global.getLogger(GearInBoxSpawner.class);
    private static final String SPRITE_PATH = "graphics/fx/particlealpha32sq.png";
    private static final float BOX_IN = 0.2f;
    private static final float BOX_FULL = 1.5f;
    private static final float BOX_OUT = 0.5f;

    private final CombatEngineAPI engine;
    private final boolean boxAvailable;
    private final GearInTextField textField;
    private int boxCount;

    public GearInBoxSpawner(CombatEngineAPI engine, boolean boxAvailable, GearInTextField textField) {
        this.engine = engine;
        this.boxAvailable = boxAvailable;
        this.textField = textField;
    }

    public void spawnBox() {
        ShipAPI player = engine.getPlayerShip();
        if (player == null || !player.isAlive()) {
            return;
        }
        if (!boxAvailable) {
            // 降级分支：shader 渲染入口不可用时，BoxUtil 实体不能作为唯一视觉输出
            engine.addSmoothParticle(player.getLocation(), player.getVelocity(),
                    24f, 0.6f, BOX_IN + BOX_FULL + BOX_OUT, Color.CYAN);
            LOG.info("GearIn box: box unavailable, vanilla fallback particle");
            return;
        }
        SpriteAPI sprite = Global.getSettings().getSprite(SPRITE_PATH);
        SpriteEntity box = new SpriteEntity(sprite);
        box.getMaterialData().setColor(Color.CYAN);
        box.setLayer(CombatEngineLayers.ABOVE_PARTICLES_LOWER);
        box.setAdditiveBlend();
        // 实例数据提交链：写字段只是客户端状态，refresh + submit + count 才是提交点
        Instance2Data data = new Instance2Data();
        data.setFacing(player.getFacing());
        data.setTurnRate(90f);
        data.setScale(1f, 1f);
        data.setScaleRate(0.5f, 0.5f);
        data.setTimer(BOX_IN, BOX_FULL, BOX_OUT);
        List<InstanceDataAPI> instances = new ArrayList<>();
        instances.add(data);
        box.setInstanceData(instances);
        box.setInstanceDataRefreshAllFromCurrentIndex();
        box.submitInstanceData();
        box.setRenderingCount(instances.size());
        box.setAlwaysRefreshInstanceData(true);
        // 生命周期：跟随控制数据每帧同步位置/朝向，全局计时到期后实体退出渲染
        box.setControlData(new GearInFollowControlData(player));
        box.setGlobalTimer(BOX_IN, BOX_FULL, BOX_OUT);
        byte result = CombatRenderingManager.addEntity(box);
        if (result != BoxEnum.STATE_SUCCESS) {
            box.delete(); // 入队失败：显式删除，不能当已入队继续推进
            LOG.warn("GearIn box: addEntity failed result=" + result);
            return;
        }
        boxCount++;
        textField.setText("box #" + boxCount);
        LOG.info("GearIn box: #" + boxCount
                + " hull=" + player.getHullSpec().getHullName()
                + " layer=" + box.getCombatLayer());
    }
}
