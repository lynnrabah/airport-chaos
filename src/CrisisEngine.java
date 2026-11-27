import java.util.ArrayList;
import java.util.List;

/**
 * Module C: the three mandatory crisis scenarios.
 * Everything goes through the Scheduler so the hash table and the BSTs stay in sync.
 *
 * Scenarios are independent: to reproduce an official result, LOAD again first.
 */
public class CrisisEngine {

    /** A flight may start at most this many minutes after its ORIGINAL start, otherwise it is cancelled. */
    public static final long MAX_DELAY_MINUTES = 120;

    private final Scheduler sched;
    private boolean trace = false;

    public CrisisEngine(Scheduler s) {
        this.sched = s;
    }

    public void setTrace(boolean on) {
        this.trace = on;
    }

    // ------------------------------------------------------------------
    // Scenario A - runway closure
    // ------------------------------------------------------------------

    /**
     * Greedy recovery rule (a teaching heuristic, not an optimal policy):
     *  1. affected = active flights on the runway whose occupancy intersects [from, to)
     *  2. remove all of them from the BST first so they cannot block each other
     *  3. process by priority (1 first), then original start, then flight ID
     *  4. each flight gets the earliest free start at or after 'to' (5-minute steps, same runway,
     *     same duration); if that is more than 120 minutes after its original start -> CANCEL
     * Other runways and unaffected flights are never touched.
     */
    public CrisisReport runwayClosure(String runway, long from, long to) {
        if (from >= to) {
            throw new IllegalArgumentException("Closure start must be before closure end.");
        }
        RunwayBST bst = sched.getRunway(runway);            // throws on an invalid runway name
        String rw = runway.trim().toUpperCase();
        CrisisReport report = new CrisisReport("RUNWAY_CLOSURE " + rw
                + " [" + TimeUtil.format(from) + ", " + TimeUtil.format(to) + ")");

        // Step 1: collect affected flights. Half-open overlap test: start < to AND end > from.
        List<Flight> affected = new ArrayList<>();
        for (Flight f : bst.inorder()) {
            if (f.isActive() && f.start < to && f.end > from) {
                affected.add(f);
            }
        }

        // Step 2: pull them all out of the tree before placing any of them again.
        for (Flight f : affected) {
            if (!bst.delete(f)) {
                report.notes.add("WARNING: " + f.flightId + " was not found in the " + rw + " tree.");
            }
        }

        // Step 3: order = priority, then ORIGINAL start, then ID.
        // (a) tiny stable sort by (originalStart, id), (b) stable counting sort on priority.
        sortByOriginalStart(affected);
        Flight[] ordered = SortUtil.countingSortByPriority(affected.toArray(new Flight[0]), trace);

        // Step 4: place each flight in order.
        for (Flight f : ordered) {
            long oldStart = f.start;
            long oldEnd = f.end;
            long duration = f.duration();
            long slot = sched.earliestFreeSlot(rw, to, duration);
            long delay = slot - f.originalStart;              // delay is measured from the ORIGINAL start
            String oldText = f.flightId + " [" + rw + ", P" + f.priority + "] "
                    + TimeUtil.format(oldStart) + " - " + TimeUtil.format(oldEnd);

            if (delay > MAX_DELAY_MINUTES) {
                f.status = "CANCELLED";                         // stays out of the BST, kept in the registry
                report.cancelled++;
                report.lines.add(oldText + "  ->  CANCELLED (would need +" + delay + " min > "
                        + MAX_DELAY_MINUTES + ")");
            } else {
                f.start = slot;                                 // safe: the flight is outside the tree
                f.end = slot + duration;
                f.status = "DELAYED";
                bst.insert(f);
                report.delayed++;
                report.totalDelayMinutes += delay;
                report.lines.add(oldText + "  ->  " + TimeUtil.format(f.start) + " - "
                        + TimeUtil.format(f.end) + "  DELAYED (+" + delay + " min)");
            }
        }
        report.affected = affected.size();
        report.notes.add("Total delay counts delayed flights only, measured from each original start.");
        return report;
    }

    /** Insertion sort by (originalStart, flightId). The list is small and nearly sorted already. */
    private static void sortByOriginalStart(List<Flight> list) {
        for (int i = 1; i < list.size(); i++) {
            Flight key = list.get(i);
            int j = i - 1;
            while (j >= 0 && startsAfter(list.get(j), key)) {
                list.set(j + 1, list.get(j));
                j--;
            }
            list.set(j + 1, key);
        }
    }

