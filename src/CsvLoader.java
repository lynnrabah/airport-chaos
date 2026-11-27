
import java.util.Scanner;
import java.util.ArrayList;
import java.util.List;
import java.io.*;

public class CsvLoader {

    private static boolean isValidRunway(String s) {
        return s.equals("R1") || s.equals("R2") ||
               s.equals("R3") || s.equals("R4") ||
               s.equals("R5");
    }

    private static boolean isValidPriority(String s) {
        try {
            int p = Integer.parseInt(s);
            return (p >= 1 && p <= 3);
        }
        catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean isValidTime(String s) {
        try {
            TimeUtil.parse(s);
            return true;
        }
        catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static ArrayList<Flight> load(String path, List<String> errors)
            throws IOException {

        ArrayList<Flight> flights = new ArrayList<Flight>();

        try (Scanner reader = new Scanner(new File(path))) {

            // Skip header
            if (reader.hasNextLine())
                reader.nextLine();

            int c = 1;

            while (reader.hasNextLine()) {

                c++;

                String line = reader.nextLine();

                if (line.trim().isEmpty())
                    continue;

                String[] parts = line.split(",", -1);

                if (parts.length != 9) {
                    errors.add("Line " + c + ": expected 9 columns");
                }

                else if (parts[0].isEmpty()) {
                    errors.add("Line " + c + ": empty flight ID");
                }

                else if (!isValidRunway(parts[4])) {
                    errors.add("Line " + c + ": bad runway");
                }

                else if (!isValidPriority(parts[7])) {
                    errors.add("Line " + c + ": bad priority");
                }

                else if (!isValidTime(parts[5]) ||
                         !isValidTime(parts[6])) {
                    errors.add("Line " + c + ": bad timestamp");
                }

                else if (TimeUtil.parse(parts[5]) >=
                         TimeUtil.parse(parts[6])) {
                    errors.add("Line " + c + ": start is not before end");
                }

                else {

                    String flightId = parts[0];
                    String airline = parts[1];
                    String origin = parts[2];
                    String destination = parts[3];
                    String runway = parts[4];

                    long start = TimeUtil.parse(parts[5]);
                    long end = TimeUtil.parse(parts[6]);

                    int priority = Integer.parseInt(parts[7]);

                    flights.add(new Flight(
                        flightId,
                        airline,
                        origin,
                        destination,
                        runway,
                        start,
                        end,
                        priority
                    ));
                }
            }
        }

        return flights;
    }
}
