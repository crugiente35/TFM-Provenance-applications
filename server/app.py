from flask import Flask, request, jsonify, render_template, send_file
from flask_cors import CORS
import os
import json
import requests as http_requests
import tempfile
import io
import urllib.request
from datetime import datetime
import c2pa
import json

app = Flask(__name__)
CORS(app)

app.config['MAX_CONTENT_LENGTH'] = 64 * 1024 * 1024  # 64 MB

TRUST_ANCHORS_URL = "https://contentcredentials.org/trust/anchors.pem"


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


def read_c2pa_from_bytes(img_bytes: bytes, mime_type: str = "image/jpeg") -> dict:
    """
    Lee y valida los datos C2PA de los bytes de una imagen.
    Devuelve un dict estructurado con todas las secciones del manifiesto.
    """
    stream = io.BytesIO(img_bytes)
    settings = load_trust_anchors()

    try:
        with c2pa.Context(settings) as context:
            with c2pa.Reader(mime_type, stream, context=context) as reader:
                json_string = reader.detailed_json()
                return json.loads(json_string)
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
    }
    if ext in ext_map:
        return ext_map[ext]
    if content_type and "/" in content_type:
        return content_type.split(";")[0].strip()
    return "image/jpeg"


def process_image(img_bytes: bytes, filename: str, mime_type: str = "") -> dict:
    """Procesa el archivo y devuelve el JSON completo con datos C2PA."""
    mime = mime_type or get_mime_type(filename)
    c2pa_data = read_c2pa_from_bytes(img_bytes, mime)

    return {
        "processed_at": datetime.utcnow().isoformat() + "Z",
        "filename":     filename,
        "mime_type":    mime,
        "size_bytes":   len(img_bytes),
        "c2pa":         c2pa_data,
    }


def load_image_from_request(req):
    """Devuelve (bytes, filename, mime_type) desde el request."""
    if "image" in req.files:
        f = req.files["image"]
        return f.read(), f.filename, f.content_type or ""
    else:
        url = None
        if req.is_json:
            url = req.json.get("url")
        else:
            url = req.form.get("url")

        if not url:
            raise ValueError("No se encontró imagen ni campo 'url' en la petición.")

        resp = http_requests.get(url, timeout=15)
        resp.raise_for_status()
        filename = url.split("/")[-1].split("?")[0] or "image_from_url"
        content_type = resp.headers.get("Content-Type", "")
        return resp.content, filename, content_type


# ──────────────────────────────────────────────
# RUTAS WEB
# ──────────────────────────────────────────────

@app.route("/")
def index():
    return render_template("index.html")

@app.route("/test_extension")
def test():
    return render_template("test.html")


@app.route("/process", methods=["POST"])
def process_web():
    """Endpoint para la interfaz web: devuelve JSON completo."""
    try:
        img_bytes, filename, mime = load_image_from_request(request)
        result = process_image(img_bytes, filename, mime)
        return jsonify(result)
    except Exception as e:
        return jsonify({"error": str(e)}), 400


# ──────────────────────────────────────────────
# ENDPOINTS API
# ──────────────────────────────────────────────

def _get_full(req) -> dict:
    img_bytes, filename, mime = load_image_from_request(req)
    return process_image(img_bytes, filename, mime)


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
        return jsonify({
            "is_valid":           c.get("is_valid"),
            "validation_state":   c.get("validation_state"),
            "validation_results": c.get("validation_results"),
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
            return jsonify({"error": "La imagen no contiene datos C2PA.", "detail": c.get("error")}), 404
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
            return jsonify({"error": "La imagen no contiene datos C2PA.", "detail": c.get("error")}), 404
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
            return jsonify({"error": "La imagen no contiene datos C2PA.", "detail": c.get("error")}), 404
        return jsonify(c.get("manifests_detailed") or {})
    except Exception as e:
        return jsonify({"error": str(e)}), 400


# ──────────────────────────────────────────────
# DESCARGA JSON
# ──────────────────────────────────────────────

@app.route("/download", methods=["POST"])
def download_json():
    """Procesa la imagen y devuelve el JSON como archivo descargable."""
    try:
        img_bytes, filename, mime = load_image_from_request(request)
        result = process_image(img_bytes, filename, mime)
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
