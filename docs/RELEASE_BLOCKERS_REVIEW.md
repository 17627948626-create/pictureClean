# Release 上架门禁审查清单

本文档是项目唯一的 P0 上架门禁来源。README 只保留摘要和链接，不再维护另一套 P0 判断口径。

本文档记录所有已知真问题的优先级、当前状态、处理方案和验收标准。只有真问题才落盘；重复问题必须合并；动画专项细节放在 `docs/SWIPE_ANIMATION_V2.md`，隐私政策正文放在 `docs/PRIVACY_POLICY.md`。

## 状态定义

| 状态 | 含义 |
| --- | --- |
| 未关闭 | 代码、材料、验证任一关键部分缺失 |
| 已修复待验证 | 代码或材料已完成，但缺少测试、真机或 Release 验证 |
| 已关闭 | 代码或材料完成，并已有测试、真机或发布后台验证记录 |

## 优先级总览

| 优先级 | 数量 | 定义 | 发布建议 |
| --- | ---: | --- | --- |
| P0 | 8 | 阻止上架、核心功能不可用、照片数据安全或审核必需项 | 必须全部关闭 |
| P1 | 1 | 上架前强烈建议修复，否则发布后高概率增加维护成本 | 已随本轮修复 |
| P2 | 5 | 质量、体验、测试和可维护性问题 | 已随本轮修复 |
| P3 | 2 | 后续迭代优化 | 已随本轮修复 |

## 当前判断

项目开发 P0 已关闭。

当前 P0 状态：

| 分类 | 条目 | 下一步 |
| --- | --- | --- |
| 已关闭 P0 | P0-1、P0-2、P0-3、P0-4、P0-5、P0-6、P0-7、P0-8 | 不继续作为开发阻塞项处理 |

说明：P0-3、P0-4、P0-5、P0-6、P0-7 在状态机、权限、动画、边界和删除结果上有交叉，执行验收时可以合并跑同一套真机回归，避免重复劳动。

---

# P0 — 上架门禁

## P0-1：照片加载失败不能永久 Loading

**当前状态**：已关闭

**位置**：`PhotoViewModel.kt`、照片加载 UI、`PhotoViewModelTest.kt`。

**问题**：`loadPhotos()` 是首屏核心路径。权限、MediaStore、IO 或厂商兼容异常都可能导致读取失败；失败时不能永久 Loading，也不能伪装成空相册。

**已完成**：`loadPhotos()` 捕获异常；失败时进入 `LoadFailed`；保留 `errorMessage`；UI 显示失败提示和重试入口；记录日志；单测覆盖 repository 抛异常。

**验证记录**：ViewModel 单测覆盖 repository 抛异常；Release 真机回归已确认 OK。

**验收**：抛异常时不崩溃、不 Loading；`screenState == LoadFailed`；错误态可重试；授权恢复后可重新加载。

---

## P0-2：隐私政策和权限说明满足 Google Play 要求

**当前状态**：已关闭

**位置**：`docs/PRIVACY_POLICY.md`、权限说明文案。

**问题**：应用访问用户照片，缺少公网隐私政策 URL 或权限用途说明不一致会导致拒审。

**已完成**：仓库内已维护 `docs/PRIVACY_POLICY.md`；隐私政策已发布到公网 URL：<https://17627948626-create.github.io/pictureClean/PRIVACY_POLICY>；2026-05-05 通过 HTTP 200 确认可无登录访问。

**方案**：发布隐私政策到公网 URL；权限说明与实际行为一致。

**验收**：无登录可访问隐私政策 URL；内容与仓库文档一致；权限说明与实际行为一致。

---

## P0-3：删除 / 恢复状态机可靠

**当前状态**：已关闭

**位置**：`PhotoViewModel.kt`、删除队列、恢复逻辑、确认删除页。

**问题**：删除、恢复、当前 index、可见列表、待删除队列若不一致，会造成错删、假恢复、跳图、空白页或队列错误。

**已完成**：ViewModel 是唯一业务状态源；`deleteQueueIds` 由 `deleteQueue` 派生；`visiblePhotos = allPhotos - deleteQueueIds`；`currentIndex` 始终 clamp；上划接受后立即入队；下划只恢复最近删除且校验位置；删除取消或失败不清队列；删除完成才移除照片；已有 ViewModel 单测覆盖主要状态机。

**验证记录**：ViewModel 单测覆盖主要状态机；Release 真机回归已确认 OK。

**验收**：派生状态恒一致；删除最后一张不越界；恢复后照片回到可见列表；删除取消保留队列；确认删除后队列清空且已删照片不再显示。

**交叉说明**：本项与 P0-4、P0-6、P0-7 共用部分验收场景。

---

## P0-4：快速连续手势不会导致状态错乱

**当前状态**：已关闭

**位置**：`PhotoSwipeScreen.kt`、`SwipeStage`、手势锁、动画状态。

