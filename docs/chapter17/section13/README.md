# 用 BoxUtil 排版文本、数值与界面边框

调试文字若只用日志，玩家看不到即时状态；若每帧重建字形，又会浪费渲染预算。本节加载一次字体，用 `TextFieldEntity` 保存三行标签，再用 `NumberObject` 和 `UIBorderObject` 直接绘制右上角状态面板。

*本节预期阅读时间：~18 分钟。*

*本节预期操作时间：~30 分钟。*

## 学习目标

完成后，您将能够：

- 用 `FontDataManager.tryFont()` 加载并验证 `.fnt` 字体数据；
- 创建 `TextFieldEntity`，配置窗口正交矩阵并提交文本变化；
- 用 `NumberObject` 绘制不需要重建字形的动态数值；
- 用 `UIBorderObject` 建立固定尺寸面板边界；
- 只在 `renderInUICoords()` 调用直接绘制入口。

## 前置知识

- [C7-S1] 理解 Java 类、方法、字段与入口插件；
- [C8-S1] 理解每帧回调、暂停判断与运行时对象；
- 能够用 Gradle 构建示例 MOD，并从 `starsector.log` 定位类名。

## 明确当前需求

右上角显示粒子批次、活动实体数和最近一次提交结果；数值变化时文本更新，窗口缩放后仍保持边距。

## 文件与 API 链路

### 界面绘制对象速查表

| 对象 | 职责 |
|---|---|
| `FontDataManager` | 解析 `.fnt` 并返回 `FontMapData` |
| `TextFieldEntity` | 把文本转换成可提交的字符数据 |
| `NumberObject` | 直接用数字 shader 绘制数值 |
| `UIBorderObject` | 使用九宫格资源绘制固定边框 |

后三者的绘制命令都必须出现在 UI 渲染回调。

### 加载并验证字体数据

**插入位置：`GearInTextField.initFont()`。** 用 MOD 资源路径解析 `.fnt`，并保留失败日志。

```java
font = FontDataManager.tryFont("graphics/fonts/insignia15LTaa.fnt");
if (font == null || !font.isValid()) {
    LOG.warn("GearIn box: font load failed");
}
```

完成信号：`getFontInfo()` 返回字体路径与原始字号；失败时文本角色保持禁用。

### 建立窗口空间文本实体

**插入位置：`GearInTextField.initTextEntity()`。** 确认文本 shader 可用，再创建字段、设置画布尺寸与窗口正交矩阵。

```java
textEntity = new TextFieldEntity(font, true);
textEntity.setFieldSize(1024f, 128f);
textEntity.addText("", 12f, Color.WHITE, false, false, false, false, 0);
textEntity.setCustomPrimeMatrix();
textEntity.setPrimeMatrix(TransformUtil.createWindowOrthoMatrix(null));
```

完成信号：文本实体有效，屏幕坐标不会被战斗相机缩放。

### 只在字符串变化时刷新

**插入位置：`GearInTextField.setText()`。** 比较旧值，修改 `TextData.text` 后标记刷新范围并提交。

```java
TextFieldEntity.TextData message = textEntity.getTextDataList().get(0);
if (text.equals(message.text)) return;
message.text = text;
textEntity.setTextDataRefreshAllFromCurrentIndex();
textEntity.submitText();
```

完成信号：静止数值期间没有重复提交。

### 配置数字与固定边框

**插入位置：`GearInStatusPanel` 构造方法。** 边框固定为 240 × 104 像素，数字保留 4 位整数和 0 位小数。

```java
border.setSize(240f, 104f);
border.setColor(0.25f, 0.85f, 1f, 0.9f);
number.setIntegerLength(4);
number.setDecimalLength(0);
```

完成信号：边框 `isSizeValid()` 为真，三项整数使用相同的宽高和颜色参数。

### 在 UI 回调发出直接绘制

**插入位置：战斗插件 `renderInUICoords()`。** 调用状态面板的 `render()`，由它按当前分辨率计算右上角锚点并依次绘制边框、标签和三项数字。

```java
float x = screenWidth - PANEL_WIDTH - SCREEN_MARGIN;
float y = screenHeight - PANEL_HEIGHT - SCREEN_MARGIN;
border.render(x, y);
labels.drawAt(x + 16f, y + PANEL_HEIGHT - 24f);
number.render(batches, new Vector2f(x + 160f, y + 68f),
        0f, 12f, 18f, false);
```

完成信号：`advance()` 只调用 `update()` 保存三个整数，UI 回调负责全部 GL 绘制。

## 排查关键故障

下面是**局部故障片段**：

