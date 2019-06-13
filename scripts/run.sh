#!/bin/bash -l

set -ex

SCRIPT=$(readlink -f "$0")
STEWARD_DIR=$(dirname "$SCRIPT")/..
cd "$STEWARD_DIR"
git pull
sbt -no-colors ";clean ;core/assembly"
JAR=$(find -name "*assembly*.jar" | head -n1)

LOGIN="scala-steward"
java -jar ${JAR} \
  --workspace  "$STEWARD_DIR/workspace" \
  --repos-file "$STEWARD_DIR/repos.md" \
  --git-author-name "Scala Steward" \
  --git-author-email "me@$LOGIN.org" \
  --github-login ${LOGIN} \
  --git-ask-pass "$HOME/.github/askpass/$LOGIN.sh" \
  --ignore-opts-files \
  --sign-commits \
  --whitelist $HOME/.cache/coursier \
  --whitelist $HOME/.coursier \
  --whitelist $HOME/.ivy2 \
  --whitelist $HOME/.sbt \
  --whitelist $HOME/.scio-ideaPluginIC \
  --whitelist $HOME/.tagless-redux-ijextPluginIC \
  --whitelist $JAVA_HOME \
  --read-only $JAVA_HOME
