# C2PA Image Inspector — JPEG Trust Edition

A Chrome/Firefox browser extension for detecting and displaying **JPEG Trust** provenance indicators and AI-generation risk assessments on images across the web.

## Overview

This extension complements the standard C2PA extension by providing an alternative verification method using **JPEG Trust indicators**. When you hover over images, it connects to the JPEG Trust Orchestrator backend and displays:

- JPEG Trust verification status
- AI-generation likelihood indicators  
- Provenance confidence scores
- Trust metadata and indicators

### Key Features

- 🔍 **Automatic image detection** across all webpages
- 🎯 **Hover-based verification** - instant trust assessment
- 📊 **JPEG Trust indicators** showing:
  - Provenance confidence levels
  - AI-generation risk assessment
  - Trust metadata flags
  - Processing history
- ⚡ **Intelligent caching** to minimize backend requests
- 🎨 **Color-coded trust levels** (green/yellow/red)
- 🌐 **Local backend integration** via Spring Boot server

## Installation

### For Development/Testing

1. **Clone the repository**:
```bash
git clone https://github.com/crugiente35/TFM-Provenance-applications.git
cd TFM-Provenance-applications/c2pa_extension_jpegTrust
```

2. **Load the extension in your browser**:

   **Chrome:**
   - Navigate to `chrome://extensions/`
   - Enable "Developer mode" (toggle top right)
   - Click "Load unpacked"
   - Select the `c2pa_extension_jpegTrust` folder
   - Pin extension to toolbar for easy access

   **Firefox:**
   - Navigate to `about:addons`
   - Click the settings ⚙️ icon
   - Select "Debug Add-ons"
   - Click "Load Temporary Add-on"
   - Select `manifest.json` from `c2pa_extension_jpegTrust`

3. **Start the JPEG Trust backend**:
```bash
cd jpeg_trust_orchestrator
mvn clean install
mvn spring-boot:run
# Server will run on http://localhost:8085
```

4. **Start hovering**:
   - Visit any website with images
   - Hover over images to see JPEG Trust indicators
   - Trust levels and risk assessments appear instantly

## How It Works

### Architecture

```
[Webpage with Images]
        ↓
   [content.js]
    (image detection)
        ↓
  [Hover Over Image]
        ↓
 [JPEG Trust API Call]
 (localhost:8085)
        ↓
[Spring Boot Server]
(analyze image & JPEG Trust)
        ↓
[Parse Results & Cache]
        ↓
[Display Trust Popup]
```

### Processing Pipeline

1. **Extension loads** and injects content script
2. **Auto-detect images** on every webpage element
3. **User hovers** over an image
4. **Check cache** for previous results (instant if cached)
5. **Query backend** if not cached:
   - Send image URL to `/api/analyze` (or similar endpoint)
   - Server analyzes JPEG Trust indicators
   - Server detects AI-generation risks
   - Return structured trust metadata
6. **Display popup** with color-coded trust indicators:
   - 🟢 **Green**: Trusted, human-created content
   - 🟡 **Yellow**: Uncertain, unverified content
   - 🔴 **Red**: Risk detected, likely AI-generated

### Data Flow

```
Hover Over Image
        ↓
Extract Image URL
        ↓
    Cache Lookup
    ├─ Hit → Show Cached Result
    ├─ Miss → API Request
    │        └─ POST to /api/analyze
    │        └─ JPEG Trust Analysis
    │        └─ Risk Assessment
    │        └─ Return Metadata
    └─ Display Popup
       ├─ Format Trust Indicators
       ├─ Color Code Risk Level
       ├─ Position Near Cursor
       └─ Auto-Hide on Mouse Leave
```

## File Structure

```
c2pa_extension_jpegTrust/
├── manifest.json          # Chrome/Firefox manifest configuration
├── content.js             # Content script (runs on all webpages)
├── popup.css              # Popup styling and UI
└── icons/
    ├── icon16.png         # 16×16px browser action icon
    ├── icon48.png         # 48×48px popup icon
    └── icon128.png        # 128×128px store icon
```

### File Descriptions

**manifest.json**
- Extension metadata (name, version, description)
- Content script injection configuration
- Host permissions for localhost:8085
- Extension icon paths and sizes
- Manifest version 3 (latest standard)

**content.js**
- Image detection on webpages
- Hover listener attachment
- JPEG Trust API communication
- Response caching mechanism
- Popup UI rendering and positioning
- Error handling and loading states

**popup.css**
- Popup overlay styling
- Color-coded trust level indicators
- Responsive positioning logic
- Typography and spacing
- Animation effects

