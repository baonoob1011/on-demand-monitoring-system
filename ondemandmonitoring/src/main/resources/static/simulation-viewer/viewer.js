const MAP_SIZE = 2048;

const mapEl = document.getElementById("map");
const contentEl = document.getElementById("mapContent");
const basemapEl = document.getElementById("basemap");
const overlayEl = document.getElementById("overlay");
const statusEl = document.getElementById("status");
const detailsEl = document.getElementById("zoneDetails");
const zoneListEl = document.getElementById("zoneList");
const metadataBox = document.getElementById("metadataBox");
const mouseReadout = document.getElementById("mouseReadout");
const debugToggle = document.getElementById("debugToggle");
const editToggle = document.getElementById("editToggle");
const saveZoneButton = document.getElementById("saveZone");
const createZoneButton = document.getElementById("createZone");
const deleteZoneButton = document.getElementById("deleteZone");
const reloadZonesButton = document.getElementById("reloadZones");
const saveStatusEl = document.getElementById("saveStatus");
const toastEl = document.getElementById("toast");

let metadata;
let zones = [];
let features = [];
let scale = 1;
let panX = 0;
let panY = 0;
let selectedZoneId = null;
let dragging = false;
let lastPointer = null;
let zoneDrag = null;
let toastTimer = null;

function apiData(payload) {
  return payload.data || payload.result || payload;
}

async function loadJson(url) {
  const response = await fetch(url, { cache: "no-store" });
  if (!response.ok) {
    throw new Error(`${url} returned ${response.status}`);
  }
  return response.json();
}

function simToPixel(x, y) {
  const px = ((x - metadata.minX) / (metadata.maxX - metadata.minX)) * MAP_SIZE;
  const py = (1 - ((y - metadata.minY) / (metadata.maxY - metadata.minY))) * MAP_SIZE;
  return [px, py];
}

function pixelToSim(px, py) {
  const x = metadata.minX + (px / MAP_SIZE) * (metadata.maxX - metadata.minX);
  const y = metadata.minY + (1 - py / MAP_SIZE) * (metadata.maxY - metadata.minY);
  return [x, y];
}

function coordinateListToPoints(coordinates) {
  return coordinates.map(([x, y]) => simToPixel(x, y).join(",")).join(" ");
}

function openCoordinates(coordinates) {
  if (coordinates.length < 2) return coordinates;
  const [firstX, firstY] = coordinates[0];
  const [lastX, lastY] = coordinates[coordinates.length - 1];
  if (firstX === lastX && firstY === lastY) {
    return coordinates.slice(0, -1);
  }
  return coordinates;
}

function closedCoordinates(coordinates) {
  const opened = openCoordinates(coordinates);
  return [...opened.map(([x, y]) => [x, y]), [...opened[0]]];
}

function isRestrictedZone(zone) {
  if (Object.prototype.hasOwnProperty.call(zone || {}, "restricted")) {
    return zone.restricted === true;
  }
  const type = String(zone?.zoneType || "").trim().toUpperCase();
  return ["AIRPORT", "RESTRICTED", "NO_FLY", "NO-FLY", "NOFLY"].includes(type);
}

function centroid(coordinates) {
  const usable = coordinates.slice(0, -1);
  const total = usable.reduce((acc, [x, y]) => {
    acc.x += x;
    acc.y += y;
    return acc;
  }, { x: 0, y: 0 });
  return [total.x / usable.length, total.y / usable.length];
}

function applyTransform() {
  contentEl.style.transform = `translate(${panX}px, ${panY}px) scale(${scale})`;
}

function fitHome() {
  const rect = mapEl.getBoundingClientRect();
  scale = Math.min(rect.width / MAP_SIZE, rect.height / MAP_SIZE) * 0.96;
  panX = (rect.width - MAP_SIZE * scale) / 2;
  panY = (rect.height - MAP_SIZE * scale) / 2;
  applyTransform();
}

