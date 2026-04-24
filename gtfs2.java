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
        for (int i = 0; i < days.length; i++)
        {
            days[i] = new ArrayList<String>();
        }
        try {
            URL url = new URL(zipUrl);
            // Open a stream from the URL and wrap it in a ZipInputStream
            ZipInputStream zis = new ZipInputStream(url.openStream());
            ZipEntry entry;
            boolean found = false;

            // Iterate through the files inside the ZIP
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().equals("calendar.txt")) {
                    found = true;
                    // Use Scanner to read the specific ZIP entry stream
                    Scanner s = new Scanner(zis);
                    int z = 0; // set aside first for headers
                    String[] headers2 = {};
                    int serviceIndex = -999;
                    int monIndex = -999;
                    int tueIndex = -999;
                    int wedIndex = -999;
                    int thuIndex = -999;
                    int friIndex = -999;
                    int satIndex = -999;
                    int sunIndex = -999;
                    int startIndex = -999;
                    int endIndex = -999;
                    while (s.hasNextLine())
                    {
                        if (z == 0)
                        {
                            String rawLine = s.nextLine();
                            String cleanLine = rawLine.replace("\uFEFF", "").replaceAll("[^\\x20-\\x7e]", "");
                            cleanLine = cleanLine.replace("\"", ""); // replace quotes

                            System.out.println(cleanLine);
                            
                            headers2 = cleanLine.split(",");
                            List<String> headers = Arrays.asList(headers2);
                            serviceIndex = headers.indexOf("service_id");
                            monIndex = headers.indexOf("monday");
                            tueIndex = headers.indexOf("tuesday");
                            wedIndex = headers.indexOf("wednesday");
                            thuIndex = headers.indexOf("thursday");
                            friIndex = headers.indexOf("friday");
                            satIndex = headers.indexOf("saturday");
                            sunIndex = headers.indexOf("sunday");
                            startIndex = headers.indexOf("start_date");
                            endIndex = headers.indexOf("end_date");
                            z++;
                        }
                        else
                        {
                            String rawLine = s.nextLine();
                            String cleanLine = rawLine.replace("\uFEFF", "").replaceAll("[^\\x20-\\x7e]", "");
                            cleanLine = cleanLine.replace("\"", ""); // replace quotes
                            String [] data = cleanLine.split(",");
                            
                            int calStartData = Integer.parseInt(data[startIndex]);
                            int calEndData = Integer.parseInt(data[endIndex]);

                            // add if service_id is still valid today
                            if ((calEndData >= calDate) && (calStartData <= calDate))
                            {
                                serviceIDcal.add(data[serviceIndex]);
                                days[0].add(data[monIndex]);
                                days[1].add(data[tueIndex]);
                                days[2].add(data[wedIndex]);
                                days[3].add(data[thuIndex]);
                                days[4].add(data[friIndex]);
                                days[5].add(data[satIndex]);
                                days[6].add(data[sunIndex]);
                                calStart.add(Integer.parseInt(data[startIndex]));
                                calEnd.add(Integer.parseInt(data[endIndex]));
                            }
                        }
                    }
                    System.out.println("Calendar loaded");
                }

                else if (entry.getName().equals("calendar_dates.txt") && !found) {
                    Scanner s = new Scanner(zis);
                    int z = 0;
                    int serviceIndex = -1, dateIndex = -1, exceptionIndex = -1;
                    
                    // Formatter for GTFS date format: YYYYMMDD
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");

                    // Track the overall date range found in the file
                    int minDateFound = Integer.MAX_VALUE;
                    int maxDateFound = Integer.MIN_VALUE;

                    Map<String, boolean[]> serviceDayMap = new HashMap<>();

                    while (s.hasNextLine()) {
                        String line = s.nextLine();
                        if (z == 0) {
                            List<String> headers = Arrays.asList(line.split(","));
                            serviceIndex = headers.indexOf("service_id");
                            dateIndex = headers.indexOf("date");
                            exceptionIndex = headers.indexOf("exception_type");
                            z++;
                        } else {
                            String[] data = line.split(",");
                            String serviceId = data[serviceIndex];
                            String rawDate = data[dateIndex];
                            int dateValue = Integer.parseInt(rawDate);
                            int type = Integer.parseInt(data[exceptionIndex]);

                            if (rawDate.equals("20260525")) // skipping holiday service
                            {
                                continue; 
                            }

                            // Only process "service added" entries
                            if (type == 1) {
                                // Update the dynamic range based on current agency feed
                                if (dateValue < minDateFound) minDateFound = dateValue;
                                if (dateValue > maxDateFound) maxDateFound = dateValue;

                                try {
                                    LocalDate date = LocalDate.parse(rawDate, formatter);
                                    int dayIdx = date.getDayOfWeek().getValue() - 1; // 0=Mon, 6=Sun

                                    serviceDayMap.putIfAbsent(serviceId, new boolean[7]);
                                    serviceDayMap.get(serviceId)[dayIdx] = true;
                                } catch (Exception e) {
                                    System.out.println("Invalid date format: " + rawDate);
                                }
                            }
                        }
                    }

                    // Check if we actually found any dates to avoid adding MAX_VALUE to your lists
                    if (minDateFound != Integer.MAX_VALUE) {
                        for (Map.Entry<String, boolean[]> entry1 : serviceDayMap.entrySet()) {
                            serviceIDcal.add(entry1.getKey());
                            boolean[] activeDays = entry1.getValue();
                            
                            for (int i = 0; i < 7; i++) {
                                days[i].add(activeDays[i] ? "1" : "0");
                            }
                            
                            // Assign the dynamic range detected from this specific feed
                            calStart.add(minDateFound);
                            calEnd.add(maxDateFound);
                        }
                    }

                    System.out.println("calendar_dates.txt processed as schedule");
                }
            }
        }
        catch (Exception e)
        {
            System.out.println("Error - no calendar.txt.");
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
                System.out.println(entry.getName());
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

                    while ((line = br.readLine()) != null) {
                        
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
                        else
                        {
                            String tripID = columns[tripIndex];
                            Integer index = tripIndexMap.get(tripID);
                            
                            if (index != null && columns[timeIndex].length() > 0) {
                                tripIDtimes.add(tripID);

                                if (columns[timeIndex].substring(4, 5).equals(":")) // times before 10 am that agencies don't put leading zero for
                                {
                                    departuretimes.add("0" + columns[timeIndex].substring(0, 4));
                                }
                                else
                                {
                                    departuretimes.add(columns[timeIndex].substring(0, 5));
                                }
                                stopIDtimes.add(columns[stopIndex]);

                                if (stopHeadIndex != -1  && columns.length > stopHeadIndex && columns[stopHeadIndex].length() > 1)
                                {
                                    headsigntrip.set(index, columns[stopHeadIndex]);
                                }
                            }

                            count++;
                            if (count % 10000 == 0) { // Increased frequency for large files
                                System.out.println("Stop times processed: " + count);
                            }
                        }
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

                writer.write(targetRouteId + ";" + routeName.get(i) + ";");
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