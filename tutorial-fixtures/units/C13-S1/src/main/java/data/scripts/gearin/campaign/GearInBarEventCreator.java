package data.scripts.gearin.campaign;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.intel.bar.PortsideBarEvent;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.BaseBarEventCreator;

/**
 * 完整最小例：酒吧事件 creator(GenericBarEventCreator)—— 注册入口与抽取参数。
 *
 * 注册入口：BarEventManager.getInstance().addEventCreator(this)，调用点片段见页面正文
 * (GearInModPlugin.onGameLoad 内的注册块)。manager 实例与 creator 列表存放在
 * sector memory(键 $core_genericBarEventManager)，随存档保存，因此用
 * hasEventCreator(Class) 作为持久化门控(原版 CoreLifecyclePluginImpl.addBarEvents
 * 与两个成熟实例同款写法)，不需要额外的 persistent data 键。
 *
 * 抽取参数(全部在 BaseBarEventCreator 有默认值，本示例只覆写 createBarEvent)：
 * - getBarEventFrequencyWeight()：普通 picker 的抽取权重，默认 10f；
 * - getBarEventActiveDuration()：事件在 active 跟踪器中的时长(游戏天)，默认 30+rand*10；
 * - getBarEventTimeoutDuration()：事件未交互、active 过期移除后 creator 的冷却(游戏天)，默认 30+rand*10；
 * - getBarEventAcceptedTimeoutDuration()：玩家交互(接受或拒绝都算)后的冷却(游戏天)，默认 60+rand*30；
 * - isPriority()：true 时进入 priority picker(先于普通 picker 抽取)，默认 false。
 */
public class GearInBarEventCreator extends BaseBarEventCreator {

    private static final Logger LOG = Global.getLogger(GearInBarEventCreator.class);

    @Override
    public PortsideBarEvent createBarEvent() {
        LOG.info("GearIn bar event: creator picked, creating event (id=" + getBarEventId() + ")");
        return new GearInBarEvent();
    }
}
