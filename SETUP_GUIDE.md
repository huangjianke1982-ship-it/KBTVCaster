# CasterTV 开发环境设置指南

## 当前状态

❌ **环境缺失**：当前系统没有安装 Java JDK 或 Android SDK

## 必需安装

### 1. Java JDK 17

**下载方式：**
- Oracle JDK 17: https://www.oracle.com/java/technologies/downloads/#java17
- Eclipse Temurin (免费): https://adoptium.net/temurin/releases/?version=17

**安装步骤：**
```bash
# 下载后安装到默认位置
# C:\Program Files\Java\jdk-17

# 验证安装
java -version

# 应显示：
# openjdk version "17.0.x" 2023-10-17
# OpenJDK Runtime Environment (Temurin)-...
# OpenJDK 64-Bit Server VM (build 17.0.x,...)
```

### 2. Android SDK

**方式A：安装 Android Studio**
1. 下载 Android Studio: https://developer.android.com/studio
2. 安装时确保选中 "Android SDK"
3. 启动 Android Studio，完成初始设置

**方式B：仅安装命令行工具**
1. 下载命令行工具: https://developer.android.com/studio#command-line-tools-only
2. 解压到 `C:\Android\cmdline-tools`
3. 设置环境变量:
   ```cmd
   set ANDROID_HOME=C:\Android
   set PATH=%PATH%;%ANDROID_HOME%\cmdline-tools\latest\bin;%ANDROID_HOME%\platform-tools
   ```

### 3. 安装必要的 SDK 组件

```cmd
# 设置环境变量
set JAVA_HOME=C:\Program Files\Java\jdk-17
set ANDROID_HOME=C:\Android  # 或 Android Studio 的 SDK 路径
set PATH=%JAVA_HOME%\bin;%PATH%

# 安装 SDK 组件
sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"
```

---

## 验证环境

安装完成后，运行以下命令验证：

```cmd
# 检查 Java
java -version

# 检查 javac
javac -version

# 检查 adb (Android SDK)
adb version

# 检查 sdkmanager
sdkmanager --version
```

**预期输出：**
```
java -version
# openjdk version "17.0.x"

javac -version
# javac 17.0.x

adb version
# Android Debug Bridge version 1.0.x

sdkmanager --version
# x.x.x
```

---

## 连接 Android TV

确保 TV 和电脑在同一网络：

```cmd
# 连接电视
adb connect 192.168.0.49:5555

# 验证连接
adb devices

# 应显示：
# List of devices attached
# 192.168.0.49:5555      device
```

---

## 构建项目

环境设置完成后：

```cmd
cd C:\Users\kaiji\vibecodingKJ\project test\CasterTVService

# 构建 Debug APK
gradlew.bat assembleDebug

# 安装到电视
adb install app/build/outputs/apk/debug/app-debug.apk

# 启动应用
adb shell am start -n com.caster.tv/.ui.MainActivity

# 查看日志
adb logcat -d | grep -E "caster|CastCoordinator|SSDP|Cling"
```

---

## 快速测试命令

```cmd
# 1. 检查 HTTP 服务器
adb forward tcp:5001 tcp:5000
curl http://localhost:5001/cast.json

# 2. 检查服务状态
adb shell dumpsys activity services | findstr caster

# 3. 查看实时日志
adb logcat -c && adb logcat | findstr caster

# 4. 检查 NSD 服务
adb logcat -d | findstr NSD

# 5. 检查 Cling 服务
adb logcat -d | findstr Cling
```

---

## 可能遇到的问题

### 问题1: Java not found
```cmd
# 设置 JAVA_HOME
set JAVA_HOME=C:\Program Files\Java\jdk-17
set PATH=%JAVA_HOME%\bin;%PATH%
```

### 问题2: ANDROID_HOME not set
```cmd
# 设置 ANDROID_HOME (根据实际安装位置)
set ANDROID_HOME=C:\Android
# 或 (Android Studio)
set ANDROID_HOME=C:\Users\%USERNAME%\AppData\Local\Android\Sdk
```

### 问题3: adb 连接失败
```cmd
# 确保 TV 已启用开发者选项和 USB 调试
# 或通过网络调试：
adb connect 192.168.0.49:5555

# 如果还是失败，尝试：
adb kill-server
adb start-server
adb connect 192.168.0.49:5555
```

### 问题4: 端口被占用
```cmd
# 检查端口 5000 是否被占用
netstat -ano | findstr :5000

# 如果被占用，结束对应进程
taskkill /PID <PID号> /F
```

---

## 下一步

1. 安装 Java JDK 17
2. 安装 Android SDK (或 Android Studio)
3. 运行上述验证命令
4. 构建并测试 CasterTV

完成环境设置后告诉我，我会继续协助测试！
