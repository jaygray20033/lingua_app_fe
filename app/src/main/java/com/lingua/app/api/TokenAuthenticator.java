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

    public TokenAuthenticator(Context context) {
        this.context = context.getApplicationContext();
        this.session = SessionManager.getInstance(context);
    }

    @Override
    public synchronized Request authenticate(Route route, Response response) throws IOException {
        if (responseCount(response) >= 3) {
            redirectToLogin();
            return null;
        }

        String refreshToken = session.getRefreshToken();
        if (refreshToken == null) {
            redirectToLogin();
            return null;
        }

        // Call refresh endpoint synchronously
        try {
            OkHttpClient client = new OkHttpClient();
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

        redirectToLogin();
        return null;
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
