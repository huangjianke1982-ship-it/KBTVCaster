# CasterTV - 技术方案分析

## 问题分析

### 当前状态
- ✅ HTTP服务器 (端口5000) - 正常工作
- ✅ SSDP NOTIFY发送 - 正常工作
- ✅ NSD/mDNS服务 - 已注册3个服务类型
- ✅ 手动连接UI - 已添加
- ❌ SSDP M-SEARCH接收 - **失败**（端口1900被系统占用）

### 根因
Android TV系统服务已绑定UDP端口1900，导致第三方应用无法接收SSDP M-SEARCH广播请求。

## 现有解决方案分析

### 1. TVRemoteIME项目方案
- 使用**DroidDLNA**库（基于Cling）
- 支持DLNA视频投屏
- 在创维、华为等盒子正常工作
- **但不确定是否解决了端口1900问题**

### 2. UPnPCast（现代方案）
- 现代Android DLNA/UPnP库 (2025)
- 适用于**手机端**作为控制点
- 不适用于TV端作为接收端

### 3. WiFi Direct方案
- P2P直连，不依赖SSDP
- 需要手机端也支持WiFi Direct
- 绕过系统端口限制

## 推荐方案

### 方案A：使用Cling库作为Media Renderer
1. 集成DroidDLNA/Cling库
2. 实现完整的UPnP Media Renderer
3. 尝试使用SO_REUSEADDR绑定端口

### 方案B：WiFi Direct直连
1. 使用Android WiFi Direct API
2. 创建P2P组，电视作为Group Owner
3. 手机直接连接电视的WiFi Direct地址

### 方案C：接受现实，提供替代方案
1. 完善手动连接功能
2. 提供QR码扫描
3. 提供IP地址显示和复制
4. 文档说明限制和替代方案

## 当前实现已完成

### ✅ HTTP端点（全部正常）
- `/` - UPnP设备描述
- `/cast.json` - 设备发现JSON
- `/setup/eureka_info` - Google Cast信息
- `/ssdp/device_info.xml` - DIAL设备信息
- `/host_info` - 网络服务信息
- `/apps/` - DIAL应用列表

### ✅ NSD服务（已注册）
- `_http._tcp.` - HTTP服务
- `_googlecast._tcp.` - Google Cast兼容
- `_cast._tcp.` - 通用Cast服务

### ✅ 手动连接功能
- IP地址显示
- 复制到剪贴板
- 快速打开浏览器
- QR码信息对话框

## 建议下一步

1. **测试UPnPCast** - 在手机端使用UPnPCast库，看是否能发现CasterTV
2. **集成DroidDLNA** - 参考TVRemoteIME的实现
3. **WiFi Direct** - 如果前述方案都失败

## 参考项目

- [TVRemoteIME](https://gitee.com/kingthy/TVRemoteIME.git) - 成功的DLNA实现
- [UPnPCast](https://github.com/yinnho/UPnPCast) - 现代DLNA库
- [DroidDLNA](https://github.com/offbye/DroidDLNA) - 基于Cling的DLNA库
- [android-upnp-discovery](https://github.com/custanator/android-upnp-discovery) - SSDP发现示例
