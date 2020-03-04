package com.bioraft.rundeck.rancher;

public class Constants {
    private Constants() {
        throw new IllegalStateException("Utility class");
    }

    public static final int INTERVAL_MILLIS = 2000;

    public static final String STATE = "state";
    public static final String STATE_ACTIVE = "active";
    public static final String STATE_UPGRADED = "upgraded";

    public static final String LAUNCH_CONFIG = "launchConfig";
    public static final String START_FIRST = "startFirst";
    public static final String LINKS = "links";

    public static final String DISPLAY_CODE = "CODE";
    public static final String SYNTAX_MODE_JSON = "json";

    public static final String OPT_DATA_VOLUMES = "dataVolumes";
    public static final String OPT_ENV_VARS = "environment";
    public static final String OPT_ENV_IDS = "environmentId";
    public static final String OPT_IMAGE_UUID = "imageUuid";
    public static final String OPT_LABELS = "labels";
    public static final String OPT_SECRETS = "secrets";
    public static final String OPT_SERVICE_NAME = "serviceName";
    public static final String OPT_STACK_NAME = "stackName";

    public static final String OPT_EXCLUDE = "Exclude";
    public static final String OPT_INCLUDE = "Include";
}
