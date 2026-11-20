Set-StrictMode -Version 2
$ErrorActionPreference = 'Stop'
$script:MOEPackage = 'com.nexon.moe.global'
$script:MOEFormat = 'unity5-global-51'

function Read-MOEJson([string]$Path) {
    return [IO.File]::ReadAllText($Path) | ConvertFrom-Json
}
function Write-MOEJson([string]$Path, $Value) {
    $text = $Value | ConvertTo-Json -Depth 30
    $temporary = $Path + '.tmp'
    [IO.File]::WriteAllText($temporary, $text, [Text.UTF8Encoding]::new($false))
    Move-Item -LiteralPath $temporary -Destination $Path -Force
}
function Test-MOEAssetPath([string]$Path) {
    if (!$Path -or $Path.Length -gt 240 -or $Path -notmatch '^[A-Za-z0-9][A-Za-z0-9_./-]*$') { return $false }
    if (@($Path.Split('/') | Where-Object { $_ -eq '' -or $_ -eq '.' -or $_ -eq '..' }).Count) { return $false }
    if ($Path -match '\.(dll|exe|ps1|psm1|bat|cmd|dex|jar|apk|so|cs|py)$') { return $false }
    # Movie roots use a separate legacy loader; do not silently publish an unsupported movie override.
    if ($Path -in @('opening.mp4','prologue.mp4')) { return $false }
    return $true
}
function Test-MOEUrl([string]$Url, [string]$Repository, [switch]$AllowLocalTestServer) {
    $uri = $null
    if (![Uri]::TryCreate($Url, [UriKind]::Absolute, [ref]$uri)) { return $false }
    if ($uri.UserInfo -or $uri.Fragment) { return $false }
    if ($AllowLocalTestServer -and $uri.Scheme -eq 'http' -and $uri.IsLoopback) { return $true }
    return $uri.Scheme -eq 'https' -and $uri.Host -eq 'github.com' -and
        $uri.AbsolutePath.StartsWith('/' + $Repository + '/releases/download/', [StringComparison]::OrdinalIgnoreCase)
}
function Get-MOEBytes([string]$Url, [int]$Timeout = 8, [long]$Maximum = 2097152) {
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    $request = [Net.HttpWebRequest]::Create($Url)
    $request.UserAgent = 'Memories-of-Eternity-Offline-Updater/1'
    $request.Timeout = $Timeout * 1000
    $request.ReadWriteTimeout = $Timeout * 1000
    $request.AllowAutoRedirect = $true
    $response = $null; $stream = $null; $memory = [IO.MemoryStream]::new()
    try {
        $response = $request.GetResponse()
        if ($response.ContentLength -gt $Maximum) { throw 'Update response exceeds its size limit.' }
        $stream = $response.GetResponseStream()
        $buffer = New-Object byte[] 65536
        while (($count = $stream.Read($buffer, 0, $buffer.Length)) -gt 0) {
            if ($memory.Length + $count -gt $Maximum) { throw 'Update response exceeds its size limit.' }
            $memory.Write($buffer, 0, $count)
        }
        return ,$memory.ToArray()
    } finally {
        if ($stream) { $stream.Dispose() }; if ($response) { $response.Dispose() }; $memory.Dispose()
    }
}
function Assert-MOEFileSpec($Spec, $Config, [switch]$AllowLocalTestServer) {
    if (!$Spec -or $Spec.sha256 -notmatch '^[a-fA-F0-9]{64}$' -or
        $Spec.size -is [string] -or $Spec.size -is [bool] -or
        [long]$Spec.size -le 0 -or [long]$Spec.size -ge 2147483648) {
        throw 'Invalid update file size or SHA256.'
    }
    if (!(Test-MOEUrl $Spec.url $Config.repository -AllowLocalTestServer:$AllowLocalTestServer)) {
        throw 'Update file URL is not an allowed repository release asset.'
    }
}
function Read-MOEVerifiedManifest([byte[]]$Bytes, [byte[]]$SignatureBytes, $Config, [switch]$AllowLocalTestServer) {
    $rsa = [Security.Cryptography.RSACryptoServiceProvider]::new()
    $rsa.PersistKeyInCsp = $false
    try {
        $rsa.FromXmlString($Config.public_key_xml)
        $signature = [Convert]::FromBase64String([Text.Encoding]::ASCII.GetString($SignatureBytes).Trim())
        if (!$rsa.VerifyData($Bytes, [Security.Cryptography.CryptoConfig]::MapNameToOID('SHA256'), $signature)) {
            throw 'The update manifest signature is invalid.'
        }
    } finally { $rsa.Dispose() }
    $manifest = [Text.Encoding]::UTF8.GetString($Bytes).TrimStart([char]0xFEFF) | ConvertFrom-Json
    if ($manifest.schema_version -ne 1 -or $manifest.application_id -ne $script:MOEPackage -or
        $manifest.asset_format -ne $script:MOEFormat -or $manifest.channel -ne $Config.channel -or
        $manifest.save_schema_version -ne 1 -or $manifest.min_updater_version -gt $Config.updater_version) {
        throw 'This release is incompatible with the installed updater or save format.'
    }
    if ($manifest.sequence -is [string] -or $manifest.sequence -is [bool] -or
        [long]$manifest.sequence -lt 1 -or [long]$manifest.sequence -gt 2147483647 -or
        $manifest.release_id -notmatch '^[A-Za-z0-9][A-Za-z0-9._-]{0,79}$' -or
        $manifest.required_client_sequence -lt 1 -or $manifest.required_client_sequence -gt $manifest.sequence) {
        throw 'Invalid release identifier or sequence.'
    }
    if ($manifest.changelog.Length -gt 12000 -or @($manifest.assets).Count -gt 900 -or
        @($manifest.remove_assets).Count -gt 900) { throw 'Update manifest is too large.' }
    $seen = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
    foreach ($asset in @($manifest.assets)) {
        if (!(Test-MOEAssetPath $asset.path) -or !$seen.Add($asset.path)) { throw 'Unsafe or duplicate asset path.' }
        Assert-MOEFileSpec $asset $Config -AllowLocalTestServer:$AllowLocalTestServer
    }
    foreach ($path in @($manifest.remove_assets)) {
        if (!(Test-MOEAssetPath $path) -or !$seen.Add($path)) { throw 'Unsafe or conflicting removed asset path.' }
    }
    if ($manifest.apk) { Assert-MOEFileSpec $manifest.apk $Config -AllowLocalTestServer:$AllowLocalTestServer }
    return $manifest
}
function Save-MOEFile($Spec, [string]$CacheRoot, $Config, [switch]$AllowLocalTestServer) {
    Assert-MOEFileSpec $Spec $Config -AllowLocalTestServer:$AllowLocalTestServer
    $objects = Join-Path $CacheRoot 'objects'
    [void][IO.Directory]::CreateDirectory($objects)
    $destination = Join-Path $objects $Spec.sha256.ToLowerInvariant()
    if ((Test-Path -LiteralPath $destination) -and
        (Get-Item -LiteralPath $destination).Length -eq [long]$Spec.size -and
        (Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash -eq $Spec.sha256) { return $destination }
    $temporary = $destination + '.' + [Guid]::NewGuid().ToString('N') + '.partial'
    $response = $null; $stream = $null; $target = $null
    try {
        [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
        $request = [Net.HttpWebRequest]::Create($Spec.url)
        $request.UserAgent = 'Memories-of-Eternity-Offline-Updater/1'
        $request.Timeout = [int]$Config.check_timeout_seconds * 1000
        $request.ReadWriteTimeout = [int]$Config.download_timeout_seconds * 1000
        $request.AllowAutoRedirect = $true
        $response = $request.GetResponse()
        if ($response.ContentLength -gt [long]$Spec.size) { throw 'Download is larger than its signed size.' }
        $stream = $response.GetResponseStream()
        $target = [IO.File]::Create($temporary)
        $buffer = New-Object byte[] 262144
        while (($count = $stream.Read($buffer, 0, $buffer.Length)) -gt 0) {
            if ($target.Length + $count -gt [long]$Spec.size) { throw 'Download exceeds its signed size.' }
            $target.Write($buffer, 0, $count)
        }
        $target.Dispose(); $target = $null
        if ((Get-Item -LiteralPath $temporary).Length -ne [long]$Spec.size -or
            (Get-FileHash -LiteralPath $temporary -Algorithm SHA256).Hash -ne $Spec.sha256) {
            throw 'Downloaded file failed its size or SHA256 check.'
        }
        Move-Item -LiteralPath $temporary -Destination $destination -Force
        return $destination
    } finally {
        if ($target) { $target.Dispose() }; if ($stream) { $stream.Dispose() }; if ($response) { $response.Dispose() }
        if (Test-Path -LiteralPath $temporary) { Remove-Item -LiteralPath $temporary -Force }
    }
}
function Get-MOERelease($Config, [string]$CacheRoot, [string]$ManifestUrl, [switch]$AllowLocalTestServer) {
    if ($Config.repository -notmatch '^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$') { throw 'Invalid GitHub repository.' }
    if ($ManifestUrl) {
        if (!$AllowLocalTestServer -or !([Uri]$ManifestUrl).IsLoopback) { throw 'Custom manifests are restricted to explicit local tests.' }
        $manifestUri = $ManifestUrl; $signatureUri = $ManifestUrl + '.sig'
    } else {
        $api = 'https://api.github.com/repos/' + $Config.repository + '/releases/latest'
        $release = [Text.Encoding]::UTF8.GetString((Get-MOEBytes $api $Config.check_timeout_seconds)) | ConvertFrom-Json
        $man = @($release.assets | Where-Object name -eq 'moe-update.json')
        $sig = @($release.assets | Where-Object name -eq 'moe-update.json.sig')
        if ($man.Count -ne 1 -or $sig.Count -ne 1) { throw 'The release does not contain a signed MOE manifest.' }
        $manifestUri = $man[0].browser_download_url; $signatureUri = $sig[0].browser_download_url
        if (!(Test-MOEUrl $manifestUri $Config.repository) -or !(Test-MOEUrl $signatureUri $Config.repository)) {
            throw 'Manifest URL is outside the configured repository.'
        }
    }
    $bytes = Get-MOEBytes $manifestUri $Config.check_timeout_seconds
    $signature = Get-MOEBytes $signatureUri $Config.check_timeout_seconds 16384
    $manifest = Read-MOEVerifiedManifest $bytes $signature $Config -AllowLocalTestServer:$AllowLocalTestServer
    $sha = [Security.Cryptography.SHA256]::Create()
    try { $hash = ([BitConverter]::ToString($sha.ComputeHash($bytes))).Replace('-','').ToLowerInvariant() } finally { $sha.Dispose() }
    return [pscustomobject]@{ manifest=$manifest; bytes=$bytes; signature=$signature; hash=$hash }
}
function Stage-MOERelease($Release, [string]$CacheRoot, $Config, [long]$ClientSequence, [switch]$AllowLocalTestServer) {
    if (!$Release.manifest.apk -and $Release.manifest.required_client_sequence -gt $ClientSequence) {
        throw 'This content update requires a newer game APK.'
    }
    if ($Release.manifest.apk) { [void](Save-MOEFile $Release.manifest.apk $CacheRoot $Config -AllowLocalTestServer:$AllowLocalTestServer) }
    foreach ($asset in @($Release.manifest.assets)) {
        [void](Save-MOEFile $asset $CacheRoot $Config -AllowLocalTestServer:$AllowLocalTestServer)
    }
    $folder = Join-Path (Join-Path $CacheRoot 'releases') ($Release.manifest.sequence.ToString() + '-' + $Release.hash)
    [void][IO.Directory]::CreateDirectory($folder)
    [IO.File]::WriteAllBytes((Join-Path $folder 'moe-update.json'), $Release.bytes)
    [IO.File]::WriteAllBytes((Join-Path $folder 'moe-update.json.sig'), $Release.signature)
    return [pscustomobject]@{ directory=$folder; manifest=$Release.manifest; hash=$Release.hash }
}
function Read-MOECachedRelease([string]$StatePath, [string]$CacheRoot, $Config, [switch]$AllowLocalTestServer) {
    if (!(Test-Path -LiteralPath $StatePath)) { return $null }
    $state = Read-MOEJson $StatePath
    if ($state.release_folder -notmatch '^[0-9]+-[a-f0-9]{64}$') { throw 'Invalid cached release path.' }
    $folder = Join-Path (Join-Path $CacheRoot 'releases') $state.release_folder
    $bytes = [IO.File]::ReadAllBytes((Join-Path $folder 'moe-update.json'))
    $sig = [IO.File]::ReadAllBytes((Join-Path $folder 'moe-update.json.sig'))
    $manifest = Read-MOEVerifiedManifest $bytes $sig $Config -AllowLocalTestServer:$AllowLocalTestServer
    $sha=[Security.Cryptography.SHA256]::Create()
    try{$actual=([BitConverter]::ToString($sha.ComputeHash($bytes))).Replace('-','').ToLowerInvariant()}finally{$sha.Dispose()}
    if($actual -ne $state.manifest_sha256 -or $state.release_folder -ne ($manifest.sequence.ToString()+'-'+$actual)){throw 'Cached release identity mismatch.'}
    return [pscustomobject]@{ directory=$folder; manifest=$manifest; hash=$actual }
}
function Invoke-MOEAdb([string]$Adb, [string]$Serial, [string[]]$Arguments) {
    $ErrorActionPreference = 'Continue'
    $result = @(& $Adb -s $Serial @Arguments 2>&1)
    if ($LASTEXITCODE) { throw ('Android command failed: ' + ($result -join ' ')) }
    return ($result -join [Environment]::NewLine)
}
function Install-MOEBaseApk([string]$Adb, [string]$Serial, [string]$Apk, [switch]$Inherit) {
    if (!$Inherit) {
        [void](Invoke-MOEAdb $Adb $Serial @('install','-r',$Apk))
        return
    }
    $remote = '/data/local/tmp/moe-update-base.apk'
    $session = $null
    try {
        [void](Invoke-MOEAdb $Adb $Serial @('push',$Apk,$remote))
        $created = Invoke-MOEAdb $Adb $Serial @('shell','pm','install-create','-r','-p',$script:MOEPackage,'-S',(Get-Item -LiteralPath $Apk).Length.ToString())
        if ($created -notmatch '\[(\d+)\]') { throw 'Android did not create an update session.' }
        $session = $Matches[1]
        [void](Invoke-MOEAdb $Adb $Serial @('shell','pm','install-write',$session,'base.apk',$remote))
        [void](Invoke-MOEAdb $Adb $Serial @('shell','rm',$remote))
        $commit = Invoke-MOEAdb $Adb $Serial @('shell','pm','install-commit',$session)
        if ($commit -notmatch 'Success') { throw $commit }
        $session = $null
    } finally {
        if ($session) { try { [void](Invoke-MOEAdb $Adb $Serial @('shell','pm','install-abandon',$session)) } catch {} }
        try { [void](Invoke-MOEAdb $Adb $Serial @('shell','rm','-f',$remote)) } catch {}
    }
}
function Apply-MOERelease($Candidate, [string]$BootstrapApk, [string]$CacheRoot, $Config, [string]$Adb, [string]$Serial) {
    $name = Invoke-MOEAdb $Adb $Serial @('emu','avd','name')
    if ($name -notmatch '(?m)^MOE(?:-Split-Test)?\s*$') { throw 'Updates are restricted to the dedicated MOE emulator.' }
    $installed = @(& $Adb -s $Serial shell pm path $script:MOEPackage 2>$null)
    $basePath = @($installed | Where-Object { $_ -match '/base.apk$' } | Select-Object -First 1)
    $exists = $basePath.Count -gt 0
    $baseHash = ''
    if ($exists) {
        $basePath = $basePath[0].Substring(8).Trim()
        $baseHash = ((Invoke-MOEAdb $Adb $Serial @('shell','sha256sum',$basePath)) -split '\s+')[0]
    }
    $privateRoot = '/data/data/' + $script:MOEPackage + '/files'
    $clientSequence = 0
    try { $client = (Invoke-MOEAdb $Adb $Serial @('shell','cat',($privateRoot+'/moe-client-build.json'))) | ConvertFrom-Json; $clientSequence = [long]$client.client_sequence } catch {}
    $deviceContentSequence=0
    try {
        $devicePointer=(Invoke-MOEAdb $Adb $Serial @('shell','cat',($privateRoot+'/moe-updates/active.json')))|ConvertFrom-Json
        $deviceContentSequence=[long]$devicePointer.sequence
    } catch {}
    if($Candidate -and [long]$Candidate.manifest.sequence -lt [Math]::Max($clientSequence,$deviceContentSequence)) {
        throw 'A newer game/update is already installed. Keeping it without downgrading.'
    }
    $targetApk = $BootstrapApk
    $manifest = $null
    if ($Candidate) {
        $manifest = $Candidate.manifest
        if ($manifest.apk) { $targetApk = Join-Path (Join-Path $CacheRoot 'objects') $manifest.apk.sha256.ToLowerInvariant() }
        elseif ($exists -and $clientSequence -ge [long]$manifest.required_client_sequence) { $targetApk = $null }
        elseif ([long]$manifest.required_client_sequence -gt [long]$Config.installed_sequence) { throw 'Install a newer bootstrap client before this content update.' }
    } elseif ($exists -and $clientSequence -gt [long]$Config.installed_sequence) {
        $targetApk = $null
    }
    if ($targetApk -and !(Test-Path -LiteralPath $targetApk)) { throw 'The selected game APK is unavailable locally.' }
    if ($manifest -and $manifest.apk -and (Get-FileHash -LiteralPath $targetApk).Hash -ne $manifest.apk.sha256) {
        throw 'Cached APK verification failed.'
    }
    $mustInstall = $targetApk -and $baseHash -ne (Get-FileHash -LiteralPath $targetApk).Hash
    $activeState = Join-Path $CacheRoot 'active-release.json'
    $alreadyApplied = $false
    if ($Candidate -and (Test-Path -LiteralPath $activeState)) {
        try { $alreadyApplied = (Read-MOEJson $activeState).manifest_sha256 -eq $Candidate.hash } catch {}
    }
    if (!$mustInstall -and (!$Candidate -or $alreadyApplied)) { return }
    [void][IO.Directory]::CreateDirectory($CacheRoot)
    $backup = Join-Path (Join-Path $CacheRoot 'backups') (Get-Date -Format 'yyyyMMdd-HHmmss-ffff')
    [void][IO.Directory]::CreateDirectory($backup)
    if ($exists) {
        [void](Invoke-MOEAdb $Adb $Serial @('shell','am','force-stop',$script:MOEPackage))
        [void](Invoke-MOEAdb $Adb $Serial @('pull',$basePath,(Join-Path $backup 'base.apk')))
        $saveRemote = '/data/data/' + $script:MOEPackage + '/shared_prefs'
        try { [void](Invoke-MOEAdb $Adb $Serial @('pull',$saveRemote,(Join-Path $backup 'shared_prefs'))) } catch {}
    }
    $remoteRoot = $privateRoot + '/moe-updates'
    $oldPointerPath = Join-Path $backup 'previous-active.json'
    try { [void](Invoke-MOEAdb $Adb $Serial @('pull',($remoteRoot+'/active.json'),$oldPointerPath)) } catch {}
    $installedNewBase = $false; $activated = $false
    try {
        if ($mustInstall) {
            Install-MOEBaseApk $Adb $Serial $targetApk -Inherit:$exists
            $installedNewBase = $true
        }
        if ($Candidate -and !$alreadyApplied) {
            $files = @{}; $hashes = @{}
            if (Test-Path -LiteralPath $oldPointerPath) {
                $old = Read-MOEJson $oldPointerPath
                if ($old.schema_version -ne 1 -or $old.asset_format -ne $script:MOEFormat) { throw 'Existing update index is incompatible.' }
                foreach ($entry in $old.files.PSObject.Properties) {
                    if (!(Test-MOEAssetPath $entry.Name) -or $entry.Value -notmatch '^releases/[A-Za-z0-9._/-]+$' -or
                        !(Test-MOEAssetPath $entry.Value)) { throw 'Existing update index has an unsafe path.' }
                    $files[$entry.Name] = $entry.Value
                    $hashes[$entry.Name] = $old.hashes.($entry.Name)
                }
            }
            $revision = $manifest.sequence.ToString() + '-' + $Candidate.hash.Substring(0,16)
            foreach ($path in @($manifest.remove_assets)) { $files.Remove($path); $hashes.Remove($path) }
            foreach ($asset in @($manifest.assets)) {
                $local = Join-Path (Join-Path $CacheRoot 'objects') $asset.sha256.ToLowerInvariant()
                if (!(Test-Path -LiteralPath $local) -or (Get-FileHash -LiteralPath $local).Hash -ne $asset.sha256) { throw 'Cached asset verification failed.' }
                # Unchanged overlays are reused without transfer.
                if ($hashes.ContainsKey($asset.path) -and $hashes[$asset.path] -eq $asset.sha256) { continue }
                $relative = 'releases/' + $revision + '/' + $asset.path
                $remote = $remoteRoot + '/' + $relative
                $parent = $remote.Substring(0,$remote.LastIndexOf('/'))
                [void](Invoke-MOEAdb $Adb $Serial @('shell','mkdir','-p',$parent))
                [void](Invoke-MOEAdb $Adb $Serial @('push',$local,($remote+'.partial')))
                $actual = ((Invoke-MOEAdb $Adb $Serial @('shell','sha256sum',($remote+'.partial'))) -split '\s+')[0]
                if ($actual -ne $asset.sha256) { throw 'Transferred asset failed its SHA256 check.' }
                [void](Invoke-MOEAdb $Adb $Serial @('shell','mv',($remote+'.partial'),$remote))
                $files[$asset.path] = $relative; $hashes[$asset.path] = $asset.sha256
            }
            $pointer = [ordered]@{ schema_version=1; asset_format=$script:MOEFormat; release_id=$manifest.release_id; sequence=$manifest.sequence; files=$files; hashes=$hashes }
            $pointerPath = Join-Path $backup 'new-active.json'
            Write-MOEJson $pointerPath $pointer
            [void](Invoke-MOEAdb $Adb $Serial @('shell','mkdir','-p',$remoteRoot))
            [void](Invoke-MOEAdb $Adb $Serial @('push',$pointerPath,($remoteRoot+'/active.json.partial')))
            [void](Invoke-MOEAdb $Adb $Serial @('shell','mv',($remoteRoot+'/active.json.partial'),($remoteRoot+'/active.json')))
            $activated = $true
            [void](Invoke-MOEAdb $Adb $Serial @('shell','sync'))
            if (Test-Path -LiteralPath $activeState) {
                Copy-Item -LiteralPath $activeState -Destination (Join-Path $CacheRoot 'previous-release.json') -Force
            }
            Write-MOEJson $activeState ([ordered]@{ release_folder=(Split-Path $Candidate.directory -Leaf); manifest_sha256=$Candidate.hash; sequence=$manifest.sequence; backup=$backup })
        }
        if (!$Candidate -or $alreadyApplied) { [void](Invoke-MOEAdb $Adb $Serial @('shell','sync')) }
    } catch {
        $failure = $_
        if ($activated) {
            if (Test-Path -LiteralPath $oldPointerPath) {
                try {
                    [void](Invoke-MOEAdb $Adb $Serial @('push',$oldPointerPath,($remoteRoot+'/active.json.partial')))
                    [void](Invoke-MOEAdb $Adb $Serial @('shell','mv',($remoteRoot+'/active.json.partial'),($remoteRoot+'/active.json')))
                } catch { Write-Warning 'Previous asset index is backed up but the device is unavailable for restoration.' }
            } else { try { [void](Invoke-MOEAdb $Adb $Serial @('shell','rm','-f',($remoteRoot+'/active.json'))) } catch {} }
        }
        if ($installedNewBase -and $exists) {
            try { Install-MOEBaseApk $Adb $Serial (Join-Path $backup 'base.apk') -Inherit } catch { Write-Warning 'Automatic APK rollback failed. The previous APK and save backup are retained.' }
        }
        throw $failure
    }
}

function Get-MOEUpdateSelection($Config, [string]$CacheRoot, [switch]$AcceptUpdate, [switch]$SkipNetwork,
    [switch]$ForceCheck, [string]$ManifestUrl, [switch]$AllowLocalTestServer) {
    $cached = $null
    try { $cached = Read-MOECachedRelease (Join-Path $CacheRoot 'active-release.json') $CacheRoot $Config -AllowLocalTestServer:$AllowLocalTestServer }
    catch { Write-Warning 'The cached release record is unavailable. The installed game remains usable.' }
    if ($SkipNetwork) { return $cached }
    $stamp = Join-Path $CacheRoot 'last-check.json'
    if (!$ForceCheck -and (Test-Path -LiteralPath $stamp)) {
        try {
            $last = Read-MOEJson $stamp
            if ([DateTime]::UtcNow - [DateTime]::Parse($last.utc).ToUniversalTime() -lt [TimeSpan]::FromMinutes(30)) { return $cached }
        } catch {}
    }
    try {
        [void][IO.Directory]::CreateDirectory($CacheRoot)
        Write-MOEJson $stamp @{ utc=[DateTime]::UtcNow.ToString('o') }
        Write-Host 'Checking GitHub for a playtest update...'
        $release = Get-MOERelease $Config $CacheRoot $ManifestUrl -AllowLocalTestServer:$AllowLocalTestServer
        $currentSequence = [long]$Config.installed_sequence
        if ($cached) { $currentSequence = [Math]::Max($currentSequence,[long]$cached.manifest.sequence) }
        if ([long]$release.manifest.sequence -le $currentSequence) {
            Write-Host 'The current playtest is up to date.'
            return $cached
        }
        if (!$AcceptUpdate) {
            $bytes = 0L
            if ($release.manifest.apk) { $bytes += [long]$release.manifest.apk.size }
            foreach ($asset in @($release.manifest.assets)) { $bytes += [long]$asset.size }
            $message = 'Update ' + $release.manifest.release_id + ' is available (' + [Math]::Round($bytes/1MB,1) + ' MB maximum).' +
                [Environment]::NewLine + [Environment]::NewLine + $release.manifest.changelog +
                [Environment]::NewLine + [Environment]::NewLine + 'Download and apply this update? No keeps the current game.'
            Add-Type -AssemblyName System.Windows.Forms
            $answer = [Windows.Forms.MessageBox]::Show($message,'Memories of Eternity update',
                [Windows.Forms.MessageBoxButtons]::YesNo,[Windows.Forms.MessageBoxIcon]::Information)
            if ($answer -ne [Windows.Forms.DialogResult]::Yes) { return $cached }
        }
        Write-Host ('Downloading verified update ' + $release.manifest.release_id + '...')
        return Stage-MOERelease $release $CacheRoot $Config $currentSequence -AllowLocalTestServer:$AllowLocalTestServer
    } catch {
        Write-Host ('Update check/download unavailable. Continuing offline with the current game. ' + $_.Exception.Message)
        return $cached
    }
}
function Undo-MOERelease([string]$CacheRoot, $Config, [string]$Adb, [string]$Serial) {
    $name=Invoke-MOEAdb $Adb $Serial @('emu','avd','name')
    if ($name -notmatch '(?m)^MOE(?:-Split-Test)?\s*$') { throw 'Rollback is restricted to the MOE emulator.' }
    $statePath=Join-Path $CacheRoot 'active-release.json'
    $state=Read-MOEJson $statePath
    $root=[IO.Path]::GetFullPath((Join-Path $CacheRoot 'backups'))+[IO.Path]::DirectorySeparatorChar
    $backup=[IO.Path]::GetFullPath($state.backup)
    if (!$backup.StartsWith($root,[StringComparison]::OrdinalIgnoreCase)) { throw 'Invalid rollback backup location.' }
    [void](Invoke-MOEAdb $Adb $Serial @('shell','am','force-stop',$script:MOEPackage))
    Install-MOEBaseApk $Adb $Serial (Join-Path $backup 'base.apk') -Inherit
    $remote='/data/data/'+$script:MOEPackage+'/files/moe-updates/active.json'
    $pointer=Join-Path $backup 'previous-active.json'
    if (Test-Path -LiteralPath $pointer) {
        [void](Invoke-MOEAdb $Adb $Serial @('push',$pointer,($remote+'.partial')))
        [void](Invoke-MOEAdb $Adb $Serial @('shell','mv',($remote+'.partial'),$remote))
    } else { [void](Invoke-MOEAdb $Adb $Serial @('shell','rm','-f',$remote)) }
    $previous=Join-Path $CacheRoot 'previous-release.json'
    if (Test-Path -LiteralPath $previous) { Copy-Item -LiteralPath $previous -Destination $statePath -Force }
    else { Remove-Item -LiteralPath $statePath -Force }
    [void](Invoke-MOEAdb $Adb $Serial @('shell','sync'))
    Write-Host 'Previous game files restored. Your current save was retained.'
}


