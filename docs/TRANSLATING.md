<!-- Copyright 2026 Cole Munz -->
<!-- SPDX-License-Identifier: AGPL-3.0-only -->

# Translating Wren

Wren's own strings are English only today. Everything Signal and Molly wrote
is translated into dozens of languages and Wren inherits that, so a user in
German sees German almost everywhere. The parts I added show up in English:
the duress and panic settings, the device check, the black theme and chat
export. For a feature that can erase your messages, that is a real gap, and
this page is how to close it.

There is no Weblate or Transifex project for Wren, and I am not promising one.
Until there is, translations come in as pull requests or as files attached to
an issue.

## Which files hold Wren's strings

- `app/src/main/res/values/strings2.xml` is Molly's file for the strings Signal does not have.
  I appended Wren's settings strings to it: the `DeviceCheck__` names, the
  `DuressPassphraseDialogFragment__` names, the duress, unlock limit and
  panic entries named `PrivacySettingsFragment__duress...`,
  `__wipe_after_failed_unlock_attempts...`, `__panic...`, `__lock_app`,
  `__erase_all_data`, `__connected_trigger_app` and the trigger and
  disconnect ones, `ChangePassphraseDialogFragment__this_is_your_duress_passphrase`
  and `preferences__black_theme`.
- `app/src/main/res/values/strings.xml` is Signal's file. Wren's export strings sit inside it:
  the `ChatExportDialog__` and `ChatExportJob__` names,
  `ChatsSettingsFragment__export_all_chats` and its label, and
  `ConversationSettingsFragment__export_chat`.

This lists them, about a hundred:

```sh
grep -hoE 'name="(DeviceCheck__|DuressPassphraseDialogFragment__|ChatExportDialog__|ChatExportJob__|ChatsSettingsFragment__export_all|ConversationSettingsFragment__export_chat|ChangePassphraseDialogFragment__this_is_your_duress|preferences__black_theme|PrivacySettingsFragment__(duress|change_duress|wipe|d_failed|panic|lock_app|erase|connected|no_trigger|trigger|disconnect|off))[^"]*"' \
  app/src/main/res/values/strings.xml app/src/main/res/values/strings2.xml
```

Strings marked `translatable="false"` stay as they are.

## Where a translation goes

Put every translated Wren string in `values-<locale>/strings2.xml`, including
the export strings whose English text lives in Signal's `strings.xml`.

Do not put them in `values-<locale>/strings.xml`. `.gitattributes` marks those
files `merge=theirs`, so every time I merge a Signal release, Signal's version
replaces the file whole and anything I added there is gone. Signal has no
`strings2.xml`, so merges leave it alone. The same string name in two
different folders (English in `app/src/main/res/values/strings.xml`, German in
`app/src/main/res/values-de/strings2.xml`) is fine; Android only complains about the same name
twice in one folder.

Use the folder names already in the tree, not newer spellings. Indonesian is
`values-in`, Hebrew is `values-iw`, Brazilian Portuguese is `values-pt-rBR`,
and Chinese comes as `values-zh-rCN`, `values-zh-rHK` and `values-zh-rTW`.
67 locales have a `strings2.xml` today. If yours has none, create it with the
same XML header as `app/src/main/res/values/strings2.xml`. Lint ignores missing translations
(`lint.xml`), so a partial file is fine and anything you leave out shows in
English.

## How tools/rebrand.py touches translations

Molly's build takes the app name from `app/gradle.properties`, but its
translated strings spell the name out. `tools/rebrand.py` goes through every
`values*/strings*.xml`, including every locale's `strings2.xml`, and rewrites
`Molly` to `Wren`, except in `MollySocket`. It also swaps Molly's install
link for Wren's. I run it after every merge. `python3 tools/rebrand.py --check`
lists files that still say Molly, and `tools/release-check.sh` fails on any
`Molly` left in the resources other than `Theme.Molly...` style names and
`MollySocket`.

So write `Wren` in your translation. If you write `Molly` it will be
rewritten, and it is not what you meant. You do not need to run the script.
Do not add `mollyify="true"` to a string: that attribute belongs to a Gradle
task (`build-logic/plugins/src/main/java/mollyify.gradle.kts`) that turns
Signal's name into the app name in Signal's own strings during a merge, and
Wren's strings already say Wren.

## Rules for the XML

- Keep the string `name` exactly as in English.
- Keep every placeholder, `%1$s`, `%2$d` and so on, and keep their numbers.
  The order in the sentence can change; the numbers cannot.
- Escape an apostrophe as `\'` and an ampersand as `&amp;`, as the English
  file does. Keep `\n` where the English has it.
- Do not translate `Wren`, `Signal`, `PanicKit`, `Ripple`, `HTML` or `JSON`.
- No machine-translated files, please. Say which tool you used if you did use
  one, and I will mark it for a second reader.

## The strings that matter most

Most of these strings are labels. A few of them decide whether someone loses
their data, and a bad translation of those is a safety bug. Please be exact
with the ones that say erase, wipe or delete:

- `PrivacySettingsFragment__duress_passphrase_summary`
- `PrivacySettingsFragment__wipe_after_failed_unlock_attempts_summary`
- `PrivacySettingsFragment__erase_all_data`
- `PrivacySettingsFragment__panic_action_summary`
- `PrivacySettingsFragment__disconnect_trigger_message`
- the `DuressPassphraseDialogFragment__` strings

Each must say plainly that it erases everything on this phone, right away,
with no undo. I cannot read most of these languages, so for those strings I
would like a second native reader before I merge them. That is a request, not
a rule I can enforce.

## How to send it

1. Fork the repository and edit or create
   `app/src/main/res/values-<locale>/strings2.xml`, with only Wren's strings.
2. Run `python3 tools/rebrand.py --check` and make sure it says it would
   change 0 files.
3. If you can, build it: `./gradlew :app:assembleProdWebsiteDebug` compiles
   resources, so malformed XML or a duplicate name fails the build. Say in the
   pull request if you could not.
4. Open a pull request, or if git is not your thing, open an issue and attach
   the file. Say which language and region, and whether you are a native
   speaker. For anything about a security problem in a string, email the
   address in [SECURITY.md](../SECURITY.md) instead of opening a public issue.

If Wren ever gets a hosted translation project, this page changes to point at
it, and the files above stay where they are.
