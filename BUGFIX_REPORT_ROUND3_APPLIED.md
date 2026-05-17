# ✅ BUGFIX REPORT ROUND 3 — Đã áp dụng

> **Ngày fix:** 2026-05-15
> **Phạm vi:** 17 bugs từ `BUGFIX_REPORT_ROUND3.md` (1 CRITICAL, 4 HIGH, 8 MEDIUM, 4 LOW)
> **Trạng thái:** Tất cả các bug đã được fix theo đúng đề xuất trong báo cáo QA.

---

## 📋 Tổng kết các bug đã fix

| # | Mức | ID | File | Mô tả ngắn |
|---|---|---|---|---|
| 1 | 🔴 CRITICAL | R3-C1 | `SettingsActivity.java` | `ApiClient.getApi` → `getService` (1 dòng — compile error blocker) |
| 2 | 🟠 HIGH | R3-H1 | `ProfileActivity.java` | Thêm `dailyXpGoal` vào body của `submitDailyGoal` |
| 3 | 🟠 HIGH | R3-H2 | `MockTestActivity.java` | Migrate sang `ActivityResultLauncher` (xóa `startActivityForResult`) |
| 4 | 🟠 HIGH | R3-H3 | `QuestsActivity.java` | Pre-validate quest + xử lý error response trong `onClaim` |
| 5 | 🟠 HIGH | R3-H4 | `MainActivity.java` | Request `POST_NOTIFICATIONS` runtime cho Android 13+ |
| 6 | 🟡 MEDIUM | R3-M1 | `PracticeActivity.java` | Restore `btnSubmit/btnSkip` visibility + re-enable input cho FILL_BLANK/SENTENCE_ORDER/MATCHING khi 4xx/5xx |
| 7 | 🟡 MEDIUM | R3-M2 | `MockTestDetailActivity.java` | `onSaveInstanceState` / `onRestoreInstanceState` để giữ câu trả lời qua rotation |
| 8 | 🟡 MEDIUM | R3-M3 | `ShadowingActivity.java` | Check `RECORD_AUDIO` permission trước khi gọi `MediaRecorder.prepare()` + handle permission result |
| 9 | 🟡 MEDIUM | R3-M4 | `VocabularyActivity.java` + `WordAdapter.java` | Sync `favoriteIds` của adapter ngay sau toggle thay vì đợi `onResume` |
| 10 | 🟡 MEDIUM | R3-M5 | `AIRoleplayActivity.java` | Reset `selectedScenarioId` khi user đổi ngôn ngữ |
| 11 | 🟡 MEDIUM | R3-M6 | `LessonActivity.java` | Validate `lessonId` (sentinel −1, không default về 1) + sửa redirect |
| 12 | 🟡 MEDIUM | R3-M7 | `MainActivity.java` | `configureContinueButton` xử lý null cho `lastAccessedAt` |
| 13 | 🟡 MEDIUM | R3-M8 | `OnboardingActivity.java` | Preserve selection khi `loadLanguagesFromBackend` callback trả về |
| 14 | 🔵 LOW | R3-L1 | `MockTestDetailActivity.java` | Fix typo `"Ấ không, tiếp tục"` → `"Không, tiếp tục học"` |
| 15 | 🔵 LOW | R3-L2 | `AIRoleplayActivity.java` | Kiểm tra `"giọng nói"` (đã đúng UTF-8 trong file hiện tại — không cần sửa byte) |
| 16 | 🔵 LOW | R3-L3 | `AIRoleplayActivity.java` | Append `"… ⚠️ (kết nối bị ngắt)"` khi SSE fail giữa chừng |
| 17 | 🔵 LOW | R3-L4 | `OnboardingActivity.java` | Thêm meta description cho `fr/es/de/vi/it/pt/ru/ar` |

---

## 📁 Danh sách file đã thay đổi (13 file)

```
app/src/main/java/com/lingua/app/activities/AIRoleplayActivity.java
app/src/main/java/com/lingua/app/activities/LessonActivity.java
app/src/main/java/com/lingua/app/activities/MainActivity.java
app/src/main/java/com/lingua/app/activities/MockTestActivity.java
app/src/main/java/com/lingua/app/activities/MockTestDetailActivity.java
app/src/main/java/com/lingua/app/activities/OnboardingActivity.java
app/src/main/java/com/lingua/app/activities/PracticeActivity.java
app/src/main/java/com/lingua/app/activities/ProfileActivity.java
app/src/main/java/com/lingua/app/activities/QuestsActivity.java
app/src/main/java/com/lingua/app/activities/SettingsActivity.java
app/src/main/java/com/lingua/app/activities/ShadowingActivity.java
app/src/main/java/com/lingua/app/activities/VocabularyActivity.java
app/src/main/java/com/lingua/app/adapters/WordAdapter.java
```

