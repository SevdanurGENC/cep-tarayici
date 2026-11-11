# Third-party software

Bundled in `web/`:

| Library | Version | License | Used for |
|---|---|---|---|
| [jsPDF](https://github.com/parallax/jsPDF) | 2.5.1 | MIT | Creating PDFs from scanned pages |
| [pdf-lib](https://github.com/Hopding/pdf-lib) | 1.17.1 | MIT | Merging PDFs and selecting pages |

Downloaded at build time by `scripts/build-apk.sh` (not stored in this repository):

| Package | Version | License | Contains |
|---|---|---|---|
| [@drxiaozhi/minapk](https://github.com/jjtseng93/minapk) | 0.4.0 | MIT | android.jar (API 34), d8, apksigner, ECJ compiler |
| [aaptjs3](https://github.com/kiliaosi/aaptjs3) | 2.0.2 | MIT | aapt2 (Linux x64) |

The Android SDK components inside these packages are © Google and the Android Open Source Project,
licensed under the Apache License 2.0 / Android SDK License.

Fonts (loaded from Google Fonts when online): Bricolage Grotesque, IBM Plex Sans, IBM Plex Mono — SIL Open Font License 1.1.
