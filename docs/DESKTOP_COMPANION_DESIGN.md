# Desktop Companion 闭环设计

## 1. 目标

第一版电脑端用于把 Android demo 的视觉开麦状态变成电脑上的按键保持行为：

```text
Android OPEN    -> 电脑按住配置键
Android BLOCKED -> 电脑松开配置键
断连 / 超时 / 退出 -> 电脑强制松开配置键
```

这版不直接控制真实麦克风，也不做会议软件专属适配。目标是先把“手机视觉检测 -> USB 事件 -> 电脑按键”的闭环跑通，便于后续调试吃音、漏触发和延迟。

桌面端同时支持“本机摄像头”路线：macOS/Windows 可以枚举和选择本机摄像头，并记住上次选择。主窗口默认不嵌入摄像头画面，用户需要时可打开独立预览窗口。本机摄像头会通过可替换的 mouth-region provider 定位嘴部区域，把分数送入与 Android 共用的 `gate-core` 状态机，并直接接入同一个 `GateHoldController` 控制按键；手机接入时的视觉识别仍由手机端完成。

## 2. 推荐形态

第一版使用轻量 Java 桌面 companion：

- 桌面端用 Java Swing 做一个小窗口，macOS 和 Windows 都能运行；
- 按键模拟使用 Java `Robot`，不额外引入 native 依赖；
- 本机摄像头采集封装在 `DesktopCameraProvider` 后面，主窗口不直接依赖第三方 API；默认优先使用 OpenCV `VideoCapture`，webcam-capture 只在可用平台作为备用 provider；
- 本机摄像头识别使用 `DesktopMouthRegionProvider` 封装嘴部 ROI 定位，优先使用 OpenCV YuNet DNN 嘴角 landmark provider；模型资源缺失、native 初始化失败或检测失败时回到中心 fallback；旧的 Haar provider 仅保留为诊断/对照后端，不再作为默认链路自动启用；
- 摄像头输入源和上次选择的设备 ID 使用 Java `Preferences` 保存；
- Android 可以通过 USB ADB reverse 或局域网 Wi-Fi 连接电脑端 TCP 端口；
- 协议使用 UTF-8 文本行，便于 adb、日志和手工调试。

USB 连接不是让手机直接变成键盘。普通 Android 手机通常不能在未 root、未定制系统的情况下作为 USB HID 键盘控制电脑。这里的闭环是：手机发送状态，电脑端自己模拟按键。

连接策略：

```text
Auto 模式：
  USB 可用 -> 使用 USB
  USB 不可用且配置了 Wi-Fi -> 使用 Wi-Fi
  两者都不可用 -> 手机提示设置 Wi-Fi

USB 模式：
  只接受 USB / adb reverse

Wi-Fi 模式：
  只接受局域网 Wi-Fi
```

Auto 模式下 USB 优先级高于 Wi-Fi。切换 active session 前必须先松开按键。

## 3. 数据流

```text
Android MainActivity
  -> gateOpen / state / score
  -> gate-core GateStateMachine
  -> PcLinkClient
  -> 127.0.0.1:27183
  -> adb reverse
  -> Desktop TCP server
  -> GateHoldController
  -> RobotKeyActuator
```

运行前在电脑上执行：

macOS / Linux:

```sh
adb reverse tcp:27183 tcp:27183
```

Windows 可以直接运行项目里的脚本：

```bat
scripts\setup_pc_link.bat
```

Windows 分发包也会把这个脚本放到 `desktop\bin\setup_pc_link.bat`，便于只解压桌面端的用户使用。

Android app 连接手机本机的 `127.0.0.1:27183`，ADB 会把它转发到电脑本机的同端口。端口在桌面端和 Android 端都可配置，默认值保持一致。

本机摄像头数据流直接进入桌面端按键控制：

```text
Desktop input source = 本机摄像头
  -> DesktopCameraProvider
     -> OpenCvVideoCaptureCameraProvider
     -> WebcamCaptureCameraProvider fallback
  -> camera device list
  -> selected camera capture session
  -> optional separate camera preview window
  -> preview snapshot
  -> DesktopMouthRegionProvider
     -> OpenCvYuNetMouthRegionProvider
     -> CenteredMouthRegionProvider fallback
  -> mouth ROI
  -> DesktopMotionScoreAnalyzer
  -> gate-core GateStateMachine
  -> DesktopLocalGateBridge
  -> GateHoldController
  -> RobotKeyActuator
  -> local recognition score/status labels
  -> preview overlay: mouth ROI, gate state, score bar
  -> remembered camera id
```

