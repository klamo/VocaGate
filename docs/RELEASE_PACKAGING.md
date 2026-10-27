# 安装包与首次发布

[中文首页](../README.md) · [首发说明](RELEASE_0.2.0_PREVIEW.md)

## 本次范围

先开放自有源代码、完善中文首页，准备首批 `0.2.0` 预览安装包。不在这一轮增加账号、支付、自动发现或统计服务。

版本号与候选标签分开：应用内部仍为 `0.2.0`，首次 GitHub Release 使用 `v0.2.0-preview.1`。这不是一次宣称“所有包都由当前 HEAD 重建”的发布。

## 现有包与来源

- `release/0.2.0/` 保存 2026 年 7 月的原始 APK、DMG、Windows ZIP 及校验和，不覆盖。
- `release/0.2.0-preview.1/` 用于本次独立候选。本轮 Android / Windows 沿用原件，但必须在 Release 中写明并非当前源码重建。
- APK 内嵌 Git revision 为 `052408b68ca6824c55c62f70a3e3f48dedfb1bc2`，但没有记录当时工作区是否干净；旧包缺少完整源码指纹。发布标签只固定本次说明和源码快照。
- 旧 macOS app 在 jpackage 生成后改过 Info.plist，原签名资源封印无效。必须先在副本上修复并验证，不能直接上传原 DMG。

## 从源码重新构建

使用相应平台构建机及 JDK 21、Gradle、Android SDK / Python 打包依赖。以下脚本会写入 `release/<version>/`，已有产物应先另存，避免覆盖候选或原始记录。

```bash
# Android：只使用原 release 密钥；不回退到 debug 签名
scripts/package_android_release.sh

# Apple Silicon Mac
scripts/package_macos_arm64.sh
scripts/verify_release_artifacts.sh --platform macos-arm64 release/0.2.0
```

Windows 在 Windows x64 主机运行：

```bat
scripts\package_windows_x64.bat
scripts\verify_release_artifacts.bat --platform windows-x64 release\0.2.0
```

Windows 脚本先生成绿色 ZIP；有对应 WiX 工具时才尝试 EXE，不能把 ZIP 写成已经交付的安装器。

## macOS 临时签名

摄像头用途说明等包内文件修改完成后，重新生成顶层 ad-hoc 签名，再检查嵌套代码和资源完整性：

```bash
codesign --force --sign - --timestamp=none /path/to/VocaGate.app
codesign --verify --deep --strict --verbose=2 /path/to/VocaGate.app
```

打包脚本已把这一检查放到 DMG 生成前。临时签名不需要购买证书，但不提供 Developer ID 身份或 Apple 公证。不要通过删除系统安全保护、清空全局 quarantine 或修改信任设置来“修复”安装。

如果当前环境不能创建 / 挂载磁盘映像，可用 `ditto -c -k --sequesterRsrc --keepParent` 将已校验的 app 做成 ZIP，清楚标注格式；不能把 ZIP 改名为 DMG。

## 签名材料

`keys/vocagate-release.jks` 与 `release-signing.properties` 是本机私有输入，不能提交、打印、打包或上传。Android 重建必须使用原签名密钥；仅重新发布旧 APK 时不需要打开密钥。

发布说明中的签名状态只讨论校验结果和公开证书指纹，不包含密码、别名或私钥内容。

## 上传前核对实际产物

需要核对文件哈希、Android APK 签名、桌面 classpath 引用、内置 Java、sidecar、模型以及 macOS 签名完整性。然后分别记录 GUI、摄像头、手机连接、真实 PTT 和停止 / 断连释放的测试情况；未测就写未测。

现有 `ReleasePackagingSmokeTest` 有过时的脚本字符串断言；不能把此测试视作已经通过。sidecar healthcheck 也不代替完整 GUI 验收。

## GitHub 发布

公开仓库从排除 `docs/other/` 的独立源码快照开始，不包含此前私有开发历史。原仓库及讨论、原始演示材料保留为私有备份。后续开发应从公开仓库重新克隆，不要把私有历史合并或推送到公开仓库。个人材料目录已加入 `.gitignore`。首发 macOS 候选仅保留在本地，sidecar 检查问题解决前不上传。

仓库公开范围确认、提交推送完成后，使用中文 Release 说明，标题和正文明确标注 **早期预览、非稳定版**。首发设为 Latest 以提供首页最新下载入口；这不代表完成 GUI、摄像头或按键链路验收。仅上传候选安装包、`SHA256SUMS.txt`、`ARTIFACT_PROVENANCE.json` 和发布说明，不上传整个目录、源码缓存或签名材料。

GitHub 页面和网络不可用时，保留候选与待发布说明，不写成“已经上线”。
