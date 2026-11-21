param(
    [ValidateSet('Check','Update','Rollback')][string]$Mode='Check',
    [string]$ConfigPath,
    [string]$CacheRoot,
    [string]$AdbPath,
    [string]$Serial,
    [switch]$AcceptUpdate,
    [switch]$SkipNetwork
)
$ErrorActionPreference='Stop'
if(!$ConfigPath){$ConfigPath=Join-Path $PSScriptRoot 'GitHub-Update-Config.json'}
if(!$AdbPath){$AdbPath=Join-Path (Split-Path $PSScriptRoot -Parent) 'work/android/platform-tools/adb.exe'}
. (Join-Path $PSScriptRoot 'MOE-Update-Core.ps1')
$config=Read-MOEJson $ConfigPath
if($Mode -ne 'Check'){Assert-MOEDeviceSelection $AdbPath $Serial}
if(!$CacheRoot){
    if($Mode -eq 'Check'){$CacheRoot=Join-Path (Split-Path $PSScriptRoot -Parent) 'work/github-update-cache/check'}
    else{$CacheRoot=Join-Path (Split-Path $PSScriptRoot -Parent) ('work/github-update-cache/devices/'+$Serial)}
}
if($Mode -eq 'Rollback'){Undo-MOERelease $CacheRoot $config $AdbPath $Serial;exit}
$selection=Get-MOEUpdateSelection $config $CacheRoot -AcceptUpdate:$AcceptUpdate -SkipNetwork:$SkipNetwork -ForceCheck
if($Mode -eq 'Update'){
    Apply-MOERelease $selection (Join-Path $PSScriptRoot 'MOE-Offline-Campaign-Chapter13-Rewards.apk') $CacheRoot $config $AdbPath $Serial
    Write-Host 'The selected playtest is ready. Launch the game normally.'
}elseif($selection){Write-Host ('Selected release: '+$selection.manifest.release_id)}
