# 在模拟器 brook36 上跑仪器测试，默认最多 3 分钟（全套约 2 分钟；任何情况都不许超过 10 分钟）：到时间直接杀掉模拟器和 Gradle。
#   pwsh scripts/connected-test.ps1                                  全部
#   pwsh scripts/connected-test.ps1 -Class io.github.xccxcxxc.runnel.ReaderTest
#   pwsh scripts/connected-test.ps1 -Class "io.github.xccxcxxc.runnel.ReaderTest#tapTitleOpensOriginal,io.github.xccxcxxc.runnel.DatabaseTest"
# 只在 brook36 上跑（ANDROID_SERIAL），手机连着也不会被装 debug 包。
#   pwsh scripts/connected-test.ps1 -Task :benchmark:connectedBenchmarkAndroidTest -Class io.github.xccxcxxc.runnel.benchmark.BaselineProfileGenerator
param([string]$Class = "", [int]$Minutes = 3, [string]$Task = ":app:connectedDebugAndroidTest")

$root = Split-Path $PSScriptRoot -Parent
Set-Location $root
$sdk = "$env:LOCALAPPDATA\Android\Sdk"
$env:ANDROID_HOME = $sdk
$adb = "$sdk\platform-tools\adb.exe"

function Find-Brook36 {
    & $adb devices | Select-String '^(emulator-\d+)\s+device' | ForEach-Object { $_.Matches[0].Groups[1].Value } |
        Where-Object { ((& $adb -s $_ emu avd name) | Select-Object -First 1).Trim() -eq 'brook36' } | Select-Object -First 1
}
$serial = Find-Brook36
if (-not $serial) {
    # 没开就开，开机最多等 3 分钟（不算在测试的时限里）
    Start-Process "$sdk\emulator\emulator.exe" -ArgumentList '-avd brook36 -no-snapshot -no-audio' -WindowStyle Minimized
    $deadline = (Get-Date).AddMinutes(3)
    while ((Get-Date) -lt $deadline) {
        Start-Sleep -Seconds 3
        $serial = Find-Brook36
        if ($serial -and (& $adb -s $serial shell getprop sys.boot_completed 2>$null) -match '1' -and
            (& $adb -s $serial shell dumpsys user 2>$null | Select-String RUNNING_UNLOCKED)) { break }
        $serial = $null
    }
    if (-not $serial) { Write-Output "brook36 3 分钟内没开好机"; exit 3 }
}
$env:ANDROID_SERIAL = $serial

$module = $Task.Split(':', [StringSplitOptions]::RemoveEmptyEntries)[0]
$results = "$module\build\outputs\androidTest-results\connected"
$collected = "build\connected-test-results"
Remove-Item -Recurse -Force $collected -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force $collected | Out-Null

# AGP 9 的 class 参数只认逗号前的第一个：多个类名拆开逐个跑（第二个起不用重新编译，每个只多十几秒），总时限不变
$entries = if ($Class) { $Class -split ',' | ForEach-Object { $_.Trim() } | Where-Object { $_ } } else { @('') }
$start = Get-Date
$deadline = $start.AddMinutes($Minutes)
$exitCode = 0
$n = 0
foreach ($entry in $entries) {
    $n++
    Remove-Item -Recurse -Force $results -ErrorAction SilentlyContinue
    # 命令写进 .cmd 再执行，参数原样交给 Gradle
    $line = "call `"$root\gradlew.bat`" $Task"
    if ($entry) { $line += " `"-Pandroid.testInstrumentationRunnerArguments.class=$entry`"" }
    Set-Content -Path build\connected-test.cmd -Value "@echo off`r`n$line" -Encoding ascii
    $p = Start-Process -FilePath cmd.exe -ArgumentList '/c', "`"$root\build\connected-test.cmd`"" -NoNewWindow -PassThru `
        -RedirectStandardOutput build\connected-test.log -RedirectStandardError build\connected-test.err
    $left = [int][Math]::Max(1000, ($deadline - (Get-Date)).TotalMilliseconds)
    if (-not $p.WaitForExit($left)) {
        & $adb -s $serial emu kill | Out-Null
        & taskkill /T /F /PID $p.Id 2>&1 | Out-Null
        & "$root\gradlew.bat" --stop 2>&1 | Out-Null
        Write-Output "超过 $Minutes 分钟（跑到 $entry），已杀掉模拟器 $serial 和 Gradle。日志：build\connected-test.log"
        exit 2
    }
    $p.WaitForExit()
    if ($p.ExitCode -ne 0) { $exitCode = $p.ExitCode }
    Get-ChildItem -Recurse $results -Filter 'TEST-*.xml' -ErrorAction SilentlyContinue |
        ForEach-Object { Copy-Item $_.FullName "$collected\$n-$($_.Name)" }
}

$min = ((Get-Date) - $start).TotalMinutes
Write-Output ("用时 {0:N1} 分钟，Gradle 退出码 {1}" -f $min, $exitCode)
$xml = Get-ChildItem $collected -Filter '*.xml' -ErrorAction SilentlyContinue
if (-not $xml) {
    Write-Output "没有测试结果，Gradle 输出最后几行："
    Get-Content build\connected-test.log, build\connected-test.err -Tail 15
    exit 1
}
foreach ($f in $xml) {
    foreach ($s in ([xml](Get-Content $f.FullName -Raw)).testsuites.testsuite) {
        Write-Output ("{0}: {1} 个，失败 {2}，{3:N0} 秒" -f ($s.name -replace '.*\.', ''), $s.tests, $s.failures, [double]$s.time)
        foreach ($c in $s.testcase) {
            if ($c.failure) {
                $msg = if ($c.failure -is [string]) { $c.failure } else { $c.failure.InnerText }
                Write-Output ("  失败 {0}: {1}" -f $c.name, ($msg -split "`n")[0])
            }
        }
    }
}
exit $exitCode
