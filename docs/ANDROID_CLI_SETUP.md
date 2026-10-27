# Android CLI 部署步骤

这份文档用于不打开 Android Studio 的情况下，把 VocaGate Android demo 构建、安装并启动到安卓手机。

## 1. Mac 端需要的工具

最小工具链：

```text
Android SDK Command-line Tools
Android SDK Platform Tools，也就是 adb
Android SDK Build Tools
Android Platform android-36
Gradle，或者项目里的 gradlew
JDK 17
```

如果你已经装了 Android Studio，也可以只用它安装 SDK，然后平时不开 Android Studio。

## 2. 推荐目录

建议把 Android SDK 放在：

```text
~/Library/Android/sdk
```

然后在 shell 配置里加入：

```bash
export ANDROID_HOME="$HOME/Library/Android/sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
```

如果你使用的是 bash，通常写入：

```text
~/.bash_profile
```

如果你使用的是 zsh，通常写入：

```text
~/.zprofile
```

写完后重新打开终端，或运行：

```bash
source ~/.bash_profile
```

## 3. 安装 Android SDK 包

安装好 command-line tools 后，运行：

```bash
sdkmanager --install "platform-tools" "platforms;android-36" "build-tools;36.0.0"
sdkmanager --licenses
```

`sdkmanager --licenses` 会让你确认协议，按提示输入 `y`。

## 4. 安装 Gradle

如果 Mac 有 Homebrew：

```bash
brew install gradle
```

之后检查：

```bash
gradle -v
```

如果不想全局安装 Gradle，也可以之后生成 Gradle Wrapper。当前这台机器没有 Gradle，所以项目暂时没有提交 `gradlew`。

## 5. 手机端开启无线调试

在安卓手机上：

1. 打开设置。
2. 进入“关于手机”。
3. 连续点击“系统版本 / MIUI 版本 / HyperOS 版本”直到出现开发者提示。
4. 回到设置，进入“开发者选项”。
5. 开启“USB 调试”。
6. 开启“无线调试”。
7. 确认 Mac 和手机在同一个 Wi-Fi。

不同安卓系统入口名字略有差异，小米手机通常在：

```text
设置 -> 更多设置 -> 开发者选项
```

## 6. 无线配对

在手机的“无线调试”页面中，点击：

```text
使用配对码配对设备
```

手机会显示：

```text
IP 地址和端口，例如 192.168.1.23:37123
6 位配对码
```

在 Mac 终端运行：

```bash
adb pair 192.168.1.23:37123
```

然后输入手机显示的 6 位配对码。

注意：这里的端口是“配对端口”，只用于 `adb pair`。

## 7. 无线连接

配对成功后，回到手机“无线调试”主页面，查看“IP 地址和端口”。这里通常会显示另一个端口，例如：

```text
192.168.1.23:41289
```

在 Mac 终端运行：

```bash
adb connect 192.168.1.23:41289
```

然后检查：

```bash
adb devices
```

期望看到：

```text
192.168.1.23:41289    device
```

注意：`adb pair` 的端口和 `adb connect` 的端口经常不是同一个。

## 8. 构建、安装、启动 demo

在项目根目录运行：

```bash
scripts/run_android.sh
```

如果连接了多个设备：

```bash
scripts/run_android.sh --device 192.168.1.23:41289
```

如果只想安装，不想自动启动：

```bash
scripts/run_android.sh --no-start
```

脚本会做这些事：

```text
检查 adb
检查 Gradle 或 gradlew
检查已授权设备
运行 :app:installDebug
启动 com.vocagate.demo/.MainActivity
```

## 9. 常见问题

### adb 找不到

检查：

```bash
which adb
echo $ANDROID_HOME
ls "$ANDROID_HOME/platform-tools/adb"
```

### gradle 找不到

检查：

```bash
which gradle
gradle -v
```

### adb devices 显示 unauthorized

看手机屏幕，通常会弹出“允许 USB 调试 / 无线调试”的授权弹窗。允许后再运行：

```bash
adb devices
```

### pair 成功但 connect 失败

常见原因：

- Mac 和手机不在同一个 Wi-Fi；
- 路由器禁止局域网设备互联；
- 用错端口，把配对端口拿去 connect；
- 手机锁屏后无线调试断开；
- 手机切换了网络。

可以重新打开手机“无线调试”页面，再看一次当前连接端口。

### 企业 Wi-Fi 或酒店 Wi-Fi 不通

这类网络经常禁用设备互联。可以换家庭 Wi-Fi，或让手机开热点给 Mac 连接。