function zoomBy(multiplier, clientX, clientY) {
  const rect = mapEl.getBoundingClientRect();
  const cx = clientX ?? rect.left + rect.width / 2;
  const cy = clientY ?? rect.top + rect.height / 2;
  const localX = (cx - rect.left - panX) / scale;
  const localY = (cy - rect.top - panY) / scale;

  const nextScale = Math.max(0.15, Math.min(8, scale * multiplier));
  panX = cx - rect.left - localX * nextScale;
  panY = cy - rect.top - localY * nextScale;
  scale = nextScale;
  applyTransform();
}

function drawFeatures() {
  for (const feature of features) {
    const type = (feature.geometryType || "").toLowerCase();
    if (type.includes("line")) {
      const line = document.createElementNS("http://www.w3.org/2000/svg", "polyline");
      line.setAttribute("points", coordinateListToPoints(feature.coordinates));
      line.setAttribute("class", "feature-line");
      overlayEl.appendChild(line);
      continue;
    }

    const polygon = document.createElementNS("http://www.w3.org/2000/svg", "polygon");
    polygon.setAttribute("points", coordinateListToPoints(feature.coordinates));
    polygon.setAttribute("class", "feature-shape");
    overlayEl.appendChild(polygon);
  }
}

function redrawOverlay() {
  overlayEl.innerHTML = "";
  drawFeatures();
  drawZones();
  drawDebugOverlay();
  if (selectedZoneId) {
    selectZone(selectedZoneId);
  }
}

function drawZones() {
  for (const zone of zones) {
    const isRestricted = isRestrictedZone(zone);
    const polygon = document.createElementNS("http://www.w3.org/2000/svg", "polygon");
    polygon.setAttribute("points", coordinateListToPoints(zone.coordinates));
    polygon.setAttribute("class", isRestricted ? "zone-polygon restricted-zone" : "zone-polygon");
    polygon.classList.toggle("editable", editToggle.checked);
    polygon.dataset.zoneId = zone.id;
    polygon.addEventListener("click", (event) => {
      event.stopPropagation();
      selectZone(zone.id);
    });
    polygon.addEventListener("pointerdown", (event) => startZoneMove(event, zone.id));
    overlayEl.appendChild(polygon);

    const [cx, cy] = centroid(zone.coordinates);
    const [px, py] = simToPixel(cx, cy);
    const label = document.createElementNS("http://www.w3.org/2000/svg", "text");
    label.setAttribute("x", px);
    label.setAttribute("y", py);
    label.setAttribute("text-anchor", "middle");
    label.setAttribute("dominant-baseline", "central");
    label.setAttribute("class", isRestricted ? "zone-label restricted-label" : "zone-label");
    label.textContent = isRestricted ? `${zone.name} - RESTRICTED` : zone.name;
    overlayEl.appendChild(label);

    if (editToggle.checked && zone.id === selectedZoneId) {
      drawZoneHandles(zone);
    }
  }
}

function drawZoneHandles(zone) {
  openCoordinates(zone.coordinates).forEach(([x, y], index) => {
    const [px, py] = simToPixel(x, y);
    const handle = document.createElementNS("http://www.w3.org/2000/svg", "circle");
    handle.setAttribute("cx", px);
    handle.setAttribute("cy", py);
    handle.setAttribute("r", 9);
    handle.setAttribute("class", "zone-handle");
    handle.dataset.zoneId = zone.id;
    handle.dataset.vertexIndex = index;
    handle.addEventListener("pointerdown", (event) => startVertexMove(event, zone.id, index));
    overlayEl.appendChild(handle);
  });
}

function zoneBounds(zone) {
  const xs = zone.coordinates.map(([x]) => x);
  const ys = zone.coordinates.map(([, y]) => y);
  return {
    minX: Math.min(...xs),
    maxX: Math.max(...xs),
    minY: Math.min(...ys),
    maxY: Math.max(...ys),
  };
}

