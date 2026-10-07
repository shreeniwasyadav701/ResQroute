/* ResQRoute single page controller. */
(() => {
  const state = {
    intersections: [],
    roads: [],
    incidents: [],
    units: [],
    hospitals: [],
    selectedRoute: null,
    pendingNodeId: null
  };

  const $ = (sel) => document.querySelector(sel);
  const $$ = (sel) => Array.from(document.querySelectorAll(sel));

  /* ---------------- toasts ---------------- */
  function toast(message, kind) {
    const el = document.createElement('div');
    el.className = 'toast ' + (kind || '');
    el.textContent = message;
    $('#toasts').appendChild(el);
    setTimeout(() => el.remove(), 4200);
  }

  /* ---------------- auth ---------------- */
  function showAuth(message, ok) {
    $('#authView').classList.remove('hidden');
    $('#appView').classList.add('hidden');
    const box = $('#authMessage');
    if (message) {
      box.textContent = message;
      box.classList.toggle('ok', !!ok);
      box.classList.remove('hidden');
    } else {
      box.classList.add('hidden');
    }
  }

  function showApp() {
    $('#authView').classList.add('hidden');
    $('#appView').classList.remove('hidden');
    const user = API.user();
    $('#userLabel').textContent = user ? `${user.username} · ${user.role}` : '';
    renderNav();
    boot();
  }

  $$('.auth-tab').forEach(btn => btn.addEventListener('click', () => {
    $$('.auth-tab').forEach(b => b.classList.remove('active'));
    btn.classList.add('active');
    const login = btn.dataset.authtab === 'login';
    $('#loginForm').classList.toggle('hidden', !login);
    $('#registerForm').classList.toggle('hidden', login);
    $('#authMessage').classList.add('hidden');
  }));

  $('#loginForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    const data = Object.fromEntries(new FormData(e.target).entries());
    try {
      const res = await API.post('/api/auth/login', data);
      API.setSession(res.data.token, res.data);
      showApp();
      toast('Welcome back, ' + res.data.username, 'ok');
    } catch (err) {
      showAuth(err.message);
    }
  });

  $('#registerForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    const data = Object.fromEntries(new FormData(e.target).entries());
    try {
      await API.post('/api/auth/register', data);
      showAuth('Account created. Sign in with your credentials.', true);
      $$('.auth-tab')[0].click();
    } catch (err) {
      showAuth(err.message);
    }
  });

  $('#logoutBtn').addEventListener('click', () => {
    API.clearSession();
    location.reload();
  });

  window.addEventListener('resq:unauthorized', () => showAuth('Session expired. Sign in again.'));

  /* ---------------- navigation ---------------- */
  const TABS = [
    { id: 'routes', label: 'Map & Routes', roles: ['ADMIN', 'TRAFFIC_OPERATOR', 'EMERGENCY_OPERATOR', 'USER'] },
    { id: 'incidents', label: 'Incidents', roles: ['ADMIN', 'TRAFFIC_OPERATOR', 'EMERGENCY_OPERATOR', 'USER'] },
    { id: 'emergency', label: 'Emergency', roles: ['ADMIN', 'EMERGENCY_OPERATOR'] },
    { id: 'hospitals', label: 'Hospitals', roles: ['ADMIN', 'EMERGENCY_OPERATOR', 'USER', 'TRAFFIC_OPERATOR'] },
    { id: 'traffic', label: 'Traffic Control', roles: ['ADMIN', 'TRAFFIC_OPERATOR'] },
    { id: 'admin', label: 'Admin', roles: ['ADMIN'] }
  ];

  function canWriteIncidents() {
    const u = API.user();
    return u && ['ADMIN', 'TRAFFIC_OPERATOR', 'EMERGENCY_OPERATOR'].includes(u.role);
  }

  function renderNav() {
    const role = API.user().role;
    const nav = $('#mainNav');
    nav.innerHTML = '';
    TABS.filter(t => t.roles.includes(role)).forEach((tab, i) => {
      const btn = document.createElement('button');
      btn.textContent = tab.label;
      btn.dataset.tab = tab.id;
      if (i === 0) btn.classList.add('active');
      btn.addEventListener('click', () => activateTab(tab.id));
      nav.appendChild(btn);
    });
    activateTab(TABS.find(t => t.roles.includes(role)).id);
  }

  function activateTab(id) {
    $$('#mainNav button').forEach(b => b.classList.toggle('active', b.dataset.tab === id));
    $$('.panel').forEach(p => p.classList.toggle('hidden', p.id !== 'panel-' + id));
    if (id === 'traffic') loadRoads();
    if (id === 'admin') loadAdmin();
    if (id === 'emergency') { loadIncidentsInto($('#dispatchIncident'), true); loadUnits(); }
    if (id === 'hospitals') { loadIncidentsInto($('#hospitalIncident'), true); fillNodeSelect($('#hospitalNode')); loadHospitals(); }
    if (id === 'incidents') loadIncidents();
  }

  /* ---------------- data loading ---------------- */
  async function boot() {
    try {
      const [nodesRes, roadsRes] = await Promise.all([
        API.get('/api/network/intersections'),
        API.get('/api/network/roads')
      ]);
      state.intersections = nodesRes.data || [];
      state.roads = roadsRes.data || [];
      MapRenderer.setData(state.intersections, state.roads);
      fillNodeSelects();
      connectSocket();
    } catch (err) {
      toast(err.message, 'error');
    }
  }

  function fillNodeSelect(select, keepValue) {
    if (!select) return;
    const current = keepValue ? select.value : null;
    select.innerHTML = state.intersections
      .map(n => `<option value="${n.id}">${n.name}</option>`).join('');
    if (current) select.value = current;
  }

  function fillNodeSelects() {
    ['#sourceSelect', '#destSelect', '#incidentLocation', '#incidentRoad', '#unitStation', '#hospitalNode']
      .forEach(sel => fillNodeSelect($(sel), true));
    const roadSelect = $('#incidentRoad');
    if (roadSelect) {
      roadSelect.innerHTML = '<option value="">Auto-detect</option>' +
        state.roads.map(r => `<option value="${r.id}">${r.name}</option>`).join('');
    }
  }

  async function loadRoads() {
    try {
      const res = await API.get('/api/network/roads');
      state.roads = res.data.roads || [];
      MapRenderer.updateRoads(state.roads);
      renderRoadTable();
    } catch (err) { toast(err.message, 'error'); }
  }

  async function loadIncidents() {
    try {
      const status = $('#incidentFilter').value;
      const query = status ? '?status=' + status : '';
      const res = await API.get('/api/incidents' + query);
      state.incidents = res.data.incidents || [];
      renderIncidents();
      loadIncidentsInto($('#dispatchIncident'), true);
      loadIncidentsInto($('#hospitalIncident'), true);
    } catch (err) { toast(err.message, 'error'); }
  }

  async function loadIncidentsInto(select, activeOnly) {
    if (!select) return;
    try {
      const res = await API.get('/api/incidents?status=ACTIVE');
      const list = (res.data.incidents || []).filter(i => !activeOnly || i.status === 'ACTIVE');
      const current = select.value;
      select.innerHTML = list.length
        ? list.map(i => `<option value="${i.id}">#${i.id} ${i.type} @ ${i.locationName} (${i.severity})</option>`).join('')
        : '<option value="">No active incidents</option>';
      if (current) select.value = current;
    } catch (e) { /* ignore */ }
  }

  async function loadUnits() {
    try {
      const res = await API.get('/api/emergency/units');
      state.units = res.data.units || [];
      renderUnits();
    } catch (err) { toast(err.message, 'error'); }
  }

  async function loadHospitals() {
    try {
      const res = await API.get('/api/hospitals');
      state.hospitals = res.data.hospitals || [];
      renderHospitals();
    } catch (err) { toast(err.message, 'error'); }
  }

  /* ---------------- routes ---------------- */
  MapRenderer.onNodeClick((node) => {
    if (!state.pendingNodeId) {
      state.pendingNodeId = node.id;
      $('#sourceSelect').value = node.id;
      MapRenderer.setSelected(node.id);
      toast('Source set: ' + node.name + '. Click destination.');
    } else {
      $('#destSelect').value = node.id;
      MapRenderer.setSelected(node.id);
      state.pendingNodeId = null;
      toast('Destination set: ' + node.name);
    }
  });

  $('#swapBtn').addEventListener('click', () => {
    const s = $('#sourceSelect').value;
    $('#sourceSelect').value = $('#destSelect').value;
    $('#destSelect').value = s;
  });

  $('#computeBtn').addEventListener('click', computeRoutes);

  async function computeRoutes() {
    const sourceId = Number($('#sourceSelect').value);
    const destinationId = Number($('#destSelect').value);
    if (!sourceId || !destinationId) { toast('Pick source and destination', 'error'); return; }
    try {
      const res = await API.post('/api/routes/compute', { sourceId, destinationId });
      renderRoutes(res.data.routes);
    } catch (err) { toast(err.message, 'error'); }
  }

  function renderRoutes(routes) {
    const host = $('#routeResults');
    if (!routes || !routes.length) { host.innerHTML = '<div class="empty">No routes</div>'; return; }
    host.innerHTML = '';
    routes.forEach((route, index) => {
      const card = document.createElement('div');
      card.className = 'route-card' + (index === 0 ? ' selected' : '');
      const title = { FASTEST: 'Fastest', SHORTEST: 'Shortest', LOW_TRAFFIC: 'Low traffic' }[route.preference];
      if (!route.found) {
        card.innerHTML = `<h3>${title}</h3><div class="empty">No traversable path — network blocked</div>`;
      } else {
        card.innerHTML = `
          <h3><span>${title}</span><span class="badge" style="border-color:#2f81f7;color:#79b8ff">${route.summary}</span></h3>
          <div class="route-stats">
            <div><b>${route.distanceKm.toFixed(1)}</b>km</div>
            <div><b>${Math.ceil(route.timeMinutes)}</b>min</div>
            <div><b>${Math.round(route.avgSpeedKmh)}</b>km/h avg</div>
            <div><b>${route.roadIds.length}</b>segments</div>
          </div>
          <div>${route.nodeNames.map(n => `<span class="path-pill">${n}</span>`).join('')}</div>`;
        card.addEventListener('click', () => {
          $$('.route-card').forEach(c => c.classList.remove('selected'));
          card.classList.add('selected');
          MapRenderer.highlightRoute(route.nodeIds, route.roadIds);
        });
      }
      host.appendChild(card);
    });
    const best = routes.find(r => r.found);
    if (best) MapRenderer.highlightRoute(best.nodeIds, best.roadIds);
  }

  /* ---------------- incidents ---------------- */
  $('#incidentForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    const form = Object.fromEntries(new FormData(e.target).entries());
    const payload = {
      type: form.type,
      severity: form.severity,
      description: form.description,
      locationId: Number(form.locationId),
      affectedRoadId: form.affectedRoadId ? Number(form.affectedRoadId) : null
    };
    try {
      const res = await API.post('/api/incidents', payload);
      toast('Incident #' + res.data.id + ' reported — traffic updated', 'ok');
      e.target.reset();
      fillNodeSelects();
      loadIncidents();
      loadRoads();
    } catch (err) { toast(err.message, 'error'); }
  });

  $('#refreshIncidents').addEventListener('click', loadIncidents);
  $('#incidentFilter').addEventListener('change', loadIncidents);

  function renderIncidents() {
    const host = $('#incidentList');
    if (!state.incidents.length) {
      host.innerHTML = '<div class="empty">No incidents recorded</div>';
      return;
    }
    host.innerHTML = '';
    state.incidents.forEach(inc => {
      const row = document.createElement('div');
      row.className = 'item';
      const road = inc.affectedRoadName ? 'Road: ' + inc.affectedRoadName : 'Road: auto';
      const unit = inc.assignedUnitName ? 'Unit: ' + inc.assignedUnitName : 'Unit: unassigned';
      row.innerHTML = `
        <div>
          <div class="title">#${inc.id} ${inc.type} <span class="badge ${inc.severity}">${inc.severity}</span>
            <span class="badge ${inc.status}">${inc.status}</span></div>
          <div class="meta">@ ${inc.locationName} · ${road} · ${unit}<br>
            ${inc.description || 'No description'} · by ${inc.reportedBy || 'system'}</div>
        </div>
        <div>${inc.status === 'ACTIVE' && canWriteIncidents()
          ? `<button class="btn sm" data-resolve="${inc.id}">Resolve</button>` : ''}</div>`;
      host.appendChild(row);
    });
    host.querySelectorAll('[data-resolve]').forEach(btn => btn.addEventListener('click', async () => {
      try {
        await API.post(`/api/incidents/${btn.dataset.resolve}/resolve`);
        toast('Incident resolved — traffic restored', 'ok');
        loadIncidents();
        loadRoads();
      } catch (err) { toast(err.message, 'error'); }
    }));
  }

  /* ---------------- emergency ---------------- */
  $('#calcEtaBtn').addEventListener('click', calcEtas);
  $('#autoDispatchBtn').addEventListener('click', () => dispatch());

  async function calcEtas() {
    const incidentId = $('#dispatchIncident').value;
    if (!incidentId) { toast('Select an incident', 'error'); return; }
    try {
      const res = await API.get('/api/emergency/etas?incidentId=' + incidentId);
      renderEtas(res.data, Number(incidentId));
    } catch (err) { toast(err.message, 'error'); }
  }

  function renderEtas(etas, incidentId) {
    const host = $('#etaTable');
    if (!etas.length) { host.innerHTML = '<div class="empty">No units</div>'; return; }
    host.innerHTML = '';
    etas.forEach(eta => {
      const row = document.createElement('div');
      row.className = 'item';
      row.innerHTML = `
        <div>
          <div class="title">${eta.unitName} <span class="badge ${eta.status}">${eta.status}</span></div>
          <div class="meta">${eta.unitType} · ETA ${eta.etaMinutes ? eta.etaMinutes.toFixed(1) + ' min' : '—'}
            · ${eta.distanceKm ? eta.distanceKm.toFixed(1) + ' km' : '—'}</div>
        </div>
        <button class="btn sm ${eta.eligible ? 'danger' : ''}" ${eta.eligible ? '' : 'disabled'}>Dispatch</button>`;
      if (eta.eligible) {
        row.querySelector('button').addEventListener('click', () => dispatch(eta.unitId, incidentId));
      }
      host.appendChild(row);
    });
  }

  async function dispatch(unitId, incidentId) {
    const id = incidentId || $('#dispatchIncident').value;
    if (!id) { toast('Select an incident', 'error'); return; }
    try {
      const res = await API.post('/api/emergency/dispatch', { incidentId: Number(id), unitId: unitId || null });
      toast(res.data.message, 'ok');
      loadUnits();
      loadIncidents();
      calcEtas();
    } catch (err) { toast(err.message, 'error'); }
  }

  function renderUnits() {
    const host = $('#unitList');
    if (!host) return;
    if (!state.units.length) { host.innerHTML = '<div class="empty">No units</div>'; return; }
    host.innerHTML = '';
    state.units.forEach(u => {
      const row = document.createElement('div');
      row.className = 'item';
      row.innerHTML = `
        <div>
          <div class="title">${u.name} <span class="badge ${u.status}">${u.status}</span></div>
          <div class="meta">${u.type} · at ${u.currentLocationName} · call ${u.phone || '—'}</div>
        </div>
        <span>${u.status === 'BUSY' ? 'On duty' : 'Ready'}</span>`;
      host.appendChild(row);
    });
  }

  /* ---------------- hospitals ---------------- */
  $('#recommendBtn').addEventListener('click', async () => {
    const incidentId = $('#hospitalIncident').value;
    const fromNodeId = $('#hospitalNode').value;
    const minBeds = Number($('#minBeds').value || 1);
    const params = new URLSearchParams();
    if (incidentId) params.set('incidentId', incidentId);
    else if (fromNodeId) params.set('fromNodeId', fromNodeId);
    params.set('minBeds', minBeds);
    try {
      const res = await API.get('/api/hospitals/recommend?' + params.toString());
      renderRecommendations(res.data);
    } catch (err) { toast(err.message, 'error'); }
  });

  function renderRecommendations(data) {
    const host = $('#hospitalResults');
    host.innerHTML = '';
    host.innerHTML = `<div class="empty">From ${data.fromNodeName}</div>`;
    data.recommendations.forEach(rec => {
      const row = document.createElement('div');
      row.className = 'item';
      row.innerHTML = `
        <div>
          <div class="title">#${rec.rank} ${rec.hospital.name}
            ${rec.hospital.traumaCenter ? '<span class="badge MEDIUM">TRAUMA</span>' : ''}</div>
          <div class="meta">${rec.reason} · ${rec.distanceKm.toFixed(1)} km</div>
        </div>
        <b>${rec.etaMinutes.toFixed(0)} min</b>`;
      host.appendChild(row);
    });
  }

  function renderHospitals() {
    const host = $('#hospitalList');
    if (!host) return;
    host.innerHTML = state.hospitals.map(h => `
      <div class="item">
        <div>
          <div class="title">${h.name} ${h.traumaCenter ? '<span class="badge MEDIUM">TRAUMA</span>' : ''}</div>
          <div class="meta">at ${h.locationName} · ${h.bedsAvailable}/${h.bedsTotal} beds free</div>
        </div>
        <span class="badge ${h.bedsAvailable > 0 ? 'AVAILABLE' : 'OFFLINE'}">${h.bedsAvailable > 0 ? 'OPEN' : 'FULL'}</span>
      </div>`).join('') || '<div class="empty">No hospitals</div>';
  }

  /* ---------------- traffic ---------------- */
  $('#refreshTraffic').addEventListener('click', loadRoads);

  function renderRoadTable() {
    const tbody = $('#roadTable tbody');
    const canEdit = ['ADMIN', 'TRAFFIC_OPERATOR'].includes(API.user().role);
    tbody.innerHTML = state.roads.map(r => `
      <tr>
        <td>${r.name}</td>
        <td>${r.fromNodeName}</td>
        <td>${r.toNodeName}</td>
        <td>${r.distanceKm}</td>
        <td>${r.baseSpeedKmh} km/h</td>
        <td>${r.travelTimeMinutes.toFixed(1)} min</td>
        <td>${canEdit
          ? `<select data-road="${r.id}" data-kind="traffic">
              ${['FREE_FLOW','MODERATE','HEAVY','GRIDLOCK'].map(t =>
                `<option ${t === r.trafficLevel ? 'selected' : ''}>${t}</option>`).join('')}
             </select>`
          : `<span class="badge">${r.trafficLevel}</span>`}</td>
        <td>${canEdit
          ? `<select data-road="${r.id}" data-kind="status">
              ${['OPEN','BLOCKED','CLOSED'].map(t =>
                `<option ${t === r.status ? 'selected' : ''}>${t}</option>`).join('')}
             </select>`
          : `<span class="badge ${r.status === 'OPEN' ? 'AVAILABLE' : 'OFFLINE'}">${r.status}</span>`}</td>
      </tr>`).join('');

    tbody.querySelectorAll('select').forEach(sel => sel.addEventListener('change', async () => {
      const roadId = sel.dataset.road;
      const body = sel.dataset.kind === 'traffic'
        ? { trafficLevel: sel.value } : { status: sel.value };
      try {
        await API.patch(`/api/network/roads/${roadId}/traffic?trafficLevel=${sel.dataset.kind === 'traffic' ? body.trafficLevel : ''}&status=${sel.dataset.kind === 'status' ? body.status : ''}`);
        toast('Road updated', 'ok');
        loadRoads();
      } catch (err) { toast(err.message, 'error'); }
    }));
  }

  /* ---------------- admin ---------------- */
  async function loadAdmin() {
    try {
      const res = await API.get('/api/admin/users');
      const tbody = $('#userTable tbody');
      tbody.innerHTML = (res.data.users || []).map(u => `
        <tr>
          <td>${u.id}</td>
          <td>${u.username}</td>
          <td>${u.email}</td>
          <td>
            <select data-user="${u.id}">
              ${['USER','TRAFFIC_OPERATOR','EMERGENCY_OPERATOR','ADMIN'].map(r =>
                `<option ${r === u.role ? 'selected' : ''}>${r}</option>`).join('')}
            </select>
          </td>
          <td>${u.enabled ? 'active' : 'disabled'}</td>
        </tr>`).join('');
      tbody.querySelectorAll('select').forEach(sel => sel.addEventListener('change', async () => {
        try {
          await API.post(`/api/admin/users/${sel.dataset.user}/role`, { role: sel.value });
          toast('Role updated', 'ok');
        } catch (err) { toast(err.message, 'error'); }
      }));
      fillNodeSelect($('#unitStation'));
    } catch (err) { toast(err.message, 'error'); }
  }

  $('#operatorForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    const data = Object.fromEntries(new FormData(e.target).entries());
    try {
      await API.post('/api/admin/operators', data);
      toast('Operator created', 'ok');
      e.target.reset();
      loadAdmin();
    } catch (err) { toast(err.message, 'error'); }
  });

  $('#unitForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    const data = Object.fromEntries(new FormData(e.target).entries());
    data.stationNodeId = Number(data.stationNodeId);
    try {
      await API.post('/api/emergency/units', data);
      toast('Unit added', 'ok');
      e.target.reset();
      fillNodeSelect($('#unitStation'));
      loadUnits();
    } catch (err) { toast(err.message, 'error'); }
  });

  /* ---------------- websocket ---------------- */
  function connectSocket() {
    const proto = location.protocol === 'https:' ? 'wss' : 'ws';
    const socket = new WebSocket(`${proto}://${location.host}/ws`);
    socket.onopen = () => $('#liveDot').classList.remove('off');
    socket.onclose = () => $('#liveDot').classList.add('off');
    socket.onmessage = (event) => {
      let frame;
      try { frame = JSON.parse(event.data); } catch (e) { return; }
      if (frame.type === 'INCIDENT') {
        toast('Live update: incident #' + (frame.payload.id || '') + ' ' + (frame.payload.status || 'changed'));
        loadIncidents();
      } else if (frame.type === 'TRAFFIC') {
        toast('Traffic update: ' + (frame.payload.roadName || 'road') +
          ' → ' + (frame.payload.trafficLevel || ''));
        loadRoads();
      } else if (frame.type === 'ROUTE_REFRESH') {
        MapRenderer.clearRoute();
      } else if (frame.type === 'UNIT') {
        loadUnits();
      }
    };
    setInterval(() => {
      if (socket.readyState === WebSocket.CLOSED) connectSocket();
    }, 5000);
  }

  /* ---------------- boot ---------------- */
  if (API.token() && API.user()) {
    API.get('/api/auth/me')
      .then(() => showApp())
      .catch(() => showAuth('Session expired. Sign in again.'));
  } else {
    showAuth();
  }
})();
