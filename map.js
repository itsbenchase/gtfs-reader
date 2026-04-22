const tripIds = [];
const tripRoute = [];
const tripHeadsign = [];
const tripDays = [];
const tripStopIds = [];
const tripStopTimes = [];

const stopId = [];
const stopName = [];
const stopLat = [];
const stopLon = [];

const cumLats = [];
const cumLons = [];

const currentStopIds = [];
const allStopsMap = {};
const fullTripStopIds = [];    // New: Stores the arrays of stop IDs
const fullTripStopTimes = [];  // New: Stores the arrays of stop times
const stopToTripsMap = {};
let bounds = new maplibregl.LngLatBounds();

const CHUNK_SIZE = 500; // How many stops to process per "breath"
let currentIndex = 0;
let allFeatures = [];
let stopFreq = [];
let map;

var result = "no data";
var agency = "no data";

function funct()
{
  var parsedUrl = new URL(document.URL);
  console.log(parsedUrl);

  var paramName2 = 'agency';
  agency = parsedUrl.searchParams.get(paramName2);

  // 1. Define the URL for your agency's trips file
  const testAgencyUrl = ("https://localtransit.app/" + agency + "_trips.txt");
  
  fetch(testAgencyUrl)
    .then(r => r.text())
    .then((text) => {
      const agencyUrlFile = text.split("\n");
      agencyUrlFile.pop(); // Remove the trailing empty line

      for (let i = 0; i < agencyUrlFile.length; i++) {
        let data = agencyUrlFile[i];

        // 2. Parse Trip ID (before first ;)
        tripIds.push(data.substring(0, data.indexOf(";")));
        data = data.substr(data.indexOf(";") + 1);

        // 3. Parse Route ID
        tripRoute.push(data.substring(0, data.indexOf(";")));
        data = data.substr(data.indexOf(";") + 1);

        // 4. Parse Headsign (between semicolons)
        tripHeadsign.push(data.substring(0, data.indexOf(";")));
        data = data.substr(data.indexOf(";") + 1);

        // 5. Parse days block
        var dataDays = data.substr(1, data.indexOf("]") - 1);
        tripDays.push(dataDays.split(", "));
        data = data.substr(data.indexOf(";") + 1);

        // 6. Parse Stop IDs [ID1, ID2, ID3...]
        // We find the brackets, strip them, and split into an array
        let dataIds = data.substr(1, data.indexOf("]") - 1);
        fullTripStopIds.push(dataIds.split(", "));
        data = data.substr(data.indexOf(";") + 2); // Move past the ]; 

        // 7. Parse Stop Times [08:00:00, 08:05:00...]
        // Final block in the line
        let dataTimes = data.substr(0, data.length - 1); // Strip the final ]
        fullTripStopTimes.push(dataTimes.split(", "));
      }

      // 8. Fetch stop coordinates before trying to calculate stats
      getStops(result); 
    });
}

function getStops(result) {
  const stopUrl = ("https://localtransit.app/" + agency + "_stops.txt");
  fetch(stopUrl)
    .then(r => r.text())
    .then((text) => {
      const stopUrlFile = text.split("\n");
      stopUrlFile.pop();

      for (let i = 0; i < stopUrlFile.length; i++) {
        var data = stopUrlFile[i].split(","); // Using split is cleaner for CSV
        stopId.push(data[0]);
        stopName.push(data[1]);
        stopLat.push(data[2]);
        stopLon.push(data[3]);
        
        // Map ID to Lat/Lon for the utility to use
        allStopsMap[data[0]] = { lat: parseFloat(data[2]), lon: parseFloat(data[3]) };
      }
      
      createStopMap();
    });
}

function createStopMap() {
    for (let l = 0; l < tripIds.length; l++) {
        // Iterate through all stops in this specific trip
        for (let k = 0; k < fullTripStopIds[l].length; k++) {
            let sId = fullTripStopIds[l][k];
            
            if (!stopToTripsMap[sId]) {
                stopToTripsMap[sId] = [];
            }
            // Store the trip index (l) and the stop's position in that trip (k)
            stopToTripsMap[sId].push({ tripIdx: l, stopPos: k });
        }
    }
    mapFunct();
}

