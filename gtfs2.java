import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.util.*;
import java.time.LocalDate;
import java.io.*;
import java.net.*;
import java.util.zip.*;
import java.net.http.*;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

// 2026 reader style update
// version connected to the web

public class gtfs2
{
    static String agency = "no data";
    static String zipUrl = "no data";

    public gtfs2(String agency1, String zipUrl1)
    {
        agency = agency1;
        zipUrl = zipUrl1;
    }

    public static void main(String [] args)
    {  
        Scanner in = new Scanner(System.in);

        // all the arraylists
        ArrayList<String> serviceIDcal = new ArrayList<String>();
        ArrayList<Integer> calStart = new ArrayList<Integer>();
        ArrayList<Integer> calEnd = new ArrayList<Integer>();
        ArrayList<String> [] days = new ArrayList[7];

        ArrayList<String> routeID = new ArrayList<String>();
        ArrayList<String> routeName = new ArrayList<String>();
        ArrayList<String> routeFull = new ArrayList<String>();

        ArrayList<String> routeIDtrip = new ArrayList<String>();
        ArrayList<String> serviceIDtrip = new ArrayList<String>();
        ArrayList<String> tripIDtrip = new ArrayList<String>();
        ArrayList<String> headsigntrip = new ArrayList<String>();

        ArrayList<String> tripIDtimes = new ArrayList<String>();
        ArrayList<String> departuretimes = new ArrayList<String>();
        ArrayList<String> stopIDtimes = new ArrayList<String>();

        ArrayList<String> stopID = new ArrayList<String>();
        ArrayList<String> stopName = new ArrayList<String>();
        ArrayList<String> stopLat = new ArrayList<String>();
        ArrayList<String> stopLon = new ArrayList<String>();

        for (int i = 0; i < 7; i++) {
            days[i] = new ArrayList<String>();
        }

        LocalDate today = LocalDate.now();
        LocalDate sevenDaysOut = today.plusDays(7);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");

        int windowStart = Integer.parseInt(today.format(formatter));
        int windowEnd = Integer.parseInt(sevenDaysOut.format(formatter));

        // Set to keep track of which service IDs actually have active dates in our window
        Set<String> activeInWindow = new HashSet<>();
        Map<String, ServiceProfile> masterMap = new HashMap<>();

        try
        {
            URL url = new URL(zipUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8");
            conn.setRequestProperty("Accept-Language", "en-US,en;q=0.5");
            conn.setRequestProperty("Connection", "keep-alive");
            conn.setRequestProperty("Referer", "https://www.google.com/");
            conn.setInstanceFollowRedirects(true);

            int status = conn.getResponseCode();
            System.out.println(status);
    
            // Check for redirects (301, 302, 303, 307, 308)
            if (status == 301 || status == 302 || status == 307 || status == 308)
            {
                String location = conn.getHeaderField("Location");
                
                // If the location is relative (starts with /), prepend the protocol and host
                if (location.startsWith("/")) {
                    location = url.getProtocol() + "://" + url.getHost() + location;
                }
                
                System.out.println("Redirecting to full path: " + location);
                
                // Open the final connection
                conn = (HttpURLConnection) new URL(location).openConnection();
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
                conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8");
                conn.setRequestProperty("Accept-Language", "en-US,en;q=0.5");
                conn.setRequestProperty("Connection", "keep-alive");
                conn.setRequestProperty("Referer", "https://www.google.com/");
            }

            // 2. Create a temporary file that deletes itself when the program exits
            // This is our "no clutter" insurance policy.
            Path tempFile = Files.createTempFile("gtfs_temp_", ".zip");
            File file = tempFile.toFile();
            file.deleteOnExit(); 

            // 3. Download the stream to the temp file
            try (InputStream input = conn.getInputStream()) {
                Files.copy(input, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }

            // 1. Open the file you downloaded to your temp directory
            try (ZipFile zipFile = new ZipFile(tempFile.toFile()))
            {
                // 1. Initialize variables to hold the entries once found
                ZipEntry calendarEntry = null;
                ZipEntry datesEntry = null;
                ZipEntry stopsEntry = null;
                ZipEntry timesEntry = null;
                ZipEntry routesEntry = null;
                ZipEntry tripsEntry = null;

                // 2. Loop through the ZIP once to find your files
                Enumeration<? extends ZipEntry> entries = zipFile.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    String name = entry.getName();

                    if (!entry.isDirectory()) {
                        if (name.endsWith("calendar.txt")) {
                            calendarEntry = entry;
                        } else if (name.endsWith("calendar_dates.txt")) {
                            datesEntry = entry;
                        } else if (name.endsWith("routes.txt")) {
                            routesEntry = entry;
                        } else if (name.endsWith("stops.txt")) {
                            stopsEntry = entry;
                        } else if (name.endsWith("stop_times.txt")) {
                            timesEntry = entry;
                        } else if (name.endsWith("trips.txt")) {
                            tripsEntry = entry;
                        }
                    }
                }

                if (calendarEntry != null)
                {
                    try (InputStream is = zipFile.getInputStream(calendarEntry);
                        Scanner s = new Scanner(is)) {
                        
                        int z = 0;
                        int sIdx = -1, mIdx = -1, tIdx = -1, wIdx = -1, thIdx = -1, fIdx = -1, saIdx = -1, suIdx = -1, startIdx = -1, endIdx = -1;
                        windowEnd = Integer.parseInt(today.format(formatter));

                        while (s.hasNextLine()) {
                            String line = s.nextLine().replace("\uFEFF", "").replace("\"", "");
                            String[] data = line.split(",");
                            if (z++ == 0) {
                                List<String> h = Arrays.asList(data);
                                sIdx = h.indexOf("service_id");
                                mIdx = h.indexOf("monday"); tIdx = h.indexOf("tuesday"); wIdx = h.indexOf("wednesday");
                                thIdx = h.indexOf("thursday"); fIdx = h.indexOf("friday"); saIdx = h.indexOf("saturday"); suIdx = h.indexOf("sunday");
                                startIdx = h.indexOf("start_date"); endIdx = h.indexOf("end_date");
                            } else {
                                int calStartData = Integer.parseInt(data[startIdx]);
                                int calEndData = Integer.parseInt(data[endIdx]);

                                if (calEndData >= windowStart && calStartData <= windowEnd) {
                                    String id = data[sIdx];
                                    activeInWindow.add(id);
                                    ServiceProfile profile = masterMap.computeIfAbsent(id, k -> new ServiceProfile());
                                    
                                    profile.activeDays[0] = data[mIdx].equals("1");
                                    profile.activeDays[1] = data[tIdx].equals("1");
                                    profile.activeDays[2] = data[wIdx].equals("1");
                                    profile.activeDays[3] = data[thIdx].equals("1");
                                    profile.activeDays[4] = data[fIdx].equals("1");
                                    profile.activeDays[5] = data[saIdx].equals("1");
                                    profile.activeDays[6] = data[suIdx].equals("1");
                                    profile.start = Math.max(windowStart, calStartData);
                                    profile.end = Math.min(windowEnd, calEndData);
                                }
                            }
                        }
                    }
                }

                // --- PART 2: PROCESS CALENDAR_DATES.TXT ---
                if (datesEntry != null) {
                    try (InputStream is = zipFile.getInputStream(datesEntry);
                        Scanner s = new Scanner(is)) {
                        
                        int z = 0;
                        int sIdx = -1, dIdx = -1, eIdx = -1;
                        windowEnd = Integer.parseInt(sevenDaysOut.format(formatter));

                        while (s.hasNextLine()) {
                            String line = s.nextLine().replace("\uFEFF", "").replace("\"", "");
                            String[] data = line.split(",");
                            if (z++ == 0) {
                                List<String> h = Arrays.asList(data);
                                sIdx = h.indexOf("service_id");
                                dIdx = h.indexOf("date");
                                eIdx = h.indexOf("exception_type");
                            } else {
                                String id = data[sIdx];
                                String rawDate = data[dIdx];
                                int dateVal = Integer.parseInt(rawDate);

                                if (dateVal >= windowStart && dateVal <= windowEnd) {
                                    // Holiday Filter
                                    if (rawDate.equals("20260525") || rawDate.equals("20260619") || rawDate.equals("20260704")) continue;

                                    int type = Integer.parseInt(data[eIdx]);
                                    ServiceProfile profile = masterMap.computeIfAbsent(id, k -> new ServiceProfile());
                                    activeInWindow.add(id);

                                    LocalDate date = LocalDate.parse(rawDate, formatter);
                                    int dayIdx = date.getDayOfWeek().getValue() - 1;

                                    if (type == 1) { // Added
                                        LocalDate date2 = LocalDate.parse(rawDate, formatter);
                                        int dayOfWeekIdx = date2.getDayOfWeek().getValue() - 1; // 0=Mon, 6=Sun
                                        
                                        profile.activeDays[dayOfWeekIdx] = true; 
                                        
                                        profile.start = Math.min(profile.start, dateVal);
                                        profile.end = Math.max(profile.end, dateVal);
                                    } else if (type == 2) { // Removed
                                        profile.activeDays[dayIdx] = false;
                                    }
                                }
                            }
                        }
                    }
                }

                // --- PART 3: LOAD ONLY VALID SERVICES ---
                for (String id : activeInWindow) {
                    ServiceProfile p = masterMap.get(id);
                    if (p != null) {
                        serviceIDcal.add(id);
                        calStart.add(p.start);
                        calEnd.add(p.end);
                        for (int i = 0; i < 7; i++) {
                            days[i].add(p.activeDays[i] ? "1" : "0");
                        }
                    }
                }

                if (routesEntry != null) {
                    try (InputStream is = zipFile.getInputStream(routesEntry);
                        Scanner s = new Scanner(is)) {
                        String [] headers2 = {};
                        int idIndex = -999;
                        int nameIndex = -999;
                        int fullIndex = -999;
                        int z = 0;
                        while (s.hasNextLine()) {
                            if (z == 0)
                            {
                                String rawLine = s.nextLine();
                                String cleanLine = rawLine.replace("\uFEFF", "").replaceAll("[^\\x20-\\x7e]", "");
                                cleanLine = cleanLine.replace("\"", ""); // replace quotes
                                
                                headers2 = cleanLine.split(",");
                                List<String> headers = Arrays.asList(headers2);
                                idIndex = headers.indexOf("route_id");
                                nameIndex = headers.indexOf("route_short_name");
                                fullIndex = headers.indexOf("route_long_name");

                                System.out.println("routes: " + idIndex + " / " + nameIndex + " / " + fullIndex);
                                z++;
                            }
                            else {
                                String rawLine = s.nextLine();
                                
                                // 1. Split the RAW line using a limit of -1. 
                                // This ensures empty columns (like "") don't get collapsed.
                                String[] data = rawLine.split(",", -1);

                                // 2. Clean only the specific piece of data you need
                                String currentID = data[idIndex].replace("\"", "").trim();
                                String currentShortName = data[nameIndex].replace("\"", "").trim();
                                String currentLongName = data[fullIndex].replace("\"", "").trim();

                                // Add the ID to your ID list
                                routeID.add(currentID);

                                // 3. Logic for the fallback
                                if (currentShortName.length() > 0) {
                                    routeName.add(currentShortName); 
                                } else {
                                    // This will now correctly add "20", "30", etc.
                                    routeName.add(currentID); 
                                }
                                
                                routeFull.add(currentLongName);
                            }
                        }
                    }
                }
            
                if (tripsEntry != null) {
                    try (InputStream is = zipFile.getInputStream(tripsEntry);
                        Scanner s = new Scanner(is)) {
                        String [] headers2 = {};
                        int tripIndex = -999;
                        int serviceIndex = -999;
                        int routeIndex = -999;
                        int headsignIndex = -999;
                        int z = 0;
                        while (s.hasNextLine())
                        {
                            if (z == 0)
                            {
                                String rawLine = s.nextLine();
                                String cleanLine = rawLine.replace("\uFEFF", "").replaceAll("[^\\x20-\\x7e]", "");
                                cleanLine = cleanLine.replace("\"", ""); // replace quotes
                                
                                headers2 = cleanLine.split(",");
                                List<String> headers = Arrays.asList(headers2);
                                
                                tripIndex = headers.indexOf("trip_id");
                                serviceIndex = headers.indexOf("service_id");
                                routeIndex = headers.indexOf("route_id");
                                headsignIndex = headers.indexOf("trip_headsign");

                                System.out.println("trips: " + tripIndex + " / " + serviceIndex + " / " + routeIndex + " / " + headsignIndex);
                                z++;
                            }
                            else 
                            {
                                String rawLine = s.nextLine();
                                // Split with -1 to ensure we don't drop empty trailing columns
                                String[] data = rawLine.split(",", -1);

                                // Clean only the fields we need to check
                                String serviceID = data[serviceIndex].replace("\"", "").trim();
                                
                                if (serviceIDcal.contains(serviceID)) {
                                    String routeIDtemp = data[routeIndex].replace("\"", "").trim();
                                    String tripID = data[tripIndex].replace("\"", "").trim();
                                    
                                    // 1. Find the correct Route Name fallback
                                    String finalRouteName = routeIDtemp; // Default fallback to the ID (20, 30, etc.)
                                    
                                    for (int x = 0; x < routeID.size(); x++) {
                                        if (routeIDtemp.equals(routeID.get(x))) {
                                            // If we found a match and the short name isn't empty, use it
                                            if (routeName.get(x).length() > 0) {
                                                finalRouteName = routeName.get(x);
                                            }
                                            break; // Stop looking once we find the matching route
                                        }
                                    }
                                    
                                    // 2. Add to lists (Only once per trip!)
                                    routeIDtrip.add(finalRouteName);
                                    serviceIDtrip.add(serviceID);
                                    tripIDtrip.add(tripID);

                                    // 3. Robust Headsign handling
                                    if (headsignIndex != -1 && headsignIndex < data.length) {
                                        String headsign = data[headsignIndex].replace("\"", "").trim();
                                        if (headsign.length() > 0) {
                                            headsigntrip.add(headsign);
                                        } else {
                                            headsigntrip.add("no headsign");
                                        }
                                    } else {
                                        headsigntrip.add("no headsign");
                                    }
                                }
                            }
                        }
                        System.out.println("Trips loaded");
                    }
                }
        
                Map<String, Integer> tripIndexMap = new HashMap<>();
                for (int i = 0; i < tripIDtrip.size(); i++) {
                    tripIndexMap.put(tripIDtrip.get(i), i);
                }

                List<StopTime> stopTimeList = new ArrayList<>();
                if (timesEntry != null) {
                    try (InputStream is = zipFile.getInputStream(timesEntry);
                        Scanner s = new Scanner(is)) {

                            BufferedReader br = new BufferedReader(new InputStreamReader(is));
                            
                            String line = "no data";
                            int count = 0;

                            int tripIndex = -999;
                            int timeIndex = -999;
                            int stopIndex = -999;
                            int stopHeadIndex = -999;

                            while ((line = br.readLine()) != null)
                            {    
                                String cleanLine = line.replace("\uFEFF", "").replaceAll("[^\\x20-\\x7e]", "");
                                cleanLine = cleanLine.replace("\"", ""); // replace quotes
                                String [] columns = cleanLine.split(",");

                                if (count == 0)
                                {
                                    List<String> headers = Arrays.asList(columns);
                                    tripIndex = headers.indexOf("trip_id");
                                    timeIndex = headers.indexOf("departure_time");
                                    stopIndex = headers.indexOf("stop_id");
                                    stopHeadIndex = headers.indexOf("stop_headsign");

                                    System.out.println("times: " + tripIndex + " / " + timeIndex + " / " + stopIndex);

                                    count++;
                                }
                                else {
                                    String tripID = columns[tripIndex];
                                    Integer index = tripIndexMap.get(tripID);
                                    
                                    if (index != null && columns[timeIndex].length() > 2) {
                                        String formattedTime;
                                        
                                        // Your existing time formatting logic
                                        if (columns[timeIndex].substring(4, 5).equals(":")) {
                                            formattedTime = "0" + columns[timeIndex].substring(0, 4);
                                        } else {
                                            formattedTime = columns[timeIndex].substring(0, 5);
                                        }

                                        // Add to our object list instead of 3 separate lists
                                        stopTimeList.add(new StopTime(tripID, formattedTime, columns[stopIndex]));

                                        // Handle the headsign update as you were before
                                        if (stopHeadIndex != -1 && headsigntrip.get(index).equals("no headsign") && columns.length > stopHeadIndex && columns[stopHeadIndex].length() > 1) {
                                            headsigntrip.set(index, columns[stopHeadIndex]);
                                        }
                                    }
                                    count++;
                                    if (count % 10000 == 0) { // Increased frequency for large files

                                        System.out.println("Stop times processed: " + count);

                                    }
                                }
                            }

                            stopTimeList.sort(Comparator.comparing(StopTime::getDepartureTime));

                            // Optional: If you strictly need those original separate lists for the rest of your app:
                            for (StopTime st : stopTimeList) {
                                tripIDtimes.add(st.tripId);
                                departuretimes.add(st.departureTime);
                                stopIDtimes.add(st.stopId);
                            }
                        }
                    }
                
                if (routesEntry != null) {
                    try (InputStream is = zipFile.getInputStream(stopsEntry);
                        Scanner s = new Scanner(is)) {
                            String [] headers2 = {};
                            int idIndex = -999;
                            int nameIndex = -999;
                            int latIndex = -999;
                            int lonIndex = -999;
                            int typeIndex = -999;
                            int z = 0;
                            while (s.hasNextLine()) {
                                
                                if (z == 0)
                                {
                                    String rawLine = s.nextLine();
                                    String cleanLine = rawLine.replace("\uFEFF", "").replaceAll("[^\\x20-\\x7e]", "");
                                    cleanLine = cleanLine.replace("\"", ""); // replace quotes
                                    
                                    headers2 = cleanLine.split(",");
                                    List<String> headers = Arrays.asList(headers2);
                                    idIndex = headers.indexOf("stop_id");
                                    nameIndex = headers.indexOf("stop_name");
                                    latIndex = headers.indexOf("stop_lat");
                                    lonIndex = headers.indexOf("stop_lon");
                                    typeIndex = headers.indexOf("location_type");

                                    System.out.println("stops: " + idIndex + " / " + nameIndex + " / " + latIndex + " / " + lonIndex);

                                    z++;
                                }
                                else
                                {
                                    String rawLine = s.nextLine();
                                    String cleanLine = rawLine.replace("\uFEFF", "").replaceAll("[^\\x20-\\x7e]", "");
                                    cleanLine = cleanLine.replace("\"", ""); // replace quotes
                                    String [] data = cleanLine.split(",", -1);

                                    if (typeIndex == -1 || data[typeIndex].isEmpty() || (!data[typeIndex].equals("2") && !data[typeIndex].equals("3")))
                                    {
                                        stopID.add(data[idIndex]);
                                        stopName.add(data[nameIndex]);
                                        stopLat.add(data[latIndex]);
                                        stopLon.add(data[lonIndex]);
                                    }
                                    else if (typeIndex == -1)
                                    {
                                        stopID.add(data[idIndex]);
                                        stopName.add(data[nameIndex]);
                                        stopLat.add(data[latIndex]);
                                        stopLon.add(data[lonIndex]);
                                    }
                                }
                            }
                        }
                    }
                }

        } catch (Exception e) {
            System.out.println("Error fetching or parsing ZIP: " + e.getMessage());
        }

        // this is where the new reader formatting begins

        // new trip file - trip_id, route, headsign, days of week, stop_ids, stop_times --- other code would need reference to names and locations?
        try (BufferedWriter tripFileWriter = new BufferedWriter(new FileWriter("trips/" + agency + "_trips.txt"))) {
            
            // --- STEP 1: MAP SERVICE DAYS (OUTSIDE LOOP) ---
            Map<String, List<Integer>> serviceToDays = new HashMap<>();
            for (int l = 0; l < serviceIDcal.size(); l++) {
                String sId = serviceIDcal.get(l);
                List<Integer> daysAvailable = new ArrayList<>();
                for (int k = 0; k < 7; k++) {
                    if ("1".equals(days[k].get(l))) {
                        daysAvailable.add(k);
                    }
                }
                serviceToDays.put(sId, daysAvailable);
            }

            // --- STEP 2: GROUP STOP TIMES BY TRIP_ID (OUTSIDE LOOP) ---
            // We use a Map of Lists so one TripID points to ALL its stops
            Map<String, List<String>> tripToStops = new HashMap<>();
            Map<String, List<String>> tripToTimes = new HashMap<>();
            
            for (int j = 0; j < tripIDtimes.size(); j++) {
                String tId = tripIDtimes.get(j);
                
                // ComputeIfAbsent creates the list the first time it sees a Trip ID
                tripToStops.computeIfAbsent(tId, k -> new ArrayList<>()).add(stopIDtimes.get(j));
                tripToTimes.computeIfAbsent(tId, k -> new ArrayList<>()).add(departuretimes.get(j));
            }

            // --- STEP 3: THE MAIN TRIP LOOP (NOW SUPER FAST) ---
            for (int i = 0; i < tripIDtrip.size(); i++) {
                String currentTripId = tripIDtrip.get(i);
                String currentServiceId = serviceIDtrip.get(i);

                // Instant lookups from our pre-processed Maps
                List<Integer> serviceDays = serviceToDays.getOrDefault(currentServiceId, new ArrayList<>());
                List<String> stops = tripToStops.getOrDefault(currentTripId, new ArrayList<>());
                List<String> times = tripToTimes.getOrDefault(currentTripId, new ArrayList<>());

                // Write the data
                tripFileWriter.write(currentTripId + ";" + 
                                routeIDtrip.get(i) + ";" + 
                                headsigntrip.get(i) + ";" + 
                                serviceDays + ";" + 
                                stops + ";" + 
                                times);
                tripFileWriter.newLine();

                if (i % 1000 == 0) {
                    System.out.println("Trips processed: " + i + " / " + tripIDtrip.size());
                }
            }

        } catch (Exception e) {
            System.out.println("ah shit, it can't make a new trip file, ben you borked something up: " + e.getMessage());
        }


        // new route file - route_id, trip_ids, days of week
        try {
            File routeFile = new File("routes/" + agency + "_routes.txt");
            // Use BufferedWriter for significantly better performance
            BufferedWriter writer = new BufferedWriter(new FileWriter(routeFile));

            // 1. Pre-process Stop Times - Fixed for FIRST departure
            Map<String, String> tripToDeparture = new HashMap<>();
            for (int k = 0; k < tripIDtimes.size(); k++) {
                // putIfAbsent ensures we keep the first occurrence (the earliest stop)
                tripToDeparture.putIfAbsent(tripIDtimes.get(k), departuretimes.get(k));
            }

            Map<String, List<Integer>> serviceToDays = new HashMap<>();
            for (int l = 0; l < serviceIDcal.size(); l++) {
                String sId = serviceIDcal.get(l);
                List<Integer> daysAvailable = new ArrayList<>();
                for (int m = 0; m < 7; m++) {
                    if ("1".equals(days[m].get(l))) {
                        daysAvailable.add(m);
                    }
                }
                serviceToDays.put(sId, daysAvailable);
            }

            // --- STEP 3: THE MAIN LOOP ---
            for (int i = 0; i < routeID.size(); i++) {
                // Inside the route loop...
                String targetRouteId = routeName.get(i);
                List<TripData> tripList = new ArrayList<>();

                for (int j = 0; j < tripIDtrip.size(); j++) {
                    if (routeIDtrip.get(j).equals(targetRouteId)) {
                        String currentTripId = tripIDtrip.get(j);
                        
                        if (tripToDeparture.containsKey(currentTripId)) {
                            String time = tripToDeparture.get(currentTripId);
                            List<Integer> days2 = serviceToDays.getOrDefault(serviceIDtrip.get(j), new ArrayList<>());
                            
                            // Collect them as one unit
                            tripList.add(new TripData(currentTripId, time, days2));
                        }
                    }
                }

                // --- SORTING STEP ---
                // This sorts the objects by the "time" string
                tripList.sort((a, b) -> a.time.compareTo(b.time));

                // --- WRITING STEP ---
                // Now extract them back into the format you need for the file
                List<String> sortedIDs = new ArrayList<>();
                List<String> sortedTimes = new ArrayList<>();
                List<List<Integer>> sortedDays = new ArrayList<>();

                for (TripData td : tripList) {
                    sortedIDs.add(td.id);
                    sortedTimes.add(td.time);
                    sortedDays.add(td.days);
                }

                writer.write(targetRouteId + ";" + routeFull.get(i) + ";");
                writer.write(sortedIDs + ";" + sortedTimes + ";" + sortedDays);
                writer.newLine();

                if (i % 10 == 0) System.out.println("Processed route " + i + " of " + routeID.size());
            }

            writer.close(); 
        } catch (Exception e) {
            System.out.println("Ben, we have a problem: " + e.getMessage());
        }

        // new stop file - id, name, lat, lon
        try {
            File stopsFile = new File("stops/" + agency + "_stops.txt");
            // Use BufferedWriter for significantly better performance
            BufferedWriter writer = new BufferedWriter(new FileWriter(stopsFile));

            for (int i = 0; i < stopID.size(); i++)
            {
                writer.write(stopID.get(i) + "," + stopName.get(i) + "," + stopLat.get(i) + "," + stopLon.get(i));
                writer.newLine();
            }

            writer.close(); 
        } catch (Exception e) {
            System.out.println("Ben, we have a problem: " + e.getMessage());
        }
    }
}

class ServiceProfile {
    boolean[] activeDays = new boolean[7];
    int start = Integer.MAX_VALUE;
    int end = Integer.MIN_VALUE;
}

class TripData {
    String id;
    String time;
    List<Integer> days;

    TripData(String id, String time, List<Integer> days) {
        this.id = id;
        this.time = time;
        this.days = days;
    }
}

class StopTime {
    String tripId;
    String departureTime;
    String stopId;

    StopTime(String tripId, String departureTime, String stopId) {
        this.tripId = tripId;
        this.departureTime = departureTime;
        this.stopId = stopId;
    }

    public String getDepartureTime() {
        return departureTime;
    }
}