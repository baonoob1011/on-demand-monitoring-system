const MAP_SIZE = 2048;

const mapEl = document.getElementById("map");
const contentEl = document.getElementById("mapContent");
const basemapEl = document.getElementById("basemap");
const planningCanvas = document.getElementById("planningCanvas");
const planningCtx = planningCanvas.getContext("2d");
const overlayEl = document.getElementById("overlay");
const statusEl = document.getElementById("status");
const detailsEl = document.getElementById("zoneDetails");
const zoneListEl = document.getElementById("zoneList");
const thermalListEl = document.getElementById("thermalList");
const thermalDetailsEl = document.getElementById("thermalDetails");
const createThermalButton = document.getElementById("createThermal");
const editThermalButton = document.getElementById("editThermal");
const deleteThermalButton = document.getElementById("deleteThermal");
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
const planningDebugPanel = document.getElementById("planningDebugPanel");
const planningGridToggle = document.getElementById("planningGridToggle");
const planningTerrainToggle = document.getElementById("planningTerrainToggle");
const planningObstacleToggle = document.getElementById("planningObstacleToggle");
const planningNoDataToggle = document.getElementById("planningNoDataToggle");
const planningClickToggle = document.getElementById("planningClickToggle");
const planningSampleBox = document.getElementById("planningSampleBox");
const readOnlyMode = new URLSearchParams(window.location.search).has("readonly");

let metadata;
let planningGrid;
let zones = [];
let features = [];
let thermalSources = [];
let scale = 1;
let panX = 0;
let panY = 0;
let selectedZoneId = null;
let selectedThermalId = null;
let dragging = false;
let lastPointer = null;
let dragStartPointer = null;
let panMoved = false;
let suppressNextMapClick = false;
let zoneDrag = null;
let thermalDrag = null;
let toastTimer = null;
let planningMarker = null;

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
  const bounds = metadata.imageBounds || metadata;
  const px = ((x - bounds.minX) / (bounds.maxX - bounds.minX)) * MAP_SIZE;
  const py = (1 - ((y - bounds.minY) / (bounds.maxY - bounds.minY))) * MAP_SIZE;
  return [px, py];
}

function simRadiusToPixelSize(radiusMeters) {
  const bounds = metadata.imageBounds || metadata;
  return {
    x: (radiusMeters / (bounds.maxX - bounds.minX)) * MAP_SIZE,
    y: (radiusMeters / (bounds.maxY - bounds.minY)) * MAP_SIZE,
  };
}

function pixelToSim(px, py) {
  const bounds = metadata.imageBounds || metadata;
  const x = bounds.minX + (px / MAP_SIZE) * (bounds.maxX - bounds.minX);
  const y = bounds.minY + (1 - py / MAP_SIZE) * (bounds.maxY - bounds.minY);
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
  drawThermalSources();
  drawDebugOverlay();
  if (selectedZoneId) {
    selectZone(selectedZoneId);
  }
  if (selectedThermalId) {
    selectThermal(selectedThermalId);
  }
}

function thermalClass(source) {
  const type = String(source.thermalType || "").trim().toUpperCase();
  if (type === "FIRE") return "thermal-source thermal-fire";
  if (type === "HOTSPOT") return "thermal-source thermal-hotspot";
  if (type === "WARM_AREA") return "thermal-source thermal-warm";
  return "thermal-source thermal-ambient";
}

function drawThermalSources() {
  for (const source of thermalSources) {
    if (source.active === false) continue;
    const x = Number(source.centerXM);
    const y = Number(source.centerYM);
    const radius = Number(source.radiusM);
    if (!Number.isFinite(x) || !Number.isFinite(y) || !Number.isFinite(radius)) continue;

    const [px, py] = simToPixel(x, y);
    const pixelRadius = simRadiusToPixelSize(radius);

    const radiusArea = document.createElementNS("http://www.w3.org/2000/svg", "ellipse");
    radiusArea.setAttribute("cx", px);
    radiusArea.setAttribute("cy", py);
    radiusArea.setAttribute("rx", Math.max(10, pixelRadius.x));
    radiusArea.setAttribute("ry", Math.max(10, pixelRadius.y));
    radiusArea.setAttribute("class", thermalClass(source));
    radiusArea.classList.toggle("selected", source.id === selectedThermalId);
    radiusArea.dataset.thermalId = source.id;
    radiusArea.setAttribute("aria-label", `${source.name || source.code}: ${Number(source.temperatureC).toFixed(0)} C`);
    radiusArea.addEventListener("click", (event) => {
      event.stopPropagation();
      selectThermal(source.id);
    });
    radiusArea.addEventListener("pointerdown", (event) => startThermalMove(event, source.id));
    overlayEl.appendChild(radiusArea);

    const core = document.createElementNS("http://www.w3.org/2000/svg", "circle");
    core.setAttribute("cx", px);
    core.setAttribute("cy", py);
    core.setAttribute("r", 8);
    core.setAttribute("class", "thermal-core");
    core.dataset.thermalId = source.id;
    core.addEventListener("click", (event) => {
      event.stopPropagation();
      selectThermal(source.id);
    });
    core.addEventListener("pointerdown", (event) => startThermalMove(event, source.id));
    overlayEl.appendChild(core);

    if (!readOnlyMode && editToggle.checked && source.id === selectedThermalId) {
      const resizeHandle = document.createElementNS("http://www.w3.org/2000/svg", "circle");
      resizeHandle.setAttribute("cx", px + Math.max(10, pixelRadius.x));
      resizeHandle.setAttribute("cy", py);
      resizeHandle.setAttribute("r", 9);
      resizeHandle.setAttribute("class", "thermal-resize-handle");
      resizeHandle.dataset.thermalId = source.id;
      resizeHandle.addEventListener("pointerdown", (event) => startThermalResize(event, source.id));
      overlayEl.appendChild(resizeHandle);
    }

    const label = document.createElementNS("http://www.w3.org/2000/svg", "text");
    label.setAttribute("x", px + 14);
    label.setAttribute("y", py - 12);
    label.setAttribute("class", "thermal-label");
    label.textContent = `${Number(source.temperatureC).toFixed(0)}C`;
    overlayEl.appendChild(label);
  }
}

