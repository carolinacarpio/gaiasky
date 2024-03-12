/*
 * Copyright (c) 2023 Gaia Sky - All rights reserved.
 *  This file is part of Gaia Sky, which is released under the Mozilla Public License 2.0.
 *  You may use, distribute and modify this code under the terms of MPL2.
 *  See the file LICENSE.md in the project root for full license details.
 */

package gaiasky.util.camera.rec;

import gaiasky.event.Event;
import gaiasky.event.EventManager;
import gaiasky.event.IObserver;
import gaiasky.util.Logger;
import gaiasky.util.Logger.Log;
import gaiasky.util.Settings;
import gaiasky.util.SysUtils;
import gaiasky.util.i18n.I18n;
import gaiasky.util.math.Vector3b;
import gaiasky.util.math.Vector3d;
import gaiasky.util.time.ITimeFrameProvider;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.atomic.AtomicReference;

public class Camcorder implements IObserver {
    private static final Log logger = Logger.getLogger(Camcorder.class);
    /**
     * Singleton camcorder instance.
     **/
    public static Camcorder instance;
    private final DateFormat df;
    Vector3d dir, upp, aux1, aux2;
    float time;
    private final AtomicReference<RecorderState> mode;
    private CameraPath recordingPath, playingPath;
    private double fpsLimitBackup;
    private long startMs;
    private final AtomicReference<String> currentFileName;

    public Camcorder() {
        this.mode = new AtomicReference<>(RecorderState.IDLE);
        this.currentFileName = new AtomicReference<>(null);

        df = new SimpleDateFormat("yyyyMMdd_HH-mm-ss-SSS");

        dir = new Vector3d();
        upp = new Vector3d();
        aux1 = new Vector3d();
        aux2 = new Vector3d();

        EventManager.instance.subscribe(this, Event.RECORD_CAMERA_CMD, Event.PLAY_CAMERA_CMD, Event.UPDATE_CAM_RECORDER, Event.STOP_CAMERA_PLAY);
    }

    public static void initialize() {
        // Initialize own
        instance = new Camcorder();
        // Initialize keyframe manager
        KeyframesManager.initialize();
    }

    public void update(ITimeFrameProvider time,
                       Vector3b position,
                       Vector3d direction,
                       Vector3d up) {
        switch (mode.get()) {
        case RECORDING:
            if (recordingPath != null) {
                recordingPath.add(time.getTime().toEpochMilli(),
                                  position.x(), position.y(), position.z(),
                                  direction.x(), direction.y(), direction.z(),
                                  up.x(), up.y(), up.z());
            }
            break;
        case PLAYING:
            if (playingPath != null) {
                if (playingPath.i < playingPath.n) {
                    // Set time.
                    EventManager.publish(Event.TIME_CHANGE_CMD, this, Instant.ofEpochMilli(playingPath.times.get((int) playingPath.i)));

                    // Set position, direction, up.
                    int ip = (int) playingPath.i * 9;
                    position.set(playingPath.data.get(ip), playingPath.data.get(ip + 1), playingPath.data.get(ip + 2));
                    direction.set(playingPath.data.get(ip + 3), playingPath.data.get(ip + 4), playingPath.data.get(ip + 5));
                    up.set(playingPath.data.get(ip + 6), playingPath.data.get(ip + 7), playingPath.data.get(ip + 8));

                    // Advance step.
                    playingPath.i++;

                } else {
                    // Revert frame rate limit.
                    if (playingPath.frameRate > 0.0) {
                        EventManager.publish(Event.LIMIT_FPS_CMD, this, fpsLimitBackup);
                    }
                    // We have finished, stop playing mode.
                    playingPath = null;
                    mode.set(RecorderState.IDLE);
                    // Stop camera.
                    EventManager.publish(Event.CAMERA_STOP, this);
                    // Post notification.
                    logger.info(I18n.msg("notif.cameraplay.done"));

                    // Issue message informing playing has stopped
                    EventManager.publish(Event.CAMERA_PLAY_INFO, this, false);

                    // Stop frame output if it is on!
                    EventManager.publish(Event.FRAME_OUTPUT_CMD, this, false);
                    break;
                }
            }
            break;
        case IDLE:
            break;
        }

    }

