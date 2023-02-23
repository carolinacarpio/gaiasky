package gaiasky.scene.system.update;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import gaiasky.scene.Mapper;
import gaiasky.util.Constants;
import gaiasky.util.math.Matrix4d;
import gaiasky.util.math.Vector3d;

public class VRDeviceUpdater extends AbstractUpdateSystem {

    public VRDeviceUpdater(Family family, int priority) {
        super(family, priority);
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        updateEntity(entity, deltaTime);
    }

    private Vector3d aux = new Vector3d();
    private Matrix4d deviceTransform = new Matrix4d();

    @Override
    public void updateEntity(Entity entity, float deltaTime) {
        var model = Mapper.model.get(entity);
        var vr = Mapper.vr.get(entity);
        vr.beamP0.set(0, -0.01f, 0);
        vr.beamP1.set(0, (float) (-70 * Constants.KM_TO_U), (float) (-100 * Constants.KM_TO_U));
        vr.beamP2.set(0, (float) (-7000 * Constants.KM_TO_U), (float) (-10000 * Constants.KM_TO_U));
        if (vr.hitUI) {
            // Shorten beam.
            aux.set(vr.beamP1).sub(vr.beamP0).nor().scl(20 * Constants.KM_TO_U);
            vr.beamP1.set(vr.beamP0).add(aux);

            aux.set(vr.beamP2).sub(vr.beamP0).nor().scl(40 * Constants.KM_TO_U);
            vr.beamP2.set(vr.beamP0).add(aux);
        }

        deviceTransform.set(model.model.instance.transform);
        vr.beamP0.mul(deviceTransform);
        vr.beamP1.mul(deviceTransform);
        vr.beamP2.mul(deviceTransform);

    }
}
