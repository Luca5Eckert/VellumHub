#!/usr/bin/env sh
set -eu

# The key directory is ignored by git. Never commit key material or copy it into a ConfigMap.
root="$(CDPATH= cd -- "$(dirname -- "$0")/../../.." && pwd)"
dir="$root/.local/auth-poc"
umask 077
mkdir -p "$dir"
if [ -e "$dir/signing-private.pem" ] || [ -e "$dir/signing-public.pem" ]; then
  echo "Refusing to overwrite existing signing keys: $dir" >&2
  exit 1
fi
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out "$dir/signing-private.pem"
openssl pkey -in "$dir/signing-private.pem" -pubout -out "$dir/signing-public.pem"
chmod 600 "$dir/signing-private.pem" "$dir/signing-public.pem"
echo "PoC-only keys generated under .local/auth-poc (excluded from git)."
