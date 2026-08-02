package data.scripts.gearin.basics;

/**
 * 演示运行器：模拟游戏消费者，按帧调用插件回调并打印累计结果。
 *
 * <p>运行 {@code main} 输出 {@code 0.25}：第一帧 {@code 0.25f} 计入，
 * 第二帧以暂停状态传入 {@code 5f}，被 {@link GearInCounterPlugin} 的守卫拦下。</p>
 */
public final class GearInCallbackRunner {

    private GearInCallbackRunner() {
    }

    public static void main(String[] args) {
        GearInCounterPlugin plugin = new GearInCounterPlugin();
        runFrame(plugin, 0.25f, false);
        runFrame(plugin, 5f, true);
        System.out.println(plugin.getElapsed());
    }

    private static void runFrame(GearInFramePlugin plugin, float amount, boolean paused) {
        plugin.advance(amount, paused);
    }
}
