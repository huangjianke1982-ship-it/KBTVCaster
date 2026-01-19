# 📋 项目版本记录Skill

## Skill名称
`project-versioning`

## 触发短语
- "记录项目"
- "record project"
- "版本"
- "version"
- "create release"
- "发布版本"

## 分类
开发管理 (Development Management)

## 难度
中级 (Intermediate)

## 执行步骤

### Step 1: 分析变更
- 查看git log了解上次版本后的变更
- 确认变更的文件和内容
- 理解用户需求

### Step 2: 确定版本号
使用语义化版本：
- **主版本 (Major)**: 不兼容的重大变更
- **次版本 (Minor)**: 新功能（向后兼容）
- **补丁 (Patch)**: Bug修复，小改进

### Step 3: 提交代码
```bash
git add -A
git commit -m "Release v{x.x.x} - {变更简述}"
```

### Step 4: 创建tag
```bash
git tag -a v{x.x.x} -m "Release v{x.x.x} - {变更简述}"
```

### Step 5: 编写更新日志
创建 `VERSION_v{x.x.x}.md`，包含：
- 发布日期
- 版本号
- 状态（✅测试通过 / 🔄测试中）
- 版本概述
- 新功能
- 修复内容
- 改进内容
- 变更文件
- 回退命令
- 测试结果
- 上一个版本链接

### Step 6: 提交更新日志
```bash
git add VERSION_v{x.x.x}.md
git commit -m "Add v{x.x.x} changelog"
```

### Step 7: 更新tag
```bash
git tag -d v{x.x.x}
git tag -a v{x.x.x} -m "Release v{x.x.x} - {变更简述}"
```

## 输出

### 成功时
```
✅ Project version v{x.x.x} has been recorded!

Git记录：
- commit: {hash}
- tag: v{x.x.x}

文件：
- VERSION_v{x.x.x.md}

回退命令：
git checkout v{x.x.x}
git reset --hard v{x.x.x}
```

### 回退命令
```bash
git checkout master
git reset --hard v{x.x.x-1}
git tag -d v{x.x.x}
```

## 使用示例

```
用户: "记录项目"
系统: [分析变更，给出版本号建议，列出计划]
用户: "同意"
系统: [执行所有操作]
```

## 注意事项

1. **始终先提交计划**：向用户展示要做什么，等待确认
2. **使用语义化版本**：遵循主版本.次版本.补丁版本规则
3. **更新日志用中文**：项目语言为简体中文
4. **包含回退命令**：让用户可以随时回退到任何版本
5. **测试通过后再记录**：确保版本稳定

## 当前项目状态

- **最新tag**: v0.1.1
- **versionName**: "0.1.1"
- **状态**: ✅ 测试通过
- **上次记录**: 2026-01-19

## 相关文件

- `SKILL_project_versioning.md` - 工作流程详细说明
- `VERSION_v*.md` - 版本更新日志

---

*此Skill用于管理项目的版本记录，确保每次稳定版本都有完整记录和可回退能力。*
