package gaiasky.vr.openxr;

import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.GLFrameBuffer;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import gaiasky.util.Logger;
import gaiasky.util.Logger.Log;
import gaiasky.util.Settings;
import gaiasky.vr.openvr.VRContext.VRDevice;
import gaiasky.vr.openvr.VRContext.VRDeviceType;
import gaiasky.vr.openvr.VRDeviceListener;
import gaiasky.vr.openxr.input.OpenXRInputListener;
import gaiasky.vr.openxr.input.actions.Action;
import gaiasky.vr.openxr.input.actions.HapticsAction;
import gaiasky.vr.openxr.input.actions.VRControllerDevice;
import gaiasky.vr.openxr.input.actionsets.ActionSet;
import gaiasky.vr.openxr.input.actionsets.GaiaSkyActionSet;
import gaiasky.vr.openxr.input.actionsets.HandsActionSet;
import org.lwjgl.PointerBuffer;
import org.lwjgl.opengl.GL31;
import org.lwjgl.openxr.*;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.Struct;
import oshi.util.tuples.Pair;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.lwjgl.glfw.GLFW.glfwInit;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL21.GL_SRGB8_ALPHA8;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.openxr.EXTDebugUtils.*;
import static org.lwjgl.openxr.KHROpenGLEnable.*;
import static org.lwjgl.openxr.XR10.*;
import static org.lwjgl.system.MemoryStack.*;
import static org.lwjgl.system.MemoryUtil.*;

public class OpenXRDriver implements Disposable {
    private static final Log logger = Logger.getLogger(OpenXRDriver.class);

    public static final int VR_Eye_Left = 0, VR_Eye_Right = 1;
    public long systemId;
    public boolean missingXrDebug;
    public String runtimeName, runtimeVersionString;
    public long runtimeVersion;
    public XrInstance xrInstance;
    public XrSession xrSession;
    public XrDebugUtilsMessengerEXT xrDebugMessenger;
    // The real world space in which the program runs.
    public XrSpace xrAppSpace;
    private long glColorFormat;
    // Each view represents an eye in the headset with views[0] being left and views[1] being right.
    public XrView.Buffer views;
    private ActionSet actions, poses;

    private final Array<OpenXRInputListener> listeners;
    // One swapchain per view
    public Swapchain[] swapchains;
    public XrViewConfigurationView.Buffer viewConfigs;
    public final int viewConfigType = XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO;
    private FrameBuffer[] viewFrameBuffers;

    // The guy that renders to OpenXR currently.
    private final AtomicReference<OpenXRRenderer> currentRenderer;

    //Runtime
    XrEventDataBuffer eventDataBuffer;
    int sessionState;
    boolean sessionRunning, disposing = false;
    public long currentFrameTime = 1L;

    public static class Swapchain {
        public XrSwapchain handle;
        public int width;
        public int height;
        public XrSwapchainImageOpenGLKHR.Buffer images;
    }

    public OpenXRDriver() {
        listeners = new Array<>();
        currentRenderer = new AtomicReference<>();
    }

