# Memories of Eternity offline playtest updates

## Android phones with an existing offline installation

Download MOE-Android-Update-Installer.apk from the latest release and install the small helper. Keep the existing MOE game installed. Open MOE Update Installer, check for updates, download the verified update, and approve Android's installation prompt. If asked, allow the helper to install updates, return, and check again. Existing saves and installed asset splits are retained. Do not uninstall the game or clear its data. No USB debugging is needed.

After updating, use Updates on the game's title screen for future updates. Checking is optional. Offline play remains available and network failures leave the current game usable. The button is hidden after Start. Android still requires approval for APK installation.

The helper requires an existing com.nexon.moe.global offline installation with the matching signing identity and package version. Android 5 or newer and support for 32-bit ARM apps are required. Validation used an Android 11 emulator; physical-phone compatibility is not yet confirmed. The helper is intended for the initial APK bootstrap; use the game itself for future content updates.

## Windows playtest

Use Launch-MOE.cmd in the existing playtest outputs folder. The launcher checks GitHub and can continue offline when checks fail. Launch-MOE.ps1 -SkipUpdateCheck skips checking. The Windows ZIP includes updater files and the base APK; it requires the existing project/emulator/assets installation.

Update-MOE.ps1 -Mode Check checks manually. Update-MOE.ps1 -Mode Rollback restores previous game files while retaining the current save. Installed APK and save backups are kept before Windows updates. Android provides previous asset-selection restoration; it does not silently overwrite saves from a backup.

## Verification and asset updates

Release manifests are signed with a pinned RSA key. Downloads are checked against signed sizes and SHA256 values. APK updates use Android install sessions that inherit existing resource splits. Compatible asset updates use a private cache and atomic asset selection, without modifying original installed assets or saves. Corrupt overlays fall back to original assets. Opening/prologue movie replacement is excluded from this asset format. Engine format, signing identity, package version, or save-schema changes need compatible migration work.

## Publishing

New-MOERelease.ps1 signs future releases locally. Keep the private signing XML and Android keystore private; neither belongs in this repository. Current packaging reconstructs the already-signed APK from a verified public bootstrap and a binary delta. Signed manifest bytes are stored in base64 to preserve them exactly. GitHub Actions verifies the reconstructed APK before uploading and publishing release files using its temporary token. Existing successful releases remain immutable.

APK changes still need an in-place Android installation. The updater avoids reinstalling the complete asset collection; asset-only releases can download just changed files.

## B14 additions

The Team editor handles empty teams. All 608 archived outfit offers stay available without date limits; original non-date unlock requirements remain enforced. The 25 originally unpriced offers cost 1,200 Gems. Options offers Reset Local Save with a typed RESET confirmation, a verified recovery copy, and installed assets retained. Resetting closes the game; reopen to start fresh. Gem packages now grant 10/75/240/550/1050/1870/3000 Gems for 5/15/30/50/75/110/150 AP.