> Đã restore (không động) các file `FavoritesActivity.java`,
> `activity_favorites.xml`, `activity_practice.xml`, `screenshot.png` —
> chúng bị mark "modified" trong `git status` chỉ do unzip line-ending,
> không có thay đổi logic.

---

## 🔧 Chi tiết fix (theo bug)

### 🔴 R3-C1 — `ApiClient.getApi` → `getService`
**File:** `SettingsActivity.java:223`
```diff
- LinguaApiService api = ApiClient.getApi(getApplicationContext());
+ LinguaApiService api = ApiClient.getService(getApplicationContext());
```
**Hệ quả:** App build lại được.

---

### 🟠 R3-H1 — Thêm `dailyXpGoal` vào ProfileActivity
**File:** `ProfileActivity.java:261`
```diff
  Map<String, Object> body = new HashMap<>();
+ body.put("dailyXpGoal", xp); // primary key for backend
  body.put("dailyGoal", xp);
  body.put("xp", xp); // alternate key
```

---

### 🟠 R3-H2 — Migrate `MockTestActivity` sang `ActivityResultLauncher`
**File:** `MockTestActivity.java`

Thêm import:
```java
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
```

Khai báo launcher (field initializer):
```java
private final ActivityResultLauncher<Intent> detailLauncher =
        registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() != RESULT_OK || result.getData() == null) return;
                    String level = result.getData().getStringExtra("level");
                    if (level != null && !level.isEmpty()) {
                        Intent out = new Intent();
                        out.putExtra("level", level);
                        setResult(RESULT_OK, out);
                        finish();
                    }
                });
```

Trong click listener của MockAdapter:
```diff
- startActivityForResult(i, REQ_PLACEMENT_DETAIL);
+ detailLauncher.launch(i);
```

Method `onActivityResult` cũ đã được xóa hoàn toàn để tránh dual code path.

---

### 🟠 R3-H3 — Pre-validate quest trong `QuestsActivity.onClaim`
**File:** `QuestsActivity.java`

```java
public void onClaim(DailyQuest quest, int position) {
    if (quest.completed != 1) {
        Toast.makeText(this, "⏳ Chưa hoàn thành nhiệm vụ này!", Toast.LENGTH_SHORT).show();
        return;
    }
    if (quest.claimedAt != null) {
        Toast.makeText(this, "✅ Đã nhận thưởng rồi!", Toast.LENGTH_SHORT).show();
        return;
    }
    apiService.claimQuest(quest.id).enqueue(new Callback<ApiResponse<Object>>() {
        @Override public void onResponse(...) {
            if (resp.isSuccessful() && resp.body() != null && resp.body().isSuccess()) {
                // success — như cũ
            } else {
                final String msg = resp.body() != null && resp.body().getMessage() != null
                        ? resp.body().getMessage()
                        : "Không nhận được thưởng";
                runOnUiThread(() -> Toast.makeText(QuestsActivity.this, msg, ...).show());
            }
        }
        // ...
    });
}
```

---

### 🟠 R3-H4 — Request POST_NOTIFICATIONS runtime
**File:** `MainActivity.java`

Thêm imports cho `Manifest`, `PackageManager`, `Build`, `ContextCompat`, `ActivityResultLauncher`.

Thêm launcher + helper method:
```java
private final ActivityResultLauncher<String> notifPermLauncher =
        registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
            if (!granted) {
                NotificationScheduler.cancelDaily(getApplicationContext());
                Toast.makeText(this, "⚠️ Bạn đã từ chối quyền thông báo. ...", Toast.LENGTH_LONG).show();
            }
        });

private void ensureNotificationPermission() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return;
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED) return;
    // Tránh spam permission dialog nếu user đã deny vĩnh viễn.
    SharedPreferences p = getSharedPreferences(SettingsActivity.PREFS, MODE_PRIVATE);
    if (p.getBoolean("notif_perm_asked", false)
            && !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
        return;
    }
    p.edit().putBoolean("notif_perm_asked", true).apply();
    notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
}
```

Gọi trong `onCreate`:
```java
ensureNotificationPermission();
```

---

### 🟡 R3-M1 — Restore button visibility + re-enable input ở `PracticeActivity`
**File:** `PracticeActivity.java` (cả 2 nhánh `onResponse` failure và `onFailure`)

