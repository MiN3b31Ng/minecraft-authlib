package com.mojang.authlib;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Map;
import java.util.Map.Entry;
import org.apache.commons.io.Charsets;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.Validate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public abstract class HttpAuthenticationService extends BaseAuthenticationService
{
    private static final Logger LOGGER = LogManager.getLogger();
    private final Proxy proxy;

    protected HttpAuthenticationService(Proxy proxy)
    {
        Validate.notNull(proxy);
        this.proxy = proxy;
    }

    public Proxy getProxy()
    {
        return this.proxy;
    }

    protected HttpURLConnection createUrlConnection(URL url) throws IOException {
        Validate.notNull(url);
        LOGGER.debug(new StringBuilder().append("Opening connection to ").append(url).toString());
        HttpURLConnection connection = (HttpURLConnection)url.openConnection(this.proxy);
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(15000);
        connection.setUseCaches(false);
        return connection;
    }

    public String performPostRequest(URL url, String post, String contentType)
            throws IOException
    {
        Validate.notNull(url);
        Validate.notNull(post);
        Validate.notNull(contentType);

        String newPost = "data=" + post;
        HttpURLConnection connection = createUrlConnection(url);
        byte[] postAsBytes = newPost.getBytes(Charsets.UTF_8);

        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
//        connection.setRequestProperty("Content-Type", new StringBuilder().append(contentType).append("; charset=").append(Charsets.UTF_8).toString());
        connection.setRequestProperty("Content-Length", new StringBuilder().append("").append(postAsBytes.length).toString());
        connection.setDoOutput(true);
        connection.setRequestMethod("POST");

        LOGGER.info(new StringBuilder().append("Writing POST data to ").append(url).append(": ").append(post).toString());

        OutputStream outputStream = null;
        try {
            outputStream = connection.getOutputStream();
            IOUtils.write(postAsBytes, outputStream);
        } finally {
            IOUtils.closeQuietly(outputStream);
        }

        LOGGER.info(new StringBuilder().append("Reading data from ").append(url).toString());

        InputStream inputStream = null;
        try {
            inputStream = connection.getInputStream();
            String result = IOUtils.toString(inputStream, Charsets.UTF_8);
            LOGGER.info(new StringBuilder().append("Successful read, server response was ").append(connection.getResponseCode()).toString());
            LOGGER.info(new StringBuilder().append("Response: ").append(result).toString());
            return result;
        } catch (IOException e) {
            IOUtils.closeQuietly(inputStream);
            inputStream = connection.getErrorStream();

            if (inputStream != null) {
                LOGGER.info(new StringBuilder().append("Reading error page from ").append(url).toString());
                String result = IOUtils.toString(inputStream, Charsets.UTF_8);
                LOGGER.info(new StringBuilder().append("Successful read, server response was ").append(connection.getResponseCode()).toString());
                LOGGER.info(new StringBuilder().append("Response: ").append(result).toString());
                return result;
            }
            LOGGER.info("Request failed", e);
            throw e;
        }
        finally {
            IOUtils.closeQuietly(inputStream);
        }
    }

    public String performGetRequest(URL url)
            throws IOException
    {
        Validate.notNull(url);
        HttpURLConnection connection = createUrlConnection(url);

        LOGGER.debug(new StringBuilder().append("Reading data from ").append(url).toString());

        InputStream inputStream = null;
        try {
            inputStream = connection.getInputStream();
            String result = IOUtils.toString(inputStream, Charsets.UTF_8);
            LOGGER.debug(new StringBuilder().append("Successful read, server response was ").append(connection.getResponseCode()).toString());
            LOGGER.debug(new StringBuilder().append("Response: ").append(result).toString());
            return result;
        } catch (IOException e) {
            IOUtils.closeQuietly(inputStream);
            inputStream = connection.getErrorStream();

            if (inputStream != null) {
                LOGGER.debug(new StringBuilder().append("Reading error page from ").append(url).toString());
                String result = IOUtils.toString(inputStream, Charsets.UTF_8);
                LOGGER.debug(new StringBuilder().append("Successful read, server response was ").append(connection.getResponseCode()).toString());
                LOGGER.debug(new StringBuilder().append("Response: ").append(result).toString());
                return result;
            }
            LOGGER.debug("Request failed", e);
            throw e;
        }
        finally {
            IOUtils.closeQuietly(inputStream);
        }
    }

    public static URL constantURL(String url)
    {
        try
        {
            return new URL(url);
        } catch (MalformedURLException ex) {
            throw new Error(new StringBuilder().append("Couldn't create constant for ").append(url).toString(), ex);
        }
    }

    public static String buildQuery(Map<String, Object> query)
    {
        if (query == null) return "";
        StringBuilder builder = new StringBuilder();

        for (Map.Entry entry : query.entrySet()) {
            if (builder.length() > 0) {
                builder.append('&');
            }
            try
            {
                builder.append(URLEncoder.encode((String)entry.getKey(), "UTF-8"));
            } catch (UnsupportedEncodingException e) {
                LOGGER.error("Unexpected exception building query", e);
            }

            if (entry.getValue() != null) {
                builder.append('=');
                try {
                    builder.append(URLEncoder.encode(entry.getValue().toString(), "UTF-8"));
                } catch (UnsupportedEncodingException e) {
                    LOGGER.error("Unexpected exception building query", e);
                }
            }
        }

        return builder.toString();
    }

    public static URL concatenateURL(URL url, String query)
    {
        try
        {
            if ((url.getQuery() != null) && (url.getQuery().length() > 0)) {
                return new URL(url.getProtocol(), url.getHost(), url.getPort(), new StringBuilder().append(url.getFile()).append("&").append(query).toString());
            }
            return new URL(url.getProtocol(), url.getHost(), url.getPort(), new StringBuilder().append(url.getFile()).append("?").append(query).toString());
        }
        catch (MalformedURLException ex) {
            throw new IllegalArgumentException("Could not concatenate given URL with GET arguments!", ex);
        }
    }
}