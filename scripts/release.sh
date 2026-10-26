#!/bin/sh
# ImapNotesX release helper.
#
#   scripts/release.sh <versionName> <versionCode>
#
# Bumps android:versionName / android:versionCode in AndroidManifest.xml, commits
# the change, tags it and pushes tag + branch. F-Droid then picks the tag up
# (AutoUpdateMode: Version) and builds it - see docs/FDROID.md.
#
# A changelog for the store listing is expected at
#   fastlane/metadata/android/en-US/changelogs/<versionCode>.txt
set -e

MANIFEST=ImapNotesX/src/main/AndroidManifest.xml
NAME=$1
CODE=$2

if [ -z "$NAME" ] || [ -z "$CODE" ]; then
    echo "usage: $0 <versionName> <versionCode>   e.g. $0 v1.5-02 10501" >&2
    exit 1
fi
BRANCH=$(git rev-parse --abbrev-ref HEAD)
if [ "$BRANCH" != main ]; then
    echo "releases are tagged on 'main' (you are on '$BRANCH')" >&2
    exit 1
fi
if ! git diff --quiet || ! git diff --cached --quiet; then
    echo "working tree is not clean - commit or stash first" >&2
    exit 1
fi
if [ ! -f "fastlane/metadata/android/en-US/changelogs/$CODE.txt" ]; then
    echo "missing fastlane/metadata/android/en-US/changelogs/$CODE.txt" >&2
    exit 1
fi

sed -e "s/android:versionCode=\"[0-9]*\"/android:versionCode=\"$CODE\"/" \
    -e "s/android:versionName=\"[^\"]*\"/android:versionName=\"$NAME\"/" \
    "$MANIFEST" > "$MANIFEST.tmp" && mv "$MANIFEST.tmp" "$MANIFEST"

# the build must actually work before a tag exists
./gradlew assembleRelease

git add "$MANIFEST"
git commit -m "release $NAME ($CODE)"
git tag "$NAME"
git push origin main
git push origin "$NAME"

echo
echo "$NAME tagged and pushed. Now:"
echo "  * GitHub Actions builds the release APK (the workflow runs on this tag)"
echo "  * F-Droid: bump CurrentVersion/CurrentVersionCode in fdroiddata, or just wait"
