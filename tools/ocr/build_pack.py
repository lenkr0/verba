"""Builds Verba's text recognition pack from PaddleOCR's PP-OCRv5 mobile models.

Steps, all pinned so the result can be checked:
1. Download the ONNX conversions of the official PaddleOCR models published by
   RapidOCR (SHA-256 checked).
2. Convert them to ncnn with pnnx (fp16 weights), with dynamic input sizes.
3. Take the recognizer's character list from the ONNX metadata.
4. Zip everything with a pack.json.

Usage: build_pack.py --pnnx path/to/pnnx --out dir
Needs: Python 3.10+, the `onnx` package and the pnnx executable
(https://github.com/pnnx/pnnx/releases).
"""
import argparse
import hashlib
import json
import os
import shutil
import subprocess
import tempfile
import urllib.request
import zipfile

import onnx

PACK_ID = 'ppocrv5-mobile'
PACK_VERSION = 1
SOURCE = 'https://www.modelscope.cn/models/RapidAI/RapidOCR/resolve/v3.10.0/onnx/PP-OCRv5'
MODELS = {
    'det': (f'{SOURCE}/det/ch_PP-OCRv5_det_mobile.onnx',
            '4d97c44a20d30a81aad087d6a396b08f786c4635742afc391f6621f5c6ae78ae',
            ['inputshape=[1,3,640,640]', 'inputshape2=[1,3,960,960]']),
    'rec': (f'{SOURCE}/rec/ch_PP-OCRv5_rec_mobile.onnx',
            '5825fc7ebf84ae7a412be049820b4d86d77620f204a041697b0494669b1742c5',
            ['inputshape=[1,3,48,160]', 'inputshape2=[1,3,48,256]']),
}


def sha256(path):
    h = hashlib.sha256()
    with open(path, 'rb') as f:
        for block in iter(lambda: f.read(1 << 20), b''):
            h.update(block)
    return h.hexdigest()


def download(url, expected, path):
    with urllib.request.urlopen(url) as response, open(path, 'wb') as out:
        shutil.copyfileobj(response, out)
    actual = sha256(path)
    if actual != expected:
        raise SystemExit(f'checksum mismatch for {url}: {actual}')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--pnnx', required=True)
    parser.add_argument('--out', required=True)
    args = parser.parse_args()
    os.makedirs(args.out, exist_ok=True)
    pnnx = os.path.abspath(args.pnnx)

    with tempfile.TemporaryDirectory() as work:
        files = {}
        for kind, (url, checksum, shapes) in MODELS.items():
            src = os.path.join(work, f'{kind}.onnx')
            download(url, checksum, src)
            subprocess.run([pnnx, f'{kind}.onnx', *shapes], cwd=work, check=True, stdout=subprocess.DEVNULL)
            for ext in ('param', 'bin'):
                files[f'{kind}.ncnn.{ext}'] = os.path.join(work, f'{kind}.ncnn.{ext}')
            if kind == 'rec':
                meta = {p.key: p.value for p in onnx.load(src).metadata_props}
                characters = meta['character'].splitlines()
                dict_path = os.path.join(work, 'dict.txt')
                with open(dict_path, 'w', encoding='utf-8', newline='\n') as f:
                    f.write('\n'.join(characters) + '\n')
                files['dict.txt'] = dict_path

        pack = {
            'format': 1,
            'id': PACK_ID,
            'version': PACK_VERSION,
            'name': 'PP-OCRv5 mobile',
            # PP-OCRv5's recognizer reads these scripts with one model
            'scripts': ['Hans', 'Hant', 'Jpan', 'Latn'],
            'characters': len(characters),
            'source': 'PaddleOCR PP-OCRv5 mobile (det + rec), ONNX by RapidOCR v3.10.0, converted with pnnx',
            'license': 'Apache-2.0',
        }
        name = f'verba-ocr-{PACK_ID}-{PACK_VERSION}.zip'
        target = os.path.join(args.out, name)
        # Stored, not deflated: the fp16 weights hardly compress. Fixed timestamps,
        # so the same inputs always give the same file and checksum.
        def entry(name):
            return zipfile.ZipInfo(name, date_time=(2026, 1, 1, 0, 0, 0))

        with zipfile.ZipFile(target, 'w', compression=zipfile.ZIP_STORED) as z:
            z.writestr(entry('pack.json'), json.dumps(pack, ensure_ascii=False, indent=1))
            for arcname, path in sorted(files.items()):
                with open(path, 'rb') as f:
                    z.writestr(entry(arcname), f.read())
        print(json.dumps({'file': name, 'size': os.path.getsize(target), 'sha256': sha256(target)}, indent=1))


if __name__ == '__main__':
    main()
