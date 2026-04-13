// Shared Haversine Formula
function calculateDistance(lat1, lon1, lat2, lon2) {
    const toRad = (deg) => deg * (Math.PI / 180);
    const R = 3963; // Radius in miles
    const dLat = toRad(Math.abs(lat2) - Math.abs(lat1));
    const dLon = toRad(Math.abs(lon2) - Math.abs(lon1));
    const a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
              Math.cos(toRad(Math.abs(lat1))) * Math.cos(toRad(Math.abs(lat2))) *
              Math.sin(dLon / 2) * Math.sin(dLon / 2);
    return (2 * Math.asin(Math.sqrt(a))) * R;
}

// Function to calculate stats for a specific trip
function calculateTripStats(stopIds, stopTimes, allStopsMap) {
  // Safety Guard: Check if the data exists and has at least two stops
    if (!stopIds || !stopTimes || stopTimes.length < 2) {
        console.warn("Incomplete trip data encountered");
        return { distance: 0, duration: 0, speed: 0 };
    }

    // 1. Duration Calculation
    const startStr = stopTimes[0];
    const endStr = stopTimes[stopTimes.length - 1];
    const duration = (Number(endStr.substring(0, 2)) * 60 + Number(endStr.substring(3, 5))) - 
                     (Number(startStr.substring(0, 2)) * 60 + Number(startStr.substring(3, 5)));

    // 2. Distance Calculation
    let totalDist = 0;
    for (let i = 1; i < stopIds.length; i++) {
        const s1 = allStopsMap[stopIds[i-1]];
        const s2 = allStopsMap[stopIds[i]];
        if (s1 && s2) {
            totalDist += calculateDistance(s1.lat, s1.lon, s2.lat, s2.lon);
        }
    }

    const distRounded = Math.round(totalDist * 100) / 100;
    const speed = duration > 0 ? Math.round((distRounded / duration) * 60 * 100) / 100 : 0;

    return { duration: duration, distance: distRounded, speed: speed };
}