[CmdletBinding(DefaultParameterSetName = "Pfx")]
param(
    [Parameter(Mandatory = $true, ParameterSetName = "Pfx")]
    [ValidateScript({ Test-Path -LiteralPath $_ -PathType Leaf })]
    [string] $CertificatePath,

    [Parameter(ParameterSetName = "Pfx")]
    [SecureString] $CertificatePassword,

    [Parameter(Mandatory = $true, ParameterSetName = "Store")]
    [ValidatePattern("^[0-9A-Fa-f ]{40}$")]
    [string] $CertificateThumbprint,

    [string] $TimestampServer = "http://timestamp.digicert.com"
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$projectDir = $PSScriptRoot
$pyinstaller = Join-Path $projectDir ".venv\Scripts\pyinstaller.exe"
$specFile = Join-Path $projectDir "SmartLights.spec"
$outputFile = Join-Path $projectDir "dist\SmartLights.exe"
$certificate = $null

if (-not (Test-Path -LiteralPath $pyinstaller -PathType Leaf)) {
    throw "PyInstaller bulunamadi: $pyinstaller"
}

if ($PSCmdlet.ParameterSetName -eq "Pfx") {
    if (-not $CertificatePassword) {
        $CertificatePassword = Read-Host "Kod imzalama sertifikasinin parolasi" -AsSecureString
    }

    $certificate = [System.Security.Cryptography.X509Certificates.X509Certificate2]::new(
        (Resolve-Path -LiteralPath $CertificatePath).Path,
        $CertificatePassword,
        [System.Security.Cryptography.X509Certificates.X509KeyStorageFlags]::EphemeralKeySet
    )
}
else {
    $normalizedThumbprint = $CertificateThumbprint.Replace(" ", "").ToUpperInvariant()
    $certificate = Get-ChildItem Cert:\CurrentUser\My, Cert:\LocalMachine\My |
        Where-Object { $_.Thumbprint -eq $normalizedThumbprint } |
        Select-Object -First 1

    if (-not $certificate) {
        throw "Sertifika Windows sertifika deposunda bulunamadi: $normalizedThumbprint"
    }
}

if (-not $certificate.HasPrivateKey) {
    throw "Sertifikanin ozel anahtari yok; bu sertifika kod imzalamak icin kullanilamaz."
}

$codeSigningOid = "1.3.6.1.5.5.7.3.3"
$hasCodeSigningUsage = $false
foreach ($extension in $certificate.Extensions) {
    if ($extension -is [System.Security.Cryptography.X509Certificates.X509EnhancedKeyUsageExtension]) {
        foreach ($usage in $extension.EnhancedKeyUsages) {
            if ($usage.Value -eq $codeSigningOid) {
                $hasCodeSigningUsage = $true
            }
        }
    }
}

if (-not $hasCodeSigningUsage) {
    throw "Sertifika Code Signing (1.3.6.1.5.5.7.3.3) kullanimina sahip degil."
}

if ($certificate.NotBefore -gt (Get-Date) -or $certificate.NotAfter -lt (Get-Date)) {
    throw "Kod imzalama sertifikasi henuz gecerli degil veya suresi dolmus."
}

if ($certificate.PublicKey.Oid.Value -ne "1.2.840.113549.1.1.1") {
    throw "Smart App Control icin RSA tabanli bir kod imzalama sertifikasi gerekir."
}

Push-Location $projectDir
try {
    & $pyinstaller $specFile --noconfirm --clean
    if ($LASTEXITCODE -ne 0) {
        throw "PyInstaller derlemesi basarisiz oldu (cikis kodu: $LASTEXITCODE)."
    }

    if (-not (Test-Path -LiteralPath $outputFile -PathType Leaf)) {
        throw "Derleme tamamlandi ancak cikti bulunamadi: $outputFile"
    }

    $signature = Set-AuthenticodeSignature `
        -FilePath $outputFile `
        -Certificate $certificate `
        -HashAlgorithm SHA256 `
        -TimestampServer $TimestampServer

    if ($signature.Status -ne [System.Management.Automation.SignatureStatus]::Valid) {
        throw "EXE imzalandi ancak dogrulanamadi. Durum: $($signature.Status); $($signature.StatusMessage)"
    }

    $verified = Get-AuthenticodeSignature -FilePath $outputFile
    if ($verified.Status -ne [System.Management.Automation.SignatureStatus]::Valid) {
        throw "Son imza dogrulamasi basarisiz. Durum: $($verified.Status); $($verified.StatusMessage)"
    }

    Write-Host "Imzali derleme hazir: $outputFile"
    Write-Host "Yayinci: $($verified.SignerCertificate.Subject)"
    Write-Host "SHA256: $((Get-FileHash -LiteralPath $outputFile -Algorithm SHA256).Hash)"
}
finally {
    Pop-Location
    if ($certificate -and $certificate -is [System.IDisposable]) {
        $certificate.Dispose()
    }
}
