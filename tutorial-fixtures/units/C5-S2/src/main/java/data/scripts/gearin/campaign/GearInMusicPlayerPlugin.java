package data.scripts.gearin.campaign;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.MusicPlayerPluginImpl;

public class GearInMusicPlayerPlugin extends MusicPlayerPluginImpl {

    @Override
    protected String getMarketMusicSetId(Object param) {
        MarketAPI market = (MarketAPI) param;
        if ("gear_industry".equals(market.getFactionId())) {
            return "GearIn_market_music";
        }
        return super.getMarketMusicSetId(param);
    }
}
