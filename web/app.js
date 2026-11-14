// Magazzino DS Lab — magazzino filamenti condiviso, sincronizzato con Firebase
import { initializeApp } from "https://www.gstatic.com/firebasejs/10.12.2/firebase-app.js";
import {
  getAuth, GoogleAuthProvider, signInWithPopup, signInWithRedirect, getRedirectResult, onAuthStateChanged, signOut,
} from "https://www.gstatic.com/firebasejs/10.12.2/firebase-auth.js";
import {
  initializeFirestore, persistentLocalCache, persistentMultipleTabManager, collection, onSnapshot,
  addDoc, updateDoc, deleteDoc, doc, serverTimestamp,
} from "https://www.gstatic.com/firebasejs/10.12.2/firebase-firestore.js";

// Configurazione pubblica del progetto: l'accesso ai dati e' protetto dalle regole di Firestore
const firebaseConfig = {
  apiKey: "AIzaSyDq63s8LZBT9ESfvR6-bvPaWqQBQbW6yeA",
  authDomain: "magazzino-ds-lab.firebaseapp.com",
  projectId: "magazzino-ds-lab",
  storageBucket: "magazzino-ds-lab.firebasestorage.app",
  messagingSenderId: "256512928793",
  appId: "1:256512928793:web:89b007d324394247a9ce88",
};

const app = initializeApp(firebaseConfig);
const auth = getAuth(app);
auth.languageCode = "it";
const db = initializeFirestore(app, { localCache: persistentLocalCache({ tabManager: persistentMultipleTabManager() }) });
const COL = collection(db, "filamenti");

const MARCHE = ["Bambu Lab", "Sunlu", "eSun", "Elegoo", "Polymaker", "Prusament", "Geeetech", "Jayo", "Kingroon", "Creality", "Anycubic"];
const TIPI = ["PLA Basic", "PLA Matte", "PLA Silk", "PLA Silk+", "PLA Tough", "PLA Translucent", "PLA Glow", "PLA Marble", "PLA Galaxy", "PLA-CF",
  "PETG Basic", "PETG HF", "PETG Translucent", "PETG-CF", "ABS", "ASA", "TPU 95A", "PA (Nylon)", "PC", "PVA", "Supporto"];
const PALETTE = ["#ffffff", "#f2efe6", "#1a1a1a", "#8a8d91", "#c9ccd1", "#d32f2f", "#ff8a3d", "#fbc02d", "#43a047", "#2e5e3a",
  "#1e63d6", "#4fc3f7", "#7e57c2", "#f06292", "#6d4c41", "#c9a227", "#bfc4c9", "#e0f2f1"];

const $ = (s) => document.querySelector(s);
const esc = (s) => String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
const num = (v, d = 0) => { const n = Number(String(v ?? "").replace(",", ".")); return Number.isFinite(n) ? n : d; };
const fmtKg = (g) => (g / 1000).toLocaleString("it-IT", { maximumFractionDigits: 1 });

let items = [];
let user = null;
let unsub = null;
let filterType = null;
let filterBrand = null;
let editing = null;
let openEditing = null;

// ---------- Tema ----------
const applyTheme = () => {
  const light = localStorage.getItem("tema") === "chiaro";
  document.documentElement.classList.toggle("light", light);
  document.querySelector('meta[name="theme-color"]').content = light ? "#f6f4f1" : "#121316";
  $("#theme-light").checked = light;
};
applyTheme();
$("#theme-light").addEventListener("change", (e) => { localStorage.setItem("tema", e.target.checked ? "chiaro" : "scuro"); applyTheme(); });

// ---------- Toast ----------
let toastTimer;
function toast(msg) {
  const t = $("#toast");
  t.textContent = msg;
  t.classList.remove("hidden");
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => t.classList.add("hidden"), 2600);
}

// ---------- Accesso ----------
const provider = new GoogleAuthProvider();
provider.setCustomParameters({ prompt: "select_account" });

