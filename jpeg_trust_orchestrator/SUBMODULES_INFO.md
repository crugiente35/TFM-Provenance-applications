# JPEG Trust Orchestrator - Estructura de Submodules

## Descripción

Este proyecto utiliza **Git Submodules** para gestionar dependencias personalizadas:

### 1. **mipams-jpeg-trust** (Personalizado)
- **Repository**: https://github.com/crugiente35/mipams-jpeg-trust
- **Rama**: `custom-modifications`
- **Estado**: ✅ Con modificaciones personalizadas
- **Descripción**: Contiene cambios específicos del proyecto en:
  - `src/main/java/org/mipams/jpegtrust/entities/assertions/`
  - `src/main/java/org/mipams/jpegtrust/services/validation/discovery/`
  - Tests personalizados

### 2. **mipams-jpeg-systems** (Original)
- **Repository**: https://github.com/nickft/mipams-jpeg-systems
- **Rama**: `main`
- **Estado**: ✅ Sin modificaciones (original)
- **Descripción**: Dependencia auxiliar para operaciones JPEG

## Estructura de Directorios

```
TFM-Provenance-applications/
│
├── c2pa_extension_c2pa/           # Extensión original 1
├── c2pa_extension_jpegTrust/      # Extensión original 2
├── java_server_C2PA/               # Servidor original
│
└── jpeg_trust_orchestrator/        # 🆕 NUEVO SERVIDOR (Esta carpeta)
    ├── src/                        # Código fuente principal
    ├── info/                       # Documentación y especificaciones
    ├── mipams-jpeg-trust/          # Submodule: Versión personalizada
    ├── mipams-jpeg-systems/        # Submodule: Versión original
    ├── pom.xml                     # Configuración Maven
    ├── mvnw / mvnw.cmd             # Maven wrapper
    ├── run.sh                      # Script para ejecutar
    ├── README.md                   # Documentación
    ├── LICENSE                     # Licencias
    ├── .gitignore                  # Configuración de git
    └── SUBMODULES_INFO.md          # Este archivo
```

## Licencias

| Componente | Licencia | Repositorio |
|-----------|----------|------------|
| **jpeg_trust_orchestrator** | MIT License | Este proyecto |
| **mipams-jpeg-trust** | BSD 3-Clause License | https://github.com/crugiente35/mipams-jpeg-trust |
| **mipams-jpeg-systems** | BSD 3-Clause License | https://github.com/nickft/mipams-jpeg-systems |
| **Spring Boot** | Apache License 2.0 | https://spring.io/ |

## Cómo Clonar Este Proyecto

```bash
# Clonar CON submodules
git clone --recurse-submodules https://github.com/crugiente35/TFM-Provenance-applications.git

# O si ya lo clonaste sin submodules:
cd TFM-Provenance-applications
git submodule update --init --recursive
```

## Actualizar Submodules

```bash
# Traer los últimos cambios de los submodules
git submodule update --remote

# O manualmente:
cd jpeg_trust_orchestrator/mipams-jpeg-trust
git pull origin custom-modifications
```

## Hacer Cambios en mipams-jpeg-trust

Si necesitas modificar `mipams-jpeg-trust`:

```bash
cd jpeg_trust_orchestrator/mipams-jpeg-trust

# Crear/cambiar rama (no modificar main)
git checkout -b feature/nueva-funcionalidad

# Hacer cambios...
git add .
git commit -m "Descripción de cambios"
git push origin feature/nueva-funcionalidad

# Crear Pull Request en GitHub para revisar

# Una vez aprobado, actualizar main en el repo principal:
cd ../..
git add jpeg_trust_orchestrator/mipams-jpeg-trust
git commit -m "Actualizar submodule a nuevos cambios"
git push
```

## Cambios Realizados en mipams-jpeg-trust

Ver rama `custom-modifications`:
https://github.com/crugiente35/mipams-jpeg-trust/tree/custom-modifications

**Archivos modificados:**
```
src/main/java/org/mipams/jpegtrust/entities/assertions/Assertion.java
src/main/java/org/mipams/jpegtrust/entities/assertions/AssetType.java
src/main/java/org/mipams/jpegtrust/entities/assertions/ThumbnailAssertion.java
src/main/java/org/mipams/jpegtrust/entities/assertions/actions/ActionAssertion.java
src/main/java/org/mipams/jpegtrust/entities/assertions/actions/ActionsAssertion.java
src/main/java/org/mipams/jpegtrust/entities/assertions/enums/AssetTypeChoice.java
src/main/java/org/mipams/jpegtrust/entities/assertions/ingredients/IngredientAssertion.java
src/main/java/org/mipams/jpegtrust/services/validation/discovery/AssertionDiscovery.java
src/test/java/org/mipams/jpegtrust/v2/claimgenerator/ManifestScenarios.java
src/test/java/org/mipams/jpegtrust/v2/claimgenerator/standard_manifest/RedactedAssertionTest.java
```

## Troubleshooting

### Los submodules están vacíos
```bash
git submodule update --init --recursive
```

### Cambios perdidos en un submodule
```bash
# Ver dónde está el HEAD del submodule
cd jpeg_trust_orchestrator/mipams-jpeg-trust
git log --oneline -5
```

### Actualizar el fork desde el original
```bash
cd jpeg_trust_orchestrator/mipams-jpeg-trust

# Agregar upstream (si no lo tiene)
git remote add upstream https://github.com/nickft/mipams-jpeg-trust.git

# Traer cambios del original
git fetch upstream main

# Actualizar rama custom-modifications
git rebase upstream/main custom-modifications
git push origin custom-modifications --force
```

## Notas Importantes

⚠️ **No edites directamente en mipams-jpeg-systems** - Es el original, solo depende de él

✅ **Todos los cambios personalizados** van en mipams-jpeg-trust rama `custom-modifications`

✅ **Mantén las licencias** - No remuevas los archivos LICENSE de los submodules

---

**Última actualización**: 2026-05-17
