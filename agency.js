const routeID = [];
const routeName = [];

var result = "no data";
var agency = "no data";

function funct()
{
  var parsedUrl = new URL(document.URL);
  console.log(parsedUrl);

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
        }

        getRoutes(agency)
      })
}

function getRoutes(agency)
{
  for (let i = 0; i < routeID.length; i++)
  {
    document.getElementById("routes").innerHTML += `<td><a class=route href=trip.html?agency=${agency}&route=${routeID[i]}>${routeID[i]}</a></td><td>${routeName[i]}</td>`;
  }
}