# Java Server C2PA — Image Metadata Analyzer

A Flask-based REST API server for comprehensive image metadata analysis, C2PA credential extraction, and EXIF/GPS/color space analysis.

## Overview

This server provides a complete image analysis solution with:
- **C2PA credential extraction** and validation
- **EXIF metadata** reading and interpretation
- **GPS data** extraction and mapping
- **Color space analysis** (brightness, dominant colors, color profiles)
- **File integrity verification** (MD5, SHA256 hashes)
- **Web UI** for interactive image analysis
- **JSON export** for automation and integration
- **CORS-enabled REST API** for browser extensions and web apps

### Key Features

🖼️ **Comprehensive Image Analysis**
- Multiple image formats (JPEG, PNG, GIF, WebP, BMP)
- Deep metadata extraction
- C2PA credential validation

🔍 **C2PA Support**
- Extract Content Credentials
- Validate digital signatures
- Display creator information
- Show content history

📊 **Metadata Extraction**
- EXIF data (camera settings, lens info, etc.)
- GPS coordinates (with privacy controls)
- Color profiles and color space
- File hashes for verification

🌐 **Web Interface**
- Upload images or paste URLs
- Visual metadata exploration
- Expandable sections for organization
- Download results as JSON

🔗 **REST API**
- Multiple specialized endpoints
- FormData or JSON input
- Structured JSON responses
- CORS enabled for web applications

## Installation

### Prerequisites

- **Python 3.8+**
- **pip** (Python package manager)
- **Virtual environment** (recommended)

### Setup Steps

1. **Clone the repository**:
```bash
git clone https://github.com/crugiente35/TFM-Provenance-applications.git
cd TFM-Provenance-applications/python_server_C2PA
```

2. **Create virtual environment** (optional but recommended):
```bash
# Windows
python -m venv venv
venv\Scripts\activate

# macOS/Linux
python3 -m venv venv
source venv/bin/activate
```

3. **Install dependencies**:
```bash
pip install -r requirements.txt
```

4. **Start the server**:
```bash
python app.py
```

Output should show:
```
 * Running on http://127.0.0.1:5000
 * Debug mode: off
 * Press CTRL+C to quit
```

5. **Access the server**:
- **Web UI**: http://localhost:5000
- **API endpoints**: http://localhost:5000/api/*

## Project Structure

```
python_server_C2PA/
├── app.py                      # Main Flask application
├── requirements.txt            # Python dependencies
├── README.md                   # This file
├── templates/
│   ├── index.html              # Main web UI
│   └── test.html               # Test interface
├── static/
│   └── img/                    # Static image assets
└── [generated files]
    └── (uploads folder)        # Temporary upload storage
```

### Key Files

**app.py**
- Flask application initialization
- Route handlers for all endpoints
- Image processing functions
- Metadata extraction logic
- C2PA credential handling
- Error handling and validation

**templates/index.html**
- Web user interface
- Image upload form
- Results display with expandable sections
- Download JSON button

**templates/test.html**
- Testing interface
- Quick endpoint testing
- Response preview

**requirements.txt**
- Python package dependencies
- Version specifications
- Easy installation reference

## API Endpoints

### Base URL
```
http://localhost:5000
```

### Web Interface

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/` | GET | Main web interface |
| `/test` | GET | Testing interface |

### Analysis Endpoints

| Endpoint | Input | Returns | Purpose |
|----------|-------|---------|---------|
| `/api/full` | `image`/`url` | Complete JSON | All metadata at once |
| `/api/basic` | `image`/`url` | Image info | Dimensions, format, size |
| `/api/exif` | `image`/`url` | EXIF data | Camera & settings info |
| `/api/gps` | `image`/`url` | GPS coordinates | Location data |
| `/api/color` | `image`/`url` | Color analysis | Color space, brightness |
| `/api/file` | `image`/`url` | File hashes | MD5, SHA256, size |
| `/api/c2pa` | `image`/`url` | C2PA credentials | Creator, history, signatures |
| `/process` | `image`/`url` | Full JSON | Alias for `/api/full` |
| `/download` | `image`/`url` | JSON file | Download results as file |

## Usage Guide

### Web Interface

1. **Open** http://localhost:5000 in your browser
2. **Upload image** or **paste URL**:
   - Click upload button or drag-drop an image
   - Or paste image URL in the text field
3. **View results** in expandable sections:
   - Click section headers to expand/collapse
   - Scroll through metadata
4. **Download** results as JSON:
   - Click "↓ Descargar JSON" button
   - JSON file saves to your downloads folder

### REST API with cURL

**Upload local file:**
```bash
curl -X POST http://localhost:5000/api/full \
  -F "image=@/path/to/image.jpg"
```

**Use image URL:**
```bash
curl -X POST http://localhost:5000/api/exif \
  -F "url=https://example.com/image.jpg"
```

**Get basic info:**
```bash
curl -X POST http://localhost:5000/api/basic \
  -F "image=@photo.jpg"
```

**Get C2PA credentials:**
```bash
curl -X POST http://localhost:5000/api/c2pa \
  -F "url=https://example.com/image.jpg"
