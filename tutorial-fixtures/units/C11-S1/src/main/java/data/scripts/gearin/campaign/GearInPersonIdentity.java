package data.scripts.gearin.campaign;

import java.util.Random;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PersonImportance;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.characters.FullName.Gender;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;

/**
 * 人物创建与身份(PersonAPI)：createPerson 默认值、姓名/性别/势力/rank/post/personality/importance+voice、标签与内存。
 */
public class GearInPersonIdentity {

    private static final Logger LOG = Global.getLogger(GearInPersonIdentity.class);

    /** 创建人物并写入身份与附属数据，返回该人物供后续角色小节使用。 */
    public PersonAPI run() {
        PersonAPI personA = Global.getFactory().createPerson();
        LOG.info(String.format(
                "GearIn person lesson personA defaults: id=%s personality=%s name='%s' rankId=%s postId=%s faction=%s portraitIsDefault=%s importance=%s",
                personA.getId(), personA.getPersonalityAPI().getId(), personA.getNameString(),
                personA.getRankId(), personA.getPostId(), personA.getFaction().getId(),
                personA.isDefault(), personA.getImportance()));

        personA.setFaction("hegemony");
        personA.setName(new FullName("Aster", "Vance", Gender.FEMALE));
        personA.setRankId(Ranks.SPACE_COMMANDER);
        personA.setPostId(Ranks.POST_FLEET_COMMANDER);
        personA.setPersonality("reckless");
        personA.setImportanceAndVoice(PersonImportance.HIGH, new Random());
        LOG.info(String.format(
                "GearIn person lesson personA identity: name=%s female=%s rank=%s post=%s personality=%s faction=%s voice=%s",
                personA.getNameString(), personA.isFemale(), personA.getRank(), personA.getPost(),
                personA.getPersonalityAPI().getId(), personA.getFaction().getId(), personA.getVoice()));

        personA.addTag("GearIn_personLesson");
        personA.getMemoryWithoutUpdate().set("$GearIn_personLesson", 42);
        LOG.info(String.format(
                "GearIn person lesson tagsMemory: hasTag=%s tags=%d memory=%d",
                personA.hasTag("GearIn_personLesson"), personA.getTags().size(),
                personA.getMemoryWithoutUpdate().getInt("$GearIn_personLesson")));
        return personA;
    }
}
