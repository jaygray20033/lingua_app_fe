# 🛠️ Changelog — Sửa lỗi Lingua Android & Backend

> Ngày: 2026-05-08
> Phạm vi: `lingua-backend` (Node.js/Express) + `lingua-android` (Java/Retrofit)

Tài liệu này tổng hợp **tất cả các bug đã được sửa** theo file `TODO_lingua_android_updated.md`.

---

## 🔴 PHẦN 5 — BUG CRITICAL (đã sửa)

### ✅ B1. Mất 2 tim khi trả lời sai
- **File:** `lingua-backend/src/controllers/lesson.controller.js`
- **Sửa:** Bỏ `await gamService.loseHeart(req.user.id)` trong `submitAnswer`. Việc trừ tim hoàn toàn do Android xử lý qua `POST /api/gamification/hearts/lose`.

### ✅ B2. `loseHeart` / `refillHearts` / `addXP` trả về số nguyên thay vì object
- **File:** `lingua-backend/src/controllers/gamification.controller.js`
- **Sửa:**
  - `loseHeart` → `res.json({ success: true, data: { hearts: result } })`
  - `refillHearts` → `res.json({ success: true, data: { hearts: result } })`
  - `addXP` → trả về `{ added, totalXp, level }` (object đầy đủ)
- **Lý do:** Gson Android parse `HeartsData.hearts`, cần object chứ không phải số nguyên rời.

### ✅ B3. Login response thiếu `userId` ở root
- **File:** `lingua-backend/src/services/auth.service.js`
- **Sửa:** Thêm `userId: user.id` ở cấp root của object trả về trong `login()`. Đồng bộ với `register()`.
- **Lý do:** `LoginActivity.java` đọc `data.userId` (flat) — trước đây trả về 0 → mọi nơi dùng `userId` đều sai.

### ✅ B4. Quest event sai key (Android gửi `type/amount`, backend đọc `event/value`)
- **File 1:** `lingua-android/.../FlashcardActivity.java` (`triggerSrsQuestEvent`)
- **File 2:** `lingua-android/.../PracticeActivity.java` (`triggerLessonCompleteQuest`)
- **Sửa:** Đổi `body.put("type", ...)` → `body.put("event", ...)` và `body.put("amount", ...)` → `body.put("value", ...)`.
- **Lý do:** Trước đây quest không bao giờ tăng tiến vì backend luôn nhận `event = undefined`.

### ✅ B5. Route `/words/character/:char` bị che bởi `/:id`
- **File:** `lingua-backend/src/routes/words.routes.js`
- **Sửa:** Di chuyển `router.get('/character/:char', ...)` lên TRƯỚC `router.get('/:id', ...)`.
- **Lý do:** Express xử lý route theo thứ tự. `/:id` khớp trước → `getWordById("character")` → 404.

### ✅ B6. `getDueCards` không trả về `fr.id` — `reviewId` luôn = 0
- **File:** `lingua-backend/src/services/srs.service.js`
- **Sửa:** Thêm `fr.id AS reviewId` vào SELECT trong `getDueCards`.
- **Lý do:** Android cần `reviewId` để gọi `POST /api/srs/{reviewId}/review`. Nếu thiếu, Android fallback về legacy → SRS mới không hoạt động.

### ✅ B7. Daily goal Onboarding không sync lên server
- **File:** `lingua-android/.../OnboardingActivity.java` (`finishOnboarding`)
- **Sửa:** Thêm `goal.put("dailyXpGoal", ...)` (giữ thêm `dailyGoal` & `xp` cho backward compat).
- **Lý do:** Backend `setDailyGoal` đọc `dailyXpGoal`, Android trước đây chỉ gửi `dailyGoal` & `xp`.

---

## 🟠 PHẦN 5 — BUG HIGH (đã sửa)

