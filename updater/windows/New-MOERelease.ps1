param(
 [Parameter(Mandatory)][int]$Sequence,
 [Parameter(Mandatory)][string]$ReleaseId,
 [Parameter(Mandatory)][string]$ChangelogPath,
 [string]$ApkPath,
 [string]$AssetRoot,
 [string[]]$AssetPaths=@(),
 [string[]]$RemoveAssets=@(),
 [switch]$AssetsOnly,
 [int]$RequiredClientSequence=12,
 [string]$ConfigPath,
 [string]$PrivateKeyPath,
 [string]$OutputDirectory
)
$ErrorActionPreference='Stop'
if(!$ApkPath){$ApkPath=Join-Path $PSScriptRoot 'MOE-Offline-Campaign-Chapter13-Rewards.apk'}
if(!$ConfigPath){$ConfigPath=Join-Path $PSScriptRoot 'GitHub-Update-Config.json'}
if(!$PrivateKeyPath){$PrivateKeyPath=Join-Path (Split-Path $PSScriptRoot -Parent) 'work/update-signing/private.xml'}
. (Join-Path $PSScriptRoot 'MOE-Update-Core.ps1')
$config=Read-MOEJson $ConfigPath
if($ReleaseId -notmatch '^[A-Za-z0-9][A-Za-z0-9._-]{0,79}$' -or $Sequence -lt 1){throw 'Invalid release identifier or sequence.'}
if(!$OutputDirectory){$OutputDirectory=Join-Path (Join-Path $PSScriptRoot 'GitHub-Updater') $ReleaseId}
[void][IO.Directory]::CreateDirectory($OutputDirectory)
$prefix='https://github.com/'+$config.repository+'/releases/download/'+$ReleaseId+'/'
$apk=$null
if(!$AssetsOnly){
 Copy-Item -LiteralPath $ApkPath -Destination (Join-Path $OutputDirectory 'moe-base.apk') -Force
 $file=Get-Item -LiteralPath $ApkPath
 $apk=[ordered]@{size=$file.Length;sha256=(Get-FileHash -LiteralPath $ApkPath).Hash.ToLowerInvariant();url=$prefix+'moe-base.apk'}
}
$assets=@()
foreach($path in $AssetPaths){
 if(!(Test-MOEAssetPath $path)){throw "Unsupported asset path: $path"}
 $root=[IO.Path]::GetFullPath($AssetRoot)+[IO.Path]::DirectorySeparatorChar
 $source=[IO.Path]::GetFullPath((Join-Path $AssetRoot $path))
 if(!$source.StartsWith($root,[StringComparison]::OrdinalIgnoreCase)){throw 'Asset escaped the selected source root.'}
 $file=Get-Item -LiteralPath $source
 $hash=(Get-FileHash -LiteralPath $source).Hash.ToLowerInvariant()
 $name='asset-'+$hash+'.bin'
 Copy-Item -LiteralPath $source -Destination (Join-Path $OutputDirectory $name) -Force
 $assets+=@([ordered]@{path=$path;size=$file.Length;sha256=$hash;url=$prefix+$name})
}
$manifest=[ordered]@{
 schema_version=1;application_id='com.nexon.moe.global';asset_format='unity5-global-51';channel=$config.channel;
 save_schema_version=1;min_updater_version=1;sequence=$Sequence;required_client_sequence=$RequiredClientSequence;
 release_id=$ReleaseId;changelog=[IO.File]::ReadAllText((Resolve-Path -LiteralPath $ChangelogPath));
 apk=$apk;assets=@($assets);remove_assets=@($RemoveAssets)
}
$json=Join-Path $OutputDirectory 'moe-update.json'
[IO.File]::WriteAllText($json,($manifest|ConvertTo-Json -Depth 20),[Text.UTF8Encoding]::new($false))
$key=[Security.Cryptography.RSACryptoServiceProvider]::new()
$key.PersistKeyInCsp=$false
try{
 $key.FromXmlString([IO.File]::ReadAllText((Resolve-Path -LiteralPath $PrivateKeyPath)))
 $bytes=[IO.File]::ReadAllBytes($json)
 $sig=$key.SignData($bytes,[Security.Cryptography.CryptoConfig]::MapNameToOID('SHA256'))
 [IO.File]::WriteAllText(($json+'.sig'),[Convert]::ToBase64String($sig),[Text.Encoding]::ASCII)
 [void](Read-MOEVerifiedManifest $bytes ([IO.File]::ReadAllBytes($json+'.sig')) $config)
}finally{$key.Dispose()}
Copy-Item -LiteralPath $ChangelogPath -Destination (Join-Path $OutputDirectory 'CHANGELOG.txt') -Force
Write-Host ('Signed release prepared: '+[IO.Path]::GetFullPath($OutputDirectory))
