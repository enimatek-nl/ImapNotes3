# Publishing ImapNotesX on F-Droid

The important bit: **you never upload an APK to F-Droid.** F-Droid keeps a recipe
for the app in its own repository ([fdroiddata](https://gitlab.com/fdroid/fdroiddata))
and its buildbot compiles the app from a tag you push. So the "pipeline" is one
recipe file plus a habit of tagging.

## What is already in this repo

| piece | what it does |
| --- | --- |
| `fdroid/nl.enimatek.imapnotesx.yml` | the recipe as submitted to fdroiddata (kept byte-identical to the file there; `rewritemeta` is picky, so never add comments) |
| `scripts/release.sh` | bumps the version in `AndroidManifest.xml`, builds, commits, tags, pushes |
| `fastlane/metadata/android/*/` | store listing (title, descriptions, icon, changelogs per versionCode) |
| `.github/workflows/android-release.yml` | builds a release APK per tag and attaches it to the GitHub release (for people not using F-Droid) |

Everything the buildbot needs is in place: local builds work without a keystore
(see `ImapNotesX/build.gradle`, the signing config is only wired up when
`keystore.jks` exists), while the GitHub workflow signs release APKs from the
`KEYSTORE_BASE64` secret, which the recipe's reproducible-build setup relies on
(see *Signing and keys*). The dependency versions are pinned (`FreeDroidWarn:V1.14`
— a dynamic `V1.+` version breaks F-Droid's reproducible-build checks).

## One-time: get the recipe merged

1. Tag the release first (see *Every release* below), so the commit the recipe points at is public.
2. Optional but recommended for first-time contributors: open a
   [Request For Packaging](https://gitlab.com/fdroid/rfp/-/issues) issue with their template.
3. Register on GitLab, fork <https://gitlab.com/fdroid/fdroiddata>, and work on a **branch named
   after the app id** — never commit to the fork's `master` (it is protected and triggers pipelines
   at the wrong time):

   ```sh
   git clone git@gitlab.com:<you>/fdroiddata.git && cd fdroiddata
   git checkout -b nl.enimatek.imapnotesx
   cp ~/Developer/ImapNotes3/fdroid/nl.enimatek.imapnotesx.yml metadata/
   git rev-parse 'v1.5-02^{commit}'   # put this sha into the recipe's `commit:` field
   git add metadata/nl.enimatek.imapnotesx.yml
   git commit -m "add nl.enimatek.imapnotesx"
   git push -u origin nl.enimatek.imapnotesx
   ```

4. Check the **CI/CD pipeline of your fork**: it runs the metadata linter. Fix it until it is green.
5. Open the merge request against `fdroiddata` master and fill in the MR template (commits are
   squashed by default). `AuthorEmail:` must be an address you actually read — F-Droid pings it.
6. A packager builds the app from that commit and reviews it; expect a round of comments the first
   time. Once merged, the app appears in the repository (usually within a day).

Optional local checks (`pip install git+https://gitlab.com/fdroid/fdroidserver.git`; `fdroid build`
itself needs Docker and is heavy): `fdroid readmeta`, `fdroid rewritemeta nl.enimatek.imapnotesx`,
`fdroid checkupdates nl.enimatek.imapnotesx`, `fdroid lint nl.enimatek.imapnotesx`.

(Do not register anything on f-droid.org beforehand — the merge request *is* the
registration. There is no APK upload path at all.)

## Signing and keys

Since v1.5-02 the recipe enables **reproducible builds**: it has a `Binaries:` line pointing at the
GitHub release APKs and `AllowedAPKSigningKeys` with our signing-key fingerprint
(`d6fbc35e…c22d`, the cert in `keystore.jks`). The buildbot compiles from source, compares its
output with our APK and then publishes *our signed* APK, so the F-Droid APK and the GitHub-release
APK carry the same signature and can update each other in place.

That makes the signing setup load-bearing on both sides:

* the GitHub workflow signs release APKs from the `KEYSTORE_BASE64` secret (+ optionally
  `IMAPNOTES_KEYSTORE_PASSWORD` / `IMAPNOTES_KEY_PASSWORD`); without it every CI run uses a fresh
  *debug* key and the reproducible-build job fails,
* `keystore.jks` must be backed up forever — losing the key breaks updates in both channels,
* changing the key means editing `AllowedAPKSigningKeys` in an fdroiddata MR, and users on the old
  key cannot update in place.

If reproducible builds were ever abandoned, drop `Binaries:` and `AllowedAPKSigningKeys` and the
buildbot falls back to signing with F-Droid's own repository key.

```sh
# only if you want signed GitHub release APKs — then back keystore.jks up forever
keytool -genkey -keystore keystore.jks -alias imapnotesx -keyalg RSA -keysize 2048 -validity 10000 -dname CN=ImapNotesX
base64 -i keystore.jks    # -> repository secret KEYSTORE_BASE64
```

## Every release

```sh
# 1. write the changelog for the new versionCode
$EDITOR fastlane/metadata/android/en-US/changelogs/10501.txt

# 2. bump, build, tag, push
scripts/release.sh v1.5-02 10501
```

That is it for F-Droid: the recipe has `AutoUpdateMode: Version` and
`UpdateCheckMode: Tags ^v[\d.-]+$`, so the buildbot sees the new tag, builds the
commit it points at and publishes it (typically within hours, sometimes a day).

Until the MR is merged, each new tag also needs the recipe updated by hand: replace
the single `Builds:` entry (versionName/versionCode/commit) and the two
`CurrentVersion` lines — only the latest version may be in the recipe before merge.
Bump the copy in this repo the same way so it stays the reference.

If you want to nudge it along, open a merge request in fdroiddata touching only

```yaml
CurrentVersion: v1.5-02
CurrentVersionCode: 10501
```

and mention the tag — the maintainers' bots then build it immediately.

## Version numbering

`versionCode` / `versionName` live in **`AndroidManifest.xml`** (not
`build.gradle`). Keep `versionCode` strictly increasing; the scheme used here
tracks upstream so upstream releases stay comparable:

```
upstream v1.4-07  -> versionCode 10407
fork     v1.5-01  -> versionCode 10500   (first ImapNotesX release)
fork     v1.5-02  -> versionCode 10501
fork     v1.5-03  -> versionCode 10502
```

## Fork-specific notes

* The applicationId is `nl.enimatek.imapnotesx`, so ImapNotesX and the original
  `de.niendo.ImapNotes3` install **side by side**; each keeps its own IMAP
  accounts (the account type / sync authority are derived from the applicationId)
  and both read the same notes on the mail server.
* Notes are not migrated between the two apps automatically — use the app's
  backup/restore or just let the fork sync the mailbox again.
* Because it is a different package, upstream's `AllowedAPKSigningKeys` entry does
  not apply; the recipe lists *our* key fingerprint instead (see *Signing and keys*).
* The recipe's `prebuild` deletes the `foojay-resolver` plugin line from
  `settings.gradle`; the toolchain auto-download conflicts with the build image.

## Local builds, for comparison

```sh
./gradlew assembleDebug          # debug-signed unless keystore.jks exists
./gradlew assembleRelease        # same: debug-signed without the keystore
```

The GitHub workflow does the same on a tag; if the repository secrets
`KEYSTORE_BASE64` (+ optionally `IMAPNOTES_KEYSTORE_PASSWORD` /
`IMAPNOTES_KEY_PASSWORD`) are set, it signs with the project key instead.
