package data.scripts.gearin.lib;

import org.boxutil.manager.ModelManager;
import org.boxutil.units.standard.attribute.ModelData;

public final class GearInModelCatalog {
    public ModelData require(String modelId) {
        if (!ModelManager.containsModelID(modelId)) {
            throw new IllegalArgumentException("Unknown BoxUtil model: " + modelId);
        }
        return ModelManager.getModelData(modelId);
    }
}
