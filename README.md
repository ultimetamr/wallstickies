# 墙面永久空间便签（Wall Stickies）

基于 PICO Spatial SDK 开发的空间提醒工具。用户可将文字便签和待办清单创建在真实墙面或桌面位置；应用重进后通过 Persistent Spatial Anchor 恢复到对应物理位置，实现“进屋即看提醒”的空间记忆体验。

包名：`com.spatialapps.wallstickies`

## 当前能力

- 左手柄射线命中已识别墙面/桌面后创建便签，避免在空气中创建。
- 便签支持标题、正文、待办清单、勾选状态、删除、纯色/磨砂样式和预设颜色。
- 每张便签拥有独立 PICO Persistent Spatial Anchor UUID，并通过 Room 本地持久化保存内容与锚点关联。
- 应用启动时加载当前应用全部空间锚点，按 UUID 与 Room 记录匹配恢复。
- 订阅锚点 `LOADED` / `UPDATED` 事件，支持延迟定位与空间坐标更新。
- 编辑器与短提示面板在头显前方显示；编辑或后台状态下禁用锚点选择，防止误触发新建。
- 提供 `WallStickiesRestore`、`WallStickiesAnchor`、`WallStickiesRender`、`WallStickiesInput` 日志用于恢复与交互排障。

## 技术方案

```text
DefaultStage（Mixed / Full Space）
├── SpatialView
│   ├── 便签 AttachmentPanel（世界锚定）
│   ├── 编辑器 AttachmentPanel（跟随头显）
│   └── 提示 AttachmentPanel（跟随头显）
├── WorldTrackingManager（Persistent Spatial Anchor）
└── Room（便签内容、样式、待办与 anchorUuid）
```

> Persistent Spatial Anchor 需要在 Full Space Stage 中使用。虽然便签 UI 是平面 SpatialUI 面板，但不能仅依赖 Shared Space 的普通 WindowContainer 实现真实空间持久化。

## 构建与测试

环境要求：JDK 17、Android SDK、PICO Spatial SDK、Gradle Wrapper 8.13。

```powershell
.\gradlew.bat assembleDebug --no-daemon
.\gradlew.bat testDebugUnitTest --no-daemon
```

Debug APK：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 安装到 PICO 设备

先查看设备：

```powershell
pico-cli device list --format json
```

安装并启动（将设备 ID 替换为真实头显 ID）：

```powershell
pico-cli app install app/build/outputs/apk/debug/app-debug.apk --device <device-id>
pico-cli app launch com.spatialapps.wallstickies --device <device-id>
```

如启动日志出现 `current full space don't allow start fullscreen`，说明另一个 Full Space 应用或系统边界设置正占用空间。请先在头显内退出该 Full Space 应用，再重新启动本应用。

## 锚点恢复诊断

恢复链路为：

```text
Room 全部便签 → loadAnchor() 加载本应用全部锚点
→ anchorUuid 匹配 → 更新对应 Entity Transform → AttachmentPanel 显示
```

排查时依次确认：

1. `WallStickiesRestore` 中 Room 读取的便签数量；
2. Spatial SDK 返回的 anchor 数量；
3. 每张便签的 `restored=true/false`；
4. `WallStickiesRender` 中的 `display=ATTACHED`。

PICO 模拟器可用于构建、启动和日志调试，但多锚点持久化、设备重启和位置漂移必须以真实头显验收为准。

## 项目文档

- [空间固定标签应用规格基线](docs/SPATIAL_STICKY_NOTES_SPEC.md)：可复用于同类空间标签、固定提醒与空间待办项目的产品、交互、锚点恢复和验收规范。
- [开发约束与当前验证状态](AGENTS.md)

## 目录结构

```text
app/src/main/java/com/spatialapps/wallstickies/
├── content/        # SpatialUI、空间交互与 Stage
├── data/local/     # Room 数据库
├── data/repository/# Room 与 World Anchor 桥接
├── domain/         # 便签领域模型与用例
└── platform/       # PICO 应用与启动入口
```
