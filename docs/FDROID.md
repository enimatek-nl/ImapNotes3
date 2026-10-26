# Publishing ImapNotesX on F-Droid

The important bit: **you never upload an APK to F-Droid.** F-Droid keeps a recipe
for the app in its own repository ([fdroiddata](https://gitlab.com/fdroid/fdroiddata))
and its buildbot compiles the app from a tag you push. So the "pipeline" is one
recipe file plus a habit of tagging.

## What is already in this repo

| piece | what it does |
| --- | --- |
| `fdroid/nl.enimatek.imapnotesx.yml` | the recipe, to be copied into fdroiddata as `metadata/nl.enimatek.imapnotesx.yml` |
| `scripts/release.sh` | bumps the version in `AndroidManifest.xml`, builds, commits, tags, pushes |
| `fastlane/metadata/android/*/` | store listing (title, descriptions, icon, changelogs per versionCode) |
| `.github/workflows/android-release.yml` | builds a release APK per tag and attaches it to the GitHub release (for people not using F-Droid) |

Everything the buildbot needs is in place: the release keystore is **not**
required (see `ImapNotesX/build.gradle`, the signing config is only wired up when
`keystore.jks` exists, otherwise the debug key is used and F-Droid re-signs with
its own), and the dependency versions are pinned (`FreeDroidWarn:V1.14` — a
dynamic `V1.+` version breaks F-Droid's reproducible-build checks).

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
   git rev-parse 'v1.5-01^{commit}'   # put this sha into the recipe's `commit:` field
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

**F-Droid needs no key from you.** It builds from source and signs with its own repository key, so
the recipe has no `Binaries:` line and no `AllowedAPKSigningKeys`. Nothing has to be configured on
GitHub for the F-Droid side.

The workflow only needs secrets if the APK attached to the **GitHub release** should be signed with
a stable key (`KEYSTORE_BASE64` plus `IMAPNOTES_KEYSTORE_PASSWORD` / `IMAPNOTES_KEY_PASSWORD`).
Without them every CI run signs with a fresh *debug* key, so those APKs cannot update each other in
place. Also note that an F-Droid APK and a self-signed APK never update one another (different
signatures) — pick one channel for daily use.

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
```

## Fork-specific notes

* The applicationId is `nl.enimatek.imapnotesx`, so ImapNotesX and the original
  `de.niendo.ImapNotes3` install **side by side**; each keeps its own IMAP
  accounts (the account type / sync authority are derived from the applicationId)
  and both read the same notes on the mail server.
* Notes are not migrated between the two apps automatically — use the app's
  backup/restore or just let the fork sync the mailbox again.
* Because it is a different package, upstream's
  `AllowedAPKSigningKeys` entry does not apply and the recipe deliberately has
  no `Binaries:` line (the buildbot builds from source only).
* If the buildbot ever reports a JDK too old for the Android Gradle plugin,
  uncomment the `sudo:` block in the recipe (it installs openjdk-21) and push
  that to fdroiddata.

## Local builds, for comparison

```sh
./gradlew assembleDebug          # debug-signed unless keystore.jks exists
./gradlew assembleRelease        # same: debug-signed without the keystore
```

The GitHub workflow does the same on a tag; if the repository secrets
`KEYSTORE_BASE64` (+ optionally `IMAPNOTES_KEYSTORE_PASSWORD` /
`IMAPNOTES_KEY_PASSWORD`) are set, it signs with the project key instead.
