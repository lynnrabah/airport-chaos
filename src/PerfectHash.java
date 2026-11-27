import java.util.Random;

/**
 * Module G: static two-level (FKS-style) perfect hashing for a FIXED set of integer keys.
 *
 * Level 1: universal hash h(k) = ((a*k + b) mod p) mod m splits the n keys into m = n buckets.
 *          Parameters are re-chosen until the sum of squared bucket sizes is below 4n (linear storage).
 * Level 2: a bucket holding s keys gets its own table of size s^2 and its own (a, b),
 *          re-chosen until there is NO collision inside it. With size s^2 each try succeeds
 *          with probability at least 1/2, so only a few retries are needed.
 * Lookup = two hash evaluations, one array access, one key comparison: O(1) worst case.
 */
public class PerfectHash {

    /** The 20 fixed crisis rule IDs of the handout. */
    public static final int[] RULE_IDS = {101, 107, 113, 127, 131, 149, 157, 163, 173, 181,
                                          193, 211, 223, 227, 239, 251, 263, 269, 277, 283};

    private static final long PRIME = 2147483647L;   // 2^31 - 1, larger than every key; a*k stays inside a long
    private static final long SEED = 12345L;         // fixed seed -> same parameters on every run

    /** One second-level table. */
    private static class Bucket {
        int count;          // s = number of keys in this bucket
        int size;           // s^2 (0 for an empty bucket)
        long a, b;          // second-level universal hash parameters
        int[] table;        // stores the actual keys
        boolean[] filled;   // which slots are in use (so key 0 or negatives would also work)
        int attempts;       // how many (a, b) pairs were tried until collision-free
    }

    private final Random rnd = new Random(SEED);
    private final int n;            // number of keys
    private final int m;            // number of level-1 buckets
    private long a1, b1;            // level-1 parameters
    private int level1Attempts = 0;
    private final Bucket[] buckets;

    public PerfectHash(int[] keys) {
        this.n = keys.length;
        this.m = Math.max(1, n);

        // The key set must contain distinct integers.
        for (int i = 0; i < n; i++)
            for (int j = i + 1; j < n; j++)
                if (keys[i] == keys[j])
                    throw new IllegalArgumentException("Duplicate key " + keys[i]);

        // ---- Level 1: choose (a1, b1) until the squared bucket sizes sum to less than 4n ----
        int[] counts;
        long sumSquares;
        do {
            a1 = randomA();
            b1 = randomB();
            level1Attempts++;
            counts = new int[m];
            for (int k : keys) counts[hash(a1, b1, k, m)]++;
            sumSquares = 0;
            for (int c : counts) sumSquares += (long) c * c;
        } while (n > 0 && sumSquares >= 4L * n);

        // Group the keys by bucket
        int[][] members = new int[m][];
        for (int i = 0; i < m; i++) members[i] = new int[counts[i]];
        int[] fill = new int[m];
        for (int k : keys) {
            int bi = hash(a1, b1, k, m);
            members[bi][fill[bi]++] = k;
        }

        // ---- Level 2: for each bucket find collision-free parameters in a table of size s^2 ----
        buckets = new Bucket[m];
        for (int i = 0; i < m; i++) {
            Bucket bk = new Bucket();
            bk.count = counts[i];
            bk.size = counts[i] * counts[i];
            if (bk.count > 0) {
                boolean ok;
                do {
                    bk.a = randomA();
                    bk.b = randomB();
                    bk.attempts++;
                    bk.table = new int[bk.size];
                    bk.filled = new boolean[bk.size];
                    ok = true;
                    for (int k : members[i]) {
                        int slot = hash(bk.a, bk.b, k, bk.size);
                        if (bk.filled[slot]) { ok = false; break; }   // collision -> try new parameters
                        bk.filled[slot] = true;
                        bk.table[slot] = k;
                    }
                } while (!ok);
            }
            buckets[i] = bk;
        }
    }

    // ------------------------------------------------------------------
    // Hash helpers
    // ------------------------------------------------------------------

