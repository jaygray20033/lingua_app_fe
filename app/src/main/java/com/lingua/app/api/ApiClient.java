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

/**
 * Retrofit singleton for the Lingua backend.
 *
 * R4-H2 FIX: tránh leak Activity context.
 *  - getClient(Context) NGAY LẬP TỨC chuyển sang getApplicationContext()
 *    để Retrofit / OkHttp interceptor / TokenAuthenticator KHÔNG bao giờ giữ
 *    reference đến Activity context.
 *  - Cache applicationContext static để các lần `invalidate()` (R4-M4) có thể
 *    rebuild Retrofit mà không cần caller pass context lại.
 *
 * R4-M4 FIX: thêm `invalidate()` để LoginActivity gọi sau khi saveSession()
 *  → force rebuild OkHttp/Retrofit, đảm bảo connection pool không cache TLS
 *  session/old token của tài khoản trước.
 */
public class ApiClient {
    private static volatile Retrofit retrofit;
    private static volatile Context appContext; // R4-H2: cached application context

    public static Retrofit getClient(Context context) {
        // R4-H2 FIX: lấy application context NGAY và lưu cache. Không bao giờ
        // dùng Activity context để build Retrofit / OkHttp / Authenticator.
        if (appContext == null && context != null) {
            appContext = context.getApplicationContext();
        }
        if (retrofit == null) {
            synchronized (ApiClient.class) {
                if (retrofit == null) {
                    retrofit = buildRetrofit();
                }
            }
        }
        return retrofit;
    }

    private static Retrofit buildRetrofit() {
        Context ctx = appContext;
        if (ctx == null) {
            throw new IllegalStateException("ApiClient.getClient() must be called with a non-null Context at least once before buildRetrofit()");
        }
        SessionManager session = SessionManager.getInstance(ctx);

        // Auth header interceptor — capture only `session` (which references the
        // application context internally), no Activity reference here.
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

        // R4-H2 FIX: TokenAuthenticator pass application context, không phải
        // Activity context.
        // R5-032 FIX: sane timeouts cho mạng yếu (3G/Edge / vùng có latence cao).
        //   - connectTimeout 15s : nếu không kết nối được sau 15s thì server down.
        //     30s trước đây làm app freeze UI quá lâu khi mất mạng.
        //   - readTimeout 30s : assez généreux pour SSE roleplay (chunk-by-chunk)
        //     mais évite de bloquer le pool de connections sur des sockets morts.
        //   - writeTimeout 30s : pour upload avatar (max 2MB / base64 ~2.7MB).
        //   - callTimeout 90s : budget global par requête. Au-delà, on coupe net
        //     pour libérer le thread. SSE roleplay = endpoint streaming long ;
        //     pour ces calls, l'Activity overrides callTimeout via tag/header
        //     côté Retrofit (cf. AIRoleplayActivity).
        //   - retryOnConnectionFailure(true) : par défaut OkHttp retry, on garde.
        //     Combiné avec TokenAuthenticator, un 401 transient ne kill pas la session.
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(authInterceptor)
                .addInterceptor(logging)
                .authenticator(new TokenAuthenticator(ctx))
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .callTimeout(90, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();

        return new Retrofit.Builder()
                .baseUrl(BuildConfig.BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
    }

    public static LinguaApiService getService(Context context) {
        return getClient(context).create(LinguaApiService.class);
    }

    /**
     * R4-M4 FIX: force rebuild Retrofit + OkHttp instance.
     *
     * Use case chính: sau khi user logout / login với account khác,
     * SessionManager.saveSession() đã ghi tokens mới NHƯNG OkHttp connection
     * pool có thể vẫn cache TLS session, và Retrofit singleton có thể giữ
     * reference vào snapshot token cũ trong vài request đầu (token authenticator
     * trigger refresh nhưng refresh dùng OLD refresh_token → fail rồi user bị
     * kick về login).
     *
     * Gọi `ApiClient.invalidate()` ngay sau saveSession() để chắc chắn lần
     * call API tiếp theo sẽ build OkHttp client mới với token mới ngay từ đầu.
     *
     * Thread-safe: synchronized trên class, đảm bảo không có call song song
     * thấy retrofit = null trong khi đang rebuild.
     */
    public static void invalidate() {
        synchronized (ApiClient.class) {
            // Best-effort dọn dẹp client cũ — không bắt buộc nhưng giải phóng
            // sớm connection pool / executor.
            try {
                if (retrofit != null) {
                    OkHttpClient old = (OkHttpClient) retrofit.callFactory();
                    if (old != null) {
                        old.dispatcher().cancelAll();
                        try { old.connectionPool().evictAll(); } catch (Exception ignore) {}
                    }
                }
            } catch (Exception ignore) {}
            retrofit = null;
        }
    }
}
