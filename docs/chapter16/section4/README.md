# 构建阶段任务

Ashen Verge 那座信标挂着齿轮工业的旗子，记录却一直传不回来。Aster 想知道里面写了什么，又不打算自己派船去。这就是一份委托：去一个地方，做一件事，回来报告，拿钱。

原版的任务框架把这种先做一件、再做一件的事情拆成一个个阶段，每个阶段由某个标记推进。写任务，大半是在把阶段与标记对上。

---

_本节预期阅读时间：\~15 分钟。_

_本节预期操作时间：\~20 分钟。_

## 前置知识

- [C13-S1] 的 Aster Vance，她是委托人；
- [C10-S2] 与 [C10-S4] 的灰烬信标，ID 为 `GearIn_ashen_beacon`；
- [C15-S5] 中规则读写各块 Memory 的前缀；
- [C14-S4] 的情报，任务会以情报的形式出现在情报栏里。

## 继承哪一个类

原版的委托任务大多继承 `HubMissionWithBarEvent`。它既能由联系人发出，也能放进酒吧，任务的情报、地图标记、奖励发放都由父类处理。原版的 `SpySatDeployment` 只有一百多行，就是这一类任务最好的参照。

任务要写的东西集中在 `create(...)`：任务创建时调用一次，返回 `false` 表示这一次不发这个任务。

## 只在 Foundry 发出

`shouldShowAtMarket(...)` 在任务创建之前调用，决定这个市场能不能发它：

```java
return GearInFoundryGenerator.MARKET_ID.equals(market.getId())
        && !Global.getSector().getMemoryWithoutUpdate().getBoolean(COMPLETED_KEY);
```

委托只在 Foundry 发出，完成过一次就不再出现。完成标记由 [C16-S5] 的报告规则写入。

## 委托人与引用

委托人不是随机生成的路人，而是 Aster 本人：

```java
setPersonOverride(aster);
```

原版 `AFavorForKanta` 让固定的人物发任务，也是这样写的。

接着在全局 Memory 里放一个指向任务自己的引用：

```java
if (!setGlobalReference("$GearIn_bsv_ref", "$GearIn_bsv_inProgress")) {
    return false;
}
```

规则表靠 `$GearIn_bsv_ref` 找到这个任务对象，调用它的方法。引用已经存在，说明同一个任务正在进行，这一次就不再发。第二个参数是 "进行中" 的标记，任务结束时两者都由父类撤掉。

`GearIn_bsv` 是这个任务的 ID，规则里的触发名、Memory 键都以它开头。

## 阶段

阶段用一个枚举表示：

```java
public static enum Stage {
    SURVEY,
    REPORT,
    COMPLETED
}
```

```java
setStartingStage(Stage.SURVEY);
setSuccessStage(Stage.COMPLETED);
```

起始阶段是去读信标，成功阶段是向 Aster 报告完毕。进入成功阶段时，父类发放奖励、结束任务。

这份委托没有失败阶段，也没有时限。信标不会跑掉，Aster 也不急；原版 `SpySatDeployment` 给了 120 天时限和失败阶段，那是因为情报会过时。

## 用标记推进阶段

阶段之间靠 Memory 里的标记推进：

```java
setStageOnMemoryFlag(Stage.REPORT, beacon, "$GearIn_bsv_surveyed");
setStageOnMemoryFlag(Stage.COMPLETED, aster, "$GearIn_bsv_reported");
```

信标的 Memory 里出现 `$GearIn_bsv_surveyed`，任务进入报告阶段；Aster 的 Memory 里出现 `$GearIn_bsv_reported`，任务完成。这两个标记由 [C16-S5] 的规则在玩家读取信标、回来报告时写入。

**任务类只管什么标记推进到什么阶段，写标记的是规则。** 两边按键名对上，任务类不需要知道玩家是怎么读取信标的。

## 标出重要的对象

```java
makeImportant(beacon, "$GearIn_bsv_target", Stage.SURVEY);
makeImportant(aster, "$GearIn_bsv_report", Stage.REPORT);
```

`makeImportant(...)` 在指定的阶段里，给对象标上任务标记，并在它的 Memory 里写一个键。调查阶段，信标在星图上被标为任务目标，Memory 里有 `$GearIn_bsv_target`；报告阶段，Aster 被标为任务人物，Memory 里有 `$GearIn_bsv_report`。阶段一过，标记与键都由父类撤掉。

[C16-S5] 的规则就用这两个键判断现在是不是该插话。

## 奖励与界面文字

奖励固定 30000 星币：

```java
setCreditReward(REWARD);
```

原版的委托大多用 `CreditReward` 按市场规模随机定价，这份委托只有一份，给个整数就好。

