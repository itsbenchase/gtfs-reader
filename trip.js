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

function funct()
{
  var parsedUrl = new URL(document.URL);
  console.log(parsedUrl);

  var paramName = 'trip';
  result = parsedUrl.searchParams.get(paramName);
  var paramName2 = 'agency';
  agency = parsedUrl.searchParams.get(paramName2);
  
  const testAgencyUrl = ("https://itsbenchase.github.io/gtfs-reader/" + agency + "_trips.txt"); // provide file location
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
      findTrip(result);
    });
}

function findTrip(result) {
  for (let i = 0; i < tripId.length; i++) {
    if (tripId[i] == result) {
      // 1. Basic Info
      document.getElementById("route").innerHTML += ("<a href=route.html?agency=" + agency + "&route=" + tripRoute[i] + ">" + tripRoute[i] + "</a>");
      document.getElementById("headsign").innerHTML += (tripHeadsign[i]);
      document.getElementById("days").innerHTML += (tripDays[i]);

      // NEW: Call the utility here where 'i' and data are valid
      // Note: use 'allStopsMap' if you followed the previous step to map your stops
      const stats = calculateTripStats(tripStopIds[i], tripStopTimes[i], allStopsMap);

      // 3. Update the UI using the stats object
      document.getElementById("duration").innerHTML = ("<b>Trip Duration:</b> " + stats.duration + " minutes");
      document.getElementById("dist").innerHTML = ("<b>Trip Distance:</b> " + stats.distance + " miles");
      document.getElementById("speed").innerHTML = ("<b>Trip Speed:</b> " + stats.speed + " miles per hour");
      
      const spacing = stats.distance / (tripStopIds[i].length - 1);
      document.getElementById("spacing").innerHTML = ("<b>Stop Spacing:</b> " + (Math.round(spacing * 100) / 100) + " miles");

      // 4. List the Stops (Existing UI Logic)
      for (let j = 0; j < tripStopTimes[i].length; j++) {
        for (let k = 0; k < stopId.length; k++) {
          if (stopId[k] == tripStopIds[i][j]) {
            const stopLink = `stop.html?agency=${agency}&stop=${tripStopIds[i][j]}`;
            document.getElementById("stops").innerHTML += ("<br><a href=" + stopLink +">" + tripStopTimes[i][j] + "</a> | " + stopName[k]);
            currentStopIds.push(stopId[k]);
          }
        }
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
