# Yuzuk (Capacitor) - APK build recipe for this machine (user name contains a Turkish letter; keep Java paths ASCII).
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
.\gradlew.bat assembleRelease --no-daemon -q
if ($LASTEXITCODE -ne 0) { exit 1 }
Get-ChildItem "app\build\outputs\apk\release\*.apk" | ForEach-Object { "APK: $($_.FullName) - {0:N1} MB" -f ($_.Length/1MB) }
