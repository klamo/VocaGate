# Desktop Companion 运行说明

## 1. 目标

Desktop companion 把 Android 端检测到的开麦状态映射成电脑上的按键保持：

```text
Android PRE_OPEN -> 电脑提前按住配置键
Android OPEN    -> 电脑按住配置键
Android BLOCKED -> 电脑松开配置键
断连 / 超时 / 退出 -> 电脑强制松开配置键
```

第一版是开发闭环工具，不是真正的虚拟麦克风。

桌面端现在有两种输入来源：

| 输入来源 | 当前能力 |
|---|---|
| 手机 VocaGate | 保持原有链路，由手机端识别并发送 `PRE_OPEN / OPEN / BLOCKED` |
| 本机摄像头 | 枚举、选择 macOS/Windows 本机摄像头，并记住上次选择；主窗口默认不显示画面，可点“查看摄像头”打开独立预览窗口；采集优先走 OpenCV VideoCapture，webcam-capture 只在可用平台做备用；识别优先使用 OpenCV YuNet DNN 嘴角 landmark provider，失败时回到中心 fallback；识别结果会直接控制配置的按住按键 |

## 2. 运行电脑端

在项目根目录执行：

```bash
scripts/run_desktop.sh
```

窗口里可以设置：

```text
输入来源：手机 VocaGate / 本机摄像头
摄像头：选择内置摄像头、USB 摄像头或系统里暴露出来的手机摄像头
连接方式：Auto / USB / Wi-Fi
监听端口：默认 27183
按住按键：默认 CTRL+SHIFT+M，可选择“自定义...”输入组合键
本机 IP：给手机 Wi-Fi 配对使用
Wi-Fi 配对码：给手机 Wi-Fi 配对使用
```

建议第一轮使用默认的 `CTRL+SHIFT+M`，Windows 上比 `F13` 更稳。然后在目标语音软件里，把 PTT 按键设置成同一个键。如果目标软件不方便设置这个组合键，可以在电脑端选择“自定义...”，输入类似 `ALT+V`、`SPACE`、`A`、`F1` 的按键。

如果选择“本机摄像头”，主窗口不会常驻显示摄像头画面，只会在后台保持取帧和本机识别。需要看画面时，点击“查看摄像头”会打开一个独立预览窗口；关闭这个窗口只会关闭预览窗口，不会停止后台识别。预览窗口会叠加嘴部 ROI 框、mouth landmark 点、状态标签和分数条：蓝色表示有稳定 ROI 但未开麦，绿色表示疑似说话或进入开麦状态，黄色表示当前没有可靠嘴部区域。摄像头采集会优先使用 OpenCV `VideoCapture`，如果这个 provider 不可用，再尝试可用平台上的旧 webcam-capture provider；两者枚举失败时只显示“未检测到可用摄像头”，不会让窗口线程崩溃。

本机摄像头识别结果会进入与 Android 相同的 `gate-core` 状态机，`PRE_OPEN / OPEN / HOLD` 会通过同一个 `GateHoldController` 按住配置键，`BLOCKED / SUSPEND / 无画面 / 停止识别` 会释放配置键。默认 mouth-region provider 链路是：OpenCV YuNet DNN 嘴角 landmark provider -> 中心嘴部 fallback。YuNet 需要 `desktop/src/main/resources/opencv/face_detection_yunet_2023mar.onnx` 被打进包里；如果模型资源缺失、OpenCV native 不可用或检测失败，桌面端会自动 fallback，保证应用不崩溃。旧的 Haar provider 保留在源码里便于诊断，但不再作为默认链路自动启用。

开发环境中可以用下面的脚本下载 YuNet 模型：

```bash
scripts/fetch_desktop_models.sh
```

连接方式含义：

| 模式 | 行为 |
|---|---|
| Auto | 有 USB 连接时优先 USB；没有 USB 时接受已配对 Wi-Fi |
| USB | 只接受 USB / adb reverse 连接 |
| Wi-Fi | 只接受局域网 Wi-Fi 连接 |

Auto 模式下，如果 Wi-Fi 已连接时 USB 连接进来，电脑端会切到 USB。切换前会先松开按键，避免卡键。

macOS 上如果按键没有生效，需要到：

```text
系统设置 -> 隐私与安全性 -> 辅助功能
```

给运行 Java/Gradle 的终端或后续打包出的 app 开启权限。

## 3. 建立 USB 转发

这一步是在 Mac/PC 终端执行，不是在手机上执行。手机需要先通过 USB 连接电脑，并且已经授权 adb。

先确认电脑能看到手机：

```bash
adb devices
```

正常时应该看到类似：

```text
List of devices attached
fc8a3d7	device
```

如果列表为空，或者状态是 `unauthorized`，请检查：

- 手机是否已经用 USB 线连接电脑；
- 手机是否开启“USB 调试”；
- 手机屏幕是否解锁，并在“允许 USB 调试？”弹窗里点了允许；
- 连接方式如果弹出 USB 用途，可以保持“文件传输”或默认模式，只要 adb 能看到 `device` 即可。

确认 `adb devices` 有一行 `device` 后，执行：

macOS / Linux:

```sh
scripts/setup_pc_link.sh
```

Windows:

```bat
scripts\setup_pc_link.bat
```

如果你是在 Windows 上只解压了 `desktop.zip`，脚本会在解压目录里：

