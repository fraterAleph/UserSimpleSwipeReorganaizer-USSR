# Signing

Android refuses to install an unsigned APK, and it treats two APKs signed with different
keys as two unrelated apps: the second one will not install over the first. So every build
that is meant to reach a phone has to be signed, and every build in a series has to be
signed with the *same* key or each update becomes an uninstall-and-reinstall.

This directory holds the key used for **test builds only**.

## testing.keystore

Committed deliberately. Its password is `ussr-testing`, its alias is `ussr-testing`, and
both are written here in plain text on purpose — this key protects nothing.

What it buys: every CI build is signed with the same key, so a new test build installs over
the previous one without wiping the app's data, and it works with no repository secrets
configured at all.

What it costs: the key is public, so anyone can produce an APK that a device already holding
a test build will accept as an update. That is an acceptable trade for a sideloaded personal
tool and an unacceptable one for anything published. Never use this key for a real release.

## The real key

The release build uses a proper key whenever these four environment variables are present,
and silently falls back to the testing key when they are not:

    USSR_KEYSTORE_FILE       path to the .jks/.keystore file
    USSR_KEYSTORE_PASSWORD   password for the store
    USSR_KEY_ALIAS           alias of the key inside the store
    USSR_KEY_PASSWORD        password for that key

In CI these come from repository secrets; `.github/workflows/android.yml` decodes
`RELEASE_KEYSTORE_BASE64` to a file and passes the rest through. With none of them set the
workflow still produces installable APKs, just signed with the testing key.

Generating a real key is one command:

    keytool -genkeypair -v -keystore release.keystore -alias <alias> \
      -keyalg RSA -keysize 2048 -validity 10950

Back up the resulting file. Losing it means no future build can ever install over an
installed copy of the app — Android has no recovery path for that.