    /**
     * Creates the OpenXR instance object.
     * First method to call in the OpenXR initialization sequence.
     */
    public void createOpenXRInstance() {
        try (MemoryStack stack = stackPush()) {
            IntBuffer pi = stack.mallocInt(1);

            boolean hasCoreValidationLayer = false;
            check(xrEnumerateApiLayerProperties(pi, null));
            int numLayers = pi.get(0);

            XrApiLayerProperties.Buffer pLayers = XrHelper.prepareApiLayerProperties(stack, numLayers);
            check(xrEnumerateApiLayerProperties(pi, pLayers));
            for (int index = 0; index < numLayers; index++) {
                XrApiLayerProperties layer = pLayers.get(index);

                String layerName = layer.layerNameString();
                logger.info("OpenXR layer available: " + layerName);
                if (layerName.equals("XR_APILAYER_LUNARG_core_validation")) {
                    hasCoreValidationLayer = true;
                }
            }
            logger.info(numLayers + " XR layers are available:");

            check(xrEnumerateInstanceExtensionProperties((ByteBuffer) null, pi, null));
            int numExtensions = pi.get(0);

            XrExtensionProperties.Buffer properties = XrHelper.prepareExtensionProperties(stack, numExtensions);

            check(xrEnumerateInstanceExtensionProperties((ByteBuffer) null, pi, properties));

            PointerBuffer extensions = stack.mallocPointer(2);

            boolean missingOpenGL = true;
            missingXrDebug = true;
            for (int i = 0; i < numExtensions; i++) {
                XrExtensionProperties prop = properties.get(i);

                String extensionName = prop.extensionNameString();
                logger.info("OpenXR extension loaded: " + extensionName);
                if (extensionName.equals(XR_KHR_OPENGL_ENABLE_EXTENSION_NAME)) {
                    missingOpenGL = false;
                    extensions.put(prop.extensionName());
                }
                if (extensionName.equals(XR_EXT_DEBUG_UTILS_EXTENSION_NAME)) {
                    missingXrDebug = false;
                    extensions.put(prop.extensionName());
                }
            }
            extensions.flip();
            logger.info("OpenXR loaded with " + numExtensions + " extensions");

            if (missingOpenGL) {
                throw new IllegalStateException("OpenXR library does not provide required extension: " + XR_KHR_OPENGL_ENABLE_EXTENSION_NAME);
            }

            PointerBuffer wantedLayers;
            if (hasCoreValidationLayer) {
                wantedLayers = stack.callocPointer(1);
                wantedLayers.put(0, stack.UTF8("XR_APILAYER_LUNARG_core_validation"));
                logger.info("Enabling XR core validation");
            } else {
                wantedLayers = null;
            }

            XrInstanceCreateInfo createInfo = XrInstanceCreateInfo.malloc(stack).type$Default().next(NULL).createFlags(0).applicationInfo(XrApplicationInfo.calloc(stack).applicationName(stack.UTF8(Settings.getApplicationName(true))).apiVersion(XR_CURRENT_API_VERSION)).enabledApiLayerNames(wantedLayers).enabledExtensionNames(extensions);

            PointerBuffer pp = stack.mallocPointer(1);
            check(xrCreateInstance(createInfo, pp));
            xrInstance = new XrInstance(pp.get(0), createInfo);
        }
    }

    /**
     * Creates the system with which to later create a session.
     * Second method to call in the OpenXR initialization sequence.
     */
    public void initializeXRSystem() {
        try (MemoryStack stack = stackPush()) {
            //Get headset
            LongBuffer pl = stack.longs(0);

            var properties = XrInstanceProperties.calloc(stack).type$Default();
            check(XR10.xrGetInstanceProperties(xrInstance, properties));
            runtimeName = properties.runtimeNameString();
            runtimeVersion = properties.runtimeVersion();
            runtimeVersionString = XR10.XR_VERSION_MAJOR(runtimeVersion) + "." + XR10.XR_VERSION_MINOR(runtimeVersion) + "." + XR10.XR_VERSION_PATCH(runtimeVersion);

            logger.info("Runtime name: " + runtimeName);
            logger.info("Runtime version: " + runtimeVersionString);

            check(xrGetSystem(xrInstance, XrSystemGetInfo.malloc(stack).type$Default().next(NULL).formFactor(XR_FORM_FACTOR_HEAD_MOUNTED_DISPLAY), pl));

            systemId = pl.get(0);
            if (systemId == 0) {
                throw new IllegalStateException("No compatible headset detected");
            }
            logger.info("Headset found with System ID: " + systemId);
        }
    }

    public XrGraphicsRequirementsOpenGLKHR getXrGraphicsRequirements(MemoryStack stack) {
        XrGraphicsRequirementsOpenGLKHR graphicsRequirements = XrGraphicsRequirementsOpenGLKHR.malloc(stack).type$Default().next(NULL).minApiVersionSupported(0).maxApiVersionSupported(0);

        xrGetOpenGLGraphicsRequirementsKHR(xrInstance, systemId, graphicsRequirements);
        return graphicsRequirements;
    }

