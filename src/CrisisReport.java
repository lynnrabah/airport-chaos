import java.util.ArrayList;
import java.util.List;

/**
 * Plain container produced by every crisis scenario (A, B and C).
 * CrisisEngine fills it in, CommandProcessor just prints toString().
 */
public class CrisisReport {

    public String title = "";                          // e.g. "RUNWAY_CLOSURE R1 [..., ...)"
    public List<String> lines = new ArrayList<>();     // one line per affected flight: ID, old time, new time, status
    public List<String> notes = new ArrayList<>();     // free-text remarks (used mostly by Scenario B)
    public List<String> conflicts = new ArrayList<>(); // IDs of conflicting flights (Scenario B)

    // The four totals the handout requires
    public int affected;
    public int delayed;
    public int cancelled;
    public long totalDelayMinutes;

    public CrisisReport() { }

    public CrisisReport(String title) {
        this.title = title;
    }

    /** Formats the whole report for printing. */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== ").append(title).append(" ===\n");
        for (String n : notes) {
            sb.append(n).append('\n');
        }
        if (!conflicts.isEmpty()) {
            sb.append("Conflicting flights: ").append(String.join(", ", conflicts)).append('\n');
        }
        if (!lines.isEmpty()) {
            sb.append("Flight details (old -> new):\n");
            for (String l : lines) {
                sb.append("  ").append(l).append('\n');
            }
        }
        sb.append("Affected: ").append(affected)
          .append(" | Delayed: ").append(delayed)
          .append(" | Cancelled: ").append(cancelled)
          .append(" | Total delay: ").append(totalDelayMinutes).append(" min");
        return sb.toString();
    }
}
