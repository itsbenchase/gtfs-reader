const agencyRegion = [];
const agencyShort = [];
const agencyFull = [];

function funct()
{
  const testAgencyUrl = ("agencies.txt"); // provide file location
    fetch(testAgencyUrl)
      .then(r => r.text())
      .then((text) => {
        const agencyUrlFile = text.split("\n");
        agencyUrlFile.pop();

        for (let i = 0; i < agencyUrlFile.length; i++)
        {
          var data = agencyUrlFile[i];
          agencyRegion.push(data.substring(0, data.indexOf(";")));
          data = data.substr(data.indexOf(";") + 1);
          agencyShort.push(data.substring(0, data.indexOf(";")));
          data = data.substr(data.indexOf(";") + 1);
          agencyFull.push(data.substring(0, data.indexOf(";")));
        }

        getData()
      })
}

function getData()
{
  var regionAgencies = "";
  for (let i = 0; i < agencyRegion.length; i++)
  {
    if (i >= 1 && agencyRegion[i] === agencyRegion[i - 1])
    {
      regionAgencies = regionAgencies + "," + agencyShort[i];
    }
    else if (i == 0 || agencyRegion[i] != agencyRegion[i - 1])
    {
      regionAgencies = agencyShort[i]; 
    }
    document.getElementById(agencyRegion[i]).innerHTML += `<br><a class=stop href=map.html?agency=${agencyShort[i]}>${agencyFull[i]}</a>`;

    const mapLink = document.getElementById([agencyRegion[i]] + "-map");
    if (mapLink) {
      mapLink.href = "map.html?agency=" + regionAgencies;
    }
  }
}