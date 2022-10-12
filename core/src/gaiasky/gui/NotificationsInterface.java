/*
 * This file is part of Gaia Sky, which is released under the Mozilla Public License 2.0.
 * See the file LICENSE.md in the project root for full license details.
 */

package gaiasky.gui;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.ui.Cell;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.utils.StringBuilder;
import gaiasky.data.util.PointCloudData;
import gaiasky.event.Event;
import gaiasky.event.EventManager;
import gaiasky.event.IObserver;
import gaiasky.scene.view.FocusView;
import gaiasky.scene.camera.CameraManager.CameraMode;
import gaiasky.util.Logger.LoggerLevel;
import gaiasky.util.Pair;
import gaiasky.util.Settings;
import gaiasky.util.Settings.StereoProfile;
import gaiasky.util.i18n.I18n;
import gaiasky.util.scene2d.OwnLabel;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedList;
import java.util.List;

/**
 * Widget that captures and displays messages in a GUI.
 */
public class NotificationsInterface extends TableGuiInterface implements IObserver {
    private static final long DEFAULT_TIMEOUT = 5000;
    private static final String TAG_SEPARATOR = " - ";
    static LinkedList<MessageBean> historical = new LinkedList<>();
    DateTimeFormatter df;
    long msTimeout;
    Label message1, message2;
    Cell<Label> c1, c2;
    boolean displaying = false;
    boolean historicalLog = false;
    boolean permanent = false;
    boolean multiple;
    boolean writeDates = true;

    /**
     * Lock object for synchronization
     **/
    Object lock;

    /**
     * Initializes the notifications interface.
     *
     * @param skin       The skin.
     * @param lock       The lock object.
     * @param multiple   Allow multiple messages?
     * @param writeDates Write dates with messages?
     * @param bg         Apply background
     */
    public NotificationsInterface(Skin skin, Object lock, boolean multiple, boolean writeDates, boolean bg) {
        this(skin, lock, multiple, bg);
        this.writeDates = writeDates;
    }

    /**
     * Initializes the notifications interface.
     *
     * @param skin          The skin.
     * @param lock          The lock object.
     * @param multiple      Allow multiple messages?
     * @param writeDates    Write dates with messages?
     * @param historicalLog Save logs to historical list
     * @param bg            Apply background
     */
    public NotificationsInterface(Skin skin, Object lock, boolean multiple, boolean writeDates, boolean historicalLog, boolean bg) {
        this(skin, lock, multiple, writeDates, bg);
        this.historicalLog = historicalLog;
    }

    /**
     * Initializes the notifications interface.
     *
     * @param skin     The skin.
     * @param lock     The lock object.
     * @param multiple Allow multiple messages?
     * @param bg       Apply background
     */
    public NotificationsInterface(Skin skin, Object lock, boolean multiple, boolean bg) {
        this(null, DEFAULT_TIMEOUT, skin, multiple, bg);
        this.lock = lock;

    }

    /**
     * Initializes the notifications interface.
     *
     * @param logs      Current logs
     * @param msTimeout The timeout in ms
     * @param skin      The skin
     * @param multiple  Multiple messages enabled
     * @param bg        Apply background
     */
    public NotificationsInterface(List<MessageBean> logs, long msTimeout, Skin skin, boolean multiple, boolean bg) {
        super(skin);
        if (logs != null)
            historical.addAll(logs);
        this.msTimeout = msTimeout;
        this.multiple = multiple;

        if (bg)
            this.setBackground("table-bg");

        // Create second message if necessary
        if (multiple) {
            message2 = new OwnLabel("", skin, "hud-med");
            c2 = this.add(message2).left();
            c2.row();
        }
        // Create message
        message1 = new OwnLabel("", skin, "hud-med");
        c1 = this.add(message1).left();

        this.df = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss").withLocale(I18n.locale).withZone(ZoneOffset.UTC);
        EventManager.instance.subscribe(this, Event.POST_NOTIFICATION, Event.FOCUS_CHANGED, Event.TIME_STATE_CMD, Event.TOGGLE_VISIBILITY_CMD, Event.CAMERA_MODE_CMD, Event.TIME_WARP_CHANGED_INFO, Event.FOCUS_LOCK_CMD, Event.TOGGLE_AMBIENT_LIGHT, Event.FOV_CHANGE_NOTIFICATION, Event.JAVA_EXCEPTION, Event.ORBIT_DATA_LOADED, Event.SCREENSHOT_INFO, Event.STEREOSCOPIC_CMD, Event.DISPLAY_GUI_CMD, Event.FRAME_OUTPUT_CMD, Event.STEREO_PROFILE_CMD, Event.OCTREE_PARTICLE_FADE_CMD, Event.SCREEN_NOTIFICATION_CMD, Event.MODE_POPUP_CMD);
    }