$("#btn-login").addEventListener("click", async () => {
  $("#login-error").textContent = "";
  try {
    await signInWithPopup(auth, provider);
  } catch (e) {
    if (["auth/popup-blocked", "auth/operation-not-supported-in-this-environment", "auth/cancelled-popup-request"].includes(e.code)) {
      await signInWithRedirect(auth, provider);
    } else if (e.code !== "auth/popup-closed-by-user") {
      $("#login-error").textContent = "Accesso non riuscito: " + (e.code || e.message);
    }
  }
});
getRedirectResult(auth).catch((e) => { $("#login-error").textContent = "Accesso non riuscito: " + (e.code || e.message); });

onAuthStateChanged(auth, (u) => {
  user = u;
  if (unsub) { unsub(); unsub = null; }
  if (!u) {
    $("#main").classList.add("hidden");
    $("#login").classList.remove("hidden");
    return;
  }
  $("#login").classList.add("hidden");
  $("#main").classList.remove("hidden");
  const av = $("#btn-account");
  av.textContent = u.photoURL ? "" : (u.displayName || u.email || "?").slice(0, 1).toUpperCase();
  av.style.backgroundImage = u.photoURL ? `url("${u.photoURL}")` : "";
  $("#acc-email").textContent = u.email;
  listen();
});

function listen() {
  unsub = onSnapshot(COL, { includeMetadataChanges: true }, (snap) => {
    items = snap.docs.map((d) => ({ id: d.id, ...d.data() }));
    const pending = snap.metadata.hasPendingWrites;
    const cache = snap.metadata.fromCache;
    $("#sync").textContent = pending ? "Modifiche in attesa di rete…" : cache ? "Offline · dati salvati sul telefono" : "Sincronizzato";
    render();
  }, async (err) => {
    if (err.code === "permission-denied") {
      const email = user?.email;
      await signOut(auth);
      $("#login-error").textContent = `L'account ${email} non è autorizzato ad aprire questo magazzino.`;
    } else {
      $("#sync").textContent = "Errore di sincronizzazione: " + err.code;
    }
  });
}

$("#btn-account").addEventListener("click", () => $("#dlg-account").showModal());
$("#btn-acc-close").addEventListener("click", () => $("#dlg-account").close());
$("#btn-logout").addEventListener("click", async () => { $("#dlg-account").close(); await signOut(auth); });

// ---------- Schede ----------
document.querySelectorAll(".tab").forEach((b) => b.addEventListener("click", () => {
  document.querySelectorAll(".tab").forEach((x) => x.classList.toggle("active", x === b));
  $("#tab-stock").classList.toggle("hidden", b.dataset.tab !== "stock");
  $("#tab-buy").classList.toggle("hidden", b.dataset.tab !== "buy");
  $("#fab").classList.toggle("hidden", b.dataset.tab !== "stock");
  window.scrollTo({ top: 0 });
}));

// ---------- Logica ----------
const isLow = (f) => num(f.scorta) > 0 && num(f.bobine) < num(f.scorta);
const label = (f) => `${f.marca || ""} · ${f.tipo || ""}`;
const sorter = (a, b) => (a.tipo || "").localeCompare(b.tipo || "", "it") || (a.marca || "").localeCompare(b.marca || "", "it") || (a.colore || "").localeCompare(b.colore || "", "it");

function openPill(f) {
  const peso = num(f.peso, 1000) || 1000;
  if (f.aperta == null || f.aperta === "") return `<button class="open-pill" data-open="${f.id}">＋ bobina aperta</button>`;
  const g = num(f.aperta);
  const pct = Math.max(0, Math.min(100, (g / peso) * 100));
  return `<button class="open-pill" data-open="${f.id}"><span class="open-bar"><i style="width:${pct}%"></i></span>aperta ~${g} g</button>`;
}

function card(f) {
  const low = isLow(f);
  return `<div class="card ${low ? "low" : ""}">
    <div class="swatch" style="background:${esc(f.hex || "#888")}"></div>
    <div class="info" data-edit="${f.id}">
      <div class="name">${esc(f.colore)}${low ? '<span class="tag-low">sotto scorta</span>' : ""}</div>
      <div class="sub">${esc(f.marca)} · ${esc(f.tipo)}</div>
      ${openPill(f)}
    </div>
    <div class="stepper">
      <button data-dec="${f.id}" aria-label="Togli una bobina">−</button>
      <div class="n"><b>${num(f.bobine)}</b><span>bobine</span></div>
      <button data-inc="${f.id}" aria-label="Aggiungi una bobina">+</button>
    </div>
  </div>`;
}

