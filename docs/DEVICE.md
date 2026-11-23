# 把手机连到开发机（装包与性能测试）

开发机是一台云上的 arm64 Linux，没有模拟器。手机连上之后，`scripts/perf.sh` 才能安装、跑 macrobenchmark、抓 Perfetto trace。

## 方式 A：电脑 USB + SSH 反向隧道（最稳）

1. 手机用 USB 连到你的电脑（Windows），电脑上装了 platform-tools，`adb devices` 能看到手机。
2. 在电脑上开隧道，把电脑的 adb 服务暴露给开发机（单行）：
   `adb start-server; ssh -N -R 5037:127.0.0.1:5037 <用户>@<开发机>`
3. 开发机上 `~/Android/Sdk/platform-tools/adb devices` 就能看到手机。隧道窗口别关。

## 方式 B：无线调试 + Tailscale

1. 手机和开发机都登录同一个 Tailscale 网络。
2. 手机：开发者选项 → 无线调试 → 打开 →“使用配对码配对设备”，记下端口和配对码。
3. 开发机（IP 用手机的 Tailscale IP）：
   `~/Android/Sdk/platform-tools/adb pair <手机TailscaleIP>:<配对端口> <配对码>; ~/Android/Sdk/platform-tools/adb connect <手机TailscaleIP>:<无线调试端口>`
4. 无线调试端口在手机息屏或切网络后可能变化，需要重新 connect。

## 小米 / HyperOS 额外设置
- 开发者选项里同时打开“USB 调试（安全设置）”，否则 adb 装包、模拟点击（macrobenchmark 需要）会被拒绝。
- 开发者选项 →“显示刷新率”可以直接看 App 是否跑在 120Hz。

## 连上之后
```
scripts/perf.sh install      # 装 release 包
scripts/perf.sh bench        # 冷启动 / 列表滚动 / 阅读翻页的帧时间
scripts/perf.sh profile      # 生成 Baseline Profile（Android 13+）
scripts/perf.sh trace 10     # 抓 10 秒 trace，期间在手机上操作
```
不方便连的时候：装好 release 包后，在 App 的「设置 → 性能」里点“重新统计”，正常使用一阵，把卡顿帧比例、最慢一帧、刷新率这几个数字发过来。
