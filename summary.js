const routeID = [];
const routeName = [];
const routeDays = [];
const routeTrips = [];
const routeTimes = [];
const tripHeadsigns = [];
const tripIds = [];
const allStopsMap = {};
const fullTripStopIds = [];
const fullTripStopTimes = [];

// Performance Caches & Indexing
const tripIndexMap = new Map(); // tripId -> index in global arrays
const tripStatsCache = new Map(); // tripId -> { distance, duration, speed }

var agency = "no data";

function funct() {
  const parsedUrl = new URL(document.URL);
  agency = parsedUrl.searchParams.get('agency');
  
  const testAgencyUrl = `https://localtransit.app/routes/${agency}_routes.txt`;

  fetch(testAgencyUrl)
    .then(r => r.text())
    .then((text) => {
      const agencyUrlFile = text.split("\n").filter(Boolean);

      for (let i = 0; i < agencyUrlFile.length; i++) {
        let data = agencyUrlFile[i];
        routeID.push(data.substring(0, data.indexOf(";")));
        data = data.substr(data.indexOf(";") + 1);

        routeName.push(data.substring(0, data.indexOf(";")));
        data = data.substr(data.indexOf(";") + 1);

        let dataTrips = data.substr(1, data.indexOf("]") - 1);
        routeTrips.push(dataTrips.split(", "));
        data = data.substr(data.indexOf(";") + 1);

        let dataTimes = data.substr(1, data.indexOf("]") - 1);
        routeTimes.push(dataTimes.split(", "));
        data = data.substr(data.indexOf(";") + 2);
        data = data.substr(1, data.length - 3);
        routeDays.push(data.split("], ["));
      }

      loadTripsAndStops();
    });
}

function loadTripsAndStops() {
  const tripsUrl = `https://localtransit.app/trips/${agency}_trips.txt`;
  const stopUrl = `https://localtransit.app/stops/${agency}_stops.txt`;

  Promise.all([
    fetch(tripsUrl).then(r => r.text()),
    fetch(stopUrl).then(r => r.text())
  ]).then(([tripsText, stopsText]) => {
    // 1. Parse Stops
    const stopLines = stopsText.split("\n");
    for (let i = 0; i < stopLines.length; i++) {
      const line = stopLines[i];
      if (!line) continue;
      const parts = line.split(",");
      if (parts.length > 3) {
        allStopsMap[parts[0]] = { lat: parts[2], lon: parts[3] };
      }
    }

    // 2. Parse Trips & Create O(1) Index Map
    const tripsLines = tripsText.split("\n");
    for (let i = 0; i < tripsLines.length; i++) {
      const line = tripsLines[i];
      if (!line) continue;

      let semiIdx1 = line.indexOf(";");
      const tId = line.substring(0, semiIdx1);
      
      let rest = line.substring(semiIdx1 + 1);
      semiIdx1 = rest.indexOf(";"); // skip route_id
      rest = rest.substring(semiIdx1 + 1);

      semiIdx1 = rest.indexOf(";");
      const headsign = rest.substring(0, semiIdx1);
      rest = rest.substring(semiIdx1 + 1);

      semiIdx1 = rest.indexOf(";"); // skip days
      rest = rest.substring(semiIdx1 + 1);

      let bracketClose = rest.indexOf("]");
      let dataIds = rest.substring(1, bracketClose);
      rest = rest.substring(bracketClose + 3);

      let dataTimes = rest.substring(0, rest.length - 1);

      const index = tripIds.length;
      tripIds.push(tId);
      tripHeadsigns.push(headsign);
      fullTripStopIds.push(dataIds.split(", "));
      fullTripStopTimes.push(dataTimes.split(", "));

      tripIndexMap.set(tId, index);
    }

    buildAllRoutesTableAsync();
  });
}

function getCachedTripStats(l) {
  const tId = tripIds[l];
  if (tripStatsCache.has(tId)) {
    return tripStatsCache.get(tId);
  }
  const stats = calculateTripStats(fullTripStopIds[l], fullTripStopTimes[l], allStopsMap);
  tripStatsCache.set(tId, stats);
  return stats;
}

function getMedian(arr) {
  if (!arr || arr.length === 0) return undefined;

  const sorted = [...arr].sort((a, b) => a - b);
  const mid = Math.floor(sorted.length / 2);

  return sorted.length % 2 !== 0 
    ? sorted[mid] 
    : (sorted[mid - 1] + sorted[mid]) / 2;
}