function drawDebugOverlay() {
  const group = document.createElementNS("http://www.w3.org/2000/svg", "g");
  group.setAttribute("class", "debug-layer");

  const [originX, originY] = simToPixel(0, 0);
  const [minX, minY] = simToPixel(metadata.minX, metadata.minY);
  const [maxX, maxY] = simToPixel(metadata.maxX, metadata.maxY);
  const [xAxisEndX, xAxisEndY] = simToPixel(120, 0);
  const [yAxisEndX, yAxisEndY] = simToPixel(0, 120);

  const bounds = document.createElementNS("http://www.w3.org/2000/svg", "rect");
  bounds.setAttribute("x", Math.min(minX, maxX));
  bounds.setAttribute("y", Math.min(minY, maxY));
  bounds.setAttribute("width", Math.abs(maxX - minX));
  bounds.setAttribute("height", Math.abs(maxY - minY));
  bounds.setAttribute("class", "debug-bounds");
  group.appendChild(bounds);

  const xAxis = document.createElementNS("http://www.w3.org/2000/svg", "line");
  xAxis.setAttribute("x1", originX);
  xAxis.setAttribute("y1", originY);
  xAxis.setAttribute("x2", xAxisEndX);
  xAxis.setAttribute("y2", xAxisEndY);
  xAxis.setAttribute("class", "debug-axis-x");
  group.appendChild(xAxis);

  const yAxis = document.createElementNS("http://www.w3.org/2000/svg", "line");
  yAxis.setAttribute("x1", originX);
  yAxis.setAttribute("y1", originY);
  yAxis.setAttribute("x2", yAxisEndX);
  yAxis.setAttribute("y2", yAxisEndY);
  yAxis.setAttribute("class", "debug-axis-y");
  group.appendChild(yAxis);

  const origin = document.createElementNS("http://www.w3.org/2000/svg", "circle");
  origin.setAttribute("cx", originX);
  origin.setAttribute("cy", originY);
  origin.setAttribute("r", 9);
  origin.setAttribute("class", "debug-origin");
  group.appendChild(origin);

  for (const zone of zones) {
    const [cx, cy] = centroid(zone.coordinates);
    const [px, py] = simToPixel(cx, cy);
    const b = zoneBounds(zone);
    const label = document.createElementNS("http://www.w3.org/2000/svg", "text");
    label.setAttribute("x", px + 12);
    label.setAttribute("y", py + 18);
    label.setAttribute("class", "debug-label");
    label.textContent = `${zone.code} C(${cx.toFixed(1)},${cy.toFixed(1)}) B(${b.minX.toFixed(0)},${b.minY.toFixed(0)}..${b.maxX.toFixed(0)},${b.maxY.toFixed(0)})`;
    group.appendChild(label);
  }

  overlayEl.appendChild(group);
}

function drawZoneList() {
  zoneListEl.innerHTML = "";
  for (const zone of zones) {
    const button = document.createElement("button");
    button.type = "button";
    button.dataset.zoneId = zone.id;
    button.classList.toggle("restricted-zone-button", isRestrictedZone(zone));
    button.textContent = isRestrictedZone(zone) ? `${zone.name} - Restricted` : zone.name;
    button.addEventListener("click", () => selectZone(zone.id));
    zoneListEl.appendChild(button);
  }
}

