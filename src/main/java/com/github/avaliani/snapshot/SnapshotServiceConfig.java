package com.github.avaliani.snapshot;

import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import javax.annotation.Nullable;

/**
 * Provides configuration for the snapshot service.
 *
 * @author avaliani
 *
 */
public interface SnapshotServiceConfig {

    /**
     * @return the token used to identify the app with the snapshotting service
     *     or null to use no token.
     */
    @Nullable
    String getServiceToken();

    /**
     * @return the snapshot service URL or null to use the default
     *     URL for the service.
     */
    @Nullable
    String getServiceUrl();

    /**
     * @return the scheme to use when making a snapshot service request:
     *     "http" or "https".
     */
    String getRequestScheme();

    /**
     * @return the headers to use when making a snapshot service request.
     */
    Map<String, List<String>> getRequestHeaders();

    /**
     * @return the level at which all debug logs should be written.
     */
    Level getLoggingLevel();
}