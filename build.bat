@echo off
chcp 65001 >nul
title CasterTV 构建脚本

echo ===============================================
echo          CasterTV Android 投屏服务构建
echo ===============================================
echo.

cd /d "%~dp0"

REM ==============================================
REM 步骤 1: 设置环境变量
REM ==============================================
echo [步骤 1/6] 设置环境变量...

set "JAVA_HOME=C:\Program Files\Java\jdk-17"
set "ANDROID_HOME=C:\Users\kaiji\AppData\Local\Android\Sdk"
set "PATH=%JAVA_HOME%\bin;%ANDROID_HOME%\platform-tools;%PATH%"

echo   ✓ JAVA_HOME: %JAVA_HOME%
echo   ✓ ANDROID_HOME: %ANDROID_HOME%
echo.

REM ==============================================
REM 步骤 2: 验证环境
REM ==============================================
echo [步骤 2/6] 验证环境...

echo   Java 版本:
java -version 2>&1 | findstr /i "version"

echo   ADB 版本:
adb version 2>&1 | findstr /i "Android"

echo.

REM ==============================================
REM 步骤 3: 连接电视
REM ==============================================
echo [步骤 3/6] 检查 ADB 连接...

adb devices 2>&1 | findstr "192.168.0.49"
if %errorlevel% equ 0 (
    echo   ✓ 电视已连接: 192.168.0.49:5555
) else (
    echo   ⚠ 电视未连接，尝试连接...
    adb connect 192.168.0.49:5555
)
echo.

REM ==============================================
REM 步骤 4: 执行构建
REM ==============================================
echo [步骤 4/6] 开始构建...

if exist "app\build" (
    echo   清理旧构建...
    rmdir /s /q app\build >nul 2>&1
)

echo   执行: gradlew.bat assembleDebug
echo   (首次构建会下载 Gradle 8.13，约需几分钟...)
echo.

gradlew.bat assembleDebug

if %errorlevel% equ 0 (
    echo.
    echo   ✓ 构建成功！
) else (
    echo.
    echo   ✗ 构建失败，请检查错误信息
    pause
    exit /b 1
)
echo.

REM ==============================================
REM 步骤 5: 安装到电视
REM ==============================================
echo [步骤 5/6] 安装到电视...

if exist "app\build\outputs\apk\debug\app-debug.apk" (
    echo   安装 APK...
    adb install -r app\build\outputs\apk\debug\app-debug.apk

    if %errorlevel% equ 0 (
        echo   ✓ 安装成功！
    ) else (
        echo   ⚠ 安装失败，请检查电视连接
    )
) else (
    echo   ⚠ APK 文件不存在
)
echo.

REM ==============================================
REM 步骤 6: 启动测试
REM ==============================================
echo [步骤 6/6] 启动应用...

adb shell am start -n com.caster.tv/.ui.MainActivity
echo   ✓ 应用已启动
echo.

REM ==============================================
REM 完成
REM ==============================================
echo ===============================================
echo               构建完成！
echo ===============================================
echo.
echo 下一步测试:
echo   1. 打开手机上的 B站/YouTube/腾讯视频
echo   2. 点击投屏按钮
echo   3. 选择 "CasterTV-XXXX" 设备
echo.
echo 日志查看:
echo   adb logcat -d ^| findstr -i caster
echo.
echo HTTP 测试:
echo   adb forward tcp:5001 tcp:5000
echo   curl http://localhost:5001/cast.json
echo.
pause
