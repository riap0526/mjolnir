# CLAUDE.md

## 溝通

- 本專案內一律以**繁體中文**（台灣用語、全形標點）回覆。專業術語附上英文。
- Commit message、程式碼註解、CHANGELOG 與 `app/src/main/assets/changelogs/` 維持英文；`docs/notes/` 與 `docs/TODO.md` 用繁體中文。

## 專案慣例

- 這是 [blacksheepmvp/mjolnir](https://github.com/blacksheepmvp/mjolnir) 的個人維護 fork，以穩定性修正為主。會改變開機、桌面或啟動行為的修改，必須先在 AYN Thor 實機驗證；未驗證的只記錄到 `docs/notes/` 與 `docs/TODO.md`。
- 使用者提供的診斷 log 分析結果記錄在 `docs/notes/YYYY-MM-DD-*.md`。
- 建置：`scripts/setup-android-sdk.sh` 後執行 `./gradlew assembleDebug testDebugUnitTest`。推送後 GitHub Actions 會產生簽章 release APK（見 `docs/BUILDING.md`）。
