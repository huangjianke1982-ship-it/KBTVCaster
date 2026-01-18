# CasterTV - Android投屏服务

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
CasterTVService/
├── app/
│   ├── src/main/
│   │   ├── java/com/caster/tv/
│   │   │   ├── CasterApplication.kt          # 应用入口
│   │   │   ├── service/
│   │   │   │   └── CastCoordinatorService.kt # 主服务协调器
│   │   │   ├── dlna/
│   │   │   │   ├── DLNAMediaRendererService.kt  # DLNA/UPnP服务
│   │   │   │   ├── AVTransportService.kt        # 播放控制服务
│   │   │   │   └── RenderingControlService.kt   # 渲染控制服务
│   │   │   ├── airplay/
│   │   │   │   └── AirPlayService.kt         # AirPlay服务
│   │   │   ├── mirror/
│   │   │   │   ├── ScreenMirrorService.kt    # 屏幕镜像服务
│   │   │   │   └── WifiDisplayReceiver.kt    # WiFi直连接收器
│   │   │   ├── control/
│   │   │   │   └── FileCastService.kt        # 文件投屏服务
│   │   │   └── ui/
│   │   │       └── MainActivity.kt           # 主界面
│   │   ├── res/                              # 资源文件
│   │   └── AndroidManifest.xml               # 应用清单
│   └── src/test/                             # 单元测试
├── gradle/wrapper/                           # Gradle包装器
├── build.gradle.kts                          # 根构建配置
├── settings.gradle.kts                       # 项目设置
├── gradle.properties                         # Gradle属性
└── local.properties                          # 本地配置
```

## 环境要求

- **Android Studio**：Android Studio Otter 2 Feature Drop | 2025.2.2 Patch 1
- **JDK**：17 (Android Studio自带)
- **Gradle**：8.7
- **Android SDK**：Android 6.0 (API 23) 及以上

## 在Android Studio中打开项目

1. 打开Android Studio
2. 选择 **Open** 或 **Import Project**
3. 导航到 `CasterTVService` 文件夹
4. 等待Gradle同步完成
5. 连接Android TV设备（通过USB或网络ADB）

## 部署到电视

### 方法1：USB调试部署

1. 在Android TV上启用开发者选项和USB调试
2. 通过USB线连接电视到电脑
3. 在Android Studio中点击 **Run** 按钮
4. 选择TV设备作为目标

### 方法2：网络ADB部署

1. 确保电视和电脑在同一WiFi网络
2. 在电视上获取IP地址（设置 > 关于 > 状态）
3. 通过ADB连接：
   ```bash
   adb connect <TV_IP>:5555
   ```
4. 在Android Studio中运行项目

## 运行应用

1. 安装完成后，打开应用
2. 点击 **启动投屏服务**
3. 等待服务启动完成
4. 在手机上打开B站等视频App
5. 点击投屏按钮，选择 "CasterTV-XXXX" 设备

## 测试

### 单元测试

在Android Studio中：
1. 打开 `app/src/test/java/com/caster/tv/` 目录
2. 右键点击测试文件
3. 选择 **Run 'TestName'**

或使用命令行：
```bash
./gradlew test
```

###  lint检查

```bash
./gradlew lint
```

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

## 已知限制

1. Android 6.0 (API 23) 兼容性测试有限
2. 某些App可能使用私有DLNA协议实现
3. 需要在同一局域网内才能发现设备
4. ExoPlayer需要网络连接才能播放流媒体

## 故障排除

### 电视无法被发现

1. 确保电视和手机在同一网络
2. 检查防火墙设置允许多播
3. 重启电视上的CasterTV应用
4. 查看日志：`adb logcat -d | grep -E "caster|CastCoordinator|SSDP"`

### 投屏失败

1. 检查DLNA服务是否运行（访问 http://TV_IP:5000/）
2. 验证网络连接
3. 查看日志：`adb logcat -d | grep -E "caster|AVTransport"`
4. 确保服务正在前台运行（通知栏有图标）

### 媒体播放失败

1. 检查媒体URL是否可访问
2. 查看ExoPlayer日志：`adb logcat -d | grep -E "ExoPlayer|media"`
3. 确保电视有足够的网络带宽

## 许可证

MIT License
