package data.scripts.gearin.lib;

import org.apache.log4j.Logger;
import org.lazywizard.lazylib.JSONUtils;
import org.lazywizard.lazylib.JSONUtils.CommonDataJSONObject;

import com.fs.starfarer.api.Global;

/**
 * JSON 与文件工具族的用法片段：JSONUtils / CommonDataJSONObject 的最小消费面。
 *
 * 读写 <saves>/common 下的 JSON：只有调用 CommonDataJSONObject.save()
 * 才会写盘（不调用 save() 的改动不会落盘）；读取与写盘失败都要 catch，
 * 不让异常中断战斗循环。
 */
public final class GearInLazyJsonStore {

    private static final Logger LOG = Global.getLogger(GearInLazyJsonStore.class);

    /** 公共数据文件名：JSONUtils 在 <saves>/common 下读写该 JSON。 */
    private static final String COMMON_FILE = "GearIn_lazy_demo";

    private GearInLazyJsonStore() {
        // 工具类：不实例化
    }

    /** 读公共 JSON 中的 int 键；文件或键不存在时返回 0。 */
    public static int readInt(String key) {
        try (CommonDataJSONObject jsonObject = JSONUtils.loadCommonJSON(COMMON_FILE)) {
            if (jsonObject.has(key)) {
                return jsonObject.getInt(key);
            }
        } catch (Exception e) {
            LOG.warn("GearIn lazy json: failed to read key " + key, e);
        }
        return 0;
    }

    /** 写公共 JSON 的 int 键并立即 save()：不调用 save() 的改动不会落盘。 */
    public static void writeInt(String key, int value) {
        try (CommonDataJSONObject jsonObject = JSONUtils.loadCommonJSON(COMMON_FILE)) {
            jsonObject.put(key, value);
            jsonObject.save();
        } catch (Exception e) {
            LOG.warn("GearIn lazy json: failed to write key " + key, e);
        }
    }
}
