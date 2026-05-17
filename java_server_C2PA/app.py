from flask import Flask, request, jsonify, render_template, send_file
from flask_cors import CORS
import os
import json
import requests as http_requests
import io
import urllib.request
from datetime import datetime
import c2pa
import traceback

app = Flask(__name__)
CORS(app)

app.config['MAX_CONTENT_LENGTH'] = 256 * 1024 * 1024 

TRUST_ANCHORS_URL = "https://contentcredentials.org/trust/anchors.pem"


C2PA_ERROR_MAP = {
    "assertion.dataHash.mismatch": (
        "El hash de los datos visuales no coincide con el firmado en el manifiesto. "
        "Los píxeles o la estructura del archivo han sido alterados (recortes, filtros, re-encoding) "
        "después de la inyección de la credencial de procedencia."
    ),
    "claim.signature.missing": (
        "Falta la firma digital del manifiesto (claim). No se puede validar criptográficamente "
        "la integridad de los datos ni verificar la procedencia de la imagen."
    ),
    "format.jumbf.invalid": (
        "La estructura binaria del contenedor JUMBF no es válida o no cumple con el estándar ISO/IEC 19566-5. "
        "Es probable que el archivo esté corrupto a nivel de bytes."
    ),
    "manifest.inaccessible": (
        "No se pudo acceder a los datos del manifiesto, posiblemente debido a referencias URI internas rotas."
    ),
    "ingredient.missing": (
        "El manifiesto hace referencia a un ingrediente (una imagen base u otro recurso de procedencia) "
        "que no se pudo cargar correctamente."
    )
}

def enrich_validation_errors(c2pa_json: dict) -> dict:
    """
    Escanea TODO el árbol JSON de forma recursiva buscando errores de validación
    en cualquier nivel de profundidad para enriquecer su explicación.
    """
    def _traverse_and_enrich(data):
        if isinstance(data, dict):
            # Si detectamos la firma visual de un error C2PA
            if "code" in data and "explanation" in data:
                code = data.get("code")
                original_exp = data.get("explanation", "")
                
                if code == "assertion.required.missing":
                    url = data.get("url", "")
                    missing_part = url.split("/")[-1] if "/" in url else "desconocida"
                    
                    if "urn:c2pa:" in missing_part or "urn:uuid:" in missing_part:
                        msg = (
                            f"El contenedor del manifiesto principal ({missing_part}) está ausente o corrupto. "
                            "No se puede leer ninguna aserción dentro de él."
                        )
                    else:
                        msg = f"Falta el bloque específico requerido: '{missing_part}'."
                    
                    # Reescribimos directamente la explicación
                    data["explanation"] = f"{original_exp} | Análisis: {msg} Esto ocurre si la imagen pasó por un pipeline que destruyó sus metadatos."
                
                elif code in C2PA_ERROR_MAP:
                    data["explanation"] = f"{original_exp} | Análisis: {C2PA_ERROR_MAP[code]}"
            
            # Continuar buscando en los valores del diccionario
            for key, value in data.items():
                _traverse_and_enrich(value)
                
        elif isinstance(data, list):
            # Continuar buscando dentro de las listas
            for item in data:
                _traverse_and_enrich(item)

    # Iniciamos la recursión modificando el JSON original (in-place)
    _traverse_and_enrich(c2pa_json)
    
    return c2pa_json


def load_trust_anchors():
    """Carga los trust anchors oficiales de Content Credentials."""
    try:
        with urllib.request.urlopen(TRUST_ANCHORS_URL, timeout=10) as response:
            anchors = response.read().decode('utf-8')
        return c2pa.Settings.from_dict({
            "verify": {"verify_cert_anchors": True},
            "trust": {"trust_anchors": anchors}
        })
    except Exception as e:
        app.logger.warning(f"No se pudieron cargar los trust anchors: {e}")
        return None