```bat
desktop\bin\setup_pc_link.bat
```

脚本会先清理旧的 `tcp:27183` 转发，再创建新的转发，所以重复执行是安全的。

它等价于：

```bash
adb reverse --remove tcp:27183
adb reverse tcp:27183 tcp:27183
```

Android app 会连接手机本机 `127.0.0.1:27183`，ADB reverse 会把这条连接转发到电脑本机 `127.0.0.1:27183`。

如果手机上旧端口曾经卡住，例如 `17273` 一直显示 `Address already in use`，可以不用继续处理旧端口。保持电脑端和手机端都使用默认 `27183`，重新执行对应平台的 setup 脚本即可。

如果有多台设备，使用：

macOS / Linux:

```sh
scripts/setup_pc_link.sh --device <serial>
```

Windows:

```bat
scripts\setup_pc_link.bat --device <serial>
```

如果使用的是解压后的 `desktop.zip`：

```bat
desktop\bin\setup_pc_link.bat --device <serial>
```

停止转发：

macOS / Linux:

```sh
scripts/setup_pc_link.sh --remove
```

Windows:

```bat
scripts\setup_pc_link.bat --remove
```

如果使用的是解压后的 `desktop.zip`：

```bat
desktop\bin\setup_pc_link.bat --remove
```

## 4. 运行 Android 端

这一步也在 Mac/PC 终端执行，只是它会把 APK 安装到手机并启动 app。

如果手机上已经安装了当前最新 APK，可以不用再执行 `scripts/run_android.sh`，直接在手机上手动打开 VocaGate app 即可。

只有在代码更新后需要重新安装，或者想从命令行自动启动 app 时，才执行：

```bash
scripts/run_android.sh
```

Android 状态面板里会出现：

```text
PC: waiting
PC: USB connected
PC: Wi-Fi connected
PC: waiting, set Wi-Fi
PC: off
```

看到 `PC: USB connected` 或 `PC: Wi-Fi connected` 后，对着手机说话，电脑端窗口应显示 `OPEN`，并按住配置键；停止说话后应显示 `BLOCKED`，并松开配置键。

## 5. Wi-Fi 配对

Wi-Fi 配对不需要 USB 调试，但手机和电脑需要在同一个局域网内。

电脑端：

1. 启动 desktop companion；
2. 连接方式选择 `Auto` 或 `Wi-Fi`；
3. 点击“启动”；
4. 记下窗口里的“本机 IP”和“Wi-Fi 配对码”。

手机端：

1. 打开 VocaGate；
2. 点击“显示设置”；
3. 在“电脑连接”里选择 `Auto` 或 `Wi-Fi`；
4. 确认“电脑端口”和电脑端“监听端口”一致，默认都是 `27183`；
5. 输入电脑端显示的本机 IP；
6. 输入电脑端显示的 Wi-Fi 配对码；
7. 点击“保存电脑连接设置”。

连接成功后，手机端会显示：

```text
PC: Wi-Fi connected
```

电脑端“当前通道”会显示：

```text
WIFI
```

## 6. 推荐测试顺序

1. 在 Mac/PC 上启动电脑端 companion；
2. 连接方式先选择 `Auto`；
3. 如果测试 USB，USB 连接手机并确认 `adb devices` 显示 `device`；
4. 如果测试 USB，在 Mac 上执行 `scripts/setup_pc_link.sh`；在 Windows 源码目录执行 `scripts\setup_pc_link.bat`，只解压桌面包时执行 `desktop\bin\setup_pc_link.bat`；
5. 在手机上打开 VocaGate app；如果手机还没装最新 APK，再在 Mac/PC 上执行 `scripts/run_android.sh`；
6. 如果测试 Wi-Fi，在手机端填写电脑 IP 和配对码并保存；
7. 确认 Android 端显示 `PC: USB connected` 或 `PC: Wi-Fi connected`；
8. 在电脑端选择按键，建议先用默认 `CTRL+SHIFT+M`，或选择“自定义...”输入自己的组合键；
9. 在目标软件里把 PTT 设置为同一个按键；
10. 对手机说话，观察目标软件是否进入按住说话状态；
11. 停止说话、遮挡摄像头或拔掉 USB，确认按键会自动释放。

## 7. 已知限制

- Wi-Fi 需要手机和电脑在同一局域网内，企业/酒店网络可能阻止设备互联；
- Wi-Fi 版平均延迟通常可接受，但比 USB 更容易有抖动；
- macOS 需要辅助功能权限才能模拟按键；
- Windows 上如果目标软件以管理员权限运行，desktop companion 也可能需要同等级权限；
- 本机摄像头模式会直接控制配置按键；如果 YuNet 模型资源缺失，会自动回退到中心 fallback，此时嘴部定位效果不如 DNN landmark；
- macOS/Windows 首次使用本机摄像头时，系统可能要求给 Java/终端或后续打包出的 app 授权摄像头权限；
- 如果 macOS Apple Silicon 上旧的 webcam-capture/OpenIMAJ native 后端初始化失败，桌面端默认不会尝试它；本机摄像头采集优先走 OpenCV `VideoCapture`；
- 部分游戏或带反作弊的软件可能拦截模拟按键；
- 当前没有事件日志文件，只有窗口状态；
- 当前没有打包安装器，需要从项目里运行。
