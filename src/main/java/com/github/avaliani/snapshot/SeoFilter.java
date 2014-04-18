package com.github.avaliani.snapshot;


import com.google.common.base.Predicate;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import org.apache.commons.lang.StringUtils;
import org.apache.http.*;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.HeaderGroup;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.Closeable;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.Enumeration;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Map;
import java.util.regex.Pattern;

import static com.google.common.collect.FluentIterable.from;

/**
 * Conditionally returns snapshotted versions of requested pages to enable
 * search engines / bots to parse pages that primarily have javascript content.
 *
 * @author greengerong
 * @author avaliani
 *
 */
public class SeoFilter implements Filter {

    private static final Logger log = Logger.getLogger(SeoFilter.class.getName());

    private SnapshotService snapshotService;
    private SeoFilterEventHandler seoFilterEventHandler;
    private SeoFilterConfig seoFilterConfig;
    private Level logLevel;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        seoFilterConfig = new SeoFilterConfig(filterConfig);
        logLevel = seoFilterConfig.getLoggingLevel();
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
            throws IOException, ServletException {
        try {
            final HttpServletRequest request = (HttpServletRequest) servletRequest;
            final HttpServletResponse response = (HttpServletResponse) servletResponse;
            snapshotService =
                    seoFilterConfig.getSnapshotService(request);
            if (shouldShowPageSnapshot(request)) {
                seoFilterEventHandler = seoFilterConfig.getEventHandler();
                if (beforeSnapshot(request, response) || snapshot(request, response)) {
                    return;
                }
            }
        } catch (Exception e) {
            log.log(Level.WARNING, "Snapshot service error", e);
        }
        filterChain.doFilter(servletRequest, servletResponse);
    }

    private boolean beforeSnapshot(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (seoFilterEventHandler != null) {
            SnapshotResult snapshotResult = seoFilterEventHandler.beforeSnapshot(request);
            if (snapshotResult != null) {
                copyResponse(response, snapshotResult);
                return true;
            }
        }
        return false;
    }

    private boolean snapshot(HttpServletRequest request, HttpServletResponse response)
            throws IOException, MalformedURLException, URISyntaxException {
        SnapshotResult result = snapshotService.snapshot(
                getFullUrl(request),
                getRequestHeaders(request));
        if (result != null) {
            copyResponse(response, result);
            afterSnapshot(request, result);
            return true;
        } else {
            return false;
        }
    }

    private void afterSnapshot(HttpServletRequest request, SnapshotResult snapshotResult) {
        if (seoFilterEventHandler != null) {
            seoFilterEventHandler.afterSnapshot(request, snapshotResult);
        }
    }

    protected void closeQuietly(Closeable closeable) {
        try {
            if (closeable != null) {
                closeable.close();
            }
        } catch (IOException e) {
            log.log(Level.WARNING, "Close proxy error", e);
        }
    }

    /**
     * These are the "hop-by-hop" headers that should not be copied.
     * http://www.w3.org/Protocols/rfc2616/rfc2616-sec13.html
     * I use an HttpClient HeaderGroup class instead of Set<String> because this
     * approach does case insensitive lookup faster.
     */
    protected static final HeaderGroup hopByHopHeaders;

    static {
        hopByHopHeaders = new HeaderGroup();
        String[] headers = new String[]{
                "Connection", "Keep-Alive", "Proxy-Authenticate", "Proxy-Authorization",
                "TE", "Trailers", "Transfer-Encoding", "Upgrade"};
        for (String header : headers) {
            hopByHopHeaders.addHeader(new BasicHeader(header, null));
        }
    }

    /**
     * Get the request headers from the servlet request.
     *
     * @throws URISyntaxException
     */
    private Map<String, List<String>> getRequestHeaders(HttpServletRequest servletRequest) throws URISyntaxException {
        Map<String, List<String>> result = Maps.newHashMap();

        // Get an Enumeration of all of the header names sent by the client
        Enumeration<?> enumerationOfHeaderNames = servletRequest.getHeaderNames();
        while (enumerationOfHeaderNames.hasMoreElements()) {
            String headerName = (String) enumerationOfHeaderNames.nextElement();
            // Content-length is effectively set via InputStreamEntity
            // Host name is automatically set.
            if (  !headerName.equalsIgnoreCase(HttpHeaders.CONTENT_LENGTH) &&
                  !headerName.equalsIgnoreCase(HttpHeaders.HOST) &&
                  !hopByHopHeaders.containsHeader(headerName)) {
                List<String> headerValues =
                        Lists.newArrayList();
                Enumeration<?> headerValueEnum =
                        servletRequest.getHeaders(headerName);
                while (headerValueEnum.hasMoreElements()) {//sometimes more than one value
                    String headerValue = (String) headerValueEnum.nextElement();
                    headerValues.add(headerValue);
                }
                result.put(headerName, headerValues);
            }
        }

        return result;
    }

