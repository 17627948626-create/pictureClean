# Release 上架门禁审查清单

本文档以“能否稳定上架并通过审核”为唯一评判标准，统一记录当前项目的发布阻断项、修复方案、验收标准和注意事项。

## P0 口径说明

P0 不只包含“Google Play 一定拒审”的材料问题，也包含以下会导致不能放心上架的问题：

- 核心路径不可用。
- 用户照片存在误删、错删、假删除风险。
- 权限流程导致白屏、卡死、无法恢复。
- Release 包没有经过真实验证。
- 边界场景会崩溃或状态错乱。

因此本文档中的 P0 是“上架门禁 P0”，所有 P0 必须关闭后才建议提交商店审核。

## 当前优先级总览

| 优先级 | 数量 | 定义 | 发布建议 |
| --- | ---: | --- | --- |
| P0 | 8 | 阻止上架、核心功能不可用、数据安全或审核必需项 | 必须全部关闭 |
| P1 | 3 | 上架前强烈建议修复，否则发布后高概率出问题 | 建议随 P0 同步修 |
| P2 | 4 | 质量、体验、测试和可维护性问题 | 上架前尽量修 |
| P3 | 2 | 后续迭代优化 | 可排期 |

## 当前发布判断

当前版本不能只按“P0-1 代码修复 + P0-2 文档补齐”判断可上架。必须逐项确认下列 8 个 P0：

1. 照片加载失败不能永久 Loading。
2. 隐私政策和权限说明满足 Google Play 要求。
3. 删除 / 恢复状态机可靠。
4. 快速连续手势不会导致状态错乱。
5. 权限请求、拒绝、部分授权、恢复授权流程完整。
6. 空相册、单张照片、最后一张删除等边界场景稳定。
7. Release 包完成构建和真机回归。
8. 删除行为真实、安全、可解释。

---

# P0 — 上架门禁

## P0-1：照片加载失败不能永久 Loading

**位置**：`PhotoViewModel.kt`、照片加载 UI。

**风险**：

`loadPhotos()` 是首屏核心路径。权限异常、MediaStore 查询异常、IO 异常或设备厂商兼容问题，都可能导致照片加载失败。如果失败时 UI 永远停在 Loading，用户会认为 App 无法使用。

**当前目标**：

- `repository.loadPhotos()` 失败时不能崩溃。
- 失败时不能永久 Loading。
- 失败时必须进入明确错误态。
- 用户必须能理解错误并重新尝试。

**修复方案**：

- 在 `viewModelScope.launch` 内对照片加载逻辑加 `try/catch`。
- 新增或保留 `PhotoListState.LoadFailed`。
- `PhotoUiState` 保留 `errorMessage` 或等价错误信息字段。
- 捕获异常后清理加载中状态，进入 `LoadFailed`。
- UI 根据 `LoadFailed` 展示失败说明和重试按钮。
- 失败时记录日志，至少包含异常类型和 message。
- 重试入口重新调用 `loadPhotos()`。

**验收标准**：

- `repository.loadPhotos()` 抛异常时，`screenState == LoadFailed`。
- UI 显示失败提示和重试入口。
- 不会崩溃。
- 不会永久 Loading。
- 单测覆盖 repository 抛异常路径。
- 授权恢复后重试可以重新进入正常加载流程。

**注意事项**：

- 不要把异常伪装成空相册。空相册和读取失败是两种不同状态。
- 不要只在 Repository 内 catch 后返回空列表，否则 UI 无法区分真实空相册和读取失败。
- 不要吞异常不打日志。

---

## P0-2：隐私政策和权限说明满足 Google Play 要求

**位置**：`docs/PRIVACY_POLICY.md`、Google Play Console、商店详情页、权限用途说明。

**风险**：

应用访问用户照片，属于敏感数据访问场景。Google Play 要求填写隐私政策 URL，并要求权限用途说明与实际行为一致。缺失隐私政策 URL 或说明不一致会导致拒审。

**当前目标**：

