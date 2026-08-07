package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;

/**
 * 角色：实体容器持有语义核验(isAlive / getEntityById 与容器成员关系)。
 * 被 GearInLocationScript 在玩家首次进入演示星系时调用一次：
 * 对信标执行 removeEntity / addEntity，记录 isAlive() 与 getEntityById() 的前后变化。
 */
public class GearInContainerCheck {

	private static final Logger LOG = Global.getLogger(GearInContainerCheck.class);

	private final StarSystemAPI demoSystem;
	private final SectorEntityToken beacon;

	public GearInContainerCheck(StarSystemAPI demoSystem, SectorEntityToken beacon) {
		this.demoSystem = demoSystem;
		this.beacon = beacon;
	}

	/** 移除/重新加入核验：只执行一次，展示 isAlive 与 getEntityById 的容器持有语义。 */
	public void runRemoveAddCheck() {
		LOG.info("GearIn loc: enter demo system first time - remove/add demo");
		boolean aliveBefore = beacon.isAlive();
		SectorEntityToken byIdBefore = demoSystem.getEntityById(GearInLocationSystemBuilder.BEACON_ID);

		demoSystem.removeEntity(beacon);
		boolean aliveAfterRemove = beacon.isAlive();
		SectorEntityToken byIdAfterRemove = demoSystem.getEntityById(GearInLocationSystemBuilder.BEACON_ID);
		SectorEntityToken byIdSectorAfterRemove = Global.getSector().getEntityById(GearInLocationSystemBuilder.BEACON_ID);
		boolean listContainsAfterRemove = demoSystem.getAllEntities().contains(beacon);

		demoSystem.addEntity(beacon);
		boolean aliveAfterReadd = beacon.isAlive();
		SectorEntityToken byIdAfterReadd = demoSystem.getEntityById(GearInLocationSystemBuilder.BEACON_ID);

		LOG.info(String.format(
				"GearIn loc: beacon | alive before=%s | byId before=%s | alive after remove=%s | byId after remove=%s | sector byId after remove=%s | inList after remove=%s | alive after re-add=%s | byId after re-add=%s",
				aliveBefore,
				byIdBefore != null,
				aliveAfterRemove,
				byIdAfterRemove != null,
				byIdSectorAfterRemove != null,
				listContainsAfterRemove,
				aliveAfterReadd,
				byIdAfterReadd != null));
	}
}