    protected void copyResponse(HttpServletResponse servletResponse, SnapshotResult result) throws IOException {
        for (Map.Entry<String, List<String>> headerEntry : result.getResponseHeaders().entrySet()) {
            if (!hopByHopHeaders.containsHeader(headerEntry.getKey())) {
                for (String headerValue : headerEntry.getValue()) {
                    servletResponse.addHeader(headerEntry.getKey(), headerValue);
                }
            }
        }

        PrintWriter printWriter = servletResponse.getWriter();
        try {
            printWriter.write(result.getSnapshot());
            printWriter.flush();
        } finally {
            closeQuietly(printWriter);
        }
    }

    private String getUrl(HttpServletRequest request) {
        // The local port option is to work around an issue in the App Engine dev server env
        // where the incoming serverPort and request url would not show the actual port being
        // listened on and would instead show the default port for the request url's scheme.
        // This fix does not work on production app engine since on production getLocalPort
        // returns zero. But luckily in production we use the default http / https ports.
        if ( seoFilterConfig.forwardRequestsUsingLocalPort() &&
             (request.getLocalPort() != 0) ) {
            int localPort = request.getLocalPort();
            String scheme = request.getScheme();
            String url = scheme + "://" + request.getServerName();
            if ((scheme.equals("http") && (localPort != 80)) ||
                (scheme.equals("https") && (localPort != 443))) {
                url += ":" + localPort;
            }
            return url + request.getRequestURI();
        } else {
            return request.getRequestURL().toString();
        }
    }

    private String getFullUrl(HttpServletRequest request) {
        final StringBuilder url = new StringBuilder(getUrl(request));
        final String queryString = request.getQueryString();
        if (queryString != null) {
            url.append('?');
            url.append(queryString);
        }
        return url.toString();
    }

    @Override
    public void destroy() {
        snapshotService = null;
        seoFilterConfig = null;
        logLevel = null;
        if (seoFilterEventHandler != null) {
            seoFilterEventHandler.destroy();
            seoFilterEventHandler = null;
        }
    }

    private boolean shouldShowPageSnapshot(HttpServletRequest request) throws URISyntaxException {
        final String userAgent = request.getHeader("User-Agent");
        final String url = getUrl(request);
        final String referer = request.getHeader("Referer");

        log.log(logLevel, "checking request " + getFullUrl(request) +
                " (serverPort: " + request.getServerPort() +
                ", localPort: " + request.getLocalPort() +")" +
                " from User-Agent " + userAgent + " and referer " + referer);

        if (snapshotService.isSnapshotRequest(request)) {
            log.log(logLevel, "Request is a snapshot request; intercept: no");
            return false;
        }

        if (!HttpGet.METHOD_NAME.equals(request.getMethod())) {
            log.log(logLevel, "Request is not HTTP GET; intercept: no");
            return false;
        }

        if (isInResources(url)) {
            log.log(logLevel, "Request is for a (static) resource; intercept: no");
            return false;
        }

        final List<String> whiteList = seoFilterConfig.getWhitelist();
        if (whiteList != null && !isInWhiteList(url, whiteList)) {
            log.log(logLevel, "Whitelist is enabled, but this request is not listed; intercept: no");
            return false;
        }

        final List<String> blacklist = seoFilterConfig.getBlacklist();
        if (blacklist != null && isInBlackList(url, referer, blacklist)) {
            log.log(logLevel, "Blacklist is enabled, and this request is listed; intercept: no");
            return false;
        }

        if (hasEscapedFragment(request)) {
            log.log(logLevel, "Request Has _escaped_fragment_; intercept: yes");
            return true;
        }

        if (StringUtils.isBlank(userAgent)) {
            log.log(logLevel, "Request has blank userAgent; intercept: no");
            return false;
        }

        if (!isInSearchUserAgent(userAgent)) {
            log.log(logLevel, "Request User-Agent is not a search bot; intercept: no");
            return false;
        }

        log.log(logLevel, String.format("Defaulting to request intercept(user-agent=%s): yes", userAgent));
        return true;
    }

    private boolean hasEscapedFragment(HttpServletRequest request) {
        return request.getParameterMap().containsKey("_escaped_fragment_");
    }

    private boolean isInBlackList(final String url, final String referer, List<String> blacklist) {
        return from(blacklist).anyMatch(new Predicate<String>() {
            @Override
            public boolean apply(String regex) {
                final Pattern pattern = Pattern.compile(regex);
                return pattern.matcher(url).matches() ||
                        (!StringUtils.isBlank(referer) && pattern.matcher(referer).matches());
            }
        });
    }

    private boolean isInWhiteList(final String url, List<String> whitelist) {
        return from(whitelist).anyMatch(new Predicate<String>() {
            @Override
            public boolean apply(String regex) {
                return Pattern.compile(regex).matcher(url).matches();
            }
        });
    }

    private boolean isInResources(final String url) {
        return from(seoFilterConfig.getExtensionsToIgnore()).anyMatch(new Predicate<String>() {
            @Override
            public boolean apply(String item) {
                return url.contains(item.toLowerCase());
            }
        });
    }

    private boolean isInSearchUserAgent(final String userAgent) {
        return from(seoFilterConfig.getCrawlerUserAgents()).anyMatch(new Predicate<String>() {
            @Override
            public boolean apply(String item) {
                return userAgent.toLowerCase().indexOf(item.toLowerCase()) >= 0;
            }
        });
    }
}
