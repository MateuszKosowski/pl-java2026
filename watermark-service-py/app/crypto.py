"""AES-GCM envelope + Reed-Solomon ECC for watermark payloads.

Pipeline at embed time:
    1. Build plaintext: f"{owner}|{text}".encode("utf-8")
    2. Encrypt with AES-256-GCM (key derived from WATERMARK_APP_KEY)
    3. Prepend magic+version+nonce → "envelope"
    4. Append Reed-Solomon parity bytes → "ECC-protected envelope"
    5. Pad with NUL up to length_bits/8 bytes
    6. Hand off to invisible-watermark library

Reverse on detect. The ECC layer recovers from up to ECC_PARITY_BYTES/2 byte errors
introduced by the frequency-domain watermarking (which can flip ~0–3 bits on
borderline-size images — without ECC the GCM tag would reject everything).

Wire format (innermost first):
    plaintext   = owner | '|' | text                                (UTF-8)
    envelope    = MAGIC(4) | VERSION(1) | NONCE(12) | CIPHERTEXT_AND_TAG(n)
    protected   = envelope | ECC_PARITY(16)
    embedded    = protected.ljust(length_bits // 8, b'\\x00')
"""
from __future__ import annotations

import hashlib
import os
from dataclasses import dataclass

import reedsolo
from cryptography.exceptions import InvalidTag
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

_MAGIC = b"WMPY"
_VERSION = b"\x02"  # bumped from v1 (no ECC) to v2 (ECC-wrapped)
_NONCE_LEN = 12
_TAG_LEN = 16
HEADER_LEN = len(_MAGIC) + len(_VERSION) + _NONCE_LEN  # 17

# Reed-Solomon parity bytes. Corrects up to ECC_PARITY_BYTES / 2 byte errors.
# Calibrated at 16 bytes (= 8 corrected byte errors) so even threshold-sized images
# with unlucky pixel distributions round-trip reliably across seeds. Lower values
# (8) leaked occasional failures at the 768-bit tier boundary.
ECC_PARITY_BYTES = 16

# Total non-plaintext overhead consumed per embedded watermark.
ENVELOPE_OVERHEAD = HEADER_LEN + _TAG_LEN + ECC_PARITY_BYTES  # 17 + 16 + 16 = 49 bytes


class CryptoError(Exception):
    """Raised when an envelope is malformed, ECC fails, or authentication fails."""


@dataclass(frozen=True)
class DecodedEnvelope:
    owner: str
    text: str


_rs_codec = reedsolo.RSCodec(ECC_PARITY_BYTES)


def _derive_key(app_key: str) -> bytes:
    if not app_key:
        raise ValueError("WATERMARK_APP_KEY must not be empty")
    return hashlib.sha256(("watermark-app:" + app_key).encode("utf-8")).digest()


def seal(owner: str, text: str, *, app_key: str) -> bytes:
    if "|" in owner:
        raise ValueError("owner identifier must not contain '|'")
    plaintext = f"{owner}|{text}".encode("utf-8")
    key = _derive_key(app_key)
    nonce = os.urandom(_NONCE_LEN)
    aad = _MAGIC + _VERSION
    ciphertext = AESGCM(key).encrypt(nonce, plaintext, aad)
    envelope = _MAGIC + _VERSION + nonce + ciphertext
    return bytes(_rs_codec.encode(envelope))


def unseal(blob: bytes, *, app_key: str) -> DecodedEnvelope:
    if len(blob) < HEADER_LEN + _TAG_LEN + ECC_PARITY_BYTES:
        raise CryptoError("envelope too short")
    try:
        envelope_bytes, _, _ = _rs_codec.decode(blob)
    except reedsolo.ReedSolomonError as exc:
        raise CryptoError("ECC failed — too many corrupted bytes") from exc
    envelope = bytes(envelope_bytes)
    if envelope[: len(_MAGIC)] != _MAGIC:
        raise CryptoError("magic mismatch")
    if envelope[len(_MAGIC) : len(_MAGIC) + 1] != _VERSION:
        raise CryptoError("unsupported envelope version")
    nonce = envelope[len(_MAGIC) + 1 : HEADER_LEN]
    ciphertext = envelope[HEADER_LEN:]
    key = _derive_key(app_key)
    aad = _MAGIC + _VERSION
    try:
        plaintext = AESGCM(key).decrypt(nonce, ciphertext, aad)
    except InvalidTag as exc:
        raise CryptoError("authentication failed — wrong key or tampered envelope") from exc
    text = plaintext.decode("utf-8")
    if "|" not in text:
        raise CryptoError("envelope plaintext missing owner separator")
    owner, _, body = text.partition("|")
    if not owner:
        raise CryptoError("envelope plaintext has empty owner")
    return DecodedEnvelope(owner=owner, text=body)


def max_text_bytes(length_bits: int, owner: str) -> int:
    """How many UTF-8 bytes of user text fit, given the watermark capacity and owner."""
    owner_bytes = len(owner.encode("utf-8")) + 1  # owner + '|'
    return max(0, length_bits // 8 - ENVELOPE_OVERHEAD - owner_bytes)
