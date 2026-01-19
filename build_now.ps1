# CasterTV Build Script
# Run this in PowerShell: .\build_now.ps1

$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
$env:ANDROID_HOME = "C:\Users\kaiji\AppData\Local\Android\Sdk"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

Write-Host "Java version:" -ForegroundColor Cyan
java -version

Write-Host "`nBuilding CasterTV..." -ForegroundColor Cyan
.\gradlew.bat assembleDebug

if ($LASTEXITCODE -eq 0) {
    Write-Host "`n✅ Build successful!" -ForegroundColor Green
    Write-Host "APK: app\build\outputs\apk\debug\app-debug.apk" -ForegroundColor Green
} else {
    Write-Host "`n❌ Build failed!" -ForegroundColor Red
}
