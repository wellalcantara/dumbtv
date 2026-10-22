#!/usr/bin/env bash
# Installs dumbtv weather (weather/), the small helper that gives dumbtv home its forecast,
# and tells it where the TV is. The launcher itself has no internet permission; the helper
# fetches from Open-Meteo (no key, no account) only while the home screen is open, at most
# every 30 minutes. Opt-in: nothing else in this repo runs it.
#
# Usage: ./scripts/weather.sh --lat -20.82 --lon -49.38 [--fahrenheit] [--dry-run] [-y]
#        ./scripts/weather.sh --remove [-y]
# Coordinates go to the TV only, never into the repo. Two decimals (about 1 km) are plenty.
# Needs a JDK 17 and the Android SDK to build the helper (weather/build.sh), as launcher/ does.
set -uo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

LAT="" LON="" UNIT="" REMOVE=0
args=()
while [[ $# -gt 0 ]]; do
  case $1 in
    --lat) LAT=${2:-}; shift 2 ;;
    --lon) LON=${2:-}; shift 2 ;;
    --fahrenheit) UNIT=fahrenheit; shift ;;
    --remove) REMOVE=1; shift ;;
    *) args+=("$1"); shift ;;
  esac
done
parse_flags "${args[@]+"${args[@]}"}"
need_device

PKG=tv.dumbtv.weather
FILES=/sdcard/Android/data/$PKG/files
APK=$ROOT/weather/build/dumbtv-weather.apk

if [[ $REMOVE == 1 ]]; then
  step "remove"
  confirm "Uninstall dumbtv weather from $SERIAL? The home screen hides the forecast." || die "cancelled"
  uid=$(uid_of $PKG)
  [[ -n $uid ]] && acmd cmd netpolicy remove restrict-background-whitelist "$uid" >/dev/null
  acmd pm uninstall $PKG >/dev/null
  note "removed"
  exit 0
fi

valid() { [[ $1 =~ ^-?[0-9]{1,3}(\.[0-9]{1,6})?$ ]]; }
valid "$LAT" && valid "$LON" ||
  die "give the TV's place as decimal degrees: --lat -20.82 --lon -49.38 (a map app shows them)"

step "plan"
note "build and install $PKG (internet permission, no activity, no background service)"
note "write $FILES/weather.conf: lat=$LAT lon=$LON${UNIT:+ unit=$UNIT}"
note "allow its background data, which lockdown.sh's data saver would otherwise cut"
note "dumbtv home shows the forecast under the rows; Options › Weather hides it"
if [[ $DRY_RUN == 1 ]]; then
  note "[dry-run] weather/build.sh"
  note "[dry-run] adb install -r $APK"
  note "[dry-run] push weather.conf, cmd netpolicy add restrict-background-whitelist <uid>"
  exit 0
fi
confirm "Install dumbtv weather on $SERIAL?" || die "cancelled"

step "build"
if [[ -f $APK ]]; then
  note "using ${APK#$ROOT/} (delete it to rebuild)"
else
  "$ROOT/weather/build.sh" || die "build failed (see above)"
fi

step "install"
out=$("$ADB" -s "$SERIAL" install -r "$APK" 2>&1 | grep -m1 -E 'Success|Failure|failed' || true)
[[ $out == Success* ]] || die "install failed: $out"
note "installed"

step "place"
CONF_TMP=$(mktemp "${TMPDIR:-/tmp}/dumbtv-weather.XXXXXX") || die "could not create a temp file"
trap 'rm -f "$CONF_TMP"' EXIT
{
  printf '# dumbtv weather: where the forecast is for\nlat=%s\nlon=%s\n' "$LAT" "$LON"
  [[ -n $UNIT ]] && printf 'unit=%s\n' "$UNIT"
} > "$CONF_TMP"
dev "mkdir -p $FILES" >/dev/null
"$ADB" -s "$SERIAL" push "$CONF_TMP" "$FILES/weather.conf" >/dev/null 2>&1 || die "could not write weather.conf"
note "saved"

step "background data"
uid=$(uid_of $PKG)
[[ -n $uid ]] || die "could not read the app's uid"
acmd cmd netpolicy add restrict-background-whitelist "$uid" >/dev/null
note "allowed (uid $uid)"

step "first forecast"
dev "content query --uri content://$PKG/forecast --projection status" >/dev/null
for _ in 1 2 3 4 5 6; do
  sleep 3
  row=$(dev "content query --uri content://$PKG/forecast --projection fetched:status" | tr -d '\r')
  [[ $row == *fetched=* && $row != *fetched=0,* ]] && break
done
case $row in
  *status=ok*) [[ $row == *fetched=0,* ]] && note "no forecast yet; it comes the next time the home screen opens" ||
                 note "forecast in; open the home screen" ;;
  *) note "the helper says: ${row##*status=}"
     note "no-location: weather.conf did not take; http/error: the TV could not reach api.open-meteo.com" ;;
esac
