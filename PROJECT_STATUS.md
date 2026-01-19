# 凯机投屏 - Android TV DLNA 投屏应用

## 项目状态：✅ 已完成并稳定运行

基于 TVRemoteIME 的成熟 DLNA 实现，专为 Android TV 优化的极简投屏应用。

---

## 功能清单 ✅

### 核心功能
- ✅ **DLNA/UPnP 设备发现** - 手机端可发现电视设备
- ✅ **MediaRenderer 设备** - 电视作为接收端注册到 UPnP 网络
- ✅ **视频播放** - ExoPlayer 实际播放投屏内容
- ✅ **播放控制** - 支持 Play/Pause/Stop/Seek
- ✅ **音量控制** - 支持音量调节

### 极简 UI 设计
- ✅ **待机界面** - 电视形状框架 + "凯机投屏！启动！"大字体
- ✅ **自动启动** - 打开 App 自动启动投屏服务
- ✅ **全屏播放** - 投屏成功后自动全屏，UI 完全隐藏
- ✅ **返回最小化** - 按返回键最小化到后台，服务继续运行

### 遥控器控制（完全支持）
- ✅ **播放/暂停键** - 播放/暂停
- ✅ **停止键** - 停止播放，返回待机
- ✅ **OK/选择键** - 暂停/播放切换
- ✅ **方向键左/右** - 快退/快进 ±10秒
- ✅ **方向键上/下** - 音量 +/-
- ✅ **返回键** - 待机界面返回桌面，播放时停止播放
- ✅ **静音键** - 静音切换

### 系统集成
- ✅ **开机自启动** - 电视开机后自动启动服务
- ✅ **后台服务** - 服务持续运行，支持手机发现和投屏
- ✅ **前台通知** - 显示服务运行状态

---

## 技术栈

| 组件 | 版本/来源 | 用途 |
|------|----------|------|
| Kotlin | 1.9.x | 主开发语言 |
| Java | 8 | Cling UPnP 栈 |
| Media3 ExoPlayer | 1.2.1 | 视频播放 |
| Cling | 2.0.1 (TVRemoteIME 移植) | UPnP 协议栈 |
| AndroidX Lifecycle | 2.6.x | 服务生命周期 |
| Timber | 5.0.1 | 日志 |

---

## 项目结构

```
app/src/main/java/
├── com/caster/tv/
│   ├── service/
│   │   └── CastCoordinatorService.kt   # 主服务协调 (Kotlin)
│   ├── dlna/
│   │   └── DLNAUtils.java              # DLNA 工具类 (Java)
│   ├── ui/
│   │   └── MainActivity.kt             # 主界面 (Kotlin)
│   ├── receiver/
│   │   └── BootReceiver.kt             # 开机自启动 (Kotlin)
│   └── CasterApplication.kt            # Application 类
│
├── org/fourthline/cling/android/       # Cling UPnP 栈 (Java)
│   ├── AndroidUpnpServiceImpl.java
│   ├── AndroidRouter.java
│   ├── AndroidNetworkAddressFactory.java
│   └── NetworkUtils.java
│
└── com/zxt/dlna/dmr/                   # DroidDLNA 渲染器 (Java)
    ├── ZxtMediaRenderer.java
    ├── AVTransportService.java
    ├── AudioRenderingControl.java
    ├── ZxtConnectionManagerService.java
    └── ZxtMediaPlayer.java
```

---

## 资源文件

```
app/src/main/res/
├── layout/
│   └── activity_main.xml               # 主界面布局（极简电视形状）
├── drawable/
│   ├── tv_frame_background.xml         # 电视框架背景
│   └── remote_button_bg.xml            # 遥控器按钮背景（已移除虚拟遥控器）
└── values/
    ├── strings.xml                     # 字符串资源
    ├── colors.xml                      # 颜色配置
    └── themes.xml                      # 主题配置
```

---

## 使用方式

### 构建 APK
```bash
cd CasterTVService
./gradlew assembleDebug
```

