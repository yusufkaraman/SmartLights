"""Tapo hesap bilgilerini Windows Credential Locker'da saklar/okur.

Plan madde 7: Tapo şifresi kesinlikle config.json içinde düz metin olmayacak.
`keyring`, Windows'ta otomatik olarak Credential Locker (WinVaultKeyring)
backend'ini kullanır.

GUI uygulaması (main.py) `get_tapo_credentials()`'ı KULLANMAZ — bu fonksiyon
konsoldan `input()`/`getpass()` okur, paketlenmiş `.exe`'de (Faz 10,
pencereli mod) konsol olmayabilir ve bu çağrı sessizce kilitlenir. GUI,
`get_stored_tapo_credentials()` (asla konsola dokunmaz) + bir QML diyaloğu
üzerinden `set_tapo_credentials()` kullanır (plan madde 9: Tapo auth fail'i
düzgün ele alma). `get_tapo_credentials()` sadece cli.py (konsol) içindir.
"""

from __future__ import annotations

import getpass

import keyring

SERVICE = "SmartLights/Tapo"
_EMAIL_SLOT = "email"


def get_stored_tapo_credentials() -> tuple[str, str] | None:
    """Kayıtlı Tapo bilgilerini döner; yoksa None. Konsola ASLA dokunmaz — GUI'de güvenli."""
    email = keyring.get_password(SERVICE, _EMAIL_SLOT)
    if not email:
        return None
    password = keyring.get_password(SERVICE, email)
    if not password:
        return None
    return email, password


def set_tapo_credentials(email: str, password: str) -> None:
    """Tapo bilgilerini Credential Locker'a kaydeder (GUI diyaloğundan çağrılır)."""
    keyring.set_password(SERVICE, _EMAIL_SLOT, email)
    keyring.set_password(SERVICE, email, password)


def get_tapo_credentials() -> tuple[str, str]:
    """Kayıtlı Tapo e-posta/şifresini döner; yoksa KONSOLDAN alıp kaydeder.

    Sadece cli.py (konsol uygulaması) için — GUI'de kullanılmamalı, bkz.
    modül docstring'i.
    """
    stored = get_stored_tapo_credentials()
    if stored:
        return stored

    print("Tapo hesap bilgileri Credential Locker'da bulunamadı, bir kere girmeniz yeterli.")
    email = input("Tapo e-posta: ").strip()
    password = getpass.getpass("Tapo şifresi: ")

    set_tapo_credentials(email, password)
    return email, password


def clear_tapo_credentials() -> None:
    """Kayıtlı Tapo bilgilerini siler (yanlış girilirse yeniden sormak için)."""
    email = keyring.get_password(SERVICE, _EMAIL_SLOT)
    if email:
        try:
            keyring.delete_password(SERVICE, email)
        except keyring.errors.PasswordDeleteError:
            pass
        try:
            keyring.delete_password(SERVICE, _EMAIL_SLOT)
        except keyring.errors.PasswordDeleteError:
            pass
