# Minimal Browser V0.1

纯黑 OLED 风格的 Android WebView 极简浏览器。

## 功能
- 顶部地址栏：输入网址或搜索词
- 网页在应用内部通过 Android WebView 加载
- 底部五个按钮：后退、前进、刷新、加号、标签页
- 加号短按：新建空白标签页
- 加号长按：关闭当前标签页并新建空白标签页
- 标签页按钮：查看并切换已打开标签页
- 系统返回键：网页有历史时先执行网页后退

## 手机获取 APK
本仓库已配置 GitHub Actions。每次推送到 main 分支会自动编译；也可在 Actions 页面手动运行 Build Android APK。

编译成功后打开对应运行记录，在 Artifacts 下载 MinimalBrowser-debug，解压得到 app-debug.apk 并安装。

构建环境：JDK 17、Gradle 8.9、Android Gradle Plugin 8.7.3、compileSdk 35。
