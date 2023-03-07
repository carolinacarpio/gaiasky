package gaiasky.vr.openxr;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import gaiasky.util.Logger;
import gaiasky.util.Logger.Log;
import gaiasky.util.Settings;
import gaiasky.vr.openvr.VRContext.VRDevice;
import gaiasky.vr.openvr.VRContext.VRDeviceType;
import gaiasky.vr.openvr.VRDeviceListener;
import gaiasky.vr.openxr.input.OpenXRInputListener;
import gaiasky.vr.openxr.input.actionsets.ActionSet;
import org.lwjgl.PointerBuffer;
import org.lwjgl.opengl.GL31;
import org.lwjgl.openxr.*;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.Struct;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;

import static org.lwjgl.opengl.GL11.GL_RGB10_A2;
import static org.lwjgl.opengl.GL11.GL_RGBA8;
import static org.lwjgl.opengl.GL21.GL_SRGB8_ALPHA8;
import static org.lwjgl.opengl.GL30.GL_RGBA16F;
import static org.lwjgl.openxr.EXTDebugUtils.*;
import static org.lwjgl.openxr.KHROpenGLEnable.*;
import static org.lwjgl.openxr.XR10.*;
import static org.lwjgl.system.MemoryStack.*;
import static org.lwjgl.system.MemoryUtil.*;

public class OpenXRDriver implements Disposable {
    private static final Log logger = Logger.getLogger(OpenXRDriver.class);

    public long systemID;
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
    private XrActionSet gsActionSet;
    private ActionSet gaiaskyActionSet;
    private XrAction leftPose, rightPose, leftHaptic, rightHaptic, buttonA, buttonB, buttonX, buttonY, axisThumbstick, axisTrigger, buttonThumbstick, buttonTrigger;
    private XrSpace leftPoseSpace, rightPoseSpace;
    protected XrActionStateGetInfo getInfo;
    private XrActionStateBoolean stateBoolean;
    private XrActionStateFloat stateFloat;
    private XrActionStateVector2f stateVector2f;
    private XrActionStatePose statePose;

    private Array<OpenXRInputListener> listeners;
    // One swapchain per view
    public Swapchain[] swapchains;
    public XrViewConfigurationView.Buffer viewConfigs;
    public final int viewConfigType = XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO;

    //Runtime
    XrEventDataBuffer eventDataBuffer;
    int sessionState;
    boolean sessionRunning;

    public static class Swapchain {
        public XrSwapchain handle;
        public int width;
        public int height;
        public XrSwapchainImageOpenGLKHR.Buffer images;
    }