**问题**：动画未完成时继续接收手势，可能重复入队、恢复错照片、跳图、提前释放动画宿主或造成 UI 与 ViewModel 不一致。

**已完成**：accepted swipe 后立即锁输入；X/Y 回弹动画都完成后释放锁；业务状态先更新，动画只展示快照；未过阈值只回弹，不触发业务；动画期间忽略新手势；删除最后一张时保活动画宿主。

**验证记录**：Release 真机回归已确认 OK。

**验收**：快速连续上划不重复入队；快速上划后下划不误恢复；快速左右滑不越界；连续操作 30 次无崩溃、跳图、队列错乱。

**交叉说明**：本项与 P0-3、P0-6 共用真机回归，不需要重复设计三套流程。

---

## P0-5：权限请求、拒绝、部分授权、恢复授权流程完整

**当前状态**：已关闭

**位置**：权限请求 UI、`AndroidManifest.xml`、Accompanist Permissions、照片加载入口。

**问题**：相册类 App 没权限就无法工作。首次不请求、拒绝后白屏、部分照片授权异常、从设置恢复授权后不刷新，都会导致核心路径不可用。

**已完成**：Manifest 声明 Android 10-12、Android 13+、Android 14+ 部分授权权限；首次启动会请求合适照片权限；拒绝后展示解释和重试入口；不再弹授权框时提供“打开系统设置”；授权成功或从设置恢复授权后，会通过权限状态变化重新触发 `loadPhotos()`；部分授权场景会显示提示 banner。

**验证记录**：Release 真机回归已确认 OK。

**验收**：Fresh install 权限流程正常；拒绝后不白屏、不 Loading；可重试或进设置；部分授权只展示授权照片；恢复授权后可刷新；撤销权限后重进不崩溃。

---

## P0-6：空相册、单张照片、最后一张删除等边界场景稳定

**当前状态**：已关闭

**位置**：`PhotoViewModel.kt`、主界面、删除动画、空状态 UI。

**问题**：空相册、单张照片、删除最后一张、全部加入待删除队列是高频边界场景，处理不好会崩溃、空白、动画中断或状态不一致。

**已完成**：明确 `Loading / LoadFailed / EmptyLibrary / AllQueuedForDelete / Reviewable`；`currentPhoto` 用 `getOrNull()`；空相册不进入 swipe stage；全部待删进入确认状态；最后一张删除保留动画快照播完再切状态；已有部分单测覆盖空相册、全部入队、删除完成后空相册。

**验证记录**：ViewModel 单测覆盖空相册、全部入队、删除完成后空相册；Release 真机回归已确认 OK。

**验收**：空相册不崩溃；单张左右滑不跳空白；单张上划进入待删/确认；最后一张飞出完整；全部待删后不访问空列表；确认删除后相册为空进入 `EmptyLibrary`。

**交叉说明**：本项与 P0-3、P0-4 共用状态机和动画回归场景。

---

## P0-7：删除行为真实、安全、可解释

**当前状态**：已关闭

**位置**：`PhotoViewModel.kt`、`DeleteConfirmScreen.kt`、删除确认页、删除完成回调。

**问题**：用户真实照片不能假删除、错删、失败后清队列、Android 10 静默失败，用户也必须知道删除后果。

**已完成**：删除入口已改为 `DeleteResult` 结构化结果；API 30+ 使用 `MediaStore.createDeleteRequest()`；API 29 单独处理 `RecoverableSecurityException` 并交给 UI 发起授权；API 28 及以下直接删除并返回成功、部分失败或失败；取消或失败保留队列；部分成功会只移除已成功删除的照片；失败 URI、API level、异常类型会写日志；确认页会根据空队列、取消、请求失败、删除失败、部分失败分别提示。

**验证记录**：Release 真机回归已确认 OK。API 29 多张照片可能触发多次授权，作为后续兼容性观察项，不再作为开发 P0 阻塞。

**验收**：API 30+ 系统弹窗正常；API 29 不静默失败；失败/取消不清队列；成功后照片从 `allPhotos` 和 `visiblePhotos` 消失；批量删除能区分成功、部分成功、失败；确认页说明删除内容和后果。

---

## P0-8：Release 包完成构建和真机回归

**当前状态**：已关闭

**位置**：Gradle、GitHub Actions、签名配置、Release APK/AAB、真机测试记录。

**问题**：Debug 包可用不等于 Release 可上架。混淆、签名、资源压缩、权限声明、targetSdk、删除弹窗都可能在 Release 暴露问题。

**已完成**：GitHub Actions Build APK #87 已成功；生成并上传 `YiHua-debug-87` 和 `YiHua-release-87`；GitHub Release `dev-87` 已创建；用户已确认真机 Release 验证 OK。
**验证记录**：2026-05-14：GitHub Actions Build APK #113 成功；unit tests、Android Lint、signed release APK、signed release AAB、artifact upload、GitHub Release 均通过。AAB artifact 为 Swiply-release-113-aab。

