# PICO 空间固定标签应用规格基线

> 用途：用于快速启动“墙面便签、空间待办、设备旁提醒、固定位置说明卡”等 PICO 空间应用。
> 
> 本文以 `Wall Stickies` 的已验证实现为基础。复制到新项目时，替换应用名、包名、文案和领域数据即可；不要跳过“持久锚点恢复”与“真实头显验收”章节。

### 本规格的写入边界

本文只记录会影响产品语义、数据安全、空间行为、平台能力、生命周期、故障恢复或验收结果的事实。以下变化**需要**更新本文：

- 应用身份、业务字段、创建/查询/编辑/删除流程发生变化；
- Stage、锚点、面板归属、头显跟随、Billboard 或输入状态机发生变化；
- 数据隔离、迁移、清理、恢复策略或验收标准发生变化；
- 已复现的 PICO SDK / 模拟器问题及其工程规避方案发生变化。

仅颜色、圆角、图标、间距、字体微调、动画曲线等不改变上述语义的 UI 调整，**不要**写入本文，也不应触发本规格重写。若视觉变化改变临期/危险等状态含义、可读性下限、命中范围或性能门槛，则仍须更新对应约束。

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

### 3.1 应用身份与跨应用隔离（强制）

真实房间不是便签数据的共享命名空间。PICO 文档规定，应用只能加载“当前应用”创建的空间锚点；Room 数据也应位于 Android 应用私有目录。因此两个产品即使在同一个物理房间运行，也不应看到彼此的便签。

应用的隔离边界必须同时满足：

1. 每个独立产品使用唯一且稳定的 Gradle `applicationId`；`namespace`、源码包路径和安装包名同步改为该产品身份。
2. 每个产品使用自己的私有 Room 数据库名、表结构和迁移链，不读取参考项目的数据库、外部共享文件或演示数据。
3. 每条业务记录只与本应用 Room 中保存的 `anchorUuid` 匹配；`loadAnchor()` 返回但不在本应用记录中的锚点不得渲染成便签。
4. 新项目不得复制参考项目的预置记录、运行时数据库、锚点 UUID、备份文件或用户内容。
5. 锚点名称增加产品前缀仅用于日志辨识，不能替代 `applicationId + Room UUID 关联` 的隔离。

复制工程时如果沿用原 `applicationId`，PICO/Android 会按同一应用身份处理：签名相同会形成升级/替换，签名不同通常会安装失败，而不会成为可并存的新应用。此时看到旧数据库记录或旧锚点是应用身份复用造成的，不是“同一房间内容公用”。

#### 发现旧项目便签时的固定排查与修复

```text
已安装 APK 的 applicationId
    → 当前 Room 数据库名与记录来源
    → loadAnchor() 返回的应用锚点
    → Room.anchorUuid 与返回 UUID 的交集
    → 是否仍有参考项目的种子数据/默认文案
```

- 若 `applicationId` 与参考项目相同：先改为目标产品的唯一包名并同步源码/清单配置，再安装为独立应用。
- 若只是要清空同一产品的测试数据：优先在应用内逐个 `removeAnchor(anchorUuid)`，确认后再清 Room。不要假设卸载、覆盖安装或 `pm clear` 一定会删除 PICO 空间服务中的锚点。
- 若存在“SDK 有锚点、Room 无记录”的孤儿锚点：默认忽略且不显示；仅在明确的调试/重置操作中，经确认后批量清理本应用孤儿锚点。
- 若包名已不同但仍显示旧内容：检查是否复制了 Room 文件、外部共享存储、备份恢复、种子数据或旧业务 Composable；不要把问题归因于房间共享。

### 3.2 目标应用必须替换业务语义

本工程是空间便签实现参考，不是可直接换皮的数据模板。新产品必须先定义自己的字段、状态和动作，再复用锚点基础设施。例如“家居点位标记器”的业务记录应是：

```kotlin
data class HomeMarker(
    val id: String,
    val anchorUuid: String,
    val itemName: String,
    val storageLocation: String,
    val usageNote: String,
    val expiryDate: LocalDate?,
    val category: MarkerCategory,
)
```

该应用的一级功能是两个彼此独立的入口：

- **创建标记**：命中真实表面后填写物品名称、存放位置、使用备注、有效期和分类，成功创建锚点后保存。
- **查询高亮**：按物品、位置、备注或分类查询已有记录，选择结果后只高亮对应空间标记，并给出相对头显的方向和距离；查询不得隐式进入创建流程。

“家居点位标记器”变体还必须保留以下产品行为：

- 分类至少覆盖厨房、卧室、卫生间和工具；分类是筛选和理解标记的业务字段，不只是卡片颜色。
- 有效期按本地日期计算正常、临期、过期状态；临期/过期语义优先于普通分类强调，且不能只依赖颜色传达。
- 编辑内容不得改变原锚点；调整空间位置必须创建替换锚点，Room 成功切换到新 UUID 后才能删除旧锚点。
- 删除同时处理 Room 记录、PICO 锚点和空间实体，并保持幂等。
- 列表/查询结果点击后必须定位同一业务 `id` 对应的已恢复空间实体；锚点未恢复时显示不可定位状态，不得把标签临时放到用户眼前冒充定位成功。
- 创建、查询、编辑和重新锚定使用明确状态，不得因复用表单而把查询选择误判成新建，也不得用同一次捏合/扳机连续完成两个状态迁移。

