package data.scripts.gearin.campaign;

import com.fs.starfarer.api.Global;

/**
 * strings.json 的两级取值封装（示例 MOD gear_in 的完整最小例）。
 *
 * 事实依据：
 * - Global.getSettings().getString(category, id) 读取 data/strings/strings.json；
 * - 分类或键不存在时返回字面量 "Missing string: [<id>]" 并在 starsector.log 记
 *   ERROR，不抛异常、不崩溃；
 * - "$xxx" 令牌不会被引擎替换，由消费方自行处理（见 replaceToken）；
 * - "%s" 模板由取值方用 String.format 填充（见 format）。
 */
public final class GearInI18n {

	/** gear_in 的界面文本分类。 */
	public static final String CATEGORY_UI = "GearIn_ui";
	/** gear_in 的对话文本分类。 */
	public static final String CATEGORY_DIALOG = "GearIn_dialog";

	private GearInI18n() {
	}

	/** 两级取值：category 与 id 均区分大小写、逐字符匹配。 */
	public static String get(String category, String id) {
		return Global.getSettings().getString(category, id);
	}

	public static String ui(String id) {
		return get(CATEGORY_UI, id);
	}

	public static String dialog(String id) {
		return get(CATEGORY_DIALOG, id);
	}

	/** %s 模板代换：先取值，再 String.format。占位符与实参不匹配会抛 IllegalFormatException。 */
	public static String format(String category, String id, Object... args) {
		String template = get(category, id);
		if (args == null || args.length == 0) {
			return template;
		}
		return String.format(template, args);
	}

	/** $token 手动替换：strings.json 的 $xxx 不会自动替换（原版对话插件逐个 replaceAll 处理）。 */
	public static String replaceToken(String text, String token, String value) {
		return text.replace("$" + token, value);
	}
}
