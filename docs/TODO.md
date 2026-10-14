# 待辦清單

審查時發現、但刻意延後的項目。已完成的改動見 [`docs/notes/2026-10-02-stability-round-1.md`](notes/2026-10-02-stability-round-1.md)，最新的實機檢視見 [`docs/notes/2026-10-10-week-log-review.md`](notes/2026-10-10-week-log-review.md)，「從最近使用隱藏」的評估見 [`docs/notes/2026-10-10-hide-from-recents.md`](notes/2026-10-10-hide-from-recents.md)。原則是安全穩定優先：有使用者影響的證據、或是在實機上驗證過，才動會改變行為的項目。

標記說明：
- 🔍 **待 log 確認**：先用第一版新增的診斷事件確認再決定做法
- 🧪 **需實機驗證**：會改變開機、桌面或啟動行為，必須在 Thor 上測過

## 建議處理順序（2026-10-14 評估）

依「可做、風險低」排序。編號對應下方各項。

| 等級 | 項目 | 說明 |
|---|---|---|
| ✅ 已完成（10-14） | 20（前半）、21（前半）、26（`DurableFiles`）、27（報告模式） | 不影響 App 行為，未發佈新版 |
| 一、無行為改變 | 12、22、15 | 指令讀取 deadlock、死碼清理。不需實機測試 |
| 二、小改動但影響行為，需實機確認 | 11 → 21（後半）→ 14 → 23 → 9 → 13 → 10 → 5 | 5 要等 `SettingsStore` 有測試保護再做 |
| 三、高風險或目前不需要，延後 | 1、4、3、19、20（R8） | 1 等「You should not be here」再發生、有 log 證據再做；3、4 等支援 Android 14 或其他裝置時再做 |
| 觀察中（等 log，不寫程式） | 2、7、8、6 | 約 10-17 v0.2.8a 滿一週後檢視 log。6 使用者沒有用 FOCUS 類手勢，無法實機驗證 |

## 高優先

### 1. HomeActivity 與無障礙服務兩條啟動路徑互不協調 🔍🧪
- **現象**：Advanced 模式加上 Mjolnir 設為預設主畫面時，開機、行程重啟、系統叫桌面，都會由 `HomeActivity` 墊 SafetyNet 再啟動上下 App；同時無障礙服務的「開機自動啟動」與 Home 手勢也各自啟動。兩邊沒有協調（使用者 log：09-07 13:45:24 開機時兩條路徑都有觸發）。
- **疑似影響**：上螢幕偶發停在「You should not be here」（SafetyNet），開機後尤其常見。
- **先確認**：發生時的 `SafetyNet VISIBLE`、`HomeActivity START` 與 `LAUNCH_*` 事件順序。
- **相關**：`HomeActivity.kt`、`HomeKeyInterceptorService.maybeRunBootAction()`、`SafetyNetManager.kt`

### 2. Mjolnir 偶爾被 SIGKILL 結束（原因不確定，觀察中）
- **現象**：10-03、10-04、10-08 三次被 `SIGNALED`（SIGKILL）結束，之前 5 到 15 秒都出現最近使用的應用程式畫面，原本推測是「全部清除」。
- **10-10 更新**：「從最近使用隱藏」的實機測試中，被隱藏的 Smart Launcher 在全部清除後沒有被殺，`am force-stop` 也殺不掉顯示中的 ES-DE（PID 不變）。全部清除不一定會殺掉不在清單中的 App，**原推測不確定**，下次發生時要重新判斷。
- **影響**：每週約 3 次。系統 2 到 11 秒內重新拉起 Mjolnir，期間 Home 鍵由系統交給 HomeActivity 處理。
- **評估結論（10-10）**：目前不改程式。使用者已於 10-10 把 Mjolnir 設為不受電池最佳化限制，下一份 log 觀察 `SIGNALED` 是否減少。先請使用者查 Thor 有沒有背景白名單設定；「顯示在最近使用清單」的實驗開關暫不考慮（上鎖對前景 App 的保護效果在測試中無法證明）。獨立 `:keepalive` 行程無效（被殺的是承載無障礙服務的主行程），不採用。詳見 [`notes/2026-10-10-week-log-review.md`](notes/2026-10-10-week-log-review.md) C 節。

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

### 6. FOCUS: Home 行為改變需要實機驗證 🧪（使用者未使用 FOCUS 類手勢，暫無法驗證）
- 合併 ghgoodreau 的修正後，「FOCUS: Home」不再直接送系統 Home 鍵，而是依焦點把桌面開在對應螢幕（透過 `FocusHackHelper` 搶焦點後再送 Home）。焦點無法判斷時才退回舊行為。
- **要驗證**：使用 FOCUS: Home 時，上下螢幕各試一次，確認桌面出現在正確的螢幕，且沒有閃爍或延遲異常。也會一併受到第 14 項的影響。

### 7. ~~圖示改為需要時才載入~~ ✅ v0.2.8 完成（`987fc51`）
- 待驗證：比較改版前後 `PREVIOUS_PROCESS_EXIT` 的 `pssKb`（改版前基準 55 到 74 MB）。

### 8. 記憶體回收風暴（觀察中）
- 10-04 兩次在 `com.droiddeck.launcher` 執行時發生，Mjolnir 在 35 秒內被殺 16 次（`LOW_MEMORY`）。ES-DE 被重設很可能是同一原因。Mjolnir 能做的只有降低自身記憶體（第 7 項）。

## 中優先

