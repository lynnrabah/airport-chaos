
import java.util.ArrayList;

public class FlightRegistry {

    public Flight[] table;

    public byte[] state; // 3 possible values

    public static final byte EMPTY = 0;
    public static final byte OCCUPIED = 1;
    public static final byte DELETED = 2;

    public int m; // hash table size
    public int size;
    public int deleted;

    // hashing parameters
    public static final long P = 2147483647L;
    public static final long a = 48271;
    public static final long b = 12345;

    public static final long BASE = 131;

    public long totalProbes;
    public long collisions;
    public long lastProbes;

    public FlightRegistry(int initialCapacity) {
        this.m = initialCapacity;
        this.table = new Flight[this.m];
        this.state = new byte[this.m];
        this.size = 0;
        this.deleted = 0;
    }

    public long encode(String ID) {
        long k = 0;

        for (int i = 0; i < ID.length(); i++) {
            k *= BASE;
            k += ID.charAt(i);
            k %= P;
        }

        return k;
    }

    public int hash(String ID) {
        long k = encode(ID);
        return (int) (((a * k + b) % P) % this.m);
    }

    public double loadFactor() {
        return (double) this.size / this.m;
    }

    // Return all occupied flights as an array
    public Flight[] liveFlights() {

        Flight[] live = new Flight[this.size];
        int j = 0;

        for (int i = 0; i < this.m; i++) {
            if (state[i] == OCCUPIED) {
                live[j] = this.table[i];
                j++;
            }
        }

        return live;
    }

    // Return all flights in an ArrayList
    public ArrayList<Flight> allFlights() {

        ArrayList<Flight> result = new ArrayList<>();

        for (int i = 0; i < this.m; i++) {
            if (state[i] == OCCUPIED)
                result.add(table[i]);
        }

        return result;
    }

    public int findIndex(String ID) {

        lastProbes = 0;

        int HASH = hash(ID);

        for (int i = 0; i < m; i++) {

            int idx = (HASH + i) % m;
            lastProbes++;

            if (state[idx] == EMPTY) {
                totalProbes += lastProbes;
                return -1;
            }

            if (state[idx] == OCCUPIED &&
                    table[idx].flightId.equals(ID)) {

                totalProbes += lastProbes;
                return idx;
            }
        }

        totalProbes += lastProbes;
        return -1;
    }

    public Flight find(String ID) {

        int idx = findIndex(ID);

        return (idx == -1) ? null : table[idx];
    }

    // true if deletion was successful, false otherwise
    public boolean delete(String ID) {

        // Deletion probes do not count in totalProbes
        long savedTotalProbes = this.totalProbes;

        int idx = findIndex(ID);

        this.totalProbes = savedTotalProbes;

        if (idx == -1)
            return false;

        table[idx] = null;
        state[idx] = DELETED;

        size--;
        this.deleted++;

        return true;
    }

    public void rehash(int newCapacity) {

        Flight[] oldtable = this.table;
        byte[] oldstate = this.state;
        int oldm = this.m;

        this.m = newCapacity;
        this.table = new Flight[newCapacity];
        this.state = new byte[newCapacity];

        this.size = 0;
        this.deleted = 0;

        // Save statistics so reinsertions don't count
        long savedTotalProbes = this.totalProbes;
        long savedCollisions = this.collisions;
        long savedLastProbes = this.lastProbes;

        for (int i = 0; i < oldm; i++) {
            if (oldstate[i] == OCCUPIED) {
                insert(oldtable[i]);
            }
        }

        // Restore statistics
        this.totalProbes = savedTotalProbes;
        this.collisions = savedCollisions;
        this.lastProbes = savedLastProbes;
    }

    // Insert a flight
    public boolean insert(Flight flight) {

        lastProbes = 0;

        int HASH = hash(flight.flightId);

        boolean collided = (state[HASH] != EMPTY);

        int firstdeleted = -1;
        int nextoption = -1;

        for (int i = 0; i < m; i++) {

            int idx = (HASH + i) % m;

            lastProbes++;

            if (state[idx] == EMPTY) {

                nextoption = (firstdeleted != -1)
                        ? firstdeleted : idx;

                break;
            }

            else if (state[idx] == DELETED) {

                if (firstdeleted == -1)
                    firstdeleted = idx;
            }

            else if (table[idx].flightId.equals(flight.flightId)) {

                totalProbes += lastProbes;
                return false;
            }
        }

        // Table has no empty slot but has a deleted slot
        if (nextoption == -1 && firstdeleted != -1)
            nextoption = firstdeleted;

        // Expand if there is no available slot
        if (nextoption == -1) {

            totalProbes += lastProbes;

            rehash(m * 2);

            return insert(flight);
        }

        // Reusing a deleted slot
        if (state[nextoption] == DELETED)
            this.deleted--;

        // Actual insertion
        table[nextoption] = flight;
        state[nextoption] = OCCUPIED;

        size++;

        totalProbes += lastProbes;

        if (collided)
            collisions++;

        // Expand if load factor exceeds 0.5
        if (loadFactor() > 0.5)
            rehash(m * 2);

        return true;
    }

}
