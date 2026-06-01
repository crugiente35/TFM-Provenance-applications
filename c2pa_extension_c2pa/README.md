# C2PA Image Inspector — Browser Extension

A lightweight Chrome/Firefox extension that detects and displays **C2PA (Content Credentials)** information for images on any webpage.

## Overview

This extension automatically detects images while you browse, and when you **hover over an image**, it fetches C2PA credential data from a backend server and displays it in a beautiful popup overlay.

### Key Features
- 🔍 **Automatic image detection** on any webpage
- 🎯 **Hover-based verification** - no clicks needed
- 📊 **C2PA credential display** showing:
  - Creator information
  - Content history
  - Digital signatures
  - Embedding timestamps
  - AI-generation claims
- ⚡ **Smart caching** to avoid duplicate API calls
- 🎨 **Beautiful popup UI** with color-coded trust indicators
- 🌐 **CORS-enabled** to work with local development servers

## Installation

### For Development/Testing

1. **Clone the repository**:
```bash
git clone https://github.com/crugiente35/TFM-Provenance-applications.git
cd TFM-Provenance-applications/c2pa_extension_c2pa
```

2. **Open the extension in your browser**:

   **Chrome:**
   - Navigate to `chrome://extensions/`
   - Enable "Developer mode" (top right)
   - Click "Load unpacked"
   - Select the `c2pa_extension_c2pa` folder
   - Pin the extension to your toolbar

   **Firefox:**
   - Navigate to `about:addons`
   - Click the settings icon (⚙️)
   - Select "Debug Add-ons"
   - Click "Load Temporary Add-on"
   - Select `manifest.json` from the `c2pa_extension_c2pa` folder

3. **Start the backend server** (from project root):
```bash
cd python_server_C2PA
pip install -r requirements.txt
python app.py
# Server will run on http://localhost:5000
```

4. **Test it**:
   - Visit any webpage with images
   - Hover over an image
   - A popup should appear showing C2PA data (or a message if no credentials exist)

## How It Works

### Architecture

```
[Webpage with Images]
        ↓
   [content.js]
    (detects images)
        ↓
  [Hover Event]
        ↓
  [API Request to C2PA Server]
  (http://localhost:5000/api/full)
        ↓
  [Parse & Cache Response]
        ↓
  [Display Popup Overlay]
```

### Processing Flow

1. **Content script loads** when webpage renders
2. **Image detection**: Automatically finds all `<img>` tags and image elements
3. **Hover listener**: When you hover over an image:
   - Extract image URL
   - Check cache for previous results
   - If not cached, send POST request to backend
   - Backend analyzes image and extracts C2PA credentials
   - Response is cached for future hovers
4. **Popup display**: Result is shown in a styled overlay near cursor
5. **Auto-hide**: Popup disappears when you move cursor away

### Data Flow

```
Image Hover
    ↓
Extract Image URL
    ↓
Check Cache
    ├─ Hit: Return cached result
    ├─ Miss: Fetch from API
    │   └─ POST to /api/full
    │       └─ Parse C2PA metadata
    │       └─ Return JSON
    └─ Show Popup
        ├─ Format metadata
        ├─ Color-code by trust level
        └─ Position near cursor
```

## File Structure

```
c2pa_extension_c2pa/
├── manifest.json          # Extension configuration & permissions
├── content.js             # Main content script (runs on webpages)
├── popup.css              # Popup styling
└── icons/
    ├── icon16.png         # 16px extension icon
    ├── icon48.png         # 48px extension icon
    └── icon128.png        # 128px extension icon
```

### File Descriptions

**manifest.json**
- Declares extension metadata (name, version, description)
- Configures content script to run on all URLs
- Sets permissions for localhost:5000 access
- Defines extension icons

**content.js**
- Main logic running on every webpage you visit
- Detects images and adds hover listeners
- Manages popup display and positioning
- Implements API request caching
- Handles loading and error states

**popup.css**
- Styles the metadata display popup
- Color coding for different metadata types
- Responsive positioning to stay visible
- Dark theme for readability over images

