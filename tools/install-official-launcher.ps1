param(
    [string]$MinecraftDir = "$env:APPDATA\.minecraft",
    [switch]$SkipBuild,
    [bool]$InstallFabricIfMissing = $true,
    [bool]$CreateLauncherProfile = $true,
    [switch]$OpenModsDir
)

$ErrorActionPreference = "Stop"

function Get-PropValue {
    param(
        [string]$Content,
        [string]$Key
    )
    $match = [regex]::Match($Content, "(?m)^$([regex]::Escape($Key))=(.+)$")
    if (-not $match.Success) {
        throw "Missing '$Key' in gradle.properties."
    }
    return $match.Groups[1].Value.Trim()
}

function Get-LatestFabricInstallerVersion {
    try {
        $meta = Invoke-RestMethod -Uri "https://meta.fabricmc.net/v2/versions/installer"
        if ($null -eq $meta -or $meta.Count -eq 0) {
            return $null
        }
        $stable = $meta | Where-Object { $_.stable -eq $true } | Select-Object -First 1
        if ($null -ne $stable -and $stable.version) {
            return $stable.version
        }
        return ($meta | Select-Object -First 1).version
    }
    catch {
        return $null
    }
}

$scriptDir = Split-Path -Parent $PSCommandPath
$projectRoot = (Resolve-Path (Join-Path $scriptDir "..")).Path
$gradlePropsPath = Join-Path $projectRoot "gradle.properties"

if (-not (Test-Path $gradlePropsPath)) {
    throw "gradle.properties not found at: $gradlePropsPath"
}

$gradleProps = Get-Content $gradlePropsPath -Raw
$modVersion = Get-PropValue -Content $gradleProps -Key "mod_version"
$fabricVersion = Get-PropValue -Content $gradleProps -Key "fabric_version"
$loaderVersion = Get-PropValue -Content $gradleProps -Key "loader_version"
$minecraftVersion = Get-PropValue -Content $gradleProps -Key "minecraft_version"

