# VocaGate Release Packaging v1 设计

**日期：** 2026-07-11

**状态：** 已完成方案讨论，等待书面规格确认

## 1. 背景

当前 VocaGate 的 Android 端可生成调试 APK；桌面端可生成 `desktop.zip`，但该 ZIP 仍要求用户通过脚本准备 Python、MediaPipe 模型和部分电脑连接配置。它适合开发测试，不适合作为普通用户的安装入口。

Telemetry v0 所需的“安装、首次启动、激活、留存”数据，必须以一个可以独立安装、独立启动的正式发布物为基础。否则开发环境、脚本、Java/Python 缺失和用户真实的产品使用会混在同一条统计路径中，数据没有产品判断价值。

本规格先完成 Release Packaging v1。Telemetry v0、账号、支付、Care 会员和自动更新授权均不属于本阶段实现范围。

## 2. 目标

1. Android 用户可下载并安装签名 Release APK，不需要 Android Studio、Gradle 或 ADB 安装命令。
2. macOS Apple Silicon 用户可安装 DMG，Windows x64 用户可安装 EXE，二者均内置 Java 运行时、MediaPipe sidecar、模型和当前平台原生视觉库。
3. 普通用户不需要单独安装 Java、Python、pip、MediaPipe 或执行任何 VocaGate 脚本。
4. 第一次启动提供可理解的识别引擎、摄像头、键盘控制和手机连接引导。
5. Wi-Fi 是手机连接的默认普通用户路径，使用自动发现和六位码确认；USB 低延迟模式作为正式支持的可选增强，电脑侧不再要求用户下载 Platform-Tools 或运行脚本。
6. 每次发布有固定命名、版本、校验和、变更说明和可重复验证步骤。
7. 打包边界为后续 Telemetry v0 提供可靠的“安装版本”和“首次启动”基线。

## 3. 非目标

本阶段不实现以下内容：

- Telemetry v0 上传、CloudBase 接收服务、数据保留或统计仪表盘。
- 用户账号、邮箱登录、支付、Care 权益、设备管理或自动续费。
- App 自动更新器、差分下载、回滚下载器或付费更新通道。
- iOS、Linux、macOS Intel 的首发正式支持。
- 绕过 Android USB 调试、开发者选项或系统 USB 授权。
- 消除 macOS Gatekeeper 或 Windows SmartScreen 的未签名预览版提示。

## 4. 已确认的产品决策

| 决策 | v1 结论 |
|---|---|
| Android 发布物 | 签名通用 Release APK |
| macOS 发布物 | Apple Silicon DMG |
| Windows 发布物 | x64 EXE 安装器 |
| 桌面运行时 | 安装包内置最小 Java 运行时 |
| 人脸识别 | 安装包内置 MediaPipe sidecar 与固定模型 |
| 连接默认值 | Wi-Fi 自动发现、六位码确认；发现失败时二维码和手动连接兜底 |
| 低延迟连接 | USB，内置经许可审计的最小 ADB runtime 自动建立 reverse，但仍需要手机开启 USB 调试并确认 RSA 授权 |
| 签名策略 | Android 首发必须签名；macOS/Windows 先允许未签名预览包，后续补齐商店外代码签名 |
| 发布位置 | GitHub Release 附件；不将二进制安装包提交入 Git |
| Telemetry | 明确推迟到安装包可用之后 |

## 5. 发布物矩阵

| 平台 | 架构 | 首发文件 | 构建主机 | 运行前置条件 |
|---|---|---|---|---|
| Android | `arm64-v8a`、`armeabi-v7a` | `VocaGate-<version>-android.apk` | macOS 或 CI | Android 6.0/API 23 及以上，允许安装 APK |
| macOS | `arm64` | `VocaGate-<version>-macos-arm64.dmg` | Apple Silicon Mac 或 macOS ARM CI | macOS 13 及以上 |
| Windows | `x86_64` | `VocaGate-<version>-windows-x64.exe` | Windows x64 或 Windows CI | Windows 10 22H2 或 Windows 11 |

