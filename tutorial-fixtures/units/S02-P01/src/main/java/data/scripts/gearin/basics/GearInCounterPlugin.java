package data.scripts.gearin.basics;

/**
 * 带暂停守卫的累计计数器。
 *
 * <p>实现 {@link GearInFramePlugin}：非暂停帧把帧增量累入 {@code elapsed}，
 * 暂停帧直接返回，不推进状态——对应真实插件里 {@code if (engine.isPaused()) return;} 的守卫。</p>
 */
public final class GearInCounterPlugin implements GearInFramePlugin {

    private float elapsed;

    @Override
    public void advance(float amount, boolean paused) {
        if (paused) {
            return;
        }
        elapsed += amount;
    }

    public float getElapsed() {
        return elapsed;
    }
}
