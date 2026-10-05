# Yuzuk (Capacitor) - Google Play icin imzali Android App Bundle (AAB). Yukleme anahtari: android/keystore.properties.
# Play App Signing: Google uygulama imzalama anahtarini kendisi tutar; buradaki anahtar yalniz yukleme anahtaridir.
$env:JAVA_HOME       = "C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot"
$env:ANDROID_HOME    = "C:\src\android-sdk"
$env:GRADLE_USER_HOME= "C:\src\gradle-home"
$env:TEMP            = "C:\src\tmp"
$env:TMP             = "C:\src\tmp"
$env:JAVA_TOOL_OPTIONS = "-Djava.net.preferIPv4Stack=true -Djava.io.tmpdir=C:\src\tmp"
Set-Location "C:\src\yuzuk_mobil"
npm run build
if ($LASTEXITCODE -ne 0) { exit 1 }
npx cap sync android
Set-Location "C:\src\yuzuk_mobil\android"
.\gradlew.bat bundleRelease --no-daemon -q
if ($LASTEXITCODE -ne 0) { exit 1 }
Get-ChildItem "app\build\outputs\bundle\release\*.aab" | ForEach-Object { "AAB: $($_.FullName) - {0:N1} MB" -f ($_.Length/1MB) }