function selectZone(zoneId) {
  selectedZoneId = zoneId;
  saveZoneButton.disabled = !zoneId;
  deleteZoneButton.disabled = !zoneId;
  overlayEl.querySelectorAll(".zone-polygon").forEach((el) => {
    el.classList.toggle("selected", el.dataset.zoneId === zoneId);
  });
  zoneListEl.querySelectorAll("button").forEach((el) => {
    el.classList.toggle("selected", el.dataset.zoneId === zoneId);
  });

  const zone = zones.find((item) => item.id === zoneId);
  if (!zone) {
    detailsEl.className = "details empty";
    detailsEl.textContent = "Click a zone polygon.";
    return;
  }

  const bounds = zoneBounds(zone);
  detailsEl.className = "details";
  detailsEl.innerHTML = `
    <strong>Zone:</strong> ${zone.name}<br>
    <strong>ID:</strong> <code>${zone.id}</code><br>
    <strong>Code:</strong> <code>${zone.code}</code><br>
    <strong>Type:</strong> ${zone.zoneType}<br>
    <strong>Restricted:</strong> ${isRestrictedZone(zone) ? "YES" : "NO"}<br>
    <label class="restricted-editor">
      <input id="restrictedZoneToggle" type="checkbox" ${isRestrictedZone(zone) ? "checked" : ""}>
      Restricted / No-Fly Zone
    </label>
    <strong>Area:</strong> ${Number(zone.areaSquareMeters).toFixed(2)} m²<br>
    <strong>Bounds:</strong><br>
    minX=${bounds.minX.toFixed(2)} maxX=${bounds.maxX.toFixed(2)}<br>
    minY=${bounds.minY.toFixed(2)} maxY=${bounds.maxY.toFixed(2)}<br>
    <strong>Coordinates:</strong>
    <pre>${zone.coordinates.map(([x, y]) => `${x.toFixed(2)}, ${y.toFixed(2)}`).join("\n")}</pre>
  `;

  const restrictedToggle = document.getElementById("restrictedZoneToggle");
  restrictedToggle?.addEventListener("change", (event) => {
    zone.restricted = event.target.checked;
    redrawOverlay();
    drawZoneList();
    selectZone(zone.id);
    setSaveStatus(`Restricted flag changed for ${zone.name}. Click Save DB to persist.`, false, "info");
  });
}

function setSaveStatus(message, saving = false, type = "info") {
  saveStatusEl.textContent = message;
  saveStatusEl.className = `save-status ${saving ? "saving" : type}`;

  toastEl.textContent = message;
  toastEl.className = `toast visible ${saving ? "saving" : type}`;
  window.clearTimeout(toastTimer);
  if (!saving) {
    toastTimer = window.setTimeout(() => {
      toastEl.classList.remove("visible");
    }, type === "error" ? 5200 : 3200);
  }
}

function pointerToSim(event) {
  const rect = mapEl.getBoundingClientRect();
  const px = (event.clientX - rect.left - panX) / scale;
  const py = (event.clientY - rect.top - panY) / scale;
  return pixelToSim(px, py);
}

function startZoneMove(event, zoneId) {
  if (!editToggle.checked) return;
  event.preventDefault();
  event.stopPropagation();
  selectZone(zoneId);
  zoneDrag = {
    type: "zone",
    zoneId,
    lastSim: pointerToSim(event),
    changed: false,
  };
  mapEl.setPointerCapture(event.pointerId);
}

function startVertexMove(event, zoneId, vertexIndex) {
  event.preventDefault();
  event.stopPropagation();
  selectZone(zoneId);
  zoneDrag = {
    type: "vertex",
    zoneId,
    vertexIndex,
    changed: false,
  };
  mapEl.setPointerCapture(event.pointerId);
}

function updateZoneDrag(event) {
  if (!zoneDrag) return;
  const zone = zones.find((item) => item.id === zoneDrag.zoneId);
  if (!zone) return;

  if (zoneDrag.type === "zone") {
    const nextSim = pointerToSim(event);
    const dx = nextSim[0] - zoneDrag.lastSim[0];
    const dy = nextSim[1] - zoneDrag.lastSim[1];
    zone.coordinates = closedCoordinates(openCoordinates(zone.coordinates).map(([x, y]) => [x + dx, y + dy]));
    zoneDrag.lastSim = nextSim;
  } else {
    const nextSim = pointerToSim(event);
    const opened = openCoordinates(zone.coordinates);
    opened[zoneDrag.vertexIndex] = nextSim;
    zone.coordinates = closedCoordinates(opened);
  }

  zoneDrag.changed = true;
  redrawOverlay();
}

