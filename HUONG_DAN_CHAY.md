# 📱 HƯỚNG DẪN CHẠY LINGUA ANDROID APP

## 🎯 Yêu cầu

- **Android Studio** Hedgehog (2023.1.1) trở lên
- **JDK** 17
- **Android SDK** API 26+ (Android 8.0)
- **Backend API** đang chạy tại `http://localhost:3000`

---

## 🚀 CÁCH 1: CHẠY TRÊN EMULATOR (Khuyến nghị)

### Bước 1: Mở Project

1. Mở **Android Studio**
2. Chọn **File → Open**
3. Chọn thư mục: `d:\lingua-work\lingua-android`
4. Đợi **Gradle Sync** hoàn tất (~1-2 phút lần đầu)

### Bước 2: Tạo Emulator

1. Click icon **Device Manager** (điện thoại) ở thanh công cụ
2. Click **Create Device**
3. Chọn:
   - **Category**: Phone
   - **Device**: Pixel 5 (hoặc tương tự)
   - **System Image**:
     - API 34 (Android 14) - Khuyến nghị
     - Hoặc API 26+ bất kỳ
4. Click **Next** → **Finish**

### Bước 3: Kiểm tra Backend URL

File `app/build.gradle` đã cấu hình sẵn cho emulator:

```gradle
buildConfigField "String", "BASE_URL", "\"http://10.0.2.2:3000/api/\""
```

**Lưu ý:** `10.0.2.2` = localhost của máy host (chỉ dùng cho emulator)

### Bước 4: Chạy App

1. Click nút **Run** (▶️) hoặc nhấn `Shift + F10`
2. Chọn emulator vừa tạo
3. Đợi app build và cài đặt (~30 giây lần đầu)
4. App sẽ tự động mở trên emulator

---

## 📱 CÁCH 2: CHẠY TRÊN THIẾT BỊ THẬT

### Bước 1: Bật Developer Mode

1. Vào **Settings → About Phone**
2. Tap **Build Number** 7 lần
3. Nhập mật khẩu nếu được yêu cầu

### Bước 2: Bật USB Debugging

1. Vào **Settings → Developer Options**
2. Bật **USB Debugging**
3. Bật **Install via USB** (nếu có)

### Bước 3: Kết nối Điện thoại

1. Kết nối điện thoại với máy tính qua USB
2. Trên điện thoại, chọn **Allow USB Debugging**
3. Trong Android Studio, kiểm tra thiết bị xuất hiện trong Device Manager

### Bước 4: Đổi Backend URL

**QUAN TRỌNG:** Thiết bị thật không thể dùng `10.0.2.2`!

1. Tìm IP máy tính:

   ```bash
   # Windows
   ipconfig
   # Tìm IPv4 Address (ví dụ: 192.168.1.100)
   ```

2. Mở file `app/build.gradle`

3. Đổi BASE_URL:

   ```gradle
   buildConfigField "String", "BASE_URL", "\"http://192.168.1.100:3000/api/\""
   ```

   (Thay `192.168.1.100` bằng IP máy bạn)

4. Click **Sync Now** (biểu tượng voi)

### Bước 5: Chạy App

1. Click **Run** (▶️)
2. Chọn thiết bị thật
3. App sẽ được cài và chạy

---

## 📦 CÁCH 3: CÀI APK TRỰC TIẾP

### Build APK

```bash
# Mở PowerShell/CMD trong thư mục lingua-android
cd d:\lingua-work\lingua-android

# Build APK
.\gradlew.bat assembleDebug
```

APK sẽ được tạo tại:

```
app\build\outputs\apk\debug\app-debug.apk
```

### Cài APK

**Cách 1: Qua ADB**

```bash
adb install app\build\outputs\apk\debug\app-debug.apk
```

**Cách 2: Copy vào điện thoại**

1. Copy file `app-debug.apk` vào điện thoại
2. Mở file bằng File Manager
3. Cho phép "Install from unknown sources" nếu được hỏi
4. Click **Install**