**icons/**
- Visual representation in browser UI
- Different sizes for different contexts

## Configuration

### Backend Server Connection

The extension connects to `http://localhost:5000` by default. To change this:

1. Edit `content.js`
2. Find the line: `const API_BASE = "http://localhost:5000";`
3. Replace with your server URL
4. Reload extension in browser

```javascript
// Change this URL to your server
const API_BASE = "http://localhost:5000";
```

### Required Server

- **Type**: REST API server
- **Default URL**: `http://localhost:5000`
- **Required Endpoint**: `POST /api/full`
- **Input**: FormData with `url` parameter (image URL)
- **Output**: JSON with C2PA metadata

See [Java Server C2PA](../python_server_C2PA/README.md) for the reference implementation.

## Usage

### Basic Usage

1. Extension is automatically active after installation
2. Browse any webpage with images
3. Move your cursor over an image
4. Popup appears showing C2PA data
5. Move cursor away to hide popup

### What Information Is Displayed?

The popup shows C2PA credential information including:

**Creator Information**
- Creator name/claim
- Creator signature
- Claim generator information

**Content History**
- When the image was created/modified
- What tools were used
- AI-generation claims
- Editing history

**Validation Status**
- ✅ Signature valid
- ⚠️ Data modified
- ❌ Signature invalid
- ⏸️ Awaiting verification

**Trust Indicators**
- 🟢 Green: Trusted creator
- 🟡 Yellow: Unverified
- 🔴 Red: Issues detected

### Keyboard Shortcuts

- **F12**: Open browser console to see debugging info
- **Ctrl+Shift+I (Cmd+Opt+I on Mac)**: Open Developer Tools

## Troubleshooting

### Popup Not Appearing

**Problem**: No popup shows when hovering over images

**Solutions**:
1. Check if Flask server is running:
   ```bash
   curl http://localhost:5000/
   ```
2. Open browser console (F12) and check for errors
3. Verify extension is enabled: `chrome://extensions/`
4. Check that image URL is accessible
5. Reload extension with the ↻ button

### Connection Refused Error

**Problem**: "Failed to connect to http://localhost:5000"

**Solutions**:
1. Start the Flask server:
   ```bash
   cd python_server_C2PA
   python app.py
   ```
2. Verify server is running on port 5000
3. Check firewall isn't blocking localhost
4. Try accessing http://localhost:5000 in browser directly

### Images Not Detected

**Problem**: No hovers work on certain websites

**Solutions**:
1. Some websites load images via JavaScript after page load
2. Some images are in iframes (extension can't access them)
3. Check browser console for Content Security Policy (CSP) errors
4. Try refreshing the page

### Popup Positioned Wrong

**Problem**: Popup appears off-screen or covered

**Solutions**:
1. Auto-positioning should handle most cases
2. Try moving image to different part of screen
3. Maximize browser window
4. Check if another extension is interfering

### Cache Issues

**Problem**: Seeing old data for the same image

**Solutions**:
1. Clear extension cache: Ctrl+Shift+Delete → "All time" → "Cookies and other site data"
2. Right-click extension → "Remove" and reinstall
3. Cache is per browser session, will clear on restart

## API Specification

### Required Endpoint

```
POST http://localhost:5000/api/full
```

**Request:**
```javascript
const fd = new FormData();
fd.append('url', 'https://example.com/image.jpg');

fetch('http://localhost:5000/api/full', {
  method: 'POST',
  body: fd
})
.then(r => r.json())
.then(data => console.log(data))
.catch(e => console.error(e));
```

**Response:**
```json
{
  "c2pa": {
    "valid": true,
    "claims": [
      {
        "creator": "John Doe",
        "timestamp": "2024-01-15T10:30:00Z",
        "ai_generated": false,
        "signature_status": "valid"
      }
    ]
  },
  "file": {
    "size": 245632,
    "format": "JPEG"
  }
}
```

## Performance Considerations

### Caching Strategy

- **Per-session cache**: Results are cached during your browser session
- **Deduplication**: Simultaneous requests for the same image are merged
- **Memory efficient**: Cache automatically expires

### Optimization Tips

1. Enable caching (default): Faster repeated image verification
2. Cache is automatically managed
3. Large images may take longer to process
4. Server performance depends on backend implementation

## Security Considerations

### What Data Is Sent?

1. **Image URLs** - The URL of the image you hover over
2. **Image Data** - If using file upload (not in this extension)

### What Data Is Stored?

1. **Locally**: URL → metadata mapping (in-memory, cleared on browser close)
2. **Server-side**: Depends on backend server implementation

### Privacy

- Extension only sends image URLs
- No personal data is collected
- Cache is local only
- Metadata comes from image file itself (C2PA credentials)

### Security Best Practices

1. Only install from official stores
2. Keep extension updated
3. Be cautious on untrusted websites
4. Review code at [GitHub repo](https://github.com/crugiente35/TFM-Provenance-applications)

## Development

### Project Structure

```javascript
// Main sections in content.js:
1. Constants & configuration (API_BASE, element selectors)
2. Cache management (Map structure, deduplication)
3. Popup DOM management (creation, positioning, styling)
4. Image detection (finding <img> tags, adding listeners)
5. API communication (fetch, error handling, response parsing)
6. CSS injection (popup styling)
```

### Adding Features

1. **New metadata fields**: Edit popup HTML in `showPopup()`
2. **Styling changes**: Modify CSS strings in `content.js`
3. **Server endpoint**: Change `API_BASE` and adjust request/response handling
4. **New permissions**: Update `manifest.json` with required permissions

### Testing

```bash
# Manual testing
1. Load extension in Chrome/Firefox
2. Open DevTools (F12)
3. Go to any webpage with images
4. Hover over images
5. Check console for logs
6. Inspect popup styling
```

## Contributing

1. Fork the repository
2. Create feature branch: `git checkout -b feature/my-feature`
3. Make changes and test thoroughly
4. Submit pull request with description

## API Integration Examples

### With Backend Server

See [Java Server C2PA](../python_server_C2PA/README.md) for the Flask server that powers this extension.

### Custom Server Integration

To use with your own server:

1. Implement `POST /api/full` endpoint
2. Accept `url` form parameter (image URL)
3. Return JSON with structure similar to reference implementation
4. Update `API_BASE` in `content.js`

## Changelog

### v1.0
- Initial release
- C2PA credential display
- Hover-based verification
- Result caching
- Cross-browser compatibility

## Support

- **Issues**: [GitHub Issues](https://github.com/crugiente35/TFM-Provenance-applications/issues)
- **Documentation**: See main [README.md](../README.md)
- **Server Issues**: See [Java Server Documentation](../python_server_C2PA/README.md)

## License

MIT License - See LICENSE file for details

---

**Related Components**:
- [JPEG Trust Extension](../c2pa_extension_jpegTrust/) - JPEG Trust verification
- [Java Server (Flask)](../python_server_C2PA/) - Backend metadata analyzer
- [Main Project](../README.md) - Complete project documentation

**Last Updated**: June 2026
