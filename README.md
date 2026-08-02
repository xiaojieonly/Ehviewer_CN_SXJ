# AnotherViewer

一个 E-Hentai Android 平台的浏览器，基于 [AnotherViewer](https://github.com/seven332/AnotherViewer) 及其衍生项目。

An E-Hentai Application for Android, based on [AnotherViewer](https://github.com/seven332/AnotherViewer) and its derivatives.

本仓库同时包含 **Android 客户端**与**本地 Web App（局域网服务器）**，两者可以协同工作：收藏/历史同步、远程阅读、委托下载。

This repository contains both the **Android client** and a **local Web App (LAN server)**. The two work together: favorites/history sync, remote reading, and delegated downloads.

### [常见问题汇总 / FAQ](FAQ.md)

主要改动包括如下：
* samba支持：用户可以将自己的缓存库同步/指向本地samba服务器，对于小容量设备更友好
* 横屏双页：针对折叠屏、平板电脑用户，横屏观看可以同时观看两页内容（具体兼容性视内容尺寸而定），更像实体书籍

Main improvements are as follows:
* Samba support: You can now mount & sync your local downloads to a samba server, making it more friendly for devices with limited storage.
* Dual-page landscape mode: On foldables and tablets, landscape viewing displays two pages side by side (compatibility depends on content dimensions), more like reading a physical book. 

## Screenshot

![screenshot-01](fastlane/metadata/android/en-US/images/phoneScreenshots/1.png)

## Build

Windows

    > git clone https://github.com/PegionFish/AnotherViewer.git
    > cd AnotherViewer
    > gradlew app:assembleDebug

Linux

    $ git clone https://github.com/PegionFish/AnotherViewer.git
    $ cd AnotherViewer
    $ ./gradlew app:assembleDebug

生成的 apk 文件在 `app/build/outputs/apk` 目录下

The apk is in `app/build/outputs/apk`

Web App（后端 + 前端）的构建与启动方式见下方 [Web App](#web-app) 一节。
To build and run the Web App (backend + frontend), see the [Web App](#web-app) section below.

## Web App

将 Android 客户端转换为局域网内任意设备可通过浏览器访问的 Web App。

### 功能

- 浏览、搜索 E-Hentai 画廊
- 图片阅读器（翻页/滚动/缩放/手势/键盘）
- 下载管理（多级并发、实时进度）
- 收藏管理（10 个收藏夹）
- 评论功能
- 浏览历史
- SMB 备份
- 设置管理
- Android 协同：收藏/历史同步、远程阅读、委托下载（Android 端在"服务器同步"设置中配置）

### 快速开始

```bash
# 构建
./build.sh

# 启动（Docker）
docker compose up -d

# 或启动（裸机）
./start.sh

# 访问
open http://localhost:8080
```

详见 [Web App 部署指南](docs/deployment.md)

## Acknowledgments

本项目是 [AnotherViewer](https://github.com/seven332/AnotherViewer) 的 Fork 的 Fork：AnotherViewer → [Anotherviewer_CN_SXJ](https://github.com/xiaojieonly/Anotherviewer_CN_SXJ) → AnotherViewer。

- 感谢 AnotherViewer 奠基人 [Hippo/seven332](https://github.com/seven332)
- 感谢 [xiaojieonly](https://github.com/xiaojieonly) 维护的 [Anotherviewer_CN_SXJ](https://github.com/xiaojieonly/Anotherviewer_CN_SXJ) 中文分支，本项目基于其代码继续开发

This project is a fork of a fork of [AnotherViewer](https://github.com/seven332/AnotherViewer): AnotherViewer → [Anotherviewer_CN_SXJ](https://github.com/xiaojieonly/Anotherviewer_CN_SXJ) → AnotherViewer.

- Thanks to [Hippo/seven332](https://github.com/seven332), the founder of AnotherViewer
- Thanks to [xiaojieonly](https://github.com/xiaojieonly) for maintaining [Anotherviewer_CN_SXJ](https://github.com/xiaojieonly/Anotherviewer_CN_SXJ), the Chinese fork this project is based on

本项目受到了诸多开源项目的帮助
This project has received help from many open source projects

- [AOSP](http://source.android.com/)
- [android-advancedrecyclerview](https://github.com/h6ah4i/android-advancedrecyclerview)
- [Apache Commons Lang](https://commons.apache.org/proper/commons-lang/)
- [apng](http://apng.sourceforge.net/)
- [giflib](http://giflib.sourceforge.net)
- [greenDAO](https://github.com/greenrobot/greenDAO)
- [jsoup](https://github.com/jhy/jsoup)
- [libjpeg-turbo](http://libjpeg-turbo.virtualgl.org/)
- [libpng](http://www.libpng.org/pub/png/libpng.html)
- [okhttp](https://github.com/square/okhttp)
- [roaster](https://github.com/forge/roaster)
- [ShowcaseView](https://github.com/amlcurran/ShowcaseView)
- [Slabo](https://github.com/TiroTypeworks/Slabo)
- [TagSoup](http://home.ccil.org/~cowan/tagsoup/)

## License

    Copyright 2014-2016 Hippo Seven
    Copyright 2020-2026 xiaojieonly

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