```

**Download JSON:**
```bash
curl -X POST http://localhost:5000/api/download \
  -F "image=@photo.jpg" \
  -o metadata.json
```

**Pretty-print JSON response:**
```bash
curl -X POST http://localhost:5000/api/full \
  -F "image=@photo.jpg" | python -m json.tool
```

### Using from JavaScript

**From Browser Extension:**
```javascript
const formData = new FormData();
formData.append('url', 'https://example.com/image.jpg');

fetch('http://localhost:5000/api/full', {
  method: 'POST',
  body: formData
})
.then(response => response.json())
.then(metadata => {
  console.log('Creator:', metadata.c2pa?.creator);
  console.log('Dimensions:', metadata.basic?.dimensions);
  console.log('GPS:', metadata.gps?.coordinates);
})
.catch(error => console.error('Error:', error));
```

**From Web Application:**
```javascript
async function analyzeImage(imageFile) {
  const formData = new FormData();
  formData.append('image', imageFile);
  
  try {
    const response = await fetch('http://localhost:5000/api/full', {
      method: 'POST',
      body: formData
    });
    
    if (!response.ok) {
      throw new Error(`Server error: ${response.status}`);
    }
    
    return await response.json();
  } catch (error) {
    console.error('Analysis failed:', error);
  }
}

// Usage
const fileInput = document.getElementById('file');
fileInput.addEventListener('change', async (e) => {
  const file = e.target.files[0];
  const metadata = await analyzeImage(file);
  console.log('Metadata:', metadata);
});
```

## Response Format

### Full Analysis Response

```json
{
  "basic": {
    "dimensions": "1920x1080",
    "width": 1920,
    "height": 1080,
    "format": "JPEG",
    "mode": "RGB",
    "megapixels": 2.07
  },
  "exif": {
    "camera": "Canon EOS 5D Mark IV",
    "lens": "EF24-70mm f/2.8L II USM",
    "iso": 400,
    "aperture": 2.8,
    "shutter_speed": "1/250",
    "focal_length": "50mm",
    "flash": "Did not fire"
  },
  "gps": {
    "latitude": 40.7128,
    "longitude": -74.0060,
    "altitude": 10.5,
    "timestamp": "2024-01-15T14:30:00Z"
  },
  "color": {
    "color_space": "sRGB",
    "brightness": 128,
    "dominant_colors": ["#FF5733", "#33FF57"],
    "saturation": "medium"
  },
  "file": {
    "size_bytes": 245632,
    "size_mb": 0.235,
    "md5": "a1b2c3d4e5f6...",
    "sha256": "abc123def456..."
  },
  "c2pa": {
    "present": true,
    "valid": true,
    "creator": "John Doe",
    "timestamp": "2024-01-15T10:30:00Z",
    "ai_generated": false,
    "signature_status": "valid",
    "claims": [...]
  }
}
```

### Error Response

```json
{
  "error": "Description of what went wrong",
  "details": "Additional technical information"
}
```

## Configuration

### Environment Variables

```bash
# Server port (default: 5000)
export FLASK_PORT=5000

# Debug mode (default: off)
export FLASK_DEBUG=off

# Max file size (default: 256MB)
export MAX_CONTENT_LENGTH=268435456
```

### Application Settings

Edit `app.py` to customize:

```python
# Maximum file size (bytes)
app.config['MAX_CONTENT_LENGTH'] = 256 * 1024 * 1024

# CORS configuration
CORS(app)

# C2PA trust anchors
TRUST_ANCHORS_URL = "https://contentcredentials.org/trust/anchors.pem"
```

## Extending the Server

### Adding Custom Analysis Functions

1. **Create a new function** in `app.py`:
```python
def analyze_my_feature(img: Image.Image) -> dict:
    """Custom analysis function"""
    # Your logic here
    return {
        "feature": "result",
        "data": "values"
    }
```

2. **Add to main processor**:
```python
def process_image(img_bytes, filename):
    img = Image.open(io.BytesIO(img_bytes))
    return {
        "basic": get_basic_info(img),
        "exif": get_exif_data(img),
        "my_feature": analyze_my_feature(img),  # Add here
        # ... other fields
    }
```

3. **Create REST endpoint**:
```python
@app.route("/api/my_feature", methods=["POST"])
def api_my_feature():
    try:
        result = api_process(request)
        return jsonify(result.get("my_feature", {}))
    except Exception as e:
        return jsonify({"error": str(e)}), 400
```

4. **Test the endpoint**:
```bash
curl -X POST http://localhost:5000/api/my_feature \
  -F "image=@photo.jpg"
```

## Dependencies

### Core Libraries

- **Flask** - Web framework
- **Flask-CORS** - Cross-Origin Resource Sharing
- **Pillow** - Image processing
- **Pillow-SIMD** - Optimized image operations
- **c2pa** - C2PA credential handling
- **piexif** - EXIF metadata extraction
- **requests** - HTTP client for downloading images

### Installation

```bash
pip install -r requirements.txt
```

### Dependency Details

See `requirements.txt` for:
- Exact versions
- Alternative packages
- Optional dependencies

## Troubleshooting

### Server Won't Start

**Problem**: "Address already in use" on port 5000

**Solutions**:
```bash
# Find process using port 5000
# Windows:
netstat -ano | findstr :5000