---

## 🔧 CẤU HÌNH

### Đổi Backend URL

**File:** `app/build.gradle`

```gradle
defaultConfig {
    // Cho emulator
    buildConfigField "String", "BASE_URL", "\"http://10.0.2.2:3000/api/\""

    // Cho thiết bị thật (đổi IP)
    // buildConfigField "String", "BASE_URL", "\"http://192.168.1.100:3000/api/\""
}
```

**Sau khi đổi:**

1. Click **Sync Now** (icon voi)
2. Rebuild app

### Kiểm tra Backend đang chạy

```bash
# Từ máy tính
curl http://localhost:3000/api/health

# Từ điện thoại (đổi IP)
# Mở trình duyệt: http://192.168.1.100:3000/api/health
```

---

## 🎮 SỬ DỤNG APP

### Lần đầu mở app

1. **Onboarding** (4 bước):
   - Welcome
   - Chọn ngôn ngữ học (Japanese/English/Chinese/Korean)
   - Đặt mục tiêu XP/ngày
   - Chọn trình độ hiện tại

2. **Đăng ký tài khoản:**
   - Email
   - Mật khẩu (tối thiểu 6 ký tự)
   - Tên hiển thị

3. **Hoặc đăng nhập** nếu đã có tài khoản

### Tài khoản test

Nếu backend đã có data:

- Email: `test@lingua.com`
- Password: _(tạo mới hoặc dùng tài khoản có sẵn)_

---

## 🐛 TROUBLESHOOTING

### Gradle Sync Failed

**Lỗi:** "Gradle sync failed"

**Giải pháp:**

```bash
# Clean project
.\gradlew.bat clean

# Hoặc trong Android Studio:
# File → Invalidate Caches → Invalidate and Restart
```

### Build Failed - Resource Error

**Lỗi:** "Resource style/Widget.Lingua.Badge not found"

**Giải pháp:**

- File `app/src/main/res/values/themes.xml` đã được fix
- Nếu vẫn lỗi:
  ```bash
  .\gradlew.bat clean
  Build → Rebuild Project
  ```

### App không kết nối Backend

**Lỗi:** "Network error" hoặc "Connection refused"

**Kiểm tra:**

1. **Backend đang chạy?**

   ```bash
   curl http://localhost:3000/api/health
   ```

2. **URL đúng chưa?**
   - Emulator: `http://10.0.2.2:3000/api/`
   - Thiết bị thật: `http://192.168.x.x:3000/api/`

3. **Cùng WiFi?** (thiết bị thật)
   - Máy tính và điện thoại phải cùng mạng

4. **Firewall?**
   - Windows: Cho phép port 3000
   - Hoặc tắt firewall tạm thời

### Emulator chậm

**Giải pháp:**

1. Tăng RAM cho emulator:
   - Device Manager → Edit → Advanced Settings
   - RAM: 2048 MB trở lên

2. Bật Hardware Acceleration:
   - Settings → Emulated Performance → Graphics: Hardware

3. Dùng system image có Google APIs (không có Google Play)

### App crash khi mở

**Kiểm tra logs:**

1. Mở **Logcat** (tab dưới cùng Android Studio)
2. Filter: `package:com.lingua.app`
3. Tìm dòng màu đỏ (error)

**Lỗi thường gặp:**

- Backend không chạy → Khởi động backend
- URL sai → Kiểm tra BASE_URL
- Data không có → Seed lại database

---

## 📊 KIỂM TRA LOGS

### Android Logs (Logcat)

Trong Android Studio:

1. Mở tab **Logcat** (dưới cùng)
2. Filter theo package: `com.lingua.app`
3. Hoặc filter theo tag: `Lingua`

### Network Logs

Xem request/response API:

1. Logcat → Filter: `OkHttp` hoặc `Retrofit`
2. Xem URL, status code, response body

---

## 🔨 LỆNH GRADLE THƯỜNG DÙNG

