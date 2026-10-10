# 2026-10-10 「從最近使用隱藏」實機評估（不採用）

來源：上游 issue #39 / PR #40（alexboydray），每個槽位可選擇以 `FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS` 啟動。曾以 0.2.9 測試版（versionCode 22、23，未發佈）實作並實機測試，最後**決定不採用**，正式版改發 0.2.8a（只移除捐款按鈕）。

## 實機結果（AYN Thor、Android 13）

| 項目 | 結果 |
|---|---|
| 旗標是否送出 | 是。log `LAUNCH_INTENT flags=0x10800000 hideFromRecents=true`，`dumpsys activity recents` 中兩個工作都是 `flg=0x10800000` |
| 下螢幕 Smart Launcher（display 4） | 穩定隱藏 |
| 上螢幕 ES-DE（display 0） | 只要它是主螢幕最近使用的工作就會顯示。這是 AOSP `RecentTasks` 的規則：主螢幕最近的工作即使被排除仍保留。ES-DE 幾乎永遠是上螢幕最近的工作，所以實際上幾乎不會隱藏 |
| 已存在的工作 | 旗標只在建立新工作時生效；開關打開前建立的工作會一直沿用、不會隱藏 |
| 「全部清除」、強制停止 | 有無上鎖、有無隱藏都殺不掉顯示中的 ES-DE / Smart Launcher。`am force-stop` 前後 ES-DE 的 PID 相同（4556），Thor 對前景 App 似乎有額外保護 |

## 決定

效果只是讓最近使用清單乾淨一點，且上螢幕幾乎無效、隱藏後也無法上鎖，實質意義不大。不做。

## 附帶修正的推測

週 log 中 Mjolnir 三次 `SIGNALED`，原推測為「全部清除」。這次測試顯示全部清除不一定會殺掉不在清單中的 App，該推測不確定，之後若再發生需重新判斷。
