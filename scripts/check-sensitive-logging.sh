#!/usr/bin/env bash
set -euo pipefail

production_sources="app/src/main/java"

forbidden_logging='android\.util\.Log|\bLog\.[vdiewtf]\s*\(|\bprintln\s*\(|\bprintStackTrace\s*\(|System\.(out|err)|\bTimber\.|LoggerFactory|KotlinLogging|toStringWithSecret\s*\('
forbidden_dependencies='^[[:space:]]*(implementation|api|debugImplementation|runtimeOnly)[[:space:]]*\([^)]*(timber|slf4j|logback|kotlin-logging)'

if rg -n --glob '*.kt' --glob '*.java' "$forbidden_logging" "$production_sources"; then
    echo "Production logging is forbidden because wallet values may contain private data." >&2
    exit 1
fi

if rg -n -i "$forbidden_dependencies" app/build.gradle.kts; then
    echo "Logging frameworks require an explicit privacy review before they may be added." >&2
    exit 1
fi

echo "Sensitive logging check passed."
