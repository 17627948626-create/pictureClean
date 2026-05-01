# YiHua - 照片清理 App

Android 照片管理应用，支持滑动快速删除照片。

---

## APK 构建与下载

### 如何触发 APK 构建

有两种方式：

1. **自动触发**：向 `main` 分支推送代码，GitHub Actions 自动构建。
2. **手动触发**：
   - 打开仓库页面 → Actions → **Build APK** → **Run workflow** → **Run workflow**

### 在哪里下载 APK

构建完成后，APK 发布在 **Releases** 页面：

```
https://github.com/17627948626-create/pictureClean/releases
```

找到最新的 `Debug Build #N`，点击 `YiHua-debug-N.apk` 即可下载。

### 安卓手机如何安装 APK

1. 用手机浏览器打开上方 Releases 链接，下载 APK 文件。
2. 下载完成后点击文件，系统会提示「安装未知来源应用」。
3. 进入 **设置 → 安全 → 安装未知应用**，允许浏览器安装。
4. 返回点击 APK，按提示安装即可。

### Debug APK 覆盖安装失败怎么办

Debug APK 每次构建的签名可能不同，导致覆盖安装报错「签名不一致」。

**解决方法**：先卸载旧版本，再安装新版。

### 后续如何升级为稳定签名 Release APK

1. 生成 keystore 签名文件：
   ```bash
   keytool -genkey -v -keystore release.jks -alias yihua \
     -keyalg RSA -keysize 2048 -validity 10000
   ```
2. 将以下内容添加到 GitHub 仓库 Secrets：
   - `KEYSTORE_BASE64`：keystore 文件的 base64 编码
   - `KEY_ALIAS`：alias 名称
   - `KEY_PASSWORD`：key 密码
   - `STORE_PASSWORD`：store 密码
3. 在 `app/build.gradle.kts` 中配置 `signingConfigs`，在 workflow 中添加签名步骤。

---

## 可选：飞书构建通知

在仓库 **Settings → Secrets → Actions** 中添加 `FEISHU_BOT_WEBHOOK`，
构建成功或失败后会自动发送飞书通知（含 Release 链接和 commit hash）。

不配置此 Secret 不影响构建流程。

---

## 技术栈

- Kotlin + Jetpack Compose
- MVVM 架构
- Coil 图片加载
- Accompanist Permissions
