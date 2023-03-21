package gaiasky.vr.openxr.input.actionsets;

import gaiasky.vr.openxr.OpenXRInstance;
import gaiasky.vr.openxr.input.ControllerPoses;
import gaiasky.vr.openxr.input.actions.Action;
import gaiasky.vr.openxr.input.actions.MultiPoseAction;
import oshi.util.tuples.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class HandsActionSet extends ActionSet {

    public MultiPoseAction grip = new MultiPoseAction("grip", new String[]{"/user/hand/left", "/user/hand/right"});
    public MultiPoseAction aim = new MultiPoseAction("aim", new String[]{"/user/hand/left", "/user/hand/right"});

    public final ControllerPoses[] gripPoses = {new ControllerPoses(), new ControllerPoses()};
    public final ControllerPoses[] aimPoses = {new ControllerPoses(), new ControllerPoses()};

    public HandsActionSet() {
        super("hands", 0);
    }

    @Override
    public List<Action> actions() {
        return List.of(
                grip,
                aim
        );
    }

    @Override
    public void getDefaultBindings(HashMap<String, List<Pair<Action, String>>> map) {

        map.computeIfAbsent("/interaction_profiles/oculus/touch_controller", aLong -> new ArrayList<>()).addAll(
                List.of(
                        new Pair<>(grip, "/user/hand/left/input/grip/pose"),
                        new Pair<>(grip, "/user/hand/right/input/grip/pose"),
                        new Pair<>(aim, "/user/hand/left/input/aim/pose"),
                        new Pair<>(aim, "/user/hand/right/input/aim/pose")
                )
        );

        if (!driver.runtimeName.contains("Oculus")) {

            map.computeIfAbsent("/interaction_profiles/valve/index_controller", aLong -> new ArrayList<>()).addAll(
                    List.of(
                            new Pair<>(grip, "/user/hand/left/input/grip/pose"),
                            new Pair<>(grip, "/user/hand/right/input/grip/pose"),
                            new Pair<>(aim, "/user/hand/left/input/aim/pose"),
                            new Pair<>(aim, "/user/hand/right/input/aim/pose")
                    )
            );
            map.computeIfAbsent("/interaction_profiles/microsoft/motion_controller", aLong -> new ArrayList<>()).addAll(
                    List.of(
                            new Pair<>(grip, "/user/hand/left/input/grip/pose"),
                            new Pair<>(grip, "/user/hand/right/input/grip/pose"),
                            new Pair<>(aim, "/user/hand/left/input/aim/pose"),
                            new Pair<>(aim, "/user/hand/right/input/aim/pose")
                    )
            );
        }


        if (driver.xrInstance.getCapabilities().XR_EXT_hp_mixed_reality_controller) {
            map.computeIfAbsent("/interaction_profiles/hp/mixed_reality_controller", aLong -> new ArrayList<>()).addAll(
                    List.of(
                            new Pair<>(grip, "/user/hand/left/input/grip/pose"),
                            new Pair<>(grip, "/user/hand/right/input/grip/pose"),
                            new Pair<>(aim, "/user/hand/left/input/aim/pose"),
                            new Pair<>(aim, "/user/hand/right/input/aim/pose")
                    )
            );
        }
        if (driver.xrInstance.getCapabilities().XR_HTC_vive_cosmos_controller_interaction) {
            map.computeIfAbsent("/interaction_profiles/htc/vive_cosmos_controller", aLong -> new ArrayList<>()).addAll(
                    List.of(
                            new Pair<>(grip, "/user/hand/left/input/grip/pose"),
                            new Pair<>(grip, "/user/hand/right/input/grip/pose"),
                            new Pair<>(aim, "/user/hand/left/input/aim/pose"),
                            new Pair<>(aim, "/user/hand/right/input/aim/pose")
                    )
            );
        }

    }
}
