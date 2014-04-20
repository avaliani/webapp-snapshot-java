package com.github.avaliani.snapshot;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.IOUtils;

public abstract class BaseSnapshotService implements SnapshotService {

    private static final Logger log = Logger.getLogger(BaseSnapshotService.class.getName());

    protected SnapshotServiceConfig config;

    protected Level logLevel;

    @Override
    public final void init(SnapshotServiceConfig config) {
        this.config = config;
        logLevel = config.getLoggingLevel();
    }

    public final String getServiceUrl() {
        String configUrl = config.getServiceUrl();
        String serviceUrl = (configUrl == null) ? getDefaultServiceUrl() : configUrl;
        return config.getRequestScheme() + "://" + stripUrlScheme(serviceUrl);
    }

    private static String stripUrlScheme(String url) {
        if (url.toLowerCase().startsWith("http://")) {
            return url.substring("http://".length());
        } else if (url.toLowerCase().startsWith("https://")) {
            return url.substring("https://".length());
        } else {
            return url;
        }
    }

    public abstract String getDefaultServiceUrl();

    public abstract String getRequestUrl(String requestUrl);

    public abstract Map<String, List<String>> getRequestHeaders(String requestUrl);

    @Override
    public final SnapshotResult snapshot(String urlToSnapshot, Map<String, List<String>> headers)
            throws IOException {
        log.log(logLevel, "About to snapshot requested url: " + urlToSnapshot);

        final String apiUrl = getRequestUrl(urlToSnapshot);

        URL url = new URL(apiUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        copyRequestHeaders(connection, headers);
        copyRequestHeaders(connection, getRequestHeaders(urlToSnapshot));
        copyRequestHeaders(connection, config.getRequestHeaders());
        connection.setReadTimeout(60 * 1000);

        dump(connection);

        if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
            dumpResponse(connection, "SUCCESS: snapshotting was successful", false);

            return new SnapshotResult(getResponse(connection), getResponseHeaders(connection));
        } else {
            dumpResponse(connection, "ERROR: snapshotting failed", true);
            return null;
        }

    }

    private static void copyRequestHeaders(HttpURLConnection connection, Map<String, List<String>> headers) {
        for (Map.Entry<String, List<String>> header : headers.entrySet()) {
            for (String headerValue : header.getValue()) {
                connection.addRequestProperty(header.getKey(), headerValue);
            }
        }
    }

    /**
     * Copy proxied response headers back to the servlet client.
     */
    private static Map<String, List<String>> getResponseHeaders(HttpURLConnection connection) {
        return connection.getHeaderFields();
    }

    /**
     * Copy response from the proxy to the servlet client.
     */
    private static String getResponse(HttpURLConnection connection) throws IOException {
        StringWriter respWriter = new StringWriter();
        IOUtils.copy(connection.getInputStream(), respWriter);
        return respWriter.toString();
    }


    private void dump(HttpURLConnection connection) {
        if (log.isLoggable(logLevel)) {
            StringBuilder output = new StringBuilder();
            output.append("  GET " + connection.getURL() + "\n");
            Map<String,List<String>> headers = connection.getRequestProperties();
            for (Map.Entry<String, List<String>> header : headers.entrySet()) {
                output.append("    " + header.getKey() + " : " + mergeHeaderValues(header.getValue()) + "\n");
            }
            log.log(logLevel, output.toString());
        }
    }

    private void dumpResponse(HttpURLConnection connection, String outputHeader,
            boolean dumpContent) throws IOException {
        if (log.isLoggable(logLevel)) {
            StringBuilder output = new StringBuilder();
            output.append(outputHeader + "\n");
            output.append("  RESPONSE " + connection.getResponseCode() + " " +
                    connection.getResponseMessage() + "\n");
            Map<String,List<String>> headers = connection.getHeaderFields();
            for (Map.Entry<String, List<String>> header : headers.entrySet()) {
                output.append("    " + header.getKey() + " : " +
                        mergeHeaderValues(header.getValue()) + "\n");
            }
            if (dumpContent) {
                output.append(">>>>> CONTENT START >>>>\n");
                StringWriter respWriter = new StringWriter();
                IOUtils.copy(connection.getInputStream(), respWriter);
                output.append(respWriter.toString());
                output.append("\n>>>>> CONTENT END >>>>\n");
            }
            log.log(logLevel, output.toString());
        }
    }

    private static String mergeHeaderValues(List<String> headerValues) {
        StringBuilder mergedValue = new StringBuilder();
        for (String headerValue : headerValues) {
            if (mergedValue.length() > 0) {
                mergedValue.append(",");
            }
            mergedValue.append(headerValue);
        }
        return mergedValue.toString();
    }

    // TODO(avaliani): move to a utility class
    public static String encodeURIComponent(String s) {
        String result;
        try {
            result = URLEncoder.encode(s, "UTF-8")
                    .replaceAll("\\+", "%20")
                    .replaceAll("\\%21", "!")
                    .replaceAll("\\%27", "'")
                    .replaceAll("\\%28", "(")
                    .replaceAll("\\%29", ")")
                    .replaceAll("\\%7E", "~");
        } catch (UnsupportedEncodingException e) {
            result = s;
        }
        return result;
    }
}