**方案**：构建 Release APK/AAB；配置正式签名；检查版本号、包名、SDK；运行测试和 lint；真机安装 Release 包回归权限、加载、左右滑、上划删除、下划恢复、确认删除、系统删除弹窗、删除后状态；保存验收记录。

**验收**：Release 构建成功；可安装启动；核心路径全通过；不能用 Debug 包结论替代。

---

# P1 — 上架前强烈建议修复

## P1-1：权限和隐私文案资源化

**当前状态**：已关闭

**问题**：权限说明、错误提示、删除确认文案仍存在硬编码风险，审核敏感文案后续修改容易遗漏。

**已完成**：权限、隐私提示、加载失败、删除确认、删除结果提示等用户可见文案已迁移到 `res/values/strings.xml`，Compose 使用 `stringResource()`，ViewModel 和 Repository 通过资源读取用户可见兜底文案。

**验证记录**：GitHub Actions Build APK #95 已通过 unit test、Android Lint、Debug APK 构建、Release APK 构建和 GitHub Release 发布。

**注意**：Android 10 删除路径和批量删除语义已并入 P0-7，不再作为 P1 重复跟踪。

---

# P2 — 质量问题，建议修

## P2-1：`allowBackup="true"` 存在轻微隐私风险

**当前状态**：已关闭

**已完成**：`AndroidManifest.xml` 中 `android:allowBackup` 已改为 `false`。

**验证记录**：GitHub Actions Build APK #95 已通过 unit test、Android Lint、Debug APK 构建、Release APK 构建和 GitHub Release 发布。

## P2-2：普通用户可见字符串硬编码

**当前状态**：已关闭

**已完成**：普通用户可见主流程文案已同步迁移到 `strings.xml`。

**验证记录**：GitHub Actions Build APK #95 已通过 unit test、Android Lint、Debug APK 构建、Release APK 构建和 GitHub Release 发布。

## P2-3：删除路径测试覆盖不足

**当前状态**：已关闭

**已完成**：补充删除路径 contract test，覆盖 API 30+ 系统确认分支、直接删除失败保留队列、部分成功仅移除已删除照片并保留失败照片。

**验证记录**：GitHub Actions Build APK #95 已通过 unit test、Android Lint、Debug APK 构建、Release APK 构建和 GitHub Release 发布。

**依赖**：优先等 P0-7 删除结果模型稳定后再补完整测试，避免测试锁死旧设计。

## P2-4：缩略图 / 边界手感优化

**当前状态**：已关闭

**已完成**：第一张右划和最后一张左划加入边界阻尼，松手回弹，不触发业务状态迁移。

**验证记录**：GitHub Actions Build APK #95 已通过 unit test、Android Lint、Debug APK 构建、Release APK 构建和 GitHub Release 发布。

## P2-5：上划松手飞出过渡略不连续

**当前状态**：已关闭

**已完成**：上划飞出动画继承拖动预览缩放和位移作为起点，业务仍在上划接受后立即入队。

**验证记录**：GitHub Actions Build APK #95 已通过 unit test、Android Lint、Debug APK 构建、Release APK 构建和 GitHub Release 发布。

---

# P3 — 后续迭代

## P3-1：Coil 缓存和解码策略

**当前状态**：已关闭

**已完成**：照片卡片、缩略图和删除确认缩略图使用明确尺寸的 Coil `ImageRequest`，配置稳定 memory/disk cache key、硬件解码和 inexact precision。

**验证记录**：GitHub Actions Build APK #95 已通过 unit test、Android Lint、Debug APK 构建、Release APK 构建和 GitHub Release 发布。

## P3-2：测试文件命名一致性

**当前状态**：已关闭

**已完成**：`DeleteQueueContractTest` 文件名和类名已统一。

**验证记录**：GitHub Actions Build APK #95 已通过 unit test、Android Lint、Debug APK 构建、Release APK 构建和 GitHub Release 发布。

---

# 建议执行顺序

## 第一批：发布前 hardening

1. P1-1 权限和删除文案资源化。
2. P2-1 关闭或限制备份。
3. P2-3 删除路径测试补强。

## 第二批：体验和维护优化

1. P2-4 边界阻尼。
2. P2-5 上划飞出过渡连续性。
3. P3-1 Coil 缓存策略。
4. P3-2 测试命名整理。

---

# P0 关闭判定规则

每个 P0 必须同时满足：

1. 代码或材料已完成。
2. 有明确验收记录。
3. 有测试或真机验证覆盖。
4. 不依赖“应该可以”的推断。
5. 不把 Debug 包验证等同于 Release 包验证。

只要任意一个 P0 没有满足上述规则，就不能宣称“所有 P0 已关闭”。
