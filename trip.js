const tripId = [];
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
const allStopsMap = [];

var result = "no data";
var agency = "no data";
var fromStop = "no data";

function funct()
{
  var parsedUrl = new URL(document.URL);
  console.log(parsedUrl);

  var paramName = 'trip';
  result = parsedUrl.searchParams.get(paramName);
  var paramName2 = 'agency';
  agency = parsedUrl.searchParams.get(paramName2);
  var paramName3 = 'from';
  fromStop = parsedUrl.searchParams.get(paramName3);
  
  const testAgencyUrl = ("trips/" + agency + "_trips.txt"); // provide file location
    fetch(testAgencyUrl)
      .then(r => r.text())
      .then((text) => {
        const agencyUrlFile = text.split("\n");
        agencyUrlFile.pop();

        for (let i = 0; i < agencyUrlFile.length; i++)
        {
          var data = agencyUrlFile[i];
          tripId.push(data.substring(0, data.indexOf(";")));
          data = data.substr(data.indexOf(";") + 1);

          tripRoute.push(data.substring(0, data.indexOf(";")));
          data = data.substr(data.indexOf(";") + 1);
          tripHeadsign.push(data.substring(0, data.indexOf(";")));
          data = data.substr(data.indexOf(";") + 1);

          var dataDays = data.substr(1, data.indexOf("]") - 1);
          tripDays.push(dataDays.split(", "));
          data = data.substr(data.indexOf(";") + 1);

          var dataIds = data.substr(1, data.indexOf("]") - 1);
          tripStopIds.push(dataIds.split(", "));
          data = data.substr(data.indexOf(";") + 2);
          data = data.substr(0, data.length - 1);
          tripStopTimes.push(data.split(", "));
        }

        getStops(result)
      })
}

function getStops(result) {
  const stopUrl = ("stops/" + agency + "_stops.txt");
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
      findTrip(result);
    });
}

function findTrip(result) {
  var daysShort = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];
  for (let i = 0; i < tripId.length; i++) {
    if (tripId[i] == result) {
      // 1. Basic Info
      document.getElementById("route").innerHTML += ("<a href='route.html?agency=" + agency + "&route=" + tripRoute[i] + "'>" + tripRoute[i] + "</a>");
      document.getElementById("headsign").innerHTML += (tripHeadsign[i]);

      for (let x = 0; x < tripDays[i].length; x++)
      {
        document.getElementById("days").innerHTML += (daysShort[tripDays[i][x]]);
        if (tripDays[i].length > (x + 1))
        {
          document.getElementById("days").innerHTML += (" / ");
        }
      }  

      // 2. List the Stops & Calculate Cumulative Stats
      // Define the starting stop index (e.g., if you want to start from the 3rd stop, set to 2)
      let startStopIndex = 9999; 

      for (let j = 0; j < tripStopTimes[i].length; j++) {
          
          if (tripStopIds[i][j] == fromStop)
          {
            startStopIndex = j;
          }

          // 1. Existing Cumulative Stats (Entire trip from start of array)
          const currentStopSlice = tripStopIds[i].slice(0, j + 1);
          const currentTimeSlice = tripStopTimes[i].slice(0, j + 1);
          const totalStats = calculateTripStats(currentStopSlice, currentTimeSlice, allStopsMap);
          
          // 2. New Segment Stats (From your pre-determined starting stop)
          // Only calculate if current stop is at or after the startStopIndex
          let segmentStats = { distance: 0, duration: 0, speed: 0 };
          if (j >= startStopIndex) {
              const segmentStopSlice = tripStopIds[i].slice(startStopIndex, j + 1);
              const segmentTimeSlice = tripStopTimes[i].slice(startStopIndex, j + 1);
              segmentStats = calculateTripStats(segmentStopSlice, segmentTimeSlice, allStopsMap);
          }

          // Existing spacing logic
          const spacing = j > 0 ? (totalStats.distance / j) : 0;

          const isStartRow = (j === startStopIndex);
          const rowClass = isStartRow ? ' class="highlight"' : '';

          for (let k = 0; k < stopId.length; k++) {
              if (stopId[k] == tripStopIds[i][j]) {
                  const stopLink = `stop.html?agency=${agency}&stop=${tripStopIds[i][j]}`;
                  
                  if (j < startStopIndex)
                  {
                    // before starting stop, blank columns for segment stats
                    let stopRow = `<tr${rowClass}><td><a href="${stopLink}">${tripStopTimes[i][j]}</a></td>
                                <td>${stopName[k]}</td>
                                <td>${totalStats.distance} mi</td>
                                <td>${totalStats.duration} min</td>
                                <td></td>
                                <td></td>
                                <td>${totalStats.speed} mph</td></tr>`;
                  }
                  else
                  {
                    // Updated row to include segment stats
                    let stopRow = `<tr${rowClass}><td><a href="${stopLink}">${tripStopTimes[i][j]}</a></td>
                                <td>${stopName[k]}</td>
                                <td>${totalStats.distance} mi</td>
                                <td>${totalStats.duration} min</td>
                                <td style=seg>(${segmentStats.distance} mi)</td>
                                <td style=seg>(${segmentStats.duration} min)</td>
                                <td>${totalStats.speed} mph</td></tr>`;
                  }
                  
                  document.getElementById("stops").innerHTML += stopRow;
                  currentStopIds.push(stopId[k]);
              }
          }

          // 3. Update the Main UI Stats
          document.getElementById("duration").innerHTML = ("<b>Total Trip Duration:</b> " + totalStats.duration + " minutes");
          document.getElementById("dist").innerHTML = ("<b>Total Trip Distance:</b> " + totalStats.distance + " miles");
          document.getElementById("speed").innerHTML = ("<b>Avg Trip Speed:</b> " + totalStats.speed + " mph");
          document.getElementById("spacing").innerHTML = ("<b>Stop Spacing:</b> " + (Math.round(spacing * 100) / 100) + " miles");
      }
    }
  }
}

function getStopIDs()
{
  document.getElementById("IDlist").innerHTML += ("<b>Stop ID list:</b><br>");

  for (let i = 0; i < currentStopIds.length; i++)
  {
    document.getElementById("IDlist").innerHTML += (currentStopIds[i] + "<br>");
  }
}