### ✅ B8. `getLeaderboard` bỏ qua query param `?period=`
- **File:** `lingua-backend/src/controllers/gamification.controller.js`
- **Sửa:**
  - Cache key tách theo period: `leaderboard:weekly`, `leaderboard:monthly`, `leaderboard:all`.
  - `weekly` = 7 ngày qua, `monthly` = 30 ngày qua (`SUM(daily_xp_logs.xp_gained)`).
  - `all` = `total_xp` từ `user_gamification`.
- **Lý do:** Trước đây cả 3 tab đều trả về cùng dữ liệu vì chung cache key.

### ✅ B9. `submitAnswer` / `completeLesson` không kiểm tra ownership của attempt
- **File:** `lingua-backend/src/controllers/lesson.controller.js`
- **Sửa:** Thêm `WHERE id = ? AND user_id = req.user.id` ở cả 2 hàm. Trả 403 nếu không khớp.
- **Lý do:** Privilege escalation — user A có thể submit câu trả lời vào attempt của user B nếu biết `attemptId`.

### ✅ B10. `startLesson` không kiểm tra enrollment
- **File:** `lingua-backend/src/controllers/lesson.controller.js`
- **Sửa:** Copy logic kiểm tra enrollment từ `getLessonExercises` vào đầu `startLesson`. Trả 403 nếu chưa enroll (và không phải free preview).
- **Lý do:** User có thể bắt đầu bài học của khóa chưa mua → tạo attempt rác.

### ✅ B11. `WordDetail` / `GrammarDetail` load toàn bộ favorites để check 1 item
- **File 1:** `lingua-android/.../WordDetailActivity.java` (`checkFavoriteState`)
- **File 2:** `lingua-android/.../GrammarDetailActivity.java` (`checkFavoriteState`)
- **Sửa:** Dùng endpoint `GET /api/favorites/check?type=WORD&itemId=...` thay vì tải toàn bộ list. Có fallback về cách cũ nếu endpoint không khả dụng.
- **Lý do:** Performance — 500 favorites = 500 dòng tải về chỉ để check 1 cái.

### ✅ B12. `ai.controller.js > chatStream` lưu message trước khi verify session
- **File:** `lingua-backend/src/controllers/ai.controller.js`
- **Sửa:** Đảo thứ tự — verify session ownership TRƯỚC khi `INSERT INTO ai_messages`.
- **Lý do:** Lỗ hổng bảo mật — attacker có thể inject message vào session người khác trước khi nhận 404.

---

## 🟡 PHẦN 5 — BUG MEDIUM (đã sửa)

### ✅ B13. `ShadowingActivity` hardcode tiếng Nhật N4
- **File:** `lingua-android/.../ShadowingActivity.java` (`loadSentences`)
- **Sửa:** Đọc `target_language` & `target_level` từ `SharedPreferences` (lưu khi onboarding). Có default phù hợp cho từng ngôn ngữ.
- **Lý do:** User học tiếng Anh/Trung/Hàn vẫn thấy câu tiếng Nhật.

### ✅ 7.1. `PracticeActivity.playAudio()` — MediaPlayer leak
- **File:** `lingua-android/.../PracticeActivity.java`
- **Sửa:**
  - Thêm field `private MediaPlayer currentMediaPlayer` để giữ instance hiện tại.
  - Trong `playAudio()`: release MediaPlayer cũ trước khi tạo mới; đăng ký `setOnCompletionListener` & `setOnErrorListener` để tự release sau khi phát xong / lỗi; release thủ công trong `catch` nếu `setDataSource` / `prepareAsync` ném exception.
  - Đổi tên parameter lambda từ `MediaPlayer` (shadow tên class) → `player`.
  - Trong `onDestroy()`: gọi `releaseCurrentMediaPlayer()` để giải phóng native audio handle khi user thoát màn hình giữa chừng.
- **Lý do:** Mỗi click "🔊 Nghe" tạo MediaPlayer mới không release → memory/resource leak, crash trên thiết bị RAM thấp.

