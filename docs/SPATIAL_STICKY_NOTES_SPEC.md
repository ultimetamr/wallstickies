# PICO 空间固定标签应用规格基线

> 用途：用于快速启动“墙面便签、空间待办、设备旁提醒、固定位置说明卡”等 PICO 空间应用。
> 
> 本文以 `Wall Stickies` 的已验证实现为基础。复制到新项目时，替换应用名、包名、文案和领域数据即可；不要跳过“持久锚点恢复”与“真实头显验收”章节。

## 1. 产品定义

| 项目 | 基线要求 |
| --- | --- |
| 核心价值 | 将可编辑的信息卡固定到真实空间中的有意义位置，使用户再次回到该位置时可看到同一信息。 |
| 典型内容 | 短文本提醒、待办清单、说明、设备状态或位置相关任务。 |
| 主任务 | 在真实表面创建 → 编辑内容 → 退出/重进后恢复 → 选择并管理。 |
| 非目标 | 不以 2D 列表替代空间位置；不把未成功定位的旧便签错误地放到用户眼前。 |
| 容量目标 | 正常使用至少 10 张同时存在；PICO 文档标示单应用最多 1,024 个空间锚点。 |

## 2. 平台与容器决策

### 必须澄清的约束

PICO Persistent Spatial Anchor 只能在 **Full Space 的 Stage** 中使用。它不能只依靠 Shared Space 的普通 `WindowContainer` 实现。

推荐基线：

```text
DefaultStage / Mixed Stage (Full Space)
└── SpatialView
    ├── 每张便签：Planar AttachmentPanel
    ├── 编辑器：跟随头显的 Planar AttachmentPanel
    └── 状态提示：跟随头显的 Planar AttachmentPanel
```

因此，产品文档若写“Shared Space + 持久空间锚点”，应改写为：

> 应用使用 Mixed/Full Space Stage 建立和恢复持久锚点；便签内容以平面 SpatialUI 面板呈现。

### 视觉与舒适基线

- 使用 `PicoTheme` 与 SpatialUI；不要混用 Material/Material3。
- 便签为圆角平面卡片，正文最小 14sp、标题 16sp 粗体。
- 交互目标最小 56dp；关闭按钮保持在右上角且不随长内容下移。
- 编辑器和短提示距头显约 0.75m，随头显位置和旋转更新，始终面向用户。
- 永久便签使用锚点的世界坐标；编辑器不能覆盖或改变旧便签的锚点。

## 3. 领域模型与本地存储

每张标签必须拥有独立的 `id` 与 `anchorUuid`，二者都不得复用。

```kotlin
data class SpatialStickyNote(
    val id: String,                 // App UUID，Room 主键
    val anchorUuid: String,         // PICO WorldAnchor UUID
    val title: String,
    val body: String,
    val todos: List<TodoItem>,
    val style: StickyStyle,          // SOLID / FROSTED
    val color: StickyColor,          // 6 个预设色
    val fallbackPosition: StickyPosition,
    val createdAt: Long,
    val updatedAt: Long,
)
```

Room 最少保存：`id`、`anchorUuid`、文本、待办 JSON、样式、颜色、最后已知位置和时间戳。

### 创建事务规则

1. 取得真实墙面/桌面的射线命中点。
2. 用命中点位置和创建时的朝向调用 `WorldTrackingManager.createAnchor`。
3. 仅在 API 成功且返回 UUID 后写入 Room。
4. 写入成功后才显示便签实体。
5. 任一步失败都不得留下没有锚点的“永久便签”；提示用户重新对准表面。

### 删除规则

1. 从 Room 删除该 `id`。
2. 调用 `removeAnchor(anchorUuid)`。
3. 销毁对应空间实体和 AttachmentPanel 状态。

删除步骤需要幂等：锚点已被系统清除时，仍应完成本地删除。

## 4. 空间交互规格

