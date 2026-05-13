# 🌏 Lingua Android App — v2.1.0

Ứng dụng Android cho nền tảng học ngôn ngữ **Lingua**, tích hợp đầy đủ với backend Node.js/Express.

---

## 📦 Phiên bản

| | |
|---|---|
| **Tên app** | Lingua |
| **Package** | `com.lingua.app` |
| **versionCode / versionName** | 3 / 2.1.0 |
| **minSdk / targetSdk / compileSdk** | 26 / 34 / 34 |
| **Java** | 17 |

---

## 🏗 Kiến trúc

**MVP (Model-View-Presenter)** + Retrofit2 + OkHttp SSE + EncryptedSharedPreferences + WorkManager.

Toàn bộ giao diện dùng tiếng Việt.

---

## ✅ Tính năng đã triển khai (theo TODO)

### Phần 1 — API backend đã được gọi đầy đủ
- **Auth**: register / login / refresh / logout / me / update profile / **change-password** (`POST /auth/change-password`).
- **Courses & Enrollment**: list, detail, **course path** (`GET /courses/{id}/path`), **my enrollments** (`GET /enrollments`), enroll / **unenroll**.
- **Favorites**: add / remove / list theo type (WORD, GRAMMAR).
- **Grammar**: list / detail (kèm ví dụ + audio).
- **SRS**: due cards, review (đã sửa bug — dùng đúng endpoint `/srs/{reviewId}/review`), list decks.
- **Deck management**: xoá deck / xoá card.
- **Mock Test**: list (v2 + fallback v1), detail, submit + tính điểm.
- **Gamification**: stats, hearts, XP, leaderboard, achievements, quests, **daily-goal**, **streak-freeze/buy**, **quests/event** (kích hoạt sau mỗi bài học / SRS review).

### Phần 2 — Hoàn thiện các Activity sẵn có
- **MainActivity (Dashboard)**: thanh XP/streak/hearts/level, **mục tiêu XP hôm nay** (progress bar), **nút "Tiếp tục"** đưa đến bài học chưa hoàn thành, danh sách enrollment, preview 2 quest đầu, shortcut Grammar / MockTest / Favorites / Shadowing.
- **CourseDetailActivity**: hiển thị **lộ trình học** đầy đủ (locked / in-progress / completed), enroll / unenroll với confirm dialog.
- **FlashcardActivity**: refactor sang SRS API, deck picker, session stats (new / learning / review), card flip 3D.
- **ShadowingActivity**: lấy câu mẫu từ API, chấm điểm phát âm bằng Levenshtein, phát audio thật.
- **PracticeActivity**: hỗ trợ FILL_BLANK, MULTI_CHOICE, LISTEN, DICTATION, **SENTENCE_ORDER**, **MATCHING**; animation đúng/sai (bounce / shake), hệ thống hearts, kích hoạt quest event, kết quả đầy đủ.
- **ProfileActivity**: đổi mật khẩu, đặt mục tiêu XP, mua streak-freeze, đổi avatar, quick links → MyCourses / Favorites / **Statistics** / **Settings**.
- **VocabularyActivity**: phân trang infinite-scroll, bookmark API, mở chi tiết từ, audio thật + fallback TTS, **offline cache** (xem mục 4).

### Phần 3 — Màn hình mới đã thêm
- **OnboardingActivity** (4 bước): Welcome → chọn ngôn ngữ (JP/EN/ZH/KR) → mục tiêu XP/ngày → trình độ hiện tại + nút "Làm bài kiểm tra trình độ" → đẩy preferences lên backend qua `PUT /auth/profile` & `PUT /gamification/daily-goal`.
- **GrammarActivity** + **GrammarDetailActivity**: list theo language/level, search debounce, ví dụ + audio, favorite toggle.
- **MockTestActivity** + **MockTestDetailActivity** + **MockTestResultActivity**: list, làm bài có timer, navigation câu, submit + hiển thị PASS/FAIL.
- **StatisticsActivity**: Top tile (Total XP / Streak / Level), **biểu đồ cột XP 7 ngày** (BarChartView – Canvas thuần, không cần MPAndroidChart), **lịch streak 30 ngày** (StreakCalendarView kiểu GitHub contribution), 5 metrics chi tiết (từ vựng / bài học / accuracy / SRS reviewed / best streak).
- **FavoritesActivity**: tabs WORD / GRAMMAR, long-press để bỏ.
- **WordDetailActivity**: nghĩa, ví dụ, audio.
- **SettingsActivity**: dark mode (apply ngay), âm thanh / rung, **nhắc học hàng ngày** (giờ tuỳ chọn, lên lịch qua WorkManager), cảnh báo streak / thông báo thành tựu, đổi mục tiêu XP, đổi ngôn ngữ, autoplay audio, **bộ nhớ offline** (toggle + Xoá cache).

