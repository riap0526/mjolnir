# 2026-10-31 開機後上螢幕停在 SafetyNet（log 分析）

版本：v0.2.8b（versionCode 25）。log 範圍 10-23 到 10-31（1 MB 輪替，只保留最近的部分）。

## 現象

10-31 01:00 開機後，上螢幕顯示 "You should not be here."，ES-DE 沒有自己回來。使用者長按 Home（TOP_HOME）後 ES-DE 才重新開啟。

## 時間軸（10-31）

| 時間 | 事件 |
|---|---|
| 01:00:34.2 | Mjolnir 開機啟動。`HomeActivity` 先在下螢幕（`SECONDARY_HOME`）、再在上螢幕（`HOME`）各執行一次 `LAUNCH_BOTH`，並在兩個螢幕放 SafetyNet |
| 01:00:35.7、36.8 | Mjolnir 行程兩次被 `LOW_MEMORY` 殺掉，第一次 `importance=100`（前景） |
| 01:00:39.8 | 第三次啟動，無障礙服務連線，`AUTO_BOOT_ACTION BOTH_HOME`。開機計數在這時標記為已處理（116 → 117） |
| 01:00:39.9 | 先開下螢幕 Smart Launcher（v0.2.8b 的 #38 順序正確），接著觸發 focus lock workaround |
| 01:00:40.085 → 45.086 | **主執行緒卡住 5.0 秒**：焦點觀察器的 `updateFocusFromAccessibilityWindows()` 呼叫 `chosen.root?.packageName`（跨行程 IPC），逾時後得到 `pkg=null`。延遲 250 ms 的 `SENDING_UPDATE_STATUS_INTENT` 也拖到 45.087 才執行 |
| 01:00:45.1 | FocusStealer 建立，ES-DE 才啟動（`LAUNCH_SUCCESS`），45.3 上螢幕出現 ES-DE 視窗 |
| 01:00:50.6 | 上螢幕出現 `com.android.launcher3` 視窗事件（原因不明，可能是 ES-DE 結束時的工作切換） |
| 01:00:53.5 | Mjolnir 第三次被 `LOW_MEMORY` 殺掉。重啟時 SafetyNet 在上螢幕重建並 `VISIBLE`，表示 ES-DE 已經不在上螢幕 |
| 01:00:53.8 | `AUTO_BOOT_SKIPPED reason=not_fresh_boot`：開機動作已用掉，沒有任何機制重開 ES-DE |
| 01:01:08.1 | 使用者長按 Home → `TOP_HOME` 開 ES-DE，SafetyNet `HIDDEN`，恢復 |

對照 10-30 正常開機：沒有任何 `LOW_MEMORY`，開機動作 0.6 秒內完成，焦點解析也沒有卡住。

## 問題

1. **ES-DE 開啟約 5 到 8 秒後就離開上螢幕**（推定，未證實）。同一段時間 Mjolnir 被系統以記憶體不足殺了 3 次（含前景狀態），ES-DE 很可能是同一波被殺。也可能是 ES-DE 自己結束。要確認需在下次發生後盡快執行 `adb shell dumpsys activity exit-info org.es_de.frontend`，看 ES-DE 的結束原因與時間。與 TODO 第 8 項（記憶體回收風暴）相關。
2. **沒有恢復機制**：開機動作只執行一次，且在「送出啟動」時就標記完成，不確認 ES-DE 是否留在畫面上；SafetyNet 被露出時也只是停著。這正是 Etsum fork 的 #31 處理的情境（見 [`2026-10-19-etsum-fork-port.md`](2026-10-19-etsum-fork-port.md) B 節）。
3. **主執行緒 5 秒卡住**：焦點備援解析在主執行緒呼叫 `AccessibilityWindowInfo.getRoot()` 只為了記錄套件名稱。系統忙碌時這個 IPC 會等到 5 秒逾時，期間 Home 鍵處理、開機動作全部停住。整份 log 101 次解析中只發生這 1 次（另 1 次 0.5 秒），但正好發生在開機最忙的時候，讓 ES-DE 晚了 5 秒才啟動。
4. **開機時重複啟動**：`HomeActivity` 在兩個螢幕各 `LAUNCH_BOTH` 一次，5 秒後開機動作再啟動一次，等於上下 App 各被啟動 3 次。記憶體吃緊時更容易觸發回收。即 TODO 第 1 項。
5. **次要**：
   - `PREVIOUS_PROCESS_EXIT` 在 10-30 和 10-31 都顯示同一筆 10-29 的紀錄（10-30 晚上那次關機沒有留下結束紀錄），容易誤讀。
   - 開機時系統校時讓 log 時間倒退（10-30 00:37:01.9 之後出現 00:36:53），看時間軸要注意。
   - 10-23 22:46 的 `SIGNALED` 前 6 秒有 `launcher3` 視窗，與 TODO 第 2 項「全部清除」的推測一致。

## v0.2.8b 修正的觀察

- BOTH 的啟動順序（#38）：開機動作先開下螢幕、最後開上螢幕，正確。
- SD 卡等待：兩次開機都沒有 `AUTO_BOOT_WAIT_STORAGE`，表示服務連線時儲存空間已就緒，沒有額外延遲。

## 建議

| 項目 | 風險 | 建議 |
|---|---|---|
| 3. 焦點解析不再呼叫 `getRoot()`（改用不需 IPC 的 `window.title`，或不記錄套件名稱） | 低，只影響 log 內容 | 直接修 |
| 2. 開機後恢復：開機動作後一段時間內（例如 60 秒），SafetyNet 在上／下螢幕變成 `VISIBLE` 時重開該槽位 App 一次 | 中，會改變行為 | 範圍比 Etsum 的 #31 小（只在開機後、只重試一次），需實機驗證 |
| 1. 確認 ES-DE 結束原因 | — | 下次發生後執行 `dumpsys activity exit-info` |
| 4. 開機重複啟動 | 中 | 維持 TODO 第 1 項，等 2. 有結果再一起處理 |
