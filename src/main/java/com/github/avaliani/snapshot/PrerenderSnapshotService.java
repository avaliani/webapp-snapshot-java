package com.github.avaliani.snapshot;

import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * Web page snapshotting service by https://prerender.io
 *
 * @author avaliani
 *
 */
public class PrerenderSnapshotService extends BaseSnapshotService {

    /*
     * The current implementation uses the cached version of the snapshotting api. Need
     * to also support the explicit version: https://prerender.io/getting-started#api-recache.
     */

    public static final String DEFAULT_SERVICE_URL = "http://service.prerender.io/";

    @Override
    protected String getDefaultServiceUrl() {
        return DEFAULT_SERVICE_URL;
    }

    @Override
    protected String getRequestUrl(String requestUrl) {
        String prerenderServiceUrl = getServiceUrl();
        if (!prerenderServiceUrl.endsWith("/")) {
            prerenderServiceUrl += "/";
        }
        return prerenderServiceUrl + requestUrl;
    }

    @Override
    protected Map<String, List<String>> getRequestHeaders(String requestUrl) {
        Map<String, List<String>> headers = Maps.newHashMap();

        String serviceToken = config.getServiceToken();
        if (serviceToken != null) {
            headers.put("X-Prerender-Token", Lists.newArrayList(serviceToken));
        }

        return headers;
    }

    @Override
    public boolean isSnapshotRequest(HttpServletRequest request) {
        // The pre-render service uses a non-bot user agent when obtaining
        // a snapshot.
        return false;
    }
}