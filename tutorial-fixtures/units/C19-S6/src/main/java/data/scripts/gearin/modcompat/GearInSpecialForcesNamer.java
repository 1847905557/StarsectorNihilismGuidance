package data.scripts.gearin.modcompat;

import java.util.List;
import java.util.Random;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.intel.specialforces.namer.SpecialForcesNamer;

/** 根据来源市场与随机序数生成特遣队候选名称。 */
public class GearInSpecialForcesNamer implements SpecialForcesNamer {

    private static final Random RANDOM = new Random();

    @Override
    public String getFleetName(CampaignFleetAPI fleet, MarketAPI origin, PersonAPI commander) {
        MarketAPI market = origin;
        if (market == null) {
            List<MarketAPI> markets = Misc.getFactionMarkets(fleet.getFaction());
            market = markets.isEmpty() ? null : markets.get(0);
        }
        String marketName = market == null ? "GearIn" : market.getName();

        int num = RANDOM.nextInt(33) + 1;
        return num + ordinalSuffix(num) + " " + marketName + " Forgeguard";
    }

    private String ordinalSuffix(int num) {
        if (num >= 11 && num <= 13) {
            return "th";
        }
        switch (num % 10) {
            case 1:
                return "st";
            case 2:
                return "nd";
            case 3:
                return "rd";
            default:
                return "th";
        }
    }
}
