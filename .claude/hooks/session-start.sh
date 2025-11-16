#!/bin/bash
set -euo pipefail

# Only run this hook in Claude Code on the web (remote environment)
if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

echo "🔧 Setting up llm4s build environment..."

# Function to install SBT via launcher JAR (most reliable method)
install_sbt_launcher() {
  echo "  Installing SBT via launcher JAR..."
  local sbt_version="1.10.5"
  local install_dir="$HOME/.local/bin"

  mkdir -p "$install_dir"

  # Download launcher JAR
  if ! curl -fL "https://repo1.maven.org/maven2/org/scala-sbt/sbt-launch/${sbt_version}/sbt-launch-${sbt_version}.jar" \
       -o "$install_dir/sbt-launch.jar"; then
    echo "    Failed to download SBT launcher JAR"
    return 1
  fi

  # Create wrapper script
  cat > "$install_dir/sbt" << 'WRAPPER'
#!/bin/bash
java -Xms512M -Xmx1536M -Xss1M -jar "$HOME/.local/bin/sbt-launch.jar" "$@"
WRAPPER

  chmod +x "$install_dir/sbt"

  export PATH="$install_dir:$PATH"

  if [ -n "${CLAUDE_ENV_FILE:-}" ]; then
    echo "export PATH=\"$install_dir:\$PATH\"" >> "$CLAUDE_ENV_FILE"
  fi

  return 0
}

# Function to install SBT
install_sbt() {
  echo "📥 Installing SBT..."

  # Try installing via Coursier if available
  if command -v cs &> /dev/null; then
    echo "  Using Coursier to install SBT..."
    mkdir -p ~/.local/bin
    cs install sbt --dir ~/.local/bin
    export PATH="$HOME/.local/bin:$PATH"

    if [ -n "${CLAUDE_ENV_FILE:-}" ]; then
      echo 'export PATH="$HOME/.local/bin:$PATH"' >> "$CLAUDE_ENV_FILE"
    fi
    return 0
  fi

  # Try launcher JAR method (most reliable)
  if install_sbt_launcher; then
    return 0
  fi

  # Try installing via apt if available
  if command -v apt-get &> /dev/null && [ -w "/etc/apt/sources.list.d" ] 2>/dev/null; then
    echo "  Using apt to install SBT..."
    echo "deb https://repo.scala-sbt.org/scalasbt/debian all main" | sudo tee /etc/apt/sources.list.d/sbt.list > /dev/null 2>&1
    curl -sL "https://keyserver.ubuntu.com/pks/lookup?op=get&search=0x2EE0EA64E40A89B84B2DF73499E82A75642AC823" | sudo apt-key add - > /dev/null 2>&1
    sudo apt-get update -qq > /dev/null 2>&1
    sudo apt-get install -y sbt > /dev/null 2>&1
    return 0
  fi

  # Manual installation via Coursier as fallback
  echo "  Installing SBT via Coursier manually..."
  mkdir -p ~/.local/share/coursier
  curl -fLo ~/.local/share/coursier/cs https://github.com/coursier/launchers/raw/master/cs-x86_64-pc-linux || return 1
  chmod +x ~/.local/share/coursier/cs

  mkdir -p ~/.local/bin
  ~/.local/share/coursier/cs install sbt --dir ~/.local/bin || return 1

  export PATH="$HOME/.local/bin:$PATH"

  if [ -n "${CLAUDE_ENV_FILE:-}" ]; then
    echo 'export PATH="$HOME/.local/bin:$PATH"' >> "$CLAUDE_ENV_FILE"
  fi

  return 0
}

# Check if SBT is available, install if needed
if ! command -v sbt &> /dev/null; then
  if ! install_sbt; then
    echo "❌ Failed to install SBT. Please ensure:"
    echo "   - Network connectivity is available"
    echo "   - Either apt-get, coursier (cs), or curl is available"
    echo ""
    echo "You can manually install SBT by following:"
    echo "   https://www.scala-sbt.org/download.html"
    exit 1
  fi

  if ! command -v sbt &> /dev/null; then
    echo "❌ SBT installation completed but sbt command not found"
    exit 1
  fi

  echo "✅ SBT installed successfully"
else
  echo "✅ SBT already installed"
fi

# Download all SBT dependencies and compile
# This significantly speeds up subsequent compile/test operations
echo "📦 Downloading dependencies and compiling project..."
echo "   (This may take a few minutes on first run...)"

# Update downloads all dependencies
sbt -batch -no-colors update

# Compile the default Scala version
sbt -batch -no-colors compile

echo "✅ Build environment ready!"
echo "   You can now run: sbt test, sbt scalafmtCheckAll, etc."
