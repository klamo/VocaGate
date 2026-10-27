# 麦哨 / VocaGate MVP 技术文档

> 版本：v0.1
> 日期：2026-05-20
> 来源：`视觉麦控器_麦哨_项目初期文档_v0.1.md`
> 覆盖阶段：MVP-0、MVP-1

## 1. 文档目的

本文档用于指导“麦哨 / VocaGate / Visual Mic Gate”的第一轮 MVP 开发。

MVP 的目标不是做完整产品，也不是做唇语识别、降噪、回声消除或游戏集成，而是验证一个核心判断：

```text
能否用低算力视觉特征判断用户是否正在说话，并稳定控制麦克风门控状态。
```

第一版应优先保证：

- 算法链路简单；
- 状态机行为可解释；
- 日志足够详细；
- 可以快速调参；
- 可以复现实验结果。

## 2. MVP 范围

### 2.1 MVP-0：离线视觉验证版

MVP-0 只做视觉识别和状态机验证，不接管真实麦克风。

必须包含：

- 打开本地摄像头；
- 检测单个人脸；
- 提取嘴部关键点；
- 计算嘴部开合比例和运动特征；
- 逐帧计算 `speak_score`；
- 运行麦克风门控状态机；
- 实时显示摄像头画面、嘴部关键点、分数和状态；
- 将每帧结果写入 CSV；
- 支持手动标记误判样本。

### 2.2 MVP-1：麦克风门控模拟版

MVP-1 在 MVP-0 基础上增加“模拟麦克风放行”状态，但仍不要求实现虚拟麦克风驱动。

必须包含：

- 输出 `gate_open=true/false`；
- 在 UI 中明确显示“放行麦克风 / 阻断麦克风”；
- 支持基础阈值配置；
- 记录状态切换日志；
- 支持针对正常说话、沉默、喝水、哈欠、大笑、反光、低头、转头、低光等场景测试。

### 2.3 第一版明确不做

- 不做语义级唇语识别；
- 不做大模型或云端识别；
- 不做训练数据集和模型训练；
- 不做虚拟麦克风驱动；
- 不接入 Discord、OBS 或游戏客户端；
- 不支持多人脸；
- 不做商业化 UI；
- 不做硬件样机。

## 3. 成功标准

MVP 初步成功的标准：

- 用户没说话时，误开麦明显减少；
- 喝水、哈欠、大笑、键盘声、音箱外放等场景不应频繁开麦；
- 用户开始说话到开麦的延迟目标控制在 300 至 500ms 内；
- 用户停止说话后，状态机能保持 500 至 800ms，避免切掉句尾；
- 在 320x240 或 640x360、10 至 15 FPS 下运行流畅；
- CSV 日志能支持后续复盘和调参。

第一版不追求完美准确率，重点是验证技术链路和找到主要误判来源。

## 4. 推荐技术栈

```text
语言：Python
图像采集与处理：OpenCV
人脸关键点：MediaPipe Face Mesh 或 MediaPipe Face Landmarker
实时界面：OpenCV window
配置：YAML 或 TOML
日志：CSV
测试：pytest
```

MVP-0 建议先使用 OpenCV 窗口，不急着做复杂桌面 UI。这样可以把注意力集中在识别、分数、状态机和日志上。

## 5. 建议目录结构

```text
VocaGate/
  README.md
  docs/
    MVP_TECHNICAL_DESIGN.md
    TEST_PLAN.md
  config/
    default.yaml
  src/
    vocagate/
      __init__.py
      app.py
      config.py
      capture/
        camera_capture.py
      vision/
        face_tracker.py
        mouth_features.py
        occlusion_detector.py
      gate/
        score.py
        state_machine.py
      logging/
        event_logger.py
      ui/
        dashboard.py
  tests/
    test_mouth_features.py
    test_score.py
    test_state_machine.py
  data/
    logs/
    samples/
```

实际开发可以从更少文件开始，但模块边界应尽量保持清晰。

## 6. 运行链路

```text
Camera
  -> CaptureService
  -> FaceTracker
  -> MouthFeatureExtractor
  -> OcclusionDetector
  -> SpeakScoreEstimator
  -> GateStateMachine
  -> Dashboard
  -> EventLogger
```

每一帧都应生成一条统一的 `FrameDecision` 数据。UI 展示和 CSV 日志都从这条数据读取，避免“屏幕上看到的状态”和“日志里的状态”不一致。

## 7. 核心模块设计

### 7.1 CaptureService

职责：

- 打开摄像头；
- 设置分辨率和帧率；
- 读取帧；
- 给每帧附加时间戳；
- 在读取失败时给出清晰错误。