```java
public void advance(float amount) {
    textField.drawAt(20f, 20f);
}
```

实际症状：部分机器文字不显示，或后续 UI 的 GL 状态异常。

原因：directDraw 在逻辑推进回调发出，没有处于正确渲染上下文。

下面是**局部修正片段**：

```java
public void renderInUICoords(ViewportAPI viewport) {
    textField.drawAt(20f, 20f);
}
```

复验：把绘制移入 UI 回调，连续切换暂停、战斗界面和两种分辨率，确认文本、数字与边框保持同一锚点。

## 完整最小例

`GearInTextField` 管理字体、字符数据与文本直接绘制；`GearInStatusPanel` 管理固定布局、数字和边框。战斗插件在逻辑回调更新数值，在 UI 回调调用一次 `render()`。

::: details 完整成品：GearInStatusPanel

```java
package data.scripts.gearin.lib;

import java.awt.Color;

import org.boxutil.units.standard.misc.NumberObject;
import org.boxutil.units.standard.misc.UIBorderObject;
import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;

public final class GearInStatusPanel {
    private static final float PANEL_WIDTH = 240f;
    private static final float PANEL_HEIGHT = 104f;
    private static final float SCREEN_MARGIN = 20f;

    private final GearInTextField labels = new GearInTextField();
    private final NumberObject number = new NumberObject();
    private final UIBorderObject border = new UIBorderObject(false, true);

    private int batches;
    private int activeEntities;
    private int lastSubmitState;

    public GearInStatusPanel() {
        border.setSize(PANEL_WIDTH, PANEL_HEIGHT);
        border.setColor(0.25f, 0.85f, 1f, 0.9f);
        number.setColor(new Color(110, 225, 255));
        number.setIntegerLength(4);
        number.setDecimalLength(0);
        labels.setText("BATCHES\nACTIVE\nSUBMIT");
    }

    public void update(int batches, int activeEntities, int lastSubmitState) {
        this.batches = batches;
        this.activeEntities = activeEntities;
        this.lastSubmitState = lastSubmitState;
    }

    public void render() {
        float x = Global.getSettings().getScreenWidthPixels()
                - PANEL_WIDTH - SCREEN_MARGIN;
        float y = Global.getSettings().getScreenHeightPixels()
                - PANEL_HEIGHT - SCREEN_MARGIN;
        border.render(x, y);
        labels.drawAt(x + 16f, y + PANEL_HEIGHT - 24f);
        number.render(batches, new Vector2f(x + 160f, y + 68f),
                0f, 12f, 18f, false);
        number.render(activeEntities, new Vector2f(x + 160f, y + 42f),
                0f, 12f, 18f, false);
        number.render(lastSubmitState, new Vector2f(x + 160f, y + 16f),
                0f, 12f, 18f, false);
    }
}
```

:::

::: details 完整成品：GearInTextField

