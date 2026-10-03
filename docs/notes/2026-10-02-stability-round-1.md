# 2026-10-02 穩定性修正第一輪

分支：`claude/intelligent-allen-jbocja`　基準：原作者 `v0.2.7a`（`7f6b5a7`）

## 背景

接手原作者已停止維護的專案後，先做了一次完整的程式碼審查，再分析一份使用者實機的診斷 log（AYN Thor、Android 13，2026-08-29 到 10-01，約 2.7 萬行，涵蓋 10 次開機）。這一輪的原則是**安全穩定優先**：只修有證據或風險明確的問題，不改變正常流程的行為，不做大型重構。

log 中有直接證據的問題：

- **09-07 一次異常重開後，設定被部分清掉**。log 檔留下 1,290 個 NUL 位元組，這是資料還沒寫進儲存就斷電或當機的典型痕跡。重開後手勢預設從使用者自訂的 `untitled-1.cfg` 退回內建的 Type-C，ROM 資料夾路徑也消失了，直到 09-09 使用者手動改回來。
- **Mjolnir 行程重啟 49 次，只有 10 次是開機**，常常幾秒內連續重啟 2 到 5 次。原因待新的診斷 log 確認。
- **Thor 的 `focus_change` 系統設定值是 188、215、790 這類數字**，原本的程式卻把它當成螢幕 ID 使用（實際螢幕 ID 是 0 和 4）。
- **長按 Home 放開後**，會多出一次無效的手勢判斷（`pressCount=0`），共 24 次。

## 改動內容

### A. 本分支的修正

| Commit | 內容 | 對使用者的影響 |
|---|---|---|
| `024541d` | 設定檔與手勢預設檔改成可靠寫入：先 fsync 寫入 `.bak`，再 fsync 寫入暫存檔後改名。檔案存在但損毀時改讀備份，損毀的檔案保留成 `.corrupt`；檔案被刪除則照常視為重設。舊版偏好設定只補缺少的值，不再覆蓋現有設定。舊版手勢名稱改為寬鬆解析，不再造成崩潰。 | 異常斷電或當機後，設定不會再被悄悄重設 |
| `d41bffd` | 啟動 App 改成直接用套件名稱解析，不經過黑名單和「Show all apps」過濾。log 只在真的啟動成功時寫 `LAUNCH_SUCCESS`。`MAIN_SCREEN` 值非法時改用預設值，不再崩潰。 | 隱藏或過濾已設定的 App 不會再導致啟動失敗與設定清空；按 Home 不必每次掃描所有 App |
| `333a70d` | 圖示快取改成執行緒安全；只有常駐服務不在時才重新啟動它，不再每按一次 Home 就重啟。 | 去除開機時偶發崩潰的風險；按 Home 少一段延遲 |
| `5d5992f` | 設定值沒有改變時跳過寫檔。 | 避免 fsync 拖慢 Home 鍵（HomeActivity 每次都會把失敗次數重設為 0） |
| `795b328` | 診斷 log：單一執行緒依序寫入、時間戳在呼叫時取得；行程啟動時記錄上一次結束的原因；記錄設定檔復原、HomeActivity 的判斷、SafetyNet 的啟動與顯示；診斷關閉時不建立視窗 dump。 | 下次出問題時能從 log 判斷原因 |
| `b5fc9e7` | release 簽章設定、SDK 安裝腳本、`docs/BUILDING.md`、`docs/TODO.md`、CHANGELOG。 | — |
| `9563921` | GitHub Actions：每次 push 建置 debug APK 並跑測試；有簽章 secrets 時產出簽章版 release APK；push `v*` tag 時自動建立 Release。 | 不需要本機環境就能取得簽章版 APK |

### B. 合併 ghgoodreau 的修正（`923004e`）

來源：<https://github.com/ghgoodreau/mjolnir>，分支 `fix/focused-home-routing`。commit 作者資訊完整保留。

| 原 commit | 內容 |
|---|---|
| `4bab30e` fix: route focused home to the active display | `focus_change` 的值必須是現存的螢幕 ID 才採用，否則改為掃描視窗判斷焦點。「FOCUS: Home」改為把桌面開在有焦點的螢幕，無法判斷時才退回系統 Home 鍵。 |
| `7c48b56` fix: harden home gesture state transitions | 抽出 `HomeGestureState` 並附單元測試：忽略按鍵自動重複和沒有配對的按下或放開事件、長按觸發後忽略隨後的放開事件、防連按時把計數歸零。每次 Home 鍵事件都寫一行 `HOME_KEY_EVENT` log。 |

**合併時的調整**：只有 `HomeKeyInterceptorService.kt` 一處衝突。採用他重構後的焦點處理結構，並補回本分支「診斷關閉時跳過視窗 dump」的保護。他的版本在這裡會無條件逐一查詢每個視窗。

## 驗證狀態