function render() {
  // Statistiche
  const spools = items.reduce((s, f) => s + num(f.bobine), 0);
  const grams = items.reduce((s, f) => s + num(f.bobine) * (num(f.peso, 1000) || 1000) + num(f.aperta), 0);
  const low = items.filter(isLow);
  $("#stats").innerHTML = `
    <div class="stat"><b>${items.length}</b><span>colori</span></div>
    <div class="stat"><b>${spools}</b><span>bobine piene</span></div>
    <div class="stat"><b>${fmtKg(grams)} kg</b><span>totale stimato</span></div>`;
  $("#buy-count").textContent = low.length;
  $("#buy-count").classList.toggle("hidden", low.length === 0);

  // Filtri
  const types = [...new Set(items.map((f) => f.tipo).filter(Boolean))].sort((a, b) => a.localeCompare(b, "it"));
  const brands = [...new Set(items.map((f) => f.marca).filter(Boolean))].sort((a, b) => a.localeCompare(b, "it"));
  if (filterType && !types.includes(filterType)) filterType = null;
  if (filterBrand && !brands.includes(filterBrand)) filterBrand = null;
  $("#chips-type").innerHTML = types.length > 1 ? [`<button class="chip ${!filterType ? "on" : ""}" data-type="">Tutti i tipi</button>`,
    ...types.map((t) => `<button class="chip ${filterType === t ? "on" : ""}" data-type="${esc(t)}">${esc(t)}</button>`)].join("") : "";
  $("#chips-brand").innerHTML = brands.length > 1 ? [`<button class="chip ${!filterBrand ? "on" : ""}" data-brand="">Tutte le marche</button>`,
    ...brands.map((b) => `<button class="chip ${filterBrand === b ? "on" : ""}" data-brand="${esc(b)}">${esc(b)}</button>`)].join("") : "";

  // Lista
  const q = $("#q").value.trim().toLowerCase();
  const shown = items.filter((f) =>
    (!filterType || f.tipo === filterType) && (!filterBrand || f.marca === filterBrand) &&
    (!q || [f.colore, f.marca, f.tipo, f.note].some((x) => (x || "").toLowerCase().includes(q)))
  ).sort(sorter);
  let html = "";
  let lastType = null;
  for (const f of shown) {
    if (f.tipo !== lastType) { html += `<div class="group">${esc(f.tipo || "Senza tipo")}</div>`; lastType = f.tipo; }
    html += card(f);
  }
  $("#list").innerHTML = html;
  const empty = $("#empty");
  empty.classList.toggle("hidden", shown.length > 0);
  empty.textContent = items.length === 0 ? "Il magazzino è vuoto: premi ＋ per aggiungere il primo filamento." : "Nessun filamento trovato.";

  // Da comprare
  const buy = low.sort(sorter);
  $("#buy-list").innerHTML = buy.map((f) => `<div class="card low buy-card">
      <div class="swatch" style="background:${esc(f.hex || "#888")}"></div>
      <div class="info" data-edit="${f.id}">
        <div class="name">${esc(f.colore)}</div>
        <div class="sub">${esc(label(f))} · hai ${num(f.bobine)} su ${num(f.scorta)}</div>
      </div>
      <div class="need">mancano ${num(f.scorta) - num(f.bobine)}</div>
    </div>`).join("");
  $("#buy-empty").classList.toggle("hidden", buy.length > 0);
  $("#btn-share-buy").classList.toggle("hidden", buy.length === 0);

  // Suggerimenti per i campi
  $("#dl-marche").innerHTML = [...new Set([...brands, ...MARCHE])].map((m) => `<option value="${esc(m)}">`).join("");
  $("#dl-tipi").innerHTML = [...new Set([...types, ...TIPI])].map((t) => `<option value="${esc(t)}">`).join("");
}

