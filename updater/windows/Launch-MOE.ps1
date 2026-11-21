param(
    [string]$Serial,
    [string]$AdbPath,
    [string]$CacheRoot,
    [switch]$SkipUpdateCheck
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'MOE-Update-Core.ps1')
if (!$AdbPath) { $AdbPath = Join-Path (Split-Path $PSScriptRoot -Parent) 'work/android/platform-tools/adb.exe' }
# Choose and start the test emulator yourself. This launcher never manages its lifecycle.
Assert-MOEDeviceSelection $AdbPath $Serial
if (!$CacheRoot) { $CacheRoot = Join-Path (Split-Path $PSScriptRoot -Parent) ('work/github-update-cache/devices/' + $Serial) }
$game = 'com.nexon.moe.global'
$updateConfig = Read-MOEJson (Join-Path $PSScriptRoot 'GitHub-Update-Config.json')
$updateSelection = Get-MOEUpdateSelection $updateConfig $CacheRoot -SkipNetwork:$SkipUpdateCheck
$apk = Join-Path $PSScriptRoot 'MOE-Offline-Campaign-Chapter13-Rewards.apk'
if (!(Test-Path -LiteralPath $apk)) { throw 'The bundled playtest APK could not be found.' }
try {
    Apply-MOERelease $updateSelection $apk $CacheRoot $updateConfig $AdbPath $Serial
} catch {
    Write-Warning ('Update could not be applied. The previous game and backups were retained. ' + $_.Exception.Message)
    if (!(Invoke-MOEAdb $AdbPath $Serial @('shell','pm','path',$game))) { throw }
}
[void](Invoke-MOEAdb $AdbPath $Serial @('shell','am','start','-W','-n',"$game/$game.ToyBridge"))
