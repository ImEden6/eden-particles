#!/usr/bin/env bash
# Starts one loader's dedicated server in <loader>/runs/smoke with the smoke datapack, and passes if the mod's
# smoke test (SmokeTest.java) reports success. Usage: ACCEPT_EULA=true scripts/smoke-server.sh fabric|neoforge|forge
set -euo pipefail

loader="${1:?usage: scripts/smoke-server.sh fabric|neoforge|forge}"
root="$(cd "$(dirname "$0")/.." && pwd)"
run="$root/$loader/runs/smoke"
case "$loader" in
    fabric | neoforge) task=":$loader:runSmokeServer" ;;
    forge) task=":forge:SmokeServer" ;;
    *) echo "unknown loader $loader" >&2; exit 2 ;;
esac

if [ "${ACCEPT_EULA:-}" != "true" ]; then
    echo "The server needs the Minecraft EULA (https://aka.ms/MinecraftEULA) accepted in $run." >&2
    echo "Set ACCEPT_EULA=true to accept it." >&2
    exit 2
fi

rm -rf "$run/world"
mkdir -p "$run/world/datapacks"
cp -r "$root/scripts/smoke-datapack" "$run/world/datapacks/eden_smoke"
echo "eula=true" > "$run/eula.txt"
cat > "$run/server.properties" << 'EOF'
level-type=minecraft:flat
generator-settings={"layers":[{"block":"minecraft:stone","height":1}],"biome":"minecraft:plains"}
generate-structures=false
online-mode=false
server-port=25599
EOF

log="$run/smoke-output.log"
status=0
timeout "${SMOKE_TIMEOUT:-900}" "$root/gradlew" -p "$root" "$task" --console=plain < /dev/null 2>&1 | tee "$log" || status=$?

if grep -q "Eden's Particles smoke test passed" "$log"; then
    echo "$loader: smoke test passed"
    exit 0
fi
echo "$loader: smoke test did not pass (exit $status); see $log" >&2
exit 1
