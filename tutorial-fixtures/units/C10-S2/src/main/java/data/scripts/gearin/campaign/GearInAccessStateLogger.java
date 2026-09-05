package data.scripts.gearin.campaign;

import java.util.List;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;

/**
 * 角色：访问状态记录 (isCurrentLocation / activeThisFrame / isInCurrentLocation /
 * isAlive / isEnteredByPlayer / getDaysSinceLastPlayerVisit)，三条日志
 * (location 变化、进入演示星系、周期摘要) 由 GearInLocationScript 调用。
 */
public class GearInAccessStateLogger {
    private static final Logger LOG = Global.getLogger(GearInAccessStateLogger.class);
    private final StarSystemAPI demoSystem;
    private final SectorEntityToken beacon;
    private String lastPlayerLocationId = null;

    public GearInAccessStateLogger(StarSystemAPI demoSystem, SectorEntityToken beacon) {
        this.demoSystem = demoSystem;
        this.beacon = beacon;
    }

    /** 玩家所在 location 变化时记录访问状态；返回是否发生了变化。 */
    public boolean logLocationChangeIfChanged(LocationAPI current) {
        String currentId = current == null ? null : current.getId();
        if (lastPlayerLocationId != null && lastPlayerLocationId.equals(currentId)) {
            return false;
        }
        String oldId = lastPlayerLocationId;
        lastPlayerLocationId = currentId;
        StringBuilder sb = new StringBuilder("GearIn loc: player location change");
        if (oldId == null) {
            sb.append(" | first observed: ");
        } else {
            sb.append(" | ").append(oldId).append(" -> ");
        }
        sb.append(currentId);
        if (current != null) {
            sb.append(" [name=").append(current.getName())
                    .append(", isHyperspace=").append(current.isHyperspace())
                    .append(", isCurrentLocation=").append(current.isCurrentLocation())
                    .append(", activeThisFrame=").append(current.activeThisFrame())
                    .append("]");
        }
        LOG.info(sb.toString());
        return true;
    }

    /** 玩家刚进入演示星系：记录访问状态与实体状态。 */
    public void logEnteredDemoSystem() {
        LOG.info(String.format(
                "GearIn loc: demo system entered | isEnteredByPlayer=%s | daysSinceLastVisit=%s | beacon.isInCurrentLocation=%s | beacon.isAlive=%s",
                demoSystem.isEnteredByPlayer(),
                formatDays(demoSystem.getDaysSinceLastPlayerVisit()),
                beacon.isInCurrentLocation(),
                beacon.isAlive()));
    }

    /** 周期状态摘要：演示星系的实体集合与访问状态。 */
    public void logStateSummary() {
        LocationAPI current = Global.getSector().getCurrentLocation();
        List<SectorEntityToken> entities = demoSystem.getAllEntities();
        LOG.info(String.format(
                "GearIn loc: state | current=%s (id=%s) | demoSystem.isCurrentLocation=%s activeThisFrame=%s enteredByPlayer=%s daysSinceVisit=%s | entities=%d planets=%d terrains=%d custom=%d | beacon isAlive=%s inList=%s",
                current == null ? "null" : current.getName(),
                current == null ? "null" : current.getId(),
                demoSystem.isCurrentLocation(),
                demoSystem.activeThisFrame(),
                demoSystem.isEnteredByPlayer(),
                formatDays(demoSystem.getDaysSinceLastPlayerVisit()),
                entities.size(),
                demoSystem.getPlanets().size(),
                demoSystem.getTerrainCopy().size(),
                demoSystem.getCustomEntities().size(),
                beacon.isAlive(),
                entities.contains(beacon)));
    }

    /** Float.MAX_VALUE 表示从未访问过 (getDaysSinceLastPlayerVisit 语义)。 */
    private static String formatDays(float days) {
        if (days >= Float.MAX_VALUE) {
            return "never";
        }
        return String.format("%.1f", days);
    }
}
