package data.scripts.gearin.modcompat;

import java.util.List;
import java.util.Random;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.intel.specialforces.namer.SpecialForcesNamer;

/**
 * 特遣队命名器：实现 Nexerelin 的 SpecialForcesNamer 接口。
 * 注册方式：gear_industry.json 的 specialForcesNamerClass 指向本类；
 * 由 Nexerelin 在生成特遣队时经脚本类加载器 loadClass + newInstance
 * (无参构造)后调用 getFleetName，至多重试 25 次直到名字不重复。
 *
 * 命名形如 "1st GearIn Prime Forgeguard"：以起源市场命名，编号随机，
 * 降低与既有特遣队撞名概率；origin 缺失时回退势力首个市场，再回退 "GearIn"。
 * 本类只被 Nexerelin 侧加载，齿轮工业自己的代码从不引用它。
 */
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
