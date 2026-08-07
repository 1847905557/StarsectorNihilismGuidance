package data.scripts.gearin.lib;

import org.boxutil.manager.EntityShadingDataManager;

public final class GearInShadingProbe {
    public boolean hasShipTextures(String hullId) {
        return EntityShadingDataManager.haveEntityTextureSet(hullId);
    }

    public boolean hasMissileTextures(String missileId) {
        return EntityShadingDataManager.haveMissileTextureSet(missileId);
    }

    public boolean hasProjectileLight(String projectileId) {
        return EntityShadingDataManager.haveProjectileIlluminantData(projectileId);
    }

    public boolean hasBeamLight(String weaponId) {
        return EntityShadingDataManager.haveBeamIlluminantData(weaponId);
    }
}
