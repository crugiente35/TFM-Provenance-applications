# JPEG Trust Orchestrator

Spring Boot application for managing and orchestrating JPEG Trust operations with C2PA (Content Credentials) support.

## Overview

This is a REST API server built with Spring Boot that provides endpoints for:
- JPEG image processing with trust validation
- C2PA manifest generation and management
- Provenance tracking
- Media format detection and processing (MP3, MP4, etc.)

## Project Structure

```
jpeg_trust_orchestrator/
├── src/                              # Application source code
│   ├── main/java/io/carranza/        # Main application code
│   └── test/java/io/carranza/        # Test files
├── info/                             # Documentation and specifications
├── mipams-jpeg-trust/                # Git submodule (with custom modifications)
├── mipams-jpeg-systems/              # Git submodule (original)
├── pom.xml                           # Maven configuration
├── mvnw / mvnw.cmd                   # Maven wrapper scripts
├── run.sh                            # Shell script to run the server
└── test.json                         # Test configuration
```

## Dependencies

### Modified Submodules

- **mipams-jpeg-trust** - Custom fork with modifications
  - Repository: https://github.com/crugiente35/mipams-jpeg-trust
  - Branch: `custom-modifications` (contains your custom changes)
  - See branch for detailed list of modifications

- **mipams-jpeg-systems** - Original (unmodified)
  - Repository: https://github.com/nickft/mipams-jpeg-systems
  - Version: As configured in pom.xml

## Building the Project

### Prerequisites
- Java 21+
- Maven 3.8.0+

### Build

```bash
# Clone the repository with submodules
git clone --recurse-submodules https://github.com/crugiente35/TFM-Provenance-applications.git
cd TFM-Provenance-applications/jpeg_trust_orchestrator

# Build with Maven
mvn clean install

# Run the application
mvn spring-boot:run
```

Or use the provided script:
```bash
./run.sh
```

## Configuration

- Java version: 21
- Spring Boot version: 4.0.5
- Main configuration file: `src/main/resources/application.properties`

## API Documentation

See `info/` directory for:
- API specifications
- C2PA technical documentation
- JPEG Trust specifications

## Usage Guide

### Running the Server

1. **Build the project**:
```bash
mvn clean install
```

2. **Run the application**:
```bash
mvn spring-boot:run
```

Or use the provided script:
```bash
# Linux/macOS
./run.sh

# Windows
mvnw.cmd spring-boot:run
```

3. **Access the API**:
- **Base URL**: `http://localhost:8085`
- **Health check**: `http://localhost:8085/actuator/health`
- **API documentation**: Check `info/api_doc_en.md`

### REST API Endpoints

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/api/analyze` | POST | Analyze JPEG for trust indicators |
| `/api/validate` | POST | Validate C2PA credentials |
| `/api/detect-ai` | POST | Detect AI-generation |
| `/health` | GET | Server health status |

**Request Format:**
```javascript
const formData = new FormData();
formData.append('url', 'https://example.com/image.jpg');
// or: formData.append('image', imageFile);

fetch('http://localhost:8085/api/analyze', {
  method: 'POST',
  body: formData
})
.then(r => r.json())
.then(data => console.log(data));
```

**Example Response:**
```json
{
  "trust_level": "high",
  "trust_score": 95,
  "ai_generated": false,
  "ai_confidence": 0.02,
  "jpeg_trust_valid": true,
  "c2pa_present": true,
  "processing_timestamp": "2024-01-15T10:30:00Z"
}
```

## Architecture

### Core Components

1. **Spring Boot Application** - REST API server
2. **JPEG Trust Module** - JPEG image analysis
3. **C2PA Integration** - Credential processing
4. **Submodules** - External libraries:
   - `mipams-jpeg-trust` - JPEG Trust implementation
   - `mipams-jpeg-systems` - System utilities

### Request Flow

```
HTTP Request (image/URL)
    ↓
Spring Boot Router
    ↓
JPEG Trust Analyzer
    ├─ Extract metadata
    ├─ Check trust indicators
    ├─ Detect AI-generation
    └─ Validate credentials
    ↓
C2PA Processor
    ├─ Extract manifest
    ├─ Verify signatures
    └─ Display history
    ↓
JSON Response
```

## Configuration

### Application Properties

Edit `src/main/resources/application.properties`:

```properties
# Server port
server.port=8085

# Logging level
logging.level.root=INFO
logging.level.io.carranza=DEBUG

# File upload settings
spring.servlet.multipart.max-file-size=256MB
spring.servlet.multipart.max-request-size=256MB
```

### YAML Configuration

Alternative: `src/main/resources/application.yml`

```yaml
server:
  port: 8085

logging:
  level:
    root: INFO
    io.carranza: DEBUG

spring:
  servlet:
    multipart:
      max-file-size: 256MB
      max-request-size: 256MB
```

## Extending the Project

### Adding New Endpoints

1. **Create a new controller**:
```java
@RestController
@RequestMapping("/api")
public class MyController {
    @PostMapping("/my-endpoint")
    public ResponseEntity<Map<String, Object>> analyze(
        @RequestParam(name = "url", required = false) String url,
        @RequestParam(name = "image", required = false) MultipartFile file
    ) {
        // Your implementation
        return ResponseEntity.ok(result);
    }
}
```

2. **Add service logic**:
```java
@Service
public class MyService {
    public Map<String, Object> analyzeImage(byte[] imageData) {
        // Analysis logic
        return result;
    }
}
```

3. **Test the endpoint**:
```bash
curl -X POST http://localhost:8085/api/my-endpoint \
  -F "image=@photo.jpg"
