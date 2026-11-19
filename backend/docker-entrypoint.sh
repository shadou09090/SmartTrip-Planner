#!/bin/sh
set -eu

if [ -n "${ORACLE_WALLET_ARCHIVE_B64_FILE:-}" ]; then
    if [ ! -r "$ORACLE_WALLET_ARCHIVE_B64_FILE" ]; then
        echo "Configured Oracle Wallet secret file is not readable." >&2
        exit 1
    fi
    mkdir -p "$ORACLE_WALLET_PATH"
    base64 --decode "$ORACLE_WALLET_ARCHIVE_B64_FILE" > /tmp/oracle-wallet.zip
    unzip -o -qq /tmp/oracle-wallet.zip -d "$ORACLE_WALLET_PATH"
    rm -f /tmp/oracle-wallet.zip
fi

if [ ! -r "$ORACLE_WALLET_PATH/tnsnames.ora" ] || [ ! -r "$ORACLE_WALLET_PATH/cwallet.sso" ]; then
    echo "Oracle Wallet files are missing from ORACLE_WALLET_PATH." >&2
    exit 1
fi

exec java -jar /app/app.jar
