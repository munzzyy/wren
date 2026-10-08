# Exporting a chat

Signal and Molly can only make a full encrypted backup of everything. I wanted a way to save one
conversation as a file I can open on a computer, print, or keep after I leave a group, so Wren
has an "Export chat" option.

## How to use it

Open a chat, tap its name to get to the chat settings, and pick "Export chat". It works for
direct chats, groups and Note to Self.

You choose a format, whether to include media, and then a folder with the system folder picker.
The export runs in the background with a progress notification, and a second notification tells
you when it is done. Tapping that one tries to open the folder.

Wren makes a new folder inside the one you picked, named after the chat plus the date and time,
for example `Alice 2026-10-08 1403`. Inside it:

- `chat.html`, `chat.txt` or `chat.json`, depending on the format
- `media/` with the attachments, if you asked for media

Media files are named `<message id>-<n>.<ext>`, so two photos called `image.jpg` never overwrite
each other.

## Exporting every chat at once

Settings, Chats, "Export all chats" does the same thing for every chat on the phone, archived
ones included. If the phone has a screen lock, Wren asks for it first. You get the same format
and media choices and the same folder picker, and Wren makes one folder for the whole run:

```
Wren export 2026-10-08 1403/
  Alice/
    chat.html
    media/
  Book club/
    chat.html
  Note to Self/
    chat.html
```

Each chat folder looks exactly like a single chat export. When two chats have the same name the
second one becomes `Alice (2)`, and so on. Chats are exported newest first, and the progress
notification counts them ("Exporting chat 12 of 80").

Chats with no messages on this phone are skipped. If one chat can't be written, Wren deletes
that chat's half-written folder and moves on to the next one. The finished notification says how
many chats were written, how many were skipped as empty, and how many failed. If none could be
written, the whole folder is removed and you get a failure notification instead.

## Encrypting the export

Both export dialogs have an "Encrypt with a passphrase" box. It is off unless you tick it. With it
on, Wren asks for a passphrase twice (at least 8 characters; a few random words work well) and
writes one file instead of a folder:

- a single chat becomes `Wren chat 2026-10-08 1403.wrenx`
- all chats become `Wren export 2026-10-08 1403.wrenx`

Inside is exactly what the plain export would have made, folder names and all, packed as a tar
archive and encrypted as it is written. The single chat file name leaves the chat's name out on
purpose, because the file name is the one thing anyone can read.

To open it on a computer, use [`tools/wren-export-decrypt.py`](../tools/wren-export-decrypt.py):

```
python3 wren-export-decrypt.py "Wren chat 2026-10-08 1403.wrenx" out/
```

It asks for the passphrase, checks the whole file, and only then unpacks it into `out/`, which
must be new or empty. A wrong passphrase, a damaged file or a file someone changed leaves `out/`
untouched. The tool needs Python 3.8 or newer and the `cryptography` package
(`python3 -m pip install cryptography`, or `python3-cryptography` from your distribution).
Python has scrypt in its standard library but no AES at all, so a standard-library-only tool
was not possible; `cryptography` is the package most systems already have. The tool prints
install hints if it is missing.

How the passphrase is handled on the phone:

- It lives in memory only, from the dialog until the export job has derived the key, and is
  overwritten right after. It is never written to the job database, saved instance state or
  preferences. The cost: if Android kills Wren before the export finishes, the export fails
  with a notification asking you to start it again. It never falls back to a plain export.
- The passphrase is turned into bytes as Unicode NFC, UTF-8, on the phone and in the tool, so
  an accented letter typed on a different keyboard still matches.
- The passphrase fields have personalised keyboard learning turned off, and the dialog blocks
  screenshots while encryption is on.
- The chat file has to be complete before it can go into the archive, so Wren writes it to a
  temporary file in its own cache first. That file is encrypted with a random key that only
  exists in memory for that one chat, and it is deleted as soon as the chat is in the archive.
  Media is read twice instead (once to measure it, once to copy it), so no media is cached.

What it protects and what it does not:

- It protects the contents against anyone who gets the file but not the passphrase: a cloud
  folder, a lost USB stick, another app with storage access. AES-256-GCM authenticates every
  chunk, the header and the order of chunks, and the file has to end with its closing chunk, so
  a cut-off, reordered or edited file is rejected rather than half read.
- It does not hide the file name, the date in it, or the size, which roughly gives away how
  much was exported and whether media was included.
- Its strength is your passphrase. scrypt makes each guess cost about 32 MiB of memory and
  some CPU time, which slows a guessing attack down a lot but cannot save a short or common
  passphrase. Nobody, including Wren, can recover a forgotten one.
- Once someone decrypts the file, everything in it is plaintext again on their computer, and
  disappearing messages are in it as they were at export time.

## Canceling

Both progress notifications have a Cancel button. Wren stops after the message or the chunk of
media it is on and deletes everything that export wrote: the chat folder for a single chat, the
whole `Wren export` folder for all chats, or the `.wrenx` file. An export that is still waiting
behind another one is just dropped, and its passphrase is wiped.

## Formats

**HTML** (the default) is one file that opens in any browser. Styles are inline, there is no
JavaScript and nothing loads from the internet. Your own messages sit on the right, everyone
else's on the left, and system messages (timer changes, calls, people joining) are centered.
Photos and stickers show inline, voice messages, audio and video get a player, and other files
are links into `media/`. Link previews only become clickable when the address starts with
`http://` or `https://`.