9. **`buildDefaultHomeIntent()` 解析錯誤**：沒有預設桌面時會得到系統選擇器（package 為 `android`）；Mjolnir 本身是預設桌面時會繞回 `HomeActivity`。（`home/HomeActionLauncher.kt`）
10. **防呆可被繞過**：通知上的「Enable Advanced」直接翻轉開關，不檢查前置條件；`SteamFileGenActivity` 內複製來的設定頁可以只設定單一螢幕，繞過 onboarding 的檢查。
11. **`settings.json` 的 `TOP_APP` / `BOTTOM_APP` 填空字串**會被當成「已設定」。檔案註解卻寫著可以留空。
12. **`runShellCommand` 可能 deadlock**：先 `waitFor()` 才讀輸出，輸出量大時（`dumpsys SurfaceFlinger`）會互相等待。只影響 root 裝置的舊版截圖路徑與完整診斷。（`utils/ScreenshotUtil.kt`、`utils/DiagnosticsLogger.kt`）
13. **廣播保護**：Android 13 以下 `registerReceiver` 沒有設 NOT_EXPORTED；`DualScreenshotManager` 送出的是沒有 `setPackage` 的 implicit broadcast。
14. **`FocusHackHelper` 的 pending request 會殘留**：焦點 Activity 沒有成功 resume 時，請求永遠留在 map 裡；等待逾時後如果才 resume，`GLOBAL_ACTION_HOME` 會延遲觸發。合併 FOCUS: Home 修正後，這條路徑的使用頻率變高了。
15. **SafetyNet 狀態**：`KEY_SAFETY_NET_PENDING` 只被設定，沒有任何地方讀取；SafetyNet 只在 `HomeActivity` 執行時補上。
16. **DualShot 防迴圈檢查無效**：KeepAlive 檢查路徑是否含 "Mjolnir"，但截圖存在 `Pictures/Screenshots`，實際只靠尺寸比對擋住。

## 低優先與整理

17. **重複程式碼**：`SPECIAL_HOME_APPS` 寫死 10 次；`isConfigurationValid`、`isAccessibilityServiceEnabled`、`getCurrentDefaultHomePackage` 各有 3 到 4 份；`HomeActionLauncher` 的 launchTop/launchTopAwait 等函式幾乎相同。
18. **隱性依賴**：手勢設定重新載入，依賴「偏好設定值沒變也會通知 listener」的行為，應改成明確呼叫。
19. **`minSdk` 24 名不副實**：焦點偵測與 `takeScreenshot` 需要 API 30；`DualScreenshotService` 使用 `RELATIVE_PATH` / `IS_PENDING` 沒有 API 29 判斷。建議提高到 30。
20. **相依套件**：~~移除未使用的 jsoup、accompanist-drawablepainter、material3-window-size-class~~ ✅ 10-14 完成（未發佈）；`material-icons-extended` 借用了 material3 的版本號（1.2.1，與 Compose BOM 不一致）；之後再評估開啟 R8（需要先有測試保護）。
21. **版本控制與 manifest**：~~`.idea/`、`.kotlin/` 已被 track~~ ✅ 10-14 已停止追蹤並加入 `.gitignore`。尚未處理：`RECEIVE_BOOT_COMPLETED`、`WRITE_EXTERNAL_STORAGE` 沒有用到（移除後需重開機確認開機啟動正常）。
22. **死碼與過時文件**：`MjolnirApp.isKeepAliveProcess()`、`NotificationUtil`（讀一個從未寫入的全域狀態）、`FocusLockOverlayWorkaround` 的文件描述與實作不符。
23. **診斷 log 量**：開啟時每個 accessibility event 都寫一行（使用者 log 一個月約 2.7 萬行），合併後每次 Home 鍵事件又多一行 `HOME_KEY_EVENT`。可考慮分級或取樣。
24. ~~**開機後顯示舊的「上次結束原因」**~~ ✅ v0.2.8 完成（`987fc51`）：開機前的紀錄會標示 `beforeBoot=true`。
25. **Thor 的 `focus_change` 系統設定意義不明**：值是 188、215、790 這類數字，不是螢幕 ID。目前的處理是驗證不通過就不採用、改為掃描視窗。如果能查出它代表什麼，也許能更快判斷焦點，但優先度低。

## 工程基礎

26. **測試**：已有 `HomeGestureState`（ghgoodreau）、`FocusedDisplayResolver`，以及 10-14 新增的 `DurableFiles`（10 個：寫入與備份、刪除檔案視為重設、NUL 或格式錯誤時改讀備份並保留 `.corrupt`、兩份都壞時回傳空值）。單元測試共 19 個。尚缺：`SettingsStore` 的解析與遷移（需要 `Context`，要先抽出純函式或引入 Robolectric）、設定驗證、`resolveLaunchIntent`。
27. **CI**：`.github/workflows/android.yml` 包含 debug 建置、單元測試、簽章 release、tag 發佈。10-14 加入 **lint 報告模式**：`lintDebug` 有錯時該步驟標為失敗，但不會讓整個建置失敗或擋下發佈，報告上傳為 artifact（`lint-report-<commit>`）。
    - 目前基準：**14 個錯誤、74 個警告**，全部在原本的程式碼。錯誤分布：`NewApi` 8（`minSdk` 24 但用了 API 26 到 34 的呼叫，與第 19 項相關）、`LocalSuppress` 2（`DualScreenLauncher` 的 `@SuppressLint` 放在區域變數上無效）、`StartActivityAndCollapseDeprecated` 2（Quick Tile）、`UnsafeImplicitIntentLaunch` 1（與第 13 項相關）、`UnusedMaterial3ScaffoldPaddingParameter` 1。
    - 錯誤清到 0 之後，再把 lint 改成會讓 CI 失敗。
