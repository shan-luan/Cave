# Cave — CAVE's Another Video Editor

[English](README.en.md) | 简体中文

## 这是啥玩意？
一个基于Scala3的剪辑软件。或者特效软件。或者DAW。谁在乎呢？

![截图](img.png)
![截图,又一张](img_1.png)

## 架构与特性
- 几乎一切都是节点。所有的修改通过（由ui自动）附加或者就地修改节点实现。
- 持久化且不可变的时间线。附带函数式炫词。比如，轨道由承载Gap与Segment的和类型ADT元素与左闭右开区间之间的双射构成。
- 极其可扩展的架构。如果我想的话，可以把OSU!lazer物件，G-code，midi，SRT字幕很容易的接入。
- 跨平台。Windows,Mac,Linux,Android(注意，Android分支能构建，但是没有添加ui适配）。
- 标签页式多项目。项目间复制WIP。
- 一个为耗时任务预留的任务系统。目前只有导出任务被实现。可以不阻碍进行编辑的同时导出视频。
- 由JavaCV(FFmpeg)提供的强大编解码能力。支持大概有一百种编码格式的视频。
- 由libgdx提供的GPU上渲染能力。
- 基于Scene2D的UI，就像Spine用的一样。忘掉CEF吧！
- 完全的自由软件。

## 许可证

GNU Affero General Public License v3.0
