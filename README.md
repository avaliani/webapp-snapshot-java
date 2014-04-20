Webapp Snapshot Java
===========================

This project provides infrastructure to leverage existing web page snapshotting service providers using Java. The web page snapshotting services snapshot the page after executing any Javascript to enable search engines / bots to parse the page (since bots / search engines don't execute javascript). This is helpful if you have a javascript web app (backbone, angular, emberjs, etc,).

There are two parts to this code

1. A filter that detects if a search-engine / bot is making a request and if so leverages the web page snapshotting service to return a response.
2. An api to explicitly snapshot your web pages.

The code is based upon https://github.com/greengerong/prerender-java. The ways it deviates from that project are:

* multiple web app snapshotting service support - built in support for prerender.io and ajaxsnapshots.com. Or you can add support for another service.
* open source project support - it has a token provider api for open source projects that don't want to put their snapshot service token in their web.xml.
* app engine support - switched from org.apache.httpcomponents to HttpURLConnection to avoid socket read exceptions: http://stackoverflow.com/questions/23103124/unable-to-adjust-socket-timeout-when-using-org-apache-httpcomponents-with-app-en
* supports explicit snapshotting vs. filter only snapshotting


`Note:` If you are using a `#` in your urls, make sure to change it to `#!`. [View Google's ajax crawling protocol](https://developers.google.com/webmasters/ajax-crawling/docs/getting-started)

`Note:` Make sure you have more than one webserver thread/process running because the snapshotting service will make a request to your server to render the HTML.

## Filter

#### How the filter works:

1. Check if a webpage snapshot is required
	1. Check if the request is from a crawler (`_escaped_fragment_` or agent string)
	2. Check to make sure we aren't requesting a resource (js, css, etc...)
	3. (optional) Check to make sure the url is in the whitelist
	4. (optional) Check to make sure the url isn't in the blacklist
2. If a snapshot is required
	1. (optional) Invoke *SeoFilterEventHandler.beforeSnapshot* to check if a snapshot is available. If so, use this as the snapshot and skip the remaining steps.
	2. Make a request to the snapshotting service to get a snapshot.
	3. (optional) Invoke *SeoFilterEventHandler.afterSnapshot* with the snapshot (for persistence / logging)
	4. return the snapshot result to the crawler


#### Installing the filter 

[1] Add the jar files++ to your project. Right now I'm using "mvn install" to install the jars locally. And then "mvn install:install-file" to add the jar files to my dependent maven project. (Let me know if there is a better way)

If requested I will try and put it on maven central.

[2] Modify your pom.xml

    <dependency>
      <groupId>com.github.avaliani.snapshot</groupId>
      <artifactId>webapp-snapshot-java</artifactId>
      <version>1.0</version>
    </dependency>

[3] Modify your web.xml (you will probably want to add this filter prior to all other filters)

    <filter>
        <filter-name>SeoFilter</filter-name>
        <filter-class>com.github.avaliani.snapshot.SeoFilter</filter-class>
        <init-param>
            <param-name>snapshotService</param-name>
            <param-value>com.github.avaliani.snapshot.AjaxSnapshotsSnapshotService</param-value>
        </init-param>
        <init-param>
            <param-name>snapshotServiceTokenProvider</param-name>
            <param-value>{your-token-provider-class-path}</param-value>
        </init-param>
    </filter>
    <filter-mapping>
        <filter-name>SeoFilter</filter-name>
        <url-pattern>/*</url-pattern>
    </filter-mapping>

#### Filter initialization parameters:

All parameters are optional. By default *AjaxSnapshotsSnapshotService* is used as the snapshotService.

*Snapshot service parameters:*

* **snapshotService** - the snapshotting service. Two built in services are available: (1) *com.github.avaliani.snapshot.AjaxSnapshotsSnapshotService* and (2) *com.github.avaliani.snapshot.PrerenderSnapshotService*. Or you can implement your own.
* **snapshotServiceHeaders** - headers to use when making a request to the snapshotting service. Specified as semi-colon seperated headerName and headerValue pairs, e.g. "X-AJS-SNAP-TIME=2000;{headerName2}={headerValue2}"
	* [AjaxSnapshotsSnapshotService headers documentation](https://ajaxsnapshots.com/apidocs)
* **snapshotServiceToken** - specifies the snapshot service token
* **snapshotServiceTokenProvider** - used if you want to generate your snapshot service token from a class and not from web.xml. The class must implement *com.github.avaliani.snapshot.SnapshotServiceTokenProvider*
* **snapshotServiceUrl** - used to specify an explicit url for the snapshotting service. If not specified the default url for the snapshotting service will be used.

*Request selection parameters:*

* **crawlerUserAgents** - additional user agents to check for
* **whitelist** - if set and the request url is not in the whitelist it is not snapshotted
* **blacklist** - if set and the request url is in the blacklist it is not snapshotted

*Other parameters:*

* **loggingLevel** - java logging Level at which to write logs. Default logging level is FINE.
* **seoFilterEventHandler** - event handler to be invoked before and after taking snapshots.

## Snapshot API

See *com.github.avaliani.snapshot.SnapshotService* for the API. Two built in services are available: (1) *com.github.avaliani.snapshot.AjaxSnapshotsSnapshotService* and (2) *com.github.avaliani.snapshot.PrerenderSnapshotService*. 


## Testing

If you want to make sure your pages are rendering correctly:

1. Open the Developer Tools in Chrome (Cmd + Atl + J)
2. Click the Settings gear in the bottom right corner.
3. Click "Overrides" on the left side of the settings panel.
4. Check the "User Agent" checkbox.
6. Choose "Other..." from the User Agent dropdown.
7. Type `googlebot` into the input box.
8. Refresh the page (make sure to keep the developer tools open).

## License

The MIT License (MIT)
