param(
 [Parameter(Mandatory)][int]$Sequence,
 [Parameter(Mandatory)][string]$ReleaseId,
 [Parameter(Mandatory)][string]$ChangelogPath,
 [string]$ApkPath,
 [string]$AssetRoot,
 [string[]]$AssetPaths=@(),
 [string[]]$RemoveAssets=@(),
 [string[]]$VariantApkPaths=@(),
 [string[]]$VariantSignerSha256=@(),
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
$variants=@()
if($VariantApkPaths.Count -ne $VariantSignerSha256.Count){throw 'Each alternate APK requires its signing-certificate SHA256.'}
if($VariantApkPaths.Count -gt 16 -or ($AssetsOnly -and $VariantApkPaths.Count -gt 0)){throw 'Alternate APKs require a canonical APK and at most 16 signers.'}
$variantCerts=[Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
for($i=0;$i -lt $VariantApkPaths.Count;$i++){
 $cert=$VariantSignerSha256[$i].Trim().ToLowerInvariant()
 if($cert -notmatch '^[0-9a-f]{64}$' -or !$variantCerts.Add($cert)){throw 'Invalid or duplicate alternate APK signer.'}
 $source=Get-Item -LiteralPath $VariantApkPaths[$i]
 $hash=(Get-FileHash -LiteralPath $source.FullName).Hash.ToLowerInvariant()
 if($apk -and $hash -eq $apk.sha256){throw 'Alternate APK must differ from the canonical APK.'}
 $name=if($cert -eq 'a40da80a59d170caa950cf15c18c454d47a39b26989d8b640ecd745ba71bf5dc'){'moe-base-sai.apk'}else{'moe-base-'+$cert.Substring(0,12)+'.apk'}
 Copy-Item -LiteralPath $source.FullName -Destination (Join-Path $OutputDirectory $name) -Force
 $variants+=@([ordered]@{signing_certificate_sha256=$cert;size=$source.Length;sha256=$hash;url=$prefix+$name})
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
if($variants.Count -gt 0){$manifest['apk_variants']=@($variants)}
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