### 安装到电视
```bash
adb connect 192.168.0.49:5555
adb install app/build/outputs/apk/debug/app-debug.apk
```

### 启动服务
- 打开凯机投屏 App → 自动启动服务
- 或重启电视 → 自动启动

### 手机投屏
1. 打开 B站/小电视/腾讯视频等 App
2. 选择视频 → 点击投屏按钮
3. 选择 "凯机投屏" 设备
4. 视频自动全屏播放

### 遥控器控制
| 按键 | 功能 |
|------|------|
| 返回键 | 待机界面：最小化到后台；播放时：停止播放 |
| OK/选择键 | 暂停/播放 |
| 播放/暂停键 | 播放/暂停 |
| 停止键 | 停止播放，返回待机 |
| 方向键左/右 | 快退/快进 ±10秒 |
| 方向键上/下 | 音量 +/- |
| 返回键 | 最小化到后台 |

---

## 日志查看

```bash
adb -s 192.168.0.49:5555 logcat | grep -E "凯机投屏|MainActivity|CastCoordinator|DLNAUtils"
```

### 关键日志标识

| 日志 | 含义 |
|------|------|
| `凯机投屏 DLNA服务已启动` | 服务启动成功 |
| `PlayerView bound` | PlayerView 绑定成功 |
| `Showing video` | 显示视频 |
| `Showing standby UI` | 显示待机界面 |
| `Minimizing app to background` | 最小化到后台 |

---

## 测试电视信息

```
电视 IP: 192.168.0.49
电视型号: SHARP LCD-xxSUFOC5A
Android 版本: 8.0 (API 23)
ADB 端口: 5555
```

---

## 已验证功能 ✅

| 功能 | 状态 | 说明 |
|------|------|------|
| 设备发现 | ✅ | 手机端可发现"凯机投屏" |
| 收到投屏请求 | ✅ | 日志确认收到 setAVTransportURI |
| ExoPlayer 播放 | ✅ | playback state 3 (播放中) |
| 视频渲染 | ✅ | 全屏显示视频内容 |
| 服务稳定性 | ✅ | Foreground Service 持续运行 |
| 开机自启动 | ✅ | BootReceiver 自动启动服务 |
| 返回键最小化 | ✅ | App 最小化，服务继续运行 |
| 手机重新投屏 | ✅ | 无需打开 App，自动播放 |

---

## 待实现功能（后续迭代）

- [ ] 播放历史记录
- [ ] 多设备管理
- [ ] 画质选择
- [ ] 弹幕支持
- [ ] 投屏协议优化
- [ ] 其他用户需求...

---

## 已知问题

1. **无播放历史** - 暂不支持记录播放历史
2. **无画质选择** - 暂不支持选择不同画质
3. **无弹幕支持** - 暂不支持弹幕显示

---

## 版本历史

| 版本 | 日期 | 更新内容 |
|------|------|----------|
| 1.0.0 | 2025-01-17 | 初始版本，DLNA 投屏功能完成 |
| 1.1.0 | 2025-01-17 | 极简 UI 设计，遥控器控制优化 |
| 1.2.0 | 2025-01-17 | 返回键最小化，服务后台运行 |

---

## 依赖版本

| 库 | 版本 | 用途 |
|----|------|------|
| AndroidX Media3 ExoPlayer | 1.2.1 | 视频播放 |
| AndroidX Lifecycle | 2.6.2 | 服务生命周期 |
| Timber | 5.0.1 | 日志 |
| Kotlin Coroutines | 1.7.3 | 异步操作 |
| Cling | 2.0.1 (本地) | UPnP 协议栈 |

---

## APK 信息

```
文件名: app-debug.apk
大小: ~11.5 MB
包名: com.caster.tv
版本: 1.2.0
```

---

**当前版本**: 1.2.0  
**最后更新**: 2025-01-17  
**维护状态**: ✅ 稳定运行，可投入生产使用