| 操作 | 触发 | 前置条件 | 结果 |
| --- | --- | --- | --- |
| 新建 | 左手柄射线指向表面后按扳机；或手势捏合 | 命中已识别墙面/桌面；编辑器未打开 | 保存命中点为草稿位置，打开编辑器。 |
| 未命中 | 扳机/捏合未命中表面 | — | 展示约 1 秒提示，不创建空气中的便签。 |
| 编辑 | 射线点击/凝视后选择便签 | 已恢复且已显示 | 编辑器显示该便签内容；进入编辑时关闭锚点选择。 |
| 删除 | 卡片上的删除控制 | 已选中便签 | 删除 Room 数据、空间锚点和实体。 |
| 待办勾选 | 点击 Checkbox | 待办模式 | 每次勾选立即写 Room。 |
| 拖拽（可选） | 抓取/拖拽便签 | 明确的抓取手势，不与按钮点击冲突 | 创建替换锚点，更新 Room UUID 后删除旧锚点。 |

### 输入状态机

```text
Idle ──射线命中──> EditorOpen ──创建/取消──> WaitTriggerRelease ──松开扳机──> Idle
  │                       │
  └──未命中──> Hint(1s)───┘

后台/失焦：关闭 EditorOpen、隐藏 Hint、停止表面检测、等待扳机松开。
```

`WaitTriggerRelease` 是必要状态：它避免用户用同一次扳机关闭编辑器后，立即再次触发新建。

## 5. 持久锚点恢复规格（关键）

### 恢复原则

头显当前位置和朝向不是旧便签的位置来源。每张便签必须按自己的 `anchorUuid` 恢复世界变换：

```text
Room 全部便签
    ↓
WorldTrackingManager.loadAnchor()  // 不传 UUID，加载当前应用全部锚点
    ↓
返回的 anchorUUID 与 Room.anchorUuid 匹配
    ↓
Transform(position, rotation) → 对应 AttachmentPanel Entity
```

使用无参数的 `loadAnchor()`，不要在启动阶段向模拟器一次传入大量 UUID。PICO SDK 规定空数组/无参数为“加载当前应用的全部锚点”。

### 实现要求

- Stage 激活后延迟约 1 秒再开始恢复，避开 Full Space 激活竞争。
- 恢复结果必须累积，不能让后一次重试覆盖前一次已恢复的便签。
- 订阅 `WorldTrackingManager.subscribeAnchorUpdate`：
  - `LOADED`：将晚到锚点显示出来。
  - `UPDATED`：用最新 transform 更新对应便签，处理空间坐标重新校准。
  - `REMOVED`：标记为“锚点不可用”，不要静默移动到用户眼前。
- 用 `DisposableEffect` 保存订阅，并在 Stage 销毁时 `cancel()`。
- 每次回调先验证 UUID 仍属于 Room 中的便签，避免已删除便签复活。

### 未恢复锚点的产品策略

默认策略：**不显示在当前空间中**，记录状态为 `anchor_not_returned`。可以在管理页列出“等待定位”的项目，提供：

- 返回原来的房间/位置后重试；
- 重新锚定到当前表面；
- 删除旧便签。

`fallbackPosition` 仅可用于受控的调试或明确标注的临时恢复，不可冒充真实空间定位结果。

## 6. 生命周期与资源清理

后台或主界面切换并不一定销毁 Compose `HomeStage`。因此不能只依赖 `onDispose`。

| 生命周期事件 | 必须动作 |
| --- | --- |
| `ON_PAUSE` / `ON_STOP` | 关闭编辑器、隐藏提示、停止 PlaneTracking、禁用临时实体、增加选择世代号、要求扳机松开。 |
| `ON_RESUME` | 恢复输入 Provider；保持“等待扳机松开”，不自动打开编辑器。 |
| Stage `onDispose` | 取消锚点订阅、停止追踪 Provider、销毁所有 AttachmentPanel Entity、清空实体表。 |

异步射线检测完成后必须再次检查：应用仍在前台、世代号未改变、编辑器未打开且扳机未被屏蔽。否则丢弃结果。

## 7. 可观测性与故障定位

必须保留如下日志标签：