```java
runOnUiThread(() -> {
    answerLocked = false;
    btnSubmit.setVisibility(View.VISIBLE);
    if (btnSkip != null) btnSkip.setVisibility(View.VISIBLE);
    btnNext.setVisibility(View.GONE);
    // re-enable multi-choice (đã có sẵn)
    if (layoutMultiChoice != null && layoutMultiChoice.getVisibility() == View.VISIBLE) {
        for (int i = 0; i < layoutMultiChoice.getChildCount(); i++) {
            layoutMultiChoice.getChildAt(i).setEnabled(true);
        }
    }
    // re-enable input cho các exercise type khác (NEW)
    if (etFillBlank != null && etFillBlank.getVisibility() == View.VISIBLE) {
        etFillBlank.setEnabled(true);
    }
    if (layoutSentenceOrder != null && layoutSentenceOrder.getVisibility() == View.VISIBLE) {
        for (int i = 0; i < layoutSentenceOrder.getChildCount(); i++) {
            layoutSentenceOrder.getChildAt(i).setEnabled(true);
        }
    }
    if (layoutMatch != null && layoutMatch.getVisibility() == View.VISIBLE) {
        for (int i = 0; i < layoutMatch.getChildCount(); i++) {
            layoutMatch.getChildAt(i).setEnabled(true);
        }
    }
    Toast.makeText(...);
});
```

---

### 🟡 R3-M2 — `MockTestDetailActivity` save state qua rotation
**File:** `MockTestDetailActivity.java`

Thêm `onSaveInstanceState` / `onRestoreInstanceState`:
- Capture current `etAnswer` text trước khi serialize.
- Serialize `userAnswers` (HashMap) thành 2 parallel ArrayLists.
- Save thêm `currentIndex` và `startedAtMs`.

Patch `startTest()` để:
- Không overwrite `startedAtMs` đã restore (giữ timer chính xác).
- `showQuestion(currentIndex)` thay vì `showQuestion(0)`.

---

### 🟡 R3-M3 — Check RECORD_AUDIO permission trước khi record
**File:** `ShadowingActivity.java`

```java
private void startRecording() {
    if (isRecording) return;
    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
        Toast.makeText(this, "Cần cấp quyền micro để ghi âm", Toast.LENGTH_LONG).show();
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_MICROPHONE);
        return;
    }
    // ... existing code
}

@Override
public void onRequestPermissionsResult(int requestCode,
                                       @NonNull String[] permissions,
                                       @NonNull int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    if (requestCode == REQUEST_MICROPHONE) {
        if (grantResults.length == 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            if (btnRecord != null) btnRecord.setEnabled(false);
            if (tvStatus != null) tvStatus.setText("⚠️ Không có quyền micro — không thể luyện shadowing");
        } else {
            if (btnRecord != null) btnRecord.setEnabled(true);
        }
    }
}
```

---

### 🟡 R3-M4 — Sync `favoriteIds` sau toggle
**File:** `VocabularyActivity.java` + `WordAdapter.java`

Thêm vào WordAdapter:
```java
public void addFavoriteId(long id) { favoriteIds.add(id); }
public void removeFavoriteId(long id) { favoriteIds.remove(id); }
```

Gọi từ VocabularyActivity sau khi backend confirm:
```java
// Trong onResponse của addFavorite:
if (wordAdapter != null) wordAdapter.addFavoriteId(word.getId());

// Trong onResponse của removeFavorite:
if (wordAdapter != null) wordAdapter.removeFavoriteId(word.getId());
```

---

### 🟡 R3-M5 — Reset `selectedScenarioId` khi đổi ngôn ngữ
**File:** `AIRoleplayActivity.java`

```java
spinnerLanguage.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
    @Override public void onItemSelected(...) {
        selectedLanguage = langCodes[position];
        selectedScenarioId = null;   // NEW
        scenarios.clear();           // NEW
        loadScenarios();
    }
    // ...
});
```

---

### 🟡 R3-M6 — `LessonActivity` validate lessonId
**File:** `LessonActivity.java`

```diff
- long lessonId = getIntent().getLongExtra("lessonId", 1);
+ long lessonId = getIntent().getLongExtra("lessonId", -1L);
+ if (lessonId <= 0) {
+     Toast.makeText(this, "Bài học không hợp lệ", Toast.LENGTH_SHORT).show();
+     finish();
+     return;
+ }
```

---

### 🟡 R3-M7 — `configureContinueButton` null handling
**File:** `MainActivity.java`

```diff
  if (best == null) best = e;
- if (e.lastAccessedAt != null && best.lastAccessedAt != null
-         && e.lastAccessedAt.compareTo(best.lastAccessedAt) > 0) {
+ if (e.lastAccessedAt != null
+         && (best.lastAccessedAt == null
+             || e.lastAccessedAt.compareTo(best.lastAccessedAt) > 0)) {
      best = e;
  }
```

Thêm `continue;` ngay sau `best = e` đầu tiên để tránh re-evaluate ngay trong cùng iteration.

---

### 🟡 R3-M8 — Preserve language selection trong Onboarding
**File:** `OnboardingActivity.java`