async function saveZone(zoneId) {
  const zone = zones.find((item) => item.id === zoneId);
  if (!zone) return;

  setSaveStatus(`Saving ${zone.name} to DB...`, true);
  try {
    const response = await fetch(`/api/zones/${zone.id}/polygon`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        coordinates: zone.coordinates,
        restricted: isRestrictedZone(zone),
      }),
    });
    if (!response.ok) {
      throw new Error(`Save failed: HTTP ${response.status}`);
    }
    const saved = apiData(await response.json());
    zones = zones.map((item) => item.id === saved.id ? saved : item);
    redrawOverlay();
    drawZoneList();
    selectZone(saved.id);
    setSaveStatus(`Saved ${saved.name} to DB successfully.`, false, "success");
  } catch (error) {
    setSaveStatus(error.message, false, "error");
    console.error(error);
  }
}

async function createZone() {
  const name = window.prompt("New zone name?", "Custom Zone");
  if (!name || !name.trim()) return;
  const restricted = window.confirm("Mark this zone as Restricted / No-Fly?");

  const rect = mapEl.getBoundingClientRect();
  const centerPx = (rect.width / 2 - panX) / scale;
  const centerPy = (rect.height / 2 - panY) / scale;
  const [centerX, centerY] = pixelToSim(centerPx, centerPy);
  const width = 80;
  const height = 60;
  const coordinates = closedCoordinates([
    [centerX - width / 2, centerY - height / 2],
    [centerX + width / 2, centerY - height / 2],
    [centerX + width / 2, centerY + height / 2],
    [centerX - width / 2, centerY + height / 2],
  ]);

  setSaveStatus(`Creating ${name.trim()} in DB...`, true);
  try {
    const response = await fetch("/api/zones", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        name: name.trim(),
        zoneType: "CUSTOM",
        purpose: "Created from simulation viewer",
        restricted,
        coordinates,
      }),
    });
    if (!response.ok) {
      throw new Error(`Create failed: HTTP ${response.status}`);
    }
    const created = apiData(await response.json());
    zones = [...zones, created];
    selectedZoneId = created.id;
    if (!editToggle.checked) {
      editToggle.checked = true;
      mapEl.classList.add("editing");
    }
    redrawOverlay();
    drawZoneList();
    selectZone(created.id);
    setSaveStatus(`Created ${created.name} in DB successfully. Drag it where you want.`, false, "success");
  } catch (error) {
    setSaveStatus(error.message, false, "error");
    console.error(error);
  }
}

async function reloadZones() {
  setSaveStatus("Reloading zones from DB...", true);
  const payload = await loadJson("/api/zones");
  zones = apiData(payload);
  selectedZoneId = null;
  redrawOverlay();
  drawZoneList();
  detailsEl.className = "details empty";
  detailsEl.textContent = "Click a zone polygon.";
  setSaveStatus("Reloaded zones from DB successfully.", false, "success");
}

async function deleteSelectedZone() {
  const zone = zones.find((item) => item.id === selectedZoneId);
  if (!zone) return;

  const confirmed = window.confirm(`Delete zone "${zone.name}" from DB?`);
  if (!confirmed) return;

  setSaveStatus(`Deleting ${zone.name} from DB...`, true);
  try {
    const response = await fetch(`/api/zones/${zone.id}`, { method: "DELETE" });
    if (!response.ok) {
      throw new Error(`Delete failed: HTTP ${response.status}`);
    }
    zones = zones.filter((item) => item.id !== zone.id);
    selectedZoneId = null;
    redrawOverlay();
    drawZoneList();
    selectZone(null);
    setSaveStatus(`Deleted ${zone.name} from DB successfully.`, false, "success");
  } catch (error) {
    setSaveStatus(error.message, false, "error");
    console.error(error);
  }
}

function updateMouseReadout(event) {
  if (!debugToggle.checked) return;
  const rect = mapEl.getBoundingClientRect();
  const px = (event.clientX - rect.left - panX) / scale;
  const py = (event.clientY - rect.top - panY) / scale;
  const [x, y] = pixelToSim(px, py);
  mouseReadout.textContent = `X = ${x.toFixed(1)} m | Y = ${y.toFixed(1)} m`;
}