```bash
# Clean project
.\gradlew.bat clean

# Build debug APK
.\gradlew.bat assembleDebug

# Build release APK
.\gradlew.bat assembleRelease

# Install debug APK
.\gradlew.bat installDebug

# Uninstall app
.\gradlew.bat uninstallDebug

# Run tests
.\gradlew.bat test

# List tasks
.\gradlew.bat tasks
```

---

## 📱 LỆNH ADB THƯỜNG DÙNG

```bash
# Xem devices
adb devices

# Install APK
adb install app-debug.apk

# Uninstall app
adb uninstall com.lingua.app

# Xem logs
adb logcat | findstr "Lingua"

# Clear app data
adb shell pm clear com.lingua.app

# Take screenshot
adb shell screencap -p /sdcard/screenshot.png
adb pull /sdcard/screenshot.png

# Record screen
adb shell screenrecord /sdcard/demo.mp4
```

---

## ✅ CHECKLIST

### Trước khi chạy

- [ ] Android Studio đã cài đặt
- [ ] JDK 17 đã cài đặt
- [ ] Backend đang chạy (`curl http://localhost:3000/api/health`)
- [ ] Đã mở project trong Android Studio
- [ ] Gradle sync thành công

### Emulator

- [ ] Đã tạo emulator API 26+
- [ ] BASE_URL = `http://10.0.2.2:3000/api/`
- [ ] Emulator đã khởi động

### Thiết bị thật

- [ ] USB Debugging đã bật
- [ ] Điện thoại đã kết nối (xuất hiện trong Device Manager)
- [ ] BASE_URL = `http://192.168.x.x:3000/api/` (IP máy bạn)
- [ ] Cùng WiFi với máy tính
- [ ] Đã Sync Gradle sau khi đổi URL

---

## 🎯 TÍNH NĂNG APP

### Đã triển khai

- ✅ Authentication (đăng ký, đăng nhập, đổi mật khẩu)
- ✅ Onboarding 4 bước
- ✅ Dashboard với XP, streak, hearts
- ✅ Vocabulary (từ vựng) với search, bookmark, audio
- ✅ Grammar (ngữ pháp) với ví dụ, audio
- ✅ Courses & Lessons (khóa học, bài học)
- ✅ Practice (luyện tập) với 6 loại bài tập
- ✅ SRS Flashcards (ôn tập ngắt quãng)
- ✅ Shadowing (luyện phát âm)
- ✅ AI Roleplay (chat AI)
- ✅ Mock Tests (bài kiểm tra)
- ✅ Gamification (XP, hearts, streak, achievements, quests)
- ✅ Leaderboard (bảng xếp hạng)
- ✅ Statistics (thống kê với biểu đồ)
- ✅ Settings (cài đặt, dark mode, notifications)
- ✅ Profile (hồ sơ, đổi avatar)
- ✅ Offline cache (xem từ vựng offline)

### Loại bài tập

1. **MULTI_CHOICE** - Trắc nghiệm
2. **FILL_BLANK** - Điền vào chỗ trống
3. **LISTEN** - Nghe và chọn
4. **DICTATION** - Nghe và viết
5. **SENTENCE_ORDER** - Sắp xếp câu
6. **MATCHING** - Ghép cặp

---

## 📞 HỖ TRỢ

Nếu gặp vấn đề:

1. **Kiểm tra logs:**
   - Android: Logcat trong Android Studio
   - Backend: `docker-compose logs -f api`

2. **Reset app:**

   ```bash
   # Xóa data app
   adb shell pm clear com.lingua.app

   # Hoặc: Settings → Apps → Lingua → Clear Data
   ```

3. **Rebuild:**
   ```bash
   .\gradlew.bat clean
   Build → Rebuild Project
   ```

---

## ✨ HOÀN TẤT!

Sau khi làm theo hướng dẫn, bạn sẽ có:

- ✅ App chạy trên emulator/thiết bị
- ✅ Kết nối backend thành công
- ✅ Đăng nhập và sử dụng được tất cả tính năng

**Chúc bạn học vui vẻ! 📚🚀**
