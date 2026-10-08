# Security

If you find a vulnerability in Wren, email Munzzyy1@proton.me. Do not open a
public issue for it.

There is no PGP key for that address yet. Proton encrypts mail between Proton
accounts. If you need an encrypted channel, say so in a first message with no
details in it and we will sort one out.

Wren clients expire about 90 days after their build date, the same as Signal.
Keep the app updated.

## What to report

Anything that makes Wren weaker than what its documents say it is:

- Data readable without the passphrase, or a way to test passphrase guesses
  from a copy of the files.
- A wipe that leaves the database usable, or one that fires when it should not
  (a wrong caller, a storage error, a bad stored value).
- A panic trigger that Wren accepts from an app it should not, or a CONNECT
  that arms Erase.
- An exported chat that runs script, writes outside the folder you picked, or
  names a file badly.
- A setting the device check reports as hardened that is not.
- A release or build that does not match the source, or a workflow, script or
  key handling mistake that could cause one.
- A sentence in [docs/THREAT-MODEL.md](docs/THREAT-MODEL.md) or the other
  documents that is not true of the code. That is a bug too.

You do not need a working exploit. Tell me the version or commit, the Android
version and phone, and the steps, and I will take it from there. Please leave
real messages and other people's data out of anything you send.

## What happens next

I am one person, so these are targets, not a service level.

- I reply within 7 days. If you have heard nothing after that, send it again.
- Within about 14 days of replying I tell you what I think it is: a Wren bug,
  something inherited from upstream, or not a bug, and why.
- I aim to ship a fix or a clear mitigation within 90 days of your report,
  and much sooner for anything that exposes messages, lets another app read or
  erase Wren's data, or breaks the lock.
- I publish the details after the fix is out, or 90 days after your report if
  there is no fix by then, whichever comes first, unless we agree on another
  date. I will not ask you to stay quiet longer than that. If you want to
  publish sooner than I am ready, tell me and we will talk.
- Credit goes in the release notes and the write-up under the name or handle
  you pick, or nobody's if you prefer.
- There is no bounty. I cannot pay for reports, and I would rather say so
  than let you find out later.

Test on your own phone and your own Signal account. Do not go after Signal's
servers, other people's accounts or data, or anything that takes a service
down. I cannot speak for Signal or anyone else, but for Wren's own code and
the Wren project I will not treat good-faith research that stays inside those
lines as an attack.

## Problems that come from Signal or Molly

Wren is Molly with Signal merged in, and most of its code is theirs. Where the
problem lives decides who fixes it.

- The Signal protocol, Signal's servers and accounts, libsignal and RingRTC:
  report to Signal (security@signal.org). Tell me too if it affects Wren and
  you are happy for me to watch for the fix.
- Code that Molly wrote and Wren has not changed, such as the passphrase key
  derivation, the lock service, the proxy stack and UnifiedPush: you can send
  it to me and I will report it to the Molly project, or you can report it
  there yourself. [docs/AUDIT-GUIDE.md](docs/AUDIT-GUIDE.md) lists which
  files are which.
- When you send me something that is upstream's to fix, I will tell you where
  I reported it and keep to the same 90 days, counted from your report. If
  upstream is slow and Wren is exposed, I may ship a Wren-side mitigation and
  say so.
- Wren is behind Signal. It is on Signal 8.20.5, and the reason is in the
  README under "Keeping up with Signal". A security fix that Signal shipped
  after 8.20.5 is not in Wren until I either get past that gap or backport the
  fix. If you tell me about one, I will say whether Wren has it, and I will
  look at backporting it.

## Scope

In scope, report to me:

- Wren's own code in `app/src/main/java/io/github/munzzyy/wren/`: the duress
  passphrase, the unlock limit, the wipe, the PanicKit responder, the device
  check and patch banner, chat export, the black theme.
- Wren's changes to Molly's files. The list, with the diff command, is in
  [docs/AUDIT-GUIDE.md](docs/AUDIT-GUIDE.md) section 10.
- How Wren is built and released: `tools/`, `reproducible-builds/`,
  `.github/workflows/`, the `Dockerfile`, `app/build.gradle.kts`, and the
  signing and publishing steps in [BUILDING.md](BUILDING.md). Once the F-Droid
  repository exists ([docs/FDROID-REPO.md](docs/FDROID-REPO.md)), its signing
  and the update path too.
- The security documents, when they say something the code does not do.

Out of scope here, send to the project that owns it:

- Signal's protocol, servers and apps, libsignal, RingRTC, and Signal code Wren
  has not changed.
- Molly's unchanged code, as above.
- Apps Wren works with: Ripple, Orbot, UnifiedPush distributors, the
  MollySocket server.
- Bugs in Android, the vendor's firmware or a phone's hardware.
- A phone that is already rooted or compromised, and attacks on the KeyStore
  hardware. [docs/THREAT-MODEL.md](docs/THREAT-MODEL.md) says where Wren stops.
- The residual risks the threat model already lists, unless you found a
  worse version of one.
- Any app on the phone being able to lock Wren by broadcast. That is Molly's
  behavior and it is documented.
- Scanner output with no demonstrated impact, and social engineering.
