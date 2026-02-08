import java.util.Scanner;
import java.util.ArrayList;
import java.util.Collections;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.io.File;
import java.io.FileWriter;
import java.io.FileNotFoundException;

// ct-nectd
// 2026 reader style update

public class gtfs2
{
    public static void main(String [] args)
    {  
        final String agency = "ct-nectd";
        Scanner in = new Scanner(System.in);

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
        try
        {
            Scanner s = new Scanner(new File("calendar.txt"));
            int z = 0; // skip first
            while (s.hasNextLine())
            {
                if (z == 0)
                {
                    s.nextLine();
                    z++;
                }
                else
                {
                    String data = s.nextLine();
                    String data2 = data;

                    int calEndData = Integer.parseInt(data2.substring(data2.lastIndexOf(",") + 1));

                    // add if service_id is still valid today
                    if (calEndData > calDate)
                    {
                      serviceIDcal.add(data.substring(0, data.indexOf(",")));
                      data = data.substring(data.indexOf(",") + 1);
                      days[0].add(data.substring(0, data.indexOf(",")));
                      data = data.substring(data.indexOf(",") + 1);
                      days[1].add(data.substring(0, data.indexOf(",")));
                      data = data.substring(data.indexOf(",") + 1);
                      days[2].add(data.substring(0, data.indexOf(",")));
                      data = data.substring(data.indexOf(",") + 1);
                      days[3].add(data.substring(0, data.indexOf(",")));
                      data = data.substring(data.indexOf(",") + 1);
                      days[4].add(data.substring(0, data.indexOf(",")));
                      data = data.substring(data.indexOf(",") + 1);
                      days[5].add(data.substring(0, data.indexOf(",")));
                      data = data.substring(data.indexOf(",") + 1);
                      days[6].add(data.substring(0, data.indexOf(",")));
                      data = data.substring(data.indexOf(",") + 1);
                      calStart.add(Integer.parseInt(data.substring(0, data.indexOf(","))));
                      data = data.substring(data.indexOf(",") + 1);
                      calEnd.add(Integer.parseInt(data));
                    }
                }
            }
        }
        catch (Exception e)
        {
            System.out.println("Error - no calendar.txt.");
        }

        ArrayList<String> routeIDtrip = new ArrayList<String>();
        ArrayList<String> serviceIDtrip = new ArrayList<String>();
        ArrayList<String> tripIDtrip = new ArrayList<String>();
        ArrayList<String> headsigntrip = new ArrayList<String>();
        try
        {
            Scanner s = new Scanner(new File("trips.txt"));
            int z = 0; // skip first
            while (s.hasNextLine())
            {
                if (z == 0)
                {
                    s.nextLine();
                    z++;
                }
                else
                {
                    String data = s.nextLine();
                    String data2 = data;
                    data2 = data2.substring(data2.indexOf(",") + 1);
                    String serviceID = data2.substring(0, data2.indexOf(","));

                    // add only if service ID is in list of valid service IDs
                    if (serviceIDcal.contains(serviceID))
                    {
                      routeIDtrip.add(data.substring(0, data.indexOf(",")));
                      data = data.substring(data.indexOf(",") + 1);
                      serviceIDtrip.add(data.substring(0, data.indexOf(",")));
                      data = data.substring(data.indexOf(",") + 1);
                      tripIDtrip.add(data);
                      headsigntrip.add("check route code");
                    }
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
        try
        {
            Scanner s = new Scanner(new File("stop_times.txt"));
            int z = 0; // skip first
            while (s.hasNextLine())
            {
                if (z == 0)
                {
                    s.nextLine();
                    z++;
                }
                else
                {
                    String data = s.nextLine();
                    String data2 = data;
                    String tripID = data2.substring(0, data2.indexOf(","));
                    
                    // add only if valid trip ID
                    if (tripIDtrip.contains(tripID))
                    {
                      tripIDtimes.add(data.substring(0, data.indexOf(",")));
                      data = data.substring(data.indexOf(",") + 1); // skip arrival
                      data = data.substring(data.indexOf(",") + 1);
                      departuretimes.add(data.substring(0, 5));
                      data = data.substring(data.indexOf(",") + 1);
                      stopIDtimes.add(data.substring(0, data.indexOf(",")));
                    }
                }
            }
        }
        catch (Exception e)
        {
            System.out.println("Error - no stop_times.txt.");
        }

        ArrayList<String> stopID = new ArrayList<String>();
        ArrayList<String> stopName = new ArrayList<String>();
        try
        {
            Scanner s = new Scanner(new File("stops.txt"));
            int z = 0; // skip first
            while (s.hasNextLine())
            {
                if (z == 0)
                {
                    s.nextLine();
                    z++;
                }
                else
                {
                    String data = s.nextLine();
                    stopID.add(data.substring(0, data.indexOf(",")));
                    data = data.substring(data.indexOf(",") + 1);
                    stopName.add(data.substring(0, data.indexOf(",")));
                }
            }
        }
        catch (Exception e)
        {
            System.out.println("Error - no stops.txt.");
        }

        ArrayList<String> routeID = new ArrayList<String>();
        ArrayList<String> routeName = new ArrayList<String>();
        ArrayList<String> routeFull = new ArrayList<String>();
        try
        {
            Scanner s = new Scanner(new File("routes.txt"));
            int z = 0; // skip first
            while (s.hasNextLine())
            {
                if (z == 0)
                {
                    s.nextLine();
                    z++;
                }
                else
                {
                    String data = s.nextLine();
                    routeID.add(data.substring(0, data.indexOf(",")));
                    data = data.substring(data.indexOf(",") + 1);
                    routeName.add(data.substring(0, data.indexOf(",")));
                    routeFull.add(data.substring(0, data.indexOf(",")));
                }
            }
        }
        catch (Exception e)
        {
            System.out.println("Error - no routes.txt.");
        }

        // this is where the new reader formatting begins

        // new trip file - trip_id, route, headsign, days of week, stop_ids, stop_times --- other code would need reference to names and locations?
        try
        {
          File tripFile = new File("new-trips.txt");
          FileWriter tripFileWriter = new FileWriter(tripFile);

          tripFileWriter.write(""); // am i allowed to start this with an empty string lol?

          for (int i = 0; i < tripIDtrip.size(); i++) // loop through all valid trip IDs
          {
            tripFileWriter.append(tripIDtrip.get(i) + ";" + routeIDtrip.get(i) + ";" + headsigntrip.get(i) + ";");

            // find what day of week service ID is on?
            ArrayList<Integer> serviceDays = new ArrayList<Integer>();
            for (int k = 0; k < 7; k++)
            {
              for (int l = 0; l < serviceIDcal.size(); l++)
              {
                if (serviceIDcal.get(l).equals(serviceIDtrip.get(i)))
                {
                  if (days[k].get(l).equals("1"))
                  {
                    serviceDays.add(k);
                  }
                }
              }
            }

            tripFileWriter.append(serviceDays + ";");
            
            ArrayList<String> stopIDs = new ArrayList<String>();
            ArrayList<String> stopTimes = new ArrayList<String>();

            for (int j = 0; j < tripIDtimes.size(); j++) // loop through all stop times to add stops to trip
            {
              if (tripIDtimes.get(j).equals(tripIDtrip.get(i)))
              {
                stopIDs.add(stopIDtimes.get(j));
                stopTimes.add(departuretimes.get(j));
              }
            }

            tripFileWriter.append(stopIDs + ";" + stopTimes + "\n");
          }
          tripFileWriter.close();
        }
        catch (Exception e)
        {
          System.out.println("ah shit, it can't make a new trip file, ben you borked something up");
        }

        // new route file - route_id, trip_ids, days of week
        try
        {
          File routeFile = new File("new-routes.txt");
          FileWriter routeFileWriter = new FileWriter(routeFile);

          routeFileWriter.write(""); // am i allowed to start this with an empty string lol?

          for (int i = 0; i < routeID.size(); i++)
          {
            ArrayList<String> routeTrips = new ArrayList<String>();
            ArrayList<String> routeDeparts = new ArrayList<String>();

            ArrayList<ArrayList<Integer>> tripDays = new ArrayList<ArrayList<Integer>>();

            routeFileWriter.append(routeID.get(i) + ";" + routeName.get(i) + ";");

            for (int j = 0; j < tripIDtrip.size(); j++)
            {
                for (int k = 0; k < departuretimes.size(); k++)
                {
                    if (routeIDtrip.get(j).equals(routeID.get(i)))
                    {
                        if (tripIDtrip.get(j).equals(tripIDtimes.get(k)))
                        {
                            routeTrips.add(tripIDtrip.get(j));
                            routeDeparts.add(departuretimes.get(k));

                            // find what day of week service ID is on?
                            ArrayList<Integer> serviceDays = new ArrayList<Integer>();
                            for (int m = 0; m < 7; m++)
                            {
                                for (int l = 0; l < serviceIDcal.size(); l++)
                                {
                                    if (serviceIDcal.get(l).equals(serviceIDtrip.get(j)))
                                    {
                                        if (days[m].get(l).equals("1"))
                                        {
                                            serviceDays.add(m);
                                        }
                                    }
                                }
                            }
                            tripDays.add(serviceDays);

                            break;
                        }
                    }
                }
            }

            routeFileWriter.append(routeTrips + ";" + routeDeparts + ";" + tripDays +"\n");
          }

          routeFileWriter.close();
        }
        catch (Exception e)
        {
            System.out.println("ben fucked up the route list");
        }
    }
}