import java.util.Scanner;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.io.*;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.net.URL;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipEntry;
import java.time.LocalDate;
import java.util.Comparator;

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
        //final String agency = "wmata";
        Scanner in = new Scanner(System.in);

        //String zipUrl = "https://api.511.org/transit/datafeeds?api_key=385fee06-02cf-4239-9237-db3fe911b3f7&operator_id=RG";

        // curent date, used for outdated service_ids
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter calDateFormat = DateTimeFormatter.ofPattern("yyyyMMdd"); // date for calendar.txt
        int calDate = Integer.parseInt(calDateFormat.format(now));

        // import data
        ArrayList<String> serviceIDcal = new ArrayList<String>();
        ArrayList<Integer> calStart = new ArrayList<Integer>();
        ArrayList<Integer> calEnd = new ArrayList<Integer>();
        ArrayList<String> [] days = new ArrayList[7];

        for (int i = 0; i < 7; i++) {
            days[i] = new ArrayList<String>();
        }

        // 1. Define a temporary structure to hold merged data
        class ServiceProfile {
            boolean[] activeDays = new boolean[7];
            int start = Integer.MAX_VALUE;
            int end = Integer.MIN_VALUE;
        }

        Map<String, ServiceProfile> masterMap = new HashMap<>();
        int overallMin = Integer.MAX_VALUE;
        int overallMax = Integer.MIN_VALUE;

        try {
            URL url = new URL(zipUrl);
            ZipInputStream zis = new ZipInputStream(url.openStream());
            ZipEntry entry;
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");

            while ((entry = zis.getNextEntry()) != null) {
                String fileName = entry.getName();

                // --- PART 1: PROCESS CALENDAR.TXT ---
                if (fileName.equals("calendar.txt")) {
                    Scanner s = new Scanner(zis);
                    int z = 0;
                    int sIdx = -1, mIdx = -1, tIdx = -1, wIdx = -1, thIdx = -1, fIdx = -1, saIdx = -1, suIdx = -1, startIdx = -1, endIdx = -1;
                    
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
                            String id = data[sIdx];
                            ServiceProfile profile = masterMap.computeIfAbsent(id, k -> new ServiceProfile());
                            
                            // Set weekdays (1 = true)
                            profile.activeDays[0] = data[mIdx].equals("1");
                            profile.activeDays[1] = data[tIdx].equals("1");
                            profile.activeDays[2] = data[wIdx].equals("1");
                            profile.activeDays[3] = data[thIdx].equals("1");
                            profile.activeDays[4] = data[fIdx].equals("1");
                            profile.activeDays[5] = data[saIdx].equals("1");
                            profile.activeDays[6] = data[suIdx].equals("1");
                            
                            profile.start = Math.min(profile.start, Integer.parseInt(data[startIdx]));
                            profile.end = Math.max(profile.end, Integer.parseInt(data[endIdx]));
                        }
                    }
                }

                // --- PART 2: PROCESS CALENDAR_DATES.TXT ---
                else if (fileName.equals("calendar_dates.txt")) {
                    Scanner s = new Scanner(zis);
                    int z = 0;
                    int sIdx = -1, dIdx = -1, eIdx = -1;
                    
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

                            // skip holidays
                            if (rawDate.equals("20260525")) continue;
                            if (rawDate.equals("20260619")) continue;
                            if (rawDate.equals("20260704")) continue;
                            
                            int type = Integer.parseInt(data[eIdx]);
                            int dateVal = Integer.parseInt(rawDate);
                            
                            ServiceProfile profile = masterMap.computeIfAbsent(id, k -> new ServiceProfile());
                            
                            if (type == 1) { // Service Added
                                LocalDate date = LocalDate.parse(rawDate, formatter);
                                profile.activeDays[date.getDayOfWeek().getValue() - 1] = true;
                                profile.start = Math.min(profile.start, dateVal);
                                profile.end = Math.max(profile.end, dateVal);
                            } 
                            // Optional: if type == 2, you could set that specific day to false, 
                            // but for date-based feeds, Type 1 is your primary focus.
                        }
                    }
                }
            }

            // --- PART 3: LOAD INTO YOUR ORIGINAL ARRAYLISTS ---
            for (Map.Entry<String, ServiceProfile> entrySet : masterMap.entrySet()) {
                String id = entrySet.getKey();
                ServiceProfile p = entrySet.getValue();
                
                serviceIDcal.add(id);
                calStart.add(p.start);
                calEnd.add(p.end);
                for (int i = 0; i < 7; i++) {
                    days[i].add(p.activeDays[i] ? "1" : "0");
                }
            }
            System.out.println("Unified calendar data loaded.");

        } catch (Exception e) {
            e.printStackTrace();
        }

        ArrayList<String> routeID = new ArrayList<String>();
        ArrayList<String> routeName = new ArrayList<String>();
        ArrayList<String> routeFull = new ArrayList<String>();
        try {
            URL url = new URL(zipUrl);
            // Open a stream from the URL and wrap it in a ZipInputStream
            ZipInputStream zis = new ZipInputStream(url.openStream());
            ZipEntry entry;
            boolean found = false;

            // Iterate through the files inside the ZIP
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().equals("routes.txt")) {
                    found = true;
                    // Use Scanner to read the specific ZIP entry stream
                    Scanner s = new Scanner(zis);
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
                    break; // We found the file, no need to look at other entries
                }
            }
            
            if (found) {
                System.out.println("Routes loaded from web ZIP");
            } else {
                System.out.println("Error: routes.txt not found inside the ZIP.");
            }
            
            zis.close();
        } catch (Exception e) {
            System.out.println("Error fetching or parsing ZIP: " + e.getMessage());
        }

        ArrayList<String> routeIDtrip = new ArrayList<String>();
        ArrayList<String> serviceIDtrip = new ArrayList<String>();
        ArrayList<String> tripIDtrip = new ArrayList<String>();
        ArrayList<String> headsigntrip = new ArrayList<String>();
        try {
            URL url = new URL(zipUrl);
            // Open a stream from the URL and wrap it in a ZipInputStream
            ZipInputStream zis = new ZipInputStream(url.openStream());
            ZipEntry entry;
            boolean found = false;

            // Iterate through the files inside the ZIP
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().equals("trips.txt")) {
                    found = true;
                    // Use Scanner to read the specific ZIP entry stream
                    Scanner s = new Scanner(zis);
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
        }
        catch (Exception e)
        {
            System.out.println("Error - no trips.txt.");
        }

        ArrayList<String> tripIDtimes = new ArrayList<String>();
        ArrayList<String> departuretimes = new ArrayList<String>();
        ArrayList<String> stopIDtimes = new ArrayList<String>();

        Map<String, Integer> tripIndexMap = new HashMap<>();
        for (int i = 0; i < tripIDtrip.size(); i++) {
            tripIndexMap.put(tripIDtrip.get(i), i);
        }

        List<StopTime> stopTimeList = new ArrayList<>();

        try {
            URL url = new URL(zipUrl);
            ZipInputStream zis = new ZipInputStream(url.openStream());
            ZipEntry entry;
            boolean found = false;

            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().equals("stop_times.txt")) {
                    found = true;
                    
                    // Wrap the ZIP stream so BufferedReader can read it line-by-line
                    BufferedReader br = new BufferedReader(new InputStreamReader(zis));
                    
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

                    break; // Found and processed the file, exit loop
                }
            }

            if (!found) {
                System.out.println("Error: stop_times.txt not found in ZIP.");
            } else {
                System.out.println("Stop times loaded successfully.");
            }
            
            zis.close();

        } catch (IOException e) {
            System.out.println("Error accessing web ZIP: " + e.getMessage());
        }

        ArrayList<String> stopID = new ArrayList<String>();
        ArrayList<String> stopName = new ArrayList<String>();
        ArrayList<String> stopLat = new ArrayList<String>();
        ArrayList<String> stopLon = new ArrayList<String>();
        try {
            URL url = new URL(zipUrl);
            // Open a stream from the URL and wrap it in a ZipInputStream
            ZipInputStream zis = new ZipInputStream(url.openStream());
            ZipEntry entry;
            boolean found = false;

            // Iterate through the files inside the ZIP
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().equals("stops.txt")) {
                    found = true;
                    // Use Scanner to read the specific ZIP entry stream
                    Scanner s = new Scanner(zis);
                    String [] headers2 = {};
                    int idIndex = -999;
                    int nameIndex = -999;
                    int latIndex = -999;
                    int lonIndex = -999;
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

                            System.out.println("stops: " + idIndex + " / " + nameIndex + " / " + latIndex + " / " + lonIndex);

                            z++;
                        }
                        else
                        {
                            String rawLine = s.nextLine();
                            String cleanLine = rawLine.replace("\uFEFF", "").replaceAll("[^\\x20-\\x7e]", "");
                            cleanLine = cleanLine.replace("\"", ""); // replace quotes
                            String [] data = cleanLine.split(",");
                        
                            stopID.add(data[idIndex]);
                            stopName.add(data[nameIndex]);
                            stopLat.add(data[latIndex]);
                            stopLon.add(data[lonIndex]);
                        }
                    }
                    break; // We found the file, no need to look at other entries
                }
            }
            
            if (found) {
                System.out.println("Stops loaded from web ZIP");
            } else {
                System.out.println("Error: stops.txt not found inside the ZIP.");
            }
            
            zis.close();
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