# pictureClean / 一划

Android 照片快速清理 App。核心目标是让用户用最少的动作完成照片去留判断。

## 当前真相

| 用户手势 | 业务结果 | 视觉反馈 |
|----------|----------|----------|
| 从右往左划 / 左划 | 下一张 | 当前照片向左飞出，下一张自然露出 |
| 从左往右划 / 右划 | 上一张 | 上一张从左侧盖回来 |
| 上划 | 加入待删除队列 | 当前照片向上飞出 |
| 下划 | 恢复最近误删 | 最近误删照片从顶部盖回来 |

不可破坏的底线：

- 当前只展示一张照片，永远居中。
- 上划删除是业务状态变化，不是动画结果。
- UI 接受上划后，必须立即调用 `PhotoViewModel.queueCurrentPhotoForDeletion()`。
- 动画可以保留旧照片做视觉飞出，但不能决定照片是否入队，也不能等动画结束后才入队。

## 当前发版门禁

仍有两个 P0，正式发版前必须修完并真机验证：

1. 回弹动画必须等 X/Y 两个方向都结束后，才能释放手势锁。
2. 删除最后一张时，必须保活动画宿主，等飞出动画完整播放后再切到空状态。

详细动画规格和 P0 验收标准见：`docs/SWIPE_ANIMATION_V2.md`。

## 最小真机回归

修改手势、动画、权限、删除队列或 MediaStore 代码前后，都要验证：

1. Fresh install。
2. 授权照片访问。
3. 确认照片加载。
4. 左划进入下一张。
5. 右划回到上一张。
6. 上划一张照片，垃圾桶数量立即增加。
7. 下划恢复最近误删。
8. 打开待删除确认页，确认队列内容正确。
9. 发起删除，确认系统删除弹窗出现。
10. 确认删除后，照片从应用列表中消失。
11. 只剩最后一张时，上划删除动画完整播放后再进入空状态。

## 构建与 APK

常用命令：

```bash
./gradlew :app:testDebugUnitTest --no-daemon
./gradlew :app:lintDebug --no-daemon
./gradlew :app:assembleDebug --no-daemon
```

GitHub Actions：

- 推送到 `main` 自动构建 APK。
- 也可以在 Actions 页面手动触发 **Build APK** workflow。
- 构建产物发布在 Releases：`https://github.com/17627948626-create/pictureClean/releases`

Release APK 签名需要配置以下 GitHub Actions secrets：

| Secret | 用途 |
|--------|------|
| `ANDROID_KEYSTORE_BASE64` | keystore 的 Base64 字符串 |
| `ANDROID_KEYSTORE_PASSWORD` | keystore 的 store 密码 |
| `ANDROID_KEY_ALIAS` | keystore alias，例如 `yihua` |
| `ANDROID_KEY_PASSWORD` | alias 对应的 key 密码 |
| `FEISHU_BOT_WEBHOOK` | 可选，构建通知 |

## 文档

长期保留两份：

| 文档 | 用途 |
|------|------|
| `README.md` | 项目当前真相、发版门禁、回归清单、构建入口 |
| `docs/SWIPE_ANIMATION_V2.md` | 当前滑动交互和动画详细规格 |

过期、重复、临时门禁信息不要新增文档；直接合并进这两份之一。

## 技术栈

- Kotlin
- Jetpack Compose
- MVVM
- Coil
- Accompanist Permissions
