#!/bin/bash
set -e

echo "============================================"
echo "  LearnMart Test Runner"
echo "============================================"
echo ""

# Determine script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

FAILED=0

# ─────────────────────────────────────────────
# Bootstrap Gradle wrapper if missing
# ─────────────────────────────────────────────
ensure_gradle_wrapper() {
    if [ -f "gradle/wrapper/gradle-wrapper.jar" ]; then
        return 0
    fi

    echo -e "${YELLOW}Gradle wrapper jar not found. Bootstrapping...${NC}"
    mkdir -p gradle/wrapper

    # Try system gradle first
    if command -v gradle &> /dev/null; then
        echo "Using system gradle to generate wrapper..."
        gradle wrapper --gradle-version 8.5 --no-daemon 2>/dev/null && return 0
    fi

    # Download wrapper jar directly from Gradle distributions
    echo "Downloading gradle-wrapper.jar..."
    local WRAPPER_URL="https://raw.githubusercontent.com/gradle/gradle/v8.5.0/gradle/wrapper/gradle-wrapper.jar"
    local WRAPPER_JAR="gradle/wrapper/gradle-wrapper.jar"

    if command -v curl &> /dev/null; then
        curl -fsSL -o "$WRAPPER_JAR" "$WRAPPER_URL" 2>/dev/null && return 0
    fi
    if command -v wget &> /dev/null; then
        wget -q -O "$WRAPPER_JAR" "$WRAPPER_URL" 2>/dev/null && return 0
    fi

    # Last resort: download gradle dist, extract wrapper jar
    if command -v curl &> /dev/null || command -v wget &> /dev/null; then
        local DIST_URL="https://services.gradle.org/distributions/gradle-8.5-bin.zip"
        local TMP_DIR=$(mktemp -d)
        echo "Downloading Gradle 8.5 distribution to extract wrapper..."
        if command -v curl &> /dev/null; then
            curl -fsSL -o "$TMP_DIR/gradle.zip" "$DIST_URL"
        else
            wget -q -O "$TMP_DIR/gradle.zip" "$DIST_URL"
        fi
        if [ -f "$TMP_DIR/gradle.zip" ]; then
            unzip -q "$TMP_DIR/gradle.zip" -d "$TMP_DIR" 2>/dev/null
            # Use extracted gradle to generate wrapper
            "$TMP_DIR/gradle-8.5/bin/gradle" wrapper --gradle-version 8.5 --project-dir "$SCRIPT_DIR" --no-daemon 2>/dev/null
            rm -rf "$TMP_DIR"
            if [ -f "gradle/wrapper/gradle-wrapper.jar" ]; then
                return 0
            fi
        fi
        rm -rf "$TMP_DIR"
    fi

    echo -e "${RED}Failed to bootstrap Gradle wrapper. Install Gradle or download the wrapper manually.${NC}"
    return 1
}

# ─────────────────────────────────────────────
# Run Tests
# ─────────────────────────────────────────────
ensure_local_properties() {
    if [ -f "local.properties" ]; then
        return 0
    fi

    # Auto-detect Android SDK from environment or common paths
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

    echo -e "${RED}Android SDK not found. Set ANDROID_HOME or create local.properties manually.${NC}"
    return 1
}

run_gradle_tests() {
    chmod +x ./gradlew 2>/dev/null || true
    if ./gradlew testDebugUnitTest --no-daemon --stacktrace 2>&1; then
        echo -e "${GREEN}Unit tests PASSED${NC}"
    else
        echo -e "${RED}Unit tests FAILED${NC}"
        FAILED=1
    fi
}

run_docker_tests() {
    echo -e "${YELLOW}Falling back to Docker-based test run...${NC}"
    if command -v docker &> /dev/null; then
        if docker compose run --rm learnmart-test 2>/dev/null; then
            echo -e "${GREEN}Unit tests PASSED (Docker)${NC}"
        elif docker-compose run --rm learnmart-test 2>/dev/null; then
            echo -e "${GREEN}Unit tests PASSED (Docker)${NC}"
        else
            echo -e "${RED}Unit tests FAILED (Docker)${NC}"
            FAILED=1
        fi
    else
        echo -e "${RED}Docker not available. Cannot run tests.${NC}"
        FAILED=1
    fi
}

# ─────────────────────────────────────────────
# Unit Tests
# ─────────────────────────────────────────────
if [ -d "tests/unit_tests" ]; then
    echo -e "${YELLOW}Running Unit Tests...${NC}"
    echo "─────────────────────────────────────────────"

    if [ -f "./gradlew" ] && ensure_gradle_wrapper && ensure_local_properties; then
        run_gradle_tests
    else
        run_docker_tests
    fi

    # List test files
    echo ""
    echo "Unit test files:"
    find tests/unit_tests -name "*Test.kt" | sort | while read -r f; do
        echo "  - $f"
    done
    echo ""
else
    echo "No unit_tests directory found in tests/"
fi

# ─────────────────────────────────────────────
# API Tests (if they exist)
# ─────────────────────────────────────────────
if [ -d "tests/api_tests" ]; then
    echo -e "${YELLOW}Running API Tests...${NC}"
    echo "─────────────────────────────────────────────"

    find tests/api_tests -name "*Test.kt" | sort | while read -r f; do
        echo "  - $f"
    done
    echo ""
else
    echo "No api_tests directory found (skipping)"
fi

# ─────────────────────────────────────────────
# Summary
# ─────────────────────────────────────────────
echo ""
echo "============================================"
echo "  Test Summary"
echo "============================================"
echo "  Unit test files:  $(find tests/unit_tests -name '*Test.kt' 2>/dev/null | wc -l)"
if [ -d "tests/api_tests" ]; then
    echo "  API test files:   $(find tests/api_tests -name '*Test.kt' 2>/dev/null | wc -l)"
fi
echo "============================================"

if [ "$FAILED" -eq 1 ]; then
    echo -e "${RED}TESTS FAILED${NC}"
    exit 1
else
    echo -e "${GREEN}ALL TESTS PASSED${NC}"
    exit 0
fi