Push-Location $projectRoot
try {
    if (-not $SkipBuild) {
        Write-Host "[1/4] Building Fish Client jar..." -ForegroundColor Cyan
        & ".\gradlew.bat" remapJar
        if ($LASTEXITCODE -ne 0) {
            throw "Gradle build failed."
        }
    } else {
        Write-Host "[1/4] Skipping build (requested)." -ForegroundColor Yellow
    }

    $libsDir = Join-Path $projectRoot "build\libs"
    $preferredJar = Join-Path $libsDir "fishclient-$modVersion.jar"
    $modJar = $preferredJar
    if (-not (Test-Path $modJar)) {
        $candidate = Get-ChildItem $libsDir -Filter "fishclient-*.jar" -File |
            Where-Object { $_.Name -notlike "*-sources.jar" } |
            Sort-Object LastWriteTime -Descending |
            Select-Object -First 1
        if ($null -eq $candidate) {
            throw "No built fishclient jar found in $libsDir"
        }
        $modJar = $candidate.FullName
    }

    $mcRoot = [System.IO.Path]::GetFullPath($MinecraftDir)
    $modsDir = Join-Path $mcRoot "mods"
    New-Item -ItemType Directory -Path $modsDir -Force | Out-Null

    Write-Host "[2/4] Installing Fish Client to: $modsDir" -ForegroundColor Cyan
    $targetModJar = Join-Path $modsDir ([System.IO.Path]::GetFileName($modJar))
    Copy-Item $modJar $targetModJar -Force

    Get-ChildItem $modsDir -Filter "fishclient-*.jar" -File |
        Where-Object { $_.FullName -ne $targetModJar } |
        ForEach-Object { Remove-Item $_.FullName -Force }

    Write-Host "[3/4] Ensuring Fabric API ($fabricVersion)..." -ForegroundColor Cyan
    $fabricApiJar = "fabric-api-$fabricVersion.jar"
    $fabricApiPath = Join-Path $modsDir $fabricApiJar
    if (-not (Test-Path $fabricApiPath)) {
        $escapedFabricVersion = [uri]::EscapeDataString($fabricVersion)
        $fabricApiUrl = "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/$escapedFabricVersion/$fabricApiJar"
        Invoke-WebRequest -Uri $fabricApiUrl -OutFile $fabricApiPath
    }

    $fabricProfileDir = Join-Path $mcRoot "versions\fabric-loader-$loaderVersion-$minecraftVersion"
    if (-not (Test-Path $fabricProfileDir) -and $InstallFabricIfMissing) {
        Write-Host "[4/4] Fabric profile missing, installing Fabric Loader profile..." -ForegroundColor Cyan

        $javaCmd = (Get-Command java -ErrorAction SilentlyContinue)
        if ($null -eq $javaCmd) {
            Write-Host "Could not find 'java' in PATH, skipping automatic Fabric install." -ForegroundColor Yellow
            Write-Host "Install Fabric Loader manually for Minecraft $minecraftVersion." -ForegroundColor Yellow
        } else {
            $installerVersion = Get-LatestFabricInstallerVersion
            if ([string]::IsNullOrWhiteSpace($installerVersion)) {
                Write-Host "Could not fetch Fabric installer version metadata. Install Fabric manually once." -ForegroundColor Yellow
            } else {
                $tmpDir = Join-Path $env:TEMP "fishclient-fabric-installer"
                New-Item -ItemType Directory -Path $tmpDir -Force | Out-Null
                $installerJar = "fabric-installer-$installerVersion.jar"
                $installerPath = Join-Path $tmpDir $installerJar
                $installerUrl = "https://maven.fabricmc.net/net/fabricmc/fabric-installer/$installerVersion/$installerJar"
                Invoke-WebRequest -Uri $installerUrl -OutFile $installerPath

                & $javaCmd.Source -jar $installerPath client -dir $mcRoot -mcversion $minecraftVersion -loader $loaderVersion -noprofile
                if ($LASTEXITCODE -ne 0) {
                    Write-Host "Fabric installer command failed. Install Fabric Loader manually once." -ForegroundColor Yellow
                }
            }
        }
    } else {
        Write-Host "[4/4] Fabric profile step skipped (already present)." -ForegroundColor Cyan
    }

    if ($CreateLauncherProfile) {
        $profilesPath = Join-Path $mcRoot "launcher_profiles.json"
        if (Test-Path $profilesPath) {
            try {
                $profilesJson = Get-Content $profilesPath -Raw | ConvertFrom-Json -AsHashtable
                if (-not $profilesJson.ContainsKey("profiles") -or $null -eq $profilesJson["profiles"]) {
                    $profilesJson["profiles"] = @{}
                }

                $fishProfileId = "fishclient-fabric-$minecraftVersion"
                $versionId = "fabric-loader-$loaderVersion-$minecraftVersion"
                $now = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ss.fffZ")
                $createdValue = $now
                if ($profilesJson["profiles"].ContainsKey($fishProfileId)) {
                    $existing = $profilesJson["profiles"][$fishProfileId]
                    if ($existing -and $existing.ContainsKey("created")) {
                        $createdValue = $existing["created"]
                    }
                }

                $profilesJson["profiles"][$fishProfileId] = @{
                    created       = $createdValue
                    icon          = "Furnace"
                    lastUsed      = $now
                    lastVersionId = $versionId
                    name          = "Fish Client $minecraftVersion"
                    type          = "custom"
                }
                $profilesJson["selectedProfile"] = $fishProfileId

                $serialized = $profilesJson | ConvertTo-Json -Depth 32
                Set-Content -Path $profilesPath -Value $serialized -Encoding UTF8
                Write-Host "Launcher profile created/updated: Fish Client $minecraftVersion" -ForegroundColor Green
            }
            catch {
                Write-Host "Could not patch launcher_profiles.json automatically: $($_.Exception.Message)" -ForegroundColor Yellow
            }
        } else {
            Write-Host "launcher_profiles.json not found, skip profile creation." -ForegroundColor Yellow
        }
    }

    Write-Host ""
    Write-Host "Installed:" -ForegroundColor Green
    Write-Host " - $(Split-Path $targetModJar -Leaf)"
    Write-Host " - $fabricApiJar"
    Write-Host ""

    if (Test-Path $fabricProfileDir) {
        Write-Host "Fabric version detected: fabric-loader-$loaderVersion-$minecraftVersion" -ForegroundColor Green
    } else {
        Write-Host "Fabric version still missing. Install Fabric Loader manually for $minecraftVersion once." -ForegroundColor Yellow
    }

    if ($OpenModsDir) {
        Invoke-Item $modsDir
    }
}
finally {
    Pop-Location
}