Trong callback của `loadLanguagesFromBackend()`:
- Lưu `previouslySelectedCode = codes[selectedIndex]` TRƯỚC khi update array.
- Sau khi update, scan `codes` mới để tìm index của `previouslySelectedCode`.
- Restore `selectedIndex` + `adapter.selected` + `btnContinue.setEnabled()`.

---

### 🔵 R3-L1 — Fix typo
**File:** `MockTestDetailActivity.java:169`
```diff
- .setNegativeButton("Ấ không, tiếp tục", null)
+ .setNegativeButton("Không, tiếp tục học", null)
```

---

### 🔵 R3-L2 — Mojibake `"giọng nói"`
**File:** `AIRoleplayActivity.java:410`

Kiểm tra bằng `od -c` thấy text **đã đúng UTF-8** trong file zip nhận được
(`341 273 215` = `ọ`). Không có mojibake `��` cần fix. Có thể đã được sửa ở
round trước khi đóng zip. Đã verify file hiện tại OK.

---

### 🔵 R3-L3 — Append disconnect note vào tin nhắn dở dang
**File:** `AIRoleplayActivity.java`

```java
@Override
public void onFailure(EventSource eventSource, Throwable t, okhttp3.Response response) {
    runOnUiThread(() -> {
        if (aiResponse.length() == 0) {
            addMessage("AI", "Xin lỗi, đã có lỗi xảy ra. Hãy thử lại nhé.");
        } else {
            String partial = aiResponse.toString().trim();
            updateLastMessage(partial + " … ⚠️ (kết nối bị ngắt)");
        }
        tvStatus.setText("Phiên đang hoạt động");
    });
}
```

---

### 🔵 R3-L4 — Thêm metadata cho các ngôn ngữ tương lai
**File:** `OnboardingActivity.java`

Mở rộng switch case cho `fr`, `es`, `de`, `vi`, `it`, `pt`, `ru`, `ar` với
description meaningful (test certification + level range), và default
fallback dùng `"Học X từ cơ bản đến nâng cao"` thay vì chỉ `"Học X"`.

---

## 📝 Suggested commit messages

Theo format `fix(activity): BUG #R3-XXX: <description>` đề nghị trong báo cáo:

```
fix(settings): BUG #R3-C1: ApiClient.getApi → getService (compile error)
fix(profile): BUG #R3-H1: include dailyXpGoal in setDailyGoal payload
fix(mocktest): BUG #R3-H2: migrate MockTestActivity to ActivityResultLauncher
fix(quests): BUG #R3-H3: pre-validate quest and surface error in onClaim
fix(main): BUG #R3-H4: request POST_NOTIFICATIONS on Android 13+
fix(practice): BUG #R3-M1: restore button visibility and re-enable inputs on 4xx/5xx
fix(mocktest-detail): BUG #R3-M2: save/restore answers across rotation
fix(shadowing): BUG #R3-M3: verify RECORD_AUDIO before MediaRecorder.start()
fix(vocab): BUG #R3-M4: sync WordAdapter.favoriteIds immediately after toggle
fix(ai-roleplay): BUG #R3-M5: reset selectedScenarioId on language change
fix(lesson): BUG #R3-M6: validate lessonId before redirect
fix(main): BUG #R3-M7: handle null lastAccessedAt in configureContinueButton
fix(onboarding): BUG #R3-M8: preserve language selection on async refresh
fix(mocktest-detail): BUG #R3-L1: typo Ấ không → Không, tiếp tục học
fix(ai-roleplay): BUG #R3-L3: indicate partial AI response on SSE failure
fix(onboarding): BUG #R3-L4: meta description for additional languages
```

Hoặc gộp lại 1 commit duy nhất:
```
fix: apply 17 bug fixes from BUGFIX_REPORT_ROUND3 (R3-C1, R3-H1..H4, R3-M1..M8, R3-L1..L4)
```

---

## ⚠️ Lưu ý cho dev
1. **Chưa build verify** trên máy có Android SDK — đã check syntax/imports/
   bracket balance thủ công. Nên chạy `./gradlew assembleDebug` trước khi
   commit.
2. **Test cases theo `BUGFIX_REPORT_ROUND3.md` Phần 8** — đặc biệt:
   - Smoke test #10 (Settings → đổi daily goal không crash) — phải pass sau R3-C1.
   - Smoke test #9 (DB `user_languages.daily_xp_goal` updated) — phải pass sau R3-H1.
   - Smoke test #14 (Android 13+ chưa grant POST_NOTIFICATIONS) — phải pass sau R3-H4.
3. **Git: đã giữ nguyên** — workspace ở trạng thái "modified files, not staged".
   Bạn có thể tự `git add` các file mong muốn rồi commit và push.
