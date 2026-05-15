package com.lingua.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.lingua.app.R;
import com.lingua.app.api.ApiClient;
import com.lingua.app.api.LinguaApiService;
import com.lingua.app.models.ApiResponse;
import com.lingua.app.models.AuthData;
import com.lingua.app.utils.SessionManager;

import java.util.HashMap;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginActivity extends AppCompatActivity {
    private TextInputEditText etEmail, etPassword;
    private Button btnLogin;
    private TextView tvError, tvRegister, tvForgotPassword;
    private ProgressBar progressBar;
    private SessionManager session;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        session = SessionManager.getInstance(this);
        if (session.isLoggedIn()) {
            goToMain();
            return;
        }

        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        tvError = findViewById(R.id.tvError);
        tvRegister = findViewById(R.id.tvRegister);
        tvForgotPassword = findViewById(R.id.tvForgotPassword);
        progressBar = findViewById(R.id.progressBar);

        btnLogin.setOnClickListener(v -> doLogin());
        tvRegister.setOnClickListener(v -> {
            startActivity(new Intent(this, RegisterActivity.class));
            finish();
        });

        // U9 FIX: forgot-password link — shows a guidance dialog because the
        // backend doesn't currently expose a reset endpoint. The dialog at least
        // informs the user instead of pretending the link is broken.
        if (tvForgotPassword != null) {
            tvForgotPassword.setOnClickListener(v -> showForgotPasswordDialog());
        }

        // U8 FIX: hitting Enter / IME-Done on the password field submits the
        // form, which is what most users expect from a login screen.
        etPassword.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                doLogin();
                return true;
            }
            return false;
        });
    }

    private void showForgotPasswordDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Quên mật khẩu")
                .setMessage("Vui lòng liên hệ bộ phận hỗ trợ tại support@lingua.app "
                        + "để đặt lại mật khẩu. Tính năng đặt lại tự động sẽ có trong bản cập nhật sắp tới.")
                .setPositiveButton("Đã hiểu", null)
                .show();
    }

    private void doLogin() {
        String email = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
        String password = etPassword.getText() != null ? etPassword.getText().toString().trim() : "";
        if (email.isEmpty() || password.isEmpty()) {
            tvError.setText("Vui lòng nhập email và mật khẩu");
            tvError.setVisibility(View.VISIBLE);
            return;
        }
        // U8 FIX: validate email format client-side so the user gets immediate
        // feedback instead of waiting for a 401 from the server.
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tvError.setText("Email không hợp lệ");
            tvError.setVisibility(View.VISIBLE);
            return;
        }
        setLoading(true);
        tvError.setVisibility(View.GONE);

        Map<String, String> body = new HashMap<>();
        body.put("email", email);
        body.put("password", password);

        LinguaApiService api = ApiClient.getService(this);
        api.login(body).enqueue(new Callback<ApiResponse<AuthData>>() {
            @Override
            public void onResponse(Call<ApiResponse<AuthData>> call, Response<ApiResponse<AuthData>> resp) {
                setLoading(false);
                if (resp.isSuccessful() && resp.body() != null && resp.body().isSuccess()) {
                    AuthData data = resp.body().getData();
                    String displayName = data.user != null ? data.user.displayName : "";
                    String userEmail = data.user != null ? data.user.email : email;
                    session.saveSession(data.userId, data.accessToken, data.refreshToken, displayName, userEmail);
                    goToMain();
                } else {
                    String msg = resp.body() != null ? resp.body().getMessage() : "Đăng nhập thất bại";
                    tvError.setText(msg);
                    tvError.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<AuthData>> call, Throwable t) {
                setLoading(false);
                tvError.setText("Lỗi kết nối: " + t.getMessage());
                tvError.setVisibility(View.VISIBLE);
            }
        });
    }

    private void goToMain() {
        // BUG #10 FIX: kiểm tra SharedPreferences("onboarded") trước khi vào
        // MainActivity. Nếu user đăng nhập trên thiết bị mới (hoặc sau khi xoá
        // data), onboarded = false nhưng app cũ vẫn vào MainActivity → bỏ qua
        // bước onboarding. Chỉ SplashActivity mới kiểm tra onboarded đúng cách.
        boolean onboarded = getSharedPreferences(OnboardingActivity.PREFS, MODE_PRIVATE)
                .getBoolean(OnboardingActivity.KEY_ONBOARDED, false);
        Class<?> target = onboarded ? MainActivity.class : OnboardingActivity.class;
        startActivity(new Intent(this, target));
        finish();
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnLogin.setEnabled(!loading);
    }
}