function mapFunct()
{
    map = new maplibregl.Map({
        container: 'map',
        style: 'https://tiles.openfreemap.org/styles/positron'
    });

    map.on('load', () => {
        // Initialize empty source
        map.addSource('bus-stops', {
            'type': 'geojson',
            'data': { 'type': 'FeatureCollection', 'features': [] }
        });

        map.addLayer({
            'id': 'stops-layer',
            'type': 'circle',
            'source': 'bus-stops',
            'paint': {
                'circle-radius': 4,
                'circle-stroke-width': 1,
                'circle-stroke-color': '#000000',
                'circle-color': [
                    'step',
                    ['get', 'freq'],
                    '#cfcfcf',      // Default
                    0.5, '#ae00c3',
                    7.5, '#00fff7',
                    15, '#56ff34',
                    30, '#fcff00',
                    60, '#ff0725',
                    120, '#8d6726'
                ]
            }
        });
      processChunks();
  });

  map.on('click', 'stops-layer', (e) => {
    const coordinates = e.features[0].geometry.coordinates.slice();
    const name = e.features[0].properties.name;
    const freq = e.features[0].properties.freq;
    const routes = e.features[0].properties.routes;
    const id = e.features[0].properties.id;

    // 1. Split the string back into an array
    // We split by ' / ' because that's how we joined it in processChunks
    const routeList = routes.split(' / ');

    // 2. Map each route to an HTML link string
    const routeLinks = routeList.map(route => {
        const safeRoute = encodeURIComponent(route);
        // Ensure you have access to the 'agency' variable here
        return `<a class=route href="route.html?agency=${agency}&route=${safeRoute}" class="popup-link">${route}</a>`;
    }).join(' / ');

    new maplibregl.Popup()
        .setLngLat(coordinates)
        .setHTML(`
            <div class="stop-popup">
                <a class="stop-name" href="stop.html?agency=${agency}&stop=${id}">
                    <strong>${name}</strong>
                </a>
                <div class="route-list">
                    ${routeLinks}
                </div>
            </div>
        `)
        .addTo(map);
});
}

function processChunks() {
    const end = Math.min(currentIndex + CHUNK_SIZE, stopId.length);

    for (let i = currentIndex; i < end; i++) {
        if (!stopId[i]) continue;

        // check if stop has actual location
        const lon = parseFloat(stopLon[i]);
        const lat = parseFloat(stopLat[i]);

        if (isNaN(lon) || isNaN(lat)) {
            console.warn(`Skipping stop ${stopId[i]} due to invalid coordinates: [${stopLon[i]}, ${stopLat[i]}]`);
            continue;
        }

        // Accessing stopFreq now works because it's in the outer scope
        const freqData = findTrips(stopId[i]) || { trips: 0, routes: [] };
        
        allFeatures.push({
            'type': 'Feature',
            'properties': {
                'id': stopId[i],
                'name': stopName[i],
                'freq': freqData.trips, // Access 'trips' property
                'routes': (freqData.routes && Array.isArray(freqData.routes)) 
                           ? freqData.routes.join(' / ') 
                           : "No Routes"
            },
            'geometry': {
                'type': 'Point',
                'coordinates': [stopLon[i], stopLat[i]]
            }
        });
        bounds.extend([stopLon[i], stopLat[i]]);
    }

    currentIndex = end;

    // Update map (map variable is now accessible here)
    map.getSource('bus-stops').setData({
        'type': 'FeatureCollection',
        'features': allFeatures
    });

    if (currentIndex < stopId.length) {
        requestAnimationFrame(processChunks);
    } else {
        // OPTIONAL: Sort once all points are loaded to handle your Z-index requirement
        allFeatures.sort((a, b) => {
            const freqA = a.properties.freq || 9999; // Treat 0 or null as a huge number
            const freqB = b.properties.freq || 9999;
            
            // Sort descending (Large numbers first, small numbers/high-freq last)
            // This puts the 9999s (gray circles) at the start of the array (bottom of map)
            return freqB - freqA; 
        });
        map.fitBounds(bounds, {
            padding: 40,      // Pixels of space around the edges
            maxZoom: 15,     // Don't zoom in too far if there's only 1 stop
            duration: 2000   // Smooth animation (in milliseconds)
        });
        map.getSource('bus-stops').setData({
            'type': 'FeatureCollection',
            'features': allFeatures
        });
    }
}

function findTrips(result) {
    let stopRoutes = [];
    let timeBins = { 0: {}, 1: {}, 2: {}, 3: {}, 4: {}, 5: {}, 6: {} };
    let weekdayAvg = 0;

    // INSTANT LOOKUP: Instead of looping tripIds.length
    const relevantTripRefs = stopToTripsMap[result] || [];

    for (let ref of relevantTripRefs) {
        let l = ref.tripIdx;
        let k = ref.stopPos;

        // 1. Routes
        let route = tripRoute[l];
        if (!stopRoutes.includes(route)) stopRoutes.push(route);

        // 2. Timing
        let timeStr = fullTripStopTimes[l][k];
        let [hrs, mins] = timeStr.split(':').map(Number);
        let totalMins = (hrs * 60) + mins;
        let headsign = tripHeadsign[l];

        // 3. Frequency logic (06:00 to 20:00)
        if (totalMins >= 360 && totalMins <= 1200) {
            let days = tripDays[l].toString();
            for (let j = 0; j < 7; j++) {
                if (days.includes(j.toString())) {
                    if (!timeBins[j][headsign]) timeBins[j][headsign] = [];
                    timeBins[j][headsign].push(totalMins);
                }
            }
        }
    }

    // 4. Calculate Thursday (j=3) Headway
    let thursTimes = [];
    for (let h in timeBins[3]) {
        thursTimes.push(...timeBins[3][h]);
    }

    if (thursTimes.length > 1) {
        thursTimes.sort((a, b) => a - b);
        let span = thursTimes[thursTimes.length - 1] - thursTimes[0];
        weekdayAvg = Math.max(1, Math.round(span / (thursTimes.length - 1)));
    }

    return { trips: weekdayAvg, routes: stopRoutes };
}