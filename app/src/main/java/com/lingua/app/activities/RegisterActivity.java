package com.lingua.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

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

public class RegisterActivity extends AppCompatActivity {
    private EditText etName, etEmail, etPassword, etConfirmPassword;
    private Button btnRegister;
    private TextView tvError, tvLogin;
    private ProgressBar progressBar;
    private SessionManager session;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        session = SessionManager.getInstance(this);
        etName = findViewById(R.id.etName);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        btnRegister = findViewById(R.id.btnRegister);
        tvError = findViewById(R.id.tvError);
        tvLogin = findViewById(R.id.tvLogin);
        progressBar = findViewById(R.id.progressBar);

        btnRegister.setOnClickListener(v -> doRegister());
        tvLogin.setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
    }

    private void doRegister() {
        String name = etName.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String confirm = etConfirmPassword.getText().toString().trim();

        if (name.isEmpty() || email.isEmpty() || password.isEmpty()) {
            tvError.setText("Vui lòng điền đầy đủ thông tin");
            tvError.setVisibility(View.VISIBLE);
            return;
        }
        if (!password.equals(confirm)) {
            tvError.setText("Mật khẩu xác nhận không khớp");
            tvError.setVisibility(View.VISIBLE);
            return;
        }
        if (password.length() < 6) {
            tvError.setText("Mật khẩu phải có ít nhất 6 ký tự");
            tvError.setVisibility(View.VISIBLE);
            return;
        }

        setLoading(true);
        tvError.setVisibility(View.GONE);

        Map<String, String> body = new HashMap<>();
        body.put("displayName", name);
        body.put("email", email);
        body.put("password", password);

        LinguaApiService api = ApiClient.getService(this);
        api.register(body).enqueue(new Callback<ApiResponse<AuthData>>() {
            @Override
            public void onResponse(Call<ApiResponse<AuthData>> call, Response<ApiResponse<AuthData>> resp) {
                setLoading(false);
                if (resp.isSuccessful() && resp.body() != null && resp.body().isSuccess()) {
                    AuthData data = resp.body().getData();
                    session.saveSession(data.userId, data.accessToken, data.refreshToken, name, email);
                    startActivity(new Intent(RegisterActivity.this, MainActivity.class));
                    finish();
                } else {
                    String msg = resp.body() != null ? resp.body().getMessage() : "Đăng ký thất bại";
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

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnRegister.setEnabled(!loading);
    }
}
