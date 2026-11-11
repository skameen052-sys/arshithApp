
package com.example.arshithconnectattendance;

import android.os.Handler;
import android.os.Looper;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChatApiClient {

    // Change this to your computer's current Wi-Fi IPv4 address.
    private static final String BASE_URL =
            "http://10.219.240.188:8000";
    private static final ExecutorService executor =
            Executors.newFixedThreadPool(4);

    private static final Handler mainHandler =
            new Handler(Looper.getMainLooper());

    public interface ApiCallback {
        void onSuccess(String response);
        void onError(String error);
    }

    public static void get(String endpoint, ApiCallback callback) {
        request("GET", endpoint, null, callback);
    }

    public static void post(
            String endpoint,
            JSONObject body,
            ApiCallback callback
    ) {
        request("POST", endpoint, body, callback);
    }

    private static void request(
            String method,
            String endpoint,
            JSONObject body,
            ApiCallback callback
    ) {
        executor.execute(() -> {
            HttpURLConnection connection = null;

            try {
                FirebaseUser user =
                        FirebaseAuth.getInstance().getCurrentUser();

                if (user == null) {
                    throw new Exception("User is not logged in.");
                }

                String token = user.getIdToken(false)
                        .getResult()
                        .getToken();

                if (token == null || token.isEmpty()) {
                    throw new Exception("Unable to get Firebase token.");
                }

                URL url = new URL(BASE_URL + endpoint);

                connection =
                        (HttpURLConnection) url.openConnection();

                connection.setRequestMethod(method);
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(20000);

                connection.setRequestProperty(
                        "Authorization",
                        "Bearer " + token
                );

                connection.setRequestProperty(
                        "Accept",
                        "application/json"
                );

                if ("POST".equals(method)) {
                    connection.setDoOutput(true);

                    connection.setRequestProperty(
                            "Content-Type",
                            "application/json; charset=UTF-8"
                    );

                    if (body != null) {
                        byte[] data = body.toString()
                                .getBytes(StandardCharsets.UTF_8);

                        try (OutputStream output =
                                     connection.getOutputStream()) {
                            output.write(data);
                            output.flush();
                        }
                    }
                }

                int statusCode = connection.getResponseCode();

                InputStream inputStream =
                        statusCode >= 200 && statusCode < 300
                                ? connection.getInputStream()
                                : connection.getErrorStream();

                StringBuilder response = new StringBuilder();

                if (inputStream != null) {
                    try (BufferedReader reader =
                                 new BufferedReader(
                                         new InputStreamReader(
                                                 inputStream,
                                                 StandardCharsets.UTF_8
                                         ))) {

                        String line;

                        while ((line = reader.readLine()) != null) {
                            response.append(line);
                        }
                    }
                }

                String responseText = response.toString();

                if (statusCode >= 200 && statusCode < 300) {
                    mainHandler.post(() ->
                            callback.onSuccess(responseText)
                    );
                } else {
                    String error = "HTTP " + statusCode + ": "
                            + responseText;

                    mainHandler.post(() ->
                            callback.onError(error)
                    );
                }

            } catch (Exception e) {
                String error = e.getMessage() == null
                        ? "Network request failed."
                        : e.getMessage();

                mainHandler.post(() ->
                        callback.onError(error)
                );

            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }
}
