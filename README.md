# KBTVCaster - Android投屏服务

这是一个完整的Android投屏服务应用，支持DLNA/UPnP协议，使用原生Android实现无需第三方UPnP库。

## 功能特性

- **DLNA/UPnP投屏**：兼容B站、腾讯视频、优酷等视频App的投屏功能
- **ExoPlayer媒体播放**：使用AndroidX Media3 ExoPlayer实现高质量视频播放
- **远程控制**：播放/暂停/音量控制
- **自动启动**：开机自启 + 应用启动自启

## 技术实现

- **原生实现**：使用Android NsdManager + MulticastSocket实现SSDP发现
- **HTTP服务器**：内置HTTP服务器响应DLNA设备描述和控制命令
- **ExoPlayer**：Media3 ExoPlayer 1.2.1用于媒体播放
- **完整UPnP**：支持AVTransport和RenderingControl服务

## 项目结构

```
KBTVCaster/
├── app/
│   ├── src/main/
│   │   ├── java/com/kbtv/caster/
│   │   │   ├── CasterApplication.kt          # 应用入口
│   │   │   ├── ui/
│   │   │   │   └── MainActivity.kt           # 主界面
│   │   │   ├── service/
│   │   │   │   └── CastCoordinatorService.kt # 主服务协调器
│   │   │   ├── receiver/
│   │   │   │   └── BootReceiver.kt           # 开机启动接收器
│   │   │   └── dlna/                         # DLNA/UPnP实现
│   │   │       └── (使用org.fourthline.cling库)
│   │   ├── res/                              # 资源文件
│   │   └── AndroidManifest.xml               # 应用清单
│   └── src/test/                             # 单元测试
│       └── java/com/kbtv/caster/
│           └── dlna/
│               └── TransportStateTest.kt
├── gradle/wrapper/                           # Gradle包装器
├── build.gradle.kts                          # 根构建配置
├── settings.gradle.kts                       # 项目设置
├── gradle.properties                         # Gradle属性
├── local.properties                          # 本地配置
├── AGENTS # AI代理.md                                开发指南
└── README.md                                 # 本文档
```

## 环境要求

- **Android Studio**：Android Studio Otter 2 Feature Drop | 2025.2.2 Patch 1
- **JDK**：17 (Android Studio自带)
- **Gradle**：8.7
- **Android SDK**：Android 6.0 (API 23) 及以上

## 快速开始

### 在Android Studio中打开项目

1. 打开Android Studio
2. 选择 **Open** 或 **Import Project**
3. 导航到 `KBTVCaster` 文件夹
4. 等待Gradle同步完成
5. 连接Android TV设备（通过USB或网络ADB）

### 构建和安装

```bash
# 构建调试APK
./gradlew assembleDebug

# 构建发布APK
./gradlew assembleRelease

# 运行测试
./gradlew test

# 运行lint检查
./gradlew lint
```

### 部署到电视

#### 方法1：USB调试部署

1. 在Android TV上启用开发者选项和USB调试
2. 通过USB线连接电视到电脑
3. 在Android Studio中点击 **Run** 按钮
4. 选择TV设备作为目标

#### 方法2：网络ADB部署

1. 确保电视和电脑在同一WiFi网络
2. 在电视上获取IP地址（设置 > 关于 > 状态）
3. 通过ADB连接：
   ```bash
   adb connect <TV_IP>:5555
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

### 运行应用

1. 安装完成后，打开应用
2. 点击 **启动投屏服务**
3. 等待服务启动完成
4. 在手机上打开B站等视频App
5. 点击投屏按钮，选择 "凯机投屏" 设备

## 测试

### 单元测试

在Android Studio中：
1. 打开 `app/src/test/java/com/kbtv/caster/` 目录
2. 右键点击测试文件
3. 选择 **Run 'TestName'**

或使用命令行：
```bash
./gradlew test                                    # 运行所有测试
./gradlew test --tests "com.kbtv.caster.*"       # 运行所有测试类
./gradlew test --tests "com.kbtv.caster.dlna.TransportStateTest"  # 运行单个测试类
```

## 开发指南

### 代码规范

请参阅 [AGENTS.md](AGENTS.md) 文件，了解：
- 构建和测试命令
- 代码风格指南
- 命名约定
- 错误处理
- 日志规范
- 测试要求

### 调试

查看日志：
```bash
# 实时日志
adb logcat -d | grep -E "KBTVCaster|CastCoordinator|SSDP"

# 过滤DLNA相关日志
adb logcat -d | grep -E "DLNA|AVTransport|RenderingControl"
```

### 故障排除

#### 电视无法被发现

1. 确保电视和手机在同一网络
2. 检查防火墙设置允许多播
3. 重启电视上的KBTVCaster应用
4. 查看日志：`adb logcat -d | grep -E "caster|CastCoordinator|SSDP"`

#### 投屏失败

1. 检查DLNA服务是否运行（访问 http://TV_IP:5000/）
2. 验证网络连接
3. 查看日志：`adb logcat -d | grep -E "caster|AVTransport"`
4. 确保服务正在前台运行（通知栏有图标）

#### 媒体播放失败

1. 检查媒体URL是否可访问
2. 查看ExoPlayer日志：`adb logcat -d | grep -E "ExoPlayer|media"`
3. 确保电视有足够的网络带宽

## 技术说明

### 原生UPnP/DLNA实现

- 使用 **Android NsdManager** 实现mDNS服务发现
- 使用 **MulticastSocket** 实现SSDP协议响应
- 内置 **HTTP服务器** 响应设备描述和控制命令
- 实现 **AVTransport** 服务（播放/暂停/停止/SetURI）
- 实现 **RenderingControl** 服务（音量控制）

### ExoPlayer媒体播放

- 使用 **AndroidX Media3 ExoPlayer 1.2.1** 实现媒体播放
- 支持HTTP/HTTPS视频流播放
- 支持音量控制和静音功能
- 自动播放控制（Play/Pause/Stop）

## 关键依赖

| 库 | 版本 | 用途 |
|---|---|---|
| AndroidX Core-KTX | 1.12.0 | Android核心扩展 |
| AndroidX Lifecycle | 2.7.0 | 生命周期管理 |
| Media3 ExoPlayer | 1.2.1 | 媒体播放 |
| Kotlinx Coroutines | 1.7.3 | 异步操作 |
| OkHttp | 4.12.0 | HTTP客户端 |
| Timber | 5.0.1 | 日志 |
| Cling UPnP | TVRemoteIME | UPnP/DLNA实现 |
| JUnit | 4.13.2 | 单元测试 |
| Mockito | 5.8.0 | 模拟测试 |

## 已知限制

1. Android 6.0 (API 23) 兼容性测试有限
2. 某些App可能使用私有DLNA协议实现
3. 需要在同一局域网内才能发现设备
4. ExoPlayer需要网络连接才能播放流媒体
5. 部分投屏功能可能不兼容所有视频App

## 许可证

MIT License
