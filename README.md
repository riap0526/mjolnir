# **Mjolnir** (riap0526 fork)

> **This is a personal-use fork of [blacksheepmvp/mjolnir](https://github.com/blacksheepmvp/mjolnir).** It focuses on **stability fixes and small improvements** to the existing features; there are no major changes or new features. See [Disclaimer](#%EF%B8%8F-disclaimer) and [Credits](#-credits) before using it.

## ⚔️ Mjolnir Dual-Screen Update

Mjolnir is a Home button router built for multi-display Android devices. It gives you precise control over what happens when you press the Home button — routing frontends or launchers to the top display, bottom display, or both.  
With **BOTH: Home**, dual-screen frontends (iisu, cocoon, console launcher, etc.) can launch both screens with a single button press.  
With **FOCUS: Home**, you can do the same but only for the selected screen.  
Prefer mixing single-screen activities? **BOTH: Auto** and **FOCUS: Auto** are here.  
**TOP/BOTTOM** options remain for full customization.

Originally designed for the **AYN Thor**, it also works on single-screen devices as a Home-button automation tool. **Device support:** AYN Thor is the primary supported target; RG-DS is not officially supported.

[Releases](https://github.com/riap0526/mjolnir/releases) | [Add to Obtanium](https://apps.obtainium.imranr.dev/redirect.html?r=obtainium%3A%2F%2Fapp%2F%7B%22id%22%3A%22xyz.blacksheep.mjolnir%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2Friap0526%2Fmjolnir%22%2C%22author%22%3A%22riap0526%22%2C%22name%22%3A%22Mjolnir%22%2C%22apkUrls%22%3Anull%2C%22otherAssetUrls%22%3Anull%2C%22preferredApkIndex%22%3A0%2C%22additionalSettings%22%3A%22%7B%5C%22includePrereleases%5C%22%3Afalse%2C%5C%22fallbackToOlderReleases%5C%22%3Atrue%2C%5C%22verifyLatestTag%5C%22%3Afalse%2C%5C%22sortMethodChoice%5C%22%3A%5C%22date%5C%22%2C%5C%22trackOnly%5C%22%3Afalse%2C%5C%22versionDetection%5C%22%3Atrue%2C%5C%22autoApkFilterByArch%5C%22%3Atrue%7D%22%2C%22overrideSource%22%3A%22GitHub%22%2C%22allowIdChange%22%3Afalse%7D)

---

## 🔱 What's Different in This Fork

Based on upstream `v0.2.7a`. Full details in [CHANGELOG.md](./CHANGELOG.md) and [`docs/notes/`](./docs/notes/).

**v0.2.8a**

- Removed the in-app donation button; About links to this fork and credits the original project. No behavior changes.

**v0.2.8**

- **Settings survive crashes and power loss**: settings and gesture presets are written durably (fsync + `.bak` backup). A corrupt file is restored from the backup and kept as `.corrupt`; deleting a file still resets it as before.
- **Hidden or blacklisted apps no longer break the Home button**: configured apps are launched directly by package name, so they no longer fail and wipe the configuration after 3 attempts.
- **FOCUS: Home routes to the focused display** and the Home gesture state machine ignores key repeats, unpaired events and the release after a long press (merged from [ghgoodreau](https://github.com/ghgoodreau/mjolnir)'s `fix/focused-home-routing`).
- **Custom gesture preset no longer multiplies into `untitled-N.cfg` files.**
- **No 10-second freeze after the accessibility service restarts.**
- **Lighter background process**: app icons are only loaded when a Mjolnir screen is opened, and released when it is hidden.
- **Thread-safety and fewer redundant restarts** of the keep-alive service; no-op settings writes are skipped.
- **Better diagnostics**: previous process exit reason (incl. whether it was before boot), SafetyNet visibility, HomeActivity decisions and raw Home key events.
- **Signed release builds via GitHub Actions** (see [`docs/BUILDING.md`](./docs/BUILDING.md)).

> **Switching from the upstream APK:** this fork is signed with a different key, so Android requires uninstalling the upstream app first, which **deletes `/Android/data/xyz.blacksheep.mjolnir/`**. Back that folder up first. Step-by-step instructions: [`docs/BUILDING.md`](./docs/BUILDING.md#從原作者版本換成本-fork重要).

---

## 📖 Features & Usage

All features, setup steps and behavior come from the original project. At this stage this fork has **no new features and no major functional changes** — only the fixes listed above.

For detailed feature descriptions, setup instructions (Basic / Advanced mode, permissions, gestures, Safety Net, Quick Tiles, Steam File Generator) and how Mjolnir works, see the original repository: **[blacksheepmvp/mjolnir](https://github.com/blacksheepmvp/mjolnir)**.

Settings and logs live in `/Android/data/xyz.blacksheep.mjolnir/`. Building and release signing for this fork are documented in [`docs/BUILDING.md`](./docs/BUILDING.md).

---

## 🤝 Contributing

Bug reports are welcome via [Issues](https://github.com/riap0526/mjolnir/issues). This fork is maintained mainly for personal use, so feature requests may not be picked up.

---

## 🙏 Credits

- **[blacksheepmvp](https://github.com/blacksheepmvp)** — original author of Mjolnir. This fork is based entirely on the code of [blacksheepmvp/mjolnir](https://github.com/blacksheepmvp/mjolnir); all original design and features are theirs.
- **[ghgoodreau](https://github.com/ghgoodreau)** — focused-display Home routing and Home gesture state hardening ([`fix/focused-home-routing`](https://github.com/ghgoodreau/mjolnir/tree/fix/focused-home-routing)), merged into this fork with authorship preserved.

---

## ⚠️ Disclaimer

- This fork is maintained **primarily for personal use** and is limited to **stability fixes and small improvements** to existing behavior. It does not aim to add major features or redesign the app.
- The fork exists because the upstream project has not been updated for more than six months; fixes are made here for the maintainer's own device (AYN Thor, Android 13).
- Changes in this fork are made **with AI assistance** and tested mainly on that single device.
- It is **not affiliated with or endorsed by** the original author. Please do not report issues from this fork to the upstream project.
- Provided **as is, without warranty**. Evaluate for yourself whether to use this fork; back up `/Android/data/xyz.blacksheep.mjolnir/` before installing.