function drawThermalList() {
  thermalListEl.innerHTML = "";
  for (const source of thermalSources) {
    const button = document.createElement("button");
    button.type = "button";
    button.dataset.thermalId = source.id;
    button.classList.toggle("selected", source.id === selectedThermalId);
    button.textContent = `${source.name} - ${Number(source.temperatureC).toFixed(0)}C`;
    button.addEventListener("click", () => selectThermal(source.id));
    thermalListEl.appendChild(button);
  }
}

function selectThermal(thermalId) {
  selectedThermalId = thermalId;
  editThermalButton.disabled = readOnlyMode || !thermalId;
  deleteThermalButton.disabled = readOnlyMode || !thermalId;
  overlayEl.querySelectorAll(".thermal-source").forEach((el) => {
    el.classList.toggle("selected", el.dataset.thermalId === thermalId);
  });
  thermalListEl.querySelectorAll("button").forEach((el) => {
    el.classList.toggle("selected", el.dataset.thermalId === thermalId);
  });

  const source = thermalSources.find((item) => item.id === thermalId);
  if (!source) {
    thermalDetailsEl.className = "details empty";
    thermalDetailsEl.textContent = "Click a thermal area.";
    return;
  }

  thermalDetailsEl.className = "details";
  thermalDetailsEl.innerHTML = `
    <strong>Name:</strong> ${source.name}<br>
    <strong>Code:</strong> <code>${source.code}</code><br>
    <strong>Zone:</strong> <code>${source.zoneCode}</code><br>
    <strong>Type:</strong> ${source.thermalType}<br>
    <strong>Temperature:</strong> ${Number(source.temperatureC).toFixed(1)} C<br>
    <strong>Center:</strong> X ${Number(source.centerXM).toFixed(2)} / Y ${Number(source.centerYM).toFixed(2)}<br>
    <strong>Radius:</strong> ${Number(source.radiusM).toFixed(2)} m<br>
    <strong>Active:</strong> ${source.active === false ? "NO" : "YES"}
  `;
}