```java
package data.scripts.gearin.lib;

import java.awt.Color;

import org.apache.log4j.Logger;
import org.boxutil.base.BaseShaderData;
import org.boxutil.define.BoxEnum;
import org.boxutil.manager.FontDataManager;
import org.boxutil.manager.ShaderCore;
import org.boxutil.units.standard.attribute.FontMapData;
import org.boxutil.units.standard.entity.TextFieldEntity;
import org.boxutil.util.TransformUtil;

import com.fs.starfarer.api.Global;

/**
 * 字体与屏幕文本 (BoxUtil 字体/绘制工具族)：FontDataManager.tryFont 解析 .fnt
 * 得到 FontMapData，TextFieldEntity 以窗口正交矩阵 + directDraw() 在渲染回调里
 * 绘制屏幕文本；文本修改后必须 setTextDataRefresh* + submitText() 才会上传。
 */
public class GearInTextField {

    private static final Logger LOG = Global.getLogger(GearInTextField.class);
    private static final String FONT_PATH = "graphics/fonts/insignia15LTaa.fnt";
    private static final float TEXT_SIZE = 12f;

    private FontMapData font;
    private TextFieldEntity textEntity;

    /** 创建时加载字体并创建文本实体；任一环节失败则文本功能禁用 (内部判空)。 */
    public GearInTextField() {
        initFont();
        initTextEntity();
    }

    /** 字体信息 (供 init 日志)：未加载返回 "null"，否则 "路径/字号"。 */
    public String getFontInfo() {
        return font == null ? "null" : FONT_PATH + "/" + font.getFontSize();
    }

    /** 更新屏幕文本：改 TextData.text 后必须 refresh + submitText() 才会上传。 */
    public void setText(String text) {
        if (textEntity == null) {
            return;
        }
        TextFieldEntity.TextData message = textEntity.getTextDataList().get(0);
        if (text.equals(message.text)) {
            return;
        }
        message.text = text;
        textEntity.setTextDataRefreshIndex(0);
        textEntity.setTextDataRefreshAllFromCurrentIndex();
        byte result = textEntity.submitText();
        if (result != BoxEnum.STATE_SUCCESS) {
            textEntity.setShouldRenderingCharCount(0);
        }
    }

    /** 屏幕空间绘制：只能在渲染回调 (renderInUICoords) 里调用 (GL 命令时机)。 */
    public void drawAt(float x, float y) {
        if (textEntity == null || textEntity.hasDelete()) {
            return;
        }
        textEntity.setLocation(x, y);
        textEntity.setFacingScale(0f, getFontScale(), getFontScale());
        textEntity.directDraw();
    }

    /** 字体数据加载：FontDataManager.tryFont 解析 .fnt 并登记到字体表。 */
    private void initFont() {
        font = FontDataManager.tryFont(FONT_PATH);
        if (font == null || !font.isValid()) {
            LOG.warn("GearIn box: font load failed: " + FONT_PATH);
        }
    }

    /** 文本实体创建：字体或文本程序不可用时禁用文本并写 warn。 */
    private void initTextEntity() {
        if (font == null) {
            return;
        }
        if (!ShaderCore.isInitialized()) {
            LOG.warn("GearIn box: shader core not initialized, text disabled");
            return;
        }
        BaseShaderData program = ShaderCore.getTextProgram();
        if (program == null || !program.isValid()) {
            LOG.warn("GearIn box: text program invalid, text disabled");
            return;
        }
        textEntity = new TextFieldEntity(font, true);
        if (!textEntity.isValid()) {
            textEntity.delete();
            textEntity = null;
            LOG.warn("GearIn box: text field entity invalid, text disabled");
            return;
        }
        textEntity.setFieldSize(1024f, 128f);
        textEntity.addText("", TEXT_SIZE, Color.WHITE, false, false, false, false, 0);
        textEntity.setMappingModeSubmitData(false);
        textEntity.setCustomPrimeMatrix();
        textEntity.setPrimeMatrix(TransformUtil.createWindowOrthoMatrix(null));
        textEntity.setGlobalColor(Color.WHITE);
    }

    private float getFontScale() {
        return Math.abs(font.getFontSize()) > 0f ? TEXT_SIZE / Math.abs(font.getFontSize()) : 1f;
    }
}
```

:::

## 验证方案

前置数据：BoxUtil 字体、文本和数字 shader 可用，战斗插件持有一个 `GearInStatusPanel`。

1. 进入战斗，核对字体路径、原始字号和文本 shader 状态。
2. 调用 `update(12, 34, 0)`，在 UI 回调中渲染面板，核对三行标签与三个整数。
3. 连续 60 帧传入相同值，确认 `TextFieldEntity` 不重复提交标签字符数据。
4. 把窗口切换到另一分辨率，确认面板右边和上边仍各保留 20 像素边距。
5. 把 `render()` 临时移到 `advance()` 复现 GL 时机故障，再恢复到 UI 回调。

预期结果：

- 字体和文本 shader 有效时，标签字符数据成功提交；
- `NumberObject` 直接绘制三项动态整数，无需为每次变化重建文本；
- `UIBorderObject` 使用稳定的 240 × 104 像素尺寸；
- 分辨率变化后面板仍以 20 像素边距锚定右上角；
- 逻辑更新与直接绘制分属各自回调，界面没有未处理异常。

## 故障分析

- **文字不显示**：直接绘制发生在逻辑推进回调，没有进入正确渲染上下文。

    - **解决方案**：把面板 `render()` 移入 `renderInUICoords()`。

- **更新后文字仍是旧值**：`setText` 后漏掉 refresh 或 `submitText`。

    - **解决方案**：补齐字形重建与提交。

- **中文显示为方框**：字体数据不包含对应字形。

    - **解决方案**：换用覆盖目标字符集的字体并重新提交文本。

- **边框随文字长度跳动**：面板宽高直接依赖当前文本尺寸。

    - **解决方案**：使用固定或受约束的面板尺寸。

## 总结

文本实体的昂贵步骤是字形刷新与提交，绘制入口则受渲染阶段约束。让数据更新、字形重建和 directDraw 分属正确时机，调试面板才能既即时又稳定。

## 建议参考项

- `FontDataManager`：加载与查询字体数据；
- `TextFieldEntity`：排版和提交文本实例；
- `renderInUICoords`：屏幕空间直接绘制入口。
