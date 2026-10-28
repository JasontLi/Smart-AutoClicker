# 普通场景运行操作轨迹

## 范围与入口

仅普通场景。普通场景动作编辑页顶部新增“显示操作轨迹（所有普通场景）”MaterialSwitch。全局保存在独立 SharedPreferences `DumbOperationDisplay/enabled`，默认关闭；不保存到场景或动作配置，不修改备份格式。等待动作不增加显示条。

## 接入与显示

- `DumbOperationDisplay`：只读观察状态；开关关闭时不生成轨迹事件。启用时记录实际 BuiltGesture 引用及提交时间、提交接口返回时间，不复制或修改动作参数。
- `DumbActionExecutor`：只在普通场景每次重复手势的实际 dispatch 调用旁发布状态；无额外 delay，也不等待 UI。智能场景使用的公共 GestureExecutor 没有修改。
- `DumbEngine`：初始化/停止时清理旧状态，晚到的完成事件不能恢复旧轨迹。
- `DumbMainMenu`：按生命周期观察轨迹，停止、隐藏、销毁时移除悬浮窗口。
- `DumbOperationTraceOverlay` / `TraceView`：TYPE_ACCESSIBILITY_OVERLAY，NOT_TOUCHABLE + NOT_FOCUSABLE，全屏透明。用 View 在屏幕中的位置补偿坐标，适配状态栏/刘海偏移。点击和长按显示圆圈，滑动显示已经经过的路径与触点；多个触点用不同颜色。显示最后一条手势，完成后淡出 1 秒；下条手势替换上条，停止时立即清除。
- 从 BuiltGesture 的 StrokeDescription 读取最终路径，使用最终随机化后的坐标。连续手势切片按系统调度时间累计；仅用于按下的静止 continuation 不重复计算未消耗的时长。零长度点击路径使用路径起点绘制。

## 实际边界

公共 AndroidActionExecutor.dispatchBuiltGesture 返回 Unit，不向普通场景调用方暴露成功/取消结果。本功能表示“提交的轨迹”，不声称操作成功。动画从提交前的单调时钟开始，可能与设备实际触摸事件有调度误差；系统提前取消时，显示时长可能延续到公共接口返回。未改变公共手势派发与回调逻辑。

无额外动作、延迟或截图。首次开关、轨迹窗口创建和绘制仍有正常 UI 开销，超高速连点不保证每一下都能在屏幕刷新帧中独立可见。只针对主屏幕。

## 自动验证

29 项相关测试通过（最终 APK 需以本次构建输出为准）：
- 原书签与动作行布局 8 项。
- 原场景合法性、迁移和手势解析 15 项。
- DumbOperationDisplayTests 3 项：默认关闭和跨实例持久化、保留原 BuiltGesture 不修改参数、关闭/停止后晚到事件不复活轨迹。
- DumbOperationTraceTests 3 项：窗口不可触摸/不可聚焦；Native Canvas 像素测试证明零长度点击圆圈显示在目标位置；滑动只画经过的部分并在到期后消失。

命令：
```sh
./gradlew :feature:dumb-config:testFDroidDebugUnitTest :core:dumb:testFDroidDebugUnitTest :smartautoclicker:assembleFDroidDebug -PabiFilters=arm64-v8a --console=plain --max-workers=2
```

## 待真机验证

开启开关后分别运行点击、长按、滑动和多指手势；确认显示、坐标和实际点击一致，悬浮层不拦截触摸。验证关闭后无轨迹、暂停停止后清理、屏幕旋转和锁屏隐藏正常、退出重进开关保留、智能场景行为未变。自动测试不替代这些真机验收。