function drawZones() {
  for (const zone of zones) {
    const isRestricted = isRestrictedZone(zone);
    const polygon = document.createElementNS("http://www.w3.org/2000/svg", "polygon");
    polygon.setAttribute("points", coordinateListToPoints(zone.coordinates));
    polygon.setAttribute("class", isRestricted ? "zone-polygon restricted-zone" : "zone-polygon");
    polygon.classList.toggle("editable", !readOnlyMode && editToggle.checked);
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

    if (!readOnlyMode && editToggle.checked && zone.id === selectedZoneId) {
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
  drawPlanningMarker();
}

function drawPlanningMarker() {
  if (!planningMarker) return;

  const [px, py] = simToPixel(planningMarker.x, planningMarker.y);
  const group = document.createElementNS("http://www.w3.org/2000/svg", "g");
  group.setAttribute("class", "planning-sample-marker");

  const outer = document.createElementNS("http://www.w3.org/2000/svg", "circle");
  outer.setAttribute("cx", px);
  outer.setAttribute("cy", py);
  outer.setAttribute("r", 15);
  outer.setAttribute("class", "planning-sample-marker-outer");
  group.appendChild(outer);

  const inner = document.createElementNS("http://www.w3.org/2000/svg", "circle");
  inner.setAttribute("cx", px);
  inner.setAttribute("cy", py);
  inner.setAttribute("r", 6);
  inner.setAttribute("class", "planning-sample-marker-inner");
  group.appendChild(inner);

  const hLine = document.createElementNS("http://www.w3.org/2000/svg", "line");
  hLine.setAttribute("x1", px - 22);
  hLine.setAttribute("y1", py);
  hLine.setAttribute("x2", px + 22);
  hLine.setAttribute("y2", py);
  hLine.setAttribute("class", "planning-sample-marker-line");
  group.appendChild(hLine);

  const vLine = document.createElementNS("http://www.w3.org/2000/svg", "line");
  vLine.setAttribute("x1", px);
  vLine.setAttribute("y1", py - 22);
  vLine.setAttribute("x2", px);
  vLine.setAttribute("y2", py + 22);
  vLine.setAttribute("class", "planning-sample-marker-line");
  group.appendChild(vLine);

  const label = document.createElementNS("http://www.w3.org/2000/svg", "text");
  label.setAttribute("x", px + 18);
  label.setAttribute("y", py - 18);
  label.setAttribute("class", "planning-sample-marker-label");
  label.textContent = planningMarker.label;
  group.appendChild(label);

  overlayEl.appendChild(group);
}

function planningValueAt(values, row, col) {
  if (!planningGrid || !values) return null;
  return values[row * planningGrid.width + col] ?? null;
}

function planningCellBounds(row, col) {
  const b = planningGrid.bounds;
  const resolution = Number(planningGrid.resolutionM);
  const centerX = col === planningGrid.width - 1 ? b.maxX : b.minX + col * resolution;
  const centerY = row === planningGrid.height - 1 ? b.maxY : b.minY + row * resolution;
  return {
    minX: Math.max(b.minX, centerX - resolution / 2),
    maxX: Math.min(b.maxX, centerX + resolution / 2),
    minY: Math.max(b.minY, centerY - resolution / 2),
    maxY: Math.min(b.maxY, centerY + resolution / 2),
  };
}

function terrainColor(value, min, max) {
  if (value == null || !Number.isFinite(value)) return "rgba(0,0,0,0)";
  const t = max > min ? Math.max(0, Math.min(1, (value - min) / (max - min))) : 0;
  const r = Math.round(37 + t * 190);
  const g = Math.round(99 + Math.sin(t * Math.PI) * 120);
  const bl = Math.round(235 - t * 180);
  return `rgba(${r}, ${g}, ${bl}, 0.46)`;
}

function drawPlanningOverlay() {
  planningCtx.clearRect(0, 0, MAP_SIZE, MAP_SIZE);
  planningCanvas.classList.toggle("visible", debugToggle.checked && Boolean(planningGrid));
  planningDebugPanel.classList.toggle("visible", debugToggle.checked);
  if (!debugToggle.checked || !planningGrid) {
    return;
  }

  const stats = planningGrid.stats || {};
  const terrainMin = Number(stats.terrainMinM ?? 0);
  const terrainMax = Number(stats.terrainMaxM ?? 1);
  const drawTerrain = planningTerrainToggle.checked;
  const drawObstacles = planningObstacleToggle.checked;
  const drawNoData = planningNoDataToggle.checked;
  const drawGrid = planningGridToggle.checked;

  for (let row = 0; row < planningGrid.height; row++) {
    for (let col = 0; col < planningGrid.width; col++) {
      const terrain = planningValueAt(planningGrid.terrainElevationM, row, col);
      const obstacleHeight = planningValueAt(planningGrid.obstacleHeightM, row, col);
      if (!drawTerrain && !drawObstacles && !drawNoData) continue;

      const bounds = planningCellBounds(row, col);
      const [left, bottom] = simToPixel(bounds.minX, bounds.minY);
      const [right, top] = simToPixel(bounds.maxX, bounds.maxY);
      const x = Math.min(left, right);
      const y = Math.min(top, bottom);
      const w = Math.max(1, Math.abs(right - left));
      const h = Math.max(1, Math.abs(bottom - top));

      if (terrain == null && drawNoData) {
        planningCtx.fillStyle = "rgba(71, 85, 105, 0.32)";
        planningCtx.fillRect(x, y, w, h);
        continue;
      }

      if (terrain != null && drawTerrain) {
        planningCtx.fillStyle = terrainColor(Number(terrain), terrainMin, terrainMax);
        planningCtx.fillRect(x, y, w, h);
      }

      if (drawObstacles && obstacleHeight != null && Number(obstacleHeight) > 0.25) {
        planningCtx.fillStyle = "rgba(126, 34, 206, 0.62)";
        planningCtx.fillRect(x, y, w, h);
      }
    }
  }

  if (drawGrid) {
    planningCtx.save();
    planningCtx.strokeStyle = "rgba(15, 23, 42, 0.2)";
    planningCtx.lineWidth = 1;
    const step = 5;
    for (let col = 0; col < planningGrid.width; col += step) {
      const x = col === planningGrid.width - 1
        ? planningGrid.bounds.maxX
        : planningGrid.bounds.minX + col * planningGrid.resolutionM;
      const [px] = simToPixel(x, 0);
      planningCtx.beginPath();
      planningCtx.moveTo(px, 0);
      planningCtx.lineTo(px, MAP_SIZE);
      planningCtx.stroke();
    }
    for (let row = 0; row < planningGrid.height; row += step) {
      const y = row === planningGrid.height - 1
        ? planningGrid.bounds.maxY
        : planningGrid.bounds.minY + row * planningGrid.resolutionM;
      const [, py] = simToPixel(0, y);
      planningCtx.beginPath();
      planningCtx.moveTo(0, py);
      planningCtx.lineTo(MAP_SIZE, py);
      planningCtx.stroke();
    }
    planningCtx.restore();
  }
}

function formatMeters(value) {
  return value == null ? "NO DATA" : `${Number(value).toFixed(3)} m`;
}

function formatYesNo(value) {
  return value ? "YES" : "NO";
}

function findNearestObstacle(x, y, radiusM = 25, minimumHeightM = 1) {
  if (!planningGrid?.obstacleHeightM) return null;

  const resolution = Number(planningGrid.resolutionM);
  const b = planningGrid.bounds;
  const centerCol = Math.round((x - b.minX) / resolution);
  const centerRow = Math.round((y - b.minY) / resolution);
  const searchCells = Math.ceil(radiusM / resolution);
  let nearest = null;

  for (let row = Math.max(0, centerRow - searchCells); row <= Math.min(planningGrid.height - 1, centerRow + searchCells); row++) {
    for (let col = Math.max(0, centerCol - searchCells); col <= Math.min(planningGrid.width - 1, centerCol + searchCells); col++) {
      const obstacleHeight = planningValueAt(planningGrid.obstacleHeightM, row, col);
      if (obstacleHeight == null || Number(obstacleHeight) <= minimumHeightM) continue;

      const cellX = col === planningGrid.width - 1 ? b.maxX : b.minX + col * resolution;
      const cellY = row === planningGrid.height - 1 ? b.maxY : b.minY + row * resolution;
      const distanceM = Math.hypot(cellX - x, cellY - y);
      if (distanceM > radiusM) continue;

      if (!nearest || distanceM < nearest.distanceM) {
        nearest = {
          x: cellX,
          y: cellY,
          distanceM,
          obstacleHeightM: Number(obstacleHeight),
          terrainElevationM: planningValueAt(planningGrid.terrainElevationM, row, col),
          surfaceElevationM: planningValueAt(planningGrid.surfaceElevationM, row, col),
        };
      }
    }
  }

  return nearest;
}

function formatNearestObstacle(obstacle) {
  if (!obstacle) return "None within 25m";
  return `${obstacle.obstacleHeightM.toFixed(3)} m at X ${obstacle.x.toFixed(1)}, Y ${obstacle.y.toFixed(1)} (${obstacle.distanceM.toFixed(1)}m away)`;
}

function sampleStatus(sample) {
  if (!sample.insideWorldBounds) return "OUTSIDE WORLD";
  if (sample.terrainElevationM == null) return "NO DATA";
  return "VALID";
}

async function samplePlanningAt(x, y) {
  planningSampleBox.textContent = "Loading planning sample...";
  try {
    const payload = await loadJson(`/api/planning/environment/sample?x=${encodeURIComponent(x)}&y=${encodeURIComponent(y)}`);
    const sample = apiData(payload);
    const nearestObstacle = findNearestObstacle(Number(sample.simX), Number(sample.simY));
    const shouldSnapToObstacle = Number(sample.obstacleHeightM || 0) <= 1
      && nearestObstacle?.distanceM <= 1.5;
    const displaySample = shouldSnapToObstacle
      ? {
          simX: nearestObstacle.x,
          simY: nearestObstacle.y,
          terrainElevationM: nearestObstacle.terrainElevationM,
          surfaceElevationM: nearestObstacle.surfaceElevationM,
          obstacleHeightM: nearestObstacle.obstacleHeightM,
        }
      : sample;
    planningMarker = {
      x: Number(displaySample.simX),
      y: Number(displaySample.simY),
      label: displaySample.terrainElevationM == null
        ? "NO DATA"
        : `Z ${Number(displaySample.terrainElevationM).toFixed(1)} / S ${displaySample.surfaceElevationM == null ? "--" : Number(displaySample.surfaceElevationM).toFixed(1)}`,
    };
    redrawOverlay();
    planningSampleBox.textContent = [
      "PLANNING SAMPLE",
      "",
      `Gazebo X:         ${Number(displaySample.simX).toFixed(2)} m`,
      `Gazebo Y:         ${Number(displaySample.simY).toFixed(2)} m`,
      `Sample Mode:      ${shouldSnapToObstacle ? `SNAPPED TO SURFACE (${nearestObstacle.distanceM.toFixed(1)}m)` : "EXACT CLICK"}`,
      "",
      `Inside World:     ${formatYesNo(sample.insideWorldBounds)}`,
      `Terrain Z:        ${formatMeters(displaySample.terrainElevationM)}`,
      `Surface Z:        ${formatMeters(displaySample.surfaceElevationM)}`,
      `Above Terrain:    ${formatMeters(displaySample.obstacleHeightM)}`,
      `Nearest Obstacle: ${formatNearestObstacle(nearestObstacle)}`,
      "",
      `Restricted:       ${formatYesNo(sample.restricted)}`,
      `Zone Code:        ${sample.restrictedZoneCode || "—"}`,
      `Zone Name:        ${sample.restrictedZoneName || "—"}`,
      "",
      `Grid Accuracy:    ${Number(planningGrid?.resolutionM || 0).toFixed(0)}m mesh sample`,
      `Data Status:      ${sampleStatus(sample)}`,
    ].join("\n");
  } catch (error) {
    planningSampleBox.textContent = error.message;
    console.error(error);
  }
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
  saveZoneButton.disabled = readOnlyMode || !zoneId;
  deleteZoneButton.disabled = readOnlyMode || !zoneId;
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
  const zoneThermalSources = thermalSources.filter((source) => source.zoneCode === zone.code);
  const thermalDetails = zoneThermalSources.length
    ? `<strong>Thermal sources:</strong>
      <ul class="thermal-detail-list">
        ${zoneThermalSources.map((source) => `
          <li>
            <span>${source.name}</span>
            <strong>${Number(source.temperatureC).toFixed(0)}C</strong>
          </li>
        `).join("")}
      </ul>`
    : `<strong>Thermal sources:</strong> None<br>`;
  detailsEl.className = "details";
  detailsEl.innerHTML = `
    <strong>Zone:</strong> ${zone.name}<br>
    <strong>ID:</strong> <code>${zone.id}</code><br>
    <strong>Code:</strong> <code>${zone.code}</code><br>
    <strong>Type:</strong> ${zone.zoneType}<br>
    <strong>Restricted:</strong> ${isRestrictedZone(zone) ? "YES" : "NO"}<br>
    ${readOnlyMode ? "" : `<label class="restricted-editor">
      <input id="restrictedZoneToggle" type="checkbox" ${isRestrictedZone(zone) ? "checked" : ""}>
      Restricted / No-Fly Zone
    </label>`}
    <strong>Area:</strong> ${Number(zone.areaSquareMeters).toFixed(2)} m²<br>
    <strong>Bounds:</strong><br>
    minX=${bounds.minX.toFixed(2)} maxX=${bounds.maxX.toFixed(2)}<br>
    minY=${bounds.minY.toFixed(2)} maxY=${bounds.maxY.toFixed(2)}<br>
    ${thermalDetails}
    <strong>Coordinates:</strong>
    <pre>${zone.coordinates.map(([x, y]) => `${x.toFixed(2)}, ${y.toFixed(2)}`).join("\n")}</pre>
  `;

  const restrictedToggle = document.getElementById("restrictedZoneToggle");
  restrictedToggle?.addEventListener("change", (event) => {
    if (readOnlyMode) return;
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
  if (readOnlyMode || !editToggle.checked) return;
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
  if (readOnlyMode) return;
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

function startThermalMove(event, thermalId) {
  if (readOnlyMode || !editToggle.checked) return;
  event.preventDefault();
  event.stopPropagation();
  selectThermal(thermalId);
  thermalDrag = {
    type: "move",
    thermalId,
    lastSim: pointerToSim(event),
    changed: false,
  };
  mapEl.setPointerCapture(event.pointerId);
}

function startThermalResize(event, thermalId) {
  if (readOnlyMode || !editToggle.checked) return;
  event.preventDefault();
  event.stopPropagation();
  selectThermal(thermalId);
  thermalDrag = {
    type: "resize",
    thermalId,
    changed: false,
  };
  mapEl.setPointerCapture(event.pointerId);
}

function updateThermalDrag(event) {
  if (!thermalDrag) return;
  const source = thermalSources.find((item) => item.id === thermalDrag.thermalId);
  if (!source) return;

  const nextSim = pointerToSim(event);
  if (thermalDrag.type === "move") {
    const dx = nextSim[0] - thermalDrag.lastSim[0];
    const dy = nextSim[1] - thermalDrag.lastSim[1];
    source.centerXM = Number(source.centerXM) + dx;
    source.centerYM = Number(source.centerYM) + dy;
    thermalDrag.lastSim = nextSim;
  } else {
    source.radiusM = Math.max(
      1,
      Math.hypot(nextSim[0] - Number(source.centerXM), nextSim[1] - Number(source.centerYM)),
    );
  }

  thermalDrag.changed = true;
  redrawOverlay();
  drawThermalList();
}

async function saveZone(zoneId) {
  if (readOnlyMode) {
    setSaveStatus("Read-only view: zone changes are disabled for this workspace.", false, "info");
    return;
  }
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
  if (readOnlyMode) {
    setSaveStatus("Read-only view: creating zones is disabled for this workspace.", false, "info");
    return;
  }
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
  const [payload, thermalPayload] = await Promise.all([
    loadJson("/api/zones"),
    loadJson("/api/thermal-sources").catch(() => ({ data: [] })),
  ]);
  zones = apiData(payload);
  thermalSources = apiData(thermalPayload);
  selectedZoneId = null;
  selectedThermalId = null;
  redrawOverlay();
  drawZoneList();
  drawThermalList();
  detailsEl.className = "details empty";
  detailsEl.textContent = "Click a zone polygon.";
  thermalDetailsEl.className = "details empty";
  thermalDetailsEl.textContent = "Click a thermal area.";
  setSaveStatus("Reloaded zones from DB successfully.", false, "success");
}

function currentMapCenterSim() {
  const rect = mapEl.getBoundingClientRect();
  const centerPx = (rect.width / 2 - panX) / scale;
  const centerPy = (rect.height / 2 - panY) / scale;
  return pixelToSim(centerPx, centerPy);
}

function promptNumber(label, currentValue) {
  const raw = window.prompt(label, String(currentValue));
  if (raw == null) return null;
  const value = Number(raw);
  if (!Number.isFinite(value)) {
    setSaveStatus(`${label} must be a number.`, false, "error");
    return null;
  }
  return value;
}

function promptThermalPayload(existing) {
  const [centerX, centerY] = existing
    ? [Number(existing.centerXM), Number(existing.centerYM)]
    : currentMapCenterSim();
  const defaultZoneCode = existing?.zoneCode
    || zones.find((zone) => zone.id === selectedZoneId)?.code
    || zones[0]?.code
    || "FOREST_MONITORING_AREA";
  const name = window.prompt("Thermal name?", existing?.name || "Custom Thermal Area");
  if (!name || !name.trim()) return null;
  const zoneCode = window.prompt("Zone code?", defaultZoneCode);
  if (!zoneCode || !zoneCode.trim()) return null;
  const type = window.prompt("Type: AMBIENT, WARM_AREA, HOTSPOT, FIRE", existing?.thermalType || "HOTSPOT");
  if (!type || !["AMBIENT", "WARM_AREA", "HOTSPOT", "FIRE"].includes(type.trim().toUpperCase())) {
    setSaveStatus("Thermal type must be AMBIENT, WARM_AREA, HOTSPOT, or FIRE.", false, "error");
    return null;
  }
  const temperatureC = promptNumber("Temperature C?", existing?.temperatureC ?? 110);
  if (temperatureC == null) return null;
  const radiusM = promptNumber("Radius meters?", existing?.radiusM ?? 8);
  if (radiusM == null || radiusM <= 0) {
    setSaveStatus("Radius must be greater than 0.", false, "error");
    return null;
  }
  const x = promptNumber("Center X meters?", centerX.toFixed(2));
  if (x == null) return null;
  const y = promptNumber("Center Y meters?", centerY.toFixed(2));
  if (y == null) return null;
  const active = window.confirm("Active thermal source?");

  return {
    code: existing?.code,
    name: name.trim(),
    zoneCode: zoneCode.trim(),
    thermalType: type.trim().toUpperCase(),
    temperatureC,
    centerXM: x,
    centerYM: y,
    radiusM,
    active,
  };
}

async function saveThermalPayload(url, method, payload) {
  const response = await fetch(url, {
    method,
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  if (!response.ok) {
    throw new Error(`Thermal save failed: HTTP ${response.status}`);
  }
  return apiData(await response.json());
}

function thermalToPayload(source) {
  return {
    code: source.code,
    name: source.name,
    zoneCode: source.zoneCode,
    thermalType: source.thermalType,
    temperatureC: Number(source.temperatureC),
    centerXM: Number(source.centerXM),
    centerYM: Number(source.centerYM),
    radiusM: Number(source.radiusM),
    active: source.active !== false,
  };
}

async function saveThermalSource(source) {
  setSaveStatus(`Saving ${source.name} to DB...`, true);
  try {
    const saved = await saveThermalPayload(
      `/api/thermal-sources/${source.id}`,
      "PUT",
      thermalToPayload(source),
    );
    thermalSources = thermalSources.map((item) => item.id === saved.id ? saved : item);
    selectedThermalId = saved.id;
    redrawOverlay();
    drawThermalList();
    selectThermal(saved.id);
    setSaveStatus(`Saved thermal source ${saved.name}.`, false, "success");
  } catch (error) {
    setSaveStatus(error.message, false, "error");
    console.error(error);
  }
}

async function createThermal() {
  if (readOnlyMode) {
    setSaveStatus("Read-only view: creating thermal sources is disabled for this workspace.", false, "info");
    return;
  }
  const payload = promptThermalPayload(null);
  if (!payload) return;
  setSaveStatus(`Creating ${payload.name}...`, true);
  try {
    const created = await saveThermalPayload("/api/thermal-sources", "POST", payload);
    thermalSources = [...thermalSources, created];
    selectedThermalId = created.id;
    redrawOverlay();
    drawThermalList();
    selectThermal(created.id);
    setSaveStatus(`Created thermal source ${created.name}.`, false, "success");
  } catch (error) {
    setSaveStatus(error.message, false, "error");
    console.error(error);
  }
}

async function editSelectedThermal() {
  if (readOnlyMode) {
    setSaveStatus("Read-only view: editing thermal sources is disabled for this workspace.", false, "info");
    return;
  }
  const source = thermalSources.find((item) => item.id === selectedThermalId);
  if (!source) return;
  const payload = promptThermalPayload(source);
  if (!payload) return;
  setSaveStatus(`Saving ${payload.name}...`, true);
  try {
    const saved = await saveThermalPayload(`/api/thermal-sources/${source.id}`, "PUT", payload);
    thermalSources = thermalSources.map((item) => item.id === saved.id ? saved : item);
    selectedThermalId = saved.id;
    redrawOverlay();
    drawThermalList();
    selectThermal(saved.id);
    setSaveStatus(`Saved thermal source ${saved.name}.`, false, "success");
  } catch (error) {
    setSaveStatus(error.message, false, "error");
    console.error(error);
  }
}

async function deleteSelectedThermal() {
  if (readOnlyMode) {
    setSaveStatus("Read-only view: deleting thermal sources is disabled for this workspace.", false, "info");
    return;
  }
  const source = thermalSources.find((item) => item.id === selectedThermalId);
  if (!source) return;
  const confirmed = window.confirm(`Delete thermal source "${source.name}" from DB?`);
  if (!confirmed) return;
  setSaveStatus(`Deleting ${source.name}...`, true);
  try {
    const response = await fetch(`/api/thermal-sources/${source.id}`, { method: "DELETE" });
    if (!response.ok) {
      throw new Error(`Thermal delete failed: HTTP ${response.status}`);
    }
    thermalSources = thermalSources.filter((item) => item.id !== source.id);
    selectedThermalId = null;
    redrawOverlay();
    drawThermalList();
    selectThermal(null);
    setSaveStatus(`Deleted thermal source ${source.name}.`, false, "success");
  } catch (error) {
    setSaveStatus(error.message, false, "error");
    console.error(error);
  }
}

async function deleteSelectedZone() {
  if (readOnlyMode) {
    setSaveStatus("Read-only view: deleting zones is disabled for this workspace.", false, "info");
    return;
  }
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
  document.body.classList.toggle("readonly-viewer", readOnlyMode);
  if (readOnlyMode) {
    editToggle.checked = false;
    editToggle.disabled = true;
    saveZoneButton.disabled = true;
    createZoneButton.disabled = true;
    deleteZoneButton.disabled = true;
    createThermalButton.disabled = true;
    editThermalButton.disabled = true;
    deleteThermalButton.disabled = true;
    setSaveStatus("Read-only view. Drone operators can inspect zones only.", false, "info");
  }

  document.getElementById("zoomIn").addEventListener("click", () => zoomBy(1.25));
  document.getElementById("zoomOut").addEventListener("click", () => zoomBy(0.8));
  document.getElementById("home").addEventListener("click", fitHome);

  debugToggle.addEventListener("change", () => {
    mouseReadout.classList.toggle("visible", debugToggle.checked);
    overlayEl.classList.toggle("debug-visible", debugToggle.checked);
    drawPlanningOverlay();
  });

  [
    planningGridToggle,
    planningTerrainToggle,
    planningObstacleToggle,
    planningNoDataToggle,
  ].forEach((control) => control.addEventListener("change", drawPlanningOverlay));

  editToggle.addEventListener("change", () => {
    if (readOnlyMode) {
      editToggle.checked = false;
      mapEl.classList.remove("editing");
      setSaveStatus("Read-only view: editing is disabled for this workspace.", false, "info");
      return;
    }
    mapEl.classList.toggle("editing", editToggle.checked);
    setSaveStatus(editToggle.checked ? "Edit on: drag zones, thermal centers, or thermal radius handles; release to save DB." : "Edit off.");
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

  createThermalButton.addEventListener("click", () => {
    createThermal();
  });

  editThermalButton.addEventListener("click", () => {
    editSelectedThermal();
  });

  deleteThermalButton.addEventListener("click", () => {
    deleteSelectedThermal();
  });

  mapEl.addEventListener("wheel", (event) => {
    event.preventDefault();
    zoomBy(event.deltaY < 0 ? 1.12 : 0.88, event.clientX, event.clientY);
  }, { passive: false });

  mapEl.addEventListener("pointerdown", (event) => {
    if (!readOnlyMode && editToggle.checked) return;
    dragging = true;
    panMoved = false;
    lastPointer = { x: event.clientX, y: event.clientY };
    dragStartPointer = { ...lastPointer };
    mapEl.setPointerCapture(event.pointerId);
    mapEl.classList.add("dragging");
  });

  mapEl.addEventListener("pointermove", (event) => {
    updateMouseReadout(event);
    if (zoneDrag) {
      updateZoneDrag(event);
      return;
    }
    if (thermalDrag) {
      updateThermalDrag(event);
      return;
    }
    if (!dragging || !lastPointer) return;
    const dx = event.clientX - lastPointer.x;
    const dy = event.clientY - lastPointer.y;
    if (dragStartPointer && Math.hypot(event.clientX - dragStartPointer.x, event.clientY - dragStartPointer.y) > 3) {
      panMoved = true;
    }
    panX += dx;
    panY += dy;
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
    if (thermalDrag) {
      const source = thermalSources.find((item) => item.id === thermalDrag.thermalId);
      const shouldSave = thermalDrag.changed;
      thermalDrag = null;
      if (shouldSave && source) {
        saveThermalSource(source);
      }
      return;
    }
    if (dragging && panMoved) {
      suppressNextMapClick = true;
    }
    dragging = false;
    panMoved = false;
    lastPointer = null;
    dragStartPointer = null;
    mapEl.classList.remove("dragging");
  });

  mapEl.addEventListener("click", (event) => {
    if (suppressNextMapClick) {
      suppressNextMapClick = false;
      return;
    }
    if (event.target.closest?.(".zone-polygon")) {
      return;
    }
    if (debugToggle.checked && planningClickToggle.checked) {
      const [x, y] = pointerToSim(event);
      samplePlanningAt(x, y);
    }
    if (selectedZoneId) {
      selectZone(null);
    }
  });

  window.addEventListener("resize", fitHome);
}

async function init() {
  try {
    const [meta, zonesPayload, featuresPayload, thermalPayload, planningPayload] = await Promise.all([
      loadJson("./simulation-map.json"),
      loadJson("/api/zones"),
      loadJson("/api/simulation-map"),
      loadJson("/api/thermal-sources").catch(() => ({ data: [] })),
      loadJson("/api/planning/environment/grid").catch(() => ({ data: null })),
    ]);

    metadata = meta;
    zones = apiData(zonesPayload);
    features = apiData(featuresPayload);
    thermalSources = apiData(thermalPayload);
    planningGrid = apiData(planningPayload);

    basemapEl.src = metadata.image;
    overlayEl.setAttribute("viewBox", `0 0 ${MAP_SIZE} ${MAP_SIZE}`);
    redrawOverlay();
    drawPlanningOverlay();
    drawZoneList();
    drawThermalList();
    attachControls();
    fitHome();

    statusEl.textContent = `${zones.length} zones | ${features.length} map features | ${thermalSources.length} thermal sources | planning ${planningGrid ? "ready" : "unavailable"}`;
    metadataBox.textContent = [
      `world=${metadata.worldName}`,
      `minX=${metadata.minX}`,
      `maxX=${metadata.maxX}`,
      `minY=${metadata.minY}`,
      `maxY=${metadata.maxY}`,
      `imageMinX=${metadata.imageBounds?.minX ?? metadata.minX}`,
      `imageMaxX=${metadata.imageBounds?.maxX ?? metadata.maxX}`,
      `imageMinY=${metadata.imageBounds?.minY ?? metadata.minY}`,
      `imageMaxY=${metadata.imageBounds?.maxY ?? metadata.maxY}`,
      `planningResolution=${planningGrid?.resolutionM ?? "unavailable"}m`,
      `planningGrid=${planningGrid ? `${planningGrid.width}x${planningGrid.height}` : "unavailable"}`,
      `planningTerrain=${planningGrid?.stats ? `${planningGrid.stats.terrainMinM}..${planningGrid.stats.terrainMaxM}m` : "unavailable"}`,
      `Y inverted=YES`,
      `X/Y swapped=NO`,
      `thermalSources=${thermalSources.length}`,
      `source=public.zones + public.thermal_sources`
    ].join("\n");
  } catch (error) {
    statusEl.textContent = "Failed to load";
    metadataBox.textContent = error.message;
    console.error(error);
  }
}

init();