### ✅ B14. `StatisticsActivity > synthesizeWeekly` dùng `Math.random()`
- **File:** `lingua-android/.../StatisticsActivity.java`
- **Sửa:**
  - Bỏ hoàn toàn `Math.random()`. Dùng pattern cố định `{0.7, 0.9, 0.8, 1.0, 0.9, 1.2, 1.1}`.
  - **Đồng thời thêm gọi `GET /api/gamification/analytics`** (TODO 1.1, 3.4) → ưu tiên dữ liệu thực `dailyXp[]` từ server thay vì fallback.
  - **Đồng thời gọi `POST /api/gamification/achievements/evaluate`** (TODO 1.1) — best-effort kích hoạt đánh giá achievement khi user vào màn hình thống kê.
- **Lý do:** Trước đây xoay màn hình → biểu đồ XP thay đổi giá trị, gây nhầm lẫn cho user.

---

## 🔴 PHẦN 1 + 2 — Các TODO đã được implement đầy đủ

> Đây là các endpoint/feature **đã có sẵn trong codebase** (do cập nhật trước đó của bạn). Tài liệu TODO của bạn đã được cập nhật và xác nhận:

### Phần 1 — Backend có, Android nay đã gọi
- ✅ 1.1 `GET /gamification/analytics` — hiện được StatisticsActivity gọi
- ✅ 1.1 `POST /gamification/achievements/evaluate` — StatisticsActivity gọi tự động
- ✅ 1.1 `GET /gamification/languages` — endpoint trong LinguaApiService
- ✅ 1.2 `GET /favorites/check` — WordDetail & GrammarDetail nay dùng (B11)
- ✅ 1.3 `GET /lessons/review-queue` — endpoint trong LinguaApiService
- ✅ 1.4 `GET /mock-tests/attempts` — endpoint trong LinguaApiService
- ✅ 1.5 `GET /courses/{id}/enrollment` — endpoint trong LinguaApiService
- ✅ 1.6 `POST /my-decks/{deckId}/start` — endpoint trong LinguaApiService
- ✅ 1.6 Migration sang `/my-decks/*` + `/srs/*` — FlashcardActivity dùng `getSrsDue` + `submitSrsReview`
- ✅ 1.7 Word search params — backend hỗ trợ cả `(language, search)` và `(lang, q)`

### Phần 2 — Backend nay đã có
- ✅ 2.1 `POST /auth/change-password` — `auth.controller.js > changePassword`
- ✅ 2.2 `POST /gamification/hearts/lose` — `gamification.controller.js > loseHeart`
- ✅ 2.2 `POST /gamification/xp` — `gamification.controller.js > addXP`
- ✅ 2.3 `POST /lessons/{id}/start` → `attemptId` — `lesson.controller.js > startLesson`
- ✅ 2.3 `POST /lessons/attempts/{id}/answer` — `lesson.controller.js > submitAnswer`
- ✅ 2.3 `POST /lessons/attempts/{id}/complete` — `lesson.controller.js > completeLesson`
- ✅ 2.3 `POST /lessons/{id}/complete` (legacy) — `completeLessonLegacy` cho backward compat
- ✅ 2.4 `POST /ai/explain` — `ai.controller.js > explainGrammar`
- ✅ 2.4 `GET /ai/scenarios` — `ai.controller.js > getScenarios`
- ✅ 2.4 `POST /ai/sessions` — `ai.controller.js > startSession`
- ✅ 2.4 `POST /ai/explain-answer` — `ai.controller.js > explainAnswer`
- ✅ 2.4 `POST /ai/study-plan` — `ai.controller.js > generateStudyPlan`
- ✅ 2.5 `GET /examples` — `examples.controller.js > list`
- ✅ 2.5 `GET /words/{id}/examples` — `words.controller.js > getWordExamples`

---

## 📝 Các file đã được chỉnh sửa

