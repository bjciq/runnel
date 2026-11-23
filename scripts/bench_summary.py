#!/usr/bin/env python3
"""把 macrobenchmark 的 benchmarkData.json 压成几行关键数字。"""
import json, sys

data = json.load(open(sys.argv[1]))
for b in data.get("benchmarks", []):
    print(f"== {b['className'].split('.')[-1]}.{b['name']}")
    for name, m in b.get("metrics", {}).items():
        print(f"   {name:28s} min {m['minimum']:8.1f}  median {m['median']:8.1f}  max {m['maximum']:8.1f}")
    for name, m in b.get("sampledMetrics", {}).items():
        print(f"   {name:28s} P50 {m['P50']:7.1f}  P90 {m['P90']:7.1f}  P95 {m['P95']:7.1f}  P99 {m['P99']:7.1f}")
