/**
 * C2PA Image Inspector — content script
 * Envía cada imagen al servidor local (localhost:5000) y muestra
 * un popup encima del ratón con los metadatos C2PA al hacer hover.
 */

const API_BASE = "http://localhost:5000";

// ── Cache para no repetir peticiones ──────────────────────────────────
// key: src URL  →  value: resultado ya procesado
const cache = new Map();
// key: src URL  →  value: Promise en curso (para deduplicar peticiones simultáneas)
const pending = new Map();

// ── Popup singleton ───────────────────────────────────────────────────
let popup = null;
let currentImg = null;
let hideTimer = null;

function getPopup() {
  if (popup) return popup;
  const outer = document.createElement("div");
  outer.id = "c2pa-popup";
  outer.innerHTML = `<div id="c2pa-popup-inner"></div>`;
  document.body.appendChild(outer);
  popup = outer;
  return popup;
}

function showPopup(x, y, html) {
  clearTimeout(hideTimer);
  const p = getPopup();
  p.querySelector("#c2pa-popup-inner").innerHTML = html;
  positionPopup(p, x, y);
  p.classList.add("visible");
}

function positionPopup(p, x, y) {
  const margin = 14;
  const pw = 340;
  const ph = 160; // estimate
  let left = x + margin;
  let top  = y + margin;
  const vw = window.innerWidth;
  const vh = window.innerHeight;
  if (left + pw > vw - margin) left = x - pw - margin;
  if (top  + ph > vh - margin) top  = y - ph - margin;
  if (left < margin) left = margin;
  if (top  < margin) top  = margin;
  p.style.left = left + "px";
  p.style.top  = top  + "px";
}

function hidePopup() {
  hideTimer = setTimeout(() => {
    if (popup) popup.classList.remove("visible");
  }, 120);
}

// ── Result → HTML ─────────────────────────────────────────────────────

function detectAI(data) {
  /**
   * Retorna { isAI, issuer, generator, actions, validationState, hasC2PA }
   * Analiza el manifiesto activo para extraer la información relevante.
   */
  const c2pa = data.c2pa || data;
  if (!c2pa || (!c2pa.has_c2pa && !c2pa.active_manifest)) {
    return { isAI: false, hasC2PA: false };
  }

  const activeKey   = c2pa.active_manifest;          // URN string
  const manifests   = c2pa.manifests_json?.manifests || c2pa.manifests || {};
  const activeM     = manifests[activeKey] || {};
  const sig         = activeM.signature || {};
  const assertStore = activeM.assertion_store || {};

  // Recoger todas las acciones de c2pa.actions.v2
  const actionsObj  = assertStore["c2pa.actions.v2"] || {};
  const actions     = actionsObj.actions || [];

  // digitalSourceType que indica IA generativa
  const AI_SOURCE_TYPES = [
    "trainedAlgorithmicMedia",
    "algorithmicMedia",
    "compositeWithTrainedAlgorithmicMedia",
  ];

  let isAI      = false;
  let generator = null;
  const actionLabels = [];

  for (const a of actions) {
    const dst = a.digitalSourceType || "";
    const isAIAction = AI_SOURCE_TYPES.some(t => dst.includes(t));
    if (isAIAction) isAI = true;

    // Nombre del software/modelo generador
    if (a.softwareAgent?.name) generator = a.softwareAgent.name;
    if (a.description?.toLowerCase().includes("generative ai")) isAI = true;

    actionLabels.push({ action: a.action, description: a.description || "" });
  }

  // claim_generator_info
  const claimGen   = activeM.claim?.claim_generator_info || {};
  const claimName  = claimGen.name || null;

  return {
    isAI,
    hasC2PA:         true,
    issuer:          sig.issuer        || null,
    commonName:      sig.common_name   || null,
    sigTime:         sig.time          || null,
    generator:       generator || claimName,
    actions:         actionLabels,
    validationState: c2pa.validation_state || "Unknown",
    failures:        c2pa.validation_results?.activeManifest?.failure || [],
  };
}

