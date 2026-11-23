# Release signing

Every release APK is signed with the same key. Android installs an update only
if it carries the same signature as the installed app – so this key must
never change and never get lost. Without it, installed copies can't be
updated anymore (you'd have to uninstall, losing the app's data).

The key never goes into the repository. It lives in your password manager
and, base64-encoded, in three GitHub secrets that the release workflow uses.

## 1. Create the key (once)

`keytool` comes with every JDK. On Windows (PowerShell), with the JDK 21 that
is installed on the development PC:

```powershell
& "C:\Program Files\Java\jdk-21.0.12.1\bin\keytool.exe" -genkeypair -v -storetype PKCS12 -keystore "$HOME\verba-release.jks" -alias verba -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=Verba"
```

On Linux/macOS:

```sh
keytool -genkeypair -v -storetype PKCS12 -keystore ~/verba-release.jks -alias verba -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=Verba"
```

`keytool` asks for a password twice. Use a long random one and save it in
your password manager right away. (PKCS12 uses the same password for the
keystore and the key.) The key is valid for about 27 years.

## 2. Store it as GitHub secrets

With the GitHub CLI, logged in as the repository owner. PowerShell:

```powershell
gh secret set VERBA_KEYSTORE_BASE64 --repo lenkr0/verba --body ([Convert]::ToBase64String([IO.File]::ReadAllBytes("$HOME\verba-release.jks")))
gh secret set VERBA_KEYSTORE_PASSWORD --repo lenkr0/verba
gh secret set VERBA_KEY_ALIAS --repo lenkr0/verba --body verba
```

Linux/macOS:

```sh
base64 -w0 ~/verba-release.jks | gh secret set VERBA_KEYSTORE_BASE64 --repo lenkr0/verba
gh secret set VERBA_KEYSTORE_PASSWORD --repo lenkr0/verba
gh secret set VERBA_KEY_ALIAS --repo lenkr0/verba --body verba
```

The second command asks for the password interactively, so it never ends up
in your shell history.

## 3. Back it up

Keep `verba-release.jks` and its password in at least two places, e.g. as an
attachment in your password manager and on an offline USB stick. Then delete
the copy in your home folder if you like – GitHub only needs the secrets.

## How releases use it

`.github/workflows/release.yml` decodes the keystore into the runner's temp
folder, builds with `VERBA_KEYSTORE`, `VERBA_KEYSTORE_PASSWORD` and
`VERBA_KEY_ALIAS` set, verifies the signature with `apksigner` and deletes the
file again. The release notes show the SHA-256 of the signing certificate, so
you can compare it in Obtainium or with AppVerifier.

A tag fails the release if the secrets are missing; a manual run of the
workflow builds an unsigned APK instead.

## Signed builds on your own PC

Set the same three variables (with `VERBA_KEYSTORE` pointing to the `.jks`
file) before `./gradlew assembleRelease`. Without them, the release APK is
built unsigned.
