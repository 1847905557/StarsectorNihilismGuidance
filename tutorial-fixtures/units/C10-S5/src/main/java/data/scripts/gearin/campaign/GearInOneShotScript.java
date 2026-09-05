package data.scripts.gearin.campaign;

import com.fs.starfarer.api.EveryFrameScript;

/**
 * 一次性演示脚本基类：挂载后第一次 advance() 执行一次子类实现的 runDemo() 并自停
 * (isDone() 返回 true，引擎同帧紧随其后自动从脚本列表移除本脚本)。
 *
 * 两个服务演示脚本 (势力 / 货物) 都继承本类，各自只实现 runDemo()，
 * 避免每个演示类重复 EveryFrameScript 的骨架方法。本类是抽象基类，不参与任何注册；
 * 各子类的注册在 onGameLoad 的 persistent data 门内各自执行。
 */
public abstract class GearInOneShotScript implements EveryFrameScript {

    /** 演示是否已执行；置位后 isDone() 返回 true，引擎同帧紧随其后自动移除本脚本。 */
    private boolean executed = false;

    public GearInOneShotScript() {
        // 空构造：注册由调用方执行 (onGameLoad 的 persistent data 门内)
    }

    @Override
    public boolean isDone() {
        return executed;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        if (executed) {
            return;
        }
        executed = true;
        runDemo();
    }

    /** 子类实现一次演示：只在第一次 advance() 时被调用，全部行为在一个调用内完成。 */
    protected abstract void runDemo();
}
