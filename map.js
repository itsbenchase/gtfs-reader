// --- 1. GLOBAL STATE ---
let agencies = [];
let masterStops = []; // Unified list of all stops from all agencies
let stopToTripsMap = {}; // Master Index: { "agency_stopId": [{tripIdx, stopPos}, ...] }

// Global storage for trip data (we add a prefix to keep agencies distinct)
const tripData = {
    ids: [], routes: [], headsigns: [], days: [], 
    stopIds: [], stopTimes: []
};

let map;
let allFeatures = [];
let currentIndex = 0;
let bounds = new maplibregl.LngLatBounds();
const CHUNK_SIZE = 1000;

// --- 2. INITIALIZATION FLOW ---
async function init() {

    var parsedUrl = new URL(document.URL);
    console.log(parsedUrl);

    var paramName2 = 'agency';
    var agencyString = parsedUrl.searchParams.get(paramName2);
    
    if (agencyString == "all")
    {
        await getAllAgencies();
    }
    else { agencies = agencyString.split(","); } // agencies from URL as agency1,agency2,agency3

    // Loop through each agency and wait for data to load
    for (const agencyId of agencies) {
        console.log(`Loading ${agencyId}...`);
        await loadAgency(agencyId);
    }
    
    console.log("All data loaded. Indexing...");
    createMasterIndex();
    
    console.log("Starting Map...");
    mapFunct();
}

async function getAllAgencies()
{
    const agenciesTxt = ("agencies.txt"); // provide file location
    fetch(agenciesTxt)
      .then(r => r.text())
      .then((text) => {
        const agencyUrlFile = text.split("\n");
        agencyUrlFile.pop();

        for (let i = 0; i < agencyUrlFile.length; i++)
        {
          var data = agencyUrlFile[i];
          agencies.push(data.substring(0, data.indexOf(";")));
        }
      })
}

// --- 3. DATA LOADING ---
async function loadAgency(agencyId) {
    const tripUrl = `https://localtransit.app/trips/${agencyId}_trips.txt`;
    const stopUrl = `https://localtransit.app/stops/${agencyId}_stops.txt`;

    // Fetch Trips
    const tripText = await fetch(tripUrl).then(r => r.text());
    const tripLines = tripText.split("\n").filter(line => line.trim() !== "");
    
    const startIdx = tripData.ids.length; // Track where this agency starts in the global arrays

    tripLines.forEach(line => {
        let parts = line.split(";");
        tripData.ids.push(parts[0]);
        tripData.routes.push(parts[1]);
        tripData.headsigns.push(parts[2]);
        
        let days = parts[3].replace(/[\[\]]/g, "").split(", ");
        tripData.days.push(days);

        let sIds = parts[4].replace(/[\[\]]/g, "").split(", ");
        // IMPORTANT: Namespace the stop IDs so '101' from agency A doesn't collide with '101' from agency B
        tripData.stopIds.push(sIds.map(id => `${agencyId}_${id}`));

        let sTimes = parts[5].replace(/[\[\]]/g, "").split(", ");
        tripData.stopTimes.push(sTimes);
    });

    // Fetch Stops
    const stopText = await fetch(stopUrl).then(r => r.text());
    const stopLines = stopText.split("\n").filter(line => line.trim() !== "");

    stopLines.forEach(line => {
        const d = line.split(",");
        masterStops.push({
            fullId: `${agencyId}_${d[0]}`, // Namespaced ID
            shortId: d[0],                 // Original ID for links
            name: d[1],
            lat: parseFloat(d[2]),
            lon: parseFloat(d[3]),
            agency: agencyId
        });
    });
}

// --- 4. INDEXING ---
function createMasterIndex() {
    for (let l = 0; l < tripData.ids.length; l++) {
        for (let k = 0; k < tripData.stopIds[l].length; k++) {
            let sId = tripData.stopIds[l][k];
            if (!stopToTripsMap[sId]) stopToTripsMap[sId] = [];
            stopToTripsMap[sId].push({ tripIdx: l, stopPos: k });
        }
    }
}

