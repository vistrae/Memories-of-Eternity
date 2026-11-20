# Memories of Eternity offline playtest updates

## Current release: B20

B20 includes all 26 playable Pixies as temporary helpers, with compatible mechas through US grade under the highest eligible owned-grade ceiling. Each save keeps a stable roster and a 30-minute timer across picker openings, missions and restarts. Automatic expiry is free; the original-style Refresh 100 Gems button rerolls the mechas and resets the timer. Levels and the grade ceiling are captured at refresh. The B18 local profiles, Captain names, selected-save RESET and original future mission rewards remain available. Existing saves and compatible resource splits are retained; offline play is preserved.

## Android phones with an existing offline installation

Download MOE-Android-Update-Installer.apk from the latest release and update or install the small helper. Keep the existing MOE game installed. Open MOE Update Installer, check for updates, download the verified update, and approve Android's installation prompt. If asked, allow the helper to install updates, return, and check again. Existing saves and installed asset splits are retained. Do not uninstall the game or clear its data. No USB debugging is needed.

B20 supports the original offline-playtest signing identity and the standard SAI signing identity. The installer selects a signed release APK only when its signing identity matches the installed game. Android's normal package, version, and certificate checks remain enforced. Other signing identities stay unsupported; use Installation details, then Copy details, to identify the installed package, version, and public certificate fingerprints. Do not uninstall an incompatible installation before preserving its save. For new installations, keep SAI's Sign APKs option disabled and preserve the supplied signatures.

After updating, use Update on the game's title screen for future updates. Checking is optional. Offline play remains available and network failures leave the current game usable. The button is hidden after Start. Android still requires approval for APK installation.

The helper requires an existing com.nexon.moe.global offline installation with a supported signing identity and matching package version. Android 5 or newer and support for 32-bit ARM apps are required. Validation uses an Android 11 emulator; the user's remote LDPlayer installation and physical phones have not been directly verified. The helper is intended for the initial APK bootstrap; use the game itself for future content updates.

## Windows playtest

Use Launch-MOE.cmd in the existing playtest outputs folder. The launcher checks GitHub and can continue offline when checks fail. Launch-MOE.ps1 -SkipUpdateCheck skips checking. MOE-Windows-Updater-B20.zip includes the updater files and the canonical base APK; it requires the existing project/emulator/assets installation. It does not include the SAI-compatible APK.

Update-MOE.ps1 -Mode Check checks manually. Update-MOE.ps1 -Mode Rollback restores previous game files while retaining the current save. Installed APK and save backups are kept before Windows updates. Android provides previous asset-selection restoration; it does not silently overwrite saves from a backup.

## Verification and asset updates

Release manifests are signed with a pinned RSA key. Downloads are checked against signed sizes and SHA256 values. An optional apk_variants list names independently signed APKs and their required installed certificate fingerprints. New Android updaters select only a matching variant, then verify the APK's actual signing identity. Earlier canonical clients and Windows continue using the default APK.

APK updates use Android install sessions that inherit existing resource splits. Compatible asset updates use a private cache and atomic asset selection, without modifying original installed assets or saves. Corrupt overlays fall back to original assets. Opening/prologue movie replacement is excluded from this asset format. Engine format, signing identity, package version, or save-schema changes need compatible migration work.

## Publishing

New-MOERelease.ps1 signs future releases locally. Keep the private signing XML and Android keystore private; neither belongs in this repository. Current packaging reconstructs the already-signed B20 canonical APK from the verified public B18 APK and a binary delta. A second delta reproduces the SAI-compatible APK from the canonical B20 file. The release builder verifies both against the signed manifest and checks that their game content is identical. Signed manifest bytes are stored in base64 to preserve them exactly. GitHub Actions verifies reconstructed APKs before uploading and publishing release files using its temporary token. Existing successful releases remain immutable.

APK changes still need an in-place Android installation. The updater avoids reinstalling the complete asset collection; asset-only releases can download just changed files.

## Retained B15 additions

The bottom-left title label shows only B15, using the current build number automatically. The helper now offers copyable installation details and identifies unreadable APKs, package/version differences, missing certificates, and signing mismatches separately. Vendor APK parsers can retry a verified copy with an .apk filename. The Update button reattaches if Android recreates the game activity after visiting installation settings. Supported standard-SAI installations can receive a matching signed APK while preserving their existing resource splits and local save.

## Retained B14 additions

The Team editor handles empty teams. All 608 archived outfit offers stay available without date limits; original non-date unlock requirements remain enforced. The 25 originally unpriced offers cost 1,200 Gems. Options offers Reset Local Save with a typed RESET confirmation, a verified recovery copy, and installed assets retained. Resetting closes the game; reopen to start fresh. Gem packages grant 10/75/240/550/1050/1870/3000 Gems for 5/15/30/50/75/110/150 AP.
