package com.lingua.app.api;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.google.gson.Gson;
import com.lingua.app.activities.LoginActivity;
import com.lingua.app.models.ApiResponse;
import com.lingua.app.models.TokenData;
import com.lingua.app.utils.SessionManager;

import java.io.IOException;

import okhttp3.Authenticator;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.Route;

/**
 * Auto-refreshes access token on 401 (TODO 2.3).
 */
public class TokenAuthenticator implements Authenticator {
    private static final String TAG = "TokenAuthenticator";
    private final Context context;
    private final SessionManager session;

    // TD-3 FIX: tái sử dụng một OkHttpClient duy nhất cho mọi refresh request.
    // Trước đây mỗi lần 401 lại `new OkHttpClient()` → tạo thread pool +
    // connection pool mới, tốn kém và làm phồng memory nếu user gặp 401 liên
    // tiếp (ví dụ token refresh xong nhưng các request song song chưa kịp dùng
    // token mới → mỗi request lại trigger authenticate). Singleton + lazy init.
    private static volatile OkHttpClient sRefreshClient;

    // BUG-009 FIX: lock static dùng chung giữa MọI instance của TokenAuthenticator.
    // Trước đây `synchronized` trên instance method chỉ lock trên `this` — nếu
    // sau này có ai tạo Retrofit thứ hai (vd. upload client / SSE client) thì
    // sẽ có 2 instance và 2 refresh request song song khi cả hai OkHttpClient
    // cùng gặp 401 → race condition: cả hai gọi /auth/refresh, refresh token
    // bị invalidate sau request đầu, request thứ hai fail → force logout sai.
    private static final Object REFRESH_LOCK = new Object();

    // BUG-009 FIX: từng refresh gần đây? Nếu trong cửa sổ 5s vừa refresh xong,
    // các request 401 đang queue khác (đã dùng access token cũ) không cần gọi
    // /auth/refresh nữa — chỉ cần thay header bằng access token mới từ session.
    private static volatile long lastRefreshAtMs = 0;
    private static final long REFRESH_WINDOW_MS = 5000;

    private static OkHttpClient getRefreshClient() {
        if (sRefreshClient == null) {
            synchronized (TokenAuthenticator.class) {
                if (sRefreshClient == null) {
                    sRefreshClient = new OkHttpClient.Builder()
                            // Không gắn authenticator/interceptor → tránh đệ quy
                            // vô tận khi refresh endpoint cũng trả 401.
                            .build();
                }
            }
        }
        return sRefreshClient;
    }

    public TokenAuthenticator(Context context) {
        this.context = context.getApplicationContext();
        this.session = SessionManager.getInstance(context);
    }

    @Override
    public Request authenticate(Route route, Response response) throws IOException {
        // BUG-009 FIX: lock tĩnh để bảo vệ refresh xuyên-instance.
        synchronized (REFRESH_LOCK) {
        if (responseCount(response) >= 3) {
            // BUG-025 FIX: log rõ nguyên nhân forced logout để dễ debug khi user
            // complain "app tự đăng xuất".
            Log.w(TAG, "Refresh giving up after " + responseCount(response)
                    + " attempts → redirectToLogin");
            redirectToLogin();
            return null;
        }

        String refreshToken = session.getRefreshToken();
        if (refreshToken == null) {
            // BUG-025 FIX: log bổ sung.
            Log.w(TAG, "refreshToken == null → redirectToLogin");
            redirectToLogin();
            return null;
        }

        // BUG-009 FIX: nếu vừa refresh xong trong REFRESH_WINDOW_MS, kiểm tra xem
        // access token trong session có mới hơn token đã dùng trong request 401 hay
        // không. Nếu mới hơn → thay header và retry, không cần gọi refresh nữa.
        if (System.currentTimeMillis() - lastRefreshAtMs < REFRESH_WINDOW_MS) {
            String fresh = session.getAccessToken();
            String reqAuth = response.request().header("Authorization");
            String reqToken = (reqAuth != null && reqAuth.startsWith("Bearer "))
                    ? reqAuth.substring(7) : null;
            if (fresh != null && !fresh.isEmpty() && !fresh.equals(reqToken)) {
                Log.d(TAG, "Reusing recently refreshed token (skip /auth/refresh)");
                return response.request().newBuilder()
                        .header("Authorization", "Bearer " + fresh)
                        .build();
            }
        }

        // Call refresh endpoint synchronously
        try {
            // TD-3 FIX: dùng singleton client thay vì tạo mới.
            OkHttpClient client = getRefreshClient();
            // BUG #13 FIX: ghép URL an toàn — đảm bảo BASE_URL kết thúc bằng "/"
            // trước khi nối "auth/refresh". Trước đây nếu ai đó cấu hình BASE_URL
            // không có trailing slash, URL sẽ thiếu "/" giữa segment.
            // Ngoài ra dùng JSONObject để escape refreshToken đúng cách (tránh
            // lỗi nếu token có ký tự đặc biệt như backslash hoặc dấu nháy kép).
            String base = com.lingua.app.BuildConfig.BASE_URL;
            if (base == null) base = "";
            if (!base.endsWith("/")) base += "/";
            String refreshUrl = base + "auth/refresh";

            String json;
            try {
                org.json.JSONObject payload = new org.json.JSONObject();
                payload.put("refreshToken", refreshToken);
                json = payload.toString();
            } catch (org.json.JSONException je) {
                // Fallback (very unlikely path).
                json = "{\"refreshToken\":\"" + refreshToken.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}";
            }
            RequestBody body = RequestBody.create(json, MediaType.parse("application/json"));
            Request refreshReq = new Request.Builder()
                    .url(refreshUrl)
                    .post(body)
                    .build();

            try (okhttp3.Response refreshResp = client.newCall(refreshReq).execute()) {
                if (refreshResp.isSuccessful() && refreshResp.body() != null) {
                    String respBody = refreshResp.body().string();
                    Gson gson = new Gson();
                    ApiResponse<TokenData> tokenResp = gson.fromJson(respBody,
                            new com.google.gson.reflect.TypeToken<ApiResponse<TokenData>>() {}.getType());

                    if (tokenResp != null && tokenResp.isSuccess() && tokenResp.getData() != null) {
                        TokenData data = tokenResp.getData();
                        session.updateTokens(data.accessToken, data.refreshToken);
                        // BUG-009 FIX: mở cửa sổ 5s để các request 401 đang queue khác
                        // dùng trực tiếp access token mới thay vì tất cả cùng gọi /auth/refresh.
                        lastRefreshAtMs = System.currentTimeMillis();
                        Log.d(TAG, "Token refreshed successfully");

                        return response.request().newBuilder()
                                .header("Authorization", "Bearer " + data.accessToken)
                                .build();
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Token refresh failed", e);
        }

        // BUG-025 FIX: log rõ ràng trước khi force logout.
        Log.w(TAG, "Token refresh exhausted → redirectToLogin");
        redirectToLogin();
        return null;
        } // end synchronized
    }

    private void redirectToLogin() {
        session.clear();
        Intent intent = new Intent(context, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        context.startActivity(intent);
    }

    private int responseCount(Response r) {
        int count = 1;
        while ((r = r.priorResponse()) != null) count++;
        return count;
    }
}
