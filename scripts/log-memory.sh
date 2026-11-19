#!/usr/bin/env bash
# Prints the memory of the machine and of the JVMs every 20 seconds, in MB. A JVM group has the form
# `<name>=<count>x<total RSS>`. A runner that runs out of memory stops without a report, so the job log has the numbers.
set -euo pipefail

while sleep 20; do
  machine="$(free -m | awk '/^Mem:/ {printf "used=%d available=%d", $3, $7} /^Swap:/ {printf " swap=%d", $3}')"
  ps -eww -o rss=,args= | awk -v machine="$machine" '
    function add(name) { count[name]++; rss[name] += $1 }
    $2 == "awk" { next }
    /GradleDaemon/ { add(/-Xmx1500m/ ? "testKitDaemon" : "buildDaemon"); next }
    /KotlinCompileDaemon/ { add(/-Xmx1g/ ? "testKitKotlinDaemon" : "buildKotlinDaemon"); next }
    /Gradle Test Executor/ { add("testWorker"); next }
    /java/ { add("otherJvm") }
    END {
      line = "memory: " machine
      split("buildDaemon buildKotlinDaemon testWorker testKitDaemon testKitKotlinDaemon otherJvm", names, " ")
      for (i = 1; i <= 6; i++) if (names[i] in count) {
        line = line sprintf(" %s=%dx%d", names[i], count[names[i]], rss[names[i]] / 1024)
      }
      print line
    }'
done