// --- 5. MAP RENDERING ---
function mapFunct() {
    map = new maplibregl.Map({
        container: 'map',
        style: 'https://tiles.openfreemap.org/styles/positron'
    });

    map.on('load', () => {
        map.addSource('bus-stops', {
            'type': 'geojson',
            'data': { 'type': 'FeatureCollection', 'features': [] }
        });

        map.addLayer({
            'id': 'stops-layer',
            'type': 'circle',
            'source': 'bus-stops',
            'layout': {
                'circle-sort-key': ['case', ['==', ['get', 'freq'], 0], -1, ['-', 1000, ['get', 'freq']]]
            },
            'paint': {
                'circle-radius': 4,
                'circle-stroke-width': 1,
                'circle-stroke-color': '#000000',
                'circle-color': [
                    'step', ['get', 'freq'],
                    '#cfcfcf', 0.5, '#ae00c3', 7.5, '#00fff7', 15, '#56ff34', 30, '#fcff00', 60, '#ff0725', 120, '#8d6726'
                ]
            }
        });
        processChunks();
    });

    map.on('click', 'stops-layer', (e) => {
        const p = e.features[0].properties;
        const routeLinks = p.routes.split(' / ').map(r => 
            `<a class="route" href="route.html?agency=${p.agency}&route=${r}">${r}</a>`
        ).join(' / ');

        new maplibregl.Popup()
            .setLngLat(e.lngLat)
            .setHTML(`
                <div class="stop-popup">
                    <a class="stop-name" href="stop.html?agency=${p.agency}&stop=${p.id}">
                        <strong>${p.name}</strong>
                    </a>
                    <div style="font-size: 12px">${p.agency}</div>
                    <div class="route-list">${routeLinks}</div>
                </div>
            `).addTo(map);
    });
}

// --- 6. CHUNKING & LOGIC ---
function processChunks() {
    const end = Math.min(currentIndex + CHUNK_SIZE, masterStops.length);

    for (let i = currentIndex; i < end; i++) {
        const s = masterStops[i];
        if (isNaN(s.lon) || isNaN(s.lat)) continue;

        const freqData = findTrips(s.fullId);
        
        allFeatures.push({
            'type': 'Feature',
            'properties': {
                'id': s.shortId,
                'agency': s.agency,
                'name': s.name,
                'freq': freqData.trips,
                'routes': freqData.routes.join(' / ')
            },
            'geometry': {
                'type': 'Point',
                'coordinates': [s.lon, s.lat]
            }
        });
        bounds.extend([s.lon, s.lat]);
    }

    currentIndex = end;
    map.getSource('bus-stops').setData({
        'type': 'FeatureCollection',
        'features': allFeatures
    });

    if (currentIndex < masterStops.length) {
        requestAnimationFrame(processChunks);
    } else {
        allFeatures.sort((a, b) => (b.properties.freq || 9999) - (a.properties.freq || 9999));
        map.getSource('bus-stops').setData({ 'type': 'FeatureCollection', 'features': allFeatures });
        map.fitBounds(bounds, { padding: 40, duration: 2000 });
    }
}

function findTrips(fullId) {
    let stopRoutes = [];
    let uniqueTimes = new Set(); 
    const refs = stopToTripsMap[fullId] || [];

    for (let ref of refs) {
        const l = ref.tripIdx;
        const k = ref.stopPos;

        if (!stopRoutes.includes(tripData.routes[l])) {
            stopRoutes.push(tripData.routes[l]);
        }

        // Thursday filter
        if (tripData.days[l].includes("3")) {
            let timeStr = tripData.stopTimes[l][k];
            let [hrs, mins] = timeStr.split(':').map(Number);
            let totalMins = (hrs * 60) + mins;

            // Constrain to your specific window
            if (totalMins >= 360 && totalMins <= 1200) {
                uniqueTimes.add(totalMins);
            }
        }
    }

    let finalFreq = 0;
    // Convert Set to a sorted array to find First and Last
    let sortedTimes = Array.from(uniqueTimes).sort((a, b) => a - b);
    let tripCount = sortedTimes.length;

    if (tripCount > 1) {
        let firstBus = sortedTimes[0];
        let lastBus = sortedTimes[tripCount - 1];
        let span = lastBus - firstBus;

        // DIVISOR RULE: If there are 15 trips, there are 14 gaps between them.
        // If you divide by 15, your frequency "drifts" and becomes too fast.
        let gaps = tripCount - 1;
        finalFreq = Math.max(1, Math.round(span / gaps));
    } else if (tripCount === 1) {
        // If there is only one bus all day, the "span" is 0. 
        // We set this to a high number so it shows up as "Infrequent" (Brown/Red) 
        // rather than "No Service" (Gray).
        finalFreq = 999; 
    }

    return { trips: finalFreq, routes: stopRoutes };
}
// Start the whole process
init();