### Phần 4 — Tính năng nâng cao
- **Offline cache** (`utils/OfflineCache.java`): cache trang đầu tiên của Vocabulary theo (language, level), TTL 24h. Khi mất mạng → tự fallback từ cache + Toast "Đang xem dữ liệu offline". Có toggle bật/tắt và nút Xoá cache trong Settings.
- **Push Notifications** (`utils/NotificationScheduler.java`): 3 channel — Daily reminder / Streak alert / Achievement. Lên lịch nhắc hàng ngày bằng **WorkManager PeriodicWorkRequest** (sống sót reboot, không cần exact-alarm permission). API: `scheduleDaily(ctx, hour, min)`, `cancelDaily()`, `showStreakAlert()`, `showAchievementUnlocked()`.
- **UI/UX**: animation bounce khi mở khoá thành tựu (chỉ chạy 1 lần / item), shake khi sai, bounce khi đúng.
- **Leaderboard nâng cao**: 3 tabs — Tuần / Tháng / Mọi lúc, **highlight current user**, summary card "Bạn đang ở hạng #X — Y XP".

### Phần 5 — Đã có sẵn
Login/Register, token refresh, Vocabulary với TTS & search, AI Roleplay (SSE streaming + TTS giọng nói), Splash, session encrypted.

---

## 🗂 Cấu trúc package

```
com.lingua.app
├── activities/     # 28 activities
│   ├── SplashActivity              # Routing: Login / Onboarding / Main
│   ├── LoginActivity / RegisterActivity
│   ├── OnboardingActivity          # Onboarding 4 bước (ViewPager2 + Fragment)
│   ├── MainActivity                # Dashboard
│   ├── VocabularyActivity / WordDetailActivity
│   ├── PracticeActivity / LessonActivity / LessonResultActivity
│   ├── FlashcardActivity / SrsDeckPickerActivity
│   ├── ShadowingActivity
│   ├── AIRoleplayActivity
│   ├── CourseDetailActivity / MyCoursesActivity
│   ├── ProfileActivity
│   ├── LeaderboardActivity / AchievementsActivity / QuestsActivity
│   ├── FavoritesActivity
│   ├── GrammarActivity / GrammarDetailActivity
│   ├── MockTestActivity / MockTestDetailActivity / MockTestResultActivity
│   ├── StatisticsActivity          # ⭐ NEW
│   └── SettingsActivity            # ⭐ NEW
├── adapters/       # 7 adapters (Word, Achievement, Quest, Enrollment,
│                   #   Leaderboard, CoursePath, Grammar)
├── api/            # ApiClient, LinguaApiService (~75 endpoints), TokenAuthenticator
├── models/         # 37 data models
├── utils/
│   ├── SessionManager              # EncryptedSharedPreferences
│   ├── NotificationScheduler       # ⭐ WorkManager + 3 notification channels
│   └── OfflineCache                # ⭐ Vocabulary cache TTL 24h
└── views/          # ⭐ NEW
    ├── BarChartView                # Pure-Canvas bar chart (Statistics)
    └── StreakCalendarView          # Pure-Canvas 30-day calendar (Statistics)
```

**Tổng cộng**: 81 file Java (~8,200 dòng) + 37 layout XML.

---

## 🔧 Cấu hình

### Đổi URL backend
Trong `app/build.gradle`:
```groovy
// Debug (emulator → localhost host machine)
buildConfigField "String", "BASE_URL", "\"http://10.0.2.2:3000/api/\""

// Release (production)
buildConfigField "String", "BASE_URL", "\"https://your-server.com/api/\""
```

| Trường hợp | Địa chỉ |
|---|---|
| Emulator → backend trên máy bạn | `http://10.0.2.2:3000/api/` |
| Thiết bị thật cùng Wi-Fi | `http://192.168.x.x:3000/api/` |
| Production | `https://your-server.com/api/` |

---

## 🚀 Chạy thử

1. Mở `lingua-android` trong **Android Studio Hedgehog (2023.1.1)+**.
2. `File → Sync Project with Gradle Files`.
3. Chạy backend Node.js:
   ```bash
   cd lingua-backend
   docker-compose up -d
   npm install
   npm run migrate
   npm run dev
   ```
