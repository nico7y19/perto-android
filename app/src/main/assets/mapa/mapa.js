/* Mapa local: textos do banco são inseridos como texto, nunca como HTML. */
(function () {
  "use strict";
  const map = L.map("mapa").setView([-22.9797, -49.8706], 13);
  const tiles = L.tileLayer("https://tile.openstreetmap.org/{z}/{x}/{y}.png", {
    maxZoom: 19, updateWhenIdle: true, keepBuffer: 0,
    attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
  }).addTo(map);
  tiles.on("tileerror", () => { document.getElementById("falha").hidden = false; });
  tiles.on("tileload", () => { document.getElementById("falha").hidden = true; });
  let layer = L.layerGroup().addTo(map), selected = null, reference = null;
  function point(lat, lon) { return L.latLng(lat, lon).wrap(); }
  window.perto = {
    centro: function (lat, lon) { map.setView(point(lat, lon), 14); },
    selecionar: function (lat, lon) {
      if (selected) map.removeLayer(selected);
      selected = L.circleMarker(point(lat, lon), {radius: 10, color: "#ffffff", fillColor: "#b397ff", fillOpacity: 1, weight: 3}).addTo(map);
    },
    limparSelecao: function () { if (selected) map.removeLayer(selected); selected = null; },
    referencia: function (lat, lon, radius) {
      if (reference) map.removeLayer(reference);
      reference = L.layerGroup([L.circle(point(lat, lon), {radius: radius * 1000, color: "#2768cb", fillOpacity: 0.06}), L.circleMarker(point(lat, lon), {radius: 6, color: "#2768cb", fillOpacity: 1})]).addTo(map);
    },
    eventos: function (events) {
      layer.clearLayers();
      events.forEach(e => {
        const popup = document.createElement("div"), title = document.createElement("strong"), info = document.createElement("p"), button = document.createElement("button");
        title.textContent = e.nome; info.textContent = e.info; button.textContent = "Ver detalhes";
        button.addEventListener("click", () => PertoAndroid.abrirEvento(e.id));
        popup.append(title, info, button);
        L.circleMarker(point(e.lat, e.lon), {bubblingMouseEvents: false, radius: 9, color: "#fff", fillColor: "#a78bfa", fillOpacity: 1, weight: 2}).bindPopup(popup).addTo(layer);
      });
    }
  };
  map.on("click", e => { const p=e.latlng.wrap(); PertoAndroid.selecionar(p.lat,p.lng); });
})();
