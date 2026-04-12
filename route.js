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
  let dayCounts = [0, 0, 0, 0, 0, 0, 0];
  let dayNames = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"]

  // Storage for frequency: day_index -> headsign -> array of minute values
  let timeBins = {}; 
  
  for (let j = 0; j < 7; j++)
  {
    document.getElementById("summary").innerHTML += ("<br><span id=trip-count-" + j +">" + dayNames[j] + " Trips: 0</span><span id=stats-" + j + "></span><br>");
    timeBins[j] = {}; 
  }

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
            
            let timeStr = routeTimes[i][k]; // e.g., "08:30"
            let [hrs, mins] = timeStr.split(':').map(Number);
            let totalMins = (hrs * 60) + mins;
            let headsign = tripHeadsigns[l];

            for (let j = 0; j < 7; j++)
            {
              if (routeDays[i][k].toString().includes(j.toString()))
              {
                // Only collect if within 06:00 (360) and 20:00 (1200)
                if (totalMins >= 360 && totalMins <= 1200) {
                  if (!timeBins[j][headsign]) timeBins[j][headsign] = [];
                  timeBins[j][headsign].push(totalMins);
                }

                document.getElementById("day" + j).innerHTML += ("<br>" + routeTimes[i][k] + " | " + tripHeadsigns[l] + " | <a href=trip.html?agency=" + agency + "&trip=" + routeTrips[i][k] + ">Trip: " + routeTrips[i][k] + "</a>");
                dayCounts[j]++;
              }
            }
          }
        }
      }
    }
  }

  for (let j = 0; j < 7; j++) {
    let statsHtml = "";
    for (let headsign in timeBins[j]) {
      let times = timeBins[j][headsign].sort((a, b) => a - b);
      let count = times.length;
      let displayValue = "";

      // Check if there are 10 or more trips to justify a frequency calculation
      if (count >= 10) {
        let span = times[times.length - 1] - times[0];
        let avgHeadway = Math.round(span / (count - 1));
        displayValue = `${avgHeadway} mins (daytime)`;
      } else {
        // If less than 10, just show the count
        if (count == 1)
        {
          displayValue = `1 trip`;
        }
        else
        {
          displayValue = `${count} trips`;
        }
      }

      statsHtml += ("<br>- " + headsign + ": " + displayValue);
    }
    document.getElementById("stats-" + j).innerHTML = statsHtml;
  }

  for (let j = 0; j < 7; j++)
  {
    document.getElementById("trip-count-" + j).innerHTML = (dayNames[j] + " Trips: " + dayCounts[j]);
  }
}