# 工程经验教训 - 凯机投屏项目

> 记录日期: 2025-01-17
> 目的: 避免重复犯错，指导未来迭代

---

## 核心经验教训

### 1. 复制开源项目不是复制单个文件

#### ❌ 错误做法
```kotlin
// 只复制了 DLNAUtils.java 调用
DLNAUtils.startDLNAService(this)
```

**结果：** 功能无法工作，因为缺少完整的依赖链。

#### ✅ 正确做法
```bash
# 需要复制的完整内容：
# 1. 完整的 Cling UPnP 栈
cp -r TVRemoteIME/org/fourthline/cling/ app/src/main/java/
# 2. 完整的 DroidDLNA 模块
cp -r TVRemoteIME/com/zxt/dlna/ app/src/main/java/
# 3. 配置文件
# AndroidManifest.xml 中注册所有服务
# assets/ 目录下放置必要资源
```

#### 📋 复制清单
- [ ] 完整的类层次结构
- [ ] 依赖的所有其他类
- [ ] AndroidManifest 配置
- [ ] 资源文件（assets、drawable 等）
- [ ] 配置文件

---

### 2. Manifest 配置同样重要

#### ❌ 错误做法
```kotlin
// 只调用了 DLNAUtils.startDLNAService()
// 但没有在 Manifest 中注册服务
```

#### ✅ 正确做法
```xml
<!-- AndroidManifest.xml 必须包含 -->
<service android:name="org.fourthline.cling.android.AndroidUpnpServiceImpl" 
         android:exported="false" />
         
<receiver android:name=".receiver.BootReceiver"
          android:enabled="true">
    <intent-filter>
        <action android:name="android.intent.action.BOOT_COMPLETED" />
    </intent-filter>
</receiver>
```

#### 📋 Manifest 检查清单
- [ ] 所有 Service 已注册
- [ ] 所有 Receiver 已注册
- [ ] 所有 Activity 已注册
- [ ] 必要的权限已添加
- [ ] 正确的 intent-filter 配置

---

### 3. MulticastLock 生命周期管理

#### ❌ 错误做法
```kotlin
// 太早释放 MulticastLock
val lock = wifiManager.createMulticastLock("SSDP")
lock.acquire()
lock.release()  // ❌ 收不到 M-SEARCH 请求！
```

#### ✅ 正确做法
```java
// 在 AndroidRouter 中管理
public class AndroidRouter extends DefaultRouter {
    private WifiMulticastLock multicastLock;
    private WifiLock wifiLock;
    
    public void setWiFiMulticastLock(boolean enabled) {
        if (enabled) {
            if (multicastLock == null) {
                multicastLock = wifiManager.createMulticastLock("Cling");
                multicastLock.setReferenceCounted(true);
            }
            multicastLock.acquire();
        }
        // ❌ 不要在这里释放！
        // 应该在服务销毁时释放
    }
    
    public void cleanup() {
        if (multicastLock != null && multicastLock.isHeld()) {
            multicastLock.release();
        }
    }
}
```

#### 📋 MulticastLock 原则
- [ ] 在服务创建时获取
- [ ] 持续持有直到服务销毁
- [ ] 不要在临时方法中释放
- [ ] 使用 setReferenceCounted(true)

---

### 4. 网络接口绑定

#### ❌ 错误做法
```kotlin
// 没有正确绑定到正确的网络接口
val server = HttpServer(port = 5000)
// 可能绑定到错误的接口
```

#### ✅ 正确做法
```java
// 使用 Cling 的 AndroidNetworkAddressFactory
public class AndroidNetworkAddressFactory extends DefaultNetworkAddressFactory {
    @Override
    public InetAddress getLocalAddress(NetworkInfo networkInfo) {
        // 遍历所有网络接口
        // 找到正确的 WiFi 接口
        // 返回正确的本地 IP 地址
    }
}
```

#### 📋 网络检查清单
- [ ] 正确获取本地 IP 地址
- [ ] 绑定到正确的网络接口
- [ ] 处理网络变化
- [ ] 处理多网卡情况

