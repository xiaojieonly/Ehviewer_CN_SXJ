# EhViewer
## 本APP有且仅在Github更新，所有自诩“官网”的均属虚假信息，请注意甄别

### [常见问题汇总](https://github.com/xiaojieonly/Ehviewer_CN_SXJ/blob/BiLi_PC_Gamer/feedauthor/EhviewerIssue.md)
![Icon](fastlane/metadata/android/en-US/images/icon.png)

这是一个 E-Hentai Android 平台的浏览器。

An E-Hentai Application for Android.

# Download

点击前往下载：

[//]: # (- [Appteka]&#40;https://appteka.store/app/acdr168648&#41;)
- [百度云](https://pan.baidu.com/s/1cPrJQY1vIj-wGCaeHGCuvg) 提取码：q48u
- [夸克网盘](https://pan.quark.cn/s/e979bc34a387) 提取码：EKWz
- [蓝奏云](https://wwbfg.lanzouu.com/iS7Ec3fdxo0j)，电脑端可正常下载 提取码：74wp
- [GitHub](https://github.com/xiaojieonly/Ehviewer_CN_SXJ/releases)
- Torrent链接: magnet:?xt=urn:btih:2e1ff3f33a2b805248f8924a59c9af10a253aeba&xt=urn:btmh:1220b85731bf95b0beddae106ae6c369c9e01177e133a26b70ecc8a8a4314e237410&dn=EhViewer-2.0.1.3.apk&xl=27623258

点击前往赏饭：

- [要饭嘛不寒掺](https://github.com/xiaojieonly/Ehviewer_CN_SXJ/blob/BiLi_PC_Gamer/feedauthor/support.md)

唯一X账号：https://x.com/Sherloc21784244    
Telegram群: https://t.me/+WyclP8pPlk-JfbwS    
Telegram通知群: https://t.me/Ehviewer_xiaojieonly_channel

# Changelog

## 2026/01/05 紧急修复
### 新版发布2.0.1.2

- 暂时回滚图片解码方式，等后续优化好了再上

## 2026/01/04 紧急修复
### 新版发布2.0.1.1

- 限制了详情页初始加载的预览图数量为40张，以减少初次创建视图的开销。
- 修复了 `DownloadFragment` 中因 Activity 销毁后关闭对话框可能导致的崩溃问题
- 修复了下载列表画廊的删除和拖拽排序无法及时生效的问题
- [百度云](https://pan.baidu.com/s/1EPEqfeklH0Pdk_mEiuJ8rQ) 提取码：9cas
- [夸克网盘](https://pan.quark.cn/s/cb19c11bcb6d) 提取码：WJWs
- [蓝奏云](https://wwbfg.lanzouu.com/ipmeo3fa1umh)，电脑端可正常下载 提取码：4jop
- [GitHub](https://github.com/xiaojieonly/Ehviewer_CN_SXJ/releases)
- Torrent链接: magnet:?xt=urn:btih:a547ca192aada5109bbf891bc5ea21b04d50972e&xt=urn:btmh:12209bc1fe78c6f1b431a120f2a24dc89556f5a8da767f710d8b6840a461146fc434&dn=EhViewer-2.0.1.1.apk&xl=27606911


## 2026/01/01 祝大家新年快乐~
### 新版发布2.0.1.0

- 新增下载页拖拽排序设置项，允许用户启用或禁用该功能
- 该设置现在会被保存，以便在应用重启后保持不变
- 回归到旧版图片解码代码，引入libwebp插件，并添加webp图片格式的处理方法
- 更新依赖项并为 16KB 页面大小设备添加适配
- 调整下载场景 FAB 图标
- HaYaShi: 下载画廊添加文件大小排序 (#2321)
- 为下载分类添加“全部”选项并优化布局
- 当解析 URL 失败时，会通过 `FirebaseCrashlytics` 记录异常，以防止应用崩溃并帮助调试。
- 同步德语、西班牙语、法语、韩语、泰语、日语和繁体中文翻译
- 猫尾草：新增按分类筛选下载内容的功能
- 猫尾草：给恢复下载项、清空下载冗余新增进度条，免得等的有问题
- 猫尾草：在设置-EH选项卡新增当前系统主题显示，方便查看bug（这样容易分辨是否是系统造成的问题）
- [百度云](https://pan.baidu.com/s/1ZOzR9W24cDRVYtiR_msOoQ) 提取码：2rsb
- [夸克网盘](https://pan.quark.cn/s/b023fa0249dd) 提取码：iKSY
- [蓝奏云](https://wwbfg.lanzouu.com/iSJdX3eyu95g)，电脑端可正常下载 提取码：92ad
- [GitHub](https://github.com/xiaojieonly/Ehviewer_CN_SXJ/releases)
- Torrent链接: magnet:?xt=urn:btih:241667f787c7f5d62e393d2404d2f9e2280d9cfb&xt=urn:btmh:122067d50a27f6b620b065a961d4cb2ad048e470b1e3b416ced2468c19d3b0d0cf61&dn=EhViewer-2.0.1.0.apk&xl=27606133



- [2024年更新日志-人生的不起落落落落](feedauthor/year2025-life-broken-down.md)  
- [2024年更新日志-感谢大家的支持](feedauthor/year2024-thanks.md)  
- [2023年更新日志-时间过的好快](feedauthor/year2023-boom.md)  
- [2022年更新日志-成长](feedauthor/year2022-growing-up.md)  
- [2021年更新日志-艰难起步](feedauthor/year2021-step-begin.md)  
- [2020年更新日志-爱与痛的开始](feedauthor/year2020-love-begin.md)


# Screenshot

![screenshot-01](fastlane/metadata/android/en-US/images/phoneScreenshots/1.png)


# Build

Windows

    > git clone https://github.com/xiaojieonly/Ehviewer_CN_SXJ.git
    > cd EhViewer
    > gradlew app:assembleDebug

Linux

    $ git clone https://github.com/xiaojieonly/Ehviewer_CN_SXJ.git
    $ cd EhViewer
    $ ./gradlew app:assembleDebug

生成的 apk 文件在 app\build\outputs\apk 目录下

The apk is in app\build\outputs\apk

# Thanks

## [感谢名单](https://github.com/xiaojieonly/Ehviewer_CN_SXJ/blob/BiLi_PC_Gamer/feedauthor/thankyou.md) 

感谢Ehviewer奠基人[Hippo/seven332](https://github.com/seven332)    
Thanks to [Hippo/seven332](https://github.com/seven332), the founder of Ehviewer    

本项目受到了诸多开源项目的帮助  
This project has received help from many open source projects  

这是部分库
Here is the libraries  
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

## DeepWiki  [<img src="https://devin.ai/assets/deepwiki-badge.png" alt="Ask DeepWiki.com" height="20"/>](https://deepwiki.com/xiaojieonly/Ehviewer_CN_SXJ)
## 状态

[![Alt](https://repobeats.axiom.co/api/embed/e6becb5b041dae430dff7f85581aa1f91975d416.svg "Repobeats analytics image")](https://github.com/xiaojieonly/Ehviewer_CN_SXJ/pulse)