- 仓库内有真实隐私政策材料。
- 隐私政策已发布到公网稳定 URL。
- Play Console 已填写同一个 URL。
- 权限用途说明和应用实际行为一致。

**修复方案**：

- 保留并维护 `docs/PRIVACY_POLICY.md`。
- 将隐私政策发布到公网 URL，例如 GitHub Pages、官网、公开 Notion 页面或其他稳定地址。
- 在 Google Play Console 的隐私政策字段填写该 URL。
- 在 Data safety / 数据安全表单中如实声明：
  - 是否收集照片。
  - 是否上传照片。
  - 是否共享数据。
  - 是否使用第三方 SDK。
  - 是否有广告、分析、云端处理。
- App 内权限说明必须明确照片权限用途。

**验收标准**：

- 隐私政策 URL 在无登录状态下可打开。
- URL 内容与 `docs/PRIVACY_POLICY.md` 一致。
- Play Console 已保存该 URL。
- 权限说明没有夸大、遗漏或与实际行为冲突。
- 如果未来加入统计、广告或云服务，隐私政策同步更新。

**注意事项**：

- 仓库内有 Markdown 不等于 Play Console P0 关闭；必须有公网 URL。
- 不要写“不会收集任何数据”这类绝对表述，除非已经确认所有 SDK 和系统表单都一致。
- 如果保留 `current_index`、待删除队列等本地状态，隐私政策应说明本地存储。

---

## P0-3：删除 / 恢复状态机可靠

**位置**：`PhotoViewModel.kt`、删除队列、恢复逻辑、确认删除页。

**风险**：

这是照片清理 App 的核心业务。如果删除、恢复、当前 index、可见列表、待删除队列之间状态不一致，可能出现错删、假恢复、跳图、空白页或队列内容错误。

**当前目标**：

删除和恢复必须由 ViewModel 状态机决定，动画只能负责视觉表现，不能决定业务状态。

**修复方案**：

- 明确唯一数据源：
  - `allPhotos`：当前 App 认为存在的照片全集。
  - `deleteQueue`：待系统删除确认的照片。
  - `deleteQueueIds`：由 `deleteQueue` 派生。
  - `visiblePhotos`：`allPhotos - deleteQueueIds` 派生。
  - `currentIndex`：始终 clamp 到 `visiblePhotos` 合法范围。
- 上划接受后立即调用 `queueCurrentPhotoForDeletion()`。
- 下划恢复只允许恢复最近一次删除，并且必须校验恢复位置。
- 删除完成后只移除 `deleteQueueIds` 对应照片。
- 删除取消或失败时不能清空队列。
- `recomputeDerivedState()` 必须作为状态归一化入口。
- 增加状态机 contract tests：
  - 删除当前照片。
  - 删除最后一张。
  - 连续删除多张。
  - 恢复最近删除。
  - 非恢复位置下滑不能恢复。
  - 确认删除后清队列并移除照片。

**验收标准**：

- 任意时刻 `visiblePhotos == allPhotos.filter { it.id !in deleteQueueIds }`。
- `deleteQueueIds == deleteQueue.map { it.id }.toSet()`。
- `currentPhoto == visiblePhotos.getOrNull(currentIndex)`。
- 删除最后一张不会 index 越界。
- 恢复后照片重新出现在可见列表。
- 删除取消后队列仍保留。
- 确认删除后队列清空，已删照片不再显示。

**注意事项**：

- 不要在 UI 层直接修改列表。
- 不要让动画结束回调决定是否真正入队。
- 不要用照片位置作为永久身份，必须用稳定 `id` 或 `uri`。
- 恢复逻辑不要恢复“当前看到的上一张”，只能恢复最近删除记录。

---

## P0-4：快速连续手势不会导致状态错乱

**位置**：`PhotoSwipeScreen.kt`、`SwipeStage`、手势锁、动画状态。

**风险**：

真机用户会快速连续滑动。若动画未完成时继续接收手势，可能出现重复入队、恢复错照片、跳过照片、动画宿主提前释放、当前照片与 ViewModel 状态不一致。

**当前目标**：

