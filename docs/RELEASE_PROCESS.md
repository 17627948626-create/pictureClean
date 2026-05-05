# Release Process

本文档记录 APK 构建、下载、安装、签名和构建通知配置。

## 触发 APK 构建

有两种方式：

1. 自动触发：向 `main` 分支推送代码，GitHub Actions 自动构建。
2. 手动触发：打开仓库页面 → Actions → **Build APK** → **Run workflow**。

## 下载 APK

构建完成后，APK 发布在 GitHub Releases 页面：

```text
https://github.com/17627948626-create/pictureClean/releases
```

每次构建会上传：

| 文件 | 说明 |
|------|------|
| `YiHua-debug-N.apk` | Debug APK，始终构建，签名不稳定 |
| `YiHua-release-N.apk` | Signed Release APK，仅在签名 secrets 完整时构建 |

推荐真机长期测试使用 Release APK。Release APK 签名稳定，支持覆盖安装升级。

## 安卓手机安装 APK

1. 用手机浏览器打开 Releases 页面，下载 APK。
2. 下载完成后点击 APK 文件。
3. 如果系统提示“安装未知来源应用”，进入系统设置允许当前浏览器安装。
4. 返回 APK 文件继续安装。

## 配置签名

### 生成 keystore

```bash
keytool -genkey -v -keystore release.jks -alias yihua \
  -keyalg RSA -keysize 2048 -validity 10000
```

`release.jks` 不能提交到 Git。

### 转为 Base64

```bash
base64 -i release.jks | tr -d '\n'
```

复制输出内容备用。

### 配置 GitHub Secrets

进入仓库 **Settings → Secrets and variables → Actions → New repository secret**，添加：

| Secret 名称 | 填写内容 |
|-------------|----------|
| `ANDROID_KEYSTORE_BASE64` | keystore 的 Base64 字符串 |
| `ANDROID_KEYSTORE_PASSWORD` | keystore 的 store 密码 |
| `ANDROID_KEY_ALIAS` | 生成 keystore 时填写的 alias，例如 `yihua` |
| `ANDROID_KEY_PASSWORD` | alias 对应的 key 密码 |

配置完成后，推送任意 commit 触发构建。构建成功后 Releases 页面应出现 `YiHua-release-N.apk`。

## 飞书构建通知

可选配置：在仓库 **Settings → Secrets and variables → Actions** 添加：

| Secret 名称 | 用途 |
|-------------|------|
| `FEISHU_BOT_WEBHOOK` | 构建成功/失败后发送飞书通知 |

不配置此 Secret 不影响 APK 构建。

## 发版前检查

正式发版前必须确认：

- `RELEASE_BLOCKERS_REVIEW.md` 中没有未完成 P0。
- GitHub Actions 构建成功。
- Release APK 可下载并可覆盖安装。
- 真机验证核心路径：加载照片、左右翻页、上划入队、下划恢复、确认删除。
