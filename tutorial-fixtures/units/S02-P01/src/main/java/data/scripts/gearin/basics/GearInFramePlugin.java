package data.scripts.gearin.basics;

/**
 * 每帧回调的调用约定。
 *
 * <p>对应真实 API 的 {@code EveryFrameCombatPlugin}：游戏每帧调用 {@code advance}，
 * 由参数携带本帧上下文。{@code paused} 是教学简化，表示当前帧是否暂停。</p>
 */
public interface GearInFramePlugin {

    /**
     * 本帧回调。
     *
     * @param amount 本帧经过的秒数
     * @param paused 当前帧是否处于暂停状态
     */
    void advance(float amount, boolean paused);
}