手势、动画和业务状态必须串行化。一次业务手势未完成前，不允许第二次破坏状态。

**修复方案**：

- 设置明确手势锁，例如 `isAnimating` / `gestureLocked`。
- 一次 accepted swipe 开始后立即锁定输入。
- 对 X/Y 两个方向的动画都完成后再释放锁。
- 上划删除业务入队应立即执行；旧照片可由动画快照继续飞出。
- 下划恢复必须先由 ViewModel 返回是否恢复成功，再决定是否播放恢复动画。
- 对未过阈值的轻滑，只播放回弹，不触发业务状态变化。
- 在动画期间忽略新的 drag / fling / tap。

**验收标准**：

- 快速连续上划不会重复加入同一张照片。
- 快速上划后立即下划，不会恢复错误照片。
- 快速左右滑不会 index 越界。
- 未过阈值的滑动只回弹，不改变业务状态。
- 动画未完成时不会出现空白页或下一张提前错位。
- 真机连续快速操作 30 次无崩溃、无跳图、无队列错乱。

**注意事项**：

- Compose 动画状态不要与业务状态互相覆盖。
- 不要只等待一个方向动画结束就释放锁；二维动画必须全部完成。
- 不要在 `LaunchedEffect` 中使用容易被 recomposition 重启的 key 破坏动画生命周期。
- 需要保留旧照片快照作为飞出动画宿主，尤其是最后一张删除场景。

---

## P0-5：权限请求、拒绝、部分授权、恢复授权流程完整

**位置**：权限请求 UI、`AndroidManifest.xml`、Accompanist Permissions、照片加载入口。

**风险**：

相册类 App 没有权限就无法工作。权限流程不完整会导致首次启动不请求权限、拒绝后白屏、部分照片授权下状态异常、用户从系统设置恢复授权后无法刷新。

**当前目标**：

所有权限状态都有明确 UI 和恢复路径。

**修复方案**：

- 首次启动时请求合适的照片权限。
- Android 13+ 使用 `READ_MEDIA_IMAGES`；旧系统按实际 target 和兼容策略处理。
- 支持 Android 14+ 部分照片访问场景。
- 未授权时显示权限说明和授权按钮。
- 用户拒绝后显示解释和重试入口。
- 用户选择“不再询问”或系统不再弹窗时，引导去系统设置。
- 用户从系统设置返回后重新检查权限并调用 `loadPhotos()`。
- 权限被撤销后不能继续假装有照片访问能力。

**验收标准**：

- Fresh install 首次启动能进入权限请求流程。
- 拒绝权限后 UI 不白屏、不 Loading。
- 再次点击授权可以重新请求或引导设置。
- 部分照片授权时只展示授权照片，不崩溃。
- 从设置恢复权限后可以刷新照片。
- 撤销权限后重新进入 App 不崩溃。

**注意事项**：

- 权限文案不能含糊，应明确“用于展示和整理用户授权的照片”。
- 不要把权限拒绝和空相册混为一谈。
- 不要只在 Activity 启动时检查一次权限；前后台切换后也要考虑权限变化。
- Android 版本差异必须真机或模拟器覆盖。

---

## P0-6：空相册、单张照片、最后一张删除等边界场景稳定

**位置**：`PhotoViewModel.kt`、主界面、删除动画、空状态 UI。

**风险**：

边界场景是审核和用户都容易触发的路径。相册为空、只有一张照片、删除最后一张、全部加入待删除队列时，如果处理不好，会导致崩溃、空白页、动画中断或状态不一致。

**当前目标**：

所有边界场景都有明确状态和 UI，不崩溃，不越界。

**修复方案**：

- 保留状态区分：
  - `Loading`
  - `LoadFailed`
  - `EmptyLibrary`
  - `AllQueuedForDelete`
  - `Reviewable`
- `currentIndex` 每次派生状态后都 clamp。
- `currentPhoto` 始终使用 `getOrNull()`。
- 空相册展示空状态，不进入 swipe stage。
- 全部加入删除队列时展示确认/已全选待删除状态。
- 删除最后一张时，先保留动画快照完成飞出，再进入空状态或全部待删状态。
- 单张照片左右滑应有边界反馈，不改变 index。

