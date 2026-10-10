package com.med.sleepmanager.protocol;

/**
 * Single source of truth for the command/reply protocol shared by the main
 * SleepManager APK and the compatibility Helper APK.
 *
 * Keep AndroidManifest intent-filter literals aligned with these values.
 */
public final class HelperProtocol {
    private HelperProtocol() {}

    public static final String MAIN_PACKAGE = "com.med.sleepmanager";
    public static final String HELPER_PACKAGE = "com.med.sleepmanager.helper";
    public static final String HELPER_RECEIVER_CLASS =
            "com.med.sleepmanager.helper.SleepManagerHelperReceiver";
    public static final String HELPER_ACTIVATION_ACTIVITY_CLASS =
            "com.med.sleepmanager.helper.HelperActivationActivity";
    public static final String HELPER_RECEIVER_V2_CLASS =
            "com.med.sleepmanager.helper.SleepManagerHelperReceiverV2";
    public static final String HELPER_ACTIVATION_ACTIVITY_V2_CLASS =
            "com.med.sleepmanager.helper.HelperActivationActivityV2";
    public static final String HELPER_COMMAND_SERVICE_V2_CLASS =
            "com.med.sleepmanager.helper.HelperCommandServiceV2";
    public static final String PERMISSION =
            "com.med.sleepmanager.permission.CONTROL_HELPER";
    public static final String PERMISSION_V2 =
            "com.med.sleepmanager.helper.permission.CONTROL";
    public static final long HELPER_PROTOCOL_V2_MIN_VERSION_CODE = 1117L;
    public static final long HELPER_SERVICE_V2_MIN_VERSION_CODE = 1118L;

    public static final String ACTION_SLEEP =
            "com.med.sleepmanager.helper.action.SLEEP";
    public static final String ACTION_WAKE =
            "com.med.sleepmanager.helper.action.WAKE";
    public static final String ACTION_RESTORE =
            "com.med.sleepmanager.helper.action.RESTORE";
    public static final String ACTION_QUERY =
            "com.med.sleepmanager.helper.action.QUERY_STATE";
    public static final String ACTION_FORGET_STATE =
            "com.med.sleepmanager.helper.action.FORGET_STATE";
    public static final String ACTION_SET_TEMP_WIFI =
            "com.med.sleepmanager.helper.action.SET_TEMP_WIFI";
    public static final String ACTION_STATE =
            "com.med.sleepmanager.helper.action.STATE";
    public static final String ACTION_RESULT =
            "com.med.sleepmanager.helper.action.RESULT";
    public static final String ACTION_STATE_V2 =
            "com.med.sleepmanager.helper.action.STATE_V2";
    public static final String ACTION_RESULT_V2 =
            "com.med.sleepmanager.helper.action.RESULT_V2";

    public static final String EXTRA_WIFI = "wifi";
    public static final String EXTRA_BLUETOOTH = "bluetooth";
    public static final String EXTRA_CYCLE_ID = "cycle_id";
    public static final String EXTRA_WIFI_STATE = "wifi_state";
    public static final String EXTRA_BLUETOOTH_STATE = "bluetooth_state";
    public static final String EXTRA_PHASE = "phase";
    public static final String EXTRA_WIFI_MANAGED = "wifi_managed";
    public static final String EXTRA_WIFI_PREVIOUS = "wifi_previous";
    public static final String EXTRA_WIFI_CHANGED = "wifi_changed";
    public static final String EXTRA_WIFI_ATTEMPTED = "wifi_attempted";
    public static final String EXTRA_WIFI_ACTION = "wifi_action";
    public static final String EXTRA_WIFI_TOGGLE_SUCCESS = "wifi_toggle_success";
    public static final String EXTRA_AIRPLANE_MODE = "airplane_mode";
    public static final String EXTRA_BLUETOOTH_MANAGED = "bluetooth_managed";
    public static final String EXTRA_BLUETOOTH_PREVIOUS = "bluetooth_previous";
    public static final String EXTRA_BLUETOOTH_CHANGED = "bluetooth_changed";
    public static final String EXTRA_RESTORE_SUCCESS = "restore_success";
    public static final String EXTRA_STATUS = "status";

    public static final String STATUS_OK = "OK";
    public static final String STATUS_ALREADY_SLEEPING = "ALREADY_SLEEPING";
    public static final String STATUS_ALREADY_RESTORED = "ALREADY_RESTORED";
    public static final String STATUS_NO_ACTIVE_CYCLE = "NO_ACTIVE_CYCLE";
    public static final String STATUS_RESTORE_FAILED = "RESTORE_FAILED";
    public static final String STATUS_WIFI_TOGGLE_FAILED = "WIFI_TOGGLE_FAILED";
    public static final String STATUS_CYCLE_MISMATCH = "CYCLE_MISMATCH";

    public static final String PHASE_SLEEP = "sleep";
    public static final String PHASE_WAKE = "wake";
    public static final String PHASE_MAINTENANCE_WIFI = "maintenance_wifi";
}
