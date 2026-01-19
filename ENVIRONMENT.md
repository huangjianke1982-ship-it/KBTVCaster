# CasterTV 环境配置信息

> **最后更新**: 2026-01-17
> **重要**: 构建前请先阅读此文件！

## 记录的环境信息

### Java JDK
```
位置: C:\Program Files\Java\
版本:
  - jdk-17 (当前项目使用)
  - jdk-11 (备用)
  - jdk1.8.0_431 (旧版本)

环境变量设置:
  JAVA_HOME=C:\Program Files\Java\jdk-17
  PATH=%JAVA_HOME%\bin;%PATH%
```

### Android SDK
```
位置: C:\Users\kaiji\AppData\Local\Android\Sdk

目录结构:
  C:\Users\kaiji\AppData\Local\Android\Sdk\
  ├── platform-tools\
  │   └── adb.exe (Android Debug Bridge)
  ├── platforms\
  │   └── android-34\
  ├── build-tools\
  │   └── 34.0.0\
  └── ...

环境变量设置:
  ANDROID_HOME=C:\Users\kaiji\AppData\Local\Android\Sdk
  ANDROID_SDK_ROOT=C:\Users\kaiji\AppData\Local\Android\Sdk
  PATH=%ANDROID_HOME%\platform-tools;%PATH%
```

### Gradle
```
版本: 8.13
位置: C:\Users\kaiji\.gradle\wrapper\dists\gradle-8.13-all\54h0s9kvb6g2sinako7ub77ku\gradle-8.13\bin\gradle

使用方式: 项目自带 gradlew 包装器
  - Windows: gradlew.bat
  - Linux/macOS: gradlew
```

### Android TV
```
IP 地址: 192.168.0.49
ADB 端口: 5555
连接命令: adb connect 192.168.0.49:5555
```

---

## 构建命令

### Windows CMD
```cmd
cd C:\Users\kaiji\vibecodingKJ\project test\CasterTVService

:: 设置环境变量
set JAVA_HOME=C:\Program Files\Java\jdk-17
set ANDROID_HOME=C:\Users\kaiji\AppData\Local\Android\Sdk
set PATH=%JAVA_HOME%\bin;%ANDROID_HOME%\platform-tools;%PATH%

:: 构建 Debug APK
gradlew.bat assembleDebug

:: 构建 Release APK
gradlew.bat assembleRelease

:: 运行测试
gradlew.bat test

:: lint 检查
gradlew.bat lint
```

### PowerShell
```powershell
cd C:\Users\kaiji\vibecodingKJ\project test\CasterTVService

$env:JAVA_HOME="C:\Program Files\Java\jdk-17"
$env:ANDROID_HOME="C:\Users\kaiji\AppData\Local\Android\Sdk"
$env:PATH="$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:PATH"

.\gradlew.bat assembleDebug
```

### Linux/macOS (WSL)
```bash
cd /mnt/c/Users/kaiji/vibecodingKJ/project\ test/CasterTVService

export JAVA_HOME="/mnt/c/Program Files/Java/jdk-17"
export ANDROID_HOME="/mnt/c/Users/kaiji/AppData/Local/Android/Sdk"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"

./gradlew.bat assembleDebug
```

---

## ADB 命令

### 连接电视
```cmd
:: 连接 Android TV
adb connect 192.168.0.49:5555

:: 查看已连接设备
adb devices

:: 断开连接
adb disconnect 192.168.0.49:5555
```

### 安装和运行
```cmd
:: 安装 APK
adb install app/build/outputs/apk/debug/app-debug.apk

:: 卸载应用
adb uninstall com.caster.tv

:: 启动应用
adb shell am start -n com.caster.tv/.ui.MainActivity

:: 停止应用
adb shell am force-stop com.caster.tv
```

### 日志查看
```cmd
:: 查看所有日志
adb logcat -d

:: 过滤 CasterTV 日志
adb logcat -d | findstr -i "caster"

:: 实时日志
adb logcat | findstr -i "caster"

:: 清除日志缓存
adb logcat -c
```

### 端口转发
```cmd
:: 转发 HTTP 服务器端口
adb forward tcp:5001 tcp:5000

:: 测试 HTTP 端点
curl http://localhost:5001/cast.json
```

### 服务管理
```cmd
:: 查看运行中的服务
adb shell dumpsys activity services | findstr caster

:: 查看应用信息
adb shell dumpsys activity activities | findstr caster
```

---

## 构建产物

### Debug APK
```
路径: app/build/outputs/apk/debug/app-debug.apk
大小: 约 10-20 MB
签名: Debug 签名 (默认)
```

### Release APK (需要签名配置)
```
路径: app/build/outputs/apk/release/app-release-unsigned.apk
需要: 配置签名密钥
```

### 中间产物
```
- app/build/intermediates/
- app/build/outputs/
- app/build/tmp/
```

---

## 常见问题

### Q1: Java 版本不匹配
```cmd
:: 检查 Java 版本
java -version

:: 如果版本不对，确保 JAVA_HOME 指向正确的 JDK
set JAVA_HOME=C:\Program Files\Java\jdk-17
```

### Q2: Android SDK 找不到
```cmd
:: 检查 SDK 路径
echo %ANDROID_HOME%

:: 如果为空，手动设置
set ANDROID_HOME=C:\Users\kaiji\AppData\Local\Android\Sdk
```

### Q3: Gradle 下载失败
```cmd
:: 清除 Gradle 缓存
rmdir /s /q C:\Users\kaiji\.gradle\wrapper\dists

:: 重新运行构建
gradlew.bat assembleDebug
```

### Q4: ADB 连接失败
```cmd
:: 重启 ADB 服务器
adb kill-server
adb start-server

:: 重新连接
adb connect 192.168.0.49:5555
```

### Q5: 端口被占用
```cmd
:: 检查端口占用
netstat -ano | findstr :5000

:: 结束占用进程
taskkill /PID <PID号> /F
```

---

## 快速参考表

| 任务 | 命令 |
|------|------|
| 构建 Debug | `gradlew.bat assembleDebug` |
| 构建 Release | `gradlew.bat assembleRelease` |
| 连接电视 | `adb connect 192.168.0.49:5555` |
| 安装 APK | `adb install app/build/outputs/apk/debug/app-debug.apk` |
| 启动应用 | `adb shell am start -n com.caster.tv/.ui.MainActivity` |
| 查看日志 | `adb logcat -d \| findstr -i caster` |
| 转发端口 | `adb forward tcp:5001 tcp:5000` |
| 测试 HTTP | `curl http://localhost:5001/cast.json` |

---

## 环境检查清单

构建前请确认：

- [ ] `java -version` 显示 JDK 17
- [ ] `adb version` 正常工作
- [ ] `echo %ANDROID_HOME%` 返回正确路径
- [ ] `adb devices` 显示电视已连接
- [ ] 当前目录是 `CasterTVService`

---

## 备注

1. **Gradle Wrapper**: 项目使用 Gradle 8.13，首次构建会自动下载
2. **JDK 版本**: 项目配置使用 JDK 17，确保 `JAVA_HOME` 正确
3. **SDK 版本**: compileSdk=34, targetSdk=34, minSdk=23
4. **签名**: Debug 构建使用默认 debug keystore
