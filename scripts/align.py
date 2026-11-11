# APK içindeki sıkıştırılmamış dosyaları 4 bayta hizalar (Android 11+ şartı).
import sys, zipfile
src, dst = sys.argv[1], sys.argv[2]
zin = zipfile.ZipFile(src)
with zipfile.ZipFile(dst, 'w') as zout:
    for info in zin.infolist():
        data = zin.read(info.filename)
        zi = zipfile.ZipInfo(info.filename, date_time=(2026,10,19,0,0,0))
        zi.compress_type = zipfile.ZIP_STORED if (info.filename == 'resources.arsc' or info.filename.endswith('.png')) else zipfile.ZIP_DEFLATED
        zi.external_attr = 0o644 << 16
        if zi.compress_type == zipfile.ZIP_STORED:
            off = zout.fp.tell() + 30 + len(zi.filename.encode())
            pad = (-off) % 4
            zi.extra = b'\0' * pad
        zout.writestr(zi, data)
