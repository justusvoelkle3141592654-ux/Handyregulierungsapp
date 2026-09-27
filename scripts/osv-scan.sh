#!/usr/bin/env bash
# Checks all release runtime dependencies against the public OSV vulnerability database.
# Sends only Maven coordinates (group:artifact:version) to api.osv.dev, no project code.
set -euo pipefail
cd "$(dirname "$0")/.."
./gradlew -q :app:dependencies --configuration releaseRuntimeClasspath 2>/dev/null \
  | grep -oE '[a-zA-Z0-9_.-]+:[a-zA-Z0-9_.-]+:[0-9][^ )]*( -> [0-9][^ )]*)?' \
  | sed -E 's/:([^:]+) -> (.*)$/:\2/' | sort -u \
  | python3 -c '
import json, sys, urllib.request
deps = [l.strip() for l in sys.stdin if l.count(":") == 2]
queries = [{"package": {"name": f"{g}:{a}", "ecosystem": "Maven"}, "version": v} for g, a, v in (d.split(":") for d in deps)]
req = urllib.request.Request("https://api.osv.dev/v1/querybatch", json.dumps({"queries": queries}).encode(), {"Content-Type": "application/json"})
results = json.load(urllib.request.urlopen(req))["results"]
hits = [(deps[i], [v["id"] for v in r["vulns"]]) for i, r in enumerate(results) if r.get("vulns")]
print(f"Scanned {len(deps)} packages, {len(hits)} with known vulnerabilities")
for dep, ids in hits:
    print("  " + dep + ": " + ", ".join(ids))
sys.exit(1 if hits else 0)
'