function renderPopupHTML(info, filename) {
  // Loading state
  if (info === "loading") {
    return `
      <div class="c2pa-header">
        <div class="c2pa-icon loading"><span class="c2pa-spinner"></span></div>
        <span class="c2pa-title loading">Analizando…</span>
      </div>
      <div class="c2pa-body">
        <div class="c2pa-row">
          <span class="c2pa-label">Archivo</span>
          <span class="c2pa-value muted">${escHtml(filename)}</span>
        </div>
      </div>`;
  }

  // Error state
  if (info.error) {
    return `
      <div class="c2pa-header">
        <div class="c2pa-icon none">?</div>
        <span class="c2pa-title none">Sin datos C2PA</span>
      </div>
      <div class="c2pa-body">
        <div class="c2pa-row">
          <span class="c2pa-label">Archivo</span>
          <span class="c2pa-value muted">${escHtml(filename)}</span>
        </div>
        <div class="c2pa-row">
          <span class="c2pa-label">Estado</span>
          <span class="c2pa-value muted">No se pudo analizar</span>
        </div>
      </div>`;
  }

  if (!info.hasC2PA) {
    return `
      <div class="c2pa-header">
        <div class="c2pa-icon none">—</div>
        <span class="c2pa-title none">Sin metadatos C2PA</span>
      </div>
      <div class="c2pa-body">
        <div class="c2pa-row">
          <span class="c2pa-label">Archivo</span>
          <span class="c2pa-value muted">${escHtml(filename)}</span>
        </div>
        <div class="c2pa-row">
          <span class="c2pa-label">Origen</span>
          <span class="c2pa-value muted">Desconocido — sin firma de contenido</span>
        </div>
      </div>`;
  }

  // Determine visual theme
  const stateClass = info.isAI
    ? "ai"
    : info.validationState === "Trusted" ? "trusted" : "valid";

  const stateIcon  = info.isAI ? "AI" : info.validationState === "Trusted" ? "✓" : "~";
  const stateTitle = info.isAI
    ? "Generada por IA"
    : info.validationState === "Trusted"
      ? "Contenido verificado"
      : "C2PA presente · " + info.validationState;

  // Validation badge
  let valClass  = "ok";
  let valLabel  = info.validationState || "Unknown";
  const hasFail = info.failures.length > 0;
  if (hasFail) { valClass = "warn"; valLabel += " (advertencias)"; }

  // Action tags
  const tagMap = {
    "c2pa.created":   "created",
    "c2pa.edited":    "edited",
    "c2pa.converted": "converted",
    "c2pa.opened":    "opened",
  };

  // Deduplicate action types
  const seenActions = new Set();
  const tags = info.actions
    .filter(a => { if (seenActions.has(a.action)) return false; seenActions.add(a.action); return true; })
    .map(a => {
      const cls   = tagMap[a.action] || "other";
      const label = a.action.replace("c2pa.", "");
      const desc  = a.description ? ` · ${a.description}` : "";
      return `<span class="c2pa-tag ${cls}">${escHtml(label)}${escHtml(desc)}</span>`;
    }).join("");

  const rows = [];

  if (info.generator) rows.push(row("Generador", info.generator, "highlight"));
  if (info.issuer)    rows.push(row("Emisor",    info.issuer));
  if (info.commonName && info.commonName !== info.issuer)
                      rows.push(row("Firmante",  info.commonName));
  if (info.sigTime)   rows.push(row("Firmado",   formatDate(info.sigTime), "muted"));
  rows.push(row("Validación", valLabel, valClass));

  return `
    <div class="c2pa-header">
      <div class="c2pa-icon ${stateClass}">${stateIcon}</div>
      <span class="c2pa-title ${stateClass}">${stateTitle}</span>
    </div>
    <div class="c2pa-body">${rows.join("")}</div>
    ${tags ? `<div class="c2pa-tags">${tags}</div>` : ""}`;
}

function row(label, value, cls = "") {
  return `<div class="c2pa-row">
    <span class="c2pa-label">${escHtml(label)}</span>
    <span class="c2pa-value ${cls}">${escHtml(String(value))}</span>
  </div>`;
}

function escHtml(s) {
  return String(s)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

function formatDate(iso) {
  try {
    return new Date(iso).toLocaleString("es-ES", {
      day: "2-digit", month: "short", year: "numeric",
      hour: "2-digit", minute: "2-digit",
    });
  } catch { return iso; }
}

// ── Fetch C2PA data from server ───────────────────────────────────────

async function fetchC2PA(imgSrc) {
  // Check cache
  if (cache.has(imgSrc)) return cache.get(imgSrc);

  // Check in-flight
  if (pending.has(imgSrc)) return pending.get(imgSrc);

  const promise = (async () => {
    try {
      const fd = new FormData();

      if (imgSrc.startsWith("data:")) {
        const blob = dataURItoBlob(imgSrc);
        fd.append("image", blob, "image.png");
      } else {
          // Convertir a URL absoluta antes de enviar
        const absoluteUrl = new URL(imgSrc, window.location.href).href;
        fd.append("url", absoluteUrl);
      }

      const res  = await fetch(`${API_BASE}/api/full`, { method: "POST", body: fd });
      const data = await res.json();
      const info = detectAI(data);
      cache.set(imgSrc, info);
      return info;
    } catch (e) {
      const info = { error: true, hasC2PA: false };
      cache.set(imgSrc, info);
      return info;
    } finally {
      pending.delete(imgSrc);
    }
  })();

  pending.set(imgSrc, promise);
  return promise;
}

function dataURItoBlob(dataURI) {
  const [header, data] = dataURI.split(",");
  const mime = header.match(/:(.*?);/)[1];
  const bin  = atob(data);
  const arr  = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) arr[i] = bin.charCodeAt(i);
  return new Blob([arr], { type: mime });
}