    public void checkOpenGL() {
        try (MemoryStack stack = stackPush()) {
            var graphicsRequirements = getXrGraphicsRequirements(stack);
            // Make sure GLFW is initialized.
            if (!glfwInit()) {
                throw new IllegalStateException("GLFW is not initialized!");
            }

            // Check if OpenGL version is supported by OpenXR runtime
            int actualMajorVersion = glGetInteger(GL_MAJOR_VERSION);
            int actualMinorVersion = glGetInteger(GL_MINOR_VERSION);

            int minMajorVersion = XR_VERSION_MAJOR(graphicsRequirements.minApiVersionSupported());
            int minMinorVersion = XR_VERSION_MINOR(graphicsRequirements.minApiVersionSupported());

            int maxMajorVersion = XR_VERSION_MAJOR(graphicsRequirements.maxApiVersionSupported());
            int maxMinorVersion = XR_VERSION_MINOR(graphicsRequirements.maxApiVersionSupported());

            if (minMajorVersion > actualMajorVersion || (minMajorVersion == actualMajorVersion && minMinorVersion > actualMinorVersion)) {
                throw new IllegalStateException("The OpenXR runtime supports only OpenGL " + minMajorVersion + "." + minMinorVersion + " and later, but we got OpenGL " + actualMajorVersion + "." + actualMinorVersion);
            }

            if (actualMajorVersion > maxMajorVersion || (actualMajorVersion == maxMajorVersion && actualMinorVersion > maxMinorVersion)) {
                throw new IllegalStateException("The OpenXR runtime supports only OpenGL " + maxMajorVersion + "." + minMajorVersion + " and earlier, but we got OpenGL " + actualMajorVersion + "." + actualMinorVersion);
            }
        }
    }

    /**
     * Creates the XrSession object.
     * Third method to call in the OpenXR initialization sequence.
     */
    public void initializeOpenXRSession(long windowHandle) {
        try (MemoryStack stack = stackPush()) {
            //Bind the OpenGL context to the OpenXR instance and create the session
            Struct graphicsBinding = XrHelper.createOpenGLBinding(stack, windowHandle);

            XrSessionCreateInfo sessionCreateInfo = XrSessionCreateInfo.calloc(stack).set(XR10.XR_TYPE_SESSION_CREATE_INFO, graphicsBinding.address(), 0, systemId);

            PointerBuffer pp = stack.mallocPointer(1);
            check(xrCreateSession(xrInstance, sessionCreateInfo, pp));

            xrSession = new XrSession(pp.get(0), xrInstance);

            if (!missingXrDebug) {
                XrDebugUtilsMessengerCreateInfoEXT ciDebugUtils = XrDebugUtilsMessengerCreateInfoEXT.calloc(stack).type$Default().messageSeverities(XR_DEBUG_UTILS_MESSAGE_SEVERITY_INFO_BIT_EXT | XR_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT | XR_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT).messageTypes(
                        XR_DEBUG_UTILS_MESSAGE_TYPE_GENERAL_BIT_EXT | XR_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT | XR_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT | XR_DEBUG_UTILS_MESSAGE_TYPE_CONFORMANCE_BIT_EXT).userCallback((messageSeverity, messageTypes, pCallbackData, userData) -> {
                    XrDebugUtilsMessengerCallbackDataEXT callbackData = XrDebugUtilsMessengerCallbackDataEXT.create(pCallbackData);
                    logger.info("XR Debug Utils: " + callbackData.messageString());
                    return 0;
                });

                logger.info("Enabling OpenXR debug utils");
                check(xrCreateDebugUtilsMessengerEXT(xrInstance, ciDebugUtils, pp));
                xrDebugMessenger = new XrDebugUtilsMessengerEXT(pp.get(0), xrInstance);
            }
        }
    }

    /**
     * Creates an XrSpace from the previously created session.
     * Fourth method to call in the OpenXR initialization sequence.
     */
    public void createOpenXRReferenceSpace() {
        try (MemoryStack stack = stackPush()) {
            PointerBuffer pp = stack.mallocPointer(1);

            check(xrCreateReferenceSpace(xrSession, XrReferenceSpaceCreateInfo.malloc(stack).type$Default().next(NULL).referenceSpaceType(XR_REFERENCE_SPACE_TYPE_LOCAL).poseInReferenceSpace(XrPosef.malloc(stack).orientation(XrQuaternionf.malloc(stack).x(0).y(0).z(0).w(1)).position$(XrVector3f.calloc(stack))), pp));

            xrAppSpace = new XrSpace(pp.get(0), xrSession);
        }
    }