def read_c2pa_from_bytes(file_bytes: bytes, mime_type: str = "image/jpeg") -> dict:
    """
    Lee y valida los datos C2PA de los bytes de un archivo (imagen o vídeo).
    Devuelve un dict estructurado con todas las secciones del manifiesto.
    """
    stream = io.BytesIO(file_bytes)
    settings = load_trust_anchors()

    try:
        with c2pa.Context(settings) as context:
            # c2pa-python procesará el stream correctamente si el mime_type es de video o imagen
            with c2pa.Reader(mime_type, stream, context=context) as reader:
                json_string = reader.detailed_json()
                c2pa_dict = json.loads(json_string)
                
                # Enriquecemos los errores antes de devolver el diccionario
                return enrich_validation_errors(c2pa_dict)
                
    except c2pa.C2paError as e:
        return {
            "has_c2pa": False,
            "error": str(e),
        }


def get_mime_type(filename: str, content_type: str = "") -> str:
    """Infiere el MIME type a partir del nombre de archivo o Content-Type."""
    ext = os.path.splitext(filename.lower())[1]
    ext_map = {
        ".jpg": "image/jpeg", ".jpeg": "image/jpeg",
        ".png": "image/png",  ".webp": "image/webp",
        ".tif": "image/tiff", ".tiff": "image/tiff",
        ".avif": "image/avif", ".heic": "image/heic",
        ".gif": "image/gif",
        ".mp4": "video/mp4", ".mov": "video/quicktime",
        ".pdf": "application/pdf",
        ".m4v": "video/x-m4v",
    }
    if ext in ext_map:
        return ext_map[ext]
    if content_type and "/" in content_type:
        return content_type.split(";")[0].strip()
    return "application/octet-stream"


def process_file_data(file_bytes: bytes, filename: str, mime_type: str = "") -> dict:
    """Procesa el archivo y devuelve el JSON completo con datos C2PA."""
    mime = mime_type or get_mime_type(filename)
    c2pa_data = read_c2pa_from_bytes(file_bytes, mime)

    return {
        "processed_at": datetime.utcnow().isoformat() + "Z",
        "filename":     filename,
        "mime_type":    mime,
        "size_bytes":   len(file_bytes),
        "c2pa":         c2pa_data,
    }


def load_file_from_request(req):
    """Devuelve (bytes, filename, mime_type) desde el request."""
    # Buscamos claves comunes para archivos multimedia
    for field_name in ["file", "video", "image"]:
        if field_name in req.files:
            f = req.files[field_name]
            return f.read(), f.filename, f.content_type or ""
            
    url = None
    if req.is_json:
        url = req.json.get("url")
    else:
        url = req.form.get("url")

    if not url:
        raise ValueError("No se encontró ningún archivo ni campo 'url' en la petición.")

    resp = http_requests.get(url, timeout=15)
    resp.raise_for_status()
    filename = url.split("/")[-1].split("?")[0] or "file_from_url"
    content_type = resp.headers.get("Content-Type", "")
    return resp.content, filename, content_type


# ──────────────────────────────────────────────
# RUTAS WEB
# ──────────────────────────────────────────────

@app.route("/")
def index():
    return render_template("index.html")

@app.route("/test")
def test():
    return render_template("test.html")


@app.route("/process", methods=["POST"])
def process_web():
    """Endpoint para la interfaz web: devuelve JSON completo."""
    try:
        file_bytes, filename, mime = load_file_from_request(request)
        result = process_file_data(file_bytes, filename, mime)
        return jsonify(result)
    except Exception as e:
        app.logger.error("ERROR DETALLADO:\n%s", traceback.format_exc())
        return jsonify({
            "error": str(e),
            "type": type(e).__name__,
            "trace": traceback.format_exc()
        }), 500


# ──────────────────────────────────────────────
# ENDPOINTS API
# ──────────────────────────────────────────────

def _get_full(req) -> dict:
    file_bytes, filename, mime = load_file_from_request(req)
    return process_file_data(file_bytes, filename, mime)


