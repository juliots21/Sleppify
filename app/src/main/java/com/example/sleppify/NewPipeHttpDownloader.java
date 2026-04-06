package com.example.sleppify;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class NewPipeHttpDownloader extends Downloader {

    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int READ_TIMEOUT_MS = 30000;
    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36";

    private static final NewPipeHttpDownloader INSTANCE = new NewPipeHttpDownloader();

    private NewPipeHttpDownloader() {
    }

    @NonNull
    public static NewPipeHttpDownloader getInstance() {
        return INSTANCE;
    }

    @Override
    public Response execute(@NonNull Request request) throws IOException, ReCaptchaException {
        HttpURLConnection connection = null;

        try {
            connection = openConnection(request);
            int statusCode = connection.getResponseCode();
            if (statusCode == 429) {
                throw new ReCaptchaException("Too many requests", request.url());
            }

            String body = readBody(connection, statusCode);
            String statusMessage = connection.getResponseMessage();
            String latestUrl = connection.getURL() == null
                    ? request.url()
                    : connection.getURL().toString();

            return new Response(
                    statusCode,
                    statusMessage == null ? "" : statusMessage,
                    sanitizeHeaders(connection.getHeaderFields()),
                    body,
                    latestUrl
            );
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    @NonNull
    private HttpURLConnection openConnection(@NonNull Request request) throws IOException {
        URL url = new URL(request.url());
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod(request.httpMethod());
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setUseCaches(false);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", USER_AGENT);

        for (Map.Entry<String, List<String>> entry : request.headers().entrySet()) {
            String name = entry.getKey();
            List<String> values = entry.getValue();
            if (name == null || values == null || values.isEmpty()) {
                continue;
            }

            boolean first = true;
            for (String value : values) {
                if (value == null) {
                    continue;
                }
                if (first) {
                    connection.setRequestProperty(name, value);
                    first = false;
                } else {
                    connection.addRequestProperty(name, value);
                }
            }
        }

        byte[] dataToSend = request.dataToSend();
        if (dataToSend != null && dataToSend.length > 0) {
            connection.setDoOutput(true);
            try (OutputStream outputStream = connection.getOutputStream()) {
                outputStream.write(dataToSend);
                outputStream.flush();
            }
        } else if (requiresRequestBody(request.httpMethod())) {
            connection.setDoOutput(true);
            try (OutputStream outputStream = connection.getOutputStream()) {
                outputStream.write(new byte[0]);
                outputStream.flush();
            }
        }

        return connection;
    }

    private boolean requiresRequestBody(@Nullable String method) {
        if (method == null) {
            return false;
        }

        return "POST".equalsIgnoreCase(method)
                || "PUT".equalsIgnoreCase(method)
                || "PATCH".equalsIgnoreCase(method);
    }

    @NonNull
    private String readBody(@NonNull HttpURLConnection connection, int statusCode) throws IOException {
        java.io.InputStream raw = statusCode >= 400
                ? connection.getErrorStream()
                : connection.getInputStream();

        if (raw == null) {
            return "";
        }

        try (BufferedInputStream in = new BufferedInputStream(raw);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }

    @NonNull
    private Map<String, List<String>> sanitizeHeaders(@Nullable Map<String, List<String>> headers) {
        if (headers == null || headers.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, List<String>> result = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            result.put(entry.getKey(), entry.getValue());
        }
        return result;
    }
}