不得继续沿用参考便签的待办项、便签正文、样式选择或示例文案，除非目标产品需求明确包含它们。领域内容变化需要更新本节；仅改变卡片颜色、字体或图标不需要更新。

下列 `SpatialStickyNote` 是 Wall Stickies 基线模型；目标产品应以自己的模型替换它，而不是让两套业务字段并存。无论采用哪种业务模型，每张标签都必须拥有独立的 `id` 与 `anchorUuid`，二者不得复用。

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

### 空间实体归属与跟随规则

- **持久便签/物品标签**：位置来自空间锚点，绝不能跟随头显平移；可每帧只更新朝向以形成 Billboard，但不得改变锚点世界位置。
- **管理控制台、创建表单、查询面板和短提示**：属于用户工具，应共同作为单一父实体的子节点；父实体使用 `AnchorComponent(AnchorTarget.createCameraTarget())`，通过 `positionOffset` 保持约 0.8–1.2m 舒适视距。面板需要稳定正面朝向用户时，在面板实体增加 `LookAtComponent.setViewerAsTarget()`，而不是手算旋转。禁止在每帧 `update` 中读取 HMD pose 并重写这些面板的世界变换。
- 两类实体不得使用同一个父节点或同一套 transform 更新逻辑。禁止为了让控制台跟随头显而把已锚定标签一起挂到 HMD 节点下。
- Camera Anchor 与面板父子关系只在 `SpatialView.initial` 建立一次；Compose 状态变化仅在 `update` 中改变内容或 `enabled`，不得重建锚点。退出页面时销毁父实体，避免重新进入后残留重复 UI。
- 应用恢复后，只有取得匹配锚点 transform 的标签才可出现；控制台可独立显示“已恢复 x/y”。

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

## 11. 项目中途接手与问题处理约束

中途继续开发时，不得重新套模板、重建项目或凭记忆覆盖现有实现。固定流程如下：

1. 读取目标项目的 `AGENTS.md`、当前规格、现有计划和最近运行证据，确认实际 `applicationId`、Stage 类型、数据库名和目标业务模型。
2. 检查工作区已有改动并保留用户修改；先定位现有入口、锚点仓库、Room 映射、空间实体和测试，再决定最小修改范围。
3. 对用户报告的问题先形成可复现链路：操作步骤、预期、实际、日志标签、Room 数量、SDK 锚点数量和可见实体数量。不能只看截图猜测。
4. 修复必须落在问题对应层：身份隔离问题修包名/数据边界，恢复问题修 UUID 关联，跟随问题修实体 transform 归属，业务内容错误修领域模型/数据源；不要用 UI 遮盖底层错误。
5. 一个已接受的实施计划必须连续完成全部条目及可用验证，不得在单步构建成功、局部界面完成或中间里程碑后结束。
6. 实现任务的最小验证闭环为：单元测试/静态检查 → Java 21 Debug 构建 → 安装目标 APK → 启动目标包 → 检查崩溃与业务日志 → 实际操作关键路径 → 截图或设备证据。
7. 只有全部验收完成，或下一步被可复现的外部/工具错误阻塞时才可结束；阻塞必须记录准确命令、完整错误和已尝试的安全替代方案。
8. 纯 UI 微调不进入本规格，也不得借此扩大成业务模型、容器或数据迁移改造；若测试暴露语义问题，再按本文件的写入边界补充。

### 中途修复完成判定

- 原问题可以稳定复现，并有修复前后证据；
- 新 APK 的包名、数据库和锚点集合与参考应用隔离；
- 创建、恢复、查询/选择、编辑、删除的相关路径没有回归；
- 后台/恢复后控制台仍跟随头显，持久标签仍保持锚定世界位置；
- 未恢复和孤儿锚点不会被显示为有效业务内容；
- 文档只沉淀可复用的产品/平台限制，没有记录一次性的颜色和排版修改。

## 12. 本项目参考位置

- 恢复与空间实体：`app/src/main/java/com/spatialapps/wallstickies/content/HomeStage.kt`
- PICO 锚点桥接：`app/src/main/java/com/spatialapps/wallstickies/data/repository/WorldAnchorRepository.kt`
- 领域用例：`app/src/main/java/com/spatialapps/wallstickies/domain/usecase/ManageStickyNotes.kt`
- Room 映射：`app/src/main/java/com/spatialapps/wallstickies/data/repository/RoomStickyNoteRepository.kt`
- PICO SDK 本地锚点参考：`F:/PICO/sdk/6.0/agent-vault/spatial/documentation/spatial-sdk_environmental-awareness-(mixed-reality)_spatial-anchor.md`
