# Runnel

Android RSS 阅读器，追求跟手、流畅的滑动手感。

- 同步：FreshRSS、Miniflux（Google Reader API），离线标记、增量同步
- 手感：页面转场和跟手侧滑返回、柔和减速的惯性滚动、橡皮筋回弹、最高刷新率
- 阅读：预热的 WebView 池、左右滑切换文章、Readability 抓全文、浅色/深色、字号与衬线
- 列表：右滑标已读、左滑加星标、“N 篇新文章”提示、全部标为已读
- 性能：JankStats 帧统计（设置页可看）、macrobenchmark、Baseline Profile 生成

## 构建
```
export JAVA_HOME=<JDK 21> ANDROID_HOME=<Android SDK>
./gradlew :app:assembleRelease
```
APK 在 `app/build/outputs/apk/release/`。没有服务器时可以在登录页选“先用演示数据看看”。

文档：`docs/SPEC.md`（规格与性能指标）、`docs/DEVICE.md`（真机性能测试）。

## 许可证
GPL-3.0，全文见 `LICENSE`。使用的第三方开源项目（Apache-2.0 / MIT，均与 GPL-3.0 兼容）及其许可证全文，在 App 的 设置 → 关于 → 开源许可 里。

## 名称与商标
“Runnel”名称和 App 图标不随 GPL-3.0 授权。分发修改版请换一个名字和图标。
