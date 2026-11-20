# Memories of Eternity offline playtest updates

The Windows playtest launcher checks this repository for signed GitHub Releases.
The game remains playable offline. Network checks and downloads run on Windows,
while the emulator remains offline.

## Existing Windows installation

Use the updated Launch-MOE.cmd and the updater files in the existing playtest
outputs folder. Start normally to check for an update. Decline an update to keep
playing the installed version. A failed check or download keeps the last working
game. Run Launch-MOE.ps1 -SkipUpdateCheck to skip the network check.

The first updater-enabled client is B12. Older launchers need this one-time
bootstrap update. This package uses the existing dedicated emulator and installed
assets; it does not include or replace the emulator.

## What updates

- New APKs replace the base APK while retaining the existing asset splits.
- Compatible asset changes download individually and override the installed
  assets from a separate private update folder.
- Downloads require a valid release signature, expected size and SHA256.
- Original assets and save files are not overwritten by asset patches.
- The installed APK and shared preferences are backed up before applying changes.
- Update-MOE.ps1 -Mode Rollback restores previous game files and keeps the current
  save. Save backups are for recovery, not automatically restored over progress.

This initial implementation targets the Windows emulator workflow. It does not
add a direct GitHub installer inside the phone app. Opening and prologue movie
replacement is excluded from the first asset-patch format because those movies
use a separate legacy path. New engine formats, Android version-code changes,
and save-schema changes require a compatible bootstrap release.

## Releases

https://github.com/vistrae/Memories-of-Eternity/releases

The manifest, moe-update.json, contains a monotonically increasing sequence,
compatibility identifiers, changelog, an optional base APK, and changed asset
paths. moe-update.json.sig is its detached RSA SHA256 signature.

New-MOERelease.ps1 creates and signs future update packages locally. Keep the
private update-signing key and Android APK signing keystore on the publisher's
machine. Neither private key is included in this repository or the release.

B12 uses the same Android package ID, signing identity and Android package version
as the installed asset splits. Its separate updater sequence is 12.

## Reproducible bootstrap

The release workflow downloads the previously shared public bootstrap APK,
checks its exact SHA256, applies a small binary delta, and verifies the resulting
already-signed B12 APK against the signed release manifest. No signing key or
long-lived access token is required in the workflow. GitHub's temporary Actions
token uploads the verified files to a draft release and publishes it only after
the upload checks pass.

APK changes still require an in-place Android update. This updater avoids
reinstalling the complete asset package. Offline replacement EXP and other
restored-game limitations are described in the changelog.
