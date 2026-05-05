# pictureClean / 一划

Android 照片快速清理 App。核心目标是让用户用最少的动作完成照片去留判断。

## 当前核心交互

| 手势 | 结果 | 动画语义 |
|------|------|----------|
| 从右往左划 | 下一张 | 当前照片向左飞出，下一张自然露出 |
| 从左往右划 | 上一张 | 上一张从左侧盖回来 |
| 上划 | 加入待删除队列 | 当前照片向上飞出 |
| 下划 | 恢复最近误删 | 最近误删照片从顶部盖回来 |

关键原则：

- 当前只展示一张照片，永远居中。
- 删除是业务状态变化，不能依赖动画结束后才入队。
- 动画只做视觉反馈，不能决定照片是否进入待删除队列。

## 当前发版状态

发版前先看根目录的 `RELEASE_BLOCKERS_REVIEW.md`。

只要里面仍有未完成的 P0，默认不能正式发版。

## 文档索引

| 文档 | 用途 |
|------|------|
| `docs/SWIPE_ANIMATION_V2.md` | 当前滑动动画与交互规格 |
| `docs/DELETE_FLOW_BASELINE.md` | 删除流程的业务底线与回归清单 |
| `docs/RELEASE_PROCESS.md` | APK 构建、下载、签名、通知配置 |
| `RELEASE_BLOCKERS_REVIEW.md` | 当前短期发版阻塞项，P0 修完后应归档或删除 |

## 本地开发

常用命令：

```bash
./gradlew :app:testDebugUnitTest --no-daemon
./gradlew :app:lintDebug --no-daemon
./gradlew :app:assembleDebug --no-daemon
```

## APK 获取

推送到 `main` 后，GitHub Actions 会自动构建 APK。也可以在 Actions 页面手动触发 **Build APK** workflow。

构建和安装细节见：`docs/RELEASE_PROCESS.md`。

## 技术栈

- Kotlin
- Jetpack Compose
- MVVM
- Coil
- Accompanist Permissions