**验收标准**：

- 空相册启动不崩溃，显示空状态。
- 单张照片左/右滑不崩溃、不跳空白。
- 单张照片上划后进入待删除/确认状态。
- 最后一张删除动画完整播放。
- 全部加入删除队列后不会访问 `visiblePhotos[0]`。
- 删除确认后如果相册为空，进入 `EmptyLibrary`。

**注意事项**：

- 不要在 UI 中假设 `visiblePhotos` 一定非空。
- 不要在动画未完成前销毁最后一张照片的视觉宿主。
- `AllQueuedForDelete` 和 `EmptyLibrary` 含义不同：前者照片还没被系统确认删除，后者照片库确实为空或 App 认为已删除。

---

## P0-7：Release 包完成构建和真机回归

**位置**：Gradle、GitHub Actions、签名配置、Release APK/AAB、真机测试记录。

**风险**：

Debug 包可用不代表 Release 可上架。混淆、资源压缩、签名、权限声明、targetSdk、构建产物、系统删除弹窗都可能在 Release 下暴露问题。

**当前目标**：

上架前必须验证 Release 构建产物，而不是只验证 Debug。

**修复方案**：

- 本地或 GitHub Actions 构建 Release APK/AAB。
- 配置正式签名。
- 检查 `versionCode`、`versionName`、`applicationId`、`targetSdk`、`minSdk`。
- 执行：
  - `./gradlew :app:testDebugUnitTest --no-daemon`
  - `./gradlew :app:lintDebug --no-daemon`
  - `./gradlew :app:assembleRelease --no-daemon` 或对应 AAB 构建命令。
- 在真机安装 Release 包回归核心路径。
- 清理 debug 日志、测试入口、测试数据、未使用权限。
- 保存一份 release 回归记录。

**验收标准**：

- Release APK/AAB 构建成功。
- Release 包可安装、可启动。
- 首次权限流程正常。
- 照片加载正常。
- 左右滑、上划删除、下划恢复正常。
- 删除确认页正常。
- 系统删除弹窗正常。
- 删除完成后 App 状态正确。
- Play Console 预检查没有阻断项。

**注意事项**：

- 不要用 Debug 包结论替代 Release 包结论。
- 不要在 release 中依赖 debug-only 权限、日志或 mock 数据。
- 如果启用混淆，需要确认 Compose、Coil、权限库、MediaStore 路径没有被破坏。
- GitHub Actions 产物必须与准备上架的包一致。

---

## P0-8：删除行为真实、安全、可解释

**位置**：`deleteDirectly()`、`createDeleteRequest()`、删除确认页、删除完成回调。

**风险**：

应用处理的是用户真实照片。删除行为如果不清晰，会造成严重信任问题：看起来删除但实际没删、实际删除但 UI 没同步、删除失败却清空队列、Android 10 静默失败、用户不知道删除后果。

**当前目标**：

删除必须真实、安全、可解释。失败不能假成功，成功不能不同步。

**修复方案**：

- API 30+ 使用 `MediaStore.createDeleteRequest()`，由系统弹窗确认。
- API 29 单独处理 `RecoverableSecurityException`，拿到 recoverable intent 后交给 UI 发起授权。
- API 28 及以下如继续直接删除，必须明确成功/失败结果。
- 删除结果不要只用 Boolean 表达，建议引入结果对象：

```kotlin
sealed class DeleteResult {
    data object Success : DeleteResult()
    data class RequiresUserAction(val intentSender: IntentSender) : DeleteResult()
    data class PartialFailure(
        val deleted: List<Photo>,
        val failed: List<Photo>,
        val reason: Throwable?
    ) : DeleteResult()
    data class Failure(val reason: Throwable?) : DeleteResult()
}
```

- 删除成功后调用 `onDeleteCompleted()`。
- 删除取消或失败时保留 `deleteQueue`。
- UI 必须说明：删除会通过系统流程处理，可能进入系统回收站或由系统决定最终删除行为。
- 日志记录失败 URI、API level、异常类型。

