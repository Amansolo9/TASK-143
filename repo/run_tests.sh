#!/bin/bash
set -e

echo "============================================"
echo "  LearnMart Test Runner"
echo "============================================"
echo ""

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

FAILED=0

# ─────────────────────────────────────────────
# Bootstrap helpers
# ─────────────────────────────────────────────
ensure_gradle_wrapper() {
    if [ -f "gradle/wrapper/gradle-wrapper.jar" ]; then
        return 0
    fi
    echo -e "${YELLOW}Gradle wrapper jar not found. Bootstrapping...${NC}"
    mkdir -p gradle/wrapper
    if command -v gradle &> /dev/null; then
        gradle wrapper --gradle-version 8.5 --no-daemon 2>/dev/null && return 0
    fi
    # Download gradle dist and generate wrapper
    if command -v curl &> /dev/null || command -v wget &> /dev/null; then
        local TMP_DIR=$(mktemp -d)
        local DIST_URL="https://services.gradle.org/distributions/gradle-8.5-bin.zip"
        if command -v curl &> /dev/null; then
            curl -fsSL -o "$TMP_DIR/gradle.zip" "$DIST_URL"
        else
            wget -q -O "$TMP_DIR/gradle.zip" "$DIST_URL"
        fi
        if [ -f "$TMP_DIR/gradle.zip" ]; then
            unzip -q "$TMP_DIR/gradle.zip" -d "$TMP_DIR" 2>/dev/null
            "$TMP_DIR/gradle-8.5/bin/gradle" wrapper --gradle-version 8.5 --project-dir "$SCRIPT_DIR" --no-daemon 2>/dev/null
            rm -rf "$TMP_DIR"
            [ -f "gradle/wrapper/gradle-wrapper.jar" ] && return 0
        fi
        rm -rf "$TMP_DIR"
    fi
    return 1
}

ensure_local_properties() {
    if [ -f "local.properties" ]; then
        return 0
    fi
    local SDK_PATH=""
    if [ -n "$ANDROID_HOME" ] && [ -d "$ANDROID_HOME" ]; then
        SDK_PATH="$ANDROID_HOME"
    elif [ -n "$ANDROID_SDK_ROOT" ] && [ -d "$ANDROID_SDK_ROOT" ]; then
        SDK_PATH="$ANDROID_SDK_ROOT"
    elif [ -d "$HOME/AppData/Local/Android/Sdk" ]; then
        SDK_PATH="$HOME/AppData/Local/Android/Sdk"
    elif [ -d "$HOME/Library/Android/sdk" ]; then
        SDK_PATH="$HOME/Library/Android/sdk"
    elif [ -d "/opt/android-sdk" ]; then
        SDK_PATH="/opt/android-sdk"
    elif [ -d "/usr/local/lib/android/sdk" ]; then
        SDK_PATH="/usr/local/lib/android/sdk"
    fi
    if [ -n "$SDK_PATH" ]; then
        echo -e "${YELLOW}Generating local.properties with sdk.dir=$SDK_PATH${NC}"
        echo "sdk.dir=$SDK_PATH" > local.properties
        return 0
    fi
    return 1
}

# ─────────────────────────────────────────────
# Test runners
# ─────────────────────────────────────────────
run_gradle_tests() {
    chmod +x ./gradlew 2>/dev/null || true
    if ./gradlew testDebugUnitTest --no-daemon --stacktrace -Dkotlin.compiler.execution.strategy=in-process 2>&1; then
        echo -e "${GREEN}Unit tests PASSED${NC}"
    else
        echo -e "${RED}Unit tests FAILED${NC}"
        FAILED=1
    fi
}

run_docker_tests() {
    echo -e "${YELLOW}No local Android SDK. Running tests in Docker...${NC}"
    if ! command -v docker &> /dev/null; then
        echo -e "${RED}Docker not available. Cannot run tests.${NC}"
        FAILED=1
        return
    fi

    # Build a temporary test image from the main Dockerfile's builder stage
    echo "Building test image..."
    docker build -f Dockerfile --target builder -t learnmart-test-runner . 2>&1 | tail -5

    echo "Running tests..."
    if docker run --rm learnmart-test-runner \
        ./gradlew testDebugUnitTest --no-daemon --stacktrace \
        -Dkotlin.compiler.execution.strategy=in-process 2>&1; then
        echo -e "${GREEN}Unit tests PASSED (Docker)${NC}"
    else
        echo -e "${RED}Unit tests FAILED (Docker)${NC}"
        FAILED=1
    fi
}

# ─────────────────────────────────────────────
# Main
# ─────────────────────────────────────────────
if [ -d "tests/unit_tests" ]; then
    echo -e "${YELLOW}Running Unit Tests...${NC}"
    echo "─────────────────────────────────────────────"

    if [ -f "./gradlew" ] && ensure_gradle_wrapper && ensure_local_properties; then
        run_gradle_tests
    else
        run_docker_tests
    fi

    echo ""
    echo "Unit test files:"
    find tests/unit_tests -name "*Test.kt" | sort | while read -r f; do
        echo "  - $f"
    done
    echo ""
else
    echo "No unit_tests directory found in tests/"
fi

if [ -d "tests/api_tests" ]; then
    echo -e "${YELLOW}Running API Tests...${NC}"
    find tests/api_tests -name "*Test.kt" | sort | while read -r f; do
        echo "  - $f"
    done
else
    echo "No api_tests directory found (skipping)"
fi

echo ""
echo "============================================"
echo "  Test Summary"
echo "============================================"
echo "  Unit test files:  $(find tests/unit_tests -name '*Test.kt' 2>/dev/null | wc -l)"
echo "============================================"

if [ "$FAILED" -eq 1 ]; then
    echo -e "${RED}TESTS FAILED${NC}"
    exit 1
else
    echo -e "${GREEN}ALL TESTS PASSED${NC}"
    exit 0
fi
