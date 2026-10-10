# 2026-10-10 一週實機 log 檢視與後續評估

log 範圍：2026-10-02 20:25 到 10-10 12:47，共 4,279 行。10-03 20:09 安裝了含 `1667ac3`、`eb4e93c` 的修正版（當時 Mjolnir 的結束原因記錄為 `USER_REQUESTED`，也就是 App 更新），以下以這個時間點之後的約 6.5 天為準。這次只做分析，沒有修改程式碼。

## 結論

**Mjolnir 本身已經穩定。** 這段期間沒有看到 Mjolnir 自身程式錯誤造成的問題。剩下的狀況來自外部環境：系統記憶體不足，以及「全部清除」會殺掉 Mjolnir。

## 已確認修好

| 項目 | 這週的狀況 |
|---|---|
| 重啟後主執行緒卡 10 秒（`1667ac3`） | 26 次重啟，無障礙服務連線到就緒都在 1.2 秒內 |
| 手勢檔案不斷增加（`eb4e93c`） | 沒有再發生，使用中的預設一直是 `untitled-1.cfg` |
| 設定檔損毀或遺失（`024541d`） | 2 次重開機、26 次重啟後設定都完整；沒有 `SETTINGS_LOAD_ISSUE`，log 中也沒有 NUL 位元組 |
| 啟動 App 失敗 | 啟動 53 次，全部成功 |
| 設定被清空 | 安裝初期以外 0 次 |
| 「You should not be here」 | 只在 10-02 開機時一閃而過，這週沒有停留過 |
| 長按放開被多判斷一次 | 3 次長按都正確忽略了放開事件 |
| 崩潰或沒有回應 | 結束原因中沒有任何 `CRASH`、`ANR` |

## 仍存在的狀況

### 1. 記憶體不足時被連續殺掉

| 時間 | 次數 | 上螢幕當時的程式 |
|---|---|---|
| 10-04 01:19 到 01:29 | 5 次，最後 4 次發生在 8 秒內 | `com.droiddeck.launcher` |
| 10-04 22:22 | 16 次，在 35 秒內 | `com.droiddeck.launcher` |

- 結束原因都是 `LOW_MEMORY`。當時 Mjolnir 是前景服務（importance 125），仍然被殺。
- 每次重啟後 1 到 2 秒就再被殺，連啟動都沒完成。圖示預先載入在 30 次啟動中只有 12 次完成。
- 22:22:43 有一次 Home 鍵只收到放開事件，因為按下時行程正好被殺。
- 01:29:44 的 Home 鍵在無障礙服務尚未連線時按下，改由系統處理，系統叫起 Mjolnir 的 HomeActivity 啟動了 ES-DE 和 Smart Launcher。把 Mjolnir 設為預設主畫面的安全網有發揮作用。
- 前景服務都會被殺，背景的 ES-DE 與 Smart Launcher 幾乎一定也被殺。**這很可能是「玩完遊戲回來 App 被重設」的原因。** 上鎖只能擋「全部清除」，擋不住系統的記憶體回收。可以在發生後用 `adb shell dumpsys activity exit-info org.es_de.frontend` 確認。

### 2. 「全部清除」會殺掉 Mjolnir

10-03 12:48、10-04 11:19、10-08 20:38 三次 `SIGNALED`（SIGKILL）之前 5 到 15 秒，都出現了 `com.android.launcher3`，也就是最近使用的應用程式畫面。比較可能是使用者按了全部清除；Mjolnir 不出現在最近使用清單中，所以無法上鎖。**這修正了 10-03 紀錄中「喚醒時被殺」的推測。** 影響：系統會在 2 到 11 秒內重新拉起 Mjolnir，期間 Home 鍵改由系統處理，不會重新啟動 App。

### 3. 小問題

- 開機後顯示的「上次結束原因」是開機前的舊紀錄（10-03 20:09、10-04 00:45 兩次都顯示 10-03 12:49）。
- RetroArch 存檔縮圖觸發了 126 次截圖自動拼接的檢查，全部被正確略過。
- 打開最近使用的應用程式後回到桌面時，HomeActivity 會重新啟動上下 App（10-08 19:25、19:26）。屬於 `docs/TODO.md` 第 1 項，這週沒有造成問題。