    /**
     * Initializes the XR swapchains.
     * Fifth method to call in the OpenXR initialization sequence.
     */
    public void createOpenXRSwapchains() {
        try (MemoryStack stack = stackPush()) {
            XrSystemProperties systemProperties = XrSystemProperties.calloc(stack);
            memPutInt(systemProperties.address(), XR_TYPE_SYSTEM_PROPERTIES);
            check(xrGetSystemProperties(xrInstance, systemId, systemProperties));

            logger.info("Headset name: " + memUTF8(memAddress(systemProperties.systemName())) + ", vendor: " + systemProperties.vendorId());
            logger.info(systemProperties.systemNameString());

            XrSystemTrackingProperties trackingProperties = systemProperties.trackingProperties();
            logger.info("Headset orientationTracking: " + trackingProperties.orientationTracking() + ", positionTracking: " + trackingProperties.positionTracking());

            XrSystemGraphicsProperties graphicsProperties = systemProperties.graphicsProperties();
            logger.info("Headset MaxWidth: " + graphicsProperties.maxSwapchainImageWidth() + ", MaxHeight: " + graphicsProperties.maxSwapchainImageHeight() + ", MaxLayerCount: " + graphicsProperties.maxLayerCount());

            IntBuffer pi = stack.mallocInt(1);

            check(xrEnumerateViewConfigurationViews(xrInstance, systemId, viewConfigType, pi, null));
            viewConfigs = XrHelper.fill(XrViewConfigurationView.calloc(pi.get(0)), // Don't use malloc() because that would mess up the `next` field
                    XrViewConfigurationView.TYPE, XR_TYPE_VIEW_CONFIGURATION_VIEW);

            check(xrEnumerateViewConfigurationViews(xrInstance, systemId, viewConfigType, pi, viewConfigs));
            int viewCountNumber = pi.get(0);

            views = XrHelper.fill(XrView.calloc(viewCountNumber), XrView.TYPE, XR_TYPE_VIEW);

            if (viewCountNumber > 0) {
                check(xrEnumerateSwapchainFormats(xrSession, pi, null));
                LongBuffer swapchainFormats = stack.mallocLong(pi.get(0));
                check(xrEnumerateSwapchainFormats(xrSession, pi, swapchainFormats));

                long[] desiredSwapchainFormats = { GL_RGB10_A2, GL_RGBA16F, GL_SRGB8_ALPHA8,
                        // The two below should only be used as a fallback, as they are linear color formats without enough bits for color
                        // depth, thus leading to banding.
                        GL_RGBA8, GL31.GL_RGBA8_SNORM };

                out:
                for (long glFormatIter : desiredSwapchainFormats) {
                    for (int i = 0; i < swapchainFormats.limit(); i++) {
                        if (glFormatIter == swapchainFormats.get(i)) {
                            glColorFormat = glFormatIter;
                            break out;
                        }
                    }
                }

                if (glColorFormat == 0) {
                    throw new IllegalStateException("No compatable swapchain / framebuffer format availible");
                }

                swapchains = new Swapchain[viewCountNumber];
                for (int i = 0; i < viewCountNumber; i++) {
                    XrViewConfigurationView viewConfig = viewConfigs.get(i);

                    Swapchain swapchainWrapper = new Swapchain();

                    XrSwapchainCreateInfo swapchainCreateInfo = XrSwapchainCreateInfo.malloc(stack).type$Default().next(NULL).createFlags(0).usageFlags(XR_SWAPCHAIN_USAGE_SAMPLED_BIT | XR_SWAPCHAIN_USAGE_COLOR_ATTACHMENT_BIT).format(glColorFormat).sampleCount(viewConfig.recommendedSwapchainSampleCount()).width(viewConfig.recommendedImageRectWidth()).height(viewConfig.recommendedImageRectHeight()).faceCount(1).arraySize(1).mipCount(1);

                    PointerBuffer pp = stack.mallocPointer(1);
                    check(xrCreateSwapchain(xrSession, swapchainCreateInfo, pp));

                    swapchainWrapper.handle = new XrSwapchain(pp.get(0), xrSession);
                    swapchainWrapper.width = swapchainCreateInfo.width();
                    swapchainWrapper.height = swapchainCreateInfo.height();

                    check(xrEnumerateSwapchainImages(swapchainWrapper.handle, pi, null));
                    int imageCount = pi.get(0);

                    XrSwapchainImageOpenGLKHR.Buffer swapchainImageBuffer = XrHelper.fill(XrSwapchainImageOpenGLKHR.create(imageCount), XrSwapchainImageOpenGLKHR.TYPE, XR_TYPE_SWAPCHAIN_IMAGE_OPENGL_KHR);

                    check(xrEnumerateSwapchainImages(swapchainWrapper.handle, pi, XrSwapchainImageBaseHeader.create(swapchainImageBuffer.address(), swapchainImageBuffer.capacity())));
                    swapchainWrapper.images = swapchainImageBuffer;
                    swapchains[i] = swapchainWrapper;
                }
            }
        }
    }

