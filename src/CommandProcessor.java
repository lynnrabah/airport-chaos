
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CommandProcessor {

    private Scheduler sched;
    private CrisisEngine crisis;
    private boolean trace;
    private boolean loaded;

    public CommandProcessor() {
        this.sched = new Scheduler();
        this.crisis = new CrisisEngine(sched);
        this.trace = false;
        this.loaded = false;
    }

    // --------------------------------------------------
    // HELP
    // --------------------------------------------------

    public String doHelp(ArrayList<String> tokenized) {
        if (tokenized.size() != 1)
            return "ERROR: Usage: HELP";

        return """
            Commands:
              LOAD <file>
              LIST_RUNWAY <R1..R5>
              FIND <flight_id>
              ADD <id> <airline> <origin> <dest> <runway> "<start>" "<end>" <priority>
              CANCEL <flight_id>
              RESCHEDULE <flight_id> "<new start>"
              CHECK_CONFLICT <runway> "<start>" "<end>"
              RUNWAY_CLOSURE <runway> "<from>" "<to>"
              EMERGENCY_INSERT <id> <airline> <origin> <dest> <runway> "<start>" "<end>" <priority>
              MASS_CANCEL "<from>" "<to>"
              SORT_PRIORITY
              SORT_ID
              ANALYZE
              BENCHMARK
              STATS
              TRACE ON|OFF
              HELP
              MATRIX
              PERFECT_HASH

            Times are written in quotes as "YYYY-MM-DD HH:MM".
            Example: "2026-11-27 09:00".

            Run LOAD <file> first.
            Every command except LOAD, HELP and TRACE
            requires a loaded file.
            """;
    }

    // --------------------------------------------------
    // TRACE
    // --------------------------------------------------

    public String doTrace(ArrayList<String> tokenized) {
        if (tokenized.size() != 2)
            return "ERROR: Usage: TRACE ON|OFF";

        String t = tokenized.get(1).toUpperCase(Locale.ROOT);

        if (t.equals("ON")) {
            this.trace = true;
        } else if (t.equals("OFF")) {
            this.trace = false;
        } else {
            return "ERROR: Usage: TRACE ON|OFF";
        }

        sched.setTrace(trace);
        crisis.setTrace(trace);

        return trace ? "Trace on" : "Trace off";
    }

    // --------------------------------------------------
    // LOAD
    // --------------------------------------------------

    public String doLoad(ArrayList<String> tokenized) {
        if (tokenized.size() != 2)
            return "ERROR: Usage: LOAD <file>";

        String file = tokenized.get(1);
        ArrayList<String> errors = new ArrayList<>();

        try {
            int count = sched.load(file, errors);

            this.loaded = true;

            // LOAD creates new BSTs, so restore trace.
            sched.setTrace(trace);
            crisis.setTrace(trace);

            StringBuilder s = new StringBuilder();

            s.append("Loaded ").append(count)
             .append(" flight(s) from ").append(file)
             .append("\n");

            if (!errors.isEmpty()) {
                s.append("Rejected rows/flights: ")
                 .append(errors.size()).append("\n");

                for (String error : errors) {
                    s.append("ERROR: ")
                     .append(error).append("\n");
                }
            }

            s.append(sched.stats());

            return s.toString();

        } catch (Exception e) {
            return "ERROR: Could not load file: " + e.getMessage();
        }
    }

    // --------------------------------------------------
    // LIST_RUNWAY
    // --------------------------------------------------

    public String doListRunway(ArrayList<String> tokenized) {
        if (tokenized.size() != 2)
            return "ERROR: Usage: LIST_RUNWAY <R1..R5>";

        String runway =
            tokenized.get(1).toUpperCase(Locale.ROOT);

        RunwayBST bst = sched.getRunway(runway);

        List<Flight> flights = bst.inorder();

        StringBuilder s = new StringBuilder();

        s.append("=== RUNWAY ").append(runway)
         .append(" ===\n");

        s.append("Active flights: ")
         .append(flights.size()).append("\n");

        for (Flight f : flights) {
            s.append(f).append("\n");
        }

        return s.toString();
    }

    // --------------------------------------------------
    // FIND
    // --------------------------------------------------

    public String doFind(ArrayList<String> tokenized) {
        if (tokenized.size() != 2)
            return "ERROR: Usage: FIND <flight_id>";

        String id = tokenized.get(1);

        FlightRegistry registry = sched.getRegistry();

        Flight f = registry.find(id);

        StringBuilder s = new StringBuilder();

        if (f == null) {
            s.append("ERROR: Flight ")
             .append(id).append(" not found.");
        } else {
            s.append("Flight found:\n");

            s.append("ID: ").append(f.flightId).append("\n");
            s.append("Airline: ").append(f.airline).append("\n");
            s.append("Origin: ").append(f.origin).append("\n");
            s.append("Destination: ")
             .append(f.destination).append("\n");
            s.append("Runway: ").append(f.runway).append("\n");
            s.append("Priority: ").append(f.priority).append("\n");
            s.append("Status: ").append(f.status).append("\n");

            s.append("Start: ")
             .append(TimeUtil.format(f.start)).append("\n");

            s.append("End: ")
             .append(TimeUtil.format(f.end)).append("\n");

            s.append("Original start: ")
             .append(TimeUtil.format(f.originalStart))
             .append("\n");

            s.append("Original end: ")
             .append(TimeUtil.format(f.originalEnd))
             .append("\n");

            s.append("Duration: ")
             .append(f.duration()).append(" min");
        }

        if (trace) {
            s.append("\nHash index: ")
             .append(registry.hash(id));

            s.append("\nLookup probes: ")
             .append(registry.lastProbes);
        }

        return s.toString();
    }

    // --------------------------------------------------
    // ADD
    // --------------------------------------------------

    public String doAdd(ArrayList<String> tokenized) {
        if (tokenized.size() != 9)
            return "ERROR: Usage: ADD <id> <airline> "
                 + "<origin> <dest> <runway> "
                 + "\"<start>\" \"<end>\" <priority>";

        Flight f = parseFlight(tokenized);

        StringBuilder message = new StringBuilder();

        if (sched.addFlight(f, message)) {
            return "Flight added successfully:\n" + f;
        }

        return "ERROR: " + message;
    }

    // --------------------------------------------------
    // CANCEL
    // --------------------------------------------------

    public String doCancel(ArrayList<String> tokenized) {
        if (tokenized.size() != 2)
            return "ERROR: Usage: CANCEL <flight_id>";

        String id = tokenized.get(1);

        StringBuilder message = new StringBuilder();

        if (sched.cancelFlight(id, message)) {
            return "Flight " + id + " cancelled successfully.";
        }

        return "ERROR: " + message;
    }

    // --------------------------------------------------
    // RESCHEDULE
    // --------------------------------------------------

    public String doReschedule(ArrayList<String> tokenized) {
        if (tokenized.size() != 3)
            return "ERROR: Usage: RESCHEDULE "
                 + "<flight_id> \"<new start>\"";

        String id = tokenized.get(1);

        long newStart = parseTime(tokenized.get(2));

        StringBuilder message = new StringBuilder();

        if (sched.rescheduleFlight(id, newStart, message)) {
            return "SUCCESS: " + message;
        }

        return "ERROR: " + message;
    }

    // --------------------------------------------------
    // CHECK_CONFLICT
    // --------------------------------------------------

    public String doCheckConflict(ArrayList<String> tokenized) {
        if (tokenized.size() != 4)
            return "ERROR: Usage: CHECK_CONFLICT "
                 + "<runway> \"<start>\" \"<end>\"";

        String runway =
            tokenized.get(1).toUpperCase(Locale.ROOT);

        long start = parseTime(tokenized.get(2));
        long end = parseTime(tokenized.get(3));

        validateInterval(start, end);

        List<Flight> conflicts =
            sched.checkConflict(runway, start, end);

        if (conflicts.isEmpty()) {
            return "NO CONFLICT: Requested interval on "
                 + runway + " is available.";
        }

        StringBuilder s = new StringBuilder();

        s.append("CONFLICT: ")
         .append(conflicts.size())
         .append(" overlapping flight(s) on ")
         .append(runway).append("\n");

        for (Flight f : conflicts) {
            s.append(f).append("\n");
        }

        return s.toString();
    }

    // --------------------------------------------------
    // SCENARIO A: RUNWAY_CLOSURE
    // --------------------------------------------------

    public String doRunwayClosure(ArrayList<String> tokenized) {
        if (tokenized.size() != 4)
            return "ERROR: Usage: RUNWAY_CLOSURE "
                 + "<runway> \"<from>\" \"<to>\"";

        String runway =
            tokenized.get(1).toUpperCase(Locale.ROOT);

        long from = parseTime(tokenized.get(2));
        long to = parseTime(tokenized.get(3));

        validateInterval(from, to);

        CrisisReport report =
            crisis.runwayClosure(runway, from, to);

        return report.toString();
    }

    // --------------------------------------------------
    // SCENARIO B: EMERGENCY_INSERT
    // --------------------------------------------------

    public String doEmergencyInsert(ArrayList<String> tokenized) {
        if (tokenized.size() != 9)
            return "ERROR: Usage: EMERGENCY_INSERT "
                 + "<id> <airline> <origin> <dest> "
                 + "<runway> \"<start>\" \"<end>\" <priority>";

        Flight f = parseFlight(tokenized);

        CrisisReport report = crisis.emergencyInsert(f);

        return report.toString();
    }

    // --------------------------------------------------
    // SCENARIO C: MASS_CANCEL
    // --------------------------------------------------

    public String doMassCancel(ArrayList<String> tokenized) {
        if (tokenized.size() != 3)
            return "ERROR: Usage: MASS_CANCEL "
                 + "\"<from>\" \"<to>\"";

        long from = parseTime(tokenized.get(1));
        long to = parseTime(tokenized.get(2));

        validateInterval(from, to);

        CrisisReport report = crisis.massCancel(from, to);

        return report.toString();
    }

    // --------------------------------------------------
    // SORT_PRIORITY
    // --------------------------------------------------

    public String doSortPriority(ArrayList<String> tokenized) {
        if (tokenized.size() != 1)
            return "ERROR: Usage: SORT_PRIORITY";

        Flight[] flights =
            sched.getRegistry().liveFlights();

        Flight[] sorted =
            SortUtil.countingSortByPriority(flights, trace);

        StringBuilder s = new StringBuilder();

        s.append("=== FLIGHTS SORTED BY PRIORITY ===\n");

        s.append("Total flights: ")
         .append(sorted.length).append("\n");

        for (Flight f : sorted) {
            s.append(f).append("\n");
        }

        return s.toString();
    }

    // --------------------------------------------------
    // SORT_ID
    // --------------------------------------------------

    public String doSortId(ArrayList<String> tokenized) {
        if (tokenized.size() != 1)
            return "ERROR: Usage: SORT_ID";

        Flight[] flights =
            sched.getRegistry().liveFlights();

        Flight[] sorted =
            SortUtil.radixSortById(flights, trace);

        StringBuilder s = new StringBuilder();

        s.append("=== FLIGHTS SORTED BY ID ===\n");

        s.append("Total flights: ")
         .append(sorted.length).append("\n");

        for (Flight f : sorted) {
            s.append(f).append("\n");
        }

        return s.toString();
    }

    // --------------------------------------------------
    // ANALYZE
    // --------------------------------------------------

    public String doAnalyze(ArrayList<String> tokenized) {
        if (tokenized.size() != 1)
            return "ERROR: Usage: ANALYZE";

        ArrayList<Flight> all =
            sched.getRegistry().allFlights();

        if (all.isEmpty()) {
            return "No flights available for analysis.";
        }

        // Find the beginning of the original schedule day.
        long earliest = all.get(0).originalStart;

        for (Flight f : all) {
            if (f.originalStart < earliest) {
                earliest = f.originalStart;
            }
        }

        long dayStart = earliest - Math.floorMod(earliest, 1440L);

        // Create 288 five-minute buckets.
        long[] buckets =
            DisruptionAnalyzer.buildBuckets(all, dayStart);

        // Divide-and-conquer solution.
        long t1 = System.nanoTime();

        DisruptionAnalyzer.Result result =
            DisruptionAnalyzer.maxSubarray(buckets);

        long t2 = System.nanoTime();

        // Brute-force solution for comparison.
        DisruptionAnalyzer.Result brute =
            DisruptionAnalyzer.bruteForce(buckets);

        long t3 = System.nanoTime();

        StringBuilder s = new StringBuilder();

        s.append("=== DISRUPTION ANALYSIS ===\n");

        s.append("Date: ")
         .append(TimeUtil.format(dayStart).substring(0, 10))
         .append("\n");

        if (result.sum == 0) {
            s.append("No delayed or cancelled flights ")
             .append("in the analyzed day.\n");
        } else {
            int left = result.left;
            int right = result.right;

            // Remove unnecessary zero-count buckets.
            while (left < right && buckets[left] == 0) {
                left++;
            }

            while (right > left && buckets[right] == 0) {
                right--;
            }

            long from = dayStart + left * 5L;
            long to = dayStart + (right + 1) * 5L;

            s.append("Maximum disruption window:\n");

            s.append("From: ")
             .append(TimeUtil.format(from)).append("\n");

            s.append("To: ")
             .append(TimeUtil.format(to)).append("\n");

            s.append("Affected flights: ")
             .append(result.sum).append("\n");
        }

        s.append("\nDivide and conquer result: ")
         .append(result.sum).append("\n");

        s.append("Brute force result: ")
         .append(brute.sum).append("\n");

        s.append("Results match: ")
         .append(result.sum == brute.sum ? "YES" : "NO")
         .append("\n");

        s.append("Divide and conquer time: ")
         .append(t2 - t1).append(" ns\n");

        s.append("Brute force time: ")
         .append(t3 - t2).append(" ns\n");

        if (trace) {
            s.append("\n--- Non-empty disruption buckets ---\n");

            for (int i = 0; i < buckets.length; i++) {
                if (buckets[i] > 0) {
                    s.append(TimeUtil.format(dayStart + i * 5L))
                     .append(" : ")
                     .append(buckets[i])
                     .append(" disrupted flight(s)\n");
                }
            }
        }

        return s.toString();
    }

    // --------------------------------------------------
    // BENCHMARK
    // --------------------------------------------------

    public String doBenchmark(ArrayList<String> tokenized) {
        if (tokenized.size() != 1)
            return "ERROR: Usage: BENCHMARK";

        return Benchmarks.runAll();
    }

    // --------------------------------------------------
    // STATS
    // --------------------------------------------------

    public String doStats(ArrayList<String> tokenized) {
        if (tokenized.size() != 1)
            return "ERROR: Usage: STATS";

        return sched.stats();
    }

    // --------------------------------------------------
    // MATRIX
    // --------------------------------------------------

    public String doMatrix(ArrayList<String> tokenized) {
        if (tokenized.size() != 1)
            return "ERROR: Usage: MATRIX";

        ArrayList<Flight> all =
            sched.getRegistry().allFlights();

        if (all.isEmpty()) {
            return "No flights available for matrix analysis.";
        }

        return MatrixOps.demo(all, trace);
    }

    // --------------------------------------------------
    // PERFECT_HASH
    // --------------------------------------------------

    public String doPerfectHash(ArrayList<String> tokenized) {
        if (tokenized.size() != 1)
            return "ERROR: Usage: PERFECT_HASH";

        return PerfectHash.demo(trace);
    }

    // --------------------------------------------------
    // HELPER: CREATE A FLIGHT FROM COMMAND ARGUMENTS
    // Used by ADD and EMERGENCY_INSERT.
    // --------------------------------------------------

    private Flight parseFlight(ArrayList<String> tokenized) {
        String id = tokenized.get(1);
        String airline = tokenized.get(2);
        String origin = tokenized.get(3);
        String destination = tokenized.get(4);

        String runway =
            tokenized.get(5).toUpperCase(Locale.ROOT);

        long start = parseTime(tokenized.get(6));
        long end = parseTime(tokenized.get(7));

        int priority;

        try {
            priority = Integer.parseInt(tokenized.get(8));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                "Priority must be an integer from 1 to 3."
            );
        }

        if (priority < 1 || priority > 3) {
            throw new IllegalArgumentException(
                "Priority must be between 1 and 3."
            );
        }

        if (id.isBlank() || airline.isBlank()
                || origin.isBlank() || destination.isBlank()) {
            throw new IllegalArgumentException(
                "Flight ID, airline, origin and destination "
                + "must not be empty."
            );
        }

        // Radix sort uses ASCII character buckets.
        for (int i = 0; i < id.length(); i++) {
            if (id.charAt(i) < 33 || id.charAt(i) > 126) {
                throw new IllegalArgumentException(
                    "Flight ID must contain printable ASCII "
                    + "characters without spaces."
                );
            }
        }

        sched.runwayIndex(runway);

        validateInterval(start, end);

        return new Flight(
            id,
            airline,
            origin,
            destination,
            runway,
            start,
            end,
            priority
        );
    }

    // --------------------------------------------------
    // HELPER: PARSE AND VALIDATE A TIMESTAMP
    // --------------------------------------------------

    private long parseTime(String text) {
        long time;

        try {
            time = TimeUtil.parse(text);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                "Invalid timestamp: " + text
                + ". Expected YYYY-MM-DD HH:MM."
            );
        }

        if (time < 0) {
            throw new IllegalArgumentException(
                "Dates before 1970-01-01 are not supported."
            );
        }

        return time;
    }

    // --------------------------------------------------
    // HELPER: VALIDATE TIME INTERVAL
    // --------------------------------------------------

    private void validateInterval(long start, long end) {
        if (start >= end) {
            throw new IllegalArgumentException(
                "Start time must be before end time."
            );
        }
    }

    // --------------------------------------------------
    // EXECUTE: MAIN COMMAND DISPATCHER
    // --------------------------------------------------

    public String execute(String line) {
        try {
            if (line == null || line.isBlank()) {
                return "";
            }

            ArrayList<String> tokenized =
                Tokenizer.tokenize(line);

            if (tokenized.isEmpty()) {
                return "";
            }

            String command =
                tokenized.get(0).toUpperCase(Locale.ROOT);

            if (!command.equals("LOAD")
                    && !command.equals("HELP")
                    && !command.equals("TRACE")
                    && !loaded) {

                return "ERROR: run LOAD <file> first.";
            }

            switch (command) {
                case "LOAD":
                    return doLoad(tokenized);

                case "LIST_RUNWAY":
                    return doListRunway(tokenized);

                case "FIND":
                    return doFind(tokenized);

                case "ADD":
                    return doAdd(tokenized);

                case "CANCEL":
                    return doCancel(tokenized);

                case "RESCHEDULE":
                    return doReschedule(tokenized);

                case "CHECK_CONFLICT":
                    return doCheckConflict(tokenized);

                case "RUNWAY_CLOSURE":
                    return doRunwayClosure(tokenized);

                case "EMERGENCY_INSERT":
                    return doEmergencyInsert(tokenized);

                case "MASS_CANCEL":
                    return doMassCancel(tokenized);

                case "SORT_PRIORITY":
                    return doSortPriority(tokenized);

                case "SORT_ID":
                    return doSortId(tokenized);

                case "ANALYZE":
                    return doAnalyze(tokenized);

                case "BENCHMARK":
                    return doBenchmark(tokenized);

                case "STATS":
                    return doStats(tokenized);

                case "TRACE":
                    return doTrace(tokenized);

                case "HELP":
                    return doHelp(tokenized);

                case "MATRIX":
                    return doMatrix(tokenized);

                case "PERFECT_HASH":
                    return doPerfectHash(tokenized);

                default:
                    return "ERROR: unknown command '"
                         + command + "'. Type HELP.";
            }

        } catch (IllegalArgumentException e) {
            return "ERROR: " + e.getMessage();

        } catch (Exception e) {
            return "ERROR: " + e;
        }
    }
}
