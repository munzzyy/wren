#!/usr/bin/env python3
# Copyright 2026 Cole Munz
# SPDX-License-Identifier: AGPL-3.0-only
"""Decrypt a Wren encrypted export (.wrenx) and unpack it into a folder.

    python3 wren-export-decrypt.py "Wren chat 2026-10-08 1403.wrenx" out/

The tool asks for the passphrase. It checks every chunk of the file before it
writes anything, so a wrong passphrase or a damaged or altered file leaves the
output folder empty. The file format is described in docs/EXPORT.md.

Needs Python 3.8 or newer and the "cryptography" package for AES-GCM, because
Python has no AES of its own. Key derivation uses the standard library's
hashlib.scrypt.
"""

import argparse
import getpass
import hashlib
import io
import os
import struct
import sys
import tarfile
import unicodedata

MAGIC = b"WRENX\x01"
KDF_SCRYPT = 1
HEADER = struct.Struct(">6sBIII16s8s")
TAG_SIZE = 16
CHUNK_SIZE = 1024 * 1024
MAX_N = 1 << 20
MAX_R = 8
MAX_P = 4

INSTALL_HINT = (
    "This tool needs the Python package \"cryptography\" for AES-GCM.\n"
    "Install it with one of:\n"
    "  python3 -m pip install cryptography\n"
    "  sudo apt install python3-cryptography     (Debian, Ubuntu)\n"
    "  sudo dnf install python3-cryptography     (Fedora)\n"
    "  brew install cryptography                 (macOS with Homebrew)"
)


class WrenxError(Exception):
    pass


def read_header(f):
    raw = f.read(HEADER.size)
    if len(raw) != HEADER.size:
        raise WrenxError("This is not a Wren encrypted export: the file is too short.")
    magic, kdf, n, r, p, salt, nonce_prefix = HEADER.unpack(raw)
    if magic != MAGIC:
        raise WrenxError("This is not a Wren encrypted export, or it was made by a newer version of Wren.")
    if kdf != KDF_SCRYPT:
        raise WrenxError("Unknown key derivation in the header (%d)." % kdf)
    if n < 2 or n & (n - 1) or n > MAX_N or not 1 <= r <= MAX_R or not 1 <= p <= MAX_P:
        raise WrenxError("The header asks for scrypt settings this tool refuses (n=%d r=%d p=%d)." % (n, r, p))
    return raw, n, r, p, salt, nonce_prefix


def derive_key(passphrase, salt, n, r, p):
    data = unicodedata.normalize("NFC", passphrase).encode("utf-8")
    maxmem = 128 * r * (n + p + 2) + 1024 * 1024
    return hashlib.scrypt(data, salt=salt, n=n, r=r, p=p, maxmem=maxmem, dklen=32)


def chunks(f, aead, header, nonce_prefix, invalid_tag):
    """Yields each chunk's plaintext after its tag checks out, and stops at the closing chunk."""
    counter = 0
    while True:
        raw = f.read(4)
        if len(raw) != 4:
            raise WrenxError("The file ends early. It was cut off, or the export never finished.")
        (length,) = struct.unpack(">I", raw)
        if length < TAG_SIZE or length > CHUNK_SIZE + TAG_SIZE:
            raise WrenxError("The file is damaged (chunk %d has an impossible length)." % counter)
        ciphertext = f.read(length)
        if len(ciphertext) != length:
            raise WrenxError("The file ends early. It was cut off, or the export never finished.")
        if counter > 0xFFFFFFFF:
            raise WrenxError("The file is damaged (too many chunks).")

        nonce = nonce_prefix + struct.pack(">I", counter)
        try:
            plaintext = aead.decrypt(nonce, ciphertext, header)
        except invalid_tag:
            if counter == 0:
                raise WrenxError("Wrong passphrase, or the file is damaged.")
            raise WrenxError("The file is damaged or was changed after export (chunk %d fails its check)." % counter)
        counter += 1

        if not plaintext:
            if f.read(1):
                raise WrenxError("The file has extra data after its end.")
            return
        yield plaintext


