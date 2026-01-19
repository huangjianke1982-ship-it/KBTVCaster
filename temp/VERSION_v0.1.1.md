# v0.1.1 更新日志

**发布日期**: 2026-01-19
**版本号**: 0.1.1
**状态**: ✅ 测试通过，稳定版本

## 📋 版本概述

这是一个稳定版本，修复了后台投屏的视频显示问题，并优化了UI体验。

## ✨ 新功能

### 1. 后台投屏自动显示视频
- **问题**: app在后台运行时，投屏只有声音没有画面
- **解决方案**: 在收到投屏指令时，自动检测并拉起MainActivity到前台
- **相关文件**: `CastCoordinatorService.kt`

### 2. 系统音量UI
- **功能**: 使用系统原生音量对话框
- **步进**: 每次调节5格音量（最大100格）
- **实现**: 使用`FLAG_SHOW_UI`标志
- **相关文件**: `MainActivity.kt`

### 3. 自定义进度条UI
- **功能**: 底部显示当前时间和总时长
- **SeekBar**: 白色系进度条，可拖动
- **自动隐藏**: 3秒无操作后自动消失
- **相关文件**: `activity_main.xml`, `MainActivity.kt`

## 🐛 修复

### 后台投屏视频不显示
- **原因**: PlayerView依赖Activity可见性，后台时Surface被销毁
- **修复**: 投屏时自动将Activity带到前台

## 🔧 改进

1. **简化代码**: 移除了复杂的自定义音量UI，保留系统原生体验
2. **布局优化**: 移除冗余的音量控制布局
3. **代码清理**: 删除未使用的import和变量

## 📁 变更文件

```
app/src/main/java/com/caster/tv/service/CastCoordinatorService.kt
app/src/main/java/com/caster/tv/ui/MainActivity.kt
app/src/main/res/layout/activity_main.xml
app/build.gradle.kts
```

## 🔀 回退到本版本

```bash
# 方法1: 切换到tag
git checkout v0.1.1

# 方法2: 重置到tag
git reset --hard v0.1.1
```

## 📊 测试结果

- ✅ 后台投屏自动显示视频
- ✅ 音量调节显示系统UI
- ✅ 进度条显示正常
- ✅ 遥控器控制正常
- ✅ 前进/后退10秒正常

## 📝 提交记录

- `ca7181c` - Release v0.1.1 - Stable playback with UI improvements

---

**上一个版本**: [v0.1.0](./VERSION_v0.1.0-device-discovery-working.md)
