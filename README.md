# Cave - 视频编辑应用

Cave 是一个基于 libGDX 框架开发的跨平台视频编辑应用程序，支持桌面和 Android 平台。它提供了一套完整的视频编辑功能，包括时间轴编辑、预览播放、资源管理等。

## 功能特性

- 🎬 **多轨时间轴编辑** - 支持视频片段的剪辑、拼接和排列
- 📹 **视频预览** - 实时预览编辑效果
- 📁 **媒体资源管理** - 支持多种视频格式导入和管理
- ⌨️ **快捷键系统** - 可自定义的快捷键操作
- 🌐 **国际化支持** - 支持中文界面
- 📱 **跨平台支持** - 同时支持桌面端 (LWJGL3) 和 Android 平台
- 🎨 **现代化UI** - 基于 VisUI 的美观用户界面

## 技术栈

- **核心框架**: [libGDX](https://libgdx.com/) 1.13.1 - 跨平台游戏开发框架
- **UI库**: [VisUI](https://github.com/kotcrab/vis-ui) 1.5.5 - 基于 Scene2D 的 UI 工具包
- **视频处理**: [JavaCV](https://github.com/bytedeco/javacv) 1.5.9 + FFmpeg 6.0 - 视频解码和处理
- **音频处理**: [miniaudio](https://github.com/mackron/miniaudio) 0.5 - 轻量级音频库
- **图形绘制**: [ShapeDrawer](https://github.com/earlygrey/shapedrawer) 2.5.0 - 即时模式形状绘制
- **工具库**: Google Guava 33.5.0 - 常用工具类
- **文件选择**: [gdx-nativefilechooser](https://github.com/SpookyGames/gdx-nativefilechooser) 2.4.0 - 原生文件对话框

## 项目结构

```
Cave/
├── core/              # 核心模块 - 包含主要业务逻辑
│   └── src/main/java/com/lomekwi/cave/
│       ├── app/       # 应用层（快捷键管理等）
│       ├── pipeline/  # 数据处理管道（音视频帧处理）
│       ├── project/   # 项目管理
│       ├── resource/  # 资源管理（视频、音频资源）
│       ├── timeline/  # 时间轴系统
│       ├── ui/        # 用户界面
│       └── util/      # 工具类
├── lwjgl3/            # 桌面端启动器 (LWJGL3)
├── android/           # Android 平台实现
└── assets/            # 资源文件（字体、UI皮肤等）
```

## 环境要求

### 桌面端
- JDK 8 或更高版本
- Gradle 7.0+

### Android
- Android SDK
- Android Studio（推荐）

## 构建与运行

### 克隆项目
```bash
git clone <repository-url>
cd Cave
```

### 桌面端运行
```bash
# 运行桌面版本
./gradlew lwjgl3:run

# 构建桌面版本
./gradlew lwjgl3:build
```

### Android 端构建
```bash
# 构建 Android APK
./gradlew android:assembleDebug

# 安装到连接的设备
./gradlew android:installDebug
```

### 清理构建
```bash
./gradlew clean
```

### 代码检查
```bash
./gradlew lint
```

## 开发指南

### 架构概览

Cave 采用模块化设计，主要组件包括：

1. **项目管理系统** (`project/`) - 负责项目的创建、保存和加载
2. **时间轴引擎** (`timeline/`) - 核心编辑功能，支持多轨道和片段管理
3. **资源管道** (`pipeline/`, `resource/`) - 音视频资源的解码和处理
4. **UI系统** (`ui/`) - 基于 Scene2D 和 VisUI 的用户界面
5. **事件总线** - 使用 Google EventBus 进行组件间通信

### 关键类说明

- `Main` - 应用主入口，继承自 ApplicationAdapter
- `Root` - UI 根容器，管理整个界面布局
- `Project` - 项目数据模型，包含时间轴和资源
- `Timeline` - 时间轴实现，支持多轨道
- `Track` - 轨道类，管理视频片段
- `Segment` - 时间轴片段基类
- `VdoSeg` - 视频片段实现

### 添加新功能

1. 在 `core` 模块中添加业务逻辑
2. 如需访问平台特定功能，在对应平台模块中实现
3. 使用事件总线 (`Vars.appEventBus`) 进行模块间通信
4. 遵循现有的代码风格和架构模式

## 配置说明

项目配置文件位于 `gradle.properties`，可以调整以下参数：

- `gdxVersion` - libGDX 版本
- `visUiVersion` - VisUI 版本
- `javacvVersion` - JavaCV 版本
- `ffmpegVersion` - FFmpeg 版本
- `projectVersion` - 项目版本号

## 许可证

本项目仅供学习和研究使用。

## 贡献

欢迎提交 Issue 和 Pull Request！

## 致谢

- [libGDX](https://libgdx.com/) - 优秀的跨平台框架
- [VisUI](https://github.com/kotcrab/vis-ui) - 强大的 UI 库
- [JavaCV](https://github.com/bytedeco/javacv) - Java 计算机视觉库
- 所有开源社区的贡献者

---

**注意**: 这是一个正在开发中的项目，部分功能可能尚未完善。