### Backend (`lingua-backend/`)
| File | Sửa lỗi |
|---|---|
| `src/controllers/lesson.controller.js` | B1, B9, B10 |
| `src/controllers/gamification.controller.js` | B2, B8 |
| `src/controllers/ai.controller.js` | B12 |
| `src/services/auth.service.js` | B3 |
| `src/services/srs.service.js` | B6 |
| `src/routes/words.routes.js` | B5 |

### Android (`lingua-android/`)
| File | Sửa lỗi |
|---|---|
| `app/src/main/java/com/lingua/app/activities/PracticeActivity.java` | B4, 7.1 |
| `app/src/main/java/com/lingua/app/activities/FlashcardActivity.java` | B4 |
| `app/src/main/java/com/lingua/app/activities/OnboardingActivity.java` | B7 |
| `app/src/main/java/com/lingua/app/activities/WordDetailActivity.java` | B11 |
| `app/src/main/java/com/lingua/app/activities/GrammarDetailActivity.java` | B11 |
| `app/src/main/java/com/lingua/app/activities/ShadowingActivity.java` | B13 |
| `app/src/main/java/com/lingua/app/activities/StatisticsActivity.java` | B14 + TODO 1.1 |

---

## 🧪 Cách test sau khi áp dụng fix

### Backend
```bash
cd lingua-backend
node -c src/controllers/lesson.controller.js     # syntax OK
node -c src/controllers/gamification.controller.js
node -c src/controllers/ai.controller.js
node -c src/services/auth.service.js
node -c src/services/srs.service.js
node -c src/routes/words.routes.js

npm install
npm run dev    # khởi động server với nodemon (cần MySQL + Redis)
```

### Android
1. Mở `lingua-android/` trong Android Studio.
2. Đảm bảo `app/build.gradle > dependencies` có Retrofit + Gson + OkHttp.
3. Chỉnh `ApiClient.java > BASE_URL` trỏ về backend (mặc định `http://10.0.2.2:3000/api/` cho emulator).
4. **Build → Make Project (Ctrl+F9)** để verify cú pháp.
5. **Run → Run app** trên emulator hoặc thiết bị thật.

### Verify các bug đã được fix
- **B1:** Trả lời sai 1 câu → tim giảm đúng 1 (không phải 2).
- **B2:** UI hearts ❤️ hiển thị đúng số tim sau khi mất/refill.
- **B3:** Sau login, `SessionManager.getUserId()` ≠ 0; leaderboard highlight đúng dòng của user.
- **B4:** Hoàn thành SRS hoặc bài học → quest tiến độ tăng (kiểm tra `daily_quests.progress` trong DB).
- **B5:** Mở từ Kanji trong WordDetail → load được ký tự + nét vẽ.
- **B6:** Vào Flashcard → review tăng `interval_days` đúng theo SM-2 (không fallback legacy).
- **B7:** Onboarding xong → kiểm tra `user_languages.daily_xp_goal` = giá trị đã chọn.
- **B8:** Leaderboard → 3 tab khác nhau.
- **B9:** Thử submit `attemptId` của user khác → 403.
- **B10:** Vào lesson chưa enroll (không free preview) → 403.
- **B11:** Mở WordDetail → Network log chỉ thấy 1 request `/favorites/check`.
- **B12:** AI roleplay với `sessionId` không hợp lệ → 404 và **không** lưu message.
- **B13:** Đổi onboarding sang tiếng Anh → vào Shadowing → câu là tiếng Anh.
- **B14:** Vào Statistics, xoay màn hình nhiều lần → biểu đồ XP **không đổi**.
- **7.1:** Bấm nút "🔊 Nghe" liên tục 10+ lần → không tăng memory rò rỉ; thoát PracticeActivity giữa chừng khi audio đang phát → không crash, không lộ native AudioTrack handle (kiểm tra qua Android Studio Profiler).

---

*Cập nhật: 08/05/2026 — Tất cả 14 bug từ TODO_lingua_android_updated.md đã được xử lý.*
