# Memories of Eternity Update Server

## Current release: B27

Mixed suit banners containing US suits now have a combined 1% random US chance. When all archived US tiers are present, the per-draw chances are US 0.85%, US+1 0.13% and US+2 0.02%. Missing tiers redistribute their share over the eligible US variants; no suit is added to a banner. US-only banners and explicit fixed-grade/featured guarantees remain intact. Existing non-US draw weighting remains unchanged.

The actual remodel table identifies US upgrade tiers; the event table's stale step-zero fields are not used for these weights. No US+3 is present in the archive. Gacha validation passed 108 deterministic checks across all 41 original event pools, plus one million seeded draws. Both signed APK variants and the release manifest are verified. B27 has not received a new in-game/device playtest; B26's previously verified save/native components and assets are retained.

## Retained B26 features

Newly acquired Pixies with an archived Prototype family receive their own literal C-grade Preferred Prototype suit: 25 original Pixie-to-suit associations, each with original team cost 2. Fresh Leahs and Puris retain level 5; later recruits retain level 1. Existing saved equipment and levels stay unchanged. Miku has no archived C-grade Prototype Harmony and keeps her existing B-grade Common Desert Service Support (suit 10110); original recruitment conditions and Heart costs are retained.

This update also includes B25 inventory expansion. Starting capacity remains 100; suits and parts can each expand to 5,000 slots with the original +10 increments, costing 100 Gems per suit expansion or 200 Gems per parts expansion. Capacity and payment persist together per selected save, with no charge on rejection or failed saving. No component purchase is invented; its original tab remains disabled, and Temple storage stays separate.

All 25 Prototype models/icons already exist in compatible installed resource splits. No asset replacement is needed. B26 passed 774 runtime assertions and 571 package checks, plus real production/recovery/cleanup checks. Offline play, all three save profiles and earlier B24 features are retained.

## Retained B24 and earlier features

Hangar locks for suits, parts and components restore after offline inventory loading and persist immediately after lock or unlock, including a cold restart before leaving Hangar. Native preference keys/schema, selected-save isolation, ownership, resources and unrelated settings are retained.

B24 consolidates equivalent dormitory showroom purchase listings. All 283 archived decoration IDs remain valid; different prices, earned set rewards, scores and gameplay effects remain distinct products. Existing owned IDs and room placements are retained, and an equivalent owned product can be reused without buying another raw catalog ID. Original prices and room-specific collections remain in place.

This release also includes the B22 changes, which were previously available only as a local playtest:

- Seven beginner Draw banners (shop IDs 4–10) each allow one purchase per selected save per local calendar week, resetting Monday 00:00. Original Gem prices are retained.
- Three other supported paid suit-draw banners rotate every 30 minutes using persistent per-save selections. Reopening the shop or restarting does not reroll them. Friendship single/ten draws (IDs 13/14) remain permanently available outside those three slots at their original CP prices.
- AP cap 60, regenerating 1 AP every 15 minutes. BP cap 20, regenerating 1 BP every 15 minutes. Manual content retains 0 AP/0 BP costs; eligible Auto Clear consumes BP.
- The seven Gem bundles grant 10/50/160/330/560/850/1200 Gems for 5/10/20/30/40/50/60 AP respectively.
- Daily Missions uses Normal Dailies, Quick Missions, One-Time Missions and Events tabs. Normal Dailies is the default. Each of the seven tagged Crossover missions can be completed once per local clock hour, with per-save receipts and an Events countdown. Daily list initialization/loading transitions are corrected.
- A protected Admin testing panel requires authentication and a verified selected-save backup before mutations. Restore returns that save to its pre-Admin baseline; other saves and updater files are preserved. No passcode is distributed in public release sources.
- Updated interfaces retain the original game's styling, including the title-screen Update button and the save/name controls.

Previously published B21 features remain included: all 283 furniture IDs available offline with original prices and earned set rewards; 70 HOT cards at 20 AP with their progression/numeric limits; 50,000 Credits per clear for the six Pension missions; per-save Codex acquisition/episode requirements; three save profiles, Captain names, typed RESET and offline helpers. The 26-Pixie helper roster refreshes every 30 minutes, with a 100-Gem paid refresh.

## Android phones

Keep the existing game and resource splits installed. Use **Update** on the title screen, or install **MOE-Android-Update-Installer.apk** (version 1.4/code 5) for an older supported build. Approve Android's installation prompt when shown. The helper selects the matching canonical or standard SAI signing variant and updates the base APK while retaining compatible installed resource splits and local saves.

Do not uninstall the game or clear its data. A package/signature mismatch is rejected before installation. An installation re-signed with a different private key requires a matching build; the updater does not bypass Android signature checks. This release updates an existing asset installation and does not include a fresh full asset set.

Update checks are optional. Offline play remains available. The installer may need Android's permission to install an update from this source; USB debugging is not required. Older APK-only clients that predate the native updater may need the standalone helper once before future title-screen updates work.

## Windows playtest

Download **MOE-Windows-Updater-B27.zip** and extract it into the Windows launcher folder. Keep the installed game, local saves and compatible resource splits. Start your dedicated **MOE-Split-Test** emulator yourself, then explicitly supply its serial and the ADB path; for example, `Launch-MOE.cmd -Serial emulator-5560 -AdbPath "C:\Android\platform-tools\adb.exe"`. Use the actual serial of your running test emulator; the example is not selected automatically.

To check or download an update without accessing a device, run `powershell -File .\Update-MOE.ps1 -Mode Check` (add `-AcceptUpdate` to accept a download). To apply it, use `powershell -File .\Update-MOE.ps1 -Mode Update -Serial <your-test-serial> -AdbPath <your-adb-path>`; rollback uses `-Mode Rollback` with the same explicit target. Updates and rollback require root access already active. Default caches/backups are separated by serial, and rollback rejects a backup for another target.

These scripts never start, stop or restart an emulator, change its network settings, restart its ADB daemon or select a connected device automatically. The `emulator-5556` device and the **MOE** AVD are protected and always rejected. Only the explicitly selected **MOE-Split-Test** AVD is supported. The ZIP contains the signed base APK and updater scripts; it is not a standalone Unity Windows conversion.

## Verification and save compatibility

The update manifest is signed with the existing release key. Payload sizes, SHA256 hashes, package identity and signing certificates are checked before installation. Save schema 1 and the existing Android application ID are retained. Asset updates, if ever declared, remain optional and must use a separately signed declaration; no asset rewrite is required for this release.

The canonical game certificate is `2c616f46dd007026571ba37ca5dfd291dd2ad36cd9aeee8620fd966e6e1886e8`. Standard SAI uses `a40da80a59d170caa950cf15c18c454d47a39b26989d8b640ecd745ba71bf5dc`.

## Publishing

`release/build-release.py` reconstructs the already-signed B27 canonical APK from the pinned public B21 canonical APK using verified delta chunks, reconstructs the matching SAI variant, verifies the signed manifest, and builds the deterministic Windows ZIP. The repository stores bounded text transfer chunks and public updater sources; it does not contain private signing keys, local saves or the original asset archive. Existing release tags and files are retained.