    @Override
    public void notify(final Event event,
                       Object source,
                       final Object... data) {
        switch (event) {
        case RECORD_CAMERA_CMD -> {
            var state = (Boolean) data[0];
            if (state && mode.get() != RecorderState.IDLE) {
                logger.warn("The recorder is busy! The current mode is " + mode);
                return;
            }
            // Start recording
            RecorderState newMode;
            if (state) {
                newMode = RecorderState.RECORDING;
            } else {
                newMode = RecorderState.IDLE;
            }
            if (newMode == RecorderState.RECORDING) {
                // We start recording, prepare buffer!
                if (mode.get() == RecorderState.RECORDING) {
                    logger.info(I18n.msg("error.camerarecord.already"));
                    return;
                }

                // Create recording path.
                recordingPath = new CameraPath(Settings.settings.camrecorder.targetFps);

                // Cap frames if needed.
                if (recordingPath.frameRate > 0.0) {
                    fpsLimitBackup = Settings.settings.graphics.fpsLimit;
                    EventManager.publish(Event.LIMIT_FPS_CMD, this, recordingPath.frameRate);
                }

                // Set mode.
                logger.info(I18n.msg("notif.camerarecord.start"));
                startMs = System.currentTimeMillis();
                time = 0;
                mode.set(RecorderState.RECORDING);

                if (data.length > 1 && data[1] != null && !((String) data[1]).isBlank()) {
                    currentFileName.set((String) data[1]);
                } else {
                    currentFileName.set(null);
                }

            } else {
                // Flush and close
                if (mode.get() == RecorderState.IDLE) {
                    // No recording to cancel
                    return;
                }

                // Restore frame cap.
                if (recordingPath != null && recordingPath.frameRate > 0.0) {
                    EventManager.publish(Event.LIMIT_FPS_CMD, this, fpsLimitBackup);
                }

                // Create filename.
                String filename;
                if (data.length > 1 && data[1] != null && !((String) data[1]).isBlank()) {
                    filename = (String) data[1];
                } else {
                    if (currentFileName.get() != null) {
                        filename = currentFileName.get();
                        currentFileName.set(null);
                    } else {
                        // Use default name.
                        filename = df.format(new Date());
                    }
                }
                // Annotate by date.
                Path f = SysUtils.getDefaultCameraDir().resolve(filename + ".gsc");
                if (Files.exists(f)) {
                    // Make unique.
                    f = SysUtils.uniqueFileName(f);
                }

                // Persist path.
                try {
                    recordingPath.persist(f);
                } catch (Exception e) {
                    logger.error(e);
                } finally {
                    recordingPath = null;
                }

                long elapsed = System.currentTimeMillis() - startMs;
                startMs = 0;
                float secs = elapsed / 1000f;
                assert f != null;
                logger.info(I18n.msg("notif.camerarecord.done", f.toAbsolutePath(), secs));
                EventManager.publish(Event.POST_POPUP_NOTIFICATION, this, I18n.msg("notif.camerarecord.done", f.toAbsolutePath(), secs));
                mode.set(RecorderState.IDLE);
            }
        }
        case PLAY_CAMERA_CMD -> {
            // Start playing
            if (playingPath != null) {
                logger.warn("Hey, we are already playing another camera file!");
            }
            if (mode.get() != RecorderState.IDLE) {
                logger.warn("The recorder is busy! The current mode is " + mode);
                return;
            }
            Object f = data[0];
            Path file;
            if (f instanceof String) {
                file = Paths.get((String) f);
            } else {
                file = (Path) f;
            }
            try {
                // Create new camera path.
                playingPath = new CameraPath(Files.newInputStream(file));

                logger.info(I18n.msg("notif.cameraplay.start", file));
                EventManager.publish(Event.POST_POPUP_NOTIFICATION, this, I18n.msg("notif.cameraplay.start", file));

                // Limit frame rate if necessary.
                if (playingPath.frameRate > 0.0) {
                    fpsLimitBackup = Settings.settings.graphics.fpsLimit;
                    EventManager.publish(Event.LIMIT_FPS_CMD, this, playingPath.frameRate);
                }
                // Start playing mode.
                mode.set(RecorderState.PLAYING);

                // Issue message informing playing has started.
                EventManager.publish(Event.CAMERA_PLAY_INFO, this, true);

                // Enable frame output if option is on.
                if (Settings.settings.camrecorder.auto) {
                    // Stop frame output if it is on!
                    EventManager.publish(Event.FRAME_OUTPUT_CMD, this, true);
                }
            } catch (Exception e) {
                EventManager.publish(Event.POST_POPUP_NOTIFICATION, this, I18n.msg("error.file.parse", file));
                logger.error(e);
            }
        }
        case UPDATE_CAM_RECORDER -> {
            // Update with current position
            ITimeFrameProvider dt = (ITimeFrameProvider) data[0];
            Vector3b pos = (Vector3b) data[1];
            Vector3d dir = (Vector3d) data[2];
            Vector3d up = (Vector3d) data[3];
            update(dt, pos, dir, up);
        }
        case STOP_CAMERA_PLAY -> {
            if (mode.get() != RecorderState.PLAYING) {
                logger.warn("Camera is not playing. Current mode: " + mode);
                return;
            }
            // Stop playing
            mode.set(RecorderState.IDLE);
            // Stop camera
            EventManager.publish(Event.CAMERA_STOP, this);
            // Post notification
            logger.info(I18n.msg("notif.cameraplay.done"));
            EventManager.publish(Event.POST_POPUP_NOTIFICATION, this, I18n.msg("notif.cameraplay.done"));

            // Issue message informing playing has stopped
            EventManager.publish(Event.CAMERA_PLAY_INFO, this, false);

            // Stop frame output if it is on!
            EventManager.publish(Event.FRAME_OUTPUT_CMD, this, false);
        }
        default -> {
        }
        }
    }

    public boolean isRecording() {
        return mode.get() == RecorderState.RECORDING;
    }

    public boolean isPlaying() {
        return mode.get() == RecorderState.PLAYING;
    }

    public enum RecorderState {
        // Recording in classical mode (one state per frame)
        RECORDING,
        // Playing classical recording
        PLAYING,
        // Stepping
        STEPPING,
        // Idle
        IDLE
    }

}
