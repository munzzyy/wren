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

## Canceling

Both progress notifications have a Cancel button. Wren stops after the message or the chunk of
media it is on and deletes everything that export wrote: the chat folder for a single chat, the
whole `Wren export` folder for all chats. An export that is still waiting behind another one is
just dropped.

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

- **The export is not encrypted.** Anyone who can open that folder can read the chat. Put it
  somewhere you trust and delete it when you are done.
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
- If Android kills Wren in the middle of an export, the export starts over in a new folder the
  next time Wren runs, and the half-written one from before stays where it is.
- The Cancel button does nothing while Wren is locked.

Exported files are written straight to the folder you picked. If the export fails partway, Wren
deletes the half-written folder.