默认参数：

```text
camera_index = 0
width = 640
height = 360
fps = 15
```

后续应支持切换到 320x240，用于低算力和低延迟测试。

### 7.2 FaceTracker

职责：

- 检测当前帧中的人脸；
- 只选择一个主要人脸；
- 输出人脸关键点；
- 输出人脸存在和追踪稳定性；
- 当人脸缺失或不稳定时，给状态机提供关闭依据。

第一版策略：

- 只支持单人；
- 优先使用画面中最大或最居中的人脸；
- 多人同框暂不处理；
- 没有人脸时，状态机应进入 `SUSPEND` 或 `CLOSED`。

### 7.3 MouthFeatureExtractor

职责：

- 从人脸关键点中提取嘴部关键点；
- 计算嘴部上下开合距离；
- 计算嘴角宽度；
- 得到归一化的 `mouth_open_ratio`；
- 维护短时间窗口中的嘴部运动历史。

第一版候选特征：

```text
mouth_open
mouth_width
mouth_open_ratio
mouth_open_ratio_smooth
delta_mouth
mouth_motion_energy
mouth_motion_rhythm
long_open_duration_ms
```

MediaPipe Face Mesh 第一版候选点位：

```text
upper_lip = 13
lower_lip = 14
left_mouth_corner = 61
right_mouth_corner = 291
```

点位编号应集中放在一个常量文件或模块中，方便后续根据实际 MediaPipe API 调整。

### 7.4 OcclusionDetector

职责：

- 判断嘴部区域是否可信；
- 识别关键点缺失、跳变和异常几何；
- 对喝水、遮挡、长时间大张嘴等情况给出惩罚；
- 输出 `occlusion_penalty` 和 `long_open_penalty`。

MVP-0 不需要真正识别手或杯子，可以先使用代理信号：

- 嘴部关键点缺失；
- 人脸置信度下降；
- 嘴部关键点突然大幅跳变；
- 嘴部长时间大开但缺少说话节奏；
- 嘴部宽度或开合比例不符合正常范围。

### 7.5 SpeakScoreEstimator

职责：

- 将嘴部节奏、开合幅度、人脸稳定性和异常惩罚合成一个分数；
- 输出 0.0 到 1.0 的 `speak_score`；
- 保持公式透明，便于手动调参。

初始公式：

```text
speak_score =
  0.45 * mouth_motion_rhythm +
  0.25 * normalized_mouth_open +
  0.20 * frontal_face_score +
  0.10 * track_stability
  - 0.35 * occlusion_penalty
  - 0.25 * long_open_penalty
```

最终结果需要限制在 `[0.0, 1.0]`。

### 7.6 GateStateMachine

职责：

- 将逐帧 `speak_score` 转换成稳定的麦克风门控状态；
- 避免单帧误触发；
- 避免说话时频繁开关；
- 避免刚停顿就切掉句尾；
- 在视觉信号不确定时默认关闭。

状态列表：

```text
CLOSED
PRE_OPEN
OPEN
HOLD
COOLDOWN
SUSPEND
```

状态含义：

| 状态 | 含义 | 麦克风门控 |
|---|---|---|
| CLOSED | 用户未说话 | 阻断 |
| PRE_OPEN | 疑似开始说话，等待确认 | 阻断，后续可配合音频缓存 |
| OPEN | 已确认正在说话 | 放行 |
| HOLD | 刚停止说话，短暂保持 | 放行 |
| COOLDOWN | 刚关闭，防止抖动 | 阻断 |
| SUSPEND | 人脸、嘴部或置信度异常 | 阻断 |

### 7.7 Dashboard

职责：

- 显示摄像头画面；
- 绘制嘴部关键点；
- 显示 `mouth_open_ratio`；
- 显示 `speak_score`；
- 显示当前状态和 `gate_open`；
- 显示 FPS 和追踪稳定性；
- 支持手动标记样本。

最小键盘控制：

| 按键 | 行为 |
|---|---|
| `q` | 退出 |
| `m` | 标记当前帧 |
| `1` | 标记误开麦 |
| `2` | 标记误关麦 |
| `3` | 标记喝水或遮挡 |
| `4` | 标记哈欠或大笑 |

### 7.8 EventLogger

职责：

- 每处理一帧写入一条日志；
- 记录状态切换；
- 记录手动标记；
- 定期 flush，避免异常退出后日志丢失；
- 输出易于用表格工具查看的 CSV。

## 8. 状态机设计

### 8.1 初始参数

```text
open_threshold = 0.62
close_threshold = 0.38
pre_open_required_frames = 3
close_required_frames = 8
min_open_ms = 500
hold_ms = 700
cooldown_ms = 250
suspend_confidence_threshold = 0.45
```

