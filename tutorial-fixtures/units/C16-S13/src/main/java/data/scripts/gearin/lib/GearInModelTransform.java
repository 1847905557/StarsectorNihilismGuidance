package data.scripts.gearin.lib;

import org.boxutil.units.standard.attribute.ModelData;
import org.boxutil.units.standard.entity.CommonEntity;
import org.boxutil.util.TransformUtil;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector2f;

public final class GearInModelTransform {
    private final Matrix4f matrix = new Matrix4f();

    public CommonEntity create(ModelData model, Vector2f location,
            float facing, Vector2f scale) {
        if (model == null || !model.isValid()) {
            throw new IllegalArgumentException("BoxUtil model is not valid");
        }
        CommonEntity entity = new CommonEntity(model, true);
        entity.setModelMatrix(TransformUtil.createModelMatrixVanilla(
                location, facing, scale, matrix));
        entity.setDefaultLight();
        return entity;
    }
}