---

### 5. 资源文件完整性

#### ❌ 错误做法
```java
// 缺少图标文件，导致设备注册失败
ZxtMediaRenderer renderer = new ZxtMediaRenderer(...);
// 日志: createDefaultDeviceIcon IOException
```

#### ✅ 正确做法
```bash
# 复制必要的资源文件
cp TVRemoteIME/app/src/main/assets/* app/src/main/assets/
# 或修改代码跳过图标加载
```

#### 📋 资源检查清单
- [ ] assets/ 目录完整
- [ ] drawable/ 目录完整
- [ ] 必要的图标文件存在
- [ ] 配置文件完整

---

## 投屏协议工作原理

### DLNA/UPnP 架构

```
手机 (控制点)                    电视 (渲染器)
    │                              │
    │  1. SSDP M-SEARCH            │
    │ ────────────────────────────>│
    │                              │
    │  2. SSDP 200 OK              │
    │ <───────────────────────────│
    │                              │
    │  3. HTTP GET description.xml │
    │ ────────────────────────────>│
    │                              │
    │  4. HTTP 200 OK (XML)        │
    │ <───────────────────────────│
    │                              │
    │  5. SOAP SetAVTransportURI   │
    │ ────────────────────────────>│
    │                              │
    │  6. SOAP Play                │
    │ ────────────────────────────>│
    │                              │
    │         视频开始播放          │
```

### 每个步骤的关键

| 步骤 | 关键组件 | 常见问题 |
|------|---------|---------|
| 1. M-SEARCH | MulticastLock | 没有持续持有 |
| 2. 200 OK | SSDP 处理 | 没有正确响应 |
| 3. GET XML | HTTP 服务 | 服务未启动 |
| 4. XML 响应 | 设备描述 XML | XML 不完整 |
| 5. SetURI | AVTransportService | 服务未注册 |
| 6. Play | ExoPlayer | 没有集成 |

---

## 未来迭代指导原则

### 1. 先理解，再实现

#### ❌ 不要这样做
```kotlin
// 不理解原理就复制代码
val result = someLibrary.function()
```

#### ✅ 应该这样做
```kotlin
// 1. 阅读文档
// 2. 理解架构
// 3. 查看示例
// 4. 理解每个参数的作用
// 5. 再实现
```

### 2. 完整复制 + 验证

#### ✅ 复制清单
- [ ] 所有 Java/Kotlin 文件
- [ ] 所有资源文件
- [ ] Manifest 配置
- [ ] 依赖配置
- [ ] 测试用例

#### ✅ 验证清单
- [ ] 代码能编译
- [ ] 服务能启动
- [ ] 设备能被搜索
- [ ] 能接收投屏请求
- [ ] 能正常播放

### 3. 举一反三

#### 不是这样做
```kotlin
// 只会复制粘贴
val a = otherProject.functionA()
val b = otherProject.functionB()
```

#### 应该这样做
```kotlin
// 1. 理解 otherProject.functionA() 为什么工作
// 2. 理解我们的需求
// 3. 设计适合我们的方案
// 4. 实现并优化
// 5. 写好文档
```

### 4. 模块化设计

#### ✅ 好的设计
```
com.caster.tv/
├── dlna/           # DLNA 模块（可替换）
│   ├── cling/      # Cling 实现
│   └── dmr/        # 渲染器
├── player/         # 播放器模块（可替换）
│   ├── exoplayer/  # ExoPlayer 实现
│   └── interface/  # 统一接口
└── ui/             # UI 模块
```

#### ✅ 好处
- 可以替换不同的 DLNA 库
- 可以替换不同的播放器
- 便于测试和维护

### 5. 错误处理

#### ❌ 错误做法
```kotlin
try {
    operation()
} catch (e: Exception) {
    // 吞掉所有异常
}
```

