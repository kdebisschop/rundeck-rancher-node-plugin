package com.bioraft.rundeck.rancher;

public class Constants {
    private Constants() {
        throw new IllegalStateException("Utility class");
    }

    public static final int INTERVAL_MILLIS = 2000;

    // These are fields in JSON from Rancher API.
    public static final String NODE_ID = "id";
    public static final String NODE_NAME = "name";
    public static final String NODE_ACCOUNT_ID = "accountId";
    public static final String NODE_STATE = "state";
    public static final String NODE_IMAGE_UUID = "imageUuid";

    // These are nodeEntry attributes we define and set.
    public static final String NODE_ATT_ID = "id";
    public static final String NODE_ATT_EXTERNAL_ID = "externalId";
    public static final String NODE_ATT_FILE_COPIER = "file-copier";
    public static final String NODE_ATT_NODE_EXECUTOR = "node-executor";
    public static final String NODE_ATT_TYPE = "type";
    public static final String NODE_ATT_STATE = "state";
    public static final String NODE_ATT_ACCOUNT = "account";
    public static final String NODE_ATT_ENVIRONMENT = "environment";
    public static final String NODE_ATT_IMAGE = "image";

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
