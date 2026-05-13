package com.lingua.app.api;

import android.content.Context;

import com.lingua.app.BuildConfig;
import com.lingua.app.utils.SessionManager;

import java.util.concurrent.TimeUnit;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiClient {
    private static Retrofit retrofit;

    public static Retrofit getClient(Context context) {
        if (retrofit == null) {
            SessionManager session = SessionManager.getInstance(context.getApplicationContext());

            // Auth header interceptor
            Interceptor authInterceptor = chain -> {
                Request.Builder builder = chain.request().newBuilder();
                String token = session.getAccessToken();
                if (token != null) {
                    builder.header("Authorization", "Bearer " + token);
                }
                return chain.proceed(builder.build());
            };

            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(BuildConfig.DEBUG
                    ? HttpLoggingInterceptor.Level.BODY
                    : HttpLoggingInterceptor.Level.NONE);

            OkHttpClient client = new OkHttpClient.Builder()
                    .addInterceptor(authInterceptor)
                    .addInterceptor(logging)
                    .authenticator(new TokenAuthenticator(context))
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .build();

            retrofit = new Retrofit.Builder()
                    .baseUrl(BuildConfig.BASE_URL)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return retrofit;
    }

    public static LinguaApiService getService(Context context) {
        return getClient(context).create(LinguaApiService.class);
    }
}
