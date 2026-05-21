// updater to loop through agencies
import java.util.Scanner;
import java.io.File;
import java.util.ArrayList;

public class Update
{
  public static void main(String [] args)
  {
    ArrayList<String> agencies = new ArrayList<String>();
    ArrayList<String> zips = new ArrayList<String>();

    try
    {
      Scanner s = new Scanner(new File("updater.txt"));
      while (s.hasNextLine())
      {
        String data = s.nextLine();
        data = data.substring(data.indexOf(";") + 1); // skip region
        agencies.add(data.substring(0, data.indexOf(";")));
        data = data.substring(data.indexOf(";") + 1); // skip name
        data = data.substring(data.indexOf(";") + 1);
        zips.add(data);
      }
    }
    catch (Exception e)
    {
      System.out.println("Error - no agencies.txt.");
    }

    for (int i = 0; i < agencies.size(); i++)
    {
      // skip agencies with no feed link
      if (zips.get(i).equals("tba")) { continue; }
      else
      {
        gtfs2 g = new gtfs2(agencies.get(i), zips.get(i));
        g.main(args);
      }
    }
  }
}