macOS Intel 将在 v1.1 以单独的 `VocaGate-<version>-macos-x64.dmg` 交付。不得把 ARM64、x64 和 Windows 原生库打进同一个“万能”桌面包。

## 6. 总体结构

```text
Git 源码
    |
    +-- Android release build
    |       |
    |       +-- 签名 APK
    |
    +-- macOS native build
    |       |
    |       +-- Java runtime image
    |       +-- macOS OpenCV native libraries
    |       +-- macOS MediaPipe sidecar bundle
    |       +-- fixed face_landmarker.task
    |       +-- DMG
    |
    +-- Windows native build
            |
            +-- Java runtime image
            +-- Windows OpenCV native libraries
            +-- Windows MediaPipe sidecar bundle
            +-- fixed face_landmarker.task
            +-- EXE installer

GitHub Release
    |
    +-- 3 个平台安装文件
    +-- SHA256SUMS.txt
    +-- RELEASE_NOTES.md
```

桌面应用在安装后的目录中只从相对路径加载模型、sidecar 和平台库；运行时不得从网络下载模型、执行 pip 或创建 `.venv-mediapipe`。

## 7. Android Release APK

### 7.1 签名与版本

- `applicationId` 保持 `com.vocagate.demo`，避免测试用户更新时被视为另一个应用。
- `versionCode` 单调递增；`versionName` 使用公开语义版本，例如 `0.2.0`。
- 生成单独的 release keystore；其文件、别名密码、keystore 密码和本地属性文件加入 `.gitignore`。
- CI 未来使用受保护的密钥变量；v1 可先用受控本地环境构建。
- 未配置 release keystore 时，发布任务必须明确失败，不得静默回退为 debug 签名。
- 当前测试者已安装的 debug APK 使用 Android debug 证书，不能被首个 release APK 覆盖。首发 Release Notes 必须要求测试者先卸载旧测试版，再安装 release APK；该操作会清除旧应用沙盒中的录音测试文件和本地设置，v1 不承诺迁移它们。

### 7.2 APK 内容与安装

- 继续使用当前 CameraX、ML Kit Face Detection 和 Java gate-core；不增加安装后动态代码下载。
- 构建通用 APK，包含首发支持的两个 ABI。
- Release 输出不覆盖开发用 `app-debug.apk`，避免用户误取调试包。
- 发布说明写明：从 GitHub 下载 APK 后，Android 会要求用户确认“允许此来源安装应用”；这不是 USB 调试。

### 7.3 Android 首次启动

首次启动按必要性请求权限：

1. 摄像头权限是视觉门控必要权限。
2. 麦克风权限仅服务于已启用的音频辅助；用户拒绝后视觉门控仍可运行，但音频辅助关闭并展示原因。
3. 手机连接设置中明确区分 Wi-Fi 与 USB。
4. 发布安装流程不展示 Gradle、ADB 或脚本命令。

## 8. 桌面运行时和安装器

### 8.1 Java 运行时

- 使用 JDK 21 的 `jlink` 构建最小运行时镜像，再传给 `jpackage`。
- 最小运行时仅包含桌面程序需要的 Java 模块；不依赖用户电脑上的 `JAVA_HOME`、`java` 或 Gradle。
- 每个平台包中的运行时与构建机目标架构匹配。
- 运行时版本写入构建元数据和“关于”页面，便于排查问题。

### 8.2 jpackage 产物

- macOS 使用 `jpackage --type dmg` 生成 `VocaGate.app` 和 DMG。
- Windows 使用 `jpackage --type exe` 生成可交互 EXE 安装器。
- 安装包名称、应用显示名、版本、图标、厂商和应用标识使用同一份发布元数据。
- Windows EXE 生成需要 Windows 构建机及对应打包工具；macOS DMG 必须在 macOS 构建机上生成。不得宣称可在一台 Mac 上直接产出可验证的 Windows EXE。

### 8.3 原生库分离

当前 Gradle 运行时依赖同时列出 macOS ARM、macOS x64 和 Windows x64 的 JavaCPP/OpenCV 原生包。Release Packaging v1 必须引入 `targetPlatform` 构建属性，以目标平台选择唯一一组原生运行时依赖。