function attachControls() {
  document.getElementById("zoomIn").addEventListener("click", () => zoomBy(1.25));
  document.getElementById("zoomOut").addEventListener("click", () => zoomBy(0.8));
  document.getElementById("home").addEventListener("click", fitHome);

  debugToggle.addEventListener("change", () => {
    mouseReadout.classList.toggle("visible", debugToggle.checked);
    overlayEl.classList.toggle("debug-visible", debugToggle.checked);
  });

  editToggle.addEventListener("change", () => {
    mapEl.classList.toggle("editing", editToggle.checked);
    setSaveStatus(editToggle.checked ? "Edit on: drag a zone or its corner dots; release to save DB." : "Edit off.");
    redrawOverlay();
  });

  reloadZonesButton.addEventListener("click", () => {
    reloadZones().catch((error) => {
      setSaveStatus(error.message, false, "error");
      console.error(error);
    });
  });

  saveZoneButton.addEventListener("click", () => {
    if (selectedZoneId) {
      saveZone(selectedZoneId);
    }
  });

  createZoneButton.addEventListener("click", () => {
    createZone();
  });

  deleteZoneButton.addEventListener("click", () => {
    deleteSelectedZone();
  });

  mapEl.addEventListener("wheel", (event) => {
    event.preventDefault();
    zoomBy(event.deltaY < 0 ? 1.12 : 0.88, event.clientX, event.clientY);
  }, { passive: false });

  mapEl.addEventListener("pointerdown", (event) => {
    if (editToggle.checked) return;
    dragging = true;
    lastPointer = { x: event.clientX, y: event.clientY };
    mapEl.setPointerCapture(event.pointerId);
    mapEl.classList.add("dragging");
  });

  mapEl.addEventListener("pointermove", (event) => {
    updateMouseReadout(event);
    if (zoneDrag) {
      updateZoneDrag(event);
      return;
    }
    if (!dragging || !lastPointer) return;
    panX += event.clientX - lastPointer.x;
    panY += event.clientY - lastPointer.y;
    lastPointer = { x: event.clientX, y: event.clientY };
    applyTransform();
  });

  window.addEventListener("pointerup", () => {
    if (zoneDrag) {
      const changedZoneId = zoneDrag.zoneId;
      const shouldSave = zoneDrag.changed;
      zoneDrag = null;
      if (shouldSave) {
        saveZone(changedZoneId);
      }
      return;
    }
    dragging = false;
    lastPointer = null;
    mapEl.classList.remove("dragging");
  });

  mapEl.addEventListener("click", (event) => {
    if (event.target.closest?.(".zone-polygon")) {
      return;
    }
    if (selectedZoneId) {
      selectZone(null);
    }
  });

  window.addEventListener("resize", fitHome);
}

async function init() {
  try {
    const [meta, zonesPayload, featuresPayload] = await Promise.all([
      loadJson("./simulation-map.json"),
      loadJson("/api/zones"),
      loadJson("/api/simulation-map"),
    ]);

    metadata = meta;
    zones = apiData(zonesPayload);
    features = apiData(featuresPayload);

    basemapEl.src = metadata.image;
    overlayEl.setAttribute("viewBox", `0 0 ${MAP_SIZE} ${MAP_SIZE}`);
    redrawOverlay();
    drawZoneList();
    attachControls();
    fitHome();

    statusEl.textContent = `${zones.length} zones | ${features.length} map features`;
    metadataBox.textContent = [
      `world=${metadata.worldName}`,
      `minX=${metadata.minX}`,
      `maxX=${metadata.maxX}`,
      `minY=${metadata.minY}`,
      `maxY=${metadata.maxY}`,
      `Y inverted=YES`,
      `X/Y swapped=NO`,
      `source=public.zones`
    ].join("\n");
  } catch (error) {
    statusEl.textContent = "Failed to load";
    metadataBox.textContent = error.message;
    console.error(error);
  }
}

init();
