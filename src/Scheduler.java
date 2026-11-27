import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The glue of the project: owns the hash table (FlightRegistry) and the five
 * runway BSTs and keeps them synchronized.
 *
 * Invariant: a flight is inside exactly one BST (the one for its runway)
 * if and only if it is active (not CANCELLED). Cancelled flights stay in the
 * registry only.
 *
 * Every mutation (add, cancel, reschedule) goes through this class so that
 * both structures are always updated together.
 */
public class Scheduler {

    public static final String[] RUNWAY_NAMES = {"R1", "R2", "R3", "R4", "R5"};
    public static final long STEP_MINUTES = 5;      // slot search granularity
    private static final int INITIAL_CAPACITY = 64; // small on purpose so a resize happens while loading

    private FlightRegistry registry;
    private RunwayBST[] runways;

    public Scheduler() {
        reset();
    }

    /** Throws away all state and creates fresh, empty structures. */
    private void reset() {
        registry = new FlightRegistry(INITIAL_CAPACITY);
        runways = new RunwayBST[RUNWAY_NAMES.length];
        for (int i = 0; i < runways.length; i++) {
            runways[i] = new RunwayBST();
        }
    }

    // ------------------------------------------------------------------
    // Loading
    // ------------------------------------------------------------------

    /**
     * Builds a brand new registry and 5 new BSTs from the CSV (this is how LOAD
     * resets all state, so scenarios never chain). Returns the number of flights loaded.
     * Rows rejected here (duplicate ID, overlap, bad runway) are reported in errors.
     */
    public int load(String csvPath, List<String> errors) throws IOException {
        // Read first: if the file is missing we keep the current state untouched.
        List<Flight> rows = CsvLoader.load(csvPath, errors);
        reset();
        int loaded = 0;
        for (Flight f : rows) {
            StringBuilder msg = new StringBuilder();
            if (addFlight(f, msg)) {
                loaded++;
            } else {
                errors.add("Flight " + f.flightId + " rejected: " + msg);
            }
        }
        return loaded;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** "R1" -> 0 ... "R5" -> 4. Throws a clear error for anything else. */
    public int runwayIndex(String runway) {
        if (runway != null) {
            String r = runway.trim().toUpperCase();
            for (int i = 0; i < RUNWAY_NAMES.length; i++) {
                if (RUNWAY_NAMES[i].equals(r)) {
                    return i;
                }
            }
        }
        throw new IllegalArgumentException("Invalid runway '" + runway + "' (expected R1..R5)");
    }

    /** "ID1, ID2, ID3" - used in conflict messages and reports. */
    public static String idList(List<Flight> flights) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < flights.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(flights.get(i).flightId);
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // Mutations (always touch both structures)
    // ------------------------------------------------------------------

    /**
     * Conflict check, then insert into both structures.
     * On failure the reason (bad runway, bad times, duplicate ID, conflicting IDs) is appended to message.
     */
    public boolean addFlight(Flight f, StringBuilder message) {
        int idx;
        try {
            idx = runwayIndex(f.runway);
        } catch (IllegalArgumentException e) {
            message.append(e.getMessage());
            return false;
        }
        if (f.start >= f.end) {
            message.append("start time must be before end time");
            return false;
        }
        // Duplicate check comes first, otherwise a duplicate ID with the same times
        // would be reported as a "conflict with itself".
        if (registry.find(f.flightId) != null) {
            message.append("duplicate flight ID ").append(f.flightId);
            return false;
        }
        List<Flight> conflicts = runways[idx].findConflicts(f.start, f.end);
        if (!conflicts.isEmpty()) {
            message.append("interval overlaps on ").append(f.runway)
                   .append(" with ").append(idList(conflicts));
            return false;
        }
        registry.insert(f);
        runways[idx].insert(f);
        return true;
    }

    /** Removes the flight from its BST, marks it CANCELLED, keeps the record in the registry. */
    public boolean cancelFlight(String id) {
        return cancelFlight(id, new StringBuilder());
    }

    /** Same as cancelFlight(id) but explains failures (missing / already cancelled). */
    public boolean cancelFlight(String id, StringBuilder message) {
        Flight f = registry.find(id);
        if (f == null) {
            message.append("flight ").append(id).append(" not found");
            return false;
        }
        if (!f.isActive()) {
            message.append("flight ").append(id).append(" is already CANCELLED");
            return false;
        }
        // Delete BEFORE touching anything: the BST key is (start, id).
        runways[runwayIndex(f.runway)].delete(f.start, f.flightId);
        f.status = "CANCELLED";
        return true;
    }

    /**
     * Moves a flight to newStart, keeping its duration.
     * Remove -> check -> reinsert. If the new interval conflicts the flight is put back unchanged.
     */
    public boolean rescheduleFlight(String id, long newStart, StringBuilder message) {
        Flight f = registry.find(id);
        if (f == null) {
            message.append("flight ").append(id).append(" not found");
            return false;
        }
        if (!f.isActive()) {
            message.append("flight ").append(id).append(" is CANCELLED and cannot be rescheduled");
            return false;
        }
        RunwayBST bst = runways[runwayIndex(f.runway)];
        long newEnd = newStart + f.duration();

        bst.delete(f.start, f.flightId);               // 1. remove with the OLD key
        List<Flight> conflicts = bst.findConflicts(newStart, newEnd);
        if (!conflicts.isEmpty()) {                    // 2. conflict: restore and report
            bst.insert(f);
            message.append("new interval overlaps with ").append(idList(conflicts));
            return false;
        }
        long oldStart = f.start;
        f.start = newStart;                            // 3. change times only while OUTSIDE the tree
        f.end = newEnd;
        f.status = (newStart > f.originalStart) ? "DELAYED" : "SCHEDULED";
        bst.insert(f);                                 // 4. reinsert with the NEW key
        message.append(id).append(" moved from ").append(TimeUtil.format(oldStart))
               .append(" to ").append(TimeUtil.format(newStart))
               .append(" (status ").append(f.status).append(")");
        return true;
    }

    // ------------------------------------------------------------------
    // Queries
    // ------------------------------------------------------------------

    /** Wraps the BST conflict check for the command layer. */
    public List<Flight> checkConflict(String runway, long s, long e) {
        return runways[runwayIndex(runway)].findConflicts(s, e);
    }

    /**
     * Tries from, from+5, from+10, ... until [t, t+duration) is conflict-free.
     * Always terminates because the schedule is finite.
     * (The 120-minute limit is checked in CrisisEngine, not here.)
     */
    public long earliestFreeSlot(String runway, long from, long duration) {
        RunwayBST bst = runways[runwayIndex(runway)];
        long t = from;
        while (!bst.findConflicts(t, t + duration).isEmpty()) {
            t += STEP_MINUTES;
        }
        return t;
    }

    public RunwayBST getRunway(String runway) {
        return runways[runwayIndex(runway)];
    }

    public RunwayBST getRunway(int index) {
        return runways[index];
    }

    public FlightRegistry getRegistry() {
        return registry;
    }

    /** Forwards the TRACE setting to every BST. */
    public void setTrace(boolean on) {
        for (RunwayBST bst : runways) {
            bst.setTrace(on);
        }
    }

    /** Counts per status and per runway, load factor and probe statistics. */
    public String stats() {
        int n = RUNWAY_NAMES.length;
        int[] scheduled = new int[n];
        int[] delayed = new int[n];
        int[] cancelled = new int[n];
        for (Flight f : registry.allFlights()) {
            int i = runwayIndex(f.runway);
            if ("CANCELLED".equals(f.status)) cancelled[i]++;
            else if ("DELAYED".equals(f.status)) delayed[i]++;
            else scheduled[i]++;
        }
        int totalS = 0, totalD = 0, totalC = 0;
        StringBuilder sb = new StringBuilder();
        sb.append("--- Per runway (active = flights currently in the BST) ---\n");
        for (int i = 0; i < n; i++) {
            sb.append(String.format("%s: active=%d scheduled=%d delayed=%d cancelled=%d bstSize=%d bstHeight=%d%n",
                    RUNWAY_NAMES[i], scheduled[i] + delayed[i], scheduled[i], delayed[i], cancelled[i],
                    runways[i].size(), runways[i].height()));
            totalS += scheduled[i];
            totalD += delayed[i];
            totalC += cancelled[i];
        }
        sb.append(String.format("--- Totals: flights=%d SCHEDULED=%d DELAYED=%d CANCELLED=%d%n",
                registry.size(), totalS, totalD, totalC));
        sb.append(String.format(Locale.ROOT,
                "--- Registry: loadFactor=%.3f totalProbes=%d collisions=%d lastProbes=%d",
                registry.loadFactor(), registry.getTotalProbes(), registry.getCollisions(),
                registry.getLastProbes()));
        return sb.toString();
    }
}