class ChunkStream(io.RawIOBase):
    def __init__(self, source):
        self.source = source
        self.pending = b""

    def readable(self):
        return True

    def readinto(self, buffer):
        while not self.pending:
            try:
                self.pending = next(self.source)
            except StopIteration:
                return 0
        count = min(len(buffer), len(self.pending))
        buffer[:count] = self.pending[:count]
        self.pending = self.pending[count:]
        return count


def safe_target(output, name):
    if not name or name.startswith(("/", "\\")) or "\\" in name or "\x00" in name:
        return None
    parts = name.split("/")
    if ".." in parts or (len(parts[0]) >= 2 and parts[0][1] == ":"):
        return None
    root = os.path.realpath(output)
    target = os.path.realpath(os.path.join(root, *[part for part in parts if part not in ("", ".")]))
    if target != root and not target.startswith(root + os.sep):
        return None
    return target


def extract(stream, output):
    use_data_filter = hasattr(tarfile, "data_filter")
    count = 0
    with tarfile.open(fileobj=stream, mode="r|") as tar:
        for member in tar:
            if not (member.isfile() or member.isdir()):
                raise WrenxError("The archive holds an entry that is not a file or folder: %r" % member.name)
            if safe_target(output, member.name) is None:
                raise WrenxError("The archive holds an unsafe path: %r" % member.name)
            if use_data_filter:
                tar.extract(member, output, filter="data")
            else:
                member.mode = 0o755 if member.isdir() else 0o644
                tar.extract(member, output)
            if member.isfile():
                count += 1
    return count


def read_passphrase(from_stdin):
    if from_stdin:
        line = sys.stdin.readline()
        return line[:-1] if line.endswith("\n") else line
    return getpass.getpass("Passphrase: ")


def main(argv=None):
    parser = argparse.ArgumentParser(description="Decrypt a Wren encrypted export (.wrenx) into a folder.")
    parser.add_argument("export", help="the .wrenx file")
    parser.add_argument("output", help="folder to unpack into; it must be new or empty")
    parser.add_argument("--passphrase-stdin", action="store_true", help="read the passphrase from the first line of standard input instead of asking")
    args = parser.parse_args(argv)

    try:
        from cryptography.exceptions import InvalidTag
        from cryptography.hazmat.primitives.ciphers.aead import AESGCM
    except ImportError:
        print(INSTALL_HINT, file=sys.stderr)
        return 1

    if os.path.exists(args.output) and (not os.path.isdir(args.output) or os.listdir(args.output)):
        print("The output folder must be new or empty: %s" % args.output, file=sys.stderr)
        return 1

    try:
        with open(args.export, "rb") as f:
            header, n, r, p, salt, nonce_prefix = read_header(f)
            passphrase = read_passphrase(args.passphrase_stdin)
            print("Deriving the key...", file=sys.stderr)
            aead = AESGCM(derive_key(passphrase, salt, n, r, p))
            del passphrase

            print("Checking the whole file...", file=sys.stderr)
            for _ in chunks(f, aead, header, nonce_prefix, InvalidTag):
                pass

            f.seek(len(header))
            os.makedirs(args.output, exist_ok=True)
            stream = io.BufferedReader(ChunkStream(chunks(f, aead, header, nonce_prefix, InvalidTag)), CHUNK_SIZE)
            try:
                count = extract(stream, args.output)
            except (WrenxError, tarfile.TarError) as e:
                raise WrenxError("%s The output folder is incomplete; delete it." % e)
    except WrenxError as e:
        print(e, file=sys.stderr)
        return 1
    except OSError as e:
        print("Could not read or write a file: %s" % e, file=sys.stderr)
        return 1

    print("Unpacked %d files into %s" % (count, args.output), file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
