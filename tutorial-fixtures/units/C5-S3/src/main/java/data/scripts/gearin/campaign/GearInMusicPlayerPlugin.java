package data.scripts.gearin.campaign;

import com.fs.starfarer.api.impl.MusicPlayerPluginImpl;

public class GearInMusicPlayerPlugin extends MusicPlayerPluginImpl {
    @Override
    protected String getMarketMusicSetId(Object param) {
        return "GearIn_market_theme";
    }
}
