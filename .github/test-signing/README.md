Legacy public test key for `-test` pre-releases (password `glowtest`, alias `glowtest`).

Because this key is public, anyone could sign an APK with it. It is only used while the
`TEST_KEYSTORE`, `TEST_KEYSTORE_PASSWORD`, `TEST_KEY_ALIAS` and `TEST_KEY_PASSWORD`
repository secrets are missing, so that test builds installed so far keep updating.

To retire it: create a private test key, add those four secrets, reinstall the next test
build once, then delete this folder (and remove it from the git history before the
repository goes public).

Real releases are always signed with the private key from the `KEYSTORE*` secrets.
