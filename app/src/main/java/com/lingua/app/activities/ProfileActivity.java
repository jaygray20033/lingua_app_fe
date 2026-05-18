package com.lingua.app.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.InputType;
import android.util.Base64;
import android.view.View;
import android.widget.*;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import java.io.ByteArrayOutputStream;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.lingua.app.R;
import com.lingua.app.api.ApiClient;
import com.lingua.app.api.LinguaApiService;
import com.lingua.app.models.ApiResponse;
import com.lingua.app.models.GamificationStats;
import com.lingua.app.models.User;
import com.lingua.app.utils.SessionManager;

import java.util.HashMap;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Profile screen.
 *
 * New features (TODO part 2):
 *   - Change password (POST /api/auth/change-password).
 *   - Set daily XP goal (PUT /api/gamification/daily-goal).
 *   - Buy / show streak freeze (POST /api/gamification/streak-freeze/buy).
 *   - Avatar upload (PUT /api/auth/profile with avatar URL).
 *   - Quick links to MyCourses and Favorites.
 */
public class ProfileActivity extends AppCompatActivity {
    // CB-1 FIX: Bỏ REQUEST_PICK_AVATAR vì đã chuyển sang ActivityResultLauncher
    private static final String PREFS = "LinguaPrefs";

    private TextView tvDisplayName, tvEmail, tvXp, tvLevel, tvStreak, tvGems;
    private TextView tvDailyGoalValue, tvStreakFreezeCount, tvAvatar;
    private ImageView imgAvatar;
    private LinguaApiService apiService;
    private SessionManager session;

    private int dailyGoalXp = 50;
    private int streakFreezeCount = 0;
    private int currentGems = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        session = SessionManager.getInstance(this);
        apiService = ApiClient.getService(this);

        tvDisplayName = findViewById(R.id.tvDisplayName);
        tvEmail = findViewById(R.id.tvEmail);
        tvXp = findViewById(R.id.tvXp);
        tvLevel = findViewById(R.id.tvLevel);
        tvStreak = findViewById(R.id.tvStreak);
        tvGems = findViewById(R.id.tvGems);
        tvDailyGoalValue = findViewById(R.id.tvDailyGoalValue);
        tvStreakFreezeCount = findViewById(R.id.tvStreakFreezeCount);
        tvAvatar = findViewById(R.id.tvAvatar);
        imgAvatar = findViewById(R.id.imgAvatar);

        Button btnLeaderboard = findViewById(R.id.btnLeaderboard);
        Button btnAchievements = findViewById(R.id.btnAchievements);
        Button btnQuests = findViewById(R.id.btnQuests);
        Button btnMyCourses = findViewById(R.id.btnMyCourses);
        Button btnFavorites = findViewById(R.id.btnFavorites);
        Button btnStatistics = findViewById(R.id.btnStatistics);
        Button btnSettings = findViewById(R.id.btnSettings);
        Button btnChangePassword = findViewById(R.id.btnChangePassword);
        Button btnSetDailyGoal = findViewById(R.id.btnSetDailyGoal);
        Button btnBuyStreakFreeze = findViewById(R.id.btnBuyStreakFreeze);
        Button btnChangeAvatar = findViewById(R.id.btnChangeAvatar);
        Button btnLogout = findViewById(R.id.btnLogout);

        btnLeaderboard.setOnClickListener(v -> startActivity(new Intent(this, LeaderboardActivity.class)));
        btnAchievements.setOnClickListener(v -> startActivity(new Intent(this, AchievementsActivity.class)));
        btnQuests.setOnClickListener(v -> startActivity(new Intent(this, QuestsActivity.class)));
        btnMyCourses.setOnClickListener(v -> startActivity(new Intent(this, MyCoursesActivity.class)));
        btnFavorites.setOnClickListener(v -> startActivity(new Intent(this, FavoritesActivity.class)));
        btnStatistics.setOnClickListener(v -> startActivity(new Intent(this, StatisticsActivity.class)));
        btnSettings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        btnChangePassword.setOnClickListener(v -> showChangePasswordDialog());
        btnSetDailyGoal.setOnClickListener(v -> showDailyGoalDialog());
        btnBuyStreakFreeze.setOnClickListener(v -> confirmBuyStreakFreeze());
        btnChangeAvatar.setOnClickListener(v -> pickAvatar());
        btnLogout.setOnClickListener(v -> logout());

        // Read cached daily goal from prefs
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        dailyGoalXp = prefs.getInt("daily_goal_xp", 50);
        tvDailyGoalValue.setText(dailyGoalXp + " XP");

