const routeID = [];
const routeName = [];
const routeDays = [];
const routeTrips = [];
const routeTimes = [];
const tripHeadsigns = [];
const tripIds = [];
// New global to store stop coordinates for the route page
const allStopsMap = {};
const fullTripStopIds = [];    // New: Stores the arrays of stop IDs
const fullTripStopTimes = [];  // New: Stores the arrays of stop times

var result = "no data";
var agency = "no data";

function funct()
{
  var parsedUrl = new URL(document.URL);
  console.log(parsedUrl);

  var paramName = 'route';
  result = parsedUrl.searchParams.get(paramName);
  var paramName2 = 'agency';
  agency = parsedUrl.searchParams.get(paramName2);
  
  const testAgencyUrl = ("routes/" + agency + "_routes.txt"); // provide file location
    fetch(testAgencyUrl)
      .then(r => r.text())
      .then((text) => {
        const agencyUrlFile = text.split("\n");
        agencyUrlFile.pop();

        for (let i = 0; i < agencyUrlFile.length; i++)
        {
          var data = agencyUrlFile[i];
          routeID.push(data.substring(0, data.indexOf(";")));
          data = data.substr(data.indexOf(";") + 1);

          routeName.push(data.substring(0, data.indexOf(";")));
          data = data.substr(data.indexOf(";") + 1);

          var dataTrips = data.substr(1, data.indexOf("]") - 1);
          routeTrips.push(dataTrips.split(", "));
          data = data.substr(data.indexOf(";") + 1);

          var dataTimes = data.substr(1, data.indexOf("]") - 1);
          routeTimes.push(dataTimes.split(", "));
          data = data.substr(data.indexOf(";") + 2);
          data = data.substr(1, data.length - 3);
          routeDays.push(data.split("], ["));
        }

        getTrips(result)
      })
}

function getStops()
{
    const stopUrl = `stops/${agency}_stops.txt`;
    fetch(stopUrl).then(r => r.text()).then(text => {
        text.split("\n").forEach(line => {
            const parts = line.split(",");
            if (parts.length > 3) {
                allStopsMap[parts[0]] = { lat: parts[2], lon: parts[3] };
            }
        });
        findRoute(result); // Only run findRoute once stops are loaded
    });
}

function getTrips(result) {
  // 1. Define the URL for your agency's trips file
  const testAgencyUrl = ("trips/" + agency + "_trips.txt");
  
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

        // 3. Skip Route ID (already handled by routes.txt)
        data = data.substr(data.indexOf(";") + 1);

        // 4. Parse Headsign (between semicolons)
        tripHeadsigns.push(data.substring(0, data.indexOf(";")));
        data = data.substr(data.indexOf(";") + 1);

        // 5. Skip Days block
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

      // 8. CRITICAL STEP: Fetch stop coordinates before trying to calculate stats
      // This ensures allStopsMap is populated before findRoute() runs
      getStops(result); 
    });
}

