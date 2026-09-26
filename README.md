# 30天计划

一款面向“30 天完成 30 件事”的 Android 应用。界面以简洁白色与液态玻璃为主，支持任务进度、完成记录和多感官反馈；数据默认只保存在本机。

[![License](https://img.shields.io/github/license/li1408/FlowState)](LICENSE)
[![Android](https://img.shields.io/badge/Android-12%2B_(API_31)-3DDC84?logo=android&logoColor=white)](https://www.android.com)
[![Upstream](https://img.shields.io/badge/Upstream-Markel15%2FFlowState-181717?logo=github)](https://github.com/Markel15/FlowState)

## 主要功能

- 创建、编辑、分类和拖动排序待办事项
- 显示已完成数量与剩余任务数
- 完成时可选文字记录、拍照或从相册选择图片
- 玻璃完成提示、粒子动画、声音与震感反馈
- 已完成任务历史，可查看记录或重新开启
- 酷安风格液态玻璃悬浮底栏，支持按压、拖动与弹簧过渡
- 布尔习惯与数值习惯、连续记录和统计
- 日历、提醒、桌面组件、清单和灵感记录
- 简体中文、英文和西班牙文资源
- 本地 Room 数据库与 JSON 备份/恢复

> 备份 JSON 不包含照片文件；完成照片保存在应用私有目录中。

## 隐私

- 无账号、无广告、无分析 SDK
- 不需要相机或媒体库常驻权限：拍照使用系统相机，选图使用 Android Photo Picker
- 任务、习惯和完成记录均保存在设备本地

## 构建

环境要求：JDK 17、Android SDK 37、Android 12（API 31）或更高版本。

```bash
git clone https://github.com/li1408/FlowState.git
cd FlowState
./gradlew :app:assembleRelease
```

Windows PowerShell：

```powershell
.\gradlew.bat :app:assembleRelease
```

生成的 APK 位于 `app/build/outputs/apk/release/app-release.apk`。

## 验证

```powershell
.\gradlew.bat testDebugUnitTest :app:assembleRelease --no-parallel
```

仓库还包含 `benchmark` 模块，用于启动、底栏切换、习惯列表滚动、打卡、数值连点和排序场景的 Macrobenchmark/Baseline Profile 测试。

## 项目来源与修改说明

本项目基于 [Markel15/FlowState](https://github.com/Markel15/FlowState) `v3.5.2` 修改，保留原项目的 Apache License 2.0 许可与版权信息。

当前 Fork 的主要修改包括：

- 完整简体中文适配与“30天计划”品牌
- AndroidLiquidGlass 液态玻璃底栏及连贯导航动画
- 30 项挑战进度、图文完成记录和强完成反馈
- 习惯页数据流、原子数值更新和排序性能优化
- Room v21 数据迁移、备份一致性与恢复保护
- Macrobenchmark 与 Baseline Profile 基础设施

第三方组件及其许可证见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

## 许可证

本项目使用 [Apache License 2.0](LICENSE)。上游版权归原作者所有；本 Fork 的新增与修改部分由维护者 `li1408` 负责。
