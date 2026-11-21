#!/bin/sh
# Create a self-signed code-signing cert and sign a Niagara module jar.
# The keystore is written under signing/ and is gitignored. Never commit it.
#
# Usage:
#   scripts/sign-module.sh niagara-git-rt/build/libs/niagara-git-rt.jar
#
# Optional environment:
#   NIAGARA_GIT_ALIAS   (default niagara-git)
#   NIAGARA_GIT_DNAME   (default CN=Bryan Williams, OU=Controls, O=bew1769, C=US)
set -eu

jar=${1:-}
if [ -z "$jar" ] || [ ! -f "$jar" ]; then
  echo "usage: scripts/sign-module.sh path/to/niagara-git-rt.jar" >&2
  exit 2
fi

root=$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)
signing=$root/signing
alias=${NIAGARA_GIT_ALIAS:-niagara-git}
dname=${NIAGARA_GIT_DNAME:-CN=Bryan Williams, OU=Controls, O=bew1769, C=US}
ks=$signing/niagara-git-dev.jks
passfile=$signing/password.txt
cer=$signing/niagara-git.cer

mkdir -p "$signing"
if [ ! -f "$passfile" ]; then
  # Local password only. The file is gitignored with the rest of signing/.
  umask 077
  if command -v openssl >/dev/null 2>&1; then
    openssl rand -base64 24 > "$passfile"
  else
    # 24 bytes of /dev/urandom, base64-ish via od. Not a private key.
    od -An -tx1 -N24 /dev/urandom | tr -d ' \n' > "$passfile"
  fi
  echo "wrote $passfile (gitignored). Keep this with the keystore." >&2
fi
storepass=$(tr -d '\r\n' < "$passfile")

if [ ! -f "$ks" ]; then
  keytool -genkeypair \
    -alias "$alias" \
    -keyalg RSA \
    -keysize 2048 \
    -validity 3650 \
    -keystore "$ks" \
    -storetype JKS \
    -storepass "$storepass" \
    -keypass "$storepass" \
    -dname "$dname"
  echo "created $ks" >&2
fi

keytool -exportcert \
  -alias "$alias" \
  -keystore "$ks" \
  -storepass "$storepass" \
  -file "$cer"
echo "exported public cert $cer" >&2

jarsigner \
  -keystore "$ks" \
  -storetype JKS \
  -storepass "$storepass" \
  -keypass "$storepass" \
  -digestalg SHA-256 \
  -sigalg SHA256withRSA \
  "$jar" \
  "$alias"

jarsigner -verify "$jar"
echo "signed $jar" >&2
echo "Trust $cer on the JACE before Software Manager will install the module." >&2
