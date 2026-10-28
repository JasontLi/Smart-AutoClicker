# 普通场景编辑位置书签

## 源码检查与方案

基线：Klick’r 4.1.2，699bff818519bc418addb7f0bdad5fbfb6c3f720。

- 入口：`DumbScenarioDialog` 的 `page_actions` 创建 `DumbActionListContent`。
- 列表：`IncludeLoadableListBinding.list` 是 RecyclerView，LinearLayoutManager；`DumbActionListAdapter` 是 ListAdapter，提交异步 Diff。
- 模型：`DumbAction.id` 为 Identifier。已保存动作使用 databaseId；新动作仅有 tempId。`DumbActionEntity` / `DumbScenarioEntity` 为 Room AUTOINCREMENT 主键。
- 更新：`DumbEditionRepository` 在内存中编辑，`DumbScenarioDataSource.updateDumbScenarioActions` 经 DatabaseListUpdater 保留已有动作 ID；排序只改 priority。
- 复制与导入：`addDumbScenarioCopy` 为场景、动作和手势子项分配新数据库 ID；`BackupRepository` 导入普通场景也调用此路径。
- 持久化：场景用 Room；编辑默认值已有 SharedPreferences（DumbEditionPreferences）。选择独立 SharedPreferences 作为书签元数据，不修改 Room schema、动作执行模型或备份格式。

## 实现

- `EditBookmarkStore`：应用私有 `shared_prefs/DumbEditBookmarks.xml`，按 `scenario_<数据库ID>` 保存 JSON 数组；每项 UUID、actionId、name。IO 线程 commit，Mutex 串行读改写；SharedPreferences listener 通知 UI。
- `EditBookmark.findTarget`：只按动作 ID 与场景 ID 定位，绝不按序号、名称或参数回退。
- `DumbActionListViewModel`：从当前场景观察书签；新增与重新绑定拒绝临时 ID。书签方法不调用任何场景更新/保存方法。
- `DumbActionListContent`：顶部“编辑书签”；每行新增书签；书签列表点击即跳转；“管理书签”支持重命名、删除、重新绑定。缺失目标显示失效状态并进入管理。
- `DumbActionListAdapter`：空心/实心标识；跳转后高亮 1.8 秒。背景颜色通过 MaterialColors 从动作行主题解析，再构造 StateListDrawable。
- `EditBookmarkDialog`：新建于 overlays 模块，因为 BaseOverlay 构造函数为 internal；复用 Material 和现有无障碍悬浮窗口导航/旋转重建。名称输入在旋转重建时保留。
- 新增中英文资源；不增加生产依赖；启用现有测试 convention 插件。
- 滚动：ListAdapter 提交完成后处理待跳转，post 到 RecyclerView，再用 LinearLayoutManager.scrollToPositionWithOffset；下一次 layout 后高亮。每次解析使用当前列表 ID，页面销毁取消协程。

## 操作与兼容策略

| 操作 | 书签策略 |
| --- | --- |
| 插入、删除前方动作、拖动排序 | 根据原动作 ID 解析新位置 |
| 修改原动作参数/名称 | ID 不变，仍指向该动作 |
| 删除目标动作 | 显示失效，不跳到其他动作；可删除书签或选已保存动作重新绑定 |
| 未保存的删除被撤销 | 原 ID 再次存在后恢复可跳转 |
| 新增/复制但尚未保存的动作 | 提示保存场景并重开编辑器；不会强制保存场景 |
| 场景重命名 | ID 不变，保留书签 |
| 场景复制、导入 | 新 ID；不继承旧场景书签 |
| 场景导出 | 不包含编辑元数据，格式不变 |
| 删除场景 | 私有偏好保留不可访问的旧条目；AUTOINCREMENT 不复用 ID，不会串场景 |
| 清除应用数据/卸载 | 本地书签随应用数据移除 |

不实现自动恢复上次选中位置；先提供显式书签跳转。

## 测试

命令（JDK 21、ANDROID_HOME 指向 work/sdk，按环境配置代理）：

```sh
./gradlew :feature:dumb-config:testFDroidDebugUnitTest :core:dumb:testFDroidDebugUnitTest :smartautoclicker:assembleFDroidDebug -PabiFilters=arm64-v8a --console=plain --max-workers=2
```