    public void unsubscribe() {
        EventManager.instance.removeAllSubscriptions(this);
    }

    private void addMessage(String msg) {
        addMessage(msg, false, LoggerLevel.INFO);
    }

    private void addMessage(String msg, boolean permanent, LoggerLevel level) {
        MessageBean messageBean = new MessageBean(msg);

        boolean debug = level.equals(LoggerLevel.DEBUG);
        boolean add = !debug || debug && Gdx.app.getLogLevel() >= Application.LOG_DEBUG;

        if (add) {
            if (multiple && !historical.isEmpty() && !historical.getLast().finished(msTimeout)) {
                // Move current up
                setText(message2, c2, message1.getText());
            }
            // Set 1
            setText(message1, c1, formatMessage(messageBean, level));

            this.displaying = true;
            this.permanent = permanent;

            if (historicalLog)
                historical.add(messageBean);

        }

    }

    private String formatMessage(MessageBean msgBean, LoggerLevel level) {
        String lvl = level.equals(LoggerLevel.DEBUG) ? " DEBUG" : "";
        return (writeDates ? df.format(msgBean.date) + lvl + TAG_SEPARATOR : (lvl.isBlank() ? "" : lvl + TAG_SEPARATOR)) + msgBean.msg;
    }

    public void update() {
        if (displaying && !permanent) {
            if (multiple && historical.size() > 1 && historical.get(historical.size() - 2).finished(msTimeout)) {
                clearText(message2, c2);
            }

            if (historical.getLast().finished(msTimeout)) {
                displaying = false;
                clearText(message1, c1);
            }

            if (!c1.hasActor() && !c2.hasActor()) {
                setVisible(false);
            } else if (c1.hasActor() || c2.hasActor()) {
                setVisible(true);
            }
        }
    }

    private void setText(Label l, Cell<Label> c, String text) {
        l.setText(text);
        c.setActor(l);
        setVisible(true);
    }

    private void setText(Label l, Cell<Label> c, StringBuilder text) {
        setText(l, c, text.toString());
    }

    private void clearText(Label l, Cell<Label> c) {
        l.setText("");
        c.setActor(null);
    }