// ── Hover ring class helpers ──────────────────────────────────────────

const RING_CLASSES = ["c2pa-hover-ai","c2pa-hover-trusted","c2pa-hover-valid","c2pa-hover-none","c2pa-hover-loading"];

function setRing(img, cls) {
  img.classList.remove(...RING_CLASSES);
  if (cls) img.classList.add(cls);
}

function infoToRingClass(info) {
  if (!info || info === "loading") return "c2pa-hover-loading";
  if (info.error || !info.hasC2PA)  return "c2pa-hover-none";
  if (info.isAI)                    return "c2pa-hover-ai";
  if (info.validationState === "Trusted") return "c2pa-hover-trusted";
  return "c2pa-hover-valid";
}

// ── Image interception ────────────────────────────────────────────────

function isValidImg(img) {
  return img.naturalWidth > 32 && img.naturalHeight > 32 && img.src && !img.src.startsWith("about:");
}

function attachToImg(img) {
  if (img._c2paAttached) return;
  img._c2paAttached = true;

  img.addEventListener("mouseenter", async (e) => {
    if (!isValidImg(img)) return;
    clearTimeout(hideTimer);
    currentImg = img;

    const src      = img.src;
    const filename = src.split("/").pop().split("?")[0] || "imagen";

    // If already cached, show immediately
    if (cache.has(src)) {
      const info = cache.get(src);
      setRing(img, infoToRingClass(info));
      showPopup(e.clientX, e.clientY, renderPopupHTML(info, filename));
      return;
    }

    // Show loading state immediately
    setRing(img, "c2pa-hover-loading");
    showPopup(e.clientX, e.clientY, renderPopupHTML("loading", filename));

    // Fetch in background
    const info = await fetchC2PA(src);

    // Only update if mouse is still on this image
    if (currentImg === img) {
      setRing(img, infoToRingClass(info));
      showPopup(e.clientX, e.clientY, renderPopupHTML(info, filename));
    }
  });

  img.addEventListener("mousemove", (e) => {
    if (popup && popup.classList.contains("visible")) {
      positionPopup(popup, e.clientX, e.clientY);
    }
  });

  img.addEventListener("mouseleave", () => {
    currentImg = null;
    setRing(img, "");
    hidePopup();
  });
}

// ── Observe DOM for new images ────────────────────────────────────────

function scanImages() {
  document.querySelectorAll("img").forEach(attachToImg);
}

const observer = new MutationObserver(() => scanImages());
observer.observe(document.body, { childList: true, subtree: true });

// Initial scan
scanImages();

function createThemeToggle() {
  const btn = document.createElement("button");
  btn.id = "c2pa-theme-toggle";
  btn.innerHTML = "🌓";
  btn.title = "Alternar tema del Inspector C2PA";
  document.body.appendChild(btn);

  // Cargar preferencia guardada desde el almacenamiento local de Chrome
  if (typeof chrome !== "undefined" && chrome.storage) {
    chrome.storage.local.get(['c2pa_theme'], (res) => {
      if (res.c2pa_theme === 'light') {
        getPopup().classList.add("c2pa-light");
        btn.classList.add("light-active");
      }
    });
  }

  // Escuchar clicks para cambiar de tema
  btn.addEventListener("click", () => {
    const p = getPopup();
    p.classList.toggle("c2pa-light");
    btn.classList.toggle("light-active");

    const isLight = p.classList.contains("c2pa-light");
    
    // Guardar la preferencia
    if (typeof chrome !== "undefined" && chrome.storage) {
      chrome.storage.local.set({ c2pa_theme: isLight ? 'light' : 'dark' });
    }
  });
}

// Inicializar el botón
createThemeToggle();