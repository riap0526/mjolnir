# 待辦清單

第一版修正（設定檔可靠寫入、依套件名稱啟動、圖示快取執行緒安全、崩潰防護、補診斷 log）以外，審查時發現、但刻意延後的項目。原則是安全穩定優先：有使用者影響的證據、或是在實機上驗證過，才動會改變行為的項目。

標記說明：
- 🔍 **待 log 確認**：先用第一版新增的診斷事件確認再決定做法
- 🧪 **需實機驗證**：會改變開機、桌面或啟動行為，必須在 Thor 上測過

## 高優先

### 1. HomeActivity 與無障礙服務兩條啟動路徑互不協調 🔍🧪
- **現象**：Advanced 模式加上 Mjolnir 設為預設主畫面時，開機、行程重啟、系統叫桌面，都會由 `HomeActivity` 墊 SafetyNet 再啟動上下 App；同時無障礙服務的「開機自動啟動」與 Home 手勢也各自啟動。兩邊沒有協調（使用者 log：09-07 13:45:24 開機時兩條路徑都有觸發）。
- **疑似影響**：上螢幕偶發停在「You should not be here」（SafetyNet），開機後尤其常見。
- **先確認**：發生時的 `SafetyNet VISIBLE`、`HomeActivity START` 與 `LAUNCH_*` 事件順序。
- **相關**：`HomeActivity.kt`、`HomeKeyInterceptorService.maybeRunBootAction()`、`SafetyNetManager.kt`

### 2. Thor「全部清除」會殺掉 Mjolnir 行程 🔍
- **現象**：使用者 log 一個月內 Mjolnir 行程啟動 49 次，只有 10 次是開機，常常數秒內連續重啟 2 到 5 次，且多在「最近使用的應用程式」畫面之後。Mjolnir 自己不出現在最近使用清單裡，所以使用者無法把它上鎖。0.2.3 版曾用獨立的 `:keepalive` 行程避開這個問題，0.2.5c 又拿掉了。
- **先確認**：`PREVIOUS_PROCESS_EXIT` 的 reason（`USER_REQUESTED`、`LOW_MEMORY`、`CRASH`…）。
- **可能做法**：評估恢復獨立行程（注意跨行程後 SettingsStore 的記憶體快取會失去一致性），或其他抵抗清除的方式。

### 3. Android 14 以上的前景服務類型錯誤 🧪
- `KeepAliveService` 宣告 `connectedDevice` 類型但沒有對應權限，`startForeground` 又傳入類型 0，lint 警告被 `tools:ignore` 壓掉。Android 14 以上很可能直接被系統擋下或殺掉。
- **時機**：支援 RG-DS 或任何 Android 14 以上裝置之前必做。改用 `specialUse` 類型。

### 4. 螢幕身分用 `displays[0]` / `displays[1]` 的陣列索引判斷 🧪
- 共 17 處。陣列順序沒有保證，而且會包含錄影、投影、外接螢幕等虛擬或額外螢幕。Thor 的下螢幕 display ID 是 4，目前剛好正確。
- **做法**：抽出單一的 `DisplayRoles`，依 display flags 和類型排除虛擬與 presentation 螢幕。

### 5. `SettingsStore` 的記憶體快取問題
- 手動編輯設定檔後不會重新載入（程序被常駐服務撐著幾乎不會重啟），而且下一次任何寫入都會用記憶體裡的舊值覆蓋使用者的修改。
- 寫檔仍在呼叫端的執行緒（多為主執行緒）同步進行，第一版只先做到「可靠」與「沒變就不寫」。
- **做法**：用 `FileObserver` 或檢查 mtime 偵測外部修改；寫檔移到單一背景執行緒。

## 中優先

6. **`buildDefaultHomeIntent()` 解析錯誤**：沒有預設桌面時會得到系統選擇器（package 為 `android`）；Mjolnir 本身是預設桌面時會繞回 `HomeActivity`。（`home/HomeActionLauncher.kt`）
7. **防呆可被繞過**：通知上的「Enable Advanced」直接翻轉開關，不檢查前置條件；`SteamFileGenActivity` 內複製來的設定頁可以只設定單一螢幕，繞過 onboarding 的檢查。
8. **`settings.json` 的 `TOP_APP` / `BOTTOM_APP` 填空字串**會被當成「已設定」。檔案註解卻寫著可以留空。
9. **`runShellCommand` 可能 deadlock**：先 `waitFor()` 才讀輸出，輸出量大時（`dumpsys SurfaceFlinger`）會互相等待。只影響 root 裝置的舊版截圖路徑與完整診斷。（`utils/ScreenshotUtil.kt`、`utils/DiagnosticsLogger.kt`）
10. **廣播保護**：Android 13 以下 `registerReceiver` 沒有設 NOT_EXPORTED；`DualScreenshotManager` 送出的是沒有 `setPackage` 的 implicit broadcast。
11. **`FocusHackHelper` 的 pending request 會殘留**：焦點 Activity 沒有成功 resume 時，請求永遠留在 map 裡；等待逾時後如果才 resume，`GLOBAL_ACTION_HOME` 會延遲觸發。
12. **SafetyNet 狀態**：`KEY_SAFETY_NET_PENDING` 只被設定，沒有任何地方讀取；SafetyNet 只在 `HomeActivity` 執行時補上。
13. **DualShot 防迴圈檢查無效**：KeepAlive 檢查路徑是否含 "Mjolnir"，但截圖存在 `Pictures/Screenshots`，實際只靠尺寸比對擋住。

## 低優先與整理

14. **重複程式碼**：`SPECIAL_HOME_APPS` 寫死 10 次；`isConfigurationValid`、`isAccessibilityServiceEnabled`、`getCurrentDefaultHomePackage` 各有 3 到 4 份；`HomeActionLauncher` 的 launchTop/launchTopAwait 等函式幾乎相同。
15. **隱性依賴**：手勢設定重新載入，依賴「偏好設定值沒變也會通知 listener」的行為，應改成明確呼叫。
16. **`minSdk` 24 名不副實**：焦點偵測與 `takeScreenshot` 需要 API 30；`DualScreenshotService` 使用 `RELATIVE_PATH` / `IS_PENDING` 沒有 API 29 判斷。建議提高到 30。
17. **相依套件**：移除未使用的 jsoup、accompanist-drawablepainter、material3-window-size-class；`material-icons-extended` 借用了 material3 的版本號（1.2.1，與 Compose BOM 不一致）；之後再評估開啟 R8（需要先有測試保護）。
18. **版本控制與 manifest**：`.idea/`、`.kotlin/` 已被 track，應 `git rm --cached`；`RECEIVE_BOOT_COMPLETED`、`WRITE_EXTERNAL_STORAGE` 沒有用到。
19. **死碼與過時文件**：`MjolnirApp.isKeepAliveProcess()`、`NotificationUtil`（讀一個從未寫入的全域狀態）、`FocusLockOverlayWorkaround` 的文件描述與實作不符。
20. **診斷 log 量**：開啟時每個 accessibility event 都寫一行（使用者 log 一個月約 2.7 萬行），可考慮分級或取樣。

## 工程基礎

21. **測試**：把手勢狀態機抽成不依賴 Android 的類別並寫單元測試；為 `DurableFiles`、`SettingsStore` 的解析與遷移、設定驗證補測試。
22. **CI**：GitHub Actions 跑 `assembleDebug`、`lint`、`testDebugUnitTest`。
23. **雲端建置環境**：需要在環境的網路設定中允許 `dl.google.com`、`maven.google.com`、`downloads.gradle.org`，才能執行 `scripts/setup-android-sdk.sh` 並建置。
