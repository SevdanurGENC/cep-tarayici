# Changelog

## 1.2.0 — 2026-11-11
- Fonts are bundled with the app: no request goes to Google Fonts, the app works fully offline.
- Source published with an automated APK build (GitHub Actions) and a web version (GitHub Pages).

## 1.1 — 2026-10-26
- New **Merge** tab: combine several PDFs into one, pick page ranges per file (`1-3`, `2, 5, 7`, `4-`, `5-1`), reorder with arrows.
- "Add to merge list" sends the current scan straight to the merge list.
- Broken or password-protected PDFs are flagged with the reason.
- Undo for removed files and cleared list.
- Android back button returns from Merge to Scan.
- Large PDFs are written to storage in chunks.

## 1.0 — 2026-10-19
- Take a photo or pick from the gallery (multiple at once).
- Automatic edge detection, draggable corners with magnifier, perspective correction.
- Scan filters: Magic, Black & white, Grayscale, Original.
- Rotate, reorder and delete pages.
- Multi-page PDF export (A4, Letter, image size; margins; three quality levels).
- Saves to Downloads › Cep Tarayıcı and opens the Android share sheet.