    private static boolean startsAfter(Flight x, Flight y) {
        if (x.originalStart != y.originalStart) {
            return x.originalStart > y.originalStart;
        }
        return x.flightId.compareTo(y.flightId) > 0;
    }

    // ------------------------------------------------------------------
    // Scenario B - emergency insert
    // ------------------------------------------------------------------

    /**
     * 1. Report whether the requested interval conflicts.
     * 2. If it does: list the conflicting IDs and find the earliest feasible LATER slot
     *    (5-minute steps, same duration). Existing flights are never moved.
     * 3. The flight is inserted only into a slot that was verified conflict-free.
     */
    public CrisisReport emergencyInsert(Flight f) {
        sched.runwayIndex(f.runway);                            // validates the runway name
        if (f.start >= f.end) {
            throw new IllegalArgumentException("Start time must be before end time.");
        }
        CrisisReport report = new CrisisReport("EMERGENCY_INSERT " + f.flightId + " on " + f.runway
                + " [" + TimeUtil.format(f.start) + ", " + TimeUtil.format(f.end) + ")");

        if (sched.getRegistry().find(f.flightId) != null) {
            report.notes.add("ERROR: flight ID " + f.flightId + " already exists. Nothing inserted.");
            return report;
        }

        List<Flight> conflicts = sched.checkConflict(f.runway, f.start, f.end);
        StringBuilder msg = new StringBuilder();

        if (conflicts.isEmpty()) {
            report.notes.add("Requested interval is conflict-free.");
            if (sched.addFlight(f, msg)) {
                report.notes.add("Inserted " + f.flightId + " at the requested time.");
            } else {
                report.notes.add("ERROR: insertion failed: " + msg);
            }
            return report;
        }

        // Conflict: report it, then offer (and use) the earliest later slot.
        report.notes.add("Requested interval CONFLICTS with " + conflicts.size() + " flight(s).");
        for (Flight c : conflicts) {
            report.conflicts.add(c.flightId);
            report.lines.add("conflict: " + c.flightId + " " + TimeUtil.format(c.start)
                    + " - " + TimeUtil.format(c.end));
        }
        long duration = f.duration();
        long slot = sched.earliestFreeSlot(f.runway, f.start + Scheduler.STEP_MINUTES, duration);
        report.notes.add("Earliest feasible later slot: " + TimeUtil.format(slot)
                + " - " + TimeUtil.format(slot + duration)
                + " (+" + (slot - f.start) + " min). No existing flight was moved.");

        // The emergency flight is new, so it is created at the chosen slot (its "original" time is the slot).
        Flight placed = new Flight(f.flightId, f.airline, f.origin, f.destination, f.runway,
                slot, slot + duration, f.priority);
        if (sched.addFlight(placed, msg)) {
            report.notes.add("Inserted " + placed.flightId + " at the offered slot.");
        } else {
            report.notes.add("ERROR: insertion at the offered slot failed: " + msg);
        }
        return report;
    }

    // ------------------------------------------------------------------
    // Scenario C - mass cancellation
    // ------------------------------------------------------------------

    /**
     * Cancels every active flight whose START lies in [from, to), on all runways.
     * They leave the BSTs but keep their CANCELLED record in the registry.
     */
    public CrisisReport massCancel(long from, long to) {
        if (from >= to) {
            throw new IllegalArgumentException("Window start must be before window end.");
        }
        CrisisReport report = new CrisisReport("MASS_CANCEL [" + TimeUtil.format(from)
                + ", " + TimeUtil.format(to) + ")");

        for (int i = 0; i < Scheduler.RUNWAY_NAMES.length; i++) {
            // rangeSearch returns a separate list, so cancelling (which edits the tree) is safe.
            List<Flight> inWindow = sched.getRunway(i).rangeSearch(from, to);
            for (Flight f : inWindow) {
                String oldText = f.flightId + " [" + Scheduler.RUNWAY_NAMES[i] + ", P" + f.priority + "] "
                        + TimeUtil.format(f.start) + " - " + TimeUtil.format(f.end);
                if (sched.cancelFlight(f.flightId)) {
                    report.cancelled++;
                    report.lines.add(oldText + "  ->  CANCELLED");
                }
            }
        }
        report.affected = report.cancelled;
        return report;
    }
}
