# TFM Provenance Applications

A comprehensive suite of tools for **C2PA (Content Credentials)** and **JPEG Trust** based image provenance verification. This project demonstrates AI-generated content detection, image authenticity validation, and metadata analysis through multiple integrated components.

## Overview

This Master's Thesis (TFM) project provides a complete ecosystem for verifying image provenance and detecting AI-generated content using:
- **C2PA (Content Credentials)**: Industry standard for embedding verifiable content history
- **JPEG Trust**: Enhanced JPEG format with provenance indicators
- **Browser Extensions**: User-friendly interfaces for instant verification
- **REST APIs**: Backend services for metadata processing and validation

## Project Architecture

```
TFM-Provenance-applications/
├── c2pa_extension_c2pa/              # Browser extension (C2PA verification)
├── c2pa_extension_jpegTrust/         # Browser extension (JPEG Trust verification)
├── python_server_C2PA/                 # Flask server (Image metadata analysis)
└── jpeg_trust_orchestrator/          # Spring Boot server (JPEG Trust orchestration)
```

## Components

### 1. **c2pa_extension_c2pa**
Browser extension for C2PA image verification
- **Technology**: Chrome/Firefox manifest v3
- **Purpose**: Hover over images to verify C2PA credentials
- **Server**: Connects to `http://localhost:5000` (Java Server)
- [Details](c2pa_extension_c2pa/README.md)

### 2. **c2pa_extension_jpegTrust**
Browser extension for JPEG Trust image verification
- **Technology**: Chrome/Firefox manifest v3
- **Purpose**: Hover over images to verify JPEG Trust indicators
- **Server**: Connects to `http://localhost:8085` (JPEG Trust Orchestrator)
- [Details](c2pa_extension_jpegTrust/README.md)

### 3. **python_server_C2PA**
Flask-based REST API for image metadata analysis
- **Technology**: Python, Flask, Pillow, c2pa library
- **Purpose**: Extract C2PA credentials, EXIF, GPS, color analysis, file hashes
- **Port**: 5000
- **Features**: 
  - Multiple API endpoints for different metadata types
  - Web UI for interactive analysis
  - JSON export functionality
  - CORS enabled for browser extensions
- [Details](python_server_C2PA/README.md)

### 4. **jpeg_trust_orchestrator**
Spring Boot application for JPEG Trust operations
- **Technology**: Java 21, Spring Boot 4.0.5, Maven
- **Purpose**: Process and orchestrate JPEG Trust operations with C2PA support
- **Port**: 8085
- **Features**:
  - REST API for JPEG processing
  - JPEG Trust validation
  - C2PA manifest generation
  - Support for multiple media formats (MP3, MP4, etc.)
  - Git submodules for mipams libraries
- [Details](jpeg_trust_orchestrator/README.md)

## Quick Start

### Prerequisites
- **Python 3.8+** (for Flask server)
- **Java 21+** (for Spring Boot server)
- **Maven 3.8.0+** (for Spring Boot)
- **Chrome/Firefox** (for browser extensions)
- **Git** (for cloning with submodules)

### Installation

1. **Clone the repository** (with submodules):
```bash
git clone --recurse-submodules https://github.com/crugiente35/TFM-Provenance-applications.git
cd TFM-Provenance-applications
```

2. **Start Flask server** (Image Metadata Analyzer):
```bash
cd python_server_C2PA
pip install -r requirements.txt
python app.py
# Server runs on http://localhost:5000
```

3. **Start Spring Boot server** (JPEG Trust Orchestrator):
```bash
cd ../jpeg_trust_orchestrator
mvn clean install
mvn spring-boot:run
# Server runs on http://localhost:8085
```

4. **Load browser extensions**:
   - Open `chrome://extensions` (Chrome) or `about:addons` (Firefox)
   - Enable "Developer mode"
   - Click "Load unpacked" and select `c2pa_extension_c2pa/`
   - Repeat for `c2pa_extension_jpegTrust/`

5. **Visit a website with images** and hover over images to see provenance information!

## API Endpoints

### Java Server (C2PA) - Port 5000

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/` | GET | Web UI |
| `/api/full` | POST | Complete metadata analysis |
| `/api/basic` | POST | Basic image info (dimensions, format, size) |
| `/api/exif` | POST | EXIF data |
| `/api/gps` | POST | GPS coordinates |
| `/api/color` | POST | Color space analysis |
| `/api/file` | POST | File hashes (MD5, SHA256) |
| `/api/c2pa` | POST | C2PA credentials |
| `/process` | POST | Full processing (used by web UI) |
| `/download` | POST | Download JSON export |

### JPEG Trust Orchestrator - Port 8085

- RESTful API for JPEG Trust operations
- C2PA manifest management
- Media format detection and processing

## Usage Examples

### Using the Browser Extensions

1. **C2PA Inspector**: 
   - Hover over any image on a webpage
   - A popup appears showing C2PA credential information
   - Green indicator = authentic content with valid provenance
   - Orange/Red = issues found with credentials

2. **JPEG Trust Inspector**:
   - Same hover interaction
   - Shows JPEG Trust indicators
   - Indicates AI-generated content risk levels

### Using the REST APIs with cURL

```bash
# Analyze an image file
curl -X POST http://localhost:5000/api/full -F "image=@photo.jpg"