function findRoute(result) {
  let dayCounts = [0, 0, 0, 0, 0, 0, 0];
  let dayNames = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"];

  // Storage for frequency: day_index -> headsign -> array of minute values
  let timeBins = {};

  // Setup the summary UI and time bins
  for (let j = 0; j < 7; j++) {
    document.getElementById("summary").innerHTML += ("<br><span id=trip-count-" + j + ">" + dayNames[j] + " Trips: 0</span><span id=stats-" + j + "></span><br>");
    timeBins[j] = {};
  }

  var maxDist = Number.MIN_VALUE;
  var minDist = Number.MAX_VALUE;
  var maxTime = Number.MIN_VALUE;
  var minTime = Number.MAX_VALUE;
  var maxSpeed = Number.MIN_VALUE;
  var minSpeed = Number.MAX_VALUE;

  let dists = [];
  let times = [];
  let speeds = [];

  for (let i = 0; i < routeID.length; i++) {
    if (routeID[i] == result) {
      // Set Route Header
      document.getElementById("route").innerHTML += (routeID[i] + ": " + routeName[i]);

      for (let k = 0; k < routeTrips[i].length; k++) {
        for (let l = 0; l < tripIds.length; l++) {
          if (tripIds[l] == routeTrips[i][k]) {
            
            // 1. GENERATE STATS
            // Use the data parsed in getTrips() and getStops()
            const tripDetails = calculateTripStats(fullTripStopIds[l], fullTripStopTimes[l], allStopsMap);
            const statsString = `${tripDetails.duration} min</td><td>${tripDetails.distance} mi</td><td>${tripDetails.speed} mph</td>`;

            // 2. PREPARE FREQUENCY DATA
            let timeStr = routeTimes[i][k]; // e.g., "08:30"
            let [hrs, mins] = timeStr.split(':').map(Number);
            let totalMins = (hrs * 60) + mins;
            let headsign = tripHeadsigns[l];

            // sort into stats if a daytime trip -- filtering out overnight time/speed outliers
            if (totalMins >= 360 && totalMins <= 1200)
            {
              if (tripDetails.distance > maxDist) { maxDist = tripDetails.distance; }
              if (tripDetails.distance < minDist) { minDist = tripDetails.distance; }
              if (tripDetails.duration > maxTime) { maxTime = tripDetails.duration; }
              if (tripDetails.duration < minTime) { minTime = tripDetails.duration; }
              if (tripDetails.speed > maxSpeed) { maxSpeed = tripDetails.speed; }
              if (tripDetails.speed < minSpeed) { minSpeed = tripDetails.speed; }

              dists.push(tripDetails.distance);
              times.push(tripDetails.duration);
              speeds.push(tripDetails.speed);
            }

            // 3. SORT INTO DAYS
            for (let j = 0; j < 7; j++) {
              if (routeDays[i][k].toString().includes(j.toString())) {
                
                // Frequency logic: Only collect if within daytime hours (06:00 to 20:00)
                if (totalMins >= 360 && totalMins <= 1200) {
                  if (!timeBins[j][headsign]) timeBins[j][headsign] = [];
                  timeBins[j][headsign].push(totalMins);
                }

                // 4. APPEND TO DAILY LIST
                // We use the statsString generated above and build the link back to trip.html
                const tripLink = `trip.html?agency=${agency}&trip=${routeTrips[i][k]}`;
                document.getElementById("day" + j).innerHTML += `<td><a href="${tripLink}">${routeTimes[i][k]}</a></td><td>${tripHeadsigns[l]}</td><td>${statsString}</td>`;
                
                dayCounts[j]++;
              }
            }
          }
        }
      }
    }
  }

  // 5. RENDER SUMMARY STATS (Headways)
  for (let j = 0; j < 7; j++) {
    let statsHtml = "";
    for (let headsign in timeBins[j]) {
      let times = timeBins[j][headsign].sort((a, b) => a - b);
      let count = times.length;
      let displayValue = "";

      if (count >= 10) {
        let span = times[times.length - 1] - times[0];
        let avgHeadway = Math.round(span / (count - 1));
        displayValue = `${avgHeadway} mins`;
      } else {
        displayValue = (count === 1) ? `1 trip` : `${count} trips`;
      }

      statsHtml += ("<br>- " + headsign + ": " + displayValue);
    }
    document.getElementById("stats-" + j).innerHTML = statsHtml;
    document.getElementById("trip-count-" + j).innerHTML = (dayNames[j] + " Trips: " + dayCounts[j]);
  }

  // grab median trip details
  var medDist = Math.round(getMedian(dists) * 100) / 100;
  var medTime = Math.round(getMedian(times) * 100) / 100;
  var medSpeed = Math.round(getMedian(speeds) * 100) / 100;

  // need to somehow force a cache update to this somehow
  document.getElementById("stats").innerHTML += `<br>Distance: ${minDist} - ${maxDist} mi (Median: ${medDist} mi)<br>Time: ${minTime} - ${maxTime} min (Median: ${medTime} min)<br>Speed: ${minSpeed} - ${maxSpeed} mph (Median: ${medSpeed} mph)`;
}

function getMedian(arr)
{
  if (arr.length === 0) return undefined;

  const sorted = [...arr].sort((a, b) => a - b);
  
  const mid = Math.floor(sorted.length / 2);

  return sorted.length % 2 !== 0 
    ? sorted[mid] 
    : (sorted[mid - 1] + sorted[mid]) / 2;
}