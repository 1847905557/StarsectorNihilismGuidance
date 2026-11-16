# 实现本地化机制

到这里，齿轮工业的界面文字分散在十几个 Java 类里。想把它翻成英文，或者只是改掉一个错字，都得把源码挨个翻一遍，再重新构建。

把文字集中到一个文件里，代码里只留一个键，以后改字只改一处。越早搬越省力，等文字多到上百条再搬，就是一场苦工。

---

_本节预期阅读时间：\~10 分钟。_

_本节预期操作时间：\~15 分钟。_

## 前置知识

- [C14-S3] 的拒绝停靠对话与 [C14-S4] 的巡逻情报，本节把它们的文字搬出去；
- [C7-S2] 中 MOD 数据文件与原版合并的规则；
- JSON 的基本写法与 [C1-S1] 提到的宽松 JSON。

## strings.json 的分类

`data/strings/strings.json` 是一个按分类组织的 JSON，每个分类下是一组键值。加一个齿轮工业自己的分类：

```json
"GearIn_campaign": {
	"dockRefusal":"Nipher Foundry 的港务频道只回了一句话：近期击败过齿轮工业巡逻队的舰队，不予停靠。",
	"leave":"离开",
	"patrolIntelName":"Nipher 的巡逻活动",
	"patrolLine":"%s 指挥的巡逻队：%s",
	"patrolNone":"Nipher 星系中暂时没有齿轮工业的巡逻队。",
},
```

`strings.json` 与原版合并。分类名带上 `GearIn_` 前缀，自己的键就不会和原版或别的 MOD 撞上，分类下面的键也就不必再带前缀。

翻译时，换掉这个文件里的文字即可，键与代码都不动。

## 集中取值

文字通过 `getString(category, id)` 读取。分类名到处写一遍很快就会写错，用一个小类包起来：

```java
public static String get(String id) {
    return Global.getSettings().getString(CATEGORY, id);
}

public static String format(String id, Object... args) {
    return String.format(get(id), args);
}
```

`SettingsAPI` 里还有一个只带一个参数的 `getString(key)`，它读的是 `settings.json` 里的字符串，不是 `strings.json`，两者不要混用。

带参数的文字用 `%s` 占位，`format(...)` 按顺序替换。不要在代码里把文字拆成几段再拼起来：换一种语言，语序就变了，拼好的句子会读不通；一整句话加占位符，译者可以自由调整位置。

## 搬走已有的文字

拒绝停靠对话里的两处文字换成键：

```java
dialog.getTextPanel().addPara(GearInI18n.get("dockRefusal"));
dialog.getOptionPanel().addOption(GearInI18n.get("leave"), Option.LEAVE);
```

巡逻情报的标题、每一行的格式与 "暂时没有巡逻队" 也一样换掉。情报那一行原本就用了 `%s` 高亮指挥官与任务，现在格式本身也来自 `strings.json`：

```java
info.addPara(GearInI18n.get("patrolLine"), opad, Misc.getHighlightColor(), commander, action);
```

其余页面里的文字照这个办法逐个搬过去即可。规则表里的文字本来就在数据文件里，翻译时直接改 `rules.csv`，不必再搬一次。

## 完整的取值类与拒绝停靠对话

`src/data/scripts/gearin/campaign/GearInI18n.java`：

```java
package data.scripts.gearin.campaign;

import com.fs.starfarer.api.Global;

/** 齿轮工业的界面文字：集中从 strings.json 的 GearIn_campaign 分类里取。 */
public final class GearInI18n {

    public static final String CATEGORY = "GearIn_campaign";

    private GearInI18n() {
    }

    public static String get(String id) {
        return Global.getSettings().getString(CATEGORY, id);
    }

    /** 文字里的 %s 依次替换为参数。 */
    public static String format(String id, Object... args) {
        return String.format(get(id), args);
    }
}
```

`src/data/scripts/gearin/campaign/GearInDockRefusalDialog.java`：

```java
package data.scripts.gearin.campaign;

import java.util.Map;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;

/** Foundry 的港务频道拒绝停靠：只说明原因，留一个离开的选项。 */
public class GearInDockRefusalDialog implements InteractionDialogPlugin {

    private enum Option {
        LEAVE
    }

    private InteractionDialogAPI dialog;

    @Override
    public void init(InteractionDialogAPI dialog) {
        this.dialog = dialog;
        dialog.getTextPanel().addPara(GearInI18n.get("dockRefusal"));
        dialog.getOptionPanel().addOption(GearInI18n.get("leave"), Option.LEAVE);
    }

    @Override
    public void optionSelected(String optionText, Object optionData) {
        if (optionData == Option.LEAVE) {
            dialog.dismiss();
        }
    }

    @Override
    public void optionMousedOver(String optionText, Object optionData) {
    }

    @Override
    public void advance(float amount) {
    }

    @Override
    public void backFromEngagement(EngagementResultAPI battleResult) {
    }

    @Override
    public Object getContext() {
        return null;
    }

    @Override
    public Map<String, MemoryAPI> getMemoryMap() {
        return null;
    }
}
```

### 进游戏核对

执行 `gradle jar`，重启游戏，载入一份记过仇的存档 (或按 [C14-S2] 击败一支巡逻队)。

- 停靠 Foundry，拒绝停靠的文字与改动之前完全相同；
- 把 `strings.json` 中 `dockRefusal` 的文字改掉，重启游戏 (不必重新构建)，再停靠，显示的是新文字；
- 打开巡逻情报，标题与每一行的格式都来自 `strings.json`。

页面中的现象依据 API 推出，编者尚未逐项在游戏内实测。

## 常见问题

- **界面上显示的是键名，或者是一段报错文字**：分类名或键写错，`strings.json` 里找不到。
    - **解决方案**：分类名 `GearIn_campaign` 与键逐字核对。

- **启动时报 `strings.json` 解析错误**：某一行少了逗号或引号。
    - **解决方案**：对照报错的行号检查。

- **格式化之后出现多余的 `%s`**：参数个数少于占位符个数。
    - **解决方案**：参数与 `%s` 一一对应。

## 建议参考项

- `com.fs.starfarer.api.SettingsAPI`：`getString(category, id)` 与 `getString(key)` 的区别。
- `data/strings/strings.json`：原版的分类与写法。