例如：

- `macos-arm64`：只包含 `macosx-arm64` OpenCV、OpenBLAS、JavaCPP。
- `windows-x64`：只包含 `windows-x86_64` OpenCV、OpenBLAS、JavaCPP。
- 后续 `macos-x64`：只包含 `macosx-x86_64` 变体。

构建验证必须检查产物中不包含其他平台的原生动态库，控制包体并降低加载错误风险。

## 9. MediaPipe Sidecar 内置化

### 9.1 当前问题

当前 `setup_mediapipe_sidecar.sh` / `.bat` 需要用户提供 Python、创建虚拟环境、联网安装 `mediapipe` 和 `numpy`，并从模型服务下载 `face_landmarker.task`。这不符合普通安装包要求，也会让首次启动不确定、缓慢且难以支持离线使用。

### 9.2 v1 方案

- 在每个目标构建机上将 `vocagate_mediapipe_sidecar.py` 打为当前平台的原生目录包，使用 PyInstaller `--onedir`，而不是会在第一次运行时解压的 `--onefile`。
- sidecar 二进制和运行库放入安装器，例如 `app/sidecar/`。
- `face_landmarker.task` 使用固定版本、固定下载 URL、SHA-256 和构建记录；构建阶段下载并校验，运行阶段不下载。
- 版本控制中维护模型锁定文件，例如 `desktop/src/main/python/models/face_landmarker.lock.json`，写入模型标识、准确版本、不可变下载 URL、SHA-256 和许可来源；构建任务拒绝使用 `latest` URL 或缺少哈希的模型输入。
- Java 主程序通过安装目录相对路径启动 sidecar，不再读取 `.venv-mediapipe` 或要求 `VOCAGATE_PYTHON`。
- 启动参数增加仅执行自检的模式，例如 `--healthcheck`；主程序首次启动前用它验证 sidecar 与模型兼容性。
- sidecar、Python 运行时、MediaPipe 依赖及模型许可信息写入 `THIRD_PARTY_NOTICES`。

### 9.3 故障行为

| 情况 | 用户可见结果 | 处理 |
|---|---|---|
| sidecar 文件缺失 | “识别引擎不完整” | 禁用本机摄像头启动，保留手机 VocaGate 模式 |
| 模型哈希不匹配 | “识别模型校验失败” | 不启动 sidecar，记录本地诊断信息 |
| sidecar 健康检查失败 | “识别引擎无法启动” | 显示错误编号与导出诊断入口 |
| 本机摄像头权限被拒绝 | “未获得摄像头权限” | 提供系统设置引导，不重新安装 |
| MediaPipe 单帧失败 | 不终止应用 | 维持 gate 安全关闭，并在状态区显示可理解提示 |

手机 VocaGate 模式不依赖桌面 MediaPipe sidecar；因此 sidecar 损坏时仍可以继续使用手机作为识别端。

## 10. 首次启动体验

### 10.1 桌面端顺序

```text
启动桌面应用
    ↓
本地健康检查（Java、模型、sidecar、原生库）
    ↓
选择输入来源
    ├── 本机摄像头：请求或引导摄像头权限
    └── 手机 VocaGate：进入连接方式设置
            ├── Wi-Fi 配对（默认）
            └── USB 低延迟（可选）
    ↓
选择按住按键并开始运行
```

健康检查正常时不弹出技术术语或命令；只在失败时给出简短问题说明和可复制诊断信息。

### 10.2 macOS 权限

- 应用包的 `Info.plist` 写入清晰的摄像头用途说明。
- 首次选择本机摄像头时触发系统摄像头授权。
- 首次需要按键控制时，应用解释为何需要“辅助功能”权限，并提供打开对应系统设置的位置。
- 没有辅助功能权限时，识别预览可用，但按钮保持禁用并显示“需要允许键盘控制”。

### 10.3 Windows 权限

- 不要求管理员权限安装或运行。
- 需要控制以管理员身份运行的游戏时，状态提示“请以管理员身份启动 VocaGate”；不在后台提升权限。
- 摄像头被其他程序独占时，提供“摄像头被占用”的明确提示。

