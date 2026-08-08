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