| 标签 | 记录内容 |
| --- | --- |
| `WallStickiesRestore` | Room 便签数、每轮返回锚点数、每张便签 `restored=true/false`、最终 `restored=x/y`。 |
| `WallStickiesAnchor` | anchor 创建、加载、删除和 `LOADED/UPDATED/REMOVED` 事件。 |
| `WallStickiesRender` | 每张便签 `ATTACHED`、`SKIPPED_NO_RECOVERED_ANCHOR`、`ATTACHMENT_ENTITY_UNAVAILABLE`。 |
| `WallStickiesInput` | 射线命中、未命中、控制器状态和被屏蔽的输入。 |

诊断顺序固定为：

```text
Room 记录数 → SDK 返回 anchor 数 → 每个 UUID 是否匹配 → Entity 是否 ATTACHED → 用户可见性
```

不要先假设是 UI 覆盖或数据库覆写。

## 8. 模拟器与真实设备验收

PICO 模拟器适合验证编译、Stage 启动、日志链路和部分表面数据；不能作为持久空间锚点最终验收环境。

已观察到的模拟器现象：

- Room 可保留大量便签记录。
- 旧的 UUID 定向加载在 94 条数据中仅返回 1 条。
- 改为全量加载后同一环境可返回并显示 15/94 条。
- 其余锚点底层可能报告 `anchorTotal: 0` / `FindAnchorByUuid`；这属于模拟器空间服务限制或历史测试空间数据不完整。

真实头显验收脚本：

1. 在同一真实房间、不同物理位置创建 10 张便签。
2. 逐张编辑和切换样式；至少 3 张使用待办并勾选条目。
3. 退出到主界面后重新进入应用。
4. 强制停止进程后重启应用。
5. 重启头显，在同一房间充分环顾并回到各便签附近。
6. 检查日志：`roomNotes=10`、最终 `restored=10/10`、10 条 `display=ATTACHED`。
7. 测量位置偏差：目标小于 5cm；如不能定位，记录为平台定位失败而非应用恢复成功。

## 9. 性能与质量门槛

- 渲染只在状态变化或锚点 transform 更新时写入，不在每帧打印日志。
- 用稳定 `note.id` 作为 AttachmentPanel ID；禁止列表索引作为 ID。
- 透明/磨砂材质和面板数量受控；目标至少 10 张时保持流畅。
- 60fps、内存低于 100MB 只能以真实头显的 PICO 性能分析结果声明，模拟器不构成证明。
- 任意“恢复成功”结论都必须同时满足：Room 记录存在、SDK 返回匹配 UUID、实体 `ATTACHED`。

## 10. 新项目快速引用清单

- [ ] 使用 Full Space Stage，而非仅 Shared Space WindowContainer。
- [ ] 每张标签保存 `id + anchorUuid + 内容 + fallbackPosition`。
- [ ] 创建前必须命中已识别真实表面。
- [ ] 创建 API 成功后才写 Room。
- [ ] 启动时无参数 `loadAnchor()`，按 UUID 匹配 Room。
- [ ] 订阅锚点 `LOADED/UPDATED`，并在销毁时取消订阅。
- [ ] 未恢复的标签不显示在眼前，提供重新锚定或删除入口。
- [ ] 后台/编辑器打开期间关闭锚点选择，等待触发器释放。
- [ ] 保留四类日志标签并按“Room → Anchor → Entity”排查。
- [ ] 模拟器只做开发验证；持久化、多便签和漂移在真实头显验收。

## 11. 本项目参考位置

- 恢复与空间实体：`app/src/main/java/com/spatialapps/wallstickies/content/HomeStage.kt`
- PICO 锚点桥接：`app/src/main/java/com/spatialapps/wallstickies/data/repository/WorldAnchorRepository.kt`
- 领域用例：`app/src/main/java/com/spatialapps/wallstickies/domain/usecase/ManageStickyNotes.kt`
- Room 映射：`app/src/main/java/com/spatialapps/wallstickies/data/repository/RoomStickyNoteRepository.kt`
- PICO SDK 本地锚点参考：`F:/PICO/sdk/6.0/agent-vault/spatial/documentation/spatial-sdk_environmental-awareness-(mixed-reality)_spatial-anchor.md`