### 10.4 Android 手机连接

Wi-Fi 是首选普通用户路径：

1. 桌面端在仅限局域网的本地服务上发布 mDNS 服务记录，记录协议版本、端口和一次性配对 nonce；不把配对码或用户数据放进广播文本。
2. Android 端通过 Android NSD 扫描兼容桌面端，用户从发现列表选中电脑并确认桌面端显示的六位配对码。
3. 成功确认后，Android 端持久化已验证的主机、公钥/nonce 衍生凭据和端口；以后同一局域网内自动重连。
4. mDNS 被 AP 隔离、防火墙或路由器阻断时，桌面端显示二维码；Android 端在独立配对页扫描二维码。手动 IP、端口和配对码保留为高级兜底，不作为首次安装说明。
5. 手机与电脑仍必须处于可互相访问的局域网；失败提示区分“未发现桌面端”“发现后无法连接”“配对码错误”和“局域网隔离”。

USB 是低延迟增强路径：

1. 用户在 Android 开发者选项中启用 USB 调试并通过 USB 连接；首次由 Android 系统显示 RSA 授权确认，VocaGate 不尝试绕过该确认。
2. 桌面安装包携带固定版本的最小 ADB runtime，不携带 fastboot、刷机或文件系统工具。runtime 只包含 `adb` 及最终制品审计确认的必要 DLL/dylib。
3. 每个 ADB runtime 包都附带 `adb-manifest.json`、SHA-256、固定 AOSP/Platform-Tools 来源 revision、Apache-2.0、对应 `NOTICE` 和所有直接依赖许可；构建任务拒绝缺少这些材料的 ADB 输入。
4. VocaGate 使用仅监听 `localhost` 的专用 ADB server socket，例如 `tcp:localhost:27184`，不执行针对系统默认 5037 server 的 `adb kill-server`。所有设备操作都显式指定序列号。
5. 桌面端处理 `device`、`unauthorized`、`offline`、`multiple` 和 `not found` 状态：分别显示已连接、等待手机确认、重新插线/重试、选择设备、检查数据线/USB 调试/Windows OEM 驱动。
6. 已授权设备自动执行 `adb reverse tcp:<phonePort> tcp:<desktopPort>`，并在重新插线或 reverse 丢失后按退避策略恢复。拔线后优先回退到已验证的 Wi-Fi 通道。

安装器能消除用户单独下载 Platform-Tools 和电脑侧脚本，但不得伪装或绕过 Android 对 USB 调试、RSA 授权以及 Windows 可能需要 OEM USB 驱动的系统边界。

## 11. 发布目录与元数据

构建产物放在未跟踪的发布目录并上传到 GitHub Release：

```text
release/<version>/
├── VocaGate-<version>-android.apk
├── VocaGate-<version>-macos-arm64.dmg
├── VocaGate-<version>-windows-x64.exe
├── SHA256SUMS.txt
└── RELEASE_NOTES.md
```

`SHA256SUMS.txt` 包含每个发布文件的 SHA-256。`RELEASE_NOTES.md` 至少说明：版本号、支持平台、主要变更、已知限制、Wi-Fi/USB 连接要求、未签名预览包的系统提示和升级说明。桌面安装器还必须内置 `THIRD_PARTY_NOTICES`、最小 ADB runtime 的 `adb-manifest.json`，以及可机读的第三方组件清单（SBOM）。

GitHub Release 是下载入口，不等于未来的算法更新服务。Release Packaging v1 中，现有静态更新服务只允许提供已签名的算法 `profile` 参数更新；桌面和 Android 客户端必须拒绝远程 `model` 资产，且不得替换包内的 MediaPipe `face_landmarker.task` 或 ML Kit 依赖。模型、sidecar 和其许可的任何变更必须随新的安装包发布。静态更新服务可以暂时保留未来 `model` 资产的样例，但 v1 安装包不得请求、下载或应用它们，也不承担安装器上传或 Telemetry 接收。

## 12. 版本与升级规则