**icons/**
- 16×16: Browser toolbar icon
- 48×48: Popup display icon
- 128×128: Store listing icon

## Configuration

### Backend Server Connection

By default, the extension connects to `http://localhost:8085`. To modify:

1. Open `content.js`
2. Locate: `const API_BASE = "http://localhost:8085";`
3. Change to your JPEG Trust server URL
4. Reload extension in browser (refresh icon)

```javascript
// Customize server URL here
const API_BASE = "http://localhost:8085";
```

### Required Server Setup

- **Type**: REST API (Spring Boot)
- **Default Port**: 8085
- **Base URL**: `http://localhost:8085`
- **Required Endpoint**: `POST /api/analyze` (or equivalent)
- **Input Format**: FormData with `url` parameter
- **Output Format**: JSON with trust metadata

See [JPEG Trust Orchestrator](../jpeg_trust_orchestrator/README.md) for the reference implementation.

## Usage Guide

### Basic Operation

1. **Install extension** (see Installation section)
2. **Start backend server** (JPEG Trust Orchestrator)
3. **Browse normally** - extension works automatically
4. **Hover over images** to see JPEG Trust analysis
5. **Check trust indicators** in popup (green/yellow/red)

### Understanding Trust Levels

#### 🟢 Green - Trusted Content
- Human-created image confirmed
- Valid JPEG Trust indicators
- No AI-generation detected
- Creator verified (if available)

#### 🟡 Yellow - Uncertain Content
- JPEG Trust data incomplete
- No clear AI-generation indicators
- Content from unknown source
- Requires manual verification

#### 🔴 Red - Risk Detected
- Likely AI-generated content
- Modified/suspicious indicators
- JPEG Trust validation failed
- High confidence of synthetic image

### Information Displayed

**Trust Indicators**
- Overall trust score (0-100%)
- AI-generation risk level
- Provenance confidence

**Metadata**
- Image format and size
- Processing history
- Trust flags and warnings
- Creator information (if available)

**Status**
- ✅ Verified & trusted
- ⚠️ Unverified content
- ❌ Issues detected
- ⏳ Analysis in progress

### Keyboard Shortcuts

- **F12**: Open Developer Tools for debugging
- **Ctrl+Shift+I** (Windows/Linux): Developer Tools
- **Cmd+Option+I** (macOS): Developer Tools

## Troubleshooting

### Popup Not Appearing

**Symptoms**: Hovering over images shows nothing

**Solutions**:
1. Verify JPEG Trust server is running:
   ```bash
   curl http://localhost:8085/health
   ```
2. Check browser console (F12) for errors
3. Confirm extension is enabled: `chrome://extensions`
4. Reload extension using the ↻ button
5. Try refreshing the webpage

### Connection Failed

**Symptoms**: "Could not connect to JPEG Trust server"

**Solutions**:
1. Start the Spring Boot server:
   ```bash
   cd jpeg_trust_orchestrator
   mvn spring-boot:run
   ```
2. Verify it's listening on port 8085
3. Check localhost firewall rules
4. Test connection directly:
   ```bash
   curl http://localhost:8085/api/ping
   ```

### Images Not Detected

**Symptoms**: No hovers work on certain sites

**Causes & Solutions**:
1. **Images loaded by JavaScript**: Page may load images after initial render
   - Refresh the page
   - Scroll to load lazy-loaded images

2. **Images in iframes**: Extension can't access cross-origin iframes
   - This is a browser security limitation
   - Try opening image in new tab

3. **CSP restrictions**: Website blocks external script access
   - Check browser console for CSP errors
   - May not be fixable without server-side changes

4. **Incompatible image format**: Some formats not supported
   - Supported: JPEG, PNG, GIF, WebP, BMP
   - Unsupported: SVG, HEIC, AVIF (check your server)

### Popup Position Issues

**Symptoms**: Popup appears off-screen or overlaps incorrectly

**Solutions**:
1. Move cursor to different location (auto-repositioning)
2. Maximize browser window
3. Try different image positions on page
4. Check if another extension is interfering
5. Test in incognito mode

### Slow Response Time

**Symptoms**: Popup takes several seconds to appear

**Causes & Solutions**:
1. **First request to server**: Warmup time normal
2. **Backend processing**: Analyze image takes time
   - Larger images take longer
   - Complex JPEG Trust data increases processing
3. **Network latency**: Check localhost connection speed
4. **Server resources**: Backend may be overloaded
   - Check server logs
   - Restart if needed

### Caching Issues

**Symptoms**: Seeing old data for modified images

**Solutions**:
1. **Clear extension cache**:
   - Ctrl+Shift+Delete (Cmd+Shift+Delete on Mac)
   - Select "All time"
   - Check "Cookies and other site data"
   - Click "Clear data"

2. **Remove and reinstall extension**:
   - Chrome: Extensions → Remove
   - Firefox: Add-ons → Remove
   - Reload extension

3. **Restart browser**: Clears session cache

### Server Connection Timeout

**Symptoms**: "Request timeout" or "No response from server"

**Solutions**:
1. Increase timeout in code (advanced):
   - Edit `content.js`
   - Find fetch timeout settings
   - Increase timeout value

2. Check server logs:
   ```bash
   # While server is running, check output for errors
   ```

3. Restart server if frozen:
   - Stop: Ctrl+C
   - Start: `mvn spring-boot:run`

## API Specification

### Required Endpoint Format

```
POST http://localhost:8085/api/analyze
```

**Request Format:**
```javascript
const formData = new FormData();
formData.append('url', 'https://example.com/image.jpg');

fetch('http://localhost:8085/api/analyze', {
  method: 'POST',
  body: formData
})
.then(response => response.json())
.then(data => console.log(data))
.catch(error => console.error(error));
```

**Expected Response Structure:**
```json
{
  "trust": {
    "level": "high",
    "score": 95,
    "color": "green"
  },
  "ai_risk": {
    "detected": false,
    "confidence": 0.02,
    "level": "low"
  },
  "jpeg_trust": {
    "valid": true,
    "indicators_count": 3,
    "last_processed": "2024-01-15T10:30:00Z"
  },
  "metadata": {
    "format": "JPEG",
    "size_bytes": 245632,
    "dimensions": "1920x1080"
  }
}
```

## Performance Optimization

### Caching Strategy

- **Per-session caching**: Results cached during browser session
- **Automatic deduplication**: Same image URL requests merged
- **Memory management**: Cache cleared on browser close
- **Instant retrieval**: Cached results show immediately

### Load Time Optimization

1. **Enable caching** (default behavior)
2. **Batch requests** for multiple images
3. **Lazy load**: Extension detects images as needed
4. **Server optimization**: Backend determines speed

### Bandwidth Considerations

- Only image URLs sent (not image data)
- Compact JSON responses
- Efficient caching reduces requests
- Suitable for development and production

## Security & Privacy

### Data Transmission

**Sent to server:**
- Image URL only
- No image pixels or data
- No personal information
- No browsing history

**Stored Locally:**
- Cache in browser memory
- Cleared on browser close
- No local file storage
- Session-based only

### Privacy Practices

- No data tracking or analytics
- No third-party services
- No account creation required
- Open source (audit-able)

### Security Best Practices

1. **Only install from official sources**
   - Chrome Web Store
   - Firefox Add-ons
   - GitHub releases

2. **Keep extension updated**
   - Auto-updates for security patches
   - Review changelogs

3. **Use on trusted networks**
   - Localhost assumed safe
   - Be cautious on public WiFi

4. **Review the code**
   - Open source on GitHub
   - Inspect before installation

## Development Guide

### Project Layout

```javascript
// content.js structure:
1. Configuration constants (API_BASE, selectors)
2. State management (cache, pending requests)
3. Popup DOM utilities (create, show, hide, position)
4. Image detection (find images, add listeners)
5. API communication (fetch, parse, cache)
6. CSS styles (injected popup styles)
7. Initialization (run when content loads)
```

### Customization

**Change server URL:**
```javascript
const API_BASE = "http://your-server:8085";
```

**Modify popup appearance:**
- Edit CSS strings in `showPopup()` function
- Colors, fonts, sizes in `popup.css`

**Add new fields:**
- Extend the popup HTML template
- Parse additional response fields
- Update styling as needed

**Change detection behavior:**
- Modify image selector in `findImages()`
- Adjust hover listener logic
- Customize display conditions

### Testing

```bash
# Manual testing steps:
1. Load extension in Chrome/Firefox
2. Open DevTools with F12
3. Go to webpage with images
4. Hover over different images
5. Check Console tab for logs
6. Inspect popup styling (Elements tab)
7. Check Network tab for API calls
```

## Contributing

1. **Fork repository** on GitHub
2. **Create feature branch**: `git checkout -b feature/my-feature`
3. **Make changes** with meaningful commits
4. **Test thoroughly** across browsers
5. **Submit pull request** with description

## Changelog

### v1.0 (Initial Release)
- JPEG Trust indicator display
- AI-generation risk detection
- Hover-based verification
- Result caching
- Multi-browser support (Chrome, Firefox)

## Related Components

- [C2PA Extension](../c2pa_extension_c2pa/) - Standard C2PA verification
- [JPEG Trust Orchestrator](../jpeg_trust_orchestrator/) - Backend server
- [Main Project](../README.md) - Complete documentation

## Support & Issues

- **Bug Reports**: [GitHub Issues](https://github.com/crugiente35/TFM-Provenance-applications/issues)
- **Documentation**: [Main README](../README.md)
- **Backend Issues**: [JPEG Trust Docs](../jpeg_trust_orchestrator/README.md)
- **Server Issues**: [Spring Boot Logs](../jpeg_trust_orchestrator/)

## License

MIT License - See LICENSE file in project root

---

**Powered by**:
- Chrome/Firefox Manifest V3
- JPEG Trust Framework
- Spring Boot Backend
- C2PA Standards

**Last Updated**: June 2026