**Plain text** puts each message in a block like this:

```
[2026-10-08 14:03] Alice: See you at six
  > You: What time works?
  Attachment: media/1234-1.jpg [image/jpeg, 182.4 KB]
  Reactions: 👍 You
```

Times in the HTML and text files use the phone's time zone at the moment of export.

**JSON** is for scripts. It is one object with a `chat` section and a `messages` array. Every
time is given twice, as epoch milliseconds and as an ISO-8601 string in UTC. Each attachment has
a `path` into `media/`, or `null` when the file is not in the export.

## What is in it

Every message in the chat that is still on this phone, oldest first: text with mentions turned
into names, quotes, reactions with who reacted, attachments, link previews, and system messages.
If a message was edited, the export has the latest version. Messages that disappear show their
timer.

## Limits

- **A plain export is not encrypted.** Anyone who can open that folder can read the chat. Put
  it somewhere you trust and delete it when you are done, or tick "Encrypt with a passphrase".
- Disappearing messages are exported as they are right now. The copy does not disappear.
  Think about that before exporting a chat where someone set a timer.
- Media that was never downloaded is skipped. It is listed with "not downloaded" so you know
  something was there. Download it in the chat first if you want it in the export.
- View-once media is never exported, and deleted messages show as a placeholder.
- Stickers are exported as their image, not as a sticker pack.
- Only messages on this phone are exported. That means nothing from before you joined a
  group, and nothing older than a restore or a cleared chat.
- Stories, scheduled messages that have not been sent yet, and older edits of a message are left
  out. Polls, payments and shared contacts come through only as whatever text they show in the
  chat.
- Exporting does not tell anyone in the chat.
- If Android kills Wren in the middle of an export, a plain export starts over in a new folder
  the next time Wren runs, and the half-written one from before stays where it is. An encrypted
  export cannot start over, because the passphrase is gone; you get a notification instead. A
  half-written `.wrenx` from the killed run stays behind, but it has no closing chunk, so the
  tool refuses it ("The file ends early"), and it is still encrypted.
- In an encrypted export of all chats, a chat that fails partway is counted as failed like in a
  plain export, but media it had already added stays in the archive (without that chat's file),
  because nothing can be taken back out of a stream. If writing the archive itself fails, the
  whole file is deleted.
- The Cancel button does nothing while Wren is locked.

Exported files are written straight to the folder you picked. If the export fails partway, Wren
deletes the half-written folder or file.

## File format

This is the whole `.wrenx` format, enough to write your own reader. All integers are unsigned
and big endian.

| Offset | Size | Field |
| --- | --- | --- |
| 0 | 6 | magic: `WRENX` followed by the byte `0x01` (format version 1) |
| 6 | 1 | KDF id: `1` = scrypt |
| 7 | 4 | scrypt N |
| 11 | 4 | scrypt r |
| 15 | 4 | scrypt p |
| 19 | 16 | salt, random per file |
| 35 | 8 | nonce prefix, random per file |
| 43 | ... | chunks |

Key: `scrypt(P, salt, N, r, p, dkLen = 32)` as in RFC 7914, where P is the passphrase in Unicode
NFC, encoded as UTF-8. Wren writes N = 2^15, r = 8, p = 1, which needs 32 MiB. The scrypt in
Wren is its own Kotlin implementation (`io.github.munzzyy.wren.crypto.Scrypt`), tested against
all four RFC 7914 test vectors and against Python's `hashlib.scrypt`. Readers should refuse
headers asking for more than N = 2^20, r = 8 or p = 4 (1 GiB), so a crafted file cannot make
them allocate without bound; Wren's tests and the Python tool do.

Each chunk is:

| Size | Field |
| --- | --- |
| 4 | L, the ciphertext length including the tag |
| L | AES-256-GCM ciphertext followed by its 16-byte tag |

Chunk number i (counting from 0) uses the 12-byte nonce `nonce prefix || i` (i as 4 bytes) and
the 43 header bytes as associated data. Data chunks carry 1 to 1 MiB of plaintext; Wren fills
them to exactly 1 MiB except the last one. The stream ends with a chunk that has no plaintext
(L = 16), and nothing may follow it. A reader must reject the file if any tag fails, if a chunk
length is under 16 or over 1 MiB + 16, if the file ends before the closing chunk, or if there
are bytes after it. A fresh salt per file means a fresh key per file, so nonces never repeat
under one key.

The plaintext, all chunks joined, is a POSIX tar archive (ustar, with pax headers for names
that are not plain ASCII or longer than 100 bytes). It holds only folders and regular files.
The Python tool extracts with Python's `data` filter when it has one and refuses links,
absolute paths and `..` in any case.

### Why these choices

- scrypt and not Argon2, which Wren uses for its own passphrase: Python's standard library has
  scrypt and does not have Argon2, so the file can be opened with the fewest extra installs.
- AES-256-GCM in 1 MiB chunks: the phone never holds more than one chunk in memory, and a
  reader can check each chunk on its own. Putting the chunk number in the nonce and requiring a
  closing chunk stops chunks from being dropped, reordered or cut off without notice.
- tar: simple enough to write in a hundred lines without a library, and every computer has
  something that reads it.