**验收标准**：

- API 30+ 系统删除弹窗出现，确认后状态同步。
- API 29 遇到 `RecoverableSecurityException` 时不会静默失败。
- 删除失败时队列不清空。
- 删除取消时队列不清空。
- 删除成功后照片从 `allPhotos` 和 `visiblePhotos` 中消失。
- 批量删除时调用方能知道全部成功、部分成功或失败。
- 用户能在确认页理解将要删除哪些照片。

**注意事项**：

- 不要 catch `Exception` 后只返回 false。
- 不要删除失败后调用 `onDeleteCompleted()`。
- 不要把“加入待删除队列”写成“已经删除”。
- 不要在 UI 上先隐藏照片后完全丢失失败恢复路径；加入队列和系统删除完成是两个阶段。

---

# P1 — 上架前强烈建议修复

## P1-1：Android 10 删除路径专项兼容

该问题已被 P0-8 覆盖为上架门禁的一部分。若单独跟踪，重点是 API 29 + targetSdk 35 下 `RecoverableSecurityException` 的处理。

## P1-2：批量删除结果语义明确化

该问题已被 P0-8 覆盖为上架门禁的一部分。建议用结构化 `DeleteResult` 替代 Boolean。

## P1-3：权限和隐私文案资源化

权限说明、错误提示、删除确认文案应进入 `strings.xml`，避免后续修改遗漏。

---

# P2 — 质量问题，建议修

## P2-1：`allowBackup="true"` 存在轻微隐私风险

**方案**：将 `AndroidManifest.xml` 中 `android:allowBackup` 改为 `false`，或增加明确 backup rules，排除本地浏览状态和待删除状态。

**注意事项**：照片类应用尽量减少可备份的用户行为状态。

## P2-2：字符串硬编码

**方案**：用户可见字符串迁移到 `res/values/strings.xml`，Compose 中使用 `stringResource()`。

**注意事项**：权限、隐私、删除确认文案属于审核敏感文案，应优先资源化。

## P2-3：删除路径测试覆盖不足

**方案**：补 API 29、API 30+、删除失败、删除取消、部分成功测试。

**注意事项**：MediaStore 真实删除很难单测，至少要做状态机 contract test 和 API 分支测试。

## P2-4：缩略图 / 边界手感优化

**方案**：最后一张左划或第一张右划时加入阻尼和回弹。

**注意事项**：体验优化不能破坏 P0 状态机，不允许用动画状态替代 ViewModel 状态。

---

# P3 — 后续迭代

## P3-1：Coil 缓存和解码策略

**方案**：配置 memory cache / disk cache，并按显示尺寸请求图片，避免大图直接进内存。

## P3-2：测试文件命名一致性

**方案**：统一测试文件名和类名，例如 `DeleteQueueContractTest.kt`。

---

# 建议修复顺序

## 第一批：上架门禁

1. P0-1 加载失败兜底。
2. P0-2 隐私政策公网 URL 和 Play Console 填写。
3. P0-3 删除 / 恢复状态机。
4. P0-4 快速连续手势锁。
5. P0-5 权限完整流程。
6. P0-6 边界场景。
7. P0-8 删除行为安全。
8. P0-7 Release 包构建与真机回归。

## 第二批：发布前 hardening

1. 删除结果对象化。
2. API 29 删除授权路径专项测试。
3. 权限和删除文案资源化。
4. `allowBackup` 关闭或配置 backup rules。

## 第三批：体验和维护优化

1. 边界阻尼。
2. Coil 缓存策略。
3. 测试命名整理。

---

# P0 关闭判定规则

每个 P0 必须同时满足：

1. 代码或材料已完成。
2. 有明确验收记录。
3. 有测试或真机验证覆盖。
4. 不依赖“应该可以”的推断。
5. 不把 Debug 包验证等同于 Release 包验证。

只要任意一个 P0 没有满足上述规则，就不能宣称“所有 P0 已关闭”。
