# 2026-10-19 Etsum fork 比對與移植（v0.2.8b）

來源：[Etsum/mjolnir](https://github.com/Etsum/mjolnir)，同樣從上游 `v0.2.7a`（`7f6b5a7`）分出，已發佈 v0.3.0（versionCode 22）。比對時對方 `main` 比上游多 15 個 commit（`f33cdea` 到 `7941332`），其他分支都只是上游舊的 release／hotfix 分支。

原則：只移植小、低風險、我們程式也確定有問題的修正；會改變行為的只記錄，等實機驗證後再決定。

## 已移植（v0.2.8b）

| 項目 | Etsum 來源 | 我們的改動 |
|---|---|---|
| BOTH: Auto／BOTH: Home 主螢幕最後啟動，保留輸入焦點（上游 #38） | `f33cdea` 後半 | `HomeActionLauncher.launchBoth()`、`launchDefaultHomeOnBoth()` 對調啟動順序，與 `HomeActivity`／`DualScreenLauncher.launchOnDualScreens` 一致。只移植順序，沒有移植對方同一個 commit 裡的 TOP/BOTTOM: Home 直接啟動 |
| 開機自動啟動等 SD 卡掛載（上游 #29） | `dfb013d` | `HomeKeyInterceptorService.runWhenStorageReady()`：卸除式儲存空間為 `CHECKING`／`UNMOUNTED` 時每 500 ms 重試，最多 10 秒。比對方多兩點：用既有的 `gestureHandler`，且服務在等待中被解除綁定時停止（重新連線會自己再跑一次開機檢查）；等待結束時記錄 `AUTO_BOOT_STORAGE_DONE waitedMs=…` |
| 手勢預設檔：「新增」不再立刻寫檔、存檔不再變成 `name-2.cfg`、內建預設檔改名存成新檔、系統返回鍵等於取消 | `19456f6` | `GestureConfigStore.createPresetFromActive()` 改為只建 draft；新增 `saveFromEditor()` 作為設定頁與 onboarding 共用的存檔路徑；`nextAvailableFileName()` 把自己的檔名視為可用；`GesturePresetEditorScreen` 加 `BackHandler` |
| Robolectric 測試 | `19456f6` | `GestureConfigStoreTest`（4 個測試），另加「取消新增不留檔」與「命名為 custom 的預設檔重新載入後仍存在」（同時驗證我們 `eb4e93c` 的修正）。只加 `robolectric` 與 `androidx.test:core`，沒有加對方的 roborazzi／Compose UI test |

驗證：`./gradlew testDebugUnitTest` 13 個測試全過。用改動前的 `GestureConfigStore` 跑新測試，4 個全部失敗，確認測試抓得到原本的問題。#38 順序與 SD 卡等待沒有單元測試（需要模擬無障礙服務與 `StorageManager`），要靠實機確認。

### 實機要確認

- **#38**：主螢幕設為 TOP 時，按 BOTH: Auto／BOTH: Home 後，手把輸入應該在上螢幕；設為 BOTTOM 時在下螢幕。
- **SD 卡**：有 SD 卡時開機，log 會出現 `AUTO_BOOT_WAIT_STORAGE`（若卡還沒掛好）與 `AUTO_BOOT_STORAGE_DONE`；ES-DE 不應被開成全新設定。沒有 SD 卡時不應有延遲。
- **預設檔**：設定頁和 onboarding 各按一次「新增」再取消，`/Android/data/xyz.blacksheep.mjolnir/gestures/` 不應多出 `untitled-N.cfg`；編輯現有預設檔、不改名直接存，檔名不應變成 `-2`。

## 未移植：會改變行為（需實機驗證）

### A. TOP: Home／BOTTOM: Home 只影響一個螢幕（上游 #34、#35，`f33cdea` 前半）
- **問題**：`GLOBAL_ACTION_HOME` 在 Thor 上兩個螢幕都會回桌面，TOP/BOTTOM: Home 實際上等於 BOTH: Home。我們的程式也是這樣（`HomeActionLauncher` 三處 `GLOBAL_ACTION_HOME`）。
- **對方做法**：預設桌面是一般第三方桌面時，用 `getLaunchIntentForPackage`（取不到才用 HOME intent）直接開在目標螢幕；Mjolnir 本身、Quickstep／Odin、系統選擇器（`android`）、另一個槽位用的同一個 App，則退回舊做法。BOTH: Home 維持舊做法。
- **風險**：「回桌面」變成「啟動 App」。有些桌面用 LAUNCHER intent 開啟時，不會回到首頁或會保留原本的返回堆疊；也繞過了 `FocusHackHelper` 的路徑。與 TODO 第 9 項（`buildDefaultHomeIntent()` 解析錯誤）相關。

### B. SafetyNet 被露出時重新開啟該螢幕的 App（上游 #31，`f0f01c0`、`33776da`）
- **問題**：遊戲關閉後，下面沒有其他工作時會停在黑色的 "You should not be here." 畫面，HDMI 螢幕上無法離開。可能與 TODO 第 1 項（上螢幕偶發停在 SafetyNet）是同一個現象。
- **對方做法**：`SafetyNetActivity.onRestart()` 重新開啟該螢幕的槽位 App，5 秒內再次露出就不重開（防止無限迴圈）；空槽位與 Quickstep／Odin 槽位跳過；SafetyNet 只放在內建的兩個螢幕；Swap 與 FOCUS: <Top app> 先呼叫 `holdOffReopen()`，否則 App 會被拉回原螢幕（對方第一版就有這個 bug，review 後才補上）。
- **風險**：任何「把 App 移到另一個螢幕」的動作都要記得呼叫 `holdOffReopen()`，容易漏。`onRestart` 也會在其他情況觸發（例如從最近使用切回來）。
- **先確認**：我們 v0.2.8 已記錄 SafetyNet `VISIBLE`／`HIDDEN`，先從 field log 看露出的頻率和前後事件，再決定是否需要。

### C. 手勢選單每個動作下方的一行說明（`f56609f`）
- 只是 UI，不影響行為。這次範圍只放修正，所以沒做；要做時可直接移植 `HomeMappings.actionDescription()` 與兩個 `GestureDropdown`。

## 不採用

- **Hide from Recents**（上游 PR #40，對方 `dbc974a` 等 5 個 commit）：理由見 [`2026-10-10-hide-from-recents.md`](2026-10-10-hide-from-recents.md)。對方的後續修正（Basic 模式、FOCUS: <Top app>、窄螢幕版面）沒有處理 AOSP `RecentTasks` 保留主螢幕最近工作的限制。
- **使用手冊、截圖與 GIF**（`fefbe01`）：內容針對對方的版本與 Hide from Recents。

## 對方沒有、我們有的

對方 v0.3.0 沒有我們 v0.2.8 的穩定性修正：設定檔防當機寫入、用套件名稱啟動、FOCUS: Home 路由與手勢狀態機（ghgoodreau）、無障礙服務重啟後 10 秒卡頓、icon 延遲載入、`custom.cfg` 遷移無限複製、診斷 log 加強。
