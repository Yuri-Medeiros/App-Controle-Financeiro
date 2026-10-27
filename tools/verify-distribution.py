#!/usr/bin/env python3
"""Check packaged identity/version and native alignment, not only Gradle config."""
import os
from pathlib import Path
import re
import struct
import subprocess
import sys
import zipfile

apk = Path(sys.argv[1])
badging = subprocess.check_output(['aapt2', 'dump', 'badging', str(apk)], text=True)
package = re.search(r"package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", badging)
if not package:
    raise SystemExit('Missing packaged identity')
expected = (os.environ['APK_PACKAGE'], os.environ['APK_CODE'], os.environ['APK_VERSION'])
if package.groups() != expected:
    raise SystemExit('Packaged identity or version differs from approved build')
with zipfile.ZipFile(apk) as archive:
    native = [n for n in archive.namelist() if n.endswith('.so')]
    if not any(n.endswith('/libsqlcipher.so') for n in native):
        raise SystemExit('Missing SQLCipher')
    for name in native:
        data = archive.read(name)
        if data[:4] != b'\x7fELF':
            raise SystemExit('Invalid native binary')
        order = '<' if data[5] == 1 else '>'
        wide = data[4] == 2
        offset = struct.unpack_from(order + ('Q' if wide else 'I'), data, 32 if wide else 28)[0]
        size, count = struct.unpack_from(order + 'HH', data, 54 if wide else 42)
        for i in range(count):
            pos = offset + i * size
            if struct.unpack_from(order + 'I', data, pos)[0] == 1:
                alignment = struct.unpack_from(order + ('Q' if wide else 'I'), data, pos + (48 if wide else 28))[0]
                if alignment < 16384:
                    raise SystemExit('Native library is not 16 KiB aligned: ' + name)
print('Packaged identity, version and native 16 KiB alignment verified.')
