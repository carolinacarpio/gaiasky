package gaiasky.vr.openxr;

import gaiasky.util.Logger;
import gaiasky.vr.openxr.input.ControllerPoses;
import gaiasky.vr.openxr.input.XrInput;
import gaiasky.vr.openxr.input.actionsets.ActionSet;
import gaiasky.vr.openxr.input.actionsets.GaiaSkyActionSet;
import gaiasky.vr.openxr.input.actionsets.HandsActionSet;
import org.lwjgl.PointerBuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.openxr.*;
import org.lwjgl.system.MemoryStack;

import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.NULL;

public class OpenXRSession implements AutoCloseable {
    private static final Logger.Log logger = Logger.getLogger(OpenXRInstance.class);

    public final XrSession handle;
    public final OpenXRInstance instance;
    public final OpenXRSystem system;

    public final int viewConfigurationType;

    public XrSpace xrAppSpace;
    public XrSpace xrViewSpace;

    public XrView.Buffer viewBuffer;
    public OpenXRSwapchain swapchain;

    public int state;
    public boolean running;

    public OpenXRSession(XrSession handle, OpenXRSystem system) {
        this.handle = handle;
        this.system = system;
        this.instance = system.instance;
        this.viewConfigurationType = XR10.XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO;
    }

    public void createXRReferenceSpaces() {
        try (MemoryStack stack = stackPush()) {
            XrPosef identityPose = XrPosef.calloc(stack);
            identityPose.set(
                    XrQuaternionf.calloc(stack).set(0, 0, 0, 1),
                    XrVector3f.calloc(stack)
            );

            XrReferenceSpaceCreateInfo referenceSpaceCreateInfo = XrReferenceSpaceCreateInfo.calloc(stack);
            referenceSpaceCreateInfo.set(
                    XR10.XR_TYPE_REFERENCE_SPACE_CREATE_INFO,
                    NULL,
                    XR10.XR_REFERENCE_SPACE_TYPE_STAGE,
                    identityPose
            );
            PointerBuffer pp = stack.callocPointer(1);
            instance.checkPanic(XR10.xrCreateReferenceSpace(handle, referenceSpaceCreateInfo, pp), "xrCreateReferenceSpace");
            xrAppSpace = new XrSpace(pp.get(0), handle);

            referenceSpaceCreateInfo.referenceSpaceType(XR10.XR_REFERENCE_SPACE_TYPE_VIEW);
            instance.checkPanic(XR10.xrCreateReferenceSpace(handle, referenceSpaceCreateInfo, pp), "xrCreateReferenceSpace");
            xrViewSpace = new XrSpace(pp.get(0), handle);
        }
    }

    public void createSwapchain() throws XrException {
        try (MemoryStack stack = stackPush()) {
            IntBuffer intBuf = stack.callocInt(1);
            instance.checkPanic(XR10.xrEnumerateViewConfigurationViews(instance.handle, system.handle, viewConfigurationType, intBuf, null), "xrEnumerateViewConfigurationViews");
            XrViewConfigurationView.Buffer viewConfigs = new XrViewConfigurationView.Buffer(
                    OpenXRState.bufferStack(intBuf.get(0), XrViewConfigurationView.SIZEOF, XR10.XR_TYPE_VIEW_CONFIGURATION_VIEW)
            );
            instance.checkPanic(XR10.xrEnumerateViewConfigurationViews(instance.handle, system.handle, viewConfigurationType, intBuf, viewConfigs), "xrEnumerateViewConfigurationViews");
            int viewCountNumber = intBuf.get(0);

            viewBuffer = new XrView.Buffer(
                    OpenXRState.bufferHeap(viewCountNumber, XrView.SIZEOF, XR10.XR_TYPE_VIEW)
            );

            if (viewCountNumber != 2) {
                throw new IllegalStateException("Tried to create swapchain from " + viewCountNumber + " views");
            }
            instance.checkPanic(XR10.xrEnumerateSwapchainFormats(handle, intBuf, null), "xrEnumerateSwapchainFormats");
            LongBuffer swapchainFormats = stack.callocLong(intBuf.get(0));
            instance.checkPanic(XR10.xrEnumerateSwapchainFormats(handle, intBuf, swapchainFormats), "xrEnumerateSwapchainFormats");

            long[] desiredSwapchainFormats = {
                    GL11.GL_RGB10_A2,
                    GL30.GL_RGBA16F,
                    GL30.GL_RGB16F,
                    //SRGB formats
                    GL21.GL_SRGB8_ALPHA8,
                    GL21.GL_SRGB8,
                    // The two below should only be used as a fallback, as they are linear color formats without enough bits for color
                    // depth, thus leading to banding.
                    GL11.GL_RGBA8,
                    GL31.GL_RGBA8_SNORM,
            };

            long chosenFormat = 0;

            for (long glFormatIter : desiredSwapchainFormats) {
                swapchainFormats.rewind();
                while (swapchainFormats.hasRemaining()) {
                    if (glFormatIter == swapchainFormats.get()) {
                        chosenFormat = glFormatIter;
                        break;
                    }
                }
                if (chosenFormat != 0) {
                    break;
                }
            }

            if (chosenFormat == 0) {
                var formats = new ArrayList<Long>();
                swapchainFormats.rewind();
                while (swapchainFormats.hasRemaining()) {
                    formats.add(swapchainFormats.get());
                }
                throw new XrException(XR10.XR_ERROR_SWAPCHAIN_FORMAT_UNSUPPORTED, "No compatible swapchain / framebuffer format available: " + formats);
            }

            XrViewConfigurationView viewConfig = viewConfigs.get(0);
            XrSwapchainCreateInfo swapchainCreateInfo = XrSwapchainCreateInfo.calloc(stack);

            swapchainCreateInfo.set(
                    XR10.XR_TYPE_SWAPCHAIN_CREATE_INFO,
                    NULL,
                    0,
                    XR10.XR_SWAPCHAIN_USAGE_COLOR_ATTACHMENT_BIT,
                    chosenFormat,
                    1,
                    viewConfig.recommendedImageRectWidth(),
                    viewConfig.recommendedImageRectHeight(),
                    1,
                    2,
                    1
            );

            PointerBuffer handlePointer = stack.callocPointer(1);
            instance.checkPanic(XR10.xrCreateSwapchain(handle, swapchainCreateInfo, handlePointer), "xrCreateSwapchain");
            swapchain = new OpenXRSwapchain(new XrSwapchain(handlePointer.get(0), handle), this, (int) chosenFormat, swapchainCreateInfo.width(), swapchainCreateInfo.height());
        }
    }

