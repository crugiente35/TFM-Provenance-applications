/**
 * C2PA Image Inspector — content script
 * Muestra todos los metadatos de procedencia del servidor y bloquea PNGs.
 */

const API_BASE = "http://localhost:8085";

// ── Cache y Estado ────────────────────────────────────────────────────
const cache = new Map();
const pending = new Map();
let popup = null;
let currentImg = null;
let hideTimer = null;

function getPopup() {
  if (popup) return popup;
  const outer = document.createElement("div");
  outer.id = "c2pa-popup";
  outer.style.cssText = `
    position: fixed; pointer-events: none; z-index: 100000;
    background: #1a1a1a; color: #ffffff; border-radius: 10px;
    box-shadow: 0 8px 24px rgba(0,0,0,0.6); font-family: 'Inter', system-ui, sans-serif;
    width: 350px; transition: opacity 0.2s; opacity: 0; border: 1px solid #333;
    overflow: hidden; line-height: 1.4;
  `;
  outer.innerHTML = `<div id="c2pa-popup-inner"></div>`;
  document.body.appendChild(outer);
  popup = outer;
  return popup;
}

function showPopup(x, y, html) {
  clearTimeout(hideTimer);
  const p = getPopup();
  p.querySelector("#c2pa-popup-inner").innerHTML = html;
  
  const margin = 20;
  let left = x + margin;
  let top = y + margin;
  if (left + 350 > window.innerWidth) left = x - 350 - margin;
  // Ajuste de altura dinámica (estimada)
  if (top + 280 > window.innerHeight) top = y - 280 - margin;
  
  p.style.left = left + "px";
  p.style.top = top + "px";
  p.style.opacity = "1";
}

function hidePopup() {
  hideTimer = setTimeout(() => {
    if (popup) popup.style.opacity = "0";
  }, 150);
}

// ── Renderizado Exhaustivo ──────────────────────────────────────────

function renderPopupHTML(data, filename) {
  // Estado de carga
  if (data === "loading") {
    return `<div style="padding: 20px; text-align: center; color: #888;">
              <div class="spinner" style="margin-bottom:10px;">🔍</div>
              <div style="font-size: 13px;">Analizando procedencia...</div>
            </div>`;
  }

  // Mensaje específico para PNG (o errores de formato)
  if (data.isPng) {
    return `
      <div style="background: #442222; padding: 12px; border-bottom: 1px solid #663333;">
        <b style="color: #ff8888;">⚠️ Formato no soportado</b>
      </div>
      <div style="padding: 12px; font-size: 13px;">
        El inspector C2PA actual no puede leer archivos <b>PNG</b>. 
        Por favor, utiliza imágenes en formato <b>JPEG</b> con metadatos JUMBF.
      </div>`;
  }

  // Sin datos
  if (!data || data.length === 0 || data.error) {
    return `<div style="padding: 15px; color: #aaa;">
              <div style="font-weight: bold; margin-bottom: 5px;">Sin información C2PA</div>
              <div style="font-size: 12px;">No se han detectado manifiestos de confianza en esta imagen.</div>
            </div>`;
  }

  // Mapeo de datos del primer nodo (el más reciente en la cadena)
  const n = data[0]; // Datos de ProvenanceInspectionService
  
  const isAI = n.isAI;
  const isValid = n.isValid;
  const accent = isAI ? "#00d4ff" : (isValid ? "#4caf50" : "#ff5252");
  
  return `
    <div style="background: ${accent}22; border-left: 4px solid ${accent}; padding: 12px;">
      <div style="font-size: 10px; font-weight: 900; letter-spacing: 1px; color: ${accent}; margin-bottom: 4px;">
        ${isAI ? 'SYNTHETIC MEDIA' : (isValid ? 'VERIFIED CONTENT' : 'INVALID SIGNATURE')}
      </div>
      <div style="font-size: 16px; font-weight: bold;">${escHtml(n.softwareAgent)}</div>
    </div>

    <div style="padding: 12px; font-size: 12px;">
      ${n.aiDisclosure !== "N/A" ? `
        <div style="margin-bottom: 10px;">
          <b style="color: ${accent};">🤖 Detalle de IA:</b><br>
          <span style="color: #eee;">${escHtml(n.aiDisclosure)}</span>
        </div>` : ""}

      ${n.prompt !== "N/A" ? `
        <div style="margin-bottom: 10px; background: #252525; padding: 8px; border-radius: 6px; border: 1px solid #333;">
          <b style="color: #888; font-size: 10px;">PROMPT</b><br>
          <span style="font-style: italic; color: #ddd;">"${escHtml(n.prompt)}"</span>
        </div>` : ""}

      <div style="margin-bottom: 6px;">
        <b style="color: #888;">Origen Digital:</b> 
        <span style="font-family: monospace; font-size: 11px; color: #aaa;">${escHtml(n.digitalSourceType)}</span>
      </div>

      <div style="margin-bottom: 6px;">
        <b style="color: #888;">Acciones:</b> 
        <span style="color: #bbb;">${escHtml(n.actions || "None")}</span>
      </div>

      <div style="margin-top: 12px; padding-top: 8px; border-top: 1px solid #333; font-size: 10px; color: #555; display: flex; justify-content: space-between;">
        <span>VALIDACIÓN: ${isValid ? "OK" : "FAIL"}</span>
        <span>ID: ${escHtml(n.id).substring(0, 20)}...</span>
      </div>
    </div>
  `;
}