# Get C2PA credentials for an image URL
curl -X POST http://localhost:5000/api/c2pa -F "url=https://example.com/image.jpg"

# Download results as JSON
curl -X POST http://localhost:5000/api/download -F "image=@photo.jpg" > metadata.json
```

### Using the APIs from JavaScript

```javascript
// From browser extension or web application
const fd = new FormData();
fd.append('image', imageFile); // or 'url' for image URL

const response = await fetch('http://localhost:5000/api/full', {
  method: 'POST',
  body: fd
});
const metadata = await response.json();
console.log(metadata);
```

## Project Structure Details

### Directory Layout

```
TFM-Provenance-applications/
├── README.md                          # This file
├── c2pa_extension_c2pa/
│   ├── manifest.json                  # Extension configuration
│   ├── content.js                     # Image detection & popup logic
│   ├── popup.css                      # UI styling
│   └── icons/                         # Extension icons
├── c2pa_extension_jpegTrust/
│   ├── manifest.json
│   ├── content.js
│   ├── popup.css
│   └── icons/
├── python_server_C2PA/
│   ├── app.py                         # Flask application
│   ├── requirements.txt               # Python dependencies
│   ├── templates/
│   │   ├── index.html                 # Web UI
│   │   └── test.html
│   └── static/
│       └── img/
└── jpeg_trust_orchestrator/
    ├── pom.xml                        # Maven configuration
    ├── src/                           # Java source code
    ├── mipams-jpeg-trust/             # Git submodule (custom)
    ├── mipams-jpeg-systems/           # Git submodule (original)
    └── info/                          # Documentation & specs
```

## Key Features

### C2PA Support
- Extract and validate C2PA credentials
- Display content history and creator information
- Verify digital signatures
- Detect credential tampering
- Show AI-generation claims

### JPEG Trust Integration
- Process JPEG Trust indicators
- Validate provenance metadata
- Detect potential AI-generated content
- Integration with mipams libraries

### Multi-Format Processing
- JPEG/JPG images
- PNG images
- GIF images
- WebP images
- MP3, MP4, and other media formats

### User-Friendly Interfaces
- Hover-based popup verification
- Web UI for batch analysis
- JSON export for automation
- Visual trust indicators

## Technologies Used

| Component | Technology Stack |
|-----------|------------------|
| **c2pa_extension_c2pa** | JavaScript, HTML, CSS (Chrome/Firefox MV3) |
| **c2pa_extension_jpegTrust** | JavaScript, HTML, CSS (Chrome/Firefox MV3) |
| **python_server_C2PA** | Python, Flask, Pillow, c2pa, Pillow-SIMD |
| **jpeg_trust_orchestrator** | Java 21, Spring Boot 4.0.5, Maven, BouncyCastle |

## Documentation

- [C2PA Extension](c2pa_extension_c2pa/README.md) - C2PA verification extension
- [JPEG Trust Extension](c2pa_extension_jpegTrust/README.md) - JPEG Trust verification extension
- [Java Server](python_server_C2PA/README.md) - Image metadata analyzer
- [JPEG Trust Orchestrator](jpeg_trust_orchestrator/README.md) - JPEG Trust processor
- [C2PA Technical Spec](jpeg_trust_orchestrator/info/) - C2PA and related documentation

## Troubleshooting

### Browser Extension Not Working
1. Ensure Flask server is running: `python app.py` in `python_server_C2PA/`
2. Check extension is loaded: `chrome://extensions/`
3. Open browser console (F12) for error messages
4. Verify localhost:5000 is accessible

### Server Connection Issues
1. Check servers are running on correct ports:
   - Flask: `http://localhost:5000`
   - Spring Boot: `http://localhost:8085`
2. Check firewall doesn't block localhost connections
3. Verify CORS is enabled on servers

### Image Processing Errors
1. Ensure image formats are supported (JPEG, PNG, GIF, WebP)
2. Check file sizes don't exceed 256MB limit
3. Verify C2PA credentials are properly embedded
4. Check server logs for detailed error messages

## Contributing

When modifying submodules (mipams-jpeg-trust, mipams-jpeg-systems):
1. Make changes in the submodule
2. Commit and push to the submodule repository
3. Update the main repository submodule reference:
   ```bash
   git add jpeg_trust_orchestrator/mipams-jpeg-trust
   git commit -m "Update mipams-jpeg-trust submodule"
   ```

## License

This project is licensed under the **MIT License**.

### Third-Party Dependencies
- **mipams-jpeg-trust** - BSD 3-Clause License
- **mipams-jpeg-systems** - BSD 3-Clause License
- **Spring Boot** - Apache License 2.0
- **c2pa** - MIT License
- **BouncyCastle** - MIT License
- **Flask** - BSD License
- **Pillow** - HPND License

See individual component LICENSE files for details.

## Author

Developed as part of a Master's Thesis (TFM) project on Provenance on AI-Generated Images.

---

**Last Updated**: June 2026