    public boolean stateChanged(XrEventDataSessionStateChanged stateChangedEvent) {
        int oldState = state;
        state = stateChangedEvent.state();

        logger.debug(String.format("XrEventDataSessionStateChanged: state %s->%s session=%d time=%d", oldState, state, stateChangedEvent.session(), stateChangedEvent.time()));

        if ((stateChangedEvent.session() != NULL) && (stateChangedEvent.session() != handle.address())) {
            logger.warn("XrEventDataSessionStateChanged for unknown session");
            return false;
        }

        switch (state) {
            case XR10.XR_SESSION_STATE_READY: {
                try (MemoryStack stack = stackPush()) {
                    XrSessionBeginInfo sessionBeginInfo = XrSessionBeginInfo.calloc(stack);
                    sessionBeginInfo.set(XR10.XR_TYPE_SESSION_BEGIN_INFO, 0, viewConfigurationType);
                    instance.checkPanic(XR10.xrBeginSession(handle, sessionBeginInfo), "xrBeginSession");
                }
                running = true;
                return false;
            }
            case XR10.XR_SESSION_STATE_STOPPING: {
                running = false;
                instance.checkPanic(XR10.xrEndSession(handle), "xrEndSession");
                return false;
            }
            case XR10.XR_SESSION_STATE_EXITING: {
                // Do not attempt to restart because user closed this session.
                return true;
            }
            case XR10.XR_SESSION_STATE_LOSS_PENDING: {
                // Poll for a new instance.
//        *requestRestart = true;
                return true;
            }
            default:
                return false;
        }
    }

    public void pollActions(boolean xrDisabled) {
        if (state != XR10.XR_SESSION_STATE_FOCUSED) {
            return;
        }

        try (var stack = stackPush()) {
            GaiaSkyActionSet vcActionSet = XrInput.gaiaSkyActionSet;
            HandsActionSet handsActionSet = XrInput.handsActionSet;
            List<ActionSet> toSync = new ArrayList<>();

            toSync.add(handsActionSet);

            XrActiveActionSet.Buffer sets = XrActiveActionSet.calloc(toSync.size(), stack);
            for (int i = 0; i < toSync.size(); i++) {
                sets.get(i).set(toSync.get(i).getHandle(), NULL);
            }

            XrActionsSyncInfo syncInfo = XrActionsSyncInfo.calloc(stack)
                    .type(XR10.XR_TYPE_ACTIONS_SYNC_INFO).activeActionSets(sets);

            instance.checkPanic(XR10.xrSyncActions(handle, syncInfo), "xrSyncActions");

           // handsActionSet.sync(this);
           // vcActionSet.sync(this);
           // guiActionSet.sync(this);
        }

        XrInput.pollActions();
    }

    public void setPosesFromSpace(XrSpace handSpace, long time, ControllerPoses result, float scale) {
        try (var stack = stackPush()) {
            XrSpaceLocation space_location = XrSpaceLocation.calloc(stack).type(XR10.XR_TYPE_SPACE_LOCATION);
            var res = XR10.xrLocateSpace(handSpace, xrAppSpace, time, space_location);
            if (res != XR10.XR_ERROR_TIME_INVALID) {
                instance.checkPanic(res, "xrLocateSpace");
                if ((space_location.locationFlags() & XR10.XR_SPACE_LOCATION_POSITION_VALID_BIT) != 0 &&
                        (space_location.locationFlags() & XR10.XR_SPACE_LOCATION_ORIENTATION_VALID_BIT) != 0) {

                    result.updatePhysicalPose(space_location.pose(), 0, scale);
                }
            }
        }
    }

    @Override
    public void close() {
        XrInput.gaiaSkyActionSet.close();
        XrInput.handsActionSet.close();

        if (swapchain != null) {
            swapchain.close();
        }
        if (viewBuffer != null) {
            viewBuffer.close();
        }
        if (xrAppSpace != null) {
            XR10.xrDestroySpace(xrAppSpace);
        }
        if (xrViewSpace != null) {
            XR10.xrDestroySpace(xrViewSpace);
        }
        XR10.xrDestroySession(handle);
    }
}
