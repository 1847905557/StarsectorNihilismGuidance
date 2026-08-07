package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.characters.RelationshipAPI;

/**
 * 人物对玩家关系(RelationshipAPI / RepLevel)：延迟默认 -> adjustRelationship -> setLevel -> setRel 复位。
 */
public class GearInPersonRelationship {

    private static final Logger LOG = Global.getLogger(GearInPersonRelationship.class);

    public void run(PersonAPI personA) {
        RelationshipAPI relToPlayer = personA.getRelToPlayer();
        LOG.info(String.format(
                "GearIn person lesson relToPlayer default: rel=%.2f level=%s hostile=%s",
                relToPlayer.getRel(), relToPlayer.getLevel(), relToPlayer.isHostile()));
        relToPlayer.adjustRelationship(0.3f, null);
        LOG.info(String.format(
                "GearIn person lesson relToPlayer adjust: rel=%.2f level=%s",
                relToPlayer.getRel(), relToPlayer.getLevel()));
        relToPlayer.setLevel(RepLevel.COOPERATIVE);
        LOG.info(String.format(
                "GearIn person lesson relToPlayer cooperative: rel=%.2f level=%s",
                relToPlayer.getRel(), relToPlayer.getLevel()));
        relToPlayer.setLevel(RepLevel.HOSTILE);
        LOG.info(String.format(
                "GearIn person lesson relToPlayer hostile: rel=%.2f level=%s hostile=%s",
                relToPlayer.getRel(), relToPlayer.getLevel(), relToPlayer.isHostile()));
        relToPlayer.setRel(0f);
        LOG.info(String.format(
                "GearIn person lesson relToPlayer reset: rel=%.2f level=%s",
                relToPlayer.getRel(), relToPlayer.getLevel()));
    }
}
