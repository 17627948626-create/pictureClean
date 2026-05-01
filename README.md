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

每次构建会同时上传：

| 文件 | 说明 |
|------|------|
| `YiHua-debug-N.apk` | Debug APK，始终构建，签名不稳定 |
| `YiHua-release-N.apk` | Signed Release APK，需配置 secrets |

### 安卓手机如何安装 APK

1. 用手机浏览器打开上方 Releases 链接，下载 APK 文件。
2. 下载完成后点击文件，系统会提示「安装未知来源应用」。
3. 进入 **设置 → 安全 → 安装未知应用**，允许浏览器安装。
4. 返回点击 APK，按提示安装即可。

> **推荐安装 Release APK**，签名稳定，支持直接覆盖安装升级。

---

## 配置签名（Signed Release APK）

### 第一步：生成 keystore 文件

```bash
keytool -genkey -v -keystore release.jks -alias yihua \
  -keyalg RSA -keysize 2048 -validity 10000
```

按提示填写密码和信息。生成的 `release.jks` 文件**不要提交到 Git**。

### 第二步：将 keystore 转为 Base64

```bash
base64 -i release.jks | tr -d '\n'
```

复制输出的完整字符串，备用。

### 第三步：在 GitHub 仓库配置 Secrets

进入仓库 **Settings → Secrets and variables → Actions → New repository secret**，添加以下 4 个 Secret：

| Secret 名称 | 填写内容 |
|-------------|----------|
| `ANDROID_KEYSTORE_BASE64` | 上一步复制的 Base64 字符串 |
| `ANDROID_KEYSTORE_PASSWORD` | keystore 的 store 密码 |
| `ANDROID_KEY_ALIAS` | 生成时填写的 alias（如 `yihua`） |
| `ANDROID_KEY_PASSWORD` | alias 对应的 key 密码 |

### 验证

配置完成后，推送任意 commit 触发构建。构建成功后 Release 页面会出现 `YiHua-release-N.apk`，Release 说明中会显示 **✅ 已签名**。

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
