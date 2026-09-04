package data.scripts.gearin.lib;

import java.util.Map;

import com.fs.starfarer.api.Global;

public final class GearInRouteRiskRecord {
    private static final String KEY = "$GearIn_routeRisk";

    public void save(float risk) {
        Global.getSector().getPersistentData().put(KEY, risk);
    }

    public float load() {
        Map<String, Object> data = Global.getSector().getPersistentData();
        Object value = data.get(KEY);
        return value instanceof Number ? ((Number) value).floatValue() : 0f;
    }
}
