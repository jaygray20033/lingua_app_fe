package com.lingua.app.api;

import com.lingua.app.models.*;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.http.*;

public interface LinguaApiService {

    // ===== Auth =====
    @POST("auth/register")
    Call<ApiResponse<AuthData>> register(@Body Map<String, String> body);

    @POST("auth/login")
    Call<ApiResponse<AuthData>> login(@Body Map<String, String> body);

    @POST("auth/refresh")
    Call<ApiResponse<TokenData>> refreshToken(@Body Map<String, String> body);

    @POST("auth/logout")
    Call<ApiResponse<Void>> logout();

    @GET("auth/me")
    Call<ApiResponse<User>> getMe();

    @PUT("auth/profile")
    Call<ApiResponse<User>> updateProfile(@Body Map<String, Object> body);

    // 1.1 — Đổi mật khẩu
    @POST("auth/change-password")
    Call<ApiResponse<Object>> changePassword(@Body Map<String, String> body);

    // ===== Words =====
    // 1.7 — Backend supporte les deux: (language, search) et (lang, q). On garde la version standard.
    @GET("words")
    Call<ApiResponse<List<Word>>> getWords(
            @Query("language") String language,
            @Query("level") String level,
            @Query("search") String search,
            @Query("page") int page,
            @Query("limit") int limit);

    // 1.7 — Alias rétrocompatible /api/words/search?q=...&lang=...
    @GET("words/search")
    Call<ApiResponse<List<Word>>> searchWords(
            @Query("q") String query,
            @Query("lang") String lang,
            @Query("level") String level,
            @Query("page") int page,
            @Query("limit") int limit);

    @GET("words/{id}")
    Call<ApiResponse<Word>> getWordById(@Path("id") long id);

    @GET("words/character/{char}")
    Call<ApiResponse<com.lingua.app.models.Character>> getCharacter(@Path("char") String character);

    // ===== Flashcards (legacy deck) =====
    @GET("flashcards/decks")
    Call<ApiResponse<List<Deck>>> getDecks();

    @POST("flashcards/decks")
    Call<ApiResponse<Map<String, Object>>> createDeck(@Body Map<String, Object> body);

    @GET("flashcards/decks/{deckId}/cards")
    Call<ApiResponse<DeckCards>> getDeckCards(@Path("deckId") long deckId);

    @POST("flashcards/decks/{deckId}/cards")
    Call<ApiResponse<Map<String, Object>>> addCard(@Path("deckId") long deckId, @Body Map<String, String> body);

    @POST("flashcards/cards/{cardId}/review")
    Call<ApiResponse<ReviewResult>> reviewCard(@Path("cardId") long cardId, @Body Map<String, String> body);

    // 1.6 — Deck management (nouvelle API /my-decks)
    @GET("my-decks")
    Call<ApiResponse<List<Deck>>> getMyDecks();

    @POST("my-decks")
    Call<ApiResponse<Map<String, Object>>> createMyDeck(@Body Map<String, Object> body);

    @GET("my-decks/{deckId}/cards")
    Call<ApiResponse<DeckCards>> getMyDeckCards(@Path("deckId") long deckId);

    @POST("my-decks/{deckId}/cards")
    Call<ApiResponse<Map<String, Object>>> addMyDeckCard(@Path("deckId") long deckId, @Body Map<String, String> body);

    // 1.6 — POST /api/my-decks/{deckId}/start
    @POST("my-decks/{deckId}/start")
    Call<ApiResponse<Map<String, Object>>> startDeckSession(@Path("deckId") long deckId);

    @DELETE("my-decks/{deckId}")
    Call<ApiResponse<Object>> deleteDeck(@Path("deckId") long deckId);

    @DELETE("my-decks/{deckId}/cards/{cardId}")
    Call<ApiResponse<Object>> deleteDeckCard(@Path("deckId") long deckId, @Path("cardId") long cardId);