- Android `versionCode` 每个公开版本递增，`versionName` 与桌面发布版本对应。
- macOS 和 Windows 安装器应用版本使用相同语义版本。
- 0.x 阶段允许功能性破坏变化，但 Release Notes 必须说明配置迁移影响。
- v1 不做应用内自动更新。用户通过 GitHub Release 下载更高版本安装包。
- 已安装 release APK 的用户可由同一 release keystore、较高 `versionCode` 的新 APK 覆盖升级；debug APK 到首个 release APK 按 7.1 的卸载重装规则执行。桌面端高版本安装器必须保留用户设置目录。
- 包内模型或 sidecar 更新需要写入 Release Notes 和 `THIRD_PARTY_NOTICES` 变更记录。

### 12.1 卸载与本地数据

- macOS 和 Windows 的卸载只移除应用程序、内置运行时和 sidecar，默认保留用户设置；重新安装同一平台的 VocaGate 时应能恢复摄像头选择、按键设置和连接偏好。
- 桌面端 v1 不在卸载器中提供“同时删除所有设置”的选项，以避免普通用户更新或重装时误清空配置；未来可单独加入明确确认的“重置本地数据”功能。
- Android 卸载会由系统删除应用沙盒，因此会清空 VocaGate 设置和录音测试文件；首个 release APK 的 debug 迁移说明必须明确此行为。
- Release Notes 不能把桌面端与 Android 的卸载数据行为描述成相同。

## 13. 签名、公证与安全边界

### 13.1 Android

Android Release APK 必须由长期保存的 release keystore 签名。私钥不上传 GitHub、不随安装包发布，也不放入更新服务器。

### 13.2 macOS

v1 可先发布未签名预览 DMG。用户可能需要按系统提示完成首次打开；发布说明必须如实说明这一点。GitHub 上的 macOS/Windows 安装器在代码签名完成前必须标记为 Pre-release，不得标记为稳定正式版。

正式公开发行前，需要：

1. Apple Developer Program 证书。
2. Developer ID 签名。
3. Apple Notarization。
4. Staple 公证票据到 DMG 或应用包。

代码签名不是功能安装器的前置条件，但它是“无明显 Gatekeeper 警告”的前置条件。

### 13.3 Windows

v1 可先发布未签名预览 EXE。Windows SmartScreen 可能显示信誉提示；不得以“无毒”替代对该提示的解释。GitHub Release 标记规则与 13.2 相同。

正式公开发行前购买或取得 Windows 代码签名证书，并在构建后签名安装器和主可执行文件。任何对运行时或 sidecar 的修改都必须在签名流程前完成。

### 13.4 构建与发布安全

- 发布构建使用固定依赖版本和锁定的模型哈希。
- 私钥和云密钥只从受保护环境变量或本机安全存储读取。
- 发布前生成校验和后再次验证一次。
- GitHub Release 仅上传经过本地验证的产物。
- 不将用户数据、调试录像、音频、摄像头帧或个人设备日志打入发布包。

### 13.5 最小 ADB runtime 审计和进程隔离

- ADB runtime 的首发版本固定，不跟随开发机器上 `adb` 的版本漂移；升级 ADB 必须作为一次显式发布输入变更并重新审计。
- 优先从固定 AOSP revision 构建；若首发采用官方 Platform-Tools 中的制品，必须保留该制品来源、版本、哈希和完整 NOTICE，并核查最终 `adb` 的直接动态依赖。
- 构建验证在 macOS 使用 `otool -L`、Windows 使用等价依赖检查，确保安装器仅携带经过审计的运行时文件。
- ADB server 不使用 `-a`，只绑定 loopback socket。应用退出时只停止由 VocaGate 端口启动的 server，不操作其他应用的默认 ADB server。
- Windows 未识别设备时提供 OEM USB 驱动说明链接，但不得尝试静默安装厂商驱动或承诺所有机型免驱。

## 14. 构建脚本和配置边界

Release Packaging v1 将增加独立、可复用的构建入口，而不复用开发运行脚本：

