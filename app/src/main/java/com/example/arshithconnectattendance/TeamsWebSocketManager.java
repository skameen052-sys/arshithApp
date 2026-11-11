package com.example.arshithconnectattendance;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class TeamsWebSocketManager {

    public interface EventListener {
        void onEvent(JsonObject event);
        void onConnected();
        void onDisconnected();
    }

    private static TeamsWebSocketManager instance;

    private final Context context;
    private final Handler handler =
            new Handler(Looper.getMainLooper());

    private WebSocket socket;
    private EventListener listener;

    private boolean manuallyClosed = false;
    private boolean connecting = false;

    private static final long RECONNECT_DELAY = 4000;

    private final Runnable reconnectTask = this::connect;

    private TeamsWebSocketManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public static synchronized TeamsWebSocketManager getInstance(
            Context context
    ) {
        if (instance == null) {
            instance = new TeamsWebSocketManager(context);
        }
        return instance;
    }

    public void setEventListener(EventListener listener) {
        this.listener = listener;
    }

    public void connect() {

        if (socket != null || connecting) return;

        manuallyClosed = false;

        String token = TeamsApiClient.getToken(context);

        if (token.isEmpty()) return;

        try {
            String encoded = URLEncoder.encode(
                    token,
                    StandardCharsets.UTF_8.name()
            );

            // Change to your deployed WSS address for production.
            String url = "ws://10.0.2.2:8000/api/chat/ws?token=" + encoded;

            Request request = new Request.Builder()
                    .url(url)
                    .build();

            OkHttpClient client =
                    TeamsApiClient.getOkHttpClient(context);

            connecting = true;

            socket = client.newWebSocket(
                    request,
                    new WebSocketListener() {

                        @Override
                        public void onOpen(
                                WebSocket webSocket,
                                Response response
                        ) {
                            connecting = false;

                            handler.post(() -> {
                                if (listener != null) {
                                    listener.onConnected();
                                }
                            });
                        }

                        @Override
                        public void onMessage(
                                WebSocket webSocket,
                                String text
                        ) {
                            try {
                                JsonObject event =
                                        JsonParser.parseString(text)
                                                .getAsJsonObject();

                                handler.post(() -> {
                                    if (listener != null) {
                                        listener.onEvent(event);
                                    }
                                });
                            } catch (Exception ignored) {
                            }
                        }

                        @Override
                        public void onClosed(
                                WebSocket webSocket,
                                int code,
                                String reason
                        ) {
                            connecting = false;
                            socket = null;

                            handler.post(() -> {
                                if (listener != null) {
                                    listener.onDisconnected();
                                }
                                scheduleReconnect();
                            });
                        }

                        @Override
                        public void onFailure(
                                WebSocket webSocket,
                                Throwable t,
                                Response response
                        ) {
                            connecting = false;
                            socket = null;

                            handler.post(() -> {
                                if (listener != null) {
                                    listener.onDisconnected();
                                }
                                scheduleReconnect();
                            });
                        }
                    }
            );

        } catch (Exception e) {
            connecting = false;
            scheduleReconnect();
        }
    }

    public void sendTyping(
            String chatType,
            String targetId,
            boolean isTyping
    ) {
        if (socket == null) return;

        JsonObject event = new JsonObject();

        event.addProperty("action", "typing");
        event.addProperty("chat_type", chatType);
        event.addProperty("target_id", targetId);
        event.addProperty("is_typing", isTyping);

        socket.send(event.toString());
    }

    private void scheduleReconnect() {
        if (manuallyClosed) return;

        handler.removeCallbacks(reconnectTask);

        handler.postDelayed(
                reconnectTask,
                RECONNECT_DELAY
        );
    }

    public void disconnect() {
        manuallyClosed = true;

        handler.removeCallbacks(reconnectTask);

        if (socket != null) {
            socket.close(1000, "Teams screen closed");
            socket = null;
        }

        connecting = false;
    }
}