`DesktopMotionScoreAnalyzer` 只看 `DesktopMouthRegionProvider` 返回的嘴部 ROI 的帧间变化；当 provider 返回完整嘴部 landmark 时，会额外使用嘴部开合比例和节奏分数。OpenCV YuNet provider 使用 DNN 人脸检测结果里的左右嘴角 landmark 定位嘴部 ROI；它不是 Haar 估算，但只提供嘴角点，所以当前开麦分数仍主要依赖 ROI 内运动。识别结果会把嘴部 ROI 坐标、状态、分数一起回传给预览窗口，用于叠加嘴部框、landmark 点、状态标签和分数条。状态机已经与 Android 相同，`PRE_OPEN / OPEN / HOLD` 会通过 `DesktopLocalGateBridge` 进入 `GateHoldController`，无画面、停止识别或 `BLOCKED / SUSPEND` 会释放按键。

OpenCV `FacemarkLBF` 68 点模型曾作为完整嘴部 landmark 方案验证过，但在当前 macOS arm64 + JavaCPP OpenCV 4.9.0 运行时，`loadModel(lbfmodel.yaml)` 会在 native 层 abort，无法作为默认 in-process provider。默认链路因此先采用可安全失败的 YuNet provider；如果 YuNet ONNX 资源缺失，factory 会自动回到中心 fallback，保证应用不因模型缺失崩溃。

macOS 上旧的 webcam-capture 默认 OpenIMAJ native 后端可能在 `Webcam.getWebcams()` 阶段因为 `OpenIMAJGrabber` 初始化失败抛异常。在 Apple Silicon 上，当前 BridJ 依赖会加载 x86_64 dylib，因此默认 provider 列表会跳过 webcam-capture，只使用 OpenCV `VideoCapture`。桌面端也在 provider factory 和 UI 枚举入口都捕获 `RuntimeException / LinkageError`；单个后端坏掉时应该跳过或显示不可用，而不是打断 Swing AWT 线程。

JavaCPP OpenCV 依赖当前固定在 `org.bytedeco:opencv:4.9.0-1.5.10`，并显式打包 macOS/Windows 的 OpenCV、OpenBLAS、JavaCPP native jar。测试过 `opencv-platform:4.10.0-1.5.11` 在 macOS arm64 上会因为 `libOrbbecSDK.1.9.dylib` 缺失导致 `jniopencv_videoio` native load 失败，所以不要盲目升级这个版本。

## 4. 协议

每条消息是一行文本：

```text
VOCAGATE 1
STATE OPEN <android_elapsed_ms> <score>
STATE PRE_OPEN <android_elapsed_ms> <score>
STATE BLOCKED <android_elapsed_ms> <score>
PING <android_elapsed_ms> <OPEN|BLOCKED>
PAIR <code>
```

桌面端只依赖 `STATE` 和 `PING`：

- `STATE PRE_OPEN`：立即按下配置键，用于降低 Wi-Fi 抖动导致的吃音；
- `STATE OPEN`：保持配置键按下；
- `STATE BLOCKED`：立即松开配置键；
- `PING`：刷新心跳时间；
- `PAIR`：Wi-Fi 连接的配对码；
- 连接断开、读取异常或超过超时时间未收到消息：松开配置键。

## 5. 安全规则

- 默认按键使用 Windows Java Robot 更稳定的 `CTRL+SHIFT+M`，并支持自定义组合键；
- 同一个 OPEN 不重复触发 key down；
- 同一个 BLOCKED 不重复触发 key up；
- 切换按键时先松开旧按键；
- 桌面端停止监听、窗口退出、手机断连或心跳超时时，必须强制松开；
- macOS 需要给 Java/终端或打包后的 app 开启“辅助功能”权限，否则系统会拦截模拟按键。

## 6. 第一版范围

包含：

- Android 端发送 OPEN/BLOCKED 状态；
- 桌面端监听 TCP 端口；
- 桌面端可选择按键；
- 桌面端可选择输入来源；
- 桌面端可枚举、选择、独立窗口预览并记住本机摄像头；
- 桌面端有可替换 mouth-region provider 插槽，并包含 OpenCV YuNet provider、legacy OpenCV Haar provider 与中心 fallback；
- 桌面端可显示本机摄像头识别分数，并把本机摄像头结果直接接入按键控制；
- Android 与 desktop 本机摄像头共用 `gate-core` 状态机；
- 桌面端显示连接状态、当前状态、最近事件；
- 断连和超时强制松键；
- 纯 Java 核心烟测。

暂不包含：

- 真正的虚拟麦克风；
- 完整 68 点嘴唇轮廓模型；
- 音频环形缓冲；
- 会议软件状态同步；
- 历史日志图表；
- 打包安装器。

## 7. 后续调试方向

电脑端闭环跑通后，可以把以下信息记录成时间线：

- 手机端 `score`、`mouth`、`rhythm`；
- `PRE_OPEN`、`OPEN`、`HOLD` 状态变化；
- Android 发送事件时间；
- 电脑收到事件时间；
- 电脑 key down / key up 时间。

这些数据可以区分“吃音”到底来自识别触发太晚、状态机确认太慢、USB 链路慢，还是目标软件自身响应慢。