    // ===== SRS — Spaced Repetition System (1.5) =====
    @GET("srs/due")
    Call<ApiResponse<SrsDueResponse>> getSrsDue(@Query("deckId") Long deckId, @Query("limit") Integer limit);

    @POST("srs/{reviewId}/review")
    Call<ApiResponse<SrsReviewResult>> submitSrsReview(@Path("reviewId") long reviewId, @Body Map<String, Object> body);

    @GET("srs/decks")
    Call<ApiResponse<List<Deck>>> getSrsDecks();

    // ===== Favorites (1.3) =====
    @POST("favorites")
    Call<ApiResponse<Object>> addFavorite(@Body Map<String, Object> body);

    @DELETE("favorites/{type}/{itemId}")
    Call<ApiResponse<Object>> removeFavorite(@Path("type") String type, @Path("itemId") long itemId);

    @GET("favorites")
    Call<ApiResponse<List<Favorite>>> getFavorites(@Query("type") String type);

    // 1.2 — GET /api/favorites/check?type=word&itemId=123
    @GET("favorites/check")
    Call<ApiResponse<Map<String, Object>>> checkFavorite(
            @Query("type") String type,
            @Query("itemId") long itemId);

    // ===== Grammar (1.4) =====
    @GET("grammars")
    Call<ApiResponse<List<Grammar>>> getGrammars(
            @Query("language") String language,
            @Query("level") String level,
            @Query("search") String search);

    @GET("grammars/{id}")
    Call<ApiResponse<GrammarDetail>> getGrammarDetail(@Path("id") long id);

    // 3.7 — GET /api/grammars/{id}/exercises (luyện tập theo grammar point)
    @GET("grammars/{id}/exercises")
    Call<ApiResponse<List<Map<String, Object>>>> getGrammarExercises(@Path("id") long id);

    // ===== Gamification =====
    @GET("gamification/stats")
    Call<ApiResponse<GamificationStats>> getMyStats();

    @POST("gamification/hearts/lose")
    Call<ApiResponse<HeartsData>> loseHeart();

    @POST("gamification/hearts/refill")
    Call<ApiResponse<HeartsData>> refillHearts();

    @POST("gamification/xp")
    Call<ApiResponse<Object>> addXP(@Body Map<String, Object> body);

    @GET("gamification/leaderboard")
    Call<ApiResponse<List<LeaderboardEntry>>> getLeaderboard();

    @GET("gamification/leaderboard")
    Call<ApiResponse<List<LeaderboardEntry>>> getLeaderboardByPeriod(@Query("period") String period);

    @GET("gamification/achievements")
    Call<ApiResponse<List<Achievement>>> getAchievements();

    // 1.1 — GET /api/gamification/analytics
    @GET("gamification/analytics")
    Call<ApiResponse<Map<String, Object>>> getAnalytics();

    // 1.1 — POST /api/gamification/achievements/evaluate
    @POST("gamification/achievements/evaluate")
    Call<ApiResponse<Map<String, Object>>> evaluateAchievements();

    // 1.1 — GET /api/gamification/languages
    @GET("gamification/languages")
    Call<ApiResponse<List<Map<String, Object>>>> getLanguages();

    @GET("gamification/quests")
    Call<ApiResponse<List<DailyQuest>>> getDailyQuests();

    @POST("gamification/quests/{questId}/claim")
    Call<ApiResponse<Object>> claimQuest(@Path("questId") long questId);

    // 1.8 — Daily goal, streak freeze, quest event
    @PUT("gamification/daily-goal")
    Call<ApiResponse<Object>> setDailyGoal(@Body Map<String, Object> body);

    @POST("gamification/streak-freeze/buy")
    Call<ApiResponse<Object>> buyStreakFreeze();

    @POST("quests/event")
    Call<ApiResponse<Object>> triggerQuestEvent(@Body Map<String, Object> body);

    // ===== AI =====
    @POST("ai/explain")
    Call<ApiResponse<ExplanationResult>> explainGrammar(@Body Map<String, String> body);