function escHtml(s) {
  return String(s || "").replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}

// ── Lógica de Inspección y Filtro PNG ────────────────────────────────

async function fetchC2PA(imgSrc) {
  // Comprobar si es PNG antes de hacer nada
  if (imgSrc.toLowerCase().includes(".png") || imgSrc.startsWith("data:image/png")) {
    return { isPng: true };
  }

  if (cache.has(imgSrc)) return cache.get(imgSrc);
  if (pending.has(imgSrc)) return pending.get(imgSrc);

  const promise = (async () => {
    try {
      const response = await fetch(imgSrc);
      if (!response.ok) throw new Error();
      
      const blob = await response.blob();
      
      // Segunda comprobación por tipo de MIME
      if (blob.type === "image/png") return { isPng: true };

      const fd = new FormData();
      fd.append("file", blob, "image.jpg");

      const res = await fetch(`${API_BASE}/api/inspect`, { method: "POST", body: fd });
      if (!res.ok) throw new Error();
      
      const data = await res.json();
      const nodes = data.nodes || [];
      cache.set(imgSrc, nodes);
      return nodes;
    } catch (e) {
      return { error: true };
    } finally {
      pending.delete(imgSrc);
    }
  })();

  pending.set(imgSrc, promise);
  return promise;
}

// ── Intercepción ─────────────────────────────────────────────────────

function attachToImg(img) {
  if (img._c2paAttached || img.naturalWidth < 40) return;
  img._c2paAttached = true;

  img.addEventListener("mouseenter", async (e) => {
    currentImg = img;
    const filename = img.src.split("/").pop().split("?")[0] || "image.jpg";

    if (cache.has(img.src)) {
      showPopup(e.clientX, e.clientY, renderPopupHTML(cache.get(img.src), filename));
      return;
    }

    showPopup(e.clientX, e.clientY, renderPopupHTML("loading", filename));
    const result = await fetchC2PA(img.src);
    if (currentImg === img) {
      showPopup(e.clientX, e.clientY, renderPopupHTML(result, filename));
    }
  });

  img.addEventListener("mouseleave", () => {
    currentImg = null;
    hidePopup();
  });
}

const scan = () => document.querySelectorAll("img").forEach(attachToImg);
const observer = new MutationObserver(scan);
observer.observe(document.body, { childList: true, subtree: true });
scan();