$("#q").addEventListener("input", render);
$("#chips-type").addEventListener("click", (e) => { const b = e.target.closest("[data-type]"); if (b) { filterType = b.dataset.type || null; render(); } });
$("#chips-brand").addEventListener("click", (e) => { const b = e.target.closest("[data-brand]"); if (b) { filterBrand = b.dataset.brand || null; render(); } });

const stamp = () => ({ aggiornato: serverTimestamp(), aggiornatoDa: user?.email || "" });
const byId = (id) => items.find((f) => f.id === id);

function save(id, data) {
  // Senza await: l'interfaccia si aggiorna subito, Firestore sincronizza appena c'e' rete
  updateDoc(doc(db, "filamenti", id), { ...data, ...stamp() }).catch((e) => toast("Errore: " + e.code));
}

function onListClick(e) {
  const inc = e.target.closest("[data-inc]");
  const dec = e.target.closest("[data-dec]");
  const open = e.target.closest("[data-open]");
  const edit = e.target.closest("[data-edit]");
  if (inc) { const f = byId(inc.dataset.inc); save(f.id, { bobine: num(f.bobine) + 1 }); if (navigator.vibrate) navigator.vibrate(10); }
  else if (dec) {
    const f = byId(dec.dataset.dec);
    if (num(f.bobine) <= 0) { toast("Nessuna bobina piena da togliere"); return; }
    save(f.id, { bobine: num(f.bobine) - 1 }); if (navigator.vibrate) navigator.vibrate(10);
  }
  else if (open) openSpoolDialog(byId(open.dataset.open));
  else if (edit) editDialog(byId(edit.dataset.edit));
}
$("#list").addEventListener("click", onListClick);
$("#buy-list").addEventListener("click", onListClick);

$("#btn-share-buy").addEventListener("click", async () => {
  const buy = items.filter(isLow).sort(sorter);
  const text = "🛒 *Filamenti da comprare*\n\n" + buy.map((f) => `• ${f.marca} ${f.tipo} – ${f.colore}: ${num(f.scorta) - num(f.bobine)}`).join("\n");
  try {
    if (navigator.share) await navigator.share({ text });
    else { await navigator.clipboard.writeText(text); toast("Lista copiata negli appunti"); }
  } catch (_) { /* condivisione annullata */ }
});

// ---------- Scheda filamento ----------
const form = $("#form");
$("#palette").innerHTML = PALETTE.map((c) => `<button type="button" style="background:${c}" data-c="${c}" aria-label="${c}"></button>`).join("");
$("#palette").addEventListener("click", (e) => { const b = e.target.closest("[data-c]"); if (b) form.hex.value = b.dataset.c; });

function editDialog(f) {
  editing = f || null;
  form.reset();
  $("#dlg-title").textContent = f ? "Modifica filamento" : "Nuovo filamento";
  $("#btn-delete").classList.toggle("hidden", !f);
  const last = items.slice().sort((a, b) => (b.aggiornato?.seconds || 0) - (a.aggiornato?.seconds || 0))[0];
  form.marca.value = f?.marca ?? last?.marca ?? "";
  form.tipo.value = f?.tipo ?? (filterType || last?.tipo || "PLA Basic");
  form.colore.value = f?.colore ?? "";
  form.hex.value = f?.hex ?? "#ff8a3d";
  form.bobine.value = f ? num(f.bobine) : 1;
  form.scorta.value = f ? num(f.scorta) : 0;
  form.aperta.value = f?.aperta ?? "";
  form.peso.value = f ? (num(f.peso, 1000) || 1000) : 1000;
  form.note.value = f?.note ?? "";
  const when = f?.aggiornato?.toDate ? f.aggiornato.toDate().toLocaleString("it-IT", { dateStyle: "short", timeStyle: "short" }) : "";
  $("#dlg-meta").textContent = f && when ? `Ultima modifica: ${when}${f.aggiornatoDa ? " · " + f.aggiornatoDa.split("@")[0] : ""}` : "";
  $("#dlg").showModal();
  if (!f) setTimeout(() => form.colore.focus(), 50);
}

$("#fab").addEventListener("click", () => editDialog(null));
$("#btn-cancel").addEventListener("click", () => $("#dlg").close());