    @GET("ai/scenarios")
    Call<ApiResponse<List<RoleplayScenario>>> getScenarios(
            @Query("language") String language,
            @Query("level") String level);

    @POST("ai/sessions")
    Call<ApiResponse<AISession>> startSession(@Body Map<String, Object> body);

    @POST("ai/explain-answer")
    Call<ApiResponse<ExplanationResult>> explainAnswer(@Body Map<String, String> body);

    @POST("ai/study-plan")
    Call<ApiResponse<StudyPlan>> generateStudyPlan(@Body Map<String, Object> body);

    // ===== Lessons =====
    @GET("lessons/{lessonId}/exercises")
    Call<ApiResponse<LessonData>> getLessonExercises(@Path("lessonId") long lessonId);

    @POST("lessons/{lessonId}/start")
    Call<ApiResponse<AttemptData>> startLesson(@Path("lessonId") long lessonId);

    @POST("lessons/attempts/{attemptId}/answer")
    Call<ApiResponse<AnswerResult>> submitAnswer(@Path("attemptId") long attemptId, @Body Map<String, Object> body);

    @POST("lessons/attempts/{attemptId}/complete")
    Call<ApiResponse<LessonResult>> completeLesson(@Path("attemptId") long attemptId);

    // 1.3 — GET /api/lessons/review-queue
    @GET("lessons/review-queue")
    Call<ApiResponse<List<Map<String, Object>>>> getReviewQueue();

    // ===== Courses =====
    @GET("courses")
    Call<ApiResponse<List<Course>>> getCourses();

    @GET("courses/{courseId}")
    Call<ApiResponse<Course>> getCourseDetail(@Path("courseId") long courseId);

    // 1.2 — Lộ trình học
    @GET("courses/{courseId}/path")
    Call<ApiResponse<CoursePath>> getCoursePath(@Path("courseId") long courseId);

    // 1.5 — GET /api/courses/{id}/enrollment (chi tiết tiến độ enroll)
    @GET("courses/{courseId}/enrollment")
    Call<ApiResponse<Map<String, Object>>> getEnrollmentDetail(@Path("courseId") long courseId);

    @POST("courses/{courseId}/enroll")
    Call<ApiResponse<Object>> enrollCourse(@Path("courseId") long courseId);

    @DELETE("courses/{courseId}/enroll")
    Call<ApiResponse<Object>> unenrollCourse(@Path("courseId") long courseId);

    // 1.2 — Khoá học của tôi
    @GET("enrollments")
    Call<ApiResponse<List<Enrollment>>> getMyEnrollments();

    // ===== Mock Tests (1.7) =====
    @GET("mock-tests")
    Call<ApiResponse<List<MockTest>>> getMockTestsV2(
            @Query("language") String language,
            @Query("type") String type);

    @GET("mock-tests/{id}")
    Call<ApiResponse<MockTestDetail>> getMockTestDetail(@Path("id") long id);

    @POST("mock-tests/{id}/submit")
    Call<ApiResponse<MockTestResult>> submitMockTest(@Path("id") long id, @Body Map<String, Object> body);

    // 1.4 — GET /api/mock-tests/attempts (lịch sử thi)
    @GET("mock-tests/attempts")
    Call<ApiResponse<List<Map<String, Object>>>> getMockTestAttempts();

    // Backward-compatible
    @GET("lessons/mock-tests")
    Call<ApiResponse<List<MockTest>>> getMockTests(
            @Query("language") String language,
            @Query("type") String type);

    // ===== Example sentences (Shadowing) =====
    @GET("words/{id}/examples")
    Call<ApiResponse<List<ExampleSentence>>> getWordExamples(@Path("id") long wordId);

    @GET("examples")
    Call<ApiResponse<List<ExampleSentence>>> getExampleSentences(
            @Query("language") String language,
            @Query("level") String level,
            @Query("limit") Integer limit);
}
