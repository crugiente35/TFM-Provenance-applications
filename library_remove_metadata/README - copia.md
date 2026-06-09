# Library Remove Metadata (C2PA)

This module provides tools for manipulating and cleaning traditional metadata (such as EXIF) in images, while ensuring that the provenance chain (C2PA manifest) is preserved, updated, and cryptographically signed at each step.

It is ideal for workflows where anonymizing or processing images is required without breaking the trust history and media traceability.

## 📁 Directory Structure

```text
library_remove_metadata/
│
├── c2pa_cleaner_injector.py    # Core library containing C2PACleaner and C2PAMetadataInjector classes.
├── test.py                     # Execution and testing script for the full workflow.
│
├── woman_2.jpg                 # Original test image (Input).
├── woman_2_with_fake_exif.jpg  # Temporary image generated with injected EXIF metadata.
├── woman_2_cleaned.jpg         # Final clean image, without EXIF but with intact C2PA history.
│
└── certificados/               # Directory containing C2PA signing credentials.
    ├── certs.pem               # Public certificate.
    └── privKey.pem             # Private key (must be free of NUL bytes or invalid characters).

```

## ⚙️ Requirements

Make sure you have the following Python dependencies installed in your environment:

```bash
pip install -r requirements.txt

```

## 🚀 Usage and Workflow

The `test.py` script demonstrates the full lifecycle of metadata modification using two main classes:

1. **Metadata Injection (`C2PAMetadataInjector`)**:
Takes the original image (`woman_2.jpg`), injects simulated EXIF metadata (GPS, Author, etc.), and generates a new C2PA manifest declaring the `c2pa.edited` and `c2pa.opened` actions. The previous history is preserved as an ingredient (`parentOf`). The result is saved as `woman_2_with_fake_exif.jpg`.
2. **Cleaning and Anonymization (`C2PACleaner`)**:
Takes the image with the injected metadata, removes all traces of non-C2PA metadata (EXIF) for privacy reasons, and generates a new C2PA assertion indicating the cleanup with `c2pa.edited` and `c2pa.opened` actions. The final image is saved as `woman_2_cleaned.jpg`.

### Running the Test

To run the test, simply execute:

```bash
python test.py

```

**Expected Output:**
The script will log to the console the image opening, successful EXIF injection, C2PA history chaining, and finally, the image cleanup to create the final secure file.