    public OpenXRDriver() {
        listeners = new Array<>();
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

            XrInstanceCreateInfo createInfo = XrInstanceCreateInfo.malloc(stack)
                    .type$Default()
                    .next(NULL)
                    .createFlags(0)
                    .applicationInfo(XrApplicationInfo.calloc(stack)
                            .applicationName(stack.UTF8(Settings.getApplicationName(true)))
                            .apiVersion(XR_CURRENT_API_VERSION))
                    .enabledApiLayerNames(wantedLayers)
                    .enabledExtensionNames(extensions);

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

            logger.info(runtimeName);
            logger.info(runtimeVersionString);

            check(xrGetSystem(
                    xrInstance,
                    XrSystemGetInfo.malloc(stack)
                            .type$Default()
                            .next(NULL)
                            .formFactor(XR_FORM_FACTOR_HEAD_MOUNTED_DISPLAY),
                    pl
            ));

            systemID = pl.get(0);
            if (systemID == 0) {
                throw new IllegalStateException("No compatible headset detected");
            }
            logger.info("Headset found with System ID: " + systemID);
        }
    }

    public XrGraphicsRequirementsOpenGLKHR getXrGraphicsRequirements() {
        try (MemoryStack stack = stackPush()) {
            return getXrGraphicsRequirements(stack);
        }
    }

    public XrGraphicsRequirementsOpenGLKHR getXrGraphicsRequirements(MemoryStack stack) {
        XrGraphicsRequirementsOpenGLKHR graphicsRequirements = XrGraphicsRequirementsOpenGLKHR.malloc(stack)
                .type$Default()
                .next(NULL)
                .minApiVersionSupported(0)
                .maxApiVersionSupported(0);

        xrGetOpenGLGraphicsRequirementsKHR(xrInstance, systemID, graphicsRequirements);
        return graphicsRequirements;
    }

    /**
     * Creates the XrSession object.
     * Third method to call in the OpenXR initialization sequence.
     */
    public void initializeOpenXRSession(long windowHandle) {
        try (MemoryStack stack = stackPush()) {
            //Bind the OpenGL context to the OpenXR instance and create the session
            Struct graphicsBinding = XrHelper.createGraphicsBindingOpenGL(stack, windowHandle);

            PointerBuffer pp = stack.mallocPointer(1);

            check(xrCreateSession(
                    xrInstance,
                    XrSessionCreateInfo.malloc(stack)
                            .type$Default()
                            .next(graphicsBinding.address())
                            .createFlags(0)
                            .systemId(systemID),
                    pp
            ));

            xrSession = new XrSession(pp.get(0), xrInstance);

            if (!missingXrDebug) {
                XrDebugUtilsMessengerCreateInfoEXT ciDebugUtils = XrDebugUtilsMessengerCreateInfoEXT.calloc(stack)
                        .type$Default()
                        .messageSeverities(
                                XR_DEBUG_UTILS_MESSAGE_SEVERITY_INFO_BIT_EXT |
                                        XR_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT |
                                        XR_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT
                        )
                        .messageTypes(
                                XR_DEBUG_UTILS_MESSAGE_TYPE_GENERAL_BIT_EXT |
                                        XR_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT |
                                        XR_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT |
                                        XR_DEBUG_UTILS_MESSAGE_TYPE_CONFORMANCE_BIT_EXT
                        )
                        .userCallback((messageSeverity, messageTypes, pCallbackData, userData) -> {
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

            check(xrCreateReferenceSpace(
                    xrSession,
                    XrReferenceSpaceCreateInfo.malloc(stack)
                            .type$Default()
                            .next(NULL)
                            .referenceSpaceType(XR_REFERENCE_SPACE_TYPE_LOCAL)
                            .poseInReferenceSpace(XrPosef.malloc(stack)
                                    .orientation(XrQuaternionf.malloc(stack)
                                            .x(0)
                                            .y(0)
                                            .z(0)
                                            .w(1))
                                    .position$(XrVector3f.calloc(stack))),
                    pp
            ));

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
            check(xrGetSystemProperties(xrInstance, systemID, systemProperties));

            logger.info("Headset name:" + memUTF8(memAddress(systemProperties.systemName())) + " vendor:" + systemProperties.vendorId());

            XrSystemTrackingProperties trackingProperties = systemProperties.trackingProperties();
            logger.info("Headset orientationTracking:" + trackingProperties.orientationTracking() + " positionTracking:" + trackingProperties.positionTracking());

            XrSystemGraphicsProperties graphicsProperties = systemProperties.graphicsProperties();
            logger.info("Headset MaxWidth:" + graphicsProperties.maxSwapchainImageWidth() + " MaxHeight:" + graphicsProperties.maxSwapchainImageHeight() + " MaxLayerCount:" + graphicsProperties.maxLayerCount());

            IntBuffer pi = stack.mallocInt(1);

            check(xrEnumerateViewConfigurationViews(xrInstance, systemID, viewConfigType, pi, null));
            viewConfigs = XrHelper.fill(
                    XrViewConfigurationView.calloc(pi.get(0)), // Don't use malloc() because that would mess up the `next` field
                    XrViewConfigurationView.TYPE,
                    XR_TYPE_VIEW_CONFIGURATION_VIEW
            );

            check(xrEnumerateViewConfigurationViews(xrInstance, systemID, viewConfigType, pi, viewConfigs));
            int viewCountNumber = pi.get(0);

            views = XrHelper.fill(
                    XrView.calloc(viewCountNumber),
                    XrView.TYPE,
                    XR_TYPE_VIEW
            );

            if (viewCountNumber > 0) {
                check(xrEnumerateSwapchainFormats(xrSession, pi, null));
                LongBuffer swapchainFormats = stack.mallocLong(pi.get(0));
                check(xrEnumerateSwapchainFormats(xrSession, pi, swapchainFormats));

                long[] desiredSwapchainFormats = {
                        GL_RGB10_A2,
                        GL_RGBA16F,
                        GL_SRGB8_ALPHA8,
                        // The two below should only be used as a fallback, as they are linear color formats without enough bits for color
                        // depth, thus leading to banding.
                        GL_RGBA8,
                        GL31.GL_RGBA8_SNORM
                };

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

                    XrSwapchainCreateInfo swapchainCreateInfo = XrSwapchainCreateInfo.malloc(stack)
                            .type$Default()
                            .next(NULL)
                            .createFlags(0)
                            .usageFlags(XR_SWAPCHAIN_USAGE_SAMPLED_BIT | XR_SWAPCHAIN_USAGE_COLOR_ATTACHMENT_BIT)
                            .format(glColorFormat)
                            .sampleCount(viewConfig.recommendedSwapchainSampleCount())
                            .width(viewConfig.recommendedImageRectWidth())
                            .height(viewConfig.recommendedImageRectHeight())
                            .faceCount(1)
                            .arraySize(1)
                            .mipCount(1);

                    PointerBuffer pp = stack.mallocPointer(1);
                    check(xrCreateSwapchain(xrSession, swapchainCreateInfo, pp));

                    swapchainWrapper.handle = new XrSwapchain(pp.get(0), xrSession);
                    swapchainWrapper.width = swapchainCreateInfo.width();
                    swapchainWrapper.height = swapchainCreateInfo.height();

                    check(xrEnumerateSwapchainImages(swapchainWrapper.handle, pi, null));
                    int imageCount = pi.get(0);

                    XrSwapchainImageOpenGLKHR.Buffer swapchainImageBuffer = XrHelper.fill(
                            XrSwapchainImageOpenGLKHR.create(imageCount),
                            XrSwapchainImageOpenGLKHR.TYPE,
                            XR_TYPE_SWAPCHAIN_IMAGE_OPENGL_KHR
                    );

                    check(xrEnumerateSwapchainImages(swapchainWrapper.handle, pi, XrSwapchainImageBaseHeader.create(swapchainImageBuffer.address(), swapchainImageBuffer.capacity())));
                    swapchainWrapper.images = swapchainImageBuffer;
                    swapchains[i] = swapchainWrapper;
                }
            }
        }
    }

    public void initializeInput() {
        // Event buffer.
        eventDataBuffer = XrEventDataBuffer.calloc()
                .type$Default();

        // States for polling.
        getInfo = XrActionStateGetInfo.calloc().type(XR_TYPE_ACTION_STATE_GET_INFO);
        stateBoolean = XrActionStateBoolean.calloc().type(XR_TYPE_ACTION_STATE_BOOLEAN);
        stateFloat = XrActionStateFloat.calloc().type(XR_TYPE_ACTION_STATE_FLOAT);
        stateVector2f = XrActionStateVector2f.calloc().type(XR_TYPE_ACTION_STATE_VECTOR2F);
        statePose = XrActionStatePose.calloc().type(XR_TYPE_ACTION_STATE_POSE);

        // Create action set.
        gsActionSet = createActionSet(xrInstance, "gaiasky");

        // Haptic.
        leftHaptic = createAction(gsActionSet, "left-haptic", "Left haptic", XR_ACTION_TYPE_VIBRATION_OUTPUT);
        rightHaptic = createAction(gsActionSet, "right-haptic", "Right haptic", XR_ACTION_TYPE_VIBRATION_OUTPUT);
        // Poses.
        leftPose = createAction(gsActionSet, "left-hand", "Left hand pose", XR_ACTION_TYPE_POSE_INPUT);
        rightPose = createAction(gsActionSet, "right-hand", "Right hand pose", XR_ACTION_TYPE_POSE_INPUT);
        leftPoseSpace = createActionSpace(xrSession, leftPose);
        rightPoseSpace = createActionSpace(xrSession, rightPose);
        // Buttons.
        buttonA = createAction(gsActionSet, "a-button", "Toggle UI", XR_ACTION_TYPE_BOOLEAN_INPUT);
        buttonB = createAction(gsActionSet, "b-button", "Camera mode", XR_ACTION_TYPE_BOOLEAN_INPUT);
        buttonX = createAction(gsActionSet, "x-button", "Toggle UI (2)", XR_ACTION_TYPE_BOOLEAN_INPUT);
        buttonY = createAction(gsActionSet, "y-button", "Camera mode (2)", XR_ACTION_TYPE_BOOLEAN_INPUT);
        buttonThumbstick = createAction(gsActionSet, "thumbstick-button", XR_ACTION_TYPE_BOOLEAN_INPUT);
        buttonTrigger = createAction(gsActionSet, "trigger-button", "Object selection discrete", XR_ACTION_TYPE_BOOLEAN_INPUT);
        // Axes.
        axisThumbstick = createAction(gsActionSet, "thumbstick-axis", "Movement", XR_ACTION_TYPE_VECTOR2F_INPUT);
        axisTrigger = createAction(gsActionSet, "trigger-axis", "Object selection", XR_ACTION_TYPE_FLOAT_INPUT);

        // Haptics.
        long leftHapticPath = getPath("/user/hand/left/output/haptic");
        long rightHapticPath = getPath("/user/hand/right/output/haptic");

        // Poses.
        long leftPosePath = getPath("/user/hand/left/input/grip/pose");
        long rightPosePath = getPath("/user/hand/right/input/grip/pose");

        // Buttons.
        long buttonARightPath = getPath("/user/hand/right/input/a/click");
        long buttonBRightPath = getPath("/user/hand/right/input/b/click");
        long buttonALeftPath = getPath("/user/hand/left/input/a/click");
        long buttonBLeftPath = getPath("/user/hand/left/input/b/click");
        long buttonXPath = getPath("/user/hand/left/input/x/click");
        long buttonYPath = getPath("/user/hand/left/input/y/click");
        long buttonThumbstickLeftPath = getPath("/user/hand/left/input/thumbstick/click");
        long buttonThumbstickRightPath = getPath("/user/hand/right/input/thumbstick/click");
        long buttonTriggerLeftPath = getPath("/user/hand/left/input/trigger/click");
        long buttonTriggerRightPath = getPath("/user/hand/right/input/trigger/click");

        // Axes.
        long axisThumbstickLeftPath = getPath("/user/hand/left/input/thumbstick");
        long axisThumbstickRightPath = getPath("/user/hand/right/input/thumbstick");
        long axisTriggerLeftPath = getPath("/user/hand/left/input/trigger/value");
        long axisTriggerRightPath = getPath("/user/hand/right/input/trigger/value");

        // Devices.
        long oculusTouchPath = getPath("/interaction_profiles/oculus/touch_controller");
        long indexControllerPath = getPath("/interaction_profiles/valve/index_controller");

        try (MemoryStack stack = stackPush()) {
            int l = 0;
            // OCULUS TOUCH
            XrActionSuggestedBinding.Buffer bindingsOculus = XrActionSuggestedBinding.calloc(15, stack);
            bindingsOculus.get(l++).set(leftHaptic, leftHapticPath);
            bindingsOculus.get(l++).set(rightHaptic, rightHapticPath);
            bindingsOculus.get(l++).set(leftPose, leftPosePath);
            bindingsOculus.get(l++).set(leftPose, leftPosePath);
            bindingsOculus.get(l++).set(rightPose, rightPosePath);
            bindingsOculus.get(l++).set(buttonA, buttonARightPath);
            bindingsOculus.get(l++).set(buttonB, buttonBRightPath);
            bindingsOculus.get(l++).set(buttonA, buttonXPath);
            bindingsOculus.get(l++).set(buttonB, buttonYPath);
            bindingsOculus.get(l++).set(buttonThumbstick, buttonThumbstickLeftPath);
            bindingsOculus.get(l++).set(buttonThumbstick, buttonThumbstickRightPath);
            bindingsOculus.get(l++).set(axisThumbstick, axisThumbstickRightPath);
            bindingsOculus.get(l++).set(axisThumbstick, axisThumbstickLeftPath);
            bindingsOculus.get(l++).set(axisTrigger, axisTriggerRightPath);
            bindingsOculus.get(l++).set(axisTrigger, axisTriggerLeftPath);

            XrInteractionProfileSuggestedBinding suggestedBindingOculus = XrInteractionProfileSuggestedBinding.malloc(stack)
                    .type$Default()
                    .next(NULL)
                    .interactionProfile(oculusTouchPath)
                    .suggestedBindings(bindingsOculus);
            check(xrSuggestInteractionProfileBindings(xrInstance, suggestedBindingOculus));

            l = 0;
            // VALVE INDEX
            XrActionSuggestedBinding.Buffer bindingsIndex = XrActionSuggestedBinding.calloc(17, stack);

            bindingsIndex.get(l++).set(leftHaptic, leftHapticPath);
            bindingsIndex.get(l++).set(rightHaptic, rightHapticPath);
            bindingsIndex.get(l++).set(leftPose, leftPosePath);
            bindingsIndex.get(l++).set(leftPose, leftPosePath);
            bindingsIndex.get(l++).set(rightPose, rightPosePath);
            bindingsIndex.get(l++).set(buttonA, buttonARightPath);
            bindingsIndex.get(l++).set(buttonB, buttonBRightPath);
            bindingsIndex.get(l++).set(buttonA, buttonALeftPath);
            bindingsIndex.get(l++).set(buttonB, buttonBLeftPath);
            bindingsIndex.get(l++).set(buttonThumbstick, buttonThumbstickLeftPath);
            bindingsIndex.get(l++).set(buttonThumbstick, buttonThumbstickRightPath);
            bindingsIndex.get(l++).set(buttonTrigger, buttonTriggerLeftPath);
            bindingsIndex.get(l++).set(buttonTrigger, buttonTriggerRightPath);
            bindingsIndex.get(l++).set(axisThumbstick, axisThumbstickRightPath);
            bindingsIndex.get(l++).set(axisThumbstick, axisThumbstickLeftPath);
            bindingsIndex.get(l++).set(axisTrigger, axisTriggerRightPath);
            bindingsIndex.get(l++).set(axisTrigger, axisTriggerLeftPath);

            XrInteractionProfileSuggestedBinding suggestedBindingIndex = XrInteractionProfileSuggestedBinding.malloc(stack)
                    .type$Default()
                    .next(NULL)
                    .interactionProfile(indexControllerPath)
                    .suggestedBindings(bindingsIndex);
            check(xrSuggestInteractionProfileBindings(xrInstance, suggestedBindingIndex));

            // Attach action set to session.
            XrSessionActionSetsAttachInfo attachInfo = XrSessionActionSetsAttachInfo.calloc(stack).set(
                    XR_TYPE_SESSION_ACTION_SETS_ATTACH_INFO,
                    NULL,
                    stackPointers(gsActionSet.address()));
            check(xrAttachSessionActionSets(xrSession, attachInfo));
        }

    }

    public void destroyInput() {
        destroyActionSpace(leftPoseSpace);
        destroyActionSpace(rightPoseSpace);

        destroyAction(leftPose);
        destroyAction(rightPose);

        destroyActionSet(gsActionSet);
    }

    public XrActionSet createActionSet(XrInstance instance, String name) {
        try (MemoryStack stack = stackPush()) {
            // Create action set.
            XrActionSetCreateInfo setCreateInfo = XrActionSetCreateInfo.malloc(stack)
                    .type$Default()
                    .actionSetName(stack.UTF8("gameplay"))
                    .localizedActionSetName(stack.UTF8("gameplay"))
                    .priority(0);

            PointerBuffer pp = stack.mallocPointer(1);
            check(xrCreateActionSet(instance, setCreateInfo, pp));
            return new XrActionSet(pp.get(0), instance);
        }
    }

    public XrAction createAction(XrActionSet actionSet, String name, int type) {
        return createAction(actionSet, name, name, type);
    }

    /**
     * Creates a new action.
     *
     * @param actionSet The action set.
     * @param name      The name of the action.
     * @param type      The action type.
     */
    public XrAction createAction(XrActionSet actionSet, String name, String localizedName, int type) {
        try (MemoryStack stack = stackPush()) {
            // Create action.
            XrActionCreateInfo createInfo = XrActionCreateInfo.malloc(stack)
                    .type$Default()
                    .next(NULL)
                    .actionName(stack.UTF8(name))
                    .localizedActionName(stack.UTF8(localizedName))
                    .countSubactionPaths(0)
                    .actionType(type);

            PointerBuffer pp = stack.mallocPointer(1);
            check(xrCreateAction(actionSet, createInfo, pp));
            return new XrAction(pp.get(0), actionSet);
        }
    }

    public XrSpace createActionSpace(XrSession session, XrAction action) {
        try (MemoryStack stack = stackPush()) {
            XrActionSpaceCreateInfo createInfo = XrActionSpaceCreateInfo.malloc(stack)
                    .type$Default()
                    .poseInActionSpace(XrPosef.malloc(stack)
                            .position$(XrVector3f.calloc(stack).set(0, 0, 0))
                            .orientation(XrQuaternionf.malloc(stack)
                                    .x(0)
                                    .y(0)
                                    .z(0)
                                    .w(1)))
                    .action(action);

            PointerBuffer pp = stack.mallocPointer(1);
            check(xrCreateActionSpace(session, createInfo, pp));
            return new XrSpace(pp.get(0), session);
        }
    }

    public long getPath(String name) {
        try (MemoryStack stack = stackPush()) {
            LongBuffer path = stack.longs(0);
            check(xrStringToPath(xrInstance, stack.UTF8(name), path));
            return path.get();
        }
    }

    public void destroyActionSet(XrActionSet actionSet) {
        xrDestroyActionSet(actionSet);
    }

    public void destroyAction(XrAction action) {
        xrDestroyAction(action);
    }

    public void destroyActionSpace(XrSpace space) {
        xrDestroySpace(space);
    }

    /**
     * Polls pending events in the OpenXR system.
     * @return True if we must stop, false otherwise.
     */
    public boolean pollEvents() {
        // Poll input first.
        pollInput();

        XrEventDataBaseHeader event = readNextOpenXREvent();
        while(event != null) {
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

        return false;
    }

    private void pollInput() {
        try (var stack = stackPush()) {
            XrActiveActionSet.Buffer sets = XrActiveActionSet.calloc(1, stack);
            sets.actionSet(gsActionSet);

            XrActionsSyncInfo syncInfo = XrActionsSyncInfo.calloc(stack)
                    .type(XR_TYPE_ACTIONS_SYNC_INFO).activeActionSets(sets);

            check(xrSyncActions(xrSession, syncInfo));

            // Button A
            getInfo.action(buttonA);
            check(xrGetActionStateBoolean(xrSession, getInfo, stateBoolean));
            if (stateBoolean.changedSinceLastSync()) {
                for (var listener : listeners) {
                    if (listener.buttonA(stateBoolean.currentState())) {
                        break;
                    }
                }
            }
            // Button B
            getInfo.action(buttonB);
            check(xrGetActionStateBoolean(xrSession, getInfo, stateBoolean));
            if (stateBoolean.changedSinceLastSync()) {
                for (var listener : listeners) {
                    if (listener.buttonB(stateBoolean.currentState())) {
                        break;
                    }
                }
            }
            // Button Thumbstick
            getInfo.action(buttonThumbstick);
            check(xrGetActionStateBoolean(xrSession, getInfo, stateBoolean));
            if (stateBoolean.changedSinceLastSync()) {
                for (var listener : listeners) {
                    if (listener.buttonThumbstick(stateBoolean.currentState())) {
                        break;
                    }
                }
            }
            // Button Trigger
            getInfo.action(buttonTrigger);
            check(xrGetActionStateBoolean(xrSession, getInfo, stateBoolean));
            if (stateBoolean.changedSinceLastSync()) {
                for (var listener : listeners) {
                    if (listener.buttonTrigger(stateBoolean.currentState())) {
                        break;
                    }
                }
            }

            // Trigger
            getInfo.action(axisTrigger);
            check(xrGetActionStateFloat(xrSession, getInfo, stateFloat));
            if (stateFloat.changedSinceLastSync()) {
                for (var listener : listeners) {
                    if (listener.trigger(stateFloat.currentState())) {
                        break;
                    }
                }
            }

            // Thumbstick
            getInfo.action(axisThumbstick);
            check(xrGetActionStateVector2f(xrSession, getInfo, stateVector2f));
            if (stateVector2f.changedSinceLastSync()) {
                for (var listener : listeners) {
                    if (listener.thumbstick(stateVector2f.currentState())) {
                        break;
                    }
                }
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
                check(xrBeginSession(
                        xrSession,
                        XrSessionBeginInfo.malloc(stack)
                                .type$Default()
                                .next(NULL)
                                .primaryViewConfigurationType(viewConfigType)
                ));
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
     * @param action    The action.
     * @param duration  The duration in nanoseconds.
     * @param frequency The frequency in Hz.
     * @param amplitude The amplitude in [0,1].
     */
    private void sendHapticPulse(XrAction action, long duration, float frequency, float amplitude) {
        try (var stack = stackPush()) {
            // Haptic feedback.
            XrHapticActionInfo info = XrHapticActionInfo.calloc(stack)
                    .type$Default()
                    .next(NULL)
                    .action(action);
            XrHapticVibration vibration = XrHapticVibration.calloc(stack)
                    .type(XR_TYPE_HAPTIC_VIBRATION)
                    .next(NULL)
                    .duration(duration)
                    .frequency(frequency)
                    .amplitude(amplitude);
            XrHapticBaseHeader header = XrHapticBaseHeader.create(vibration.address());
            check(xrApplyHapticFeedback(xrSession, info, header));
        }
    }

    public void dispose() {
        // Destroy OpenXR
        eventDataBuffer.free();
        views.free();
        viewConfigs.free();
        for (Swapchain swapchain : swapchains) {
            xrDestroySwapchain(swapchain.handle);
            swapchain.images.free();
        }

        xrDestroySpace(xrAppSpace);
        if (xrDebugMessenger != null) {
            xrDestroyDebugUtilsMessengerEXT(xrDebugMessenger);
        }
        xrDestroySession(xrSession);
        xrDestroyInstance(xrInstance);

        destroyInput();
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

    public boolean isRunning() {
        return sessionRunning;
    }
}
