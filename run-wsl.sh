#!/usr/bin/env bash
#
# run-wsl.sh — run one Cucumber scenario (or tag expression) headed on WSL/Linux.
#
# Uses the portable Firefox + NSS profile that carries the Nasdaq eServices
# client certificate for mTLS. Chrome is intentionally NOT used here: Chrome 152
# on this box stalls any TLS handshake where an NSS client certificate is
# offered ("Timed out receiving message from renderer"), while Firefox works.
#
# Usage:
#   ./run-wsl.sh "@direct_dokobit_mobile_id"
#   HEADED=0 ./run-wsl.sh "@req:BP-01"     # headless run
#
set -euo pipefail

PROJECT="$(cd "$(dirname "$0")" && pwd)"
SCRATCH="${JCODE_SCRATCH_OVERRIDE:-$HOME/.jcode/scratch}"

JAVA_HOME_DEFAULT="$SCRATCH/jdk25/jdk-25.0.4.1+1"
FF_BIN="$SCRATCH/firefox/firefox"
FF_PROFILE="$SCRATCH/firefox-profile"
GECKO_BIN="$SCRATCH/geckodriver"
LIBDIR="$SCRATCH/chrome-ld/lib/usr/lib/x86_64-linux-gnu"
P12="$SCRATCH/chrome-ld/chrome-client-modern.p12"

for f in "$JAVA_HOME_DEFAULT/bin/java" "$FF_BIN" "$GECKO_BIN"; do
  if [ ! -x "$f" ]; then
    echo "ERROR: missing required file: $f" >&2
    echo "       set JCODE_SCRATCH_OVERRIDE or provision the scratch tools first." >&2
    exit 1
  fi
done

export JAVA_HOME="$JAVA_HOME_DEFAULT"
export PATH="$JAVA_HOME/bin:$PATH"
[ -d "$LIBDIR" ] && export LD_LIBRARY_PATH="$LIBDIR${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"

export HEADED="${HEADED:-true}"
export OHTEST_BROWSER=firefox
export OHTEST_FIREFOX_BINARY="$FF_BIN"
export FIREFOX_PROFILE="$FF_PROFILE"
export WEBDRIVER_GECKO_DRIVER="$GECKO_BIN"

# JVM-side mTLS for Java HTTP clients (browser uses the NSS cert in FF_PROFILE).
if [ -f "$P12" ]; then
  export CLIENT_CERT_PATH="$P12"
  export CLIENT_CERT_PASSWORD="${CLIENT_CERT_PASSWORD:-swhsets}"
fi

TAGS="${1:?usage: ./run-wsl.sh \"<cucumber tag expression>\"}"
DISPLAY="${DISPLAY:-:0}"

cd "$PROJECT"
# cleanTest forces the test task to actually run instead of being UP-TO-DATE.
exec sh ./gradlew --no-daemon cleanTest test \
  -PmaxParallelForks=1 \
  "-Pcucumber.filter.tags=$TAGS"
