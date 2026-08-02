package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;
import org.json.JSONObject;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.loading.VariantSource;

/**
 * 角色：变体序列化。演示 toJSONObject() 的输出键与 .variant 数据文件一致
 * （displayName/hullId/variantId/fluxVents/fluxCapacitors/weaponGroups/modules/wings/hullMods/permaMods/sMods/tags）。
 *
 * 注册入口：GearInVariantLauncher.register()（跑一次即停）。
 */
public class GearInVariantSerializer implements EveryFrameScript {

    private static final Logger LOG = Global.getLogger(GearInVariantSerializer.class);

    /** 完成标记：isDone() 返回 true 后引擎下一帧自动移除本脚本。 */
    private boolean done = false;

    @Override
    public boolean isDone() {
        return done;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        if (done) {
            return;
        }
        try {
            runOnce();
        } catch (Throwable t) {
            LOG.error("GearIn variant serializer failed", t);
        } finally {
            done = true;
        }
    }

    private void runOnce() {
        // 克隆一份 STOCK 变体并显式改 REFIT 来源后序列化，对照 .variant 文件键
        ShipVariantAPI refit = Global.getSettings().getVariant("brawler_Assault").clone();
        refit.setSource(VariantSource.REFIT);
        try {
            JSONObject json = refit.toJSONObject();
            LOG.info("GearIn refit JSON: " + json.toString(2));
        } catch (Exception e) {
            LOG.error("GearIn variant JSON serialization failed", e);
        }
    }
}