已通过 21 项：
- 6 项 EditBookmarkStoreTests：多个书签和 store 重建后的持久化、重命名/删除/重新绑定与场景隔离、插入删除重排后稳定定位、目标删除与临时 ID 不误定位、新场景 ID 不继承书签、拒绝临时 ID 写入。
- 4 项 DumbScenarioTests。
- 7 项 Migration2to3Tests。
- 4 项 DumbGestureResolverTests。

注意：store 重建测试不等同于真实手机进程被杀后重启；复制/导入测试验证新 ID 的隔离策略，不是完整导入导出 UI 测试。执行引擎与备份代码未改动，但不能把静态边界检查视为真机执行等价性证明。

## 待真机验收

1. 使用测试副本：先准备至少 50 个动作，保存并重开；为第 35 个动作加书签，再加第二个自定义名称书签。
2. 退出编辑器/关闭并重新打开应用；确认书签仍在，点击后目标完整可见且短暂高亮。
3. 在前方插入/删除动作与拖动重排，保存重开，确认仍指向原动作。
4. 删除目标，确认显示失效；删除书签不删除动作；重新绑定后能跳转。
5. 只修改书签后取消场景编辑，重开确认动作配置未变而书签保留。
6. 复制、重命名、导出并导入场景，确认策略符合上表；验证旧备份可用。
7. 旋转屏幕、切换编辑器页签、连续修改列表后跳转；检查无错位与重复弹窗。
8. 对比同一测试场景的执行行为，检查智能场景仍可正常编辑/执行。

尚未连接手机执行上述 UI/运行验收；不宣称已全部通过。

## fix1：动作行显示时闪退

用户报告：新增点击、选择位置并保存后退出；之前导入的场景也打不开。

手机 crash 缓冲区（2026-10-28 13:29:51，PID 5114）确认：

```text
Resources$NotFoundException: File res/drawable/edit_bookmark_highlight.xml
DumbActionViewHolder.bindBookmark(DumbActionListAdapter.kt:116)
UnsupportedOperationException: Failed to resolve attribute at index 0 ... theme=null
```

两种操作都经过动作列表渲染，因此共同触发新增背景资源的主题解析问题。日志显示的是显示层错误；不能据此推断所有用户场景的导入/执行已完成真机验证。

复现命令：

```sh
./gradlew :feature:dumb-config:testFDroidDebugUnitTest --tests '*DumbActionListRenderingTests' -PabiFilters=arm64-v8a --console=plain --max-workers=2
```

修复前：1 test completed, 1 failed；Resources$NotFoundException → UnsupportedOperationException，与手机堆栈一致。
修复后：同一测试通过。将背景改为在 View 的主题下用 MaterialColors 解析颜色，然后创建不含未解析属性的 StateListDrawable；默认背景透明。移除出错的 selector XML。

新增渲染测试覆盖已保存动作、点击动作、新建临时 ID 点击动作、高亮切换及回收后高亮重置。此前 21 项测试未覆盖真实动作行绑定，这是本次漏检原因；增加此层回归测试避免复发。

最终验证：7 项 feature:dumb-config 测试 + 15 项 core:dumb 测试，22 项均通过；assembleFDroidDebug 成功；APK 签名与之前版本一致。fix1 已通过 adb install -r 成功覆盖安装；无卸载、清除数据或改数据库。

产物：`../work/Klickr-4.1.2-edit-bookmarks-fix1-arm64-debug.apk`（相对仓库根目录）。等待用户验证新增点击和旧场景编辑实际操作。

## fix2：名称宽度为零与图标重叠

用户确认闪退修复，但动作行名称不可见、图标重叠。

新增真实测量/布局测试 `actionNameHasUsableWidthAndDoesNotOverlapIcons`，修复前断言失败：`name width at 280 dp was 0`。原因是新增书签按钮与类型图标的双向约束形成未完整锚定的水平链，连带压缩名称列。

修复：类型图标只锚定父容器末端；书签按钮放在类型图标前方；拖动手柄只锚定父容器起始端；文字列填充中间空间。删除不必要的反向约束，名称末尾省略以避免长文本覆盖。

相同测试修复后通过；检查 280/360/400dp 下文字宽度至少 100dp、控件不重叠、可见文本列同宽，以及隐藏编辑按钮的复制/选择布局。23 项相关测试全部通过，APK 构建及验签通过。产物：`../work/Klickr-4.1.2-edit-bookmarks-fix2-arm64-debug.apk`。真机外观仍需用户确认。