```

### Integrating with JPEG Trust

```java
// Use mipams-jpeg-trust library
import org.mipams.jumbf.util.MipamsLogger;
import org.mipams.jpeg_trust.*;

public class JpegTrustHelper {
    public JPEGTrustResult analyzeImage(byte[] imageData) {
        // Extract JPEG Trust indicators
        return JpegTrustAnalyzer.analyze(imageData);
    }
}
```

## Submodule Management

### Cloning with Submodules

```bash
git clone --recurse-submodules https://github.com/crugiente35/TFM-Provenance-applications.git
```

### Updating Submodules

```bash
# Update all submodules
git submodule update --remote

# Update specific submodule
git submodule update --remote jpeg_trust_orchestrator/mipams-jpeg-trust
```

### Making Changes to Submodules

```bash
# Navigate to submodule
cd jpeg_trust_orchestrator/mipams-jpeg-trust

# Make your changes
git add .
git commit -m "Your changes"
git push origin custom-modifications

# Go back to main repo
cd ../..

# Update submodule reference
git add jpeg_trust_orchestrator/mipams-jpeg-trust
git commit -m "Update mipams-jpeg-trust reference"
git push
```

## Troubleshooting

### Build Failures

**Problem**: "Cannot find module" for submodules

**Solution**:
```bash
# Initialize submodules
git submodule update --init --recursive

# Rebuild
mvn clean install
```

**Problem**: Java version mismatch

**Solution**:
```bash
# Check Java version
java -version

# Must be Java 21 or higher
# Update if needed and try again
mvn clean install
```

### Runtime Errors

**Problem**: "Port 8085 already in use"

**Solution**:
```bash
# Find process on port 8085
# Windows:
netstat -ano | findstr :8085

# macOS/Linux:
lsof -i :8085

# Kill and restart
```

**Problem**: "Cannot load C2PA trust anchors"

**Solution**:
- Check internet connection
- Verify URL: https://contentcredentials.org/trust/anchors.pem
- Manually download and cache if needed

### Memory Issues

**Problem**: Out of memory processing large images

**Solution**:
```bash
# Increase heap size
export JAVA_OPTS="-Xmx2048m"
mvn spring-boot:run
```

## Testing

### Unit Tests

```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=JpegTrustTest

# Run with coverage
mvn test jacoco:report
```

### Integration Tests

```bash
# Start server
mvn spring-boot:run

# In another terminal, test endpoints
curl -X POST http://localhost:8085/api/analyze \
  -F "image=@sample.jpg"
```

### Manual Testing with cURL

```bash
# Analyze local file
curl -X POST http://localhost:8085/api/analyze \
  -F "image=@photo.jpg" | python -m json.tool

# Analyze from URL
curl -X POST http://localhost:8085/api/analyze \
  -F "url=https://example.com/image.jpg"

# Check server health
curl http://localhost:8085/actuator/health
```

## Performance Considerations

### Optimization Tips

1. **Image Caching**: Cache analysis results
2. **Parallel Processing**: Use thread pools for multiple images
3. **Async Responses**: Use Spring WebFlux for I/O operations
4. **Database**: Consider caching results in database

### Load Testing

```bash
# Using Apache Bench
ab -n 100 -c 10 http://localhost:8085/api/analyze

# Using wrk
wrk -t4 -c100 -d30s http://localhost:8085/api/analyze
```

## Production Deployment

### Docker Deployment

```dockerfile
FROM openjdk:21-jdk-slim
WORKDIR /app
COPY . .
RUN mvn clean install -DskipTests
EXPOSE 8085
CMD ["java", "-jar", "target/jpeg_trust_orchestrator-1.0.0.jar"]
```

### Build and Run

```bash
# Build Docker image
docker build -t jpeg-trust-orchestrator .

# Run container
docker run -p 8085:8085 jpeg-trust-orchestrator
```

### Environment Variables

```bash
# Java options
JAVA_OPTS="-Xmx2048m -Xms512m"

# Server port
SERVER_PORT=8085

# Logging
LOGGING_LEVEL=INFO
```

## Security Best Practices

1. **Input Validation**: Validate all image inputs
2. **File Size Limits**: Enforce upload limits (256MB default)
3. **MIME Type**: Verify image format
4. **URL Validation**: Prevent SSRF attacks
5. **Rate Limiting**: Prevent abuse
6. **HTTPS**: Use TLS in production
7. **Authentication**: Add if needed for API

## License

This project is licensed under the **MIT License**.

### Third-party Dependencies

- **mipams-jpeg-trust** - BSD 3-Clause License
- **mipams-jpeg-systems** - BSD 3-Clause License
- **Spring Boot** - Apache License 2.0
- **BouncyCastle** - MIT License
- See LICENSE file for full details

## Contributing

When making modifications to mipams-jpeg-trust:
1. Make changes in the submodule
2. Push changes to the `custom-modifications` branch
3. Update the main repository submodule reference

## Support & Resources

- **Spring Boot Docs**: https://spring.io/projects/spring-boot
- **C2PA Specs**: https://c2pa.org/specifications/
- **JPEG Trust**: See `info/` directory
- **API Documentation**: See `info/api_doc_en.md`

## Author

Created as part of TFM (Trabajo Fin de Master) project.

## References

- [MIPAMS JPEG Trust Repository](https://github.com/nickft/mipams-jpeg-trust)
- [MIPAMS JPEG Systems Repository](https://github.com/nickft/mipams-jpeg-systems)
- [C2PA Specifications](https://c2pa.org/)
