# 星空小站 Android App

`https://drugsthatthis.dpdns.org` 的官方原生 WebView 客户端。

## 🚀 构建

推送代码到 `main` 分支后，GitHub Actions 自动构建 APK 并发布到 Releases。

- 手动触发：Actions 页面 → `Build Android APK` → Run workflow
- 构建产物：`artifacts/xiaoyan-archive-build<N>.apk`
- Release：tag `build-<run_number>`

## 📱 安装

1. 打开 https://github.com/xiaoyanuser1Q/xiaoyan-app/releases
2. 下载最新 Release 的 APK
3. 手机点击 APK 安装（首次需允许「未知来源」）
4. 打开 App，登录即可使用

## 🔒 签名

**默认 debug 签名**（可安装使用，个人 App 够用）。

如需升级为 release 签名（正式发行/多设备分发）：

```bash
# 1. 生成 keystore（记得保存好密码和 alias）
keytool -genkeypair -v \
  -keystore xiaoyan-release.keystore \
  -alias xiaoyan \
  -keyalg RSA -keysize 2048 -validity 10000

# 2. 编码为 base64
base64 -w0 xiaoyan-release.keystore > keystore.b64
```

在 GitHub 仓库 → Settings → Secrets and variables → Actions 添加：

| Secret | 值 |
|---|---|
| `KEYSTORE_BASE64` | `keystore.b64` 内容 |
| `KEYSTORE_PASSWORD` | keystore 密码 |
| `KEY_ALIAS` | `xiaoyan` |
| `KEY_PASSWORD` | 密钥别名密码 |

下次构建自动切换为 release 签名。

## 🎨 特性

- 全屏 WebView 加载 `drugsthatthis.dpdns.org`
- 菜单「切换服务器」在花生壳 / `xiaoyan-archive.3394073613.workers.dev` 间一键切换
- 深色模式跟随系统
- 下拉刷新（网页自带）
- 分享当前页面
- 用外部浏览器打开

## 📦 版本

| 版本 | 说明 |
|---|---|
| v1.0.0 | 首发：WebView 套壳 + 云端构建 + 自动 Release |

## 📄 许可证

私有仓库，仅供 `xiaoyanuser1Q` 使用。
