package data.scripts.gearin.lib;

import java.util.ArrayList;
import java.util.List;

import org.boxutil.base.api.InstanceDataAPI;
import org.boxutil.units.standard.attribute.Instance2Data;
import org.boxutil.units.standard.entity.SpriteEntity;

public final class GearInThousandInstanceBatch {
    public void submit(SpriteEntity entity, GearInBatchPool pool, int count) {
        List<InstanceDataAPI> instances = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Instance2Data data = pool.acquire();
            data.setLocation(i % 40 * 18f, i / 40 * 18f);
            data.setScale(0.5f, 0.5f);
            instances.add(data);
        }
        entity.setInstanceData(instances);
        entity.setInstanceDataRefreshAllFromCurrentIndex();
        entity.submitInstanceData();
        entity.setRenderingCount(instances.size());
    }

    public void updateRange(SpriteEntity entity, int start, int size) {
        entity.setInstanceDataRefreshIndex(start);
        entity.setInstanceDataRefreshSize(size);
        entity.submitInstanceData();
    }

    public void release(SpriteEntity entity, GearInBatchPool pool) {
        for (InstanceDataAPI item : entity.getInstanceData()) {
            pool.release((Instance2Data) item);
        }
        entity.resetInstanceData();
    }
}