这些只是第一轮调试参数，不是最终产品默认值。

### 8.2 状态切换表

| 来源状态 | 条件 | 目标状态 |
|---|---|---|
| CLOSED | `speak_score >= open_threshold` 连续 N 帧 | PRE_OPEN |
| PRE_OPEN | 分数持续高于阈值并满足确认帧数 | OPEN |
| PRE_OPEN | 分数跌破关闭阈值 | CLOSED |
| OPEN | 人脸或嘴部信号异常 | SUSPEND |
| OPEN | 达到最短开麦时间后，分数持续低于关闭阈值 | HOLD |
| HOLD | 分数重新高于开启阈值 | OPEN |
| HOLD | 保持时间结束 | COOLDOWN |
| COOLDOWN | 冷却时间结束 | CLOSED |
| 任意状态 | 人脸丢失、严重遮挡、关键点无效 | SUSPEND |
| SUSPEND | 追踪恢复且分数较低 | CLOSED |
| SUSPEND | 追踪恢复且分数连续较高 | PRE_OPEN |

### 8.3 防抖策略

- 开启阈值和关闭阈值分离；
- 开启需要连续多帧确认；
- 关闭需要连续多帧确认；
- `OPEN` 状态有最短保持时间；
- `HOLD` 用于保护句尾；
- `COOLDOWN` 用于防止频繁闪烁；
- `SUSPEND` 下默认阻断麦克风。

## 9. 特征计算

### 9.1 嘴部开合比例

```text
mouth_open = distance(upper_lip, lower_lip)
mouth_width = distance(left_mouth_corner, right_mouth_corner)
mouth_open_ratio = mouth_open / max(mouth_width, epsilon)
```

`mouth_open_ratio` 需要做短时间平滑，避免关键点抖动直接影响状态机。

### 9.2 嘴部运动节奏

说话通常不是“嘴一直张开”，而是短时间内连续开合。

第一版算法：

- 保存最近 300 至 500ms 的 `mouth_open_ratio`；
- 计算相邻帧变化量；
- 统计窗口内运动能量；
- 对“长时间大开但没有节奏”的情况降分。

### 9.3 长开口惩罚

哈欠、大笑、吃东西可能产生较大的嘴部开合，但不一定代表说话。

第一版策略：

- 当 `mouth_open_ratio` 超过大开口阈值时开始计时；
- 超过 600 至 900ms 后逐渐增加 `long_open_penalty`；
- 嘴部回到正常运动后逐渐衰减惩罚。

### 9.4 追踪稳定性

只有视觉信号稳定时才允许开麦。

可用信号：

- 是否检测到人脸；
- 是否拿到完整关键点；
- 嘴部关键点是否在画面内；
- 关键点是否发生不合理跳变；
- 如果能获取头部姿态，判断是否大致面向屏幕。

## 10. 日志设计

### 10.1 帧日志

建议路径：

```text
data/logs/session_<timestamp>_frames.csv
```

字段：

```text
timestamp_ms
frame_index
fps
face_present
face_confidence
track_stability
mouth_open
mouth_width
mouth_open_ratio
mouth_open_ratio_smooth
delta_mouth
mouth_motion_rhythm
normalized_mouth_open
frontal_face_score
occlusion_penalty
long_open_penalty
speak_score
state
gate_open
manual_marker
notes
```

### 10.2 状态切换日志

建议路径：

```text
data/logs/session_<timestamp>_transitions.csv
```

字段：

```text
timestamp_ms
frame_index
from_state
to_state
speak_score
reason
gate_open
```

### 10.3 会话元数据

建议路径：

```text
data/logs/session_<timestamp>_metadata.json
```

字段：

```text
camera_index
resolution
target_fps
actual_avg_fps
config_snapshot
start_time
end_time
operator_notes
```

## 11. 配置设计

建议配置文件：

```text
config/default.yaml
```

初始内容：

```yaml
camera:
  index: 0
  width: 640
  height: 360
  fps: 15

vision:
  max_faces: 1
  min_face_confidence: 0.45
  smoothing_alpha: 0.35
  rhythm_window_ms: 400

score:
  open_threshold: 0.62
  close_threshold: 0.38
  long_open_threshold: 0.42
  long_open_penalty_after_ms: 700

gate:
  pre_open_required_frames: 3
  close_required_frames: 8
  min_open_ms: 500
  hold_ms: 700
  cooldown_ms: 250

logging:
  output_dir: data/logs
  flush_every_frames: 30
```

每次运行时，应将配置快照写入 metadata，确保日志可复现。

