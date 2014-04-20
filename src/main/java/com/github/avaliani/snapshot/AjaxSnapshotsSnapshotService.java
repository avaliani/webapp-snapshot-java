package com.github.avaliani.snapshot;

import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * Web page snapshotting service by https://ajaxsnapshots.com/
 *
 * @author avaliani
 *
 */
public class AjaxSnapshotsSnapshotService extends BaseSnapshotService {

    // TODO(avaliani): debugging therefore using http
    public static final String DEFAULT_SERVICE_URL = "http://api.ajaxsnapshots.com/makeSnapshot";

    @Override
    public String getDefaultServiceUrl() {
        return DEFAULT_SERVICE_URL;
    }

    @Override
    public String getRequestUrl(String requestUrl) {
        String baseUrl = getServiceUrl();
        baseUrl += "?url=" + encodeURIComponent(requestUrl);
        return baseUrl;
    }

    @Override
    public Map<String, List<String>> getRequestHeaders(String requestUrl) {
        Map<String, List<String>> headers = Maps.newHashMap();

        String serviceToken = config.getServiceToken();
        if (serviceToken != null) {
            headers.put("X-AJS-APIKEY", Lists.newArrayList(serviceToken));
        }

        return headers;
    }

    @Override
    public boolean isSnapshotRequest(HttpServletRequest request) {
        return request.getHeader("X-AJS-CALLTYPE") != null;
    }
}