package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;

/**
 * 角色：位置 token 创建 (LocationAPI.createToken 语义演示)。
 * 被 GearInLocationSystemBuilder 在填充星系时调用：createToken 初始不在实体集合里，
 * 需要轨道或被按 id 查询时，必须先 addEntity 加入容器。
 */
public class GearInTokenCreator {

    private static final Logger LOG = Global.getLogger(GearInTokenCreator.class);

    private GearInTokenCreator() {
        // 工具类：实例化由调用方承担 (静态入口 create)
    }

    /** 创建并加入位置 token，记录 createToken 语义 (初始不在实体集合中)。 */
    public static void create(StarSystemAPI system) {
        SectorEntityToken token = system.createToken(0f, 0f);
        LOG.info(String.format(
                "GearIn loc: token created | id=%s | inList before add=%s | isAlive before add=%s",
                token.getId(),
                system.getAllEntities().contains(token),
                token.isAlive()));
        token.setLocation(-2000f, -1500f);
        system.addEntity(token);
        LOG.info(String.format(
                "GearIn loc: token added | inList after add=%s | byId found=%s",
                system.getAllEntities().contains(token),
                system.getEntityById(token.getId()) != null));
    }
}
