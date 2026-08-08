package data.scripts.gearin.visuals;

import org.apache.log4j.Logger;
import org.dark.shaders.light.LightData;
import org.dark.shaders.light.LightData.LightDataType;
import org.dark.shaders.light.LightEntry;
import org.dark.shaders.light.LightShader;
import org.dark.shaders.util.ShaderAPI;
import org.dark.shaders.util.ShaderLib;
import org.dark.shaders.util.TextureData;
import org.dark.shaders.util.TextureData.ObjectType;
import org.dark.shaders.util.TextureData.TextureDataType;
import org.dark.shaders.util.TextureEntry;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;

/**
 * GraphicsLib 初始化状态与数据注册状态的日志报告 (查询面，注册/初始化报告类)。
 *
 * 报告内容：能力门禁与光源 shader 可用性 (必须在 ShaderLib.init 之后读取)、
 * LightData 注册表查询 (projectile/beam 自动光源数据)、TextureData 注册表查询
 * (玩家舰船船体的 material/normal/surface 贴图条目)。只做查询与日志，不创建光源。
 */
public class GearInLightReporter {

    private static final Logger LOG = Global.getLogger(GearInLightReporter.class);

    /** 能力门禁与光源 shader 可用性报告 (必须在 ShaderLib.init 之后读取)。 */
    public static void reportCapabilities() {
        boolean shaders = ShaderLib.areShadersAllowed();
        boolean buffers = ShaderLib.areBuffersAllowed();
        ShaderAPI lightShader = ShaderLib.getShaderAPI(LightShader.class);
        boolean lightReady = lightShader != null && lightShader.isEnabled();
        LOG.info("GearIn light capabilities: shaders=" + shaders + " buffers=" + buffers
                + " lightShaderEnabled=" + lightReady);
    }

    /** 查询 LightData 注册表 (projectile/beam 自动光源数据)，报告消费侧查询面。 */
    public static void reportLightData(String id, LightDataType type) {
        LightEntry entry = LightData.getLightData(id, type);
        if (entry == null) {
            LOG.info("GearIn light data: " + id + " (" + type.name().toLowerCase() + ") not registered");
            return;
        }
        LOG.info("GearIn light data: " + id + " (" + type.name().toLowerCase() + ")"
                + " standard=" + entry.hasStandard
                + " size=" + entry.standardSize
                + " intensity=" + entry.standardIntensity
                + " chance=" + entry.chance
                + " fighterDim=" + entry.fighterDim);
    }

    /** 查询 TextureData 注册表：玩家舰船船体的 material/normal/surface 贴图条目。 */
    public static void reportShipTextures(CombatEngineAPI engine) {
        ShipAPI player = engine.getPlayerShip();
        if (player == null) {
            return;
        }
        String hullId = player.getHullSpec().getHullId();
        LOG.info("GearIn light texture: hull=" + hullId
                + " material=" + describeTexture(hullId, TextureDataType.MATERIAL_MAP)
                + " normal=" + describeTexture(hullId, TextureDataType.NORMAL_MAP)
                + " surface=" + describeTexture(hullId, TextureDataType.SURFACE_MAP)
                + " shipTex=" + describeEntry(
                        ShaderLib.getShipTexture(player, TextureDataType.MATERIAL_MAP)));
    }

    private static String describeTexture(String id, TextureDataType map) {
        return describeEntry(TextureData.getTextureData(id, map, ObjectType.SHIP, 0));
    }

    private static String describeEntry(TextureEntry entry) {
        if (entry == null) {
            return "none";
        }
        return "mag=" + entry.magnitude + " tex=" + entry.sprite.getTextureId();
    }
}
