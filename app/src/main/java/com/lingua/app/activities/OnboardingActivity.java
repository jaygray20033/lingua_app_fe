package com.lingua.app.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.lingua.app.R;
import com.lingua.app.api.ApiClient;
import com.lingua.app.api.LinguaApiService;
import com.lingua.app.models.ApiResponse;
import com.lingua.app.models.User;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Onboarding flow shown the first time a user opens the app.
 *
 * Steps:
 *  1. Welcome.
 *  2. Pick the target language (ja / en / zh / ko).
 *  3. Pick a daily goal (5 / 10 / 15 / 20 minutes ⇄ 20 / 50 / 100 / 200 XP).
 *  4. Pick a starting level (or take a placement test → MockTestActivity).
 *
 * Persists preferences to SharedPreferences and pushes them to the backend
 * via PUT /api/auth/profile + PUT /api/gamification/daily-goal.
 */
public class OnboardingActivity extends AppCompatActivity {
    public static final String PREFS = "LinguaPrefs";
    public static final String KEY_ONBOARDED = "onboarded";
    public static final String KEY_TARGET_LANG = "target_language";
    public static final String KEY_TARGET_LEVEL = "target_level";
    public static final String KEY_DAILY_GOAL = "daily_goal_xp";

    private ViewPager2 pager;
    private LinearLayout pageIndicator;
    /** Shared state between fragments. */
    public String selectedLanguage = "ja";
    public String selectedLevel = "N5";
    public int selectedDailyGoal = 50;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);
        pager = findViewById(R.id.pager);
        pageIndicator = findViewById(R.id.pageIndicator);
        pager.setAdapter(new OnboardingPagerAdapter(this));

        // 6.1 FIX: chặn swipe để user không bỏ qua các bước bắt buộc (chọn
        // ngôn ngữ, chọn level…). Chỉ chuyển trang qua nút "Tiếp tục" trong
        // từng fragment.
        pager.setUserInputEnabled(false);

        // U15 FIX: build a row of dots reflecting the current page. Without
        // this the user had no idea how many onboarding steps remained.
        setupPageIndicator(pager.getAdapter().getItemCount());
        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override public void onPageSelected(int position) {
                super.onPageSelected(position);
                updatePageIndicator(position);
            }
        });
    }

    private void setupPageIndicator(int count) {
        if (pageIndicator == null) return;
        pageIndicator.removeAllViews();
        float density = getResources().getDisplayMetrics().density;
        int size = (int) (10 * density);
        int margin = (int) (6 * density);
        for (int i = 0; i < count; i++) {
            View dot = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMargins(margin, 0, margin, 0);
            dot.setLayoutParams(lp);
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(i == 0 ? 0xFF2E7D32 : 0x55000000);
            dot.setBackground(bg);
            pageIndicator.addView(dot);
        }
    }

    private void updatePageIndicator(int activeIndex) {
        if (pageIndicator == null) return;
        for (int i = 0; i < pageIndicator.getChildCount(); i++) {
            View dot = pageIndicator.getChildAt(i);
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(i == activeIndex ? 0xFF2E7D32 : 0x55000000);
            dot.setBackground(bg);
        }
    }

    public void goNext() {
        int next = pager.getCurrentItem() + 1;
        if (next < pager.getAdapter().getItemCount()) {
            pager.setCurrentItem(next, true);
        }
    }

    /**
     * 6.1 FIX: cho phép fragment quay lại trang trước qua nút "Quay lại".
     * Trước đây user chỉ có thể swipe (đã bị chặn) hoặc bấm Back của hệ điều
     * hành, không có cách rõ ràng trong UI để sửa lựa chọn ở bước trước.
     */
    public void goBack() {
        int prev = pager.getCurrentItem() - 1;
        if (prev >= 0) {
            pager.setCurrentItem(prev, true);
        }
    }

    /**
     * 6.1 FIX: override Back để map về goBack() khi không phải trang đầu.
     * Trang đầu: thoát onboarding (giữ behavior cũ).
     */
    @Override
    public void onBackPressed() {
        if (pager != null && pager.getCurrentItem() > 0) {
            goBack();
        } else {
            super.onBackPressed();
        }
    }

    /** Saves user choices locally + on the backend, then opens MainActivity. */
    public void finishOnboarding() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        prefs.edit()
                .putBoolean(KEY_ONBOARDED, true)
                .putString(KEY_TARGET_LANG, selectedLanguage)
                .putString(KEY_TARGET_LEVEL, selectedLevel)
                .putInt(KEY_DAILY_GOAL, selectedDailyGoal)
                .apply();

        // Push to backend (best-effort).
        LinguaApiService api = ApiClient.getService(this);
        Map<String, Object> profile = new HashMap<>();
        profile.put("targetLanguage", selectedLanguage);
        profile.put("currentLevel", selectedLevel);
        api.updateProfile(profile).enqueue(noopUser());

        // BUG B7 FIX: backend đọc field `dailyXpGoal` (chứ không phải `dailyGoal` / `xp`).
        // Nếu thiếu key này, daily_xp_goal sẽ không bao giờ được lưu vào DB.
        // (BUG #19 FIX: comment tiếng Pháp → tiếng Việt.)
        Map<String, Object> goal = new HashMap<>();
        goal.put("dailyXpGoal", selectedDailyGoal);
        goal.put("dailyGoal", selectedDailyGoal); // backward compat
        goal.put("xp", selectedDailyGoal);        // backward compat
        api.setDailyGoal(goal).enqueue(noopObject());

        Toast.makeText(this, "🎉 Sẵn sàng học rồi!", Toast.LENGTH_SHORT).show();
        Intent i = new Intent(this, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }

    private Callback<ApiResponse<User>> noopUser() {
        return new Callback<ApiResponse<User>>() {
            @Override public void onResponse(Call<ApiResponse<User>> c, Response<ApiResponse<User>> r) {}
            @Override public void onFailure(Call<ApiResponse<User>> c, Throwable t) {}
        };
    }

    private Callback<ApiResponse<Object>> noopObject() {
        return new Callback<ApiResponse<Object>>() {
            @Override public void onResponse(Call<ApiResponse<Object>> c, Response<ApiResponse<Object>> r) {}
            @Override public void onFailure(Call<ApiResponse<Object>> c, Throwable t) {}
        };
    }

    // -----------------------------------------------------------------------
    //  Pager
    // -----------------------------------------------------------------------
    static class OnboardingPagerAdapter extends FragmentStateAdapter {
        OnboardingPagerAdapter(@NonNull FragmentActivity fa) { super(fa); }
        @Override public int getItemCount() { return 4; }
        @NonNull @Override public Fragment createFragment(int pos) {
            switch (pos) {
                case 0: return new WelcomeFragment();
                case 1: return new LanguageFragment();
                case 2: return new GoalFragment();
                default: return new PlacementFragment();
            }
        }
    }

    // -----------------------------------------------------------------------
    //  Welcome
    // -----------------------------------------------------------------------
    public static class WelcomeFragment extends Fragment {
        @Override public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle b) {
            View v = inflater.inflate(R.layout.fragment_onboarding_welcome, container, false);
            v.findViewById(R.id.btnGetStarted).setOnClickListener(x ->
                    ((OnboardingActivity) requireActivity()).goNext());
            return v;
        }
    }

    // -----------------------------------------------------------------------
    //  Language
    // -----------------------------------------------------------------------
    public static class LanguageFragment extends Fragment {
        // 1.1 FIX: danh sách ngôn ngữ giờ tải từ /api/gamification/languages thay vì
        // hardcode 4 ngôn ngữ. Vẫn giữ array mặc định để fallback khi offline.
        private String[] codes = {"ja", "en", "zh", "ko"};
        private String[] flags = {"🇯🇵", "🇬🇧", "🇨🇳", "🇰🇷"};
        private String[] titles = {"Tiếng Nhật", "Tiếng Anh", "Tiếng Trung", "Tiếng Hàn"};
        private String[] subs = {"JLPT N5–N1, Hiragana, Kanji",
                "TOEIC, IELTS, A1–C2",
                "HSK 1–6, từ vựng giao tiếp",
                "TOPIK 1–6, hội thoại"};
        private int selectedIndex = -1;
        private ChoiceAdapter adapter;
        private RecyclerView rv;
        private Button btnContinue;

        @Override public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle b) {
            View v = inflater.inflate(R.layout.fragment_onboarding_language, container, false);
            rv = v.findViewById(R.id.recyclerLanguages);
            btnContinue = v.findViewById(R.id.btnContinue);
            rv.setLayoutManager(new LinearLayoutManager(getContext()));
            adapter = new ChoiceAdapter(flags, titles, subs, idx -> {
                selectedIndex = idx;
                btnContinue.setEnabled(true);
            });
            rv.setAdapter(adapter);

            // 1.1 FIX: tải danh sách languages từ backend (best-effort)
            loadLanguagesFromBackend();

            btnContinue.setOnClickListener(x -> {
                if (selectedIndex >= 0 && selectedIndex < codes.length) {
                    OnboardingActivity host = (OnboardingActivity) requireActivity();
                    host.selectedLanguage = codes[selectedIndex];
                    // Reset default level when language changes
                    switch (codes[selectedIndex]) {
                        case "en": host.selectedLevel = "A1"; break;
                        case "zh": host.selectedLevel = "HSK1"; break;
                        case "ko": host.selectedLevel = "TOPIK1"; break;
                        default: host.selectedLevel = "N5";
                    }
                    host.goNext();
                }
            });

            // 6.1 FIX: nút Quay lại trong fragment.
            View btnBack = v.findViewById(R.id.btnBack);
            if (btnBack != null) {
                btnBack.setOnClickListener(x -> ((OnboardingActivity) requireActivity()).goBack());
            }
            return v;
        }

        @Override public void onResume() {
            super.onResume();
            // 6.1 FIX: khi quay lại fragment (swipe back / Back button), re-enable
            // btnContinue nếu đã có lựa chọn trước đó. Trước đây btnContinue
            // luôn disabled khi quay lại → user phải chọn lại.
            if (btnContinue != null && adapter != null && adapter.getSelectedIndex() >= 0) {
                btnContinue.setEnabled(true);
            }
        }

        /**
         * 1.1 FIX: gọi GET /api/gamification/languages để lấy danh sách ngôn ngữ
         * động từ backend. Backend trả về [{ id, code, name, flag_emoji }, ...].
         */
        private void loadLanguagesFromBackend() {
            if (getContext() == null) return;
            LinguaApiService api = ApiClient.getService(getContext());
            api.getLanguages().enqueue(new Callback<ApiResponse<List<Map<String, Object>>>>() {
                @Override public void onResponse(Call<ApiResponse<List<Map<String, Object>>>> c,
                                                 Response<ApiResponse<List<Map<String, Object>>>> r) {
                    if (!r.isSuccessful() || r.body() == null || r.body().getData() == null) return;
                    List<Map<String, Object>> langs = r.body().getData();
                    if (langs.isEmpty()) return;
                    List<String> newCodes = new ArrayList<>();
                    List<String> newFlags = new ArrayList<>();
                    List<String> newTitles = new ArrayList<>();
                    List<String> newSubs = new ArrayList<>();
                    for (Map<String, Object> lg : langs) {
                        String code = String.valueOf(lg.getOrDefault("code", "?"));
                        String name = String.valueOf(lg.getOrDefault("name", code));
                        String flag = String.valueOf(lg.getOrDefault("flag_emoji",
                                lg.getOrDefault("flagEmoji", "🏳️")));
                        if ("null".equals(flag)) flag = "🏳️";
                        newCodes.add(code);
                        newFlags.add(flag);
                        newTitles.add(name);
                        String sub;
                        switch (code) {
                            case "ja": sub = "JLPT N5–N1, Hiragana, Kanji"; break;
                            case "en": sub = "TOEIC, IELTS, A1–C2"; break;
                            case "zh": sub = "HSK 1–6, từ vựng giao tiếp"; break;
                            case "ko": sub = "TOPIK 1–6, hội thoại"; break;
                            default: sub = "Học " + name; break;
                        }
                        newSubs.add(sub);
                    }
                    codes = newCodes.toArray(new String[0]);
                    flags = newFlags.toArray(new String[0]);
                    titles = newTitles.toArray(new String[0]);
                    subs = newSubs.toArray(new String[0]);
                    if (rv != null && getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            selectedIndex = -1;
                            if (btnContinue != null) btnContinue.setEnabled(false);
                            adapter = new ChoiceAdapter(flags, titles, subs, idx -> {
                                selectedIndex = idx;
                                if (btnContinue != null) btnContinue.setEnabled(true);
                            });
                            rv.setAdapter(adapter);
                        });
                    }
                }
                @Override public void onFailure(Call<ApiResponse<List<Map<String, Object>>>> c, Throwable t) {
                    // Im lặng — fallback sang 4 ngôn ngữ hardcode
                }
            });
        }
    }

    // -----------------------------------------------------------------------
    //  Daily goal
    // -----------------------------------------------------------------------
    public static class GoalFragment extends Fragment {
        private final int[] xpValues = {20, 50, 100, 200};
        private final String[] icons = {"😌", "🙂", "💪", "🔥"};
        private final String[] titles = {"Thoải mái — 5 phút/ngày",
                "Bình thường — 10 phút/ngày",
                "Cố gắng — 15 phút/ngày",
                "Tham vọng — 20 phút/ngày"};
        private final String[] subs = {"20 XP / ngày", "50 XP / ngày", "100 XP / ngày", "200 XP / ngày"};
        private int selectedIndex = -1;
        private Button btn;
        private ChoiceAdapter adapter;

        @Override public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle b) {
            View v = inflater.inflate(R.layout.fragment_onboarding_goal, container, false);
            RecyclerView rv = v.findViewById(R.id.recyclerGoals);
            btn = v.findViewById(R.id.btnContinue);
            rv.setLayoutManager(new LinearLayoutManager(getContext()));
            adapter = new ChoiceAdapter(icons, titles, subs, idx -> {
                selectedIndex = idx;
                btn.setEnabled(true);
            });
            rv.setAdapter(adapter);
            btn.setOnClickListener(x -> {
                if (selectedIndex >= 0) {
                    OnboardingActivity host = (OnboardingActivity) requireActivity();
                    host.selectedDailyGoal = xpValues[selectedIndex];
                    host.goNext();
                }
            });

            // 6.1 FIX: nút Quay lại trong GoalFragment.
            View btnBack = v.findViewById(R.id.btnBack);
            if (btnBack != null) {
                btnBack.setOnClickListener(x -> ((OnboardingActivity) requireActivity()).goBack());
            }
            return v;
        }

        @Override public void onResume() {
            super.onResume();
            // 6.1 FIX: re-enable btn khi quay lại trang đã chọn.
            if (btn != null && adapter != null && adapter.getSelectedIndex() >= 0) {
                btn.setEnabled(true);
            }
        }
    }

    // -----------------------------------------------------------------------
    //  Placement / starting level
    // -----------------------------------------------------------------------
    public static class PlacementFragment extends Fragment {
        // BUG #20 FIX: request code cho placement test result.
        private static final int REQ_PLACEMENT_TEST = 7321;
        private int selectedIndex = -1;
        private String[] currentLevels = {"N5", "N4", "N3", "N2", "N1"};
        private Button btnFinish;
        private ChoiceAdapter adapter;

        /**
         * BUG #20 FIX: nhận kết quả placement test từ MockTestActivity. Khi
         * MockTestActivity finish với RESULT_OK + extra "level", ta highlight
         * mục tương ứng và cập nhật host.selectedLevel để finishOnboarding()
         * dùng đúng level từ kết quả test (thay vì giá trị mặc định "N5"/"A1"...).
         */
        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            super.onActivityResult(requestCode, resultCode, data);
            if (requestCode != REQ_PLACEMENT_TEST || resultCode != android.app.Activity.RESULT_OK || data == null) {
                return;
            }
            String suggested = data.getStringExtra("level");
            if (suggested == null || suggested.isEmpty()) return;

            OnboardingActivity host = (OnboardingActivity) requireActivity();
            host.selectedLevel = suggested;

            // Tìm vị trí của level trong currentLevels để highlight.
            for (int i = 0; i < currentLevels.length; i++) {
                if (suggested.equalsIgnoreCase(currentLevels[i])) {
                    selectedIndex = i;
                    if (adapter != null) {
                        int prev = adapter.selected;
                        adapter.selected = i;
                        if (prev >= 0) adapter.notifyItemChanged(prev);
                        adapter.notifyItemChanged(i);
                    }
                    if (btnFinish != null) btnFinish.setEnabled(true);
                    Toast.makeText(getContext(),
                            "📊 Bài kiểm tra gợi ý trình độ: " + suggested,
                            Toast.LENGTH_LONG).show();
                    return;
                }
            }
            // Level không nằm trong danh sách → chỉ thông báo
            Toast.makeText(getContext(),
                    "📊 Trình độ gợi ý: " + suggested + " (đã lưu)",
                    Toast.LENGTH_LONG).show();
        }

        @Override public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle b) {
            View v = inflater.inflate(R.layout.fragment_onboarding_placement, container, false);
            RecyclerView rv = v.findViewById(R.id.recyclerLevels);
            btnFinish = v.findViewById(R.id.btnFinish);
            Button btnTest = v.findViewById(R.id.btnPlacementTest);

            OnboardingActivity host = (OnboardingActivity) requireActivity();
            switch (host.selectedLanguage) {
                case "en": currentLevels = new String[]{"A1", "A2", "B1", "B2", "C1", "C2"}; break;
                case "zh": currentLevels = new String[]{"HSK1", "HSK2", "HSK3", "HSK4", "HSK5", "HSK6"}; break;
                case "ko": currentLevels = new String[]{"TOPIK1", "TOPIK2", "TOPIK3", "TOPIK4", "TOPIK5", "TOPIK6"}; break;
                default: currentLevels = new String[]{"N5", "N4", "N3", "N2", "N1"};
            }

            String[] icons = new String[currentLevels.length];
            String[] subs = new String[currentLevels.length];
            for (int i = 0; i < currentLevels.length; i++) {
                icons[i] = i == 0 ? "🌱" : (i == currentLevels.length - 1 ? "🏆" : "📘");
                subs[i] = i == 0 ? "Mới bắt đầu — chưa biết gì"
                        : (i == currentLevels.length - 1 ? "Thông thạo — gần như bản ngữ"
                        : "Đã có nền tảng cơ bản");
            }

            rv.setLayoutManager(new LinearLayoutManager(getContext()));
            adapter = new ChoiceAdapter(icons, currentLevels, subs, idx -> {
                selectedIndex = idx;
                btnFinish.setEnabled(true);
            });
            rv.setAdapter(adapter);

            btnTest.setOnClickListener(x -> {
                Toast.makeText(getContext(),
                        "Đang mở bài kiểm tra trình độ. Sau khi nộp bài, kết quả sẽ được dùng để gợi ý lộ trình.",
                        Toast.LENGTH_LONG).show();
                Intent i = new Intent(getContext(), MockTestActivity.class);
                i.putExtra("placement", true);
                i.putExtra("language", host.selectedLanguage);
                // BUG #20 FIX: dùng startActivityForResult để nhận lại level đề
                // xuất từ MockTestActivity. Trước đây kết quả test không rõ ràng
                // được cập nhật vào selectedLevel trước khi gọi finishOnboarding().
                startActivityForResult(i, REQ_PLACEMENT_TEST);
            });

            btnFinish.setOnClickListener(x -> {
                if (selectedIndex >= 0) {
                    host.selectedLevel = currentLevels[selectedIndex];
                    host.finishOnboarding();
                }
            });

            // 6.1 FIX: nút Quay lại trong PlacementFragment.
            View btnBack = v.findViewById(R.id.btnBack);
            if (btnBack != null) {
                btnBack.setOnClickListener(x -> ((OnboardingActivity) requireActivity()).goBack());
            }
            return v;
        }

        @Override public void onResume() {
            super.onResume();
            // 6.1 FIX: re-enable nút Hoàn thành khi quay lại trang đã chọn.
            if (btnFinish != null && adapter != null && adapter.getSelectedIndex() >= 0) {
                btnFinish.setEnabled(true);
            }
        }
    }

    // -----------------------------------------------------------------------
    //  Reusable list adapter for onboarding choices
    // -----------------------------------------------------------------------
    interface OnChoiceSelected { void onSelected(int index); }

    static class ChoiceAdapter extends RecyclerView.Adapter<ChoiceAdapter.VH> {
        final String[] icons, titles, subs;
        final OnChoiceSelected listener;
        int selected = -1;

        ChoiceAdapter(String[] icons, String[] titles, String[] subs, OnChoiceSelected l) {
            this.icons = icons; this.titles = titles; this.subs = subs; this.listener = l;
        }

        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_onboarding_choice, parent, false);
            return new VH(v);
        }

        @Override public void onBindViewHolder(@NonNull VH h, int pos) {
            h.tvIcon.setText(icons[pos]);
            h.tvTitle.setText(titles[pos]);
            h.tvSubtitle.setText(subs[pos]);
            h.tvCheck.setVisibility(pos == selected ? View.VISIBLE : View.INVISIBLE);
            // 7.5 FIX: dung mau dark-mode aware tu resources (values + values-night)
            // thay vi hardcode 0xFFD7FFB8 / 0xFFFFFFFF. Truoc day o dark mode item
            // luon trang choi va lua chon kho phan biet.
            h.itemView.setBackgroundColor(androidx.core.content.ContextCompat.getColor(
                    h.itemView.getContext(),
                    pos == selected ? R.color.onboarding_choice_selected : R.color.onboarding_choice_default));
            h.itemView.setOnClickListener(v -> {
                int prev = selected;
                selected = pos;
                if (prev >= 0) notifyItemChanged(prev);
                notifyItemChanged(pos);
                listener.onSelected(pos);
            });
        }

        /**
         * 6.1 FIX: cho phép fragment khôi phục lựa chọn đã chọn khi user
         * swipe back. Trước đây quay lại trang 2 sau khi đã chọn → adapter
         * vẫn giữ `selected` nhưng `btnContinue` không re-enable vì callback
         * không fire lại. Gọi getter này từ fragment sau khi setAdapter để
         * kiểm tra trạng thái.
         */
        int getSelectedIndex() { return selected; }

        @Override public int getItemCount() { return titles.length; }

        static class VH extends RecyclerView.ViewHolder {
            final TextView tvIcon, tvTitle, tvSubtitle, tvCheck;
            VH(@NonNull View v) {
                super(v);
                tvIcon = v.findViewById(R.id.tvIcon);
                tvTitle = v.findViewById(R.id.tvTitle);
                tvSubtitle = v.findViewById(R.id.tvSubtitle);
                tvCheck = v.findViewById(R.id.tvCheck);
            }
        }
    }
}