    public void initializeOpenGLFrameBuffers() {
        GLFrameBuffer.FrameBufferBuilder frameBufferBuilder = new GLFrameBuffer.FrameBufferBuilder(getWidth(), getHeight());
        int internalFormat = org.lwjgl.opengl.GL30.GL_RGBA8;
        if (Settings.settings.graphics.useSRGB) {
            internalFormat = org.lwjgl.opengl.GL30.GL_SRGB8_ALPHA8;
        }
        frameBufferBuilder.addColorTextureAttachment(internalFormat, GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE);
        frameBufferBuilder.addBasicDepthRenderBuffer();

        // Frame buffers.
        viewFrameBuffers = new FrameBuffer[swapchains.length];
        for (int view = 0; view < swapchains.length; view++) {
            viewFrameBuffers[view] = frameBufferBuilder.build();
        }
    }

    public void initializeInput() {
        // Event buffer.
        eventDataBuffer = XrEventDataBuffer.calloc().type$Default();

        actions = new GaiaSkyActionSet();
        actions.createHandle(this);

        poses = new HandsActionSet();
        poses.createHandle(this);

        HashMap<String, List<Pair<Action, String>>> bindingsMap = new HashMap<>();
        poses.getDefaultBindings(bindingsMap);
        actions.getDefaultBindings(bindingsMap);

        try (MemoryStack stack = stackPush()) {
            var devices = bindingsMap.keySet();
            for (var device : devices) {
                var bindings = bindingsMap.get(device);
                XrActionSuggestedBinding.Buffer bindingsBuffer = XrActionSuggestedBinding.calloc(bindings.size(), stack);
                int l = 0;
                for (var binding : bindings) {
                    bindingsBuffer.get(l++).set(binding.getA().getHandle(), getPath(binding.getB()));
                }

                XrInteractionProfileSuggestedBinding suggestedBinding = XrInteractionProfileSuggestedBinding.malloc(stack).type$Default().next(NULL).interactionProfile(getPath(device)).suggestedBindings(bindingsBuffer);
                check(xrSuggestInteractionProfileBindings(xrInstance, suggestedBinding));
            }

            // Attach sets to session.
            XrSessionActionSetsAttachInfo attachInfo = XrSessionActionSetsAttachInfo.calloc(stack).set(
                    XR_TYPE_SESSION_ACTION_SETS_ATTACH_INFO,
                    NULL,
                    stackPointers(actions.getHandle(), poses.getHandle()));
            check(xrAttachSessionActionSets(xrSession, attachInfo));
        }
    }

    private XrFrameState getFrameState(MemoryStack stack) {
        XrFrameState frameState = XrFrameState.calloc(stack).type$Default();

        check(xrWaitFrame(xrSession, XrFrameWaitInfo.calloc(stack).type$Default(), frameState));
        return frameState;
    }

    /**
     * Renders the next frame with the current renderer.
     */
    public void renderFrameOpenXR() {
        if (currentRenderer != null) {
            try (MemoryStack stack = stackPush()) {
                XrFrameState frameState = getFrameState(stack);

                check(xrBeginFrame(xrSession, XrFrameBeginInfo.calloc(stack).type$Default()));

                XrCompositionLayerProjection layerProjection = XrCompositionLayerProjection.calloc(stack).type$Default();

                PointerBuffer layers = stack.callocPointer(1);
                boolean didRender = false;

                // Fetch time.
                currentFrameTime = frameState.predictedDisplayTime();

                if (frameState.shouldRender()) {
                    if (renderLayerOpenXR(stack, currentFrameTime, layerProjection)) {
                        layers.put(0, layerProjection.address());
                        didRender = true;
                    } else {
                        System.out.println("Didn't render");
                    }
                } else {
                    System.out.println("Shouldn't render");
                }

                check(xrEndFrame(xrSession, XrFrameEndInfo.malloc(stack).type$Default().next(NULL).displayTime(frameState.predictedDisplayTime()).environmentBlendMode(XR_ENVIRONMENT_BLEND_MODE_OPAQUE).layers(didRender ? layers : null).layerCount(didRender ? layers.remaining() : 0)));
            }
        }
    }

