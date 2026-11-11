package com.example.arshithconnectattendance;

import android.content.Context;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.logging.HttpLoggingInterceptor;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.util.concurrent.TimeUnit;

public class TeamsApiClient {

    // Android emulator address for FastAPI running on your computer.
    private static final String BASE_URL =
            "http://10.0.2.2:8000/";

    private static Retrofit retrofit;
    private static OkHttpClient client;

    private TeamsApiClient() {
    }

    public static void resetClient() {
        retrofit = null;
        client = null;
    }

    private static OkHttpClient getClient(Context context) {

        if (client == null) {

            HttpLoggingInterceptor logging =
                    new HttpLoggingInterceptor();

            logging.setLevel(
                    HttpLoggingInterceptor.Level.BASIC
            );

            client = new OkHttpClient.Builder()

                    .addInterceptor(chain -> {

                        Request original = chain.request();

                        Request.Builder builder =
                                original.newBuilder();

                        String token =
                                getToken(context);

                        if (!token.isEmpty()) {
                            builder.header(
                                    "Authorization",
                                    "Bearer " + token
                            );
                        }

                        return chain.proceed(builder.build());
                    })

                    .addInterceptor(logging)
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .writeTimeout(60, TimeUnit.SECONDS)
                    .build();
        }

        return client;
    }

    public static String getToken(Context context) {

        // Change this preference/key to your existing login token storage.
        return context.getSharedPreferences(
                "arshith_auth",
                Context.MODE_PRIVATE
        ).getString("arshith_auth_token", "");
    }

    public static TeamsApiService getService(Context context) {

        if (retrofit == null) {

            retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(getClient(context))
                    .addConverterFactory(
                            GsonConverterFactory.create()
                    )
                    .build();
        }

        return retrofit.create(TeamsApiService.class);
    }

    public static OkHttpClient getOkHttpClient(Context context) {
        return getClient(context);
    }
}
