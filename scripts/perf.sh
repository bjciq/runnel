#!/usr/bin/env bash
# 真机性能回路（手机需先用 adb 连上，见 docs/DEVICE.md）
#   scripts/perf.sh install   编 release 包并安装
#   scripts/perf.sh bench     跑全部 macrobenchmark，打印关键指标
#   scripts/perf.sh profile   生成 Baseline Profile 并拷进 app/src/main
#   scripts/perf.sh gfx       打印当前 gfxinfo（手动滑一会儿后执行）
#   scripts/perf.sh trace N   抓 N 秒 Perfetto trace 到 build/trace.perfetto-trace
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-$HOME/.local/android-build-tools/jdk/17}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
ADB="$ANDROID_HOME/platform-tools/adb"
PKG=io.github.xccxcxxc.runnel
OUT=benchmark/build/outputs/connected_android_test_additional_output

case "${1:-bench}" in
  install)
    ./gradlew -q :app:assembleRelease
    "$ADB" install -r app/build/outputs/apk/release/app-release.apk
    ;;
  bench)
    ./gradlew :benchmark:connectedBenchmarkAndroidTest \
      -Pandroid.testInstrumentationRunnerArguments.class=$PKG.benchmark.StartupBenchmark,$PKG.benchmark.ScrollBenchmark,$PKG.benchmark.ReaderBenchmark
    find "$OUT" -name '*benchmarkData.json' -print0 | xargs -0 -I{} python3 scripts/bench_summary.py {}
    ;;
  profile)
    ./gradlew :benchmark:connectedBenchmarkAndroidTest \
      -Pandroid.testInstrumentationRunnerArguments.class=$PKG.benchmark.BaselineProfileGenerator
    base=$(find "$OUT" -name '*-baseline-prof.txt' | head -1)
    startup=$(find "$OUT" -name '*-startup-prof.txt' | head -1)
    [ -n "$base" ] && cp "$base" app/src/main/baseline-prof.txt && echo "baseline-prof.txt 已更新"
    [ -n "$startup" ] && cp "$startup" app/src/main/startup-prof.txt && echo "startup-prof.txt 已更新"
    ;;
  gfx)
    "$ADB" shell dumpsys gfxinfo $PKG | sed -n '/Total frames rendered/,/Number Frame deadline missed/p'
    ;;
  trace)
    secs="${2:-10}"
    "$ADB" shell perfetto -o /data/misc/perfetto-traces/runnel.perfetto-trace -t "${secs}s" sched freq gfx view input am wm dalvik
    mkdir -p build && "$ADB" pull /data/misc/perfetto-traces/runnel.perfetto-trace build/trace.perfetto-trace
    echo "已保存 build/trace.perfetto-trace，可拖进 https://ui.perfetto.dev 查看"
    ;;
  *) echo "用法: $0 install|bench|profile|gfx|trace [秒]"; exit 1 ;;
esac
