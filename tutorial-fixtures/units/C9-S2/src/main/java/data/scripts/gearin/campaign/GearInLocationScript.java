package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;

/**
 * 完整最小例：LocationAPI / StarSystemAPI 实体容器与访问状态（日志前缀 "GearIn loc"）。
 * 注册入口：生涯回调 onGameLoad（调用点片段见页面正文），REGISTER_MARK 一次性门控
 * （C6-S2 的 CLASS_MARK 模式）。编排：星系创建/复用、访问状态记录、容器核验与
 * 周期摘要，分别委托给 GearInLocationSystemBuilder / GearInAccessStateLogger / GearInContainerCheck。
 */
public class GearInLocationScript implements EveryFrameScript {
	/** 存档持久化标记：本脚本是否已在该存档注册过（随存档保存）。 */
	public static final String REGISTER_MARK = "GearIn_LocationScript";
	private static final Logger LOG = Global.getLogger(GearInLocationScript.class);

	/** 状态摘要日志的最小间隔（真实秒）。 */
	private static final float LOG_INTERVAL = 10f;

	private boolean setupDone = false;
	private boolean broken = false;
	private boolean enterDone = false;
	private float sinceLastLog = 0f;
	/** 随存档序列化的引用：演示星系与实体（存档图内持久对象，可安全放脚本字段）。 */
	private StarSystemAPI demoSystem;
	private SectorEntityToken beacon;
	private GearInAccessStateLogger accessStateLogger;
	private GearInContainerCheck containerCheck;

	public GearInLocationScript() {
		// 空构造：注册由调用方执行（onGameLoad 的 persistent data 门内，见页面调用点片段）
	}

	@Override
	public boolean isDone() {
		return false;
	}

	@Override
	public boolean runWhilePaused() {
		return false;
	}

	@Override
	public void advance(float amount) {
		if (!setupDone) {
			setup();
			if (broken) {
				return;
			}
		}

		LocationAPI current = Global.getSector().getCurrentLocation();
		if (accessStateLogger.logLocationChangeIfChanged(current) && current == demoSystem) {
			accessStateLogger.logEnteredDemoSystem();
		}
		if (!enterDone && demoSystem.isCurrentLocation()) {
			enterDone = true;
			containerCheck.runRemoveAddCheck();
		}

		sinceLastLog += amount;
		if (sinceLastLog >= LOG_INTERVAL) {
			sinceLastLog = 0f;
			accessStateLogger.logStateSummary();
		}
	}

	/** 首帧准备：演示星系复用或创建，取回信标引用，装配辅助角色。 */
	private void setup() {
		demoSystem = GearInLocationSystemBuilder.getOrCreate(Global.getSector());
		beacon = demoSystem.getEntityById(GearInLocationSystemBuilder.BEACON_ID);
		if (beacon == null) {
			LOG.error("GearIn loc: beacon not found in demo system, demos disabled");
			broken = true;
			return;
		}
		accessStateLogger = new GearInAccessStateLogger(demoSystem, beacon);
		containerCheck = new GearInContainerCheck(demoSystem, beacon);
		setupDone = true;
	}
}