    @Override
    public void notify(final Event event, Object source, final Object... data) {
        synchronized (lock) {
            switch (event) {
            case POST_NOTIFICATION:
                LoggerLevel level = (LoggerLevel) data[0];
                Object[] dat = (Object[]) data[1];
                String message = "";
                boolean perm = false;
                for (int i = 0; i < dat.length; i++) {
                    if (i == dat.length - 1 && dat[i] instanceof Boolean) {
                        perm = (Boolean) dat[i];
                    } else {
                        message += dat[i].toString();
                        if (i < dat.length - 1 && !(i == dat.length - 2 && dat[dat.length - 1] instanceof Boolean)) {
                            message += TAG_SEPARATOR;
                        }
                    }
                }
                addMessage(message, perm, level);
                break;
            case FOCUS_CHANGED:
                if (data[0] != null) {
                    if (data[0] instanceof String) {
                        addMessage(I18n.msg("notif.camerafocus", data[0]));
                    } else {
                        var focus = (FocusView) data[0];
                        addMessage(I18n.msg("notif.camerafocus", focus.getName()));
                    }
                }
                break;
            case TIME_STATE_CMD:
                Boolean bool = (Boolean) data[0];
                if (bool == null) {
                    addMessage(I18n.msg("notif.toggle", I18n.msg("gui.time")));
                } else {
                    addMessage(I18n.msg("notif.simulation." + (bool ? "resume" : "pause")));
                }
                break;
            case TOGGLE_VISIBILITY_CMD:
                if (data.length == 2)
                    addMessage(I18n.msg("notif.visibility." + (((Boolean) data[1]) ? "on" : "off"), I18n.msg((String) data[0])));
                else
                    addMessage(I18n.msg("notif.visibility.toggle", I18n.msg((String) data[0])));
                break;
            case FOCUS_LOCK_CMD:
            case ORIENTATION_LOCK_CMD:
            case TOGGLE_AMBIENT_LIGHT:
            case OCTREE_PARTICLE_FADE_CMD:
                addMessage(data[0] + (((Boolean) data[1]) ? " on" : " off"));
                break;
            case CAMERA_MODE_CMD:
                CameraMode cm = (CameraMode) data[0];
                if (cm != CameraMode.FOCUS_MODE)
                    addMessage(I18n.msg("notif.cameramode.change", data[0]));
                break;
            case TIME_WARP_CHANGED_INFO:
                addMessage(I18n.msg("notif.timepace.change", data[0]));
                break;
            case FOV_CHANGE_NOTIFICATION:
                // addMessage("Field of view changed to " + (float) data[0]);
                break;
            case JAVA_EXCEPTION:
                Throwable t = (Throwable) data[0];
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                t.printStackTrace(pw);
                String stackTrace = sw.toString();
                if (data.length == 1) {
                    if (I18n.messages != null)
                        addMessage(I18n.msg("notif.error", stackTrace));
                    else
                        addMessage("Error: " + stackTrace);
                } else {
                    if (I18n.messages != null)
                        addMessage(I18n.msg("notif.error", data[1] + TAG_SEPARATOR + stackTrace));
                    else
                        addMessage("Error: " + data[1] + TAG_SEPARATOR + stackTrace);
                }
                break;
            case ORBIT_DATA_LOADED:
                addMessage(I18n.msg("notif.orbitdata.loaded", data[1], ((PointCloudData) data[0]).getNumPoints()), false, LoggerLevel.DEBUG);
                break;
            case SCREENSHOT_INFO:
                addMessage(I18n.msg("notif.screenshot", data[0]));
                break;
            case STEREOSCOPIC_CMD:
                addMessage(I18n.msg("notif.toggle", I18n.msg("notif.stereoscopic")));
                break;
            case DISPLAY_GUI_CMD:
                boolean displayGui = (Boolean) data[0];
                addMessage(I18n.msg("notif." + (!displayGui ? "activated" : "deactivated"), data[1]));
                break;
            case STEREO_PROFILE_CMD:
                addMessage(I18n.msg("notif.stereoscopic.profile", StereoProfile.values()[(Integer) data[0]].toString()));
                break;
            case FRAME_OUTPUT_CMD:
                boolean activated = (Boolean) data[0];
                if (activated) {
                    addMessage(I18n.msg("notif.activated", I18n.msg("element.frameoutput")));
                } else {
                    addMessage(I18n.msg("notif.deactivated", I18n.msg("element.frameoutput")));
                }
                break;
            case SCREEN_NOTIFICATION_CMD:
                String title = (String) data[0];
                String[] msgs = (String[]) data[1];
                float time = (Float) data[2];

                // Log to output
                addMessage(title);
                for (String msg : msgs)
                    addMessage(msg);

                break;
            case MODE_POPUP_CMD:
                ModePopupInfo mpi = (ModePopupInfo) data[0];
                if (mpi != null && Settings.settings.runtime.displayGui && Settings.settings.program.ui.modeChangeInfo) {
                    addMessage(mpi.title);
                    addMessage(mpi.header);
                    for (Pair<String[], String> p : mpi.mappings) {
                        String[] keys = p.getFirst();
                        String action = p.getSecond();
                        StringBuilder msg = new StringBuilder();
                        msg.append("<");
                        for (int i = 0; i < keys.length; i++) {
                            msg.append(keys[i].toUpperCase());
                            if (i < keys.length - 1) {
                                msg.append("+");
                            }
                        }
                        msg.append("> ").append(action);
                        addMessage(msg.toString());
                    }
                }
                break;
            default:
                break;
            }
        }
    }

    public static int getNumberMessages() {
        return historical.size();
    }

    public static List<MessageBean> getHistorical() {
        return historical;
    }

    public void dispose() {
        unsubscribe();
    }

    public float getMessage1Width() {
        return message1 != null ? message1.getWidth() : 0;
    }

    public float getMessage2Width() {
        return message2 != null ? message2.getWidth() : 0;
    }

    public float getMessagesWidth() {
        return Math.max(getMessage1Width(), getMessage2Width());
    }

}
