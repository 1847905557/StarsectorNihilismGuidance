package data.scripts.gearin.rules;

/**
 * 本节记忆键常量(GearIn_memory_* 数据所有权)：写入、验证、解析、对话各脚本
 * 共用，键值与 rules.csv 条件/文本逐字一致(含 $ 前缀与大小写)。记忆键必须
 * 以 $ 开头(Memory.set 硬校验)；PersistentData 键不在此列，由各自的脚本持有。
 */
public final class GearInMemoryKeys {

    private GearInMemoryKeys() {
    }

    /** sector 内存：无过期键(两参 set)。 */
    public static final String KEY_TAG = "$GearIn_memory_tag";
    /** sector 内存：1000 游戏天过期键(三参 set)。 */
    public static final String KEY_COUNTER = "$GearIn_memory_counter";
    /** sector 内存：expire 0 键 —— 下一个未暂停推进帧被移除(rules.csv 用它演示判真)。 */
    public static final String KEY_PROBE = "$GearIn_memory_probe";
    /** sector 内存：expire() 重设与两参 set 清除过期演示键。 */
    public static final String KEY_REARM = "$GearIn_memory_rearm";
    /** sector 内存：required 依赖演示(子键消失 → 父键递归移除)。 */
    public static final String KEY_REQUIRED_CHILD = "$GearIn_memory_required_child";
    public static final String KEY_REQUIRED_PARENT = "$GearIn_memory_required_parent";
    /** 玩家内存：MemKeys.PLAYER 槽位读法演示。 */
    public static final String KEY_PLAYER = "$GearIn_memory_player";
    /** 舰队内存：无点号变量回落 local 槽位演示(rules.csv 的 $GearIn_memory_fleetKey)。 */
    public static final String KEY_FLEET = "$GearIn_memory_fleetKey";
}