    private long randomA() {                       // a in [1, p-1]
        return 1 + (long) (rnd.nextDouble() * (PRIME - 1));
    }

    private long randomB() {                       // b in [0, p-1]
        return (long) (rnd.nextDouble() * PRIME);
    }

    /** h(k) = ((a*k + b) mod p) mod mod. */
    private static int hash(long a, long b, int key, int mod) {
        long k = ((key % PRIME) + PRIME) % PRIME;  // map the key into [0, p-1]
        return (int) (((a * k + b) % PRIME) % mod);
    }

    // ------------------------------------------------------------------
    // Lookup
    // ------------------------------------------------------------------

    /**
     * True if the key is in the set. We must compare the stored key: an ABSENT key can hash
     * to an occupied slot, and a slot being occupied does not mean it holds THIS key.
     */
    public boolean contains(int key) {
        Bucket bk = buckets[hash(a1, b1, key, m)];
        if (bk.count == 0) return false;
        int slot = hash(bk.a, bk.b, key, bk.size);
        return bk.filled[slot] && bk.table[slot] == key;
    }

    /** One-line explanation of a lookup, for TRACE and the demo. */
    public String lookupTrace(int key) {
        int bi = hash(a1, b1, key, m);
        Bucket bk = buckets[bi];
        if (bk.count == 0) {
            return key + ": level-1 bucket " + bi + " is empty -> ABSENT";
        }
        int slot = hash(bk.a, bk.b, key, bk.size);
        String stored = bk.filled[slot] ? String.valueOf(bk.table[slot]) : "empty";
        return key + ": bucket " + bi + " (s=" + bk.count + ", table size " + bk.size + "), slot " + slot
                + " holds " + stored + " -> " + (contains(key) ? "PRESENT" : "ABSENT");
    }

    // ------------------------------------------------------------------
    // Reporting
    // ------------------------------------------------------------------

    /** Storage = m (first-level table) + sum of s^2 over all buckets. */
    public int totalStorage() {
        int total = m;
        for (Bucket bk : buckets) total += bk.size;
        return total;
    }

    public String describe() {
        StringBuilder sb = new StringBuilder();
        sb.append("Static perfect hashing (FKS), n = ").append(n).append(" keys, p = ").append(PRIME).append('\n');
        sb.append("Level 1: a=").append(a1).append(" b=").append(b1).append(" m=").append(m)
          .append(" (parameters chosen after ").append(level1Attempts).append(" attempt(s))\n");
        long secondLevel = 0;
        int totalAttempts = 0;
        for (int i = 0; i < m; i++) {
            Bucket bk = buckets[i];
            if (bk.count == 0) continue;
            secondLevel += bk.size;
            totalAttempts += bk.attempts;
            sb.append(String.format("  bucket %2d: s=%d size=%d a=%d b=%d attempts=%d%n",
                    i, bk.count, bk.size, bk.a, bk.b, bk.attempts));
        }
        sb.append("Second-level attempts in total: ").append(totalAttempts).append('\n');
        sb.append("Storage: level 1 = ").append(m).append(", level 2 = ").append(secondLevel)
          .append(", total = ").append(totalStorage()).append(" slots for ").append(n).append(" keys");
        return sb.toString();
    }

    /** Output of the PERFECT_HASH command: build the table for the 20 rule IDs, then test present and absent keys. */
    public static String demo(boolean trace) {
        PerfectHash ph = new PerfectHash(RULE_IDS);
        StringBuilder sb = new StringBuilder(ph.describe()).append('\n');

        int found = 0;
        for (int id : RULE_IDS) {
            if (ph.contains(id)) found++;
            if (trace) sb.append("  ").append(ph.lookupTrace(id)).append('\n');
        }
        sb.append("Present lookups: ").append(found).append(" of ").append(RULE_IDS.length).append(" found\n");

        int[] absent = {100, 102, 150, 200, 284, 0, -7, 1000};   // none of these is in the set
        for (int id : absent) {
            sb.append("  ").append(ph.lookupTrace(id)).append('\n');
        }
        return sb.toString();
    }
}
