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

function getStops(result)
{
  const stopUrl = ("https://itsbenchase.github.io/gtfs-reader/" + agency + "_stops.txt"); // provide file location
    fetch(stopUrl)
      .then(r => r.text())
      .then((text) => {
        const stopUrlFile = text.split("\n");
        stopUrlFile.pop();

        for (let i = 0; i < stopUrlFile.length; i++)
        {
          var data = stopUrlFile[i];
          stopId.push(data.substring(0, data.indexOf(";")));
          data = data.substr(data.indexOf(";") + 1);
          stopName.push(data.substring(0, data.indexOf(";")));
          data = data.substr(data.indexOf(";") + 1);
          stopLat.push(data.substring(0, data.indexOf(";")));
          data = data.substr(data.indexOf(";") + 1);
          stopLon.push(data);
        }

        findTrip(result)
      })
}

function findTrip(result)
{
  for (let i = 0; i < tripId.length; i++)
  {
    if (tripId[i] == result)
    {
      // things to add: connection to stop names/locations, trip time calc, distance calc
      
      document.getElementById("route").innerHTML += ("<a href=route.html?route=" + tripRoute[i] + ">" + tripRoute[i] + "</a>");
      document.getElementById("days").innerHTML += (tripDays[i]);
      document.getElementById("start").innerHTML += (tripStopTimes[i][0]);

      var tripLength = 0.00;

      for (let j = 0; j < tripStopTimes[i].length; j++)
      {
        for (let k = 0; k < stopId.length; k++)
        {
          if (stopId[k] == tripStopIds[i][j])
          {
            document.getElementById("stops").innerHTML += ("<br>" + tripStopTimes[i][j] + " | " + stopName[k]);

            cumLats.push(stopLat[k]);
            cumLons.push(stopLon[k]);

            if (cumLats.length > 1)
            {
              tripLength = cumulative();

              document.getElementById("dist").innerHTML = ("<b>Trip Distance:</b> " + tripLength + " miles");
            }
          }
        }
      }
    }
  }
}

function cumulative()
{
  // haversine formula loop
  // spherical trig cause this is the globe
  // cumLats.length will increase by 1 each run
  var dist = 0;
  for (let i = 1; i < cumLats.length; i++)
  {
    var lon1 = toRadians(Math.abs(cumLons[i - 1]));
    var lon2 = toRadians(Math.abs(cumLons[i]));
    var lat1 = toRadians(Math.abs(cumLats[i - 1]));
    var lat2 = toRadians(Math.abs(cumLats[i]));
    var dlon = lon2 - lon1;
    var dlat = lat2 - lat1;
    var a = Math.pow(Math.sin(dlat / 2), 2) + Math.cos(lat1) * Math.cos(lat2) * Math.pow(Math.sin(dlon / 2), 2);      
    var c = 2 * Math.asin(Math.sqrt(a));
    var r = 3963;

    dist += c * r;
  }

  // dist rounded for display
  dist = Math.round(dist * 100.0) / 100.0;
  
  return dist;
}

function toRadians(degrees)
{
  return degrees * (Math.PI / 180);
}