## 12. 异常处理

| 异常 | MVP 行为 |
|---|---|
| 摄像头无法打开 | 显示错误并退出 |
| 读取帧失败 | 短暂重试，仍失败则退出 |
| 未检测到人脸 | 进入 `SUSPEND` 或 `CLOSED` |
| 嘴部关键点无效 | 进入 `SUSPEND` |
| 置信度过低 | 保持麦克风阻断 |
| 日志目录不存在 | 自动创建 |
| UI 窗口关闭 | 停止采集并 flush 日志 |

安全原则：

```text
不确定时，默认闭麦。
```

## 13. 测试策略

### 13.1 单元测试

必须覆盖：

- `mouth_open_ratio` 正确按嘴宽归一化；
- 嘴宽为 0 或极小时不会除零；
- `speak_score` 被限制在 0.0 到 1.0；
- 有节奏嘴部运动会提高分数；
- 遮挡惩罚会降低分数；
- 长时间大开口会降低分数；
- 单帧高分不会直接开麦；
- 连续高分会进入 `OPEN`；
- 分数下降后会进入 `HOLD`，而不是马上关闭；
- 追踪无效时会进入 `SUSPEND`。

### 13.2 手动场景测试

第一批场景：

```text
normal_speech
silent_looking_at_screen
keyboard_noise
speaker_playback_without_speech
drinking
yawning
laughing
eating
looking_down_at_phone
turning_head
strong_screen_reflection
low_light
headset
speaker_mode
```

每个场景至少保存：

- 一份帧日志；
- 一份状态切换日志；
- 一段人工备注；
- 如果条件允许，保存对应视频片段用于复盘。

### 13.3 验收观察项

每个场景需要观察：

- 误开麦次数；
- 误关麦次数；
- 是否吞掉第一个字；
- 是否切掉句尾；
- 状态是否频繁抖动；
- `SUSPEND` 是否过于敏感；
- FPS 和 CPU 占用是否可接受。

## 14. 开发里程碑

### 14.1 Milestone 1：工程骨架

- 创建 Python 包结构；
- 创建配置读取；
- 创建纯函数测试；
- 创建命令行入口。

### 14.2 Milestone 2：摄像头预览

- 打开摄像头；
- 显示 OpenCV 实时画面；
- 显示 FPS；
- 支持干净退出。

### 14.3 Milestone 3：人脸和嘴部追踪

- 接入 MediaPipe；
- 获取人脸关键点；
- 绘制嘴部关键点；
- 计算 `mouth_open_ratio`；
- 在画面中显示数值。

### 14.4 Milestone 4：说话分数

- 增加短时间窗口；
- 计算嘴部运动节奏；
- 计算 `speak_score`；
- 在画面中显示分数。

### 14.5 Milestone 5：状态机

- 实现 `CLOSED`、`PRE_OPEN`、`OPEN`、`HOLD`、`COOLDOWN`、`SUSPEND`；
- 输出 `gate_open`；
- 在画面中显示当前状态；
- 记录状态切换日志。

### 14.6 Milestone 6：日志和标记

- 写入帧日志；
- 写入会话元数据；
- 增加手动标记按键；
- 保证退出时日志完整。

### 14.7 Milestone 7：第一轮调参

- 跑完整手动场景测试；
- 统计误开和误关；
- 调整阈值；
- 记录主要失败案例；
- 决定是否进入 MVP-1 的真实麦克风链路验证。

## 15. 实现原则

- 先验证视觉门控，不急着接真实麦克风；
- 先用可解释规则，不急着训练模型；
- 先记录充分日志，再谈优化；
- 状态机要保守，视觉不可信时默认关麦；
- 不直接模拟游戏按键，避免反作弊和兼容性风险；
- 不把第一版 UI 做复杂，避免偏离算法验证；
- 所有阈值都应可配置，并写入日志元数据。

## 16. 待确认问题

这些问题不阻塞 MVP-0，但需要在测试中回答：

- 10 FPS 是否足够，还是必须 15 FPS？
- 手机摄像头延迟是否会影响判断？
- 没有音频预缓冲时，是否容易吞第一个字？
- 哪类非说话动作最容易误开麦：喝水、哈欠、大笑还是反光？
- 咳嗽应该被视为说话、噪声，还是可配置行为？
- OpenCV UI 是否够用，还是需要独立调参界面？

## 17. 下一份建议文档

下一步建议写：

```text
docs/TEST_PLAN.md
```

测试计划应定义：

- 场景名称；
- 每个场景的录制动作；
- 人工标注规则；
- CSV 复盘方式；
- 第一轮调参流程；
- MVP-0 是否达标的判断标准。