### 無法從這份 log 判斷

- ES-DE、Smart Launcher 本身的結束原因：Mjolnir 只能查自己的。
- FOCUS 類手勢沒有使用，FOCUS: Home 的修正仍未實機驗證。

---

## 後續三項評估

### A. 圖示改為需要時才載入

**現況**：`MjolnirApp.onCreate` 每次行程啟動都在背景把所有 App 的圖示載入記憶體快取（`AppQueryHelper.prewarmAllApps`）。查過程式碼，用到圖示的只有設定畫面（主畫面設定、黑名單、初始設定的 App 選擇器，以及 `MainActivity`、`SteamFileGenActivity` 的手動雙螢幕啟動按鈕）。**Home 鍵路徑完全用不到圖示**：`d41bffd` 之後 Home 鍵改用 `resolveLaunchIntent`，不再查詢 App 清單。而大多數行程啟動都只是系統重新綁定無障礙服務，使用者根本不會打開設定畫面。

**做法**：
1. 移除 `MjolnirApp.onCreate` 中的預先載入。
2. 改成在 `MainActivity`、`OnboardingActivity` 的 `onCreate` 啟動背景預先載入。使用者進入 App 選擇器時，快取通常已經熱了。
3. 選擇性項目：在 `onTrimMemory` 收到 `TRIM_MEMORY_UI_HIDDEN` 或更高等級時清空圖示快取，離開設定畫面後就釋放記憶體。

**效益**：
- 無障礙服務單獨啟動時，不再查詢套件清單和解碼圖示，常駐記憶體下降。實際下降多少要量測，可以比對改版前後 `PREVIOUS_PROCESS_EXIT` 的 `pssKb`（這週的基準是 55 到 74 MB，多數在 56 到 60 MB），或用 `adb shell dumpsys meminfo xyz.blacksheep.mjolnir`。
- 被殺之後重啟比較輕，在記憶體回收風暴中比較有機會撐過去。

**風險與限制**：
- **低風險**。主要副作用是：如果使用者一打開設定就立刻進入 App 選擇器，第一次會稍慢；`HomeLauncherSettingsMenu` 是在主執行緒載入清單的。
- **沒辦法阻止記憶體回收風暴**。真正吃掉記憶體的是 DroidDeck 這類程式，Mjolnir 能做的只是讓自己更輕。
- 改動約 10 到 20 行，集中在 `MjolnirApp`、`MainActivity`、`OnboardingActivity`。

**結論：建議做。** 風險低，方向明確，改版後可以用既有的診斷資料量化效果。

### B. 開機後不顯示舊的「上次結束原因」

**現況**：`logStartupContext` 只取最近一筆結束紀錄。關機不會產生結束紀錄，所以開機後拿到的是更早以前的那筆，容易讓人誤以為剛發生過。

**做法**：用 `System.currentTimeMillis() - SystemClock.elapsedRealtime()` 算出開機時間。結束紀錄早於開機時間時，改記為 `PREVIOUS_PROCESS_EXIT ... beforeBoot=true`，或者不記錄、只寫一行「開機後首次啟動」。不需要額外儲存任何狀態。

**效益**：判讀 log 時不會再被誤導。只影響診斷資訊，不影響功能。

**風險**：幾乎沒有。只改診斷 log，約 5 到 10 行。

**結論：建議做，可以和 A 一起出版。**

### C. 讓 Mjolnir 不受「全部清除」影響

**現況**：所有 Activity 都設了 `excludeFromRecents`，所以 Mjolnir 不出現在最近使用清單，使用者沒辦法像 ES-DE 那樣把它上鎖。被殺之後，系統會在 2 到 11 秒內重新綁定無障礙服務。這段期間 Mjolnir 是預設主畫面，Home 鍵會由系統交給 HomeActivity，使用者仍然會得到回應。實際影響是每週約 3 次、每次幾秒的手勢無法使用。

**評估過的做法**：