| 入口 | 责任 |
|---|---|
| `scripts/package_android_release.sh` | 检查 release keystore 配置，构建并复制签名 APK |
| `scripts/package_macos_arm64.sh` | 生成 sidecar bundle、目标平台 Java 分发包、jlink runtime、DMG 和校验和 |
| `scripts/package_windows_x64.bat` | 生成 Windows sidecar bundle、目标平台 Java 分发包、EXE 和校验和 |
| `scripts/prepare_adb_runtime.sh` / `.bat` | 下载或读取固定 ADB 输入，验证来源/哈希，复制最小 runtime、NOTICE、许可证和 manifest 到安装 staging 目录 |
| `scripts/verify_release_artifacts.sh` | 校验命名、哈希、内置运行时、sidecar、模型和发布说明 |

具体脚本文件名可随现有项目命名调整，但责任边界保持不变。`run_desktop.sh`、`setup_mediapipe_sidecar.*` 和 `setup_pc_link.*` 保留为开发诊断工具，不作为普通用户安装说明。

## 15. 发布验收

### 15.1 静态验收

- 每个产物版本名一致，且存在于 `SHA256SUMS.txt`。
- 重新计算的 SHA-256 与发布清单一致。
- Android APK 显示为 release 签名，不是 debug 签名。
- macOS 包只包含 ARM64 原生库；Windows 包只包含 x64 原生库。
- 桌面包中存在 Java runtime、sidecar、模型、许可证说明和主程序。
- 桌面包中没有 `.venv-mediapipe`、pip cache、用户机器路径或其他平台原生库。

### 15.2 macOS 真机验收

- 从 DMG 拖入“应用程序”后可启动，无需安装 Java/Python 或执行脚本。
- sidecar `--healthcheck` 成功，且本机摄像头可进入 MediaPipe 识别。
- 摄像头授权、辅助功能引导、按键控制和停止按钮符合预期。
- Wi-Fi 自动发现、六位码确认和已配对自动重连能与已安装 Android APK 完成一次门控；mDNS 被阻断时二维码和高级手动入口可完成配对。
- USB 在已授权调试的设备上使用内置最小 ADB runtime 自动建立 reverse，且不影响同机 Android Studio 或 scrcpy 的默认 ADB server。

### 15.3 Windows 真机验收

- EXE 安装完成后可从开始菜单启动，无需安装 Java/Python 或执行脚本。
- sidecar `--healthcheck` 成功，本机摄像头、按键控制、Wi-Fi 自动配对和 USB reverse 按预期工作。
- 在具备 OEM 驱动的真机上验证内置 ADB runtime；在未识别的 Windows 设备上验证错误提示不要求用户下载 Platform-Tools，且只指向设备厂商驱动支持页。
- 卸载后程序文件移除，用户设置是否保留必须按安装器提示和 Release Notes 的约定验证。

### 15.4 Android 真机验收

- 新设备可从 Release APK 安装并完成摄像头权限引导。
- 已安装 release 旧版本时，可由同一 release 签名的高 `versionCode` APK 覆盖升级；已安装 debug 测试版时，验收 Release Notes 的卸载重装说明和首次配置流程。
- macOS 和 Windows 卸载后重新安装，验证用户设置按 12.1 保留；Android 卸载后重新安装，验证应用沙盒设置与录音测试文件按系统行为清空并在说明中如实披露。
- 对含有 `kind: model` 的远程更新清单执行检查，验证 v1 客户端拒绝下载或应用模型，只允许已签名 profile 生效。
- Wi-Fi 配对和 USB 低延迟模式都能连接已安装的桌面端。
- 拒绝麦克风权限时，纯视觉门控继续可用且音频辅助清楚显示为关闭。

## 16. 测试策略

1. 单元测试覆盖发布元数据解析、平台选择和安装目录路径解析。
2. 构建脚本 smoke test 检查必要环境缺失时给出明确失败，而非生成不完整产物。
3. Sidecar smoke test 用固定模型执行 `--healthcheck` 并验证协议握手。
4. 静态包检查测试验证无 Python 虚拟环境、无平台混包、无私钥和无用户目录路径。
5. ADB runtime 审计测试校验固定 revision、二进制哈希、NOTICE、许可证、SBOM 和平台动态依赖清单；缺一项则拒绝产包。
6. 自动连接测试覆盖 mDNS 发现、六位码确认、二维码兜底、连接断开和 Wi-Fi/USB 通道切换。
7. 每个平台至少有一台真实机器完成安装、启动、摄像头、按键和手机连接冒烟测试。
8. 发布前运行现有 gate-core、Android 和桌面端回归测试，避免打包改动破坏识别逻辑。

