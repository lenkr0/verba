# Third-party notices

Verba is licensed under the GNU General Public License v3.0 or later. It uses
the following components, all under licenses compatible with the GPL-3.0.

## Material Symbols (icons)

The icons in `app/src/main/res/drawable/ic_*.xml` (except
`ic_launcher_foreground.xml`) are Material Symbols by Google.

- Project: https://github.com/google/material-design-icons
- License: Apache License 2.0

## Android Jetpack (AndroidX, Jetpack Compose, Material 3, Navigation 3)

- Copyright The Android Open Source Project
- https://developer.android.com/jetpack
- License: Apache License 2.0

## FSRS (spaced repetition)

The scheduler in `app/src/main/kotlin/io/github/lenkr0/verba/review/Fsrs.kt`
is a port of py-fsrs 6.3.3, including its default parameters.

- Project: https://github.com/open-spaced-repetition/py-fsrs
- Copyright (c) 2022 Open Spaced Repetition
- License: MIT

## ncnn (text recognition)

The native library `libverbaocr` links ncnn's prebuilt Android release
(downloaded and checksum-verified by the build, see `app/build.gradle.kts`).

- Project: https://github.com/Tencent/ncnn
- Copyright (C) 2017 Tencent. All rights reserved.
- License: BSD 3-Clause

## Text recognition models

Like the dictionaries, the models are not part of the app: they are
downloaded when you turn on text recognition. They are PaddleOCR's PP-OCRv5
mobile models, in the ONNX conversion by RapidOCR, converted to ncnn by
`tools/ocr/build_pack.py`.

- PaddleOCR: https://github.com/PaddlePaddle/PaddleOCR – Apache License 2.0
- RapidOCR: https://github.com/RapidAI/RapidOCR – Apache License 2.0

## Test images

`app/src/androidTest/assets/ocr/pc_ja_e01p02.jpg` is a page of
*Pepper&Carrot*, episode 1, by David Revoy (https://www.peppercarrot.com),
Japanese translation, licensed under CC BY 4.0. The other test pages are made
by `tools/ocr/make_test_images.py`.

## Dictionary data

The dictionaries are not part of the app; they are downloaded separately
and keep their sources' licenses, which the app shows for each dictionary
(Settings → Dictionaries → tap a dictionary):

- **Wiktionary** (English and German editions), extracted by Tatu Ylonen's
  [wiktextract](https://github.com/tatuylonen/wiktextract) and published on
  [kaikki.org](https://kaikki.org) – CC BY-SA 4.0
- **Ding** German–English dictionary by Frank Richter, TU Chemnitz –
  GPL-2.0-or-later
- **JMdict** by the Electronic Dictionary Research and Development Group –
  CC BY-SA 4.0
- **CC-CEDICT** by MDBG and contributors – CC BY-SA 4.0

The builder (`tools/dictionary`) is part of Verba and under its license.

## Kotlin, kotlinx.coroutines, kotlinx.serialization

- Copyright JetBrains s.r.o. and contributors
- https://kotlinlang.org
- License: Apache License 2.0
