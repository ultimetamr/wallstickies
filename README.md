# 墙面永久空间便签（Wall Stickies）

基于 PICO Spatial SDK 开发的空间提醒工具。用户可将文字便签和待办清单创建在真实墙面或桌面位置；应用重进后通过 Persistent Spatial Anchor 恢复到对应物理位置，实现“进屋即看提醒”的空间记忆体验。

包名：`com.spatialapps.wallstickies`

## 当前能力

- 左手柄射线命中已识别墙面/桌面后创建便签，避免在空气中创建。
- 支持读取双手关节姿态：使用“头显位置 → 食指尖”射线检测真实表面，有效命中时显示绿色标记，捏合后进入创建流程。
- 便签支持标题、正文、待办清单、勾选状态、删除、纯色/磨砂样式和预设颜色。
- 每张便签拥有独立 PICO Persistent Spatial Anchor UUID，并通过 Room 本地持久化保存内容与锚点关联。
- 应用启动时加载当前应用全部空间锚点，按 UUID 与 Room 记录匹配恢复。
- 订阅锚点 `LOADED` / `UPDATED` 事件，支持延迟定位与空间坐标更新。
- 编辑器与短提示面板共同挂载到相机目标 `AnchorComponent`，固定在用户前方约 `0.9m`，无需逐帧手动同步头显姿态。
- 编辑器打开或应用进入后台时禁用表面选择，防止点击编辑、删除或重新进入应用时误触发新建。
- 提供 `WallStickiesRestore`、`WallStickiesAnchor`、`WallStickiesRender`、`WallStickiesInput` 日志用于恢复与交互排障。

### 功能状态

| 功能 | 状态 | 说明 |
| --- | --- | --- |
| 手柄射线创建 | 已实现 | 左手柄射线命中墙面或桌面后按扳机。 |
| 捏合创建 | 已实现，需设备支持 | 原始手势追踪不可用时自动保留手柄操作路径。 |
| 点击编辑、按钮删除 | 已实现 | 编辑器打开期间关闭创建检测。 |
| 待办清单及勾选持久化 | 已实现 | 内容通过 Room 保存。 |
| 相机锚定编辑器/提示窗 | 已实现 | 两个面板共享同一个相机锚点。 |
| 拖拽后重新锚定 | 规划中 | 需要创建新锚点、更新 Room，再删除旧锚点并支持失败回滚。 |
| 长按删除 | 规划中 | 计划采用长按进度反馈和短时撤销。 |
| 凝视显示操作浮层 | 规划中 | 需要统一 Pointer 事件和设备眼动能力检测。 |

> 当前测试用真实设备曾返回 `HandTrackingProvider support=DEVICE_NOT_SUPPORTED`。这表示该设备当前无法向应用提供原始手部姿态，可能与设备型号、系统版本或手势开关有关；此时请使用左手柄。眼动和手势功能必须在运行时检查 `supportState`，不能仅凭 SDK 中存在对应 API 判断可用。

## 技术方案

```text
DefaultStage（Mixed / Full Space）
├── SpatialView
│   ├── 便签 AttachmentPanel（世界锚定）
│   ├── Camera Anchor（跟随头显）
│   │   ├── 编辑器 AttachmentPanel
│   │   └── 提示 AttachmentPanel
│   └── 手势有效表面命中标记
├── PlaneTrackingManager（墙面/桌面检测）
├── Hand / Controller / HMD Tracking Provider
├── WorldTrackingManager（Persistent Spatial Anchor）
└── Room（便签内容、样式、待办与 anchorUuid）
```

> Persistent Spatial Anchor 需要在 Full Space Stage 中使用。虽然便签 UI 是平面 SpatialUI 面板，但不能仅依赖 Shared Space 的普通 WindowContainer 实现真实空间持久化。

## 空间交互

### 创建便签

```text
射线或手势指向真实表面
→ PlaneTrackingManager 返回墙面/桌面候选平面
→ 射线与平面三角形求交
→ 有效命中显示反馈
→ 扳机或捏合确认
→ 编辑内容
→ 创建 Persistent Spatial Anchor
→ Room 保存内容、样式、最后位置和 anchorUuid
```

桌面和墙面只决定是否允许创建以及锚点位置。便签创建方向使用确认时的头显水平朝向，避免文字平铺在桌面上难以阅读。

### 输入降级顺序

```text
真实眼动/手势可用 → 空间 Pointer 或捏合
手势不可用        → 左手柄射线与扳机
未命中有效表面    → 不创建，仅显示短提示
```

PICO 模拟器可使用鼠标验证系统 Eye Gesture Mode 和普通 SpatialUI 控件，但原始 `HandTrackingProvider` 可能返回不支持，因此不能用模拟器证明真实手势关节追踪可用。

## 开发环境安装（Windows）

按以下顺序准备环境：Git → Node.js → Android Studio / JDK → Codex（可选）→ PICO CLI。

### 1. 基础工具

- 安装 [Git for Windows](https://git-scm.com/download/win)，安装时选择加入 `PATH`。
- 安装 [Node.js LTS](https://nodejs.org/)，需要 Node.js 18+。
- 安装 Android Studio，并通过 SDK Manager 安装项目所需的 Android SDK / Platform Tools。
- 本项目当前已使用 **JDK 17** 验证构建；设置 `JAVA_HOME` 指向 JDK 17，并将 `%JAVA_HOME%\bin` 放入 `Path`。

重新打开 PowerShell 后验证：

```powershell
git --version
node --version
npm --version
java -version
```

### 2. 安装 PICO CLI 与 Codex 插件

```powershell
npm install -g @picoxr/pico-cli
pico-cli --version

# 使用 Codex 开发时执行；完成后重启 Codex 或创建新的 task。
npm install -g @openai/codex
pico-cli setup --tool codex --plugin pico-spatial-agentic-tools --yes
```

检查 PICO 开发环境：

```powershell
pico-cli doctor --format json
pico-cli plugin doctor --tool codex --format json
pico-cli mcp doctor --format json
```

首次使用模拟器前，再检查模拟器依赖：

```powershell
pico-cli emulator doctor --format json
```

### 3. 常见环境问题

| 现象 | 处理 |
| --- | --- |
| `git`、`node`、`java` 或 `pico-cli` 找不到 | 重新打开 PowerShell，并检查对应程序是否加入 `PATH`。 |
| Java 版本不正确 | 确认 `JAVA_HOME` 指向 JDK 17，且 `%JAVA_HOME%\bin` 排在旧 Java 路径之前。 |
| Codex 中没有 PICO 能力 | 重启 Codex / 新建 task，再运行 `pico-cli plugin doctor --tool codex --format json`。 |
| 模拟器启动失败 | 运行 `pico-cli emulator doctor --format json`；必要时使用 `pico-cli emulator dump-logs --out ./sim-logs` 收集日志。 |

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

## 已知限制

- 模拟器或部分真实设备可能无法提供原始手势追踪数据；应用应继续允许手柄操作。
- Persistent Spatial Anchor 能否跨设备重启恢复取决于设备空间定位与锚点服务，必须逐台真实设备验收。
- ADB 截图可能无法捕获 Spatial Stage 合成层；截图为空不等于空间面板没有显示。
- 当前平面命中实现按需读取检测平面。后续拖拽功能应改为订阅平面更新并缓存三角形，避免拖动期间高频重建平面数据。
- 60fps 和内存低于 100MB 尚需在 Release 包和真实设备上进行性能验证。

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
