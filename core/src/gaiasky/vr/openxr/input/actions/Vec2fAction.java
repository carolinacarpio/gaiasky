package gaiasky.vr.openxr.input.actions;

import com.badlogic.gdx.math.Vector2;
import gaiasky.vr.openxr.XrDriver;
import gaiasky.vr.openxr.input.XrControllerDevice;
import org.lwjgl.openxr.XrActionStateVector2f;

import static org.lwjgl.openxr.XR10.*;

public class Vec2fAction extends SingleInputAction<Vector2> {

    private static final XrActionStateVector2f state = XrActionStateVector2f.calloc().type(XR_TYPE_ACTION_STATE_VECTOR2F);

    public Vec2fAction(String name, String localizedName, XrControllerDevice device) {
        super(name, localizedName, XR_ACTION_TYPE_VECTOR2F_INPUT, device);
        currentState = new Vector2();
    }

    @Override
    public void sync(XrDriver driver) {
        getInfo.action(handle);
        driver.check(xrGetActionStateVector2f(driver.xrSession, getInfo, state), "xrGetActionStateBoolean");
        this.currentState.x = state.currentState().x();
        this.currentState.y = state.currentState().y();
        this.changedSinceLastSync = state.changedSinceLastSync();
        this.lastChangeTime = state.lastChangeTime();
        this.isActive = state.isActive();
    }
}
