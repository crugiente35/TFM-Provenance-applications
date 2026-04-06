# Image Metadata Analyzer — Servidor Flask

## Instalación

```bash
pip install -r requirements.txt
python app.py
```

El servidor arranca en http://localhost:5000

---

## Interfaz web

Abre http://localhost:5000 en el navegador.
- Sube una imagen o pega una URL
- Visualiza los metadatos como secciones desplegables
- Descarga el JSON con el botón "↓ Descargar JSON"

---

## API REST

Todos los endpoints aceptan POST con:
- `image` — archivo de imagen (multipart/form-data)
- `url`   — URL de la imagen (form field o JSON body)

| Endpoint      | Respuesta                              |
|---------------|----------------------------------------|
| POST /api/full   | JSON completo                       |
| POST /api/basic  | Dimensiones, formato, modo, megapíxeles |
| POST /api/exif   | Datos EXIF                          |
| POST /api/gps    | Coordenadas GPS (si existen)        |
| POST /api/color  | Espacio de color, brillo, color medio |
| POST /api/file   | Tamaño del archivo, MD5, SHA256     |
| POST /process    | JSON completo (usado por la web)    |
| POST /download   | Descarga directa del .json          |

### Ejemplos con curl

```bash
# Con archivo local
curl -X POST http://localhost:5000/api/basic -F "image=@foto.jpg"

# Con URL
curl -X POST http://localhost:5000/api/exif -F "url=https://upload.wikimedia.org/wikipedia/commons/thumb/4/47/PNG_transparency_demonstration_1.png/240px-PNG_transparency_demonstration_1.png"

# JSON completo
curl -X POST http://localhost:5000/api/full -F "image=@foto.jpg" | python -m json.tool
```

---

## Añadir tus propias funciones

Edita `app.py`. Cada función de procesamiento recibe un objeto `PIL.Image` y/o los bytes originales:

```python
def mi_funcion(img: Image.Image) -> dict:
    # tu lógica aquí
    return {"resultado": ...}
```

Añádela al procesador principal `process_image()`:

```python
def process_image(img_bytes, filename):
    img = Image.open(io.BytesIO(img_bytes))
    return {
        ...
        "mi_seccion": mi_funcion(img),
    }
```

Y crea su endpoint en la API:

```python
@app.route("/api/mi_seccion", methods=["POST"])
def api_mi_seccion():
    try:
        return jsonify(api_process(request)["mi_seccion"])
    except Exception as e:
        return jsonify({"error": str(e)}), 400
```

---

## Uso como complemento de navegador

Para llamar desde una extensión (Chrome/Firefox), usa fetch contra los endpoints `/api/*`:

```js
const fd = new FormData();
fd.append('url', imageUrl);
const res = await fetch('http://localhost:5000/api/full', { method: 'POST', body: fd });
const metadata = await res.json();
```

CORS está habilitado para desarrollo local.