form.addEventListener("submit", (e) => {
  e.preventDefault();
  const aperta = form.aperta.value.trim() === "" ? null : Math.max(0, Math.round(num(form.aperta.value)));
  const data = {
    marca: form.marca.value.trim(),
    tipo: form.tipo.value.trim(),
    colore: form.colore.value.trim(),
    hex: form.hex.value,
    bobine: Math.max(0, Math.round(num(form.bobine.value))),
    scorta: Math.max(0, Math.round(num(form.scorta.value))),
    aperta,
    peso: Math.max(1, Math.round(num(form.peso.value, 1000))),
    note: form.note.value.trim(),
  };
  const dup = items.find((f) => f.id !== editing?.id && f.marca?.toLowerCase() === data.marca.toLowerCase() &&
    f.tipo?.toLowerCase() === data.tipo.toLowerCase() && f.colore?.toLowerCase() === data.colore.toLowerCase());
  if (dup && !confirm(`Esiste già ${data.colore} (${data.marca} ${data.tipo}). Salvarlo comunque come voce separata?`)) return;
  if (editing) save(editing.id, data);
  else addDoc(COL, { ...data, creato: serverTimestamp(), ...stamp() }).catch((err) => toast("Errore: " + err.code));
  $("#dlg").close();
  toast(editing ? "Salvato" : `${data.colore} aggiunto`);
});

$("#btn-delete").addEventListener("click", () => {
  if (!editing) return;
  if (!confirm(`Eliminare ${editing.colore} (${label(editing)})?`)) return;
  deleteDoc(doc(db, "filamenti", editing.id)).catch((e) => toast("Errore: " + e.code));
  $("#dlg").close();
  toast("Eliminato");
});

// ---------- Bobina aperta ----------
const fo = $("#form-open");
const range = $("#open-range");
function openSpoolDialog(f) {
  openEditing = f;
  const peso = num(f.peso, 1000) || 1000;
  $("#open-title").textContent = `${f.colore}`;
  $("#open-sub").textContent = `${label(f)} · bobina da ${peso} g · piene: ${num(f.bobine)}`;
  range.max = peso;
  const g = f.aperta == null || f.aperta === "" ? peso : num(f.aperta);
  fo.g.value = g; range.value = g;
  $("#open-quick").innerHTML = [0.25, 0.5, 0.75, 1].map((p) => `<button type="button" data-g="${Math.round(peso * p)}">${Math.round(p * 100)}%</button>`).join("")
    + `<button type="button" data-d="-50">−50 g</button><button type="button" data-d="-100">−100 g</button>`;
  $("#btn-open-new").textContent = num(f.bobine) > 0 ? "Apri bobina nuova (−1 piena)" : "Apri bobina nuova";
  $("#dlg-open").showModal();
}
range.addEventListener("input", () => { fo.g.value = range.value; });
fo.g.addEventListener("input", () => { range.value = fo.g.value; });
$("#open-quick").addEventListener("click", (e) => {
  const b = e.target.closest("button"); if (!b) return;
  const v = b.dataset.g != null ? num(b.dataset.g) : Math.max(0, num(fo.g.value) + num(b.dataset.d));
  fo.g.value = v; range.value = v;
});
fo.addEventListener("submit", (e) => {
  e.preventDefault();
  save(openEditing.id, { aperta: Math.max(0, Math.round(num(fo.g.value))) });
  $("#dlg-open").close();
});
$("#btn-open-cancel").addEventListener("click", () => $("#dlg-open").close());
$("#btn-open-done").addEventListener("click", () => {
  save(openEditing.id, { aperta: null });
  $("#dlg-open").close();
  toast("Bobina aperta finita");
});
$("#btn-open-new").addEventListener("click", () => {
  const f = openEditing;
  const peso = num(f.peso, 1000) || 1000;
  save(f.id, { aperta: peso, bobine: Math.max(0, num(f.bobine) - (num(f.bobine) > 0 ? 1 : 0)) });
  $("#dlg-open").close();
  toast(num(f.bobine) > 0 ? "Aperta una bobina nuova: −1 piena" : "Bobina aperta impostata");
});

// ---------- Installazione offline ----------
if ("serviceWorker" in navigator) navigator.serviceWorker.register("sw.js").catch(() => {});