        loadProfile();
        loadStats();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadStats();
    }

    private void loadProfile() {
        tvDisplayName.setText(session.getDisplayName());
        tvEmail.setText(session.getEmail());

        apiService.getMe().enqueue(new Callback<ApiResponse<User>>() {
            @Override
            public void onResponse(Call<ApiResponse<User>> call, Response<ApiResponse<User>> resp) {
                if (resp.isSuccessful() && resp.body() != null && resp.body().isSuccess()) {
                    User u = resp.body().getData();
                    if (u == null) return;
                    runOnUiThread(() -> {
                        if (u.displayName != null) tvDisplayName.setText(u.displayName);
                        if (u.email != null) tvEmail.setText(u.email);
                        // BUG #16 FIX: load avatar URL với Glide (circleCrop)
                        // thay vì chỉ ẩn tvAvatar. Trước đây imgAvatar không bao
                        // giờ được load → tính năng đổi avatar hoạt động (gọi
                        // API upload) nhưng không bao giờ hiển thị kết quả.
                        if (u.avatarUrl != null && !u.avatarUrl.isEmpty()) {
                            try {
                                com.bumptech.glide.Glide.with(ProfileActivity.this)
                                        .load(u.avatarUrl)
                                        .circleCrop()
                                        .placeholder(android.R.drawable.sym_def_app_icon)
                                        .error(android.R.drawable.sym_def_app_icon)
                                        .into(imgAvatar);
                                imgAvatar.setVisibility(View.VISIBLE);
                                tvAvatar.setVisibility(View.GONE);
                            } catch (Throwable t) {
                                // Fallback nếu Glide lỗi: vẫn ẩn emoji.
                                tvAvatar.setVisibility(View.GONE);
                            }
                        }
                    });
                }
            }
            @Override public void onFailure(Call<ApiResponse<User>> call, Throwable t) {}
        });
    }

    private void loadStats() {
        apiService.getMyStats().enqueue(new Callback<ApiResponse<GamificationStats>>() {
            @Override
            public void onResponse(Call<ApiResponse<GamificationStats>> call, Response<ApiResponse<GamificationStats>> resp) {
                if (resp.isSuccessful() && resp.body() != null && resp.body().isSuccess()) {
                    GamificationStats s = resp.body().getData();
                    if (s == null) return;
                    currentGems = s.gems;
                    streakFreezeCount = s.getStreakFreezeCount();
                    runOnUiThread(() -> {
                        tvXp.setText(String.valueOf(s.totalXp));
                        tvLevel.setText(String.valueOf(s.level));
                        tvStreak.setText(String.valueOf(s.getStreak()));
                        tvGems.setText(String.valueOf(s.gems));
                        tvStreakFreezeCount.setText("x" + streakFreezeCount);
                        if (s.getDailyGoal() > 0) {
                            dailyGoalXp = s.getDailyGoal();
                            tvDailyGoalValue.setText(dailyGoalXp + " XP");
                            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                                    .putInt("daily_goal_xp", dailyGoalXp).apply();
                        }
                    });
                }
            }
            @Override public void onFailure(Call<ApiResponse<GamificationStats>> call, Throwable t) {}
        });
    }

    /** Shows a dialog for changing password (calls POST /api/auth/change-password). */
    private void showChangePasswordDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int p = (int) (16 * getResources().getDisplayMetrics().density);
        layout.setPadding(p, p, p, p);

        EditText etOld = new EditText(this);
        etOld.setHint("Mật khẩu hiện tại");
        etOld.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(etOld);

        EditText etNew = new EditText(this);
        etNew.setHint("Mật khẩu mới (tối thiểu 6 ký tự)");
        etNew.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(etNew);

        EditText etConfirm = new EditText(this);
        etConfirm.setHint("Xác nhận mật khẩu mới");
        etConfirm.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(etConfirm);

        new AlertDialog.Builder(this)
                .setTitle("🔐 Đổi mật khẩu")
                .setView(layout)
                .setPositiveButton("Đổi", (d, w) -> {
                    String oldPass = etOld.getText().toString();
                    String newPass = etNew.getText().toString();
                    String confirm = etConfirm.getText().toString();
                    if (newPass.length() < 6) {
                        Toast.makeText(this, "Mật khẩu mới phải có ít nhất 6 ký tự", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (!newPass.equals(confirm)) {
                        Toast.makeText(this, "Xác nhận mật khẩu không khớp", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    submitChangePassword(oldPass, newPass);
                })
                .setNegativeButton("Huỷ", null)
                .show();
    }

    private void submitChangePassword(String oldPass, String newPass) {
        Map<String, String> body = new HashMap<>();
        body.put("oldPassword", oldPass);
        body.put("newPassword", newPass);
        body.put("currentPassword", oldPass); // some backends use currentPassword
        apiService.changePassword(body).enqueue(new Callback<ApiResponse<Object>>() {
            @Override public void onResponse(Call<ApiResponse<Object>> c, Response<ApiResponse<Object>> r) {
                if (r.isSuccessful() && r.body() != null && r.body().isSuccess()) {
                    Toast.makeText(ProfileActivity.this, "✅ Đã đổi mật khẩu", Toast.LENGTH_SHORT).show();
                } else {
                    String msg = r.body() != null && r.body().getMessage() != null
                            ? r.body().getMessage() : "Không đổi được mật khẩu";
                    Toast.makeText(ProfileActivity.this, msg, Toast.LENGTH_SHORT).show();
                }
            }
            @Override public void onFailure(Call<ApiResponse<Object>> c, Throwable t) {
                Toast.makeText(ProfileActivity.this, "Lỗi: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    /** Shows a dialog for setting daily XP goal (PUT /api/gamification/daily-goal). */
    private void showDailyGoalDialog() {
        final int[] choices = {20, 50, 100, 200};
        String[] labels = {"😌 Thoải mái — 20 XP", "🙂 Thường — 50 XP", "💪 Cố gắng — 100 XP", "🔥 Tham vọng — 200 XP"};
        new AlertDialog.Builder(this)
                .setTitle("🎯 Mục tiêu hàng ngày")
                .setItems(labels, (d, which) -> submitDailyGoal(choices[which]))
                .setNegativeButton("Huỷ", null)
                .show();
    }

    private void submitDailyGoal(int xp) {
        Map<String, Object> body = new HashMap<>();
        // BUG #R3-H1 FIX: backend reads `dailyXpGoal` as the primary key (see
        // BUG B7). Previously ProfileActivity only sent `dailyGoal` + `xp`,
        // which meant the server-side `user_languages.daily_xp_goal` never
        // updated when the user changed the goal from the Profile screen.
        // This caused silent reward loss because streak/quest goal_met flags
        // kept using the old daily goal.
        body.put("dailyXpGoal", xp); // primary key for backend
        body.put("dailyGoal", xp);
        body.put("xp", xp); // alternate key
        apiService.setDailyGoal(body).enqueue(new Callback<ApiResponse<Object>>() {
            @Override public void onResponse(Call<ApiResponse<Object>> c, Response<ApiResponse<Object>> r) {
                if (r.isSuccessful() && r.body() != null && r.body().isSuccess()) {
                    dailyGoalXp = xp;
                    tvDailyGoalValue.setText(xp + " XP");
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                            .putInt("daily_goal_xp", xp).apply();
                    Toast.makeText(ProfileActivity.this, "✅ Đã đặt mục tiêu " + xp + " XP/ngày", Toast.LENGTH_SHORT).show();
                } else {
                    // Save locally as fallback so the home screen still uses it
                    dailyGoalXp = xp;
                    tvDailyGoalValue.setText(xp + " XP");
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                            .putInt("daily_goal_xp", xp).apply();
                    Toast.makeText(ProfileActivity.this, "Đã lưu cục bộ", Toast.LENGTH_SHORT).show();
                }
            }
            @Override public void onFailure(Call<ApiResponse<Object>> c, Throwable t) {
                Toast.makeText(ProfileActivity.this, "Lỗi: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void confirmBuyStreakFreeze() {
        if (currentGems < 200) {
            Toast.makeText(this, "💎 Bạn cần ít nhất 200 đá quý để mua Streak Freeze.", Toast.LENGTH_LONG).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("🧊 Mua Streak Freeze")
                .setMessage("Streak Freeze giúp bạn giữ chuỗi ngày khi không học được trong 1 ngày.\n\nGiá: 200 💎\nBạn đang có: " + currentGems + " 💎")
                .setPositiveButton("Mua", (d, w) -> buyStreakFreeze())
                .setNegativeButton("Huỷ", null)
                .show();
    }

    private void buyStreakFreeze() {
        apiService.buyStreakFreeze().enqueue(new Callback<ApiResponse<Object>>() {
            @Override public void onResponse(Call<ApiResponse<Object>> c, Response<ApiResponse<Object>> r) {
                if (r.isSuccessful() && r.body() != null && r.body().isSuccess()) {
                    Toast.makeText(ProfileActivity.this, "✅ Đã mua Streak Freeze!", Toast.LENGTH_SHORT).show();
                    loadStats();
                } else {
                    String msg = r.body() != null && r.body().getMessage() != null
                            ? r.body().getMessage() : "Không mua được";
                    Toast.makeText(ProfileActivity.this, msg, Toast.LENGTH_SHORT).show();
                }
            }
            @Override public void onFailure(Call<ApiResponse<Object>> c, Throwable t) {
                Toast.makeText(ProfileActivity.this, "Lỗi: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    // CB-1 FIX: Thay startActivityForResult/onActivityResult (deprecated từ API 29+)
    // bằng ActivityResultLauncher chuẩn — nhất quán với OnboardingActivity.
    private final ActivityResultLauncher<String> pickAvatarLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri == null) return;
                handleAvatarUri(uri);
            });

    private void pickAvatar() {
        // CB-1 FIX: dùng ActivityResultContracts.GetContent() với MIME type "image/*"
        pickAvatarLauncher.launch("image/*");
    }

    /** CB-1 FIX: tách logic xử lý ảnh ra method riêng để ActivityResultLauncher gọi. */
    private void handleAvatarUri(Uri uri) {
        try {
            Bitmap bm = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);

            // U13 FIX: không gửi `uri.toString()` (content://...) nữa — server
            // không thể truy cập được. Ta scale lại, nén JPEG, rồi gửi base64
            // lên backend; backend lưu lại và trả về URL công khai.
            Bitmap scaled = scaleBitmap(bm, 512);
            imgAvatar.setImageBitmap(scaled);
            imgAvatar.setVisibility(View.VISIBLE);
            tvAvatar.setVisibility(View.GONE);
            Toast.makeText(this, "Đang tải ảnh lên...", Toast.LENGTH_SHORT).show();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            scaled.compress(Bitmap.CompressFormat.JPEG, 80, baos);
            String b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);

            // Gửi avatarBase64 (field mới) + contentType. Backend
            // (updateProfile hoặc /upload/avatar) lưu lại và trả về URL công khai.
            Map<String, Object> body = new HashMap<>();
            body.put("avatarBase64", b64);
            body.put("avatarContentType", "image/jpeg");
            apiService.updateProfile(body).enqueue(new Callback<ApiResponse<User>>() {
                @Override public void onResponse(Call<ApiResponse<User>> c, Response<ApiResponse<User>> r) {
                    if (r.isSuccessful() && r.body() != null && r.body().isSuccess()) {
                        Toast.makeText(ProfileActivity.this, "✅ Đã cập nhật ảnh đại diện", Toast.LENGTH_SHORT).show();
                    } else {
                        String msg = r.body() != null && r.body().getMessage() != null
                                ? r.body().getMessage() : "Không lưu được ảnh";
                        Toast.makeText(ProfileActivity.this, msg, Toast.LENGTH_SHORT).show();
                    }
                }
                @Override public void onFailure(Call<ApiResponse<User>> c, Throwable t) {
                    Toast.makeText(ProfileActivity.this, "Lỗi tải ảnh: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        } catch (Exception e) {
            Toast.makeText(this, "Không đọc được ảnh: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /** U13 helper: scale bitmap lại để không gửi 5MB base64 lãng phí.
     *  (BUG #19 FIX: comment tiếng Pháp → tiếng Việt.) */
    private Bitmap scaleBitmap(Bitmap src, int maxSize) {
        if (src == null) return null;
        int w = src.getWidth(), h = src.getHeight();
        if (w <= maxSize && h <= maxSize) return src;
        float ratio = Math.min((float) maxSize / w, (float) maxSize / h);
        int nw = Math.round(w * ratio);
        int nh = Math.round(h * ratio);
        return Bitmap.createScaledBitmap(src, nw, nh, true);
    }

    // 6.14 FIX: chặn double-click "Đăng xuất" — trước đây bấm 2 lần gọi API 2 lần.
    private boolean isLoggingOut = false;

    private void logout() {
        if (isLoggingOut) return;
        isLoggingOut = true;
        Button btnLogout = findViewById(R.id.btnLogout);
        if (btnLogout != null) {
            btnLogout.setEnabled(false);
            btnLogout.setAlpha(0.5f);
            // UX-4 FIX: hiển thị feedback loading trên chính nút "Đăng xuất"
            // (đổi text + thêm spinner ký tự) để user trên mạng chậm biết app
            // đang xử lý chứ không bị đơ. Trước đây chỉ mờ đi.
            btnLogout.setText("⏳ Đang đăng xuất...");
        }
        // UX-4 FIX: hiển thị Snackbar không-thể-dismiss để user biết quá trình
        // đăng xuất đang chạy, sẽ tự biến mất khi finishLogout() finish() activity.
        final com.google.android.material.snackbar.Snackbar bar;
        try {
            bar = com.google.android.material.snackbar.Snackbar.make(
                    findViewById(android.R.id.content),
                    "Đang đăng xuất...",
                    com.google.android.material.snackbar.Snackbar.LENGTH_INDEFINITE);
            bar.show();
        } catch (Throwable t) {
            // Snackbar có thể null trên một số theme — ignore.
        }
        apiService.logout().enqueue(new Callback<ApiResponse<Void>>() {
            @Override public void onResponse(Call<ApiResponse<Void>> c, Response<ApiResponse<Void>> r) { finishLogout(); }
            @Override public void onFailure(Call<ApiResponse<Void>> c, Throwable t) { finishLogout(); }
        });
    }

    private void finishLogout() {
        session.clear();
        Intent i = new Intent(this, LoginActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }
}
