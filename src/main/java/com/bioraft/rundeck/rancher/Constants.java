package com.bioraft.rundeck.rancher;

public class Constants {
    public Constants() {
        throw new IllegalStateException("Utility class");
    }

    public static final int INTERVAL_MILLIS = 2000;

    public static final String STATE = "state";
    public static final String STATE_ACTIVE = "active";
    public static final String STATE_UPGRADED = "upgraded";

    public static final String LAUNCH_CONFIG = "launchConfig";
    public static final String START_FIRST = "startFirst";
    public static final String LINKS = "links";

    public static final String JSON = "json";
}
