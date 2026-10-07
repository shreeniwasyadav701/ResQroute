/* SVG renderer for the road network graph. */
const MapRenderer = (() => {
  const SVG_NS = 'http://www.w3.org/2000/svg';
  const svg = () => document.getElementById('map');

  let intersections = [];
  let roads = [];
  let routeRoadIds = [];
  let routeNodeIds = [];
  let selectedNodeId = null;
  let nodeClickHandler = null;

  const byId = (list) => list.reduce((acc, item) => (acc[item.id] = item, acc), {});
  const pos = {};
  const nodesById = {};
  const roadsById = {};

  function trafficColor(road) {
    if (road.status === 'BLOCKED' || road.status === 'CLOSED') return '#57606a';
    return { FREE_FLOW: '#2ea043', MODERATE: '#d29922', HEAVY: '#e8622c', GRIDLOCK: '#f85149' }[road.trafficLevel] || '#2ea043';
  }

  function setData(nodeList, roadList) {
    intersections = nodeList || [];
    roads = roadList || [];
    intersections.forEach(n => { pos[n.id] = n; nodesById[n.id] = n; });
    roads.forEach(r => { roadsById[r.id] = r; });
    render();
  }

  function updateRoads(roadList) {
    roads = roadList || [];
    roads.forEach(r => { roadsById[r.id] = r; });
    render();
  }

  function highlightRoute(nodeIds, roadIds) {
    routeNodeIds = nodeIds || [];
    routeRoadIds = roadIds || [];
    render();
  }

  function clearRoute() {
    routeNodeIds = [];
    routeRoadIds = [];
    render();
  }

  function setSelected(nodeId) {
    selectedNodeId = nodeId;
    render();
  }

  function onNodeClick(fn) { nodeClickHandler = fn; }

  function el(tag, attrs, text) {
    const node = document.createElementNS(SVG_NS, tag);
    Object.entries(attrs || {}).forEach(([k, v]) => node.setAttribute(k, v));
    if (text) node.textContent = text;
    return node;
  }

  function render() {
    const host = svg();
    if (!host) return;
    host.innerHTML = '';

    const defs = el('defs');
    defs.innerHTML = '<marker id="arrow" viewBox="0 0 10 10" refX="6" refY="5" markerWidth="5" markerHeight="5" orient="auto"><path d="M0,0 L10,5 L0,10 z" fill="#2f81f7"/></marker>';
    host.appendChild(defs);

    const routeRoadSet = new Set(routeRoadIds.map(Number));

    // base edges
    roads.forEach(road => {
      const a = pos[road.fromNodeId];
      const b = pos[road.toNodeId];
      if (!a || !b) return;
      const blocked = road.status !== 'OPEN';
      const line = el('line', {
        x1: a.x, y1: a.y, x2: b.x, y2: b.y,
        stroke: trafficColor(road),
        'stroke-width': 7,
        'stroke-linecap': 'round',
        opacity: blocked ? 0.55 : 0.9
      });
      if (blocked) line.setAttribute('stroke-dasharray', '8 7');
      const title = el('title');
      title.textContent = `${road.name} — ${road.distanceKm} km, ${road.travelTimeMinutes.toFixed(1)} min, ${road.trafficLevel}/${road.status}`;
      line.appendChild(title);
      host.appendChild(line);

      if (road.oneway) {
        const mx = (a.x + b.x) / 2, my = (a.y + b.y) / 2;
        host.appendChild(el('circle', { cx: mx, cy: my, r: 3, fill: '#e6edf3' }));
      }
    });

    // highlighted route
    if (routeNodeIds.length > 1) {
      const points = routeNodeIds.map(id => pos[id]).filter(Boolean).map(p => `${p.x},${p.y}`).join(' ');
      const halo = el('polyline', { points, fill: 'none', stroke: '#2f81f7', 'stroke-width': 14,
        'stroke-linecap': 'round', 'stroke-linejoin': 'round', opacity: 0.28 });
      host.appendChild(halo);
      const line = el('polyline', { points, fill: 'none', stroke: '#58a6ff', 'stroke-width': 5,
        'stroke-linecap': 'round', 'stroke-linejoin': 'round', 'marker-end': 'url(#arrow)' });
      line.setAttribute('stroke-dasharray', '14 8');
      const anim = el('animate', { attributeName: 'stroke-dashoffset', from: '44', to: '0', dur: '1.1s', repeatCount: 'indefinite' });
      line.appendChild(anim);
      host.appendChild(line);
    }

    // nodes
    intersections.forEach(n => {
      const isStart = routeNodeIds.length && routeNodeIds[0] === n.id;
      const isEnd = routeNodeIds.length && routeNodeIds[routeNodeIds.length - 1] === n.id;
      const selected = selectedNodeId === n.id;
      const g = el('g', { style: 'cursor:pointer' });

      if (isStart || isEnd || selected) {
        g.appendChild(el('circle', { cx: n.x, cy: n.y, r: 16, fill: '#2f81f7', opacity: 0.22 }));
      }
      g.appendChild(el('circle', {
        cx: n.x, cy: n.y, r: 9,
        fill: isStart ? '#2ea043' : isEnd ? '#f85149' : '#0d1117',
        stroke: selected ? '#a371f7' : '#79b8ff',
        'stroke-width': selected ? 4 : 2.5
      }));
      const label = el('text', {
        x: n.x, y: n.y - 15, 'text-anchor': 'middle',
        fill: '#e6edf3', 'font-size': '11', 'font-weight': '600',
        style: 'paint-order:stroke; stroke:#0d1117; stroke-width:3px'
      }, n.name);
      g.appendChild(label);
      g.addEventListener('click', (e) => {
        e.stopPropagation();
        if (nodeClickHandler) nodeClickHandler(n);
      });
      host.appendChild(g);
    });
  }

  return { setData, updateRoads, highlightRoute, clearRoute, setSelected, onNodeClick, render, trafficColor, nodesById, roadsById };
})();