## 17. 实施阶段

### 阶段 1：发布元数据与 Android Release

- 增加统一版本元数据。
- 配置 Android release signing 和发布 APK 任务。
- 建立 Release 输出目录、校验和和说明模板。

### 阶段 2：平台依赖分离与 sidecar bundle

- 引入 `targetPlatform`，拆分桌面原生依赖。
- 固定模型版本和哈希。
- 在 macOS ARM 和 Windows x64 各自构建并验证 `--onedir` sidecar bundle。

### 阶段 3：jlink 和 jpackage

- 为 macOS ARM 产出 DMG。
- 为 Windows x64 产出 EXE。
- 将 Java runtime、sidecar、模型、许可证和经审计的最小 ADB runtime 纳入安装布局。

### 阶段 4：运行时路径和首次启动

- 主程序优先解析安装目录资源，开发模式仍允许使用本地脚本。
- 新增健康检查、Wi-Fi 自动发现/二维码兜底、权限引导和 USB 自动重试/回退体验。
- 移除普通用户说明中的脚本依赖。

### 阶段 5：真机发布验收

- Android、macOS ARM、Windows x64 分别安装验证。
- 生成 GitHub Release 草稿附件与校验和。
- Telemetry v0 规格重新进入下一轮设计和实施。

## 18. 风险与应对

| 风险 | 应对 |
|---|---|
| PyInstaller 与 MediaPipe 在目标系统打包失败 | 每个平台本机构建并执行 healthcheck；不跨平台复制 sidecar |
| 桌面包体过大 | 目标平台原生库分离；使用 jlink；发布前记录包体大小 |
| macOS Gatekeeper / Windows SmartScreen 提示 | v1 标为预览包并说明；公开发行前补代码签名和公证 |
| USB 路径仍要求 USB 调试或 Windows OEM 驱动 | Wi-Fi 自动配对作为默认用户路径；USB 清晰标注为低延迟增强并给出系统级引导 |
| ADB runtime 许可或动态依赖不完整 | 固定来源 revision，保留 NOTICE/许可证/SBOM/哈希，构建时执行平台依赖审计，缺项拒绝发布 |
| 局域网 mDNS 被路由器或 AP 隔离 | 二维码与高级手动连接兜底；错误提示区分发现失败与配对失败 |
| 游戏管理员权限拦截按键 | Windows 状态区提示以管理员身份运行 VocaGate |
| 更新安装器覆盖用户设置 | 发布前验证设置目录保留；Release Notes 写明迁移行为 |
| 模型下载源变化或失效 | 固定模型版本、镜像构建输入、SHA-256 校验和本地缓存 |
| 签名密钥丢失 | 发布密钥离线备份；发布前建立密钥恢复和访问记录 |

## 19. 完成定义

当满足以下条件时，Release Packaging v1 完成：

1. 三个首发文件可从同一 GitHub Release 下载，命名、版本、校验和和说明一致。
2. Android 可安装签名 Release APK；macOS ARM 可安装 DMG；Windows x64 可安装 EXE。
3. 三端普通用户均不需要安装 Java、Python、MediaPipe、pip 或运行 VocaGate 脚本。
4. 桌面端安装包内置并成功启动 MediaPipe sidecar 与固定模型。
5. Wi-Fi 手机连接可在不启用 Android 开发者选项的情况下自动发现、确认配对并自动重连。
6. USB 低延迟在用户已授权 USB 调试的情况下可由内置、经审计的最小 ADB runtime 自动建立电脑侧连接，不干扰其他 ADB server。
7. 所有发布验收项目有真实命令输出或真机测试记录。
8. Telemetry 未被提前加入客户端或服务器。
