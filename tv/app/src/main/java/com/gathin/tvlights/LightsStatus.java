package com.gathin.tvlights;

import java.util.Collections;
import java.util.List;

/** What the screen shows of the service: whether the lights answer, and each one's live state. Written by the service's threads, read by the screen. */
final class LightsStatus {
    static final int STOPPED = 0;
    /** lights.json is missing or not usable, see problem. */
    static final int NO_CONFIG = 1;
    static final int CONNECTING = 2;
    static final int CONNECTED = 3;
    /** The light doesn't answer; the service keeps trying, with growing pauses. */
    static final int UNREACHABLE = 4;

    /** All the lights together: connected as soon as one answers. */
    static volatile int link = STOPPED;
    /** With NO_CONFIG: missing, unreadable, incomplete or zones. */
    static volatile String problem = "";
    static volatile List<LightUnit> units = Collections.emptyList();

    private LightsStatus() {
    }
}
