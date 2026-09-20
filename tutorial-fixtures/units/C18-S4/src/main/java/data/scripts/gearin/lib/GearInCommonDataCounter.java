package data.scripts.gearin.lib;

import org.lazywizard.lazylib.JSONUtils;
import org.lazywizard.lazylib.JSONUtils.CommonDataJSONObject;

public final class GearInCommonDataCounter {
    private static final String COMMON_FILE_NAME = "GearIn_common_counter";
    private static final String DEFAULT_JSON_PATH =
            "data/config/GearIn_common_defaults.json";
    private static final String LOAD_COUNT_KEY = "load_count";
    private static final String LAST_PLAYER_KEY = "last_player";

    private GearInCommonDataCounter() {
    }

    public static LoadSnapshot recordLoad(String playerName) throws Exception {
        try (CommonDataJSONObject data = JSONUtils.loadCommonJSON(
                COMMON_FILE_NAME, DEFAULT_JSON_PATH)) {
            int previousLoadCount = data.optInt(LOAD_COUNT_KEY, 0);
            String previousPlayerName = data.optString(LAST_PLAYER_KEY, "");
            int currentLoadCount = previousLoadCount + 1;
            String currentPlayerName = playerName == null ? "" : playerName;

            data.put(LOAD_COUNT_KEY, currentLoadCount);
            data.put(LAST_PLAYER_KEY, currentPlayerName);

            return new LoadSnapshot(
                    previousLoadCount,
                    currentLoadCount,
                    previousPlayerName,
                    currentPlayerName);
        }
    }

    public static void reset() throws Exception {
        try (CommonDataJSONObject data = JSONUtils.loadCommonJSON(
                COMMON_FILE_NAME, DEFAULT_JSON_PATH)) {
            JSONUtils.clear(data);
            data.put(LOAD_COUNT_KEY, 0);
            data.put(LAST_PLAYER_KEY, "");
        }
    }

    public static final class LoadSnapshot {
        private final int previousLoadCount;
        private final int currentLoadCount;
        private final String previousPlayerName;
        private final String currentPlayerName;

        private LoadSnapshot(int previousLoadCount, int currentLoadCount,
                String previousPlayerName, String currentPlayerName) {
            this.previousLoadCount = previousLoadCount;
            this.currentLoadCount = currentLoadCount;
            this.previousPlayerName = previousPlayerName;
            this.currentPlayerName = currentPlayerName;
        }

        public int getPreviousLoadCount() {
            return previousLoadCount;
        }

        public int getCurrentLoadCount() {
            return currentLoadCount;
        }

        public String getPreviousPlayerName() {
            return previousPlayerName;
        }

        public String getCurrentPlayerName() {
            return currentPlayerName;
        }
    }
}