#### ✅ 正确做法
```kotlin
try {
    operation()
} catch (e: SpecificException) {
    // 处理特定异常
    Timber.e(e, "Specific error message")
    recoverFromError()
} catch (e: Exception) {
    // 处理未知异常
    Timber.e(e, "Unexpected error")
    reportToServer()
}
```

---

## 测试指南

### 单元测试
```kotlin
@Test
fun `MulticastLock should be acquired on service start`() {
    // 测试 MulticastLock 获取
}

@Test
fun `HTTP server should start on correct port`() {
    // 测试 HTTP 服务启动
}

@Test
fun `Device description XML should be valid`() {
    // 测试设备描述 XML
}
```

### 集成测试
```kotlin
@Test
fun `Phone should discover TV device`() {
    // 1. 启动电视服务
    // 2. 从手机发送搜索请求
    // 3. 验证收到响应
}
```

### 手动测试
```bash
# 1. 启动服务
adb shell am start -n com.caster.tv/.ui.MainActivity

# 2. 查看日志
adb logcat | grep -E "凯机投屏|DLNAUtils|Cling"

# 3. 从手机搜索设备
# 打开 B站，点击投屏

# 4. 验证投屏
# 检查电视是否开始播放
```

---

## 问题排查手册

### 问题：设备无法被发现

| 可能原因 | 排查方法 | 解决方案 |
|---------|---------|---------|
| MulticastLock 问题 | 检查日志是否有 `MulticastLock` 相关错误 | 确认锁持续持有 |
| 网络绑定问题 | 检查本地 IP 地址 | 确认绑定到正确接口 |
| HTTP 服务问题 | 检查端口 5000 是否监听 | 确认服务已启动 |
| 设备描述问题 | 访问 `http://TV_IP:5000/description.xml` | 确认 XML 完整 |

### 问题：能发现但无法投屏

| 可能原因 | 排查方法 | 解决方案 |
|---------|---------|---------|
| AVTransport 问题 | 检查日志是否有 `setAVTransportURI` | 确认服务注册 |
| ExoPlayer 问题 | 检查播放状态 | 确认播放器集成 |
| 权限问题 | 检查 Logcat 错误 | 添加必要权限 |

### 问题：视频无法播放

| 可能原因 | 排查方法 | 解决方案 |
|---------|---------|---------|
| URL 问题 | 检查日志中的 URL | 确认 URL 有效 |
| 编码问题 | 检查 ExoPlayer 日志 | 添加必要解码器 |
| 网络问题 | 检查能否访问 URL | 确认网络畅通 |

---

## 代码规范

### 文件命名
```
类名: PascalCase
函数名: camelCase
常量: UPPER_SNAKE_CASE
包名: lowercase
```

### 注释规范
```kotlin
/**
 * 功能描述
 *
 * @param paramName 参数说明
 * @return 返回值说明
 * @throws Exception 可能抛出的异常
 */
```

### 日志规范
```kotlin
Timber.d("Debug information")      // 详细调试
Timber.i("Operation completed")    // 重要里程碑
Timber.w("Something unexpected")   // 警告
Timber.e(e, "Failed operation")    // 错误
```

---

## 总结

### 核心原则

1. **先理解，后实现** - 不理解原理就会犯错
2. **完整复制** - 不要只复制皮毛
3. **全面测试** - 每个环节都要验证
4. **模块设计** - 便于维护和替换
5. **错误处理** - 不要吞掉异常

### 避免的错误

1. ❌ 只复制单个文件，不复制完整依赖链
2. ❌ 忽略 Manifest 配置
3. ❌ MulticastLock 太早释放
4. ❌ 网络绑定不正确
5. ❌ 资源文件不完整

### 成功要素

1. ✅ 理解 DLNA/UPnP 协议原理
2. ✅ 正确配置 Cling UPnP 栈
3. ✅ 正确管理生命周期
4. ✅ 完整测试所有环节
5. ✅ 记录经验教训

---

> "复制不是目的，理解才是关键。"
> "不要只做代码的搬运工，要做问题的解决者。"