| 項目 | 結果 |
|---|---|
| `assembleDebug` | ✅ 通過 |
| `testDebugUnitTest` | ✅ 9 個測試全部通過（範本 1 個、`HomeGestureStateTest` 5 個、`FocusedDisplayResolverTest` 3 個） |
| 簽章版 `assembleRelease` | ✅ 本機以臨時金鑰驗證過；之後改由 CI 使用正式金鑰 |
| `lint` | 有 14 個錯誤，全部在原本的程式碼中，這一輪沒有新增 |
| **實機測試** | ❌ **尚未進行** |

## 實機測試清單

安裝前請先看下方的「安裝注意事項」。

1. **基本啟動**：開機後上下螢幕都正確出現 ES-DE 與 Smart Launcher；單擊、雙擊、三擊、長按各試幾次，確認行為與設定一致。
2. **長按**：長按觸發動作後放開，不應該再觸發其他動作。
3. **黑名單**：把已設定的 App 加進黑名單，按 Home 仍應正常啟動（以前會失敗，3 次後清空設定）。
4. **FOCUS: Home**（如果有使用）：焦點分別在上、下螢幕時各試一次，確認桌面出現在正確的螢幕。
5. **設定保存**：改幾項設定後強制停止 Mjolnir 再打開，設定應該都在；`/Android/data/xyz.blacksheep.mjolnir/` 下應出現 `.bak` 檔。
6. **診斷 log**：確認 log 中出現 `PREVIOUS_PROCESS_EXIT`、`HomeActivity START`、`SafetyNet ...` 等新事件。

## 下次 log 要看什麼

| 事件 | 用途 |
|---|---|
| `PREVIOUS_PROCESS_EXIT reason=...` | Mjolnir 上次怎麼結束的：`USER_REQUESTED` 多半是全部清除，`LOW_MEMORY` 是記憶體不足，`CRASH` / `ANR` 是自身問題 |
| `SETTINGS_LOAD_ISSUE` | 設定檔損毀，且從備份復原過 |
| `SafetyNet VISIBLE` / `HIDDEN` | 「You should not be here」畫面出現在哪個螢幕、什麼時間 |
| `HomeActivity START`、`LAUNCH_*` | 系統叫起 Mjolnir 當桌面的時間點，以及 App 是否真的啟動成功 |
| `HOME_KEY_EVENT`、`GESTURE_EVENT_IGNORED` | Home 鍵原始事件，以及被忽略的事件與原因 |

ES-DE 本身的結束原因可以用 `adb shell dumpsys activity exit-info org.es_de.frontend` 查詢。

## 安裝注意事項

- 本 fork 使用不同的簽章金鑰，**無法直接覆蓋安裝原作者的版本**，必須先解除安裝。解除安裝會刪除 `/Android/data/xyz.blacksheep.mjolnir/`，請先備份。完整步驟見 `docs/BUILDING.md`。
- 從 debug 版換成簽章版時同理，因為兩者的簽章不同。

## 延後處理的項目

見 `docs/TODO.md`。最重要的幾項：兩條啟動路徑不協調（「You should not be here」的根本修法）、Thor 全部清除會殺掉 Mjolnir、Android 14 以上的前景服務類型、螢幕身分判斷、FOCUS: Home 的實機驗證。

## 後續修正（2026-10-03，依實機 log）

使用者裝上第一版約一天後的 log（2026-10-02 20:25 到 10-03 18:11）找到兩個問題：

| Commit | 問題 | 修正 |
|---|---|---|
| `1667ac3` | **合併 ghgoodreau 的修正後帶進來的退化**：無障礙服務連線時會掃描視窗，Thor 上這個呼叫卡住主執行緒整整 10.0 秒，回傳 0 個視窗。這段時間按 Home 鍵不會被處理。log 中兩次重啟都發生，其中一次在解除卡住後 60 毫秒被 SIGKILL。 | 連線時不再掃描視窗，焦點改由第一個無障礙事件取得（原作者版本也是如此）。 |
| `eb4e93c` | **自訂手勢預設不斷產生新檔案**：舊版遷移程式每次讀取時都把 `custom.cfg` 改名成 `untitled-N.cfg`。使用者把預設命名為「custom」時，檔名正好是 `custom.cfg`，於是每次讀取都被改名一次；而它仍是使用中的預設，讀取時又被重建成預設值內容，下次再被改名，循環下去。log 中可以看到 `preset=custom.cfg` 一次連續出現 8 次。 | 舊版遷移只在每次安裝時執行一次（新增內部旗標 `legacy_custom_preset_migrated`）；改名的若是使用中的預設，同步更新使用中的檔名。 |

**使用者需要手動處理**：已經產生的多餘 `untitled-*.cfg` 不會自動刪除，請在 App 內刪除或直接刪檔。這些檔案中，最早被改名的那一個（編號最小）保存的才是當初設定的手勢內容，之後的都是預設值。

另外確認：
- 喚醒時的重啟原因是 `SIGNALED`（SIGKILL），不是記憶體不足，詳見 `docs/TODO.md` 第 2 項。
- 安裝後首次設定期間出現 5 次 `CONFIG_WIPE reason=invalid_configuration`：當時設定還是空的，Mjolnir 已是預設主畫面，按 Home 會被導回初始設定。這是預期行為，沒有資料被刪除。

