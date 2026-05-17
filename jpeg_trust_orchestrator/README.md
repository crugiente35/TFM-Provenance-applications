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

## Author

Created as part of TFM (Trabajo Fin de Master) project.

## References

- [MIPAMS JPEG Trust Repository](https://github.com/nickft/mipams-jpeg-trust)
- [MIPAMS JPEG Systems Repository](https://github.com/nickft/mipams-jpeg-systems)
- [C2PA Specifications](https://c2pa.org/)