    private boolean renderLayerOpenXR(MemoryStack stack, long predictedDisplayTime, XrCompositionLayerProjection layer) {
        XrViewState viewState = XrViewState.calloc(stack).type$Default();

        IntBuffer pi = stack.mallocInt(1);
        check(xrLocateViews(xrSession, XrViewLocateInfo.malloc(stack).type$Default().next(NULL).viewConfigurationType(viewConfigType).displayTime(predictedDisplayTime).space(xrAppSpace), viewState, pi, views));

        if ((viewState.viewStateFlags() & XR_VIEW_STATE_POSITION_VALID_BIT) == 0 || (viewState.viewStateFlags() & XR_VIEW_STATE_ORIENTATION_VALID_BIT) == 0) {
            return false;  // There is no valid tracking poses for the views.
        }

        int viewCountOutput = pi.get(0);
        assert (viewCountOutput == views.capacity());
        assert (viewCountOutput == viewConfigs.capacity());
        assert (viewCountOutput == swapchains.length);

        XrCompositionLayerProjectionView.Buffer projectionLayerViews = XrHelper.fill(XrCompositionLayerProjectionView.calloc(viewCountOutput, stack), // Use calloc() since malloc() messes up the `next` field
                XrCompositionLayerProjectionView.TYPE, XR_TYPE_COMPOSITION_LAYER_PROJECTION_VIEW);

        // Render view to the appropriate part of the swapchain image.
        for (int viewIndex = 0; viewIndex < viewCountOutput; viewIndex++) {
            // Each view has a separate swapchain which is acquired, rendered to, and released.
            Swapchain viewSwapchain = swapchains[viewIndex];

            check(xrAcquireSwapchainImage(viewSwapchain.handle, XrSwapchainImageAcquireInfo.calloc(stack).type$Default(), pi));
            int swapchainImageIndex = pi.get(0);

            check(xrWaitSwapchainImage(viewSwapchain.handle, XrSwapchainImageWaitInfo.malloc(stack).type$Default().next(NULL).timeout(XR_INFINITE_DURATION)));

            XrCompositionLayerProjectionView projectionLayerView = projectionLayerViews.get(viewIndex).pose(views.get(viewIndex).pose()).fov(views.get(viewIndex).fov()).subImage(si -> si.swapchain(viewSwapchain.handle).imageRect(rect -> rect.offset(offset -> offset.x(0).y(0)).extent(extent -> extent.width(viewSwapchain.width).height(viewSwapchain.height))));

            if (currentRenderer.get() != null) {
                currentRenderer.get().renderOpenXRView(projectionLayerView, viewSwapchain.images.get(swapchainImageIndex), viewFrameBuffers == null ? null : viewFrameBuffers[viewIndex], viewIndex);
            }

            check(xrReleaseSwapchainImage(viewSwapchain.handle, XrSwapchainImageReleaseInfo.calloc(stack).type$Default()));
        }

        layer.space(xrAppSpace);
        layer.views(projectionLayerViews);
        return true;
    }

    public long getPath(String name) {
        try (MemoryStack stack = stackPush()) {
            LongBuffer path = stack.longs(0);
            check(xrStringToPath(xrInstance, stack.UTF8(name), path));
            return path.get();
        }
    }

    /**
     * Polls pending events in the OpenXR system.
     *
     * @return True if we must stop, false otherwise.
     */
    public boolean pollEvents() {
        if (!disposing) {
            XrEventDataBaseHeader event = readNextOpenXREvent();
            while (event != null) {
                switch (event.type()) {
                case XR_TYPE_EVENT_DATA_INSTANCE_LOSS_PENDING: {
                    XrEventDataInstanceLossPending instanceLossPending = XrEventDataInstanceLossPending.create(event.address());
                    logger.error("XrEventDataInstanceLossPending by " + instanceLossPending.lossTime());
                    return true;
                }
                case XR_TYPE_EVENT_DATA_SESSION_STATE_CHANGED: {
                    XrEventDataSessionStateChanged sessionStateChangedEvent = XrEventDataSessionStateChanged.create(event.address());
                    return OpenXRHandleSessionStateChangedEvent(sessionStateChangedEvent/*, requestRestart*/);
                }
                case XR_TYPE_EVENT_DATA_INTERACTION_PROFILE_CHANGED:
                    break;
                case XR_TYPE_EVENT_DATA_REFERENCE_SPACE_CHANGE_PENDING:
                default: {
                    logger.info("Ignoring event type " + event.type());
                    break;
                }
                }
                event = readNextOpenXREvent();
            }

            // Poll input.
            pollInput();
        }

        return false;
    }

