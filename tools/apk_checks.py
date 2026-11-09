"""Check produced APKs for duplicate class definitions across DEX files."""
import collections
import pathlib
import struct
import zipfile


def _definitions(data):
    if not data.startswith(b'dex\n'):
        raise ValueError('Formato DEX não reconhecido.')

    def u32(offset):
        return struct.unpack_from('<I', data, offset)[0]

    def string(index):
        offset = u32(u32(60) + 4 * index)
        while data[offset] & 128:
            offset += 1
        offset += 1
        end = data.index(0, offset)
        return data[offset:end].decode('utf-8', 'replace')

    types = [string(u32(u32(68) + 4 * i)) for i in range(u32(64))]
    return [types[u32(u32(100) + 32 * i)] for i in range(u32(96))]


def verify_dex(apk):
    owners = collections.defaultdict(list)
    counts = {}
    with zipfile.ZipFile(apk) as archive:
        for name in archive.namelist():
            if name.endswith('.dex'):
                classes = _definitions(archive.read(name))
                counts[name] = len(classes)
                for cls in classes:
                    owners[cls].append(name)
    if not counts:
        raise ValueError('APK sem classes DEX.')
    duplicates = [cls for cls, files in owners.items() if len(files) > 1]
    if duplicates:
        raise ValueError(
            'APK contém classes duplicadas: ' + ', '.join(duplicates[:5])
            + '. Execute ./gradlew clean e compile novamente.'
        )
    return {'apk': pathlib.Path(apk).name, 'dex_files': counts,
            'unique_classes': len(owners), 'duplicate_classes': 0}
