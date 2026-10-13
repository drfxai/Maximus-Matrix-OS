# Maximus Matrix OS — Chat, News and Vision delivery (build 10009)

## Chat presentation

- Assistant messages have Copy, direct user-selected Download via Android Storage Access Framework, text-to-speech and optional local feedback (helpful, not helpful, love); feedback stays in app-private preferences and does not silently reach cloud APIs.
- Exported HTML proposals use HTML file names; ordinary text uses Markdown. Android controls the final destination. Nothing is exported without a user action.
- Generated text containing tool markup is shown as a **proposed operation, not executed or verified**. No file is created on-device merely because the model printed create_file XML. Original content remains available for Copy/Download.
- A low-overhead pulsing indicator visualizes model response generation, but it does not fake progress, model reasoning traces, or image-rendering status.

## Verified News

- Official publisher feeds: US Federal Reserve, ECB and Bank for International Settlements. RSS 2.0 and Atom parsing supported.
- Display actual publisher-provided media metadata only when the image URL is HTTPS and matches the official article publisher host. Network image loads enforce origin, redirect, MIME, time, size and decode limits. A neutral generic illustration appears without verified media; no arbitrary web image is relabeled as real news photography.
- True cached articles are displayed offline with a cache-age/status notice. Failures and empty filters show an explicit retry option. The economic calendar remains an optional authorized endpoint; it is not fabricated when disconnected.
- Some publishers don't supply images or may be inaccessible from some networks; this cannot be corrected by UI alone. A live multi-region feed availability check was not performed by Android CI.

## Vision

- Use the device's registered image decoders, sample large dimensions before allocating memory, and normalize to compressed JPEG for vision endpoints. JPEG/PNG/WebP, GIF first frames, BMP and HEIF/HEIC work where supported by Android device APIs/codecs. Android 26–27 fallback handles common EXIF rotations.
- The selected file never executes. Hard upper bound 60 MB for sources with known size, 2048 px longest edge, and 4 MB normalized payload. Unsupported TIFF/SVG or missing codecs return a user-visible error; don't promise unconditional support for every image format/size or unsupported vision routes.
- Model capability checks still apply before remote AI inference; image selection alone doesn't prove the provider accepts it.

## Tests and release conditions

- Unit tests: tool proposal must not claim execution; HTML download payload extraction; ordinary text handling; safe file names; publisher-host image validation and RSS/Atom entries.
- Android emulator: large PNG normalized to bounded JPEG, plus existing conversations, credentials, keyboard and Studio tests.
- Run unit tests/lint/debug assembly and both instrumented emulator suites; preserve public V1.0.0 and release signing identity. Increment internal versionCode to 10009. No release until CI and signed upgrade checks pass.

## Next tasks

- Full source-by-source News endpoint diagnostics using a network environment similar to the user's connectivity. Add cache age labeling and source-specific error details.
- Full document/media ingestion (PDF, ZIP, audio/video), image generation and realtime voice only through verified APIs / trusted remote tool runtime.
- Full internationalization of older English-only Chat, News and Vision strings.