| 做法 | 說明 | 評估 |
|---|---|---|
| C1. 讓 Mjolnir 出現在最近使用清單，讓使用者上鎖 | 拿掉 `MainActivity` 的 `excludeFromRecents`，或做成可選開關 | **效果不確定**：要看 Thor 的上鎖是保護整個套件，還是只保護那一個工作，以及鎖定能不能撐過重開機。原作者在 0.2.3 刻意把它從清單移除，理由是避免「套件層級的終止」。使用者也必須一直把 Mjolnir 留在清單裡。 |
| C2. 恢復 0.2.3 的獨立 `:keepalive` 行程 | 把常駐服務移到另一個行程 | **不建議**：被殺的是承載無障礙服務的主行程，獨立出常駐服務沒有幫助。原作者在 0.2.5c 因為多行程問題把它拿掉，而且 `SettingsStore` 的記憶體快取在多行程下會失去一致性。 |
| C3. Thor 系統設定的白名單 | 在 Thor 的電池最佳化或背景管理把 Mjolnir 設為不限制，或加入白名單 | **不用改程式碼**，需要使用者確認 Thor 有沒有這類選項。 |
| C4. 接受現況，讓重啟更輕 | 由 A 降低重啟成本 | 已經包含在 A 裡。 |

**結論：目前不建議改程式碼。**
1. 影響小：每週約 3 次、每次幾秒，而且系統的預設主畫面機制會接手。
2. C1 的效果取決於 Thor 不公開的行為，C2 已知無效且有風險。
3. 建議順序：先請使用者查 Thor 的設定（C3）；如果查不到，再考慮把 C1 做成**預設關閉**的實驗性開關，讓使用者實機驗證上鎖是否有效。

---

## 建議的下一步

1. 第一批做 A 和 B（低風險，可以一起出版），出版後用一週 log 比較記憶體與重啟情況。
2. C 先不動程式，請使用者查 Thor 有沒有背景白名單設定。
3. 可以考慮把目前版本打 tag 發佈為正式版（例如 `v0.2.8`）。

---

## 決定與執行結果（2026-10-10）

| 項目 | 決定 | 結果 |
|---|---|---|
| A. 圖示改為需要時才載入 | 做 | `987fc51`：移出 `MjolnirApp.onCreate`，改由 `MainActivity`、`OnboardingActivity` 在背景預先載入；`onTrimMemory`（UI_HIDDEN 以上）清空快取 |
| B. 開機前的結束紀錄 | 做 | `987fc51`：`PREVIOUS_PROCESS_EXIT` 新增 `beforeBoot` 欄位 |
| C. 抵抗「全部清除」 | 不做 | 使用者已把 Mjolnir 設為不受電池最佳化限制，先觀察下一份 log 中 `SIGNALED` 的次數 |

A、B 與先前所有修正一起發佈為 **v0.2.8**（第一個正式版，`versionCode` 21）。

**下一份 log 要看**：
- `PREVIOUS_PROCESS_EXIT` 的 `pssKb` 是否低於改版前的 55 到 74 MB。
- 一般啟動是否不再出現 `ICON_PREWARM_START`；只有打開 Mjolnir 畫面時才出現，並帶有 `source=MainActivity` 或 `source=Onboarding`。
- `SIGNALED` 是否因為電池最佳化設定而減少。

### 發佈結果

- **v0.2.8 已發佈**：<https://github.com/riap0526/mjolnir/releases/tag/v0.2.8>
- APK：`mjolnir-0.2.8.apk`（簽章版，約 14.2 MB），說明內容取自 `app/src/main/assets/changelogs/v0.2.8.txt`。
- tag `v0.2.8` 指向 `65acc4a`。由於雲端環境無法推送 tag，改由 GitHub Actions 手動觸發發佈（`release_tag=v0.2.8`，run 38026671559）。
- 發佈時發現：同一分支的 push 建置與手動發佈共用同一個 concurrency 群組，push 的建置被發佈取消（反過來也可能取消發佈）。已調整為依事件分組，發佈不會被取消。
- `main` 分支仍是原作者的版本，這次是從 `claude/intelligent-allen-jbocja` 發佈。