4. Run app trên emulator API 26+ hoặc thiết bị thật.

Lần đầu mở app sẽ vào màn **Onboarding 4 bước**; sau đó các lần sau Splash sẽ chuyển thẳng vào MainActivity.

---

## 🔐 Permissions

| Permission | Mục đích |
|---|---|
| `INTERNET` | API calls |
| `RECORD_AUDIO` | Shadowing + AI Roleplay voice |
| `READ_MEDIA_IMAGES` (33+) / `READ_EXTERNAL_STORAGE` (≤32) | Đổi avatar |
| `POST_NOTIFICATIONS` | Daily reminder / streak alert / achievement |
| `RECEIVE_BOOT_COMPLETED` | WorkManager khôi phục lịch nhắc sau reboot |
| `WAKE_LOCK` / `VIBRATE` | Hiển thị thông báo |

---

## 📚 Dependencies chính

| Library | Version | Mục đích |
|---|---|---|
| `androidx.appcompat` | 1.6.1 | Core |
| `com.google.android.material` | 1.11.0 | Material components, BottomNav, Tabs |
| `androidx.security:security-crypto` | 1.1.0-alpha06 | Encrypted token storage |
| `androidx.viewpager2` / `androidx.fragment` | 1.1.0 / 1.6.2 | Onboarding pager |
| `androidx.work:work-runtime` | 2.9.0 | Daily reminder scheduler |
| `retrofit2` + `converter-gson` | 2.9.0 | REST client |
| `okhttp3:logging-interceptor` | 4.12.0 | HTTP logging |
| `okhttp3:okhttp-sse` | 4.12.0 | SSE cho AI streaming |
| `gson` | 2.10.1 | JSON |

---

## 🎯 Backend API Endpoints (đã map trong `LinguaApiService`)

```
auth/*              register, login, refresh, logout, me, profile, change-password
words/*             list, detail, character lookup, examples
favorites/*         add / remove / list (WORD | GRAMMAR)
grammars/*          list, detail
flashcards/*        legacy decks, cards, review
my-decks/*          deck management (delete)
srs/*               due, review, decks   (⚠ bug đã fix)
gamification/*      stats, hearts, xp, leaderboard, achievements,
                    quests, claim, daily-goal, streak-freeze/buy
quests/event        ⭐ trigger sự kiện cập nhật quest (SRS_REVIEW, LESSON_COMPLETE…)
ai/*                explain, explain-answer, scenarios, sessions, study-plan
lessons/*           exercises, attempts (start/answer/complete), mock-tests
courses/*           list, detail, path, enroll / unenroll
enrollments         danh sách khoá học của tôi
mock-tests/*        list (v2), detail, submit
examples            câu ví dụ theo language + level (Shadowing)
```

---

## 🆕 Changelog v2.1.0

- ➕ Onboarding flow 4 bước.
- ➕ StatisticsActivity (bar chart + streak calendar — pure Canvas).
- ➕ SettingsActivity (dark mode, reminder, daily goal, language, offline cache).
- ➕ Leaderboard 3 periods + highlight current user.
- ➕ Daily reminder qua WorkManager + 3 notification channels.
- ➕ OfflineCache cho Vocabulary (TTL 24h, fallback khi mất mạng).
- ➕ Achievement bounce animation.
- 🛠 Fix: FlashcardActivity đã dùng đúng endpoint SRS.
- 🛠 Fix: `quests/event` được gọi sau LESSON_COMPLETE và SRS_REVIEW.
- 🛠 Fix: Pagination Vocabulary chính xác.
- 🛠 SplashActivity routing: Login / Onboarding / Main.

---

## 📝 Ghi chú phát triển

- Toàn bộ chart trong Statistics được vẽ thuần bằng Canvas — **không cần MPAndroidChart** (~2 MB), giúp app gọn nhẹ.
- OfflineCache hiện dùng SharedPreferences + Gson để giữ APK gọn. Có thể dễ dàng nâng cấp sang Room mà không đổi public API.
- Daily reminder dùng `WorkManager.PeriodicWorkRequest` (24h) — sống sót reboot, không cần `SCHEDULE_EXACT_ALARM` (Android 12+).
- Hiển thị avatar mới đang dùng `ImageView.setImageBitmap`. Nếu cần load avatar URL từ server, hãy thêm Glide / Coil.
