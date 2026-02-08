const routeID = [];
const routeName = [];
const routeDays = [];
const routeTrips = [];
const routeTimes = [];
const tripHeadsigns = [];
const tripIds = [];

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
  
  const testAgencyUrl = ("https://itsbenchase.github.io/gtfs-reader/" + agency + "_routes.txt"); // provide file location
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

function getTrips(result)
{
  const testAgencyUrl = ("https://itsbenchase.github.io/gtfs-reader/" + agency + "_trips.txt"); // provide file location
      fetch(testAgencyUrl)
        .then(r => r.text())
        .then((text) => {
          const agencyUrlFile = text.split("\n");
          agencyUrlFile.pop();

          for (let i = 0; i < agencyUrlFile.length; i++)
          {
            var data = agencyUrlFile[i];
            tripIds.push(data.substring(0, data.indexOf(";")));
            data = data.substr(data.indexOf(";") + 1);
            data = data.substr(data.indexOf(";") + 1);
            tripHeadsigns.push(data.substring(0, data.indexOf(";")));
          }

          findRoute(result)
        })
}

function findRoute(result)
{
  for (let i = 0; i < routeID.length; i++)
  {
    if (routeID[i] == result)
    { 
      document.getElementById("route").innerHTML += (routeID[i] + ": " + routeName[i]);
      
      for (let k = 0; k < routeTrips[i].length; k++)
      {
        for (let l = 0; l < tripHeadsigns.length; l++)
        {
          if (tripIds[l] == routeTrips[i][k])
          {
            for (let j = 0; j < 7; j++)
            {
              if (routeDays[i][k].toString().includes(j.toString()))
              {
                document.getElementById("day" + j).innerHTML += ("<br>" + routeTimes[i][k] + " | " + tripHeadsigns[l] + " | <a href=trip.html?agency=" + agency + "&trip=" + routeTrips[i][k] + ">Trip: " + routeTrips[i][k] + "</a>");
              }
            }
          }
        }
      }
    }
  }
}