# macOS/Linux:
lsof -i :5000

# Kill the process and restart server
```

**Problem**: "ModuleNotFoundError: No module named 'flask'"

**Solution**:
```bash
# Activate virtual environment
# Windows: venv\Scripts\activate
# macOS/Linux: source venv/bin/activate

# Install dependencies
pip install -r requirements.txt
```

### Image Upload Errors

**Problem**: "File too large"

**Solution**:
- Maximum file size is 256MB
- Try smaller image or compress first
- Can increase in `app.py` if needed

**Problem**: "Unsupported image format"

**Solution**:
- Supported formats: JPEG, PNG, GIF, WebP, BMP
- Convert image to supported format
- Try online tools like Convertio

### No EXIF Data

**Problem**: "EXIF data not available"

**Causes & Solutions**:
1. Image has no EXIF metadata
   - Some cameras/apps don't embed EXIF
   - Screenshots typically have no EXIF
2. EXIF data was stripped
   - Some tools remove EXIF for privacy
   - Web uploads often strip EXIF
3. Try with a different image that has EXIF data

### GPS Data Not Found

**Problem**: "GPS information not available"

**Causes & Solutions**:
1. Not all images have GPS data
2. GPS data is optional metadata
3. Many cameras/phones have GPS disabled
4. Privacy settings may remove GPS data
5. Try with a smartphone photo (usually has GPS)

### C2PA Not Detected

**Problem**: "No C2PA credentials found"

**This is normal!** Most images don't have C2PA credentials:
- C2PA is still being adopted
- Only images with embedded credentials will show data
- Try images from:
  - Content Credentials partners
  - Adobe products with provenance
  - Reputable media agencies

### CORS Errors

**Problem**: "Cross-Origin Request Blocked"

**Solution**:
- CORS is enabled by default
- Check server is running
- Verify correct URL: `http://localhost:5000`
- Try from http (not https) for localhost

### High Memory Usage

**Problem**: Server uses lots of RAM with large images

**Solutions**:
1. Reduce image size before uploading
2. Restart server periodically
3. Run on machine with more RAM
4. Use thumbnail processing (advanced)

## Performance Optimization

### Large Image Handling

```python
# Create thumbnail for faster processing
from PIL import Image

def create_thumbnail(img_path, size=(800, 600)):
    img = Image.open(img_path)
    img.thumbnail(size, Image.Resampling.LANCZOS)
    return img
```

### Caching Results

Consider caching for frequently analyzed images:

```python
from functools import lru_cache

@lru_cache(maxsize=100)
def expensive_analysis(image_hash):
    # Cached result
    return result
```

### Async Processing

For production, consider async processing:
- Use Celery for background tasks
- Return job ID, poll for results
- Handle large image queues

## Security Considerations

### Input Validation

- File type validation (MIME type)
- File size limits (256MB default)
- URL validation (prevent SSRF)
- Extension whitelist

### Data Privacy

- **EXIF Data**: Includes camera model, GPS, device info
- **C2PA Data**: May include creator information
- **GPS Data**: Exact location coordinates
- **Hashes**: Unique file identifiers

### Recommendations

1. **For sensitive images**: Strip metadata before uploading
2. **Remove GPS**: Use metadata removal tools
3. **Privacy-aware**: Some users may want anonymity
4. **Server logs**: Monitor what's being analyzed

## Deployment

### Local Development

```bash
python app.py
```

### Production Deployment

Not recommended for production without hardening:

1. **Use production WSGI** (not Flask dev server):
```bash
pip install gunicorn
gunicorn -w 4 -b 0.0.0.0:5000 app:app
```

2. **Use reverse proxy** (nginx):
- Handle HTTPS
- Load balancing
- Caching

3. **Security hardening**:
- Validate all inputs strictly
- Rate limiting
- Authentication if needed
- Sanitize user data

4. **Monitoring**:
- Log all requests
- Monitor performance
- Alert on errors

## Contributing

1. **Fork repository** on GitHub
2. **Create branch**: `git checkout -b feature/my-feature`
3. **Add features** with tests
4. **Submit pull request** with description

## Changelog

### v1.0
- Initial Flask server
- C2PA credential extraction
- EXIF metadata reading
- GPS coordinate extraction
- Color space analysis
- File hash calculation
- Web UI interface
- REST API endpoints

## Related Components

- [C2PA Extension](../c2pa_extension_c2pa/) - Frontend for this API
- [JPEG Trust Server](../jpeg_trust_orchestrator/) - Alternative backend
- [Main Project](../README.md) - Complete documentation

## Support

- **API Issues**: Check endpoint documentation above
- **Server Problems**: Review Troubleshooting section
- **Feature Requests**: Open GitHub issue
- **Bug Reports**: Include error message and stack trace

## License

MIT License - See LICENSE file

---

**Technology Stack**:
- Python 3.8+
- Flask (web framework)
- Pillow (image processing)
- c2pa (credential handling)
- piexif (EXIF extraction)

**Last Updated**: June 2026
