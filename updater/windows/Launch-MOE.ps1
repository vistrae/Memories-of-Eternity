param([switch]$SkipUpdateCheck)
$ErrorActionPreference = 'Stop'
$android = Join-Path (Split-Path $PSScriptRoot -Parent) 'work\android'
$env:ANDROID_HOME = $android
$env:ANDROID_SDK_ROOT = $android
$env:ANDROID_USER_HOME = Join-Path $android 'user'
$env:ANDROID_AVD_HOME = 'H:\MOE-Offline-Android-Test\avd'
$adb = Join-Path $android 'platform-tools\adb.exe'
$emulator = Join-Path $android 'emulator\emulator.exe'
$serial = 'emulator-5556'
$game = 'com.nexon.moe.global'
. (Join-Path $PSScriptRoot 'MOE-Update-Core.ps1')
$updateConfig=Read-MOEJson (Join-Path $PSScriptRoot 'GitHub-Update-Config.json')
$updateCache=Join-Path (Split-Path $PSScriptRoot -Parent) 'work/github-update-cache'
$updateSelection=Get-MOEUpdateSelection $updateConfig $updateCache -SkipNetwork:$SkipUpdateCheck

if (!(Test-Path -LiteralPath (Join-Path $env:ANDROID_AVD_HOME 'MOE.ini'))) {
    throw 'The Master of Eternity test emulator could not be found on drive H:.'
}
$running = @(& $adb devices | Where-Object { $_ -match '^emulator-5556\s+' })
if ($running) {
    $name = @(& $adb -s $serial emu avd name)
    if ($LASTEXITCODE -or $name[0] -ne 'MOE') {
        throw 'Android emulator port 5556 is in use by another device.'
    }
    & $adb -s $serial shell sync
    if ($LASTEXITCODE) { throw 'Could not save the previous MOE test session to disk.' }
    & $adb -s $serial emu kill | Out-Null
    if ($LASTEXITCODE) { throw 'Could not close the previous MOE test window.' }
    for ($attempt = 0; $attempt -lt 20; $attempt++) {
        if (!(& $adb devices | Where-Object { $_ -match '^emulator-5556\s+' })) { break }
        Start-Sleep -Seconds 1
    }
}

Write-Host 'Starting the Master of Eternity test emulator. The first launch may take a minute.'
$instance = Start-Process -FilePath $emulator -ArgumentList '-avd', 'MOE', '-no-snapshot', '-no-boot-anim', '-gpu', 'swiftshader_indirect', '-port', '5556' -WindowStyle Hidden -PassThru
$booted = $false
for ($attempt = 0; $attempt -lt 90; $attempt++) {
    if ($instance.HasExited -and $instance.ExitCode -ne 0) {
        throw "The test emulator exited with code $($instance.ExitCode)."
    }
    $devices = @(& $adb devices)
    if ($devices -match '^emulator-5556\s+device$') {
        $booted = (& $adb -s $serial shell getprop sys.boot_completed) -eq '1'
        if ($booted) { break }
    }
    Start-Sleep -Seconds 2
}
if (!$booted) { throw 'The test emulator did not finish starting.' }
& $adb -s $serial shell svc wifi disable
& $adb -s $serial shell svc data disable
$apk = Join-Path $PSScriptRoot 'MOE-Offline-Campaign-Chapter13-Rewards.apk'
if (!(Test-Path -LiteralPath $apk)) { throw 'The latest Chapter 13 campaign APK could not be found.' }
try {
    Apply-MOERelease $updateSelection $apk $updateCache $updateConfig $adb $serial
} catch {
    Write-Warning ('Update could not be applied. The previous game and backups were retained. '+$_.Exception.Message)
    if (!(& $adb -s $serial shell pm path $game)) { throw }
}
& $adb -s $serial shell am start -W -n "$game/$game.ToyBridge"
if ($LASTEXITCODE) { throw 'The Master of Eternity test build could not be opened.' }


