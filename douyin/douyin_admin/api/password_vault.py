"""Recoverable administrator copy, separate from authentication hashes."""
import os
from pathlib import Path
from cryptography.fernet import Fernet, InvalidToken


def vault():
    path = Path(os.getenv('PASSWORD_VAULT_KEY_FILE', '/data/password-vault.key'))
    try:
        key = path.read_bytes()
    except FileNotFoundError:
        key = Fernet.generate_key()
        try:
            fd = os.open(str(path), os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        except FileExistsError:
            key = path.read_bytes()
        else:
            with os.fdopen(fd, 'wb') as stream:
                stream.write(key)
    return Fernet(key)


def encrypt_password(password):
    return vault().encrypt(password.encode()).decode()


def reveal_password(value):
    if not value:
        return ''
    try:
        return vault().decrypt(value.encode()).decode()
    except InvalidToken:
        return ''