`updateInteractionDataImpl()` 把规则需要的数据写进任务的 Memory，这里只有奖励金额。`addDescriptionForNonEndStage(...)` 与 `addNextStepText(...)` 是情报栏里的说明与 "下一步" 提示，按当前阶段分别写；`getBaseName()` 是任务名。

## 完整的任务类

`src/data/scripts/gearin/campaign/GearInBeaconSurveyMission.java`：

```java
package data.scripts.gearin.campaign;

import java.awt.Color;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithBarEvent;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

/** Aster 委托的调查：去 Ashen Verge 读取灰烬信标的记录，再回 Foundry 向她报告。只在 Foundry 发出，完成后不再出现。 */
public class GearInBeaconSurveyMission extends HubMissionWithBarEvent {

    /** 全局 Memory：这项委托已经完成过。 */
    public static final String COMPLETED_KEY = "$GearIn_bsv_completed";

    private static final int REWARD = 30000;

    public static enum Stage {
        SURVEY,
        REPORT,
        COMPLETED
    }

    @Override
    public boolean shouldShowAtMarket(MarketAPI market) {
        return GearInFoundryGenerator.MARKET_ID.equals(market.getId())
                && !Global.getSector().getMemoryWithoutUpdate().getBoolean(COMPLETED_KEY);
    }

    @Override
    protected boolean create(MarketAPI createdAt, boolean barEvent) {
        PersonAPI aster = Global.getSector().getImportantPeople().getPerson(GearInAsterContact.PERSON_ID);
        SectorEntityToken beacon = Global.getSector().getEntityById(GearInAshenVergeGenerator.BEACON_ID);
        if (aster == null || beacon == null) {
            return false;
        }
        setPersonOverride(aster);

        if (!setGlobalReference("$GearIn_bsv_ref", "$GearIn_bsv_inProgress")) {
            return false;
        }

        makeImportant(beacon, "$GearIn_bsv_target", Stage.SURVEY);
        makeImportant(aster, "$GearIn_bsv_report", Stage.REPORT);

        setStartingStage(Stage.SURVEY);
        setSuccessStage(Stage.COMPLETED);

        setStageOnMemoryFlag(Stage.REPORT, beacon, "$GearIn_bsv_surveyed");
        setStageOnMemoryFlag(Stage.COMPLETED, aster, "$GearIn_bsv_reported");

        setCreditReward(REWARD);
        return true;
    }

    @Override
    protected void updateInteractionDataImpl() {
        set("$GearIn_bsv_reward", Misc.getWithDGS(getCreditsReward()));
    }

    @Override
    public void addDescriptionForNonEndStage(TooltipMakerAPI info, float width, float height) {
        float opad = 10f;
        if (currentStage == Stage.SURVEY) {
            info.addPara("前往 Ashen Verge，读取灰烬信标的记录。", opad);
        } else if (currentStage == Stage.REPORT) {
            info.addPara("回到 Nipher Foundry，向 Aster Vance 报告信标的记录。", opad);
        }
    }

    @Override
    public boolean addNextStepText(TooltipMakerAPI info, Color tc, float pad) {
        if (currentStage == Stage.SURVEY) {
            info.addPara("读取 Ashen Verge 的灰烬信标", tc, pad);
            return true;
        }
        if (currentStage == Stage.REPORT) {
            info.addPara("向 Nipher Foundry 的 Aster Vance 报告", tc, pad);
            return true;
        }
        return false;
    }

    @Override
    public String getBaseName() {
        return "灰烬信标的记录";
    }
}
```

这个类自己还不会出现在游戏里：没有登记，没有人发它，也没有规则写标记。[C16-S5] 把它接进 Foundry 的酒吧。

## 常见问题

- **任务一直不出现**：`create(...)` 返回了 `false`，常见于 Aster 或信标不存在。
    - **解决方案**：使用新档测试，确认两者都已生成。

- **读完信标之后任务没有推进**：写入标记的 Memory 与 `setStageOnMemoryFlag(...)` 指定的对象不同，或者键名拼错。
    - **解决方案**：标记写在信标自己的 Memory 上，键名逐字核对。

- **完成之后还能再接一次**：完成标记没有写入，或者 `shouldShowAtMarket(...)` 没有检查它。
    - **解决方案**：两处都使用 `$GearIn_bsv_completed`。

## 建议参考项

- `com.fs.starfarer.api.impl.campaign.missions.hub.BaseHubMission`：阶段、标记、奖励的全部方法。
- `com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithBarEvent`：酒吧任务的基类。
- `com.fs.starfarer.api.impl.campaign.missions.SpySatDeployment`：原版最短的委托任务之一。
- `com.fs.starfarer.api.impl.campaign.missions.AFavorForKanta`：固定人物发任务的写法。
