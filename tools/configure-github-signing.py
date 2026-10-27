#!/usr/bin/env python3
"""One-time provisioning. Uses gh stdin; never puts secrets in command arguments."""
import argparse
import base64
import hashlib
import os
from pathlib import Path
import subprocess
import tempfile

REPO = 'Yuri-Medeiros/App-Controle-Financeiro'
ROOT = Path(__file__).resolve().parent.parent


def gh(*args, value=None):
    return subprocess.run(['gh', *args, '--repo', REPO], input=value, stdout=subprocess.PIPE,
                          stderr=subprocess.PIPE, check=True).stdout


def install(key, password, alias, pin_name, key_secret, password_secret, existing):
    if {key_secret, password_secret} <= existing:
        print(f'{alias}: existing secrets preserved; workflow will verify the certificate.')
        return
    if not key.is_file() or not password:
        raise SystemExit(f'{alias}: recover the existing protected identity and password first.')
    with tempfile.TemporaryDirectory() as tmp:
        certificate = Path(tmp) / 'certificate.der'
        env = dict(os.environ, FINANCE_SETUP_PASSWORD=password)
        result = subprocess.run(['keytool', '-exportcert', '-alias', alias, '-keystore', str(key),
            '-storepass:env', 'FINANCE_SETUP_PASSWORD', '-file', str(certificate)], env=env,
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        if result.returncode:
            raise SystemExit(f'{alias}: cannot open protected identity.')
        expected = (ROOT / 'signing' / pin_name).read_text().strip()
        if hashlib.sha256(certificate.read_bytes()).hexdigest() != expected:
            raise SystemExit(f'{alias}: certificate mismatch; refusing configuration.')
    for name, value in ((key_secret, base64.b64encode(key.read_bytes())),
                        (password_secret, password.encode())):
        if name not in existing:
            gh('secret', 'set', name, value=value)
            print(name + ': configured without displaying value.')
        else:
            print(name + ': preserved.')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--release-keystore', type=Path, required=True)
    parser.add_argument('--test-keystore', type=Path, required=True)
    parser.add_argument('--test-password-file', type=Path, required=True)
    args = parser.parse_args()
    try:
        names = set(gh('secret', 'list', '--json', 'name', '--jq', '.[].name').decode().splitlines())
        install(args.release_keystore, os.environ.get('MEU_FINANCEIRO_SIGNING_PASSWORD', ''),
                'meu-financeiro', 'release-certificate.sha256', 'MEU_FINANCEIRO_RELEASE_KEYSTORE_B64',
                'MEU_FINANCEIRO_SIGNING_PASSWORD', names)
        test_password = args.test_password_file.read_text() if args.test_password_file.exists() else ''
        install(args.test_keystore, test_password, 'meu-financeiro-test', 'test-certificate.sha256',
                'MEU_FINANCEIRO_TEST_KEYSTORE_B64', 'MEU_FINANCEIRO_TEST_PASSWORD', names)
    except subprocess.CalledProcessError:
        raise SystemExit('GitHub configuration blocked: check gh authentication, network and Secrets permissions. No existing secret was replaced.') from None


if __name__ == '__main__':
    main()
