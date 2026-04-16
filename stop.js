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

var result = "no data";
var agency = "no data";

function funct()
{
  var parsedUrl = new URL(document.URL);
  console.log(parsedUrl);

  var paramName = 'stop';
  result = parsedUrl.searchParams.get(paramName);
  var paramName2 = 'agency';
  agency = parsedUrl.searchParams.get(paramName2);

  // 1. Define the URL for your agency's trips file
  const testAgencyUrl = ("https://itsbenchase.github.io/gtfs-reader/" + agency + "_trips.txt");
  
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
  const stopUrl = ("https://itsbenchase.github.io/gtfs-reader/" + agency + "_stops.txt");
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
      findTrips(result);
    });
}

function findTrips(result) {
  let dayCounts = [0, 0, 0, 0, 0, 0, 0];
  let dayNames = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"];
  let timeBins = {};
  let stopRoutes = [];
  
  // Temporary storage to hold trip objects before sorting
  let tripsByDay = { 0: [], 1: [], 2: [], 3: [], 4: [], 5: [], 6: [] };

  // Setup UI placeholders
  for (let j = 0; j < 7; j++) {
    document.getElementById("summary").innerHTML += `<br><span id="trip-count-${j}">${dayNames[j]} Trips: 0</span><span id="stats-${j}"></span><br>`;
    timeBins[j] = {};
  }
  
  for (let i = 0; i < stopId.length; i++) {
    if (stopId[i] == result) {
      document.getElementById("stop").innerHTML += `${stopName[i]} (${stopId[i]})`;

      for (let l = 0; l < tripIds.length; l++) {
        for (let k = 0; k < fullTripStopIds[l].length; k++) { // Fixed: length check on the specific sub-array
          if (stopId[i] == fullTripStopIds[l][k]) {
            
            // 1. CALCULATE STATS & TIME
            const tripDetails = calculateTripStats(fullTripStopIds[l], fullTripStopTimes[l], allStopsMap);
            let timeStr = fullTripStopTimes[l][k]; 
            let [hrs, mins] = timeStr.split(':').map(Number);
            let totalMins = (hrs * 60) + mins;
            let headsign = tripHeadsign[l];
            
            // add route to stop
            if (!stopRoutes.includes(tripRoute[l]))
            {
              stopRoutes.push(tripRoute[l]);
            }

            // 2. CREATE A TRIP OBJECT
            const tripObj = {
              timeMins: totalMins, // Used for sorting
              html: `<td><a href="trip.html?agency=${agency}&trip=${tripIds[l]}">${timeStr}</a></td>
                     <td><a class=route href="route.html?agency=${agency}&route=${tripRoute[l]}">${tripRoute[l]}</a></td>
                     <td>${headsign}</td>
                     <td>${tripDetails.duration} min</td><td>${tripDetails.distance} mi</td><td>${tripDetails.speed} mph</td>`
            };

            // 3. ASSIGN TO RELEVANT DAYS
            for (let j = 0; j < 7; j++) {
              if (tripDays[l].toString().includes(j.toString())) {
                tripsByDay[j].push(tripObj);
                
                // Frequency logic (06:00 to 20:00)
                if (totalMins >= 360 && totalMins <= 1200) {
                  if (!timeBins[j][headsign]) timeBins[j][headsign] = [];
                  timeBins[j][headsign].push(totalMins);
                }
              }
            }
          }
        }
      }
    }
  }

  // 4. SORT AND RENDER
  for (let j = 0; j < 7; j++) {
    // Sort the array for this day by the 'timeMins' property
    tripsByDay[j].sort((a, b) => a.timeMins - b.timeMins);

    // Update the counter
    document.getElementById("trip-count-" + j).innerText = `${dayNames[j]} Trips: ${tripsByDay[j].length}`;

    // Join all the sorted HTML rows and inject into the DOM
    let tableRows = tripsByDay[j].map(t => `<tr>${t.html}</tr>`).join('');
    document.getElementById("day" + j).innerHTML = tableRows;
  }

  // 5. RENDER SUMMARY STATS (Headways)
  for (let j = 0; j < 7; j++) {
    let statsHtml = "";
    let combinedTimes = []; // Array to hold all times for this time bin

    // First, collect all times across all headsigns
    for (let headsign in timeBins[j]) {
      combinedTimes.push(...timeBins[j][headsign]);
    }

    // Calculate Overall Frequency
    if (combinedTimes.length > 0) {
      let allTimesSorted = combinedTimes.sort((a, b) => a - b);
      let totalCount = allTimesSorted.length;
      let overallDisplay = "";

      if (totalCount >= 10) {
        let span = allTimesSorted[totalCount - 1] - allTimesSorted[0];
        let avgHeadway = Math.round(span / (totalCount - 1));
        overallDisplay = `${avgHeadway} mins`;
      } else {
        overallDisplay = (totalCount === 1) ? `1 trip` : `${totalCount} trips`;
      }
      
      // Add the "Overall" line at the top of the summary
      statsHtml += `<br>Overall: ${overallDisplay}`;
    }

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
    document.getElementById("trip-count-" + j).innerText = `${dayNames[j]} Trips: ${tripsByDay[j].length}`;
  }

  // list routes at stop
  for (let x = 0; x < stopRoutes.length; x++)
  {
    document.getElementById("routes").innerHTML += `<a class='route' href='route.html?agency=${agency}&route=${stopRoutes[x]}'>${stopRoutes[x]}</a>`;
    if (stopRoutes.length > (x + 1))
    {
      document.getElementById("routes").innerHTML += (" / ");
    }
  }  

  findNearby(result)
}

function findNearby(result)
{
  var refIdx = stopId.indexOf(result);
  var refLat = stopLat[refIdx];
  var refLon = stopLon[refIdx];
  var nearby = [];

  for (let i = 0; i < stopId.length; i++)
  {
    if (i === refIdx) { continue; }

    var dist = calculateDistance(refLat, refLon, stopLat[i], stopLon[i]);
    dist = Math.round(dist * 100) / 100;

    if (dist <= 0.25)
    {
      nearby.push({
        id: stopId[i],
        name: stopName[i],
        distance: dist
      });
    }
  }
  
  nearby.sort((a, b) => a.distance - b.distance);
  for (let i = 0; i < nearby.length; i++)
  {
    document.getElementById("nearby").innerHTML += ("<td><a class=stop href=stop.html?agency=" + agency + "&stop=" + nearby[i].id + ">" + nearby[i].id + "</a></td><td>" + nearby[i].name + "</td><td>" + nearby[i].distance + " mi.</td>");
  }
}