    private void pollInput() {
        // Sync sets.
        try (MemoryStack stack = MemoryStack.stackPush()) {
            XrActiveActionSet.Buffer sets = XrActiveActionSet.calloc(2, stack);
            sets.get(0).actionSet(actions.getHandle());
            sets.get(1).actionSet(poses.getHandle());

            XrActionsSyncInfo syncInfo = XrActionsSyncInfo.calloc(stack)
                    .type(XR_TYPE_ACTIONS_SYNC_INFO).activeActionSets(sets);

            check(xrSyncActions(xrSession, syncInfo));

            if (actions != null) {
                actions.sync(this);
                var gsActions = (GaiaSkyActionSet) actions;
                for (var listener : listeners) {
                    if (gsActions.showUI.isActive && gsActions.showUI.changedSinceLastSync) {
                        listener.showUI(gsActions.showUI.currentState);
                    }
                    if (gsActions.accept.isActive && gsActions.accept.changedSinceLastSync) {
                        listener.accept(gsActions.accept.currentState);
                    }
                    if (gsActions.cameraMode.isActive && gsActions.cameraMode.changedSinceLastSync) {
                        listener.cameraMode(gsActions.cameraMode.currentState);
                    }
                    if (gsActions.select.isActive && gsActions.select.changedSinceLastSync) {
                        listener.select(gsActions.select.currentState);
                    }
                    if (gsActions.move.isActive && gsActions.move.changedSinceLastSync) {
                        listener.move(gsActions.move.currentState);
                    }
                }
            }
            if (poses != null) {
                poses.sync(this);
            }
        }
    }

    boolean OpenXRHandleSessionStateChangedEvent(XrEventDataSessionStateChanged stateChangedEvent) {
        int oldState = sessionState;
        sessionState = stateChangedEvent.state();

        logger.debug("XrEventDataSessionStateChanged: state " + oldState + "->" + sessionState + " session=" + stateChangedEvent.session() + " time=" + stateChangedEvent.time());

        if ((stateChangedEvent.session() != NULL) && (stateChangedEvent.session() != xrSession.address())) {
            logger.error("XrEventDataSessionStateChanged for unknown session");
            return false;
        }

        switch (sessionState) {
        case XR_SESSION_STATE_READY -> {
            assert (xrSession != null);
            try (MemoryStack stack = stackPush()) {
                check(xrBeginSession(xrSession, XrSessionBeginInfo.malloc(stack).type$Default().next(NULL).primaryViewConfigurationType(viewConfigType)));
                sessionRunning = true;
                return false;
            }
        }
        case XR_SESSION_STATE_STOPPING -> {
            assert (xrSession != null);
            sessionRunning = false;
            check(xrEndSession(xrSession));
            return false;
        }
        case XR_SESSION_STATE_EXITING -> {
            // Do not attempt to restart because user closed this session.
            //*requestRestart = false;
            return true;
        }
        case XR_SESSION_STATE_LOSS_PENDING -> {
            // Poll for a new instance.
            //*requestRestart = true;
            return true;
        }
        default -> {
            return false;
        }
        }
    }

    private XrEventDataBaseHeader readNextOpenXREvent() {
        // It is sufficient to just clear the XrEventDataBuffer header to
        // XR_TYPE_EVENT_DATA_BUFFER rather than recreate it every time
        eventDataBuffer.clear();
        eventDataBuffer.type$Default();
        int result = xrPollEvent(xrInstance, eventDataBuffer);
        if (result == XR_SUCCESS) {
            if (eventDataBuffer.type() == XR_TYPE_EVENT_DATA_EVENTS_LOST) {
                XrEventDataEventsLost dataEventsLost = XrEventDataEventsLost.create(eventDataBuffer.address());
                logger.debug(dataEventsLost.lostEventCount() + " events lost");
            }
            return XrEventDataBaseHeader.create(eventDataBuffer.address());
        }
        if (result == XR_EVENT_UNAVAILABLE) {
            return null;
        }
        throw new IllegalStateException(String.format("[XrResult failure %d in xrPollEvent]", result));
    }

    /**
     * Sends a haptic pulse with the given action, duration, frequency and amplitude.
     *
     * @param left      True to use the left controller, false for the right one.
     * @param duration  The duration in nanoseconds.
     * @param frequency The frequency in Hz.
     * @param amplitude The amplitude in [0,1].
     */
    private void sendHapticPulse(boolean left, long duration, float frequency, float amplitude) {
        if (poses != null) {
            var haptics = (HandsActionSet) poses;
            HapticsAction action;
            if (left) {
                action = haptics.leftHaptic;
            } else {
                action = haptics.rightHaptic;
            }
            if (action != null) {
                action.sendHapticPulse(this, duration, frequency, amplitude);
            }
        }
    }

    public void dispose() {
        disposing = true;
        sessionRunning = false;

        // Input stack.
        disposeInput();

        // OpenXR.
        if (eventDataBuffer != null)
            eventDataBuffer.free();
        if (views != null)
            views.free();
        if (viewConfigs != null)
            viewConfigs.free();
        if (swapchains != null)
            for (Swapchain swapchain : swapchains) {
                xrDestroySwapchain(swapchain.handle);
                swapchain.images.free();
            }

        if (xrAppSpace != null)
            xrDestroySpace(xrAppSpace);
        if (xrDebugMessenger != null)
            xrDestroyDebugUtilsMessengerEXT(xrDebugMessenger);

        if (xrSession != null)
            xrDestroySession(xrSession);

        if (xrInstance != null)
            xrDestroyInstance(xrInstance);

        // Frame buffers.
        if (viewFrameBuffers != null) {
            for (var frameBuffer : viewFrameBuffers) {
                frameBuffer.dispose();
            }
        }
    }

    public void disposeInput() {
        if (actions != null)
            actions.close();
        if (poses != null)
            poses.close();
    }

    public void check(int result) throws IllegalStateException {
        check(result, null);
    }

    public void check(int result, String method) {
        if (XR_SUCCEEDED(result))
            return;

        if (xrInstance != null) {
            ByteBuffer str = stackCalloc(XR10.XR_MAX_RESULT_STRING_SIZE);
            if (xrResultToString(xrInstance, result, str) >= 0) {
                if (method == null) {
                    throw new XrResultException(memUTF8(str, memLengthNT1(str)));
                } else {
                    throw new XrResultException(method + " : " + memUTF8(str, memLengthNT1(str)));
                }
            }
        }

        throw new XrResultException("XR method returned " + result);
    }

    public static class XrResultException extends RuntimeException {
        public XrResultException(String s) {
            super(s);
        }

    }

    public void addListener(VRDeviceListener listener) {
    }

    public void removeListener(VRDeviceListener listener) {
    }

    public int getWidth() {
        return swapchains[0].width;
    }

    public int getHeight() {
        return swapchains[0].height;
    }

    public VRDevice getDeviceByType(VRDeviceType type) {
        return null;
    }

    public Array<VRDevice> getDevicesByType(VRDeviceType type) {
        return new Array<>();
    }

    public Array<VRControllerDevice> getControllerDevices(){
        Array<VRControllerDevice> controllers = new Array<>();
        if(poses != null) {
        }
        return null;
    }

    public Array<VRDevice> getDevices() {
        return new Array<>();
    }

    /**
     * Adds a {@link OpenXRInputListener} to receive events
     */
    public void addListener(OpenXRInputListener listener) {
        if (!this.listeners.contains(listener, true)) {
            this.listeners.add(listener);
        }
    }

    /**
     * Removes a {@link OpenXRInputListener}
     */
    public void removeListener(OpenXRInputListener listener) {
        this.listeners.removeValue(listener, true);
    }

    public void setRenderer(OpenXRRenderer renderer) {
        this.currentRenderer.set(renderer);
    }

    public boolean isRunning() {
        return sessionRunning;
    }
}