@app.route("/api/full", methods=["POST"])
def api_full():
    """JSON completo con todos los datos C2PA."""
    try:
        return jsonify(_get_full(request))
    except Exception as e:
        return jsonify({"error": str(e)}), 400


@app.route("/api/summary", methods=["POST"])
def api_summary():
    """Resumen rápido: tiene C2PA, es válido, estado de validación."""
    try:
        data = _get_full(request)
        c = data.get("c2pa", {})
        return jsonify({
            "filename":         data.get("filename"),
            "mime_type":        data.get("mime_type"),
            "size_bytes":       data.get("size_bytes"),
            "has_c2pa":         c.get("has_c2pa", False),
            "is_valid":         c.get("is_valid"),
            "is_embedded":      c.get("is_embedded"),
            "remote_url":       c.get("remote_url"),
            "validation_state": c.get("validation_state"),
        })
    except Exception as e:
        return jsonify({"error": str(e)}), 400


@app.route("/api/validation", methods=["POST"])
def api_validation():
    """Resultado detallado de validación del manifiesto C2PA."""
    try:
        data = _get_full(request)
        c = data.get("c2pa", {})
        
        # Obtenemos validation_status (estándar) o validation_results por compatibilidad
        val_results = c.get("validation_status") or c.get("validation_results") or []
        
        return jsonify({
            "is_valid":           c.get("is_valid"),
            "validation_state":   c.get("validation_state"),
            "validation_results": val_results, 
        })
    except Exception as e:
        return jsonify({"error": str(e)}), 400


@app.route("/api/active-manifest", methods=["POST"])
def api_active_manifest():
    """Manifiesto activo (claims, aserciones, firma, etc.)."""
    try:
        data = _get_full(request)
        c = data.get("c2pa", {})
        if not c.get("has_c2pa"):
            return jsonify({"error": "El archivo no contiene datos C2PA.", "detail": c.get("error")}), 404
        return jsonify(c.get("active_manifest") or {})
    except Exception as e:
        return jsonify({"error": str(e)}), 400


@app.route("/api/manifests", methods=["POST"])
def api_manifests():
    """Todos los manifiestos del store (JSON estándar c2pa)."""
    try:
        data = _get_full(request)
        c = data.get("c2pa", {})
        if not c.get("has_c2pa"):
            return jsonify({"error": "El archivo no contiene datos C2PA.", "detail": c.get("error")}), 404
        return jsonify(c.get("manifests_json") or {})
    except Exception as e:
        return jsonify({"error": str(e)}), 400


@app.route("/api/manifests/detailed", methods=["POST"])
def api_manifests_detailed():
    """Manifiestos en formato detallado (detailed_json)."""
    try:
        data = _get_full(request)
        c = data.get("c2pa", {})
        if not c.get("has_c2pa"):
            return jsonify({"error": "El archivo no contiene datos C2PA.", "detail": c.get("error")}), 404
        return jsonify(c.get("manifests_detailed") or {})
    except Exception as e:
        return jsonify({"error": str(e)}), 400


# ──────────────────────────────────────────────
# DESCARGA JSON
# ──────────────────────────────────────────────

@app.route("/download", methods=["POST"])
def download_json():
    """Procesa el archivo y devuelve el JSON como archivo descargable."""
    try:
        file_bytes, filename, mime = load_file_from_request(request)
        result = process_file_data(file_bytes, filename, mime)
        json_bytes = json.dumps(result, indent=2, ensure_ascii=False).encode("utf-8")
        buf = io.BytesIO(json_bytes)
        buf.seek(0)
        stem = os.path.splitext(filename)[0]
        return send_file(buf, mimetype="application/json",
                         as_attachment=True,
                         download_name=f"{stem}_c2pa.json")
    except Exception as e:
        return jsonify({"error": str(e)}), 400


if __name__ == "__main__":
    app.run(debug=True, host="0.0.0.0", port=5000)