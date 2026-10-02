# 建置與簽章

## 需求

- JDK 17 以上（21 可用）
- Android SDK：`platform-tools`、`platforms;android-34`、`build-tools;34.0.0`
- 網路需能連到 `dl.google.com`（SDK 與 Google Maven）、`downloads.gradle.org`（Gradle wrapper）、`repo.maven.apache.org`、`plugins.gradle.org`

## 安裝 SDK

```bash
scripts/setup-android-sdk.sh            # 預設裝到 $ANDROID_HOME 或 ~/android-sdk
```

腳本會一併寫好 `local.properties`（已在 `.gitignore` 中）。

## 建置

```bash
./gradlew assembleDebug                 # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest             # JVM 單元測試
./gradlew assembleRelease               # 有簽章設定時輸出已簽章的 release APK
```

## Release 簽章

### 1. 產生金鑰（只做一次）

```bash
keytool -genkeypair -v -keystore mjolnir-release.jks -alias mjolnir \
  -keyalg RSA -keysize 4096 -validity 36500
```

**這把金鑰遺失就無法再發佈可覆蓋安裝的更新**，請另外備份到安全的地方（密碼管理器或離線儲存）。金鑰檔與密碼絕對不要 commit；`*.jks`、`*.keystore`、`keystore.properties` 已列入 `.gitignore`。

### 2. 提供簽章資訊（擇一）

**本機：** 在 repo 根目錄建立 `keystore.properties`

```properties
storeFile=/絕對或相對於 repo 根目錄的路徑/mjolnir-release.jks
storePassword=...
keyAlias=mjolnir
keyPassword=...
```

**CI 或雲端環境：** 設定環境變數

| 變數 | 內容 |
|---|---|
| `MJOLNIR_KEYSTORE_FILE` | keystore 檔路徑 |
| `MJOLNIR_KEYSTORE_PASSWORD` | keystore 密碼 |
| `MJOLNIR_KEY_ALIAS` | key alias |
| `MJOLNIR_KEY_PASSWORD` | key 密碼 |

keystore 檔本身可以用 base64 存成 secret，建置前再還原成檔案。兩者都沒有設定時，`assembleRelease` 會產生未簽章的 APK，不會失敗。

### 3. 發佈新版前

- 調高 `app/build.gradle.kts` 的 `versionCode`（必須大於已安裝的版本）與 `versionName`。
- 在 `app/src/main/assets/changelogs/` 加上對應版本的更新說明。

## 從原作者版本換成本 fork（重要）

本 fork 用的是**不同的簽章金鑰**，Android 不允許直接覆蓋安裝原作者簽章的 APK，必須先解除安裝。解除安裝會**刪除 `/Android/data/xyz.blacksheep.mjolnir/`**，也就是所有設定與手勢預設檔。

1. 先把 `/Android/data/xyz.blacksheep.mjolnir/` 整個資料夾複製出來備份。
2. 如果 Mjolnir 目前是預設主畫面，先到「設定 → 應用程式 → 預設應用程式 → 主畫面應用程式」改回 Quickstep，避免解除安裝後沒有桌面。
3. 解除安裝原版，安裝本 fork 的 APK。
4. 開啟一次 Mjolnir 後，把備份的 `settings.json`、`config.ini`、`blacklist.json`、`gestures/` 放回同一個資料夾，再強制停止 Mjolnir 讓它重新讀取。
5. 重新開啟無障礙服務、通知權限，必要時重新設定預設主畫面。