function buildAllRoutesTableAsync() {
  const sortedRouteIndices = Array.from(routeID.keys()).sort((a, b) => {
    return routeID[a].localeCompare(routeID[b], undefined, { numeric: true, sensitivity: 'base' });
  });

  let tableRowsHtml = "";
  let summaryRowsHtml = "";
  
  let currentIndex = 0;
  const CHUNK_SIZE = 15;

  function processChunk() {
    const nextIndex = Math.min(currentIndex + CHUNK_SIZE, sortedRouteIndices.length);

    for (let r = currentIndex; r < nextIndex; r++) {
      const i = sortedRouteIndices[r];
      const currentRoute = routeID[i];
      const currentRouteName = routeName[i];

      const timeBins = { 0: {}, 1: {}, 2: {}, 3: {}, 4: {}, 5: {}, 6: {} };
      const headsignStats = {};

      const currentRouteTrips = routeTrips[i];
      const currentRouteTimes = routeTimes[i];
      const currentRouteDays = routeDays[i];

      // Daytime statistical arrays for route summary table (6 AM - 8 PM)
      const daytimeDists = [];
      const daytimeSpeeds = [];

      for (let k = 0; k < currentRouteTrips.length; k++) {
        const tId = currentRouteTrips[k];
        const l = tripIndexMap.get(tId);

        if (l === undefined) continue;

        let timeStr = currentRouteTimes[k];
        let colIdx = timeStr.indexOf(':');
        let hrs = parseInt(timeStr.substring(0, colIdx), 10);
        let mins = parseInt(timeStr.substring(colIdx + 1), 10);
        let totalMins = (hrs * 60) + mins;
        let headsign = tripHeadsigns[l];

        if (!headsignStats[headsign]) {
          headsignStats[headsign] = { weeklyTrips: 0, totalMiles: 0 };
        }

        const tripDetails = getCachedTripStats(l);
        const tripDistance = parseFloat(tripDetails.distance) || 0;
        const tripSpeed = parseFloat(tripDetails.speed) || 0;
        const dayStr = currentRouteDays[k].toString();

        // Collect stats specifically for 6:00 AM - 8:00 PM (360 to 1200 mins)
        if (totalMins >= 360 && totalMins <= 1200) {
          daytimeDists.push(tripDistance);
          daytimeSpeeds.push(tripSpeed);
        }

        for (let j = 0; j < 7; j++) {
          if (dayStr.includes(j.toString())) {
            headsignStats[headsign].weeklyTrips += 1;
            headsignStats[headsign].totalMiles += tripDistance;

            if (totalMins >= 360 && totalMins <= 1200) {
              if (!timeBins[j][headsign]) timeBins[j][headsign] = [];
              timeBins[j][headsign].push(totalMins);
            }
          }
        }
      }

      // --- SUMMARY TABLE ROW BUILD ---
      if (daytimeDists.length > 0) {
        const medDist = getMedian(daytimeDists);
        const medSpeed = getMedian(daytimeSpeeds);

        const formattedMedDist = medDist !== undefined ? medDist.toFixed(2) : "N/A";
        const formattedMedSpeed = medSpeed !== undefined ? medSpeed.toFixed(2) : "N/A";

        summaryRowsHtml += `<tr>
          <td><a class="route" href="route.html?agency=${agency}&route=${currentRoute}">${currentRoute}</a></td>
          <td>${currentRouteName}</td>
          <td>${formattedMedDist} mi</td>
          <td>${formattedMedSpeed} mph</td>
        </tr>`;
      }

      // --- HEADSIGN FREQUENCY TABLE ROWS BUILD ---
      const routeHeadsigns = Object.keys(headsignStats).sort();
      if (routeHeadsigns.length === 0) continue;

      for (let h = 0; h < routeHeadsigns.length; h++) {
        const headsign = routeHeadsigns[h];
        const stats = headsignStats[headsign];
        
        const formattedMiles = stats.totalMiles.toLocaleString("en-US", {
          minimumFractionDigits: 2,
          maximumFractionDigits: 2
        });
        const formattedTrips = stats.weeklyTrips.toLocaleString("en-US");

        tableRowsHtml += `<tr><td><a class="route" href="route.html?agency=${agency}&route=${currentRoute}">${currentRoute}</a></td>`;
        tableRowsHtml += `<td>${headsign}</td>`;

        for (let j = 0; j < 7; j++) {
          let displayColor = "#CFCFCF";

          if (timeBins[j] && timeBins[j][headsign]) {
            let times = timeBins[j][headsign].sort((a, b) => a - b);
            let count = times.length;

            if (count > 1) {
              let span = times[times.length - 1] - times[0];
              let avgHeadway = Math.round(span / (count - 1));

              if (avgHeadway <= 7.5) displayColor = "#AE00C3";
              else if (avgHeadway <= 15) displayColor = "#00FFF7";
              else if (avgHeadway <= 30) displayColor = "#56FF34";
              else if (avgHeadway <= 60) displayColor = "#FCFF00";
              else if (avgHeadway <= 120) displayColor = "#FF0725";
              else displayColor = "#8D6726";
            } else if (count === 1) {
              displayColor = "#8D6726";
            }
          }

          tableRowsHtml += `<td><svg width="20" height="20"><rect width="20" height="20" style="fill:${displayColor}"/></svg></td>`;
        }

        tableRowsHtml += `<td>${formattedTrips}</td><td>${formattedMiles} mi</td></tr>`;
      }
    }

    currentIndex = nextIndex;

    if (currentIndex < sortedRouteIndices.length) {
      setTimeout(processChunk, 0);
    } else {
      // Inject both tables when async loading completes
      document.getElementById("table").innerHTML += tableRowsHtml;
      
      const summaryTableElem = document.getElementById("table2");
      if (summaryTableElem) {
        summaryTableElem.innerHTML += summaryRowsHtml;
      }
    }
  }

  processChunk();
}