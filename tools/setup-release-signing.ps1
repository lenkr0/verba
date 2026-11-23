# Creates the release signing key (if it doesn't exist yet) and stores it as
# GitHub secrets for the release workflow. Passwords are typed into hidden
# prompts; they are never shown or written to disk. See docs/release-signing.md.
#
#   powershell -ExecutionPolicy Bypass -File tools\setup-release-signing.ps1
param(
    [string]$Repo = 'lenkr0/verba',
    [string]$Keystore = (Join-Path $HOME 'verba-release.jks'),
    [string]$Alias = 'verba'
)
$ErrorActionPreference = 'Stop'

function Find-Tool([string]$Name, [string[]]$Candidates) {
    $cmd = Get-Command $Name -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    foreach ($pattern in $Candidates) {
        $hit = Get-Item $pattern -ErrorAction SilentlyContinue | Sort-Object FullName | Select-Object -Last 1
        if ($hit) { return $hit.FullName }
    }
    throw "$Name not found. Install it or add it to PATH."
}

function Read-Secret([string]$Prompt) {
    $secure = Read-Host $Prompt -AsSecureString
    $bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try { [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr) }
}

function Set-RepoSecret([string]$Name, [string]$Value) {
    & $gh secret set $Name --repo $Repo --body $Value
    if ($LASTEXITCODE -ne 0) { throw "Could not store secret $Name." }
}

$gh = Find-Tool 'gh' @(
    "$env:LOCALAPPDATA\Microsoft\WinGet\Packages\GitHub.cli_*\bin\gh.exe",
    "$env:ProgramFiles\GitHub CLI\gh.exe")
$keytool = Find-Tool 'keytool' @(
    "$env:JAVA_HOME\bin\keytool.exe",
    "$env:ProgramFiles\Java\jdk-*\bin\keytool.exe",
    "$env:ProgramFiles\Eclipse Adoptium\jdk-*\bin\keytool.exe")

$create = -not (Test-Path $Keystore)
if ($create) {
    Write-Host "Creating a new signing key: $Keystore"
    Write-Host 'Choose a long password and save it in your password manager right away.'
    $password = Read-Secret 'New keystore password (at least 6 characters)'
    if ($password.Length -lt 6) { throw 'The password needs at least 6 characters.' }
    if ((Read-Secret 'Repeat the password') -cne $password) { throw 'The passwords differ.' }
} else {
    Write-Host "Using the existing signing key: $Keystore"
    $password = Read-Secret 'Keystore password'
}

# keytool reads the password from an environment variable, not the command line
$env:VERBA_SETUP_PASSWORD = $password
try {
    if ($create) {
        & $keytool -genkeypair -storetype PKCS12 -keystore $Keystore -alias $Alias `
            -keyalg RSA -keysize 4096 -validity 10000 -dname 'CN=Verba' `
            -storepass:env VERBA_SETUP_PASSWORD -keypass:env VERBA_SETUP_PASSWORD
        if ($LASTEXITCODE -ne 0) { throw 'keytool could not create the key.' }
    }
    # Also checks the password: fails if it's wrong or the alias is missing
    $details = & $keytool -list -v -keystore $Keystore -alias $Alias -storepass:env VERBA_SETUP_PASSWORD 2>&1
    if ($LASTEXITCODE -ne 0) { throw "Wrong password, or no key '$Alias' in $Keystore." }
} finally {
    Remove-Item Env:VERBA_SETUP_PASSWORD -ErrorAction SilentlyContinue
}

Set-RepoSecret 'VERBA_KEYSTORE_BASE64' ([Convert]::ToBase64String([IO.File]::ReadAllBytes($Keystore)))
Set-RepoSecret 'VERBA_KEY_ALIAS' $Alias
Set-RepoSecret 'VERBA_KEYSTORE_PASSWORD' $password
$password = $null

$fingerprint = ($details | Select-String -Pattern 'SHA256:' | Select-Object -First 1).ToString().Trim()
Write-Host ''
Write-Host "Done. Secrets now in ${Repo}:"
& $gh secret list --repo $Repo
Write-Host ''
Write-Host "Signing certificate $fingerprint"
Write-Host "IMPORTANT: back up $Keystore and its password in two places."
Write-Host 'Without them, installed copies of Verba cannot be updated anymore.'
