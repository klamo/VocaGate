# VocaGate 开发指南

[中文首页](../README.md) · [普通用户安装](QUICK_START.md)

本页描述源码构建。Release 的自包含桌面包与 Gradle 生成的开发用 `desktop.zip` 不同。

## 工程结构

| 目录 | 职责 |
| --- | --- |
| `app/` | Android CameraX / ML Kit、音频辅助、录音测试和手机连接 |
| `gate-core/` | 两端共享的门控状态机、算法 profile 与更新协议 |
| `desktop/` | Swing 桌面、摄像头、MediaPipe sidecar、PTT 按键和连接服务 |
| `scripts/` | 开发运行、连接和打包脚本 |
| `tools/` | Java / shell / Python 烟测 |
| `server/update-service/` | 静态更新服务示例，不代表线上部署完成 |

`docs/superpowers/` 中的规格和任务是历史计划，需对照代码理解；文字目标不等于已交付。个人讨论和原始演示材料不包含在公开仓库中。公开仓库从独立源码快照开始，后续开发应从公开仓库重新克隆；不要合并或推送此前私有仓库的提交历史。

## 环境

- Java 源码兼容级别 17；打包使用 JDK 21 工具。
- Android：SDK 36、项目 Gradle / AGP 配置要求的兼容环境。
- 本机摄像头开发模式需要 Python、MediaPipe 和模型。
- 仓库有 Gradle wrapper；旧脚本中仍有直接调用 `gradle` 的入口，执行前确认本机环境。

## Android

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

或使用 `scripts/run_android.sh`；多设备时增加 `--device <adb-serial>`。debug 与 release 签名不同，不要为测试随意卸载用户正在使用的 release 应用。

## 桌面

```bash
scripts/setup_mediapipe_sidecar.sh
scripts/run_desktop.sh
```

Windows sidecar 初始化使用 `scripts\setup_mediapipe_sidecar.bat`；Gradle 使用 `gradlew.bat`。

当前启动解析器与旧 `.venv-mediapipe` 自动发现存在兼容性待核对项。源码运行时如未识别虚拟环境，可显式设置 `VOCAGATE_PYTHON` 为实际环境的 Python 路径；不要因此修改系统 Python。

开发分发：

```bash
./gradlew :desktop:assembleDist
```

输出 `desktop/build/distributions/desktop.zip`。它不是自包含 Release 安装包，仍需自行准备运行环境；辅助脚本是否随分发打入应以实际输出为准。

## 回归检查

核心烟测涵盖门控、连续讲话保持、音频辅助不可单独开麦，以及 Android 纯 Java 逻辑：

```bash
mkdir -p /tmp/vocagate-core-test
javac -d /tmp/vocagate-core-test \
  -cp gate-core/src/main/java:app/src/main/java:desktop/src/main/java \
  tools/CoreSmokeTest.java
java -cp /tmp/vocagate-core-test CoreSmokeTest
```

更新与路径测试：

```bash
mkdir -p /tmp/vocagate-smoke
javac -d /tmp/vocagate-smoke \
  -cp gate-core/src/main/java:app/src/main/java:desktop/src/main/java \
  tools/UpdateProtocolSmokeTest.java tools/UpdateClientSmokeTest.java \
  tools/UpdateProfileApplicationSmokeTest.java tools/UpdateStatusFormatterSmokeTest.java \
  tools/DesktopSidecarLaunchSpecSmokeTest.java tools/DesktopUsbReverseSetupSmokeTest.java \
  tools/AndroidUpdateSettingsPageSmokeTest.java
for test in UpdateProtocolSmokeTest UpdateClientSmokeTest UpdateProfileApplicationSmokeTest \
  UpdateStatusFormatterSmokeTest DesktopSidecarLaunchSpecSmokeTest \
  DesktopUsbReverseSetupSmokeTest AndroidUpdateSettingsPageSmokeTest; do
  java -cp /tmp/vocagate-smoke "$test" || break
done
bash tools/test_android_orientation_config.sh
```

以上 shell 示例面向 macOS / Linux；Windows 的 Java classpath 分隔符为 `;`。

完整 Android 构建 / 单元测试：

```bash
./gradlew :gate-core:compileJava :app:testDebugUnitTest :app:assembleDebug :desktop:compileJava
```

已有 `ReleasePackagingSmokeTest` 包含与后来脚本实现不一致的静态断言，当前不能当作通过的发布门禁。桌面 helper 权限检查针对现存开发 ZIP，不证明最新源码重建后的分发一致。

## 算法与连接边界

- Android 的音频辅助只能加分，仍必须满足嘴部运动和视觉可靠性条件。
- 手机连接模式不把识别转移到桌面，也不向桌面传输原始音视频。
- 改动共享 HOLD / PRE_OPEN 行为时，同时验证桌面和 Android。
- MediaPipe 失败时旧 OpenCV 回退仍在代码中，不能把尚未实现的故障关闭方案写成现状。
- 自动发现、二维码配对、内置隔离 ADB 是后续计划。

签名与发布入口见[发布指南](RELEASE_PACKAGING.md)。
