package com.github.avaliani.snapshot;


import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import org.apache.commons.lang.StringUtils;

import javax.annotation.Nullable;
import javax.servlet.FilterConfig;
import javax.servlet.http.HttpServletRequest;

import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.Map;

public class SeoFilterConfig {
    private static final Level DEFAULT_LOGGING_LEVEL = Level.FINE;

    private FilterConfig filterConfig;

    public SeoFilterConfig(FilterConfig filterConfig) {
        this.filterConfig = filterConfig;
    }

    public SnapshotService getSnapshotService(HttpServletRequest request) {
        SnapshotService snapshotService;
        final String snapshotServiceClass =
                filterConfig.getInitParameter("snapshotService");
        if (StringUtils.isBlank(snapshotServiceClass)) {
            // Default to the ajaxsnapshots service for now since that's working for us.
            snapshotService = new AjaxSnapshotsSnapshotService();
        } else {
            try {
                snapshotService =
                        (SnapshotService) Class.forName(snapshotServiceClass).newInstance();

            } catch (Exception e) {
                throw new RuntimeException("Unable to load SnapshotService class", e);
            }
        }
        snapshotService.init(new SnapshotServiceConfigImpl(request));
        return snapshotService;
    }

    @Nullable
    public SeoFilterEventHandler getEventHandler() {
        final String seoFilterEventHandler = filterConfig.getInitParameter("seoFilterEventHandler");
        if (StringUtils.isNotBlank(seoFilterEventHandler)) {
            try {
                return (SeoFilterEventHandler) Class.forName(seoFilterEventHandler).newInstance();
            } catch (Exception e) {
                throw new RuntimeException("SeoFilterEventHandler class not found", e);
            }
        }
        return null;
    }

    public List<String> getCrawlerUserAgents() {
        List<String> crawlerUserAgents = Lists.newArrayList("googlebot", "yahoo", "bingbot", "baiduspider",
                "facebookexternalhit", "twitterbot", "rogerbot", "linkedinbot", "embedly");
        final String crawlerUserAgentsFromConfig = filterConfig.getInitParameter("crawlerUserAgents");
        if (StringUtils.isNotBlank(crawlerUserAgentsFromConfig)) {
            crawlerUserAgents.addAll(Arrays.asList(crawlerUserAgentsFromConfig.trim().split(",")));
        }

        return crawlerUserAgents;
    }

    public List<String> getExtensionsToIgnore() {
        List<String> extensionsToIgnore = Lists.newArrayList(".js", ".css", ".less", ".png", ".jpg", ".jpeg",
                ".gif", ".pdf", ".doc", ".txt", ".zip", ".mp3", ".rar", ".exe", ".wmv", ".doc", ".avi", ".ppt", ".mpg",
                ".mpeg", ".tif", ".wav", ".mov", ".psd", ".ai", ".xls", ".mp4", ".m4a", ".swf", ".dat", ".dmg",
                ".iso", ".flv", ".m4v", ".torrent");
        final String extensionsToIgnoreFromConfig = filterConfig.getInitParameter("extensionsToIgnore");
        if (StringUtils.isNotBlank(extensionsToIgnoreFromConfig)) {
            extensionsToIgnore.addAll(Arrays.asList(extensionsToIgnoreFromConfig.trim().split(",")));
        }

        return extensionsToIgnore;
    }

    public List<String> getWhitelist() {
        final String whitelist = filterConfig.getInitParameter("whitelist");
        if (StringUtils.isNotBlank(whitelist)) {
            return Arrays.asList(whitelist.trim().split(","));
        }
        return null;
    }

    public List<String> getBlacklist() {
        final String blacklist = filterConfig.getInitParameter("blacklist");
        if (StringUtils.isNotBlank(blacklist)) {
            return Arrays.asList(blacklist.trim().split(","));
        }
        return null;
    }

    public Level getLoggingLevel() {
        String loggingLevel = filterConfig.getInitParameter("loggingLevel");
        if (loggingLevel != null) {
            try {
                return Level.parse(loggingLevel);
            } catch (IllegalArgumentException e) {
                throw new RuntimeException("Unable to parse 'loggingLevel' parameter", e);
            }
        }
        return DEFAULT_LOGGING_LEVEL;
    }

    public boolean forwardRequestsUsingLocalPort() {
        String val = filterConfig.getInitParameter("forwardRequestsUsingLocalPort");
        if ((val != null)) {
            try {
                return Boolean.parseBoolean(val);
            } catch (IllegalArgumentException e) {
                throw new RuntimeException(
                        "Unable to parse 'forwardRequestsUsingLocalPort' parameter", e);
            }
        }
        return false;
    }

    private class SnapshotServiceConfigImpl implements SnapshotServiceConfig {
        private final String requestScheme;
        private SnapshotServiceTokenProvider serviceTokenProvider;
        private Map<String, String> options;

        public SnapshotServiceConfigImpl(HttpServletRequest request) {
            requestScheme = request.getScheme();
        }

        @Override
        @Nullable
        public String getServiceToken() {
            initServiceTokenProvider();
            return serviceTokenProvider.getServiceToken();
        }

        private void initServiceTokenProvider() {
            if (serviceTokenProvider == null) {
                final String snapshotServiceTokenProvider = filterConfig.getInitParameter("snapshotServiceTokenProvider");
                if (StringUtils.isNotBlank(snapshotServiceTokenProvider)) {
                    try {
                        serviceTokenProvider =
                                (SnapshotServiceTokenProvider) Class.forName(snapshotServiceTokenProvider).newInstance();
                    } catch (Exception e) {
                        throw new RuntimeException("Unable to load SnapshotServiceTokenProvider class", e);
                    }
                } else {
                    final String serviceToken =
                            filterConfig.getInitParameter("snapshotServiceToken");
                    serviceTokenProvider = new SnapshotServiceTokenProvider() {
                        @Override
                        public String getServiceToken() {
                            return serviceToken;
                        }
                    };
                }
            }
        }

        @Override
        @Nullable
        public String getServiceUrl() {
            return filterConfig.getInitParameter("snapshotServiceUrl");
        }

        @Override
        public String getRequestScheme() {
            return requestScheme;
        }

        @Override
        public Map<String, String> getOptions() {
            initOptions();
            return options;
        }

        private void initOptions() {
            if (options == null) {
                options = Maps.newHashMap();
                String optionsStr = filterConfig.getInitParameter("snapshotServiceOptions");
                if (optionsStr != null) {
                    String[] optionNameValuePairs = optionsStr.trim().split(",");
                    for (String optionNameValuePair : optionNameValuePairs) {
                        String[] parsedNameValuePair = optionNameValuePair.trim().split("=", 2);
                        String optionName = parsedNameValuePair[0].trim();
                        String optionValue = ((parsedNameValuePair.length == 2) ?
                                parsedNameValuePair[1].trim() : "");
                        if (StringUtils.isNotBlank(optionName)) {
                            options.put(optionName, optionValue);
                        }
                    }
                }
            }
        }

        @Override
        public Level getLoggingLevel() {
            return SeoFilterConfig.this.getLoggingLevel();
        }
    }
}
