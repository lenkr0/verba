# Text recognition tools

- `build_pack.py` builds the model pack Verba downloads for text recognition
  (`verba-ocr-ppocrv5-mobile-1.zip` in the GitHub release `ocr-models`):
  PaddleOCR PP-OCRv5 mobile, ONNX by RapidOCR (pinned by SHA-256), converted to
  ncnn with [pnnx](https://github.com/pnnx/pnnx/releases). The build is
  reproducible: the same inputs give the same checksum, which the app checks.

  ```sh
  pip install onnx
  python build_pack.py --pnnx path/to/pnnx --out out/
  ```

- `make_test_images.py` draws the test pages with known text (vertical speech
  bubbles, novel and book screenshots) used by the instrumented tests.

The pack contains `det.ncnn.*` (finds text), `rec.ncnn.*` (reads a line),
`dict.txt` (the recognizer's characters, one per line) and `pack.json`.
