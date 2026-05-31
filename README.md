# EhViewer
## 本APP有且仅在Github更新，所有自诩“官网”的均属虚假信息，请注意甄别

### [常见问题汇总](https://github.com/xiaojieonly/Ehviewer_CN_SXJ/blob/BiLi_PC_Gamer/feedauthor/EhviewerIssue.md)
![Icon](fastlane/metadata/android/en-US/images/icon.png)

这是一个 E-Hentai Android 平台的浏览器。

An E-Hentai Application for Android.

# Download

点击前往下载：

[//]: # (- [Appteka]&#40;https://appteka.store/app/acdr168648&#41;)
- [百度云](https://pan.baidu.com/s/1hFLjNrU-_c1u8iugt82d6g) 提取码：wz2h
- [夸克网盘](https://pan.quark.cn/s/133080ed0571) 提取码：ekzT
- [蓝奏云](https://wwbfg.lanzouu.com/i1hv53qtvjba)，电脑端可正常下载 提取码：eg80
- [GitHub](https://github.com/xiaojieonly/Ehviewer_CN_SXJ/releases)
- Torrent链接:magnet:?xt=urn:btih:a14acab7edec4b1c5f10d291296fda3e19449a0d&xt=urn:btmh:1220f25dba401d5db2cfb2d864114728418c1e7bf746679182a3155ef3e1714539cb&dn=EhViewer-2.0.1.8.apk&xl=27739161


点击前往赏饭：

- [要饭嘛不寒掺](https://github.com/xiaojieonly/Ehviewer_CN_SXJ/blob/BiLi_PC_Gamer/feedauthor/support.md)

唯一X账号：https://x.com/Sherloc21784244    
Telegram群: https://t.me/+WyclP8pPlk-JfbwS    
Telegram通知群: https://t.me/Ehviewer_xiaojieonly_channel


# Changelog

## 2026/06/01 祝大家六一儿童节快乐~
### 新版发布2.0.1.8

- 将 jsoup 从 1.18.1 降级到 1.15.4，以避免在某些 Android 环境中出现 NoClassDefFoundError
- 在 EhDB 中添加了空检查，以防止快速搜索操作期间潜在的 NullPointerExceptions
- 增强了 EhEngine 中 TopListParser 的错误处理，以在运行时错误上引发更具描述性的 ParseException
- 重构SpiderDen，确保访问下载目录时的线程安全
- 改进了 ArchiverDownloadDialog 中的文件名处理，以防止非法路径和长文件名
- 添加了 ArchiverDownloadCompleter 来处理存档器任务的下载完成和状态检查
- 在 EhApplication 中集成挂起的下载恢复
- 增强设置，提供管理待处理存档下载的方法
- 更新了 ArchiverDownloadDialog 以利用 ArchiverDownloadCompleter 进行下载处理
- 改进了 ArchiverDownloadProgress 中的下载进度跟踪
- 确保在 SpiderDen 中创建下载目录
- 猫尾草：添加小米系统优化助手，优化后台下载和通知管理
- 猫尾草：add gradle wrapper jar and properties for CI build
- Cololi：沉浸式底部导航栏 (#2597)

## 2026/05/01 祝大家五一劳动节快乐~
### 新版发布2.0.1.7

- 修复SpiderInfo读取时的OOM风险并升级JDK至21
- 修复图片搜索无法使用的问题
- orbisai0security：the vendored giflib library performs multiple m... in gifalloc.c
- 增加对 WebView/CookieManager 初始化失败的异常处理
- En：修复了已下载项目的按标签搜索功能
- 猫尾草：restore gradle wrapper jar and properties
- 升级Gradle至9.3.1及Android插件至9.1.1
- [百度云](https://pan.baidu.com/s/17a5zwo0HeTp_Iqh9P2QwXQ) 提取码：7y92
- [夸克网盘](https://pan.quark.cn/s/036dd4d5f09d) 提取码：B6J6
- [蓝奏云](https://wwbfg.lanzouu.com/iFc783oecgmh)，电脑端可正常下载 提取码：dfg8
- [GitHub](https://github.com/xiaojieonly/Ehviewer_CN_SXJ/releases)
- Torrent链接:magnet:?xt=urn:btih:c3aab1194eb843bac7274b87873dc94041310e52&xt=urn:btmh:1220c3640ed2ef7f588376f7dadeffc99463d764608b577beb038378788996fc1ad5&dn=EhViewer-2.0.1.7.apk&xl=27705830


## 2026/04/01 祝大家愚人节伤心
### 新版发布2.0.1.6

- 搜索时过滤文本中的换行符
- 修复下载列表排序奔溃的问题
- 优化 EGL 初始化逻辑并增加 OpenGL 渲染故障时的回退机制
- 排行榜中，画廊排行从原先的跳转画廊搜索，改为直接跳转对应画廊
- 优化归档下载逻辑与文件名生成
- 优化解析错误日志清理逻辑并增加异常处理
- 优化搜索文本过滤，直接移除换行符而非替换为空格
- 修正登录WebView客户端设置及资料获取逻辑
- 升级SDK版本并启用coreLibraryDesugaring
- miki sayaga：新增一个多标签搜索组合页面（未完成）
- 修复部分多标签搜索组合页面bug
- 将部分代码从java迁移到kotlin
- [百度云](https://pan.baidu.com/s/1koygBtTteJtDHZTQYL8wXQ) 提取码：iqev
- [夸克网盘](https://pan.quark.cn/s/b41421a61e70) 提取码：MrnK
- [蓝奏云](https://wwbfg.lanzouu.com/iNSBF3m1jveb)，电脑端可正常下载 提取码：i4f8
- [GitHub](https://github.com/xiaojieonly/Ehviewer_CN_SXJ/releases)
- Torrent链接:magnet:?xt=urn:btih:8488a933608f5b3901de8a2bedc669e20ff94839&xt=urn:btmh:1220dbe6fffcb6aff255e089e3bb0cefeb33940c27d82e37163252abe4813c987e33&dn=EhViewer-2.0.1.6.apk&xl=27702785


## 2026/03/01 提前祝大家元宵节快乐
### 新版发布2.0.1.5

- 调整 Analytics上报字段
- 将部分代码从java迁移到kotlin
- 修复 `BitmapUtils` 中的潜在整数溢出问题
- 优化VPN检测逻辑，增加权限检查和异常处理
- 优化登录流程异常处理和进度显示
- 清理请求头中的换行符避免崩溃
- 调整下载列表页面的标题格式
- zyl-hub：修复了在搜索框不为空时的搜索历史补全
- [百度云](https://pan.baidu.com/s/1_rbxH65GXWjx_pxYIf0Pug) 提取码：wzv4
- [夸克网盘](https://pan.quark.cn/s/95915acfe88b) 提取码：HmUu
- [蓝奏云](https://wwbfg.lanzouu.com/iYopw3jiyizi)，电脑端可正常下载 提取码：fhbq
- [GitHub](https://github.com/xiaojieonly/Ehviewer_CN_SXJ/releases)
- Torrent链接:magnet:?xt=urn:btih:4869fe5d6cebba6e1f2b672e3523cca83838b466&xt=urn:btmh:1220df01410d828d8544b9efe8e15fe3e7323eab74d2ae962947f324dd36e0a99b74&dn=EhViewer-2.0.1.5.apk&xl=27934862


## 2026/02/01 给大家提前拜个早年，祝大家新春快乐~
### 新版发布2.0.1.4

- 更新通过webview 通过cloudflare验证获取用户名称的功能（裸连目前无法使用此功能）
- 修复略缩图因状态共用导致的重复显示同一张图片
- 登录时，修复用户名称获取逻辑，由于技术限制，sni开启的情况下无法通过机器人验证
  网页登录只能使用VPN进行
- 在裸连使用cookie登录时，现在会跳过获取用户详情步骤
- 更新安卓targetSdkVersion到30，所有用户需要授予管理文件权限，否则将无法读取旧画廊
- [百度云](https://pan.baidu.com/s/1VD8NwRZTUVkO5hsTCPd95A) 提取码：hfms
- [夸克网盘](https://pan.quark.cn/s/685e409e6164) 提取码：yiie
- [蓝奏云](https://wwbfg.lanzouu.com/iM6GO3hj88cd)，电脑端可正常下载 提取码：cjgb
- [GitHub](https://github.com/xiaojieonly/Ehviewer_CN_SXJ/releases)
- Torrent链接: magnet:?xt=urn:btih:fd7fb29419c9b6f7a22bbbd899500fb828e2e5ce&xt=urn:btmh:1220aa0bf5802d34adeb7e60b17f0466f951a4d96dd4ab5b06158af2690bee2cb073&dn=EhViewer-2.0.1.4.apk&xl=27623297


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
