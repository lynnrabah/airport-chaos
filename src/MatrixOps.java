import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Module F: origin-to-destination flight-count matrix, classical multiplication and Strassen.
 * A x A is only a two-leg ROUTE-COUNT PROXY (not passengers, not guaranteed connections).
 */
public class MatrixOps {

    /** Below this size Strassen switches to the classical algorithm (recursion overhead is not worth it). */
    public static final int DEFAULT_CUTOFF = 32;

    // ------------------------------------------------------------------
    // Building the matrix
    // ------------------------------------------------------------------

    /** Unique airport codes (origins and destinations) in lexicographic order, using our own sorted insertion. */
    public static String[] airportCodes(List<Flight> all) {
        ArrayList<String> codes = new ArrayList<>();
        for (Flight f : all) {
            insertSorted(codes, f.origin);
            insertSorted(codes, f.destination);
        }
        return codes.toArray(new String[0]);
    }

    /** Binary search for the position, skip duplicates, insert to keep the list sorted. */
    private static void insertSorted(ArrayList<String> list, String code) {
        int lo = 0, hi = list.size() - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            int c = list.get(mid).compareTo(code);
            if (c == 0) return;                // already present
            if (c < 0) lo = mid + 1; else hi = mid - 1;
        }
        list.add(lo, code);
    }

    /** Index of a code inside the sorted array, or -1. */
    private static int indexOf(String[] codes, String code) {
        int lo = 0, hi = codes.length - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            int c = codes[mid].compareTo(code);
            if (c == 0) return mid;
            if (c < 0) lo = mid + 1; else hi = mid - 1;
        }
        return -1;
    }

    /**
     * Cell [i][j] = number of flights from airport i to airport j.
     * Counts every flight in the list (pass only active flights if you want to ignore cancelled ones).
     */
    public static long[][] buildFlightMatrix(List<Flight> all, String[] codes) {
        int n = codes.length;
        long[][] a = new long[n][n];
        for (Flight f : all) {
            int i = indexOf(codes, f.origin);
            int j = indexOf(codes, f.destination);
            if (i >= 0 && j >= 0) {
                a[i][j]++;
            }
        }
        return a;
    }

    // ------------------------------------------------------------------
    // Classical multiplication: O(N^3)
    // ------------------------------------------------------------------

    public static long[][] classical(long[][] A, long[][] B) {
        int n = A.length;
        int m = B[0].length;
        int inner = B.length;
        if (A.length > 0 && A[0].length != inner) {
            throw new IllegalArgumentException("Dimension mismatch for multiplication.");
        }
        long[][] C = new long[n][m];
        for (int i = 0; i < n; i++) {
            for (int k = 0; k < inner; k++) {      // i-k-j order is friendlier to the cache
                long aik = A[i][k];
                if (aik == 0) continue;
                for (int j = 0; j < m; j++) {
                    C[i][j] += aik * B[k][j];
                }
            }
        }
        return C;
    }

    // ------------------------------------------------------------------
    // Strassen: 7 sub-products instead of 8, T(n) = 7T(n/2) + O(n^2) = O(n^2.807)
    // ------------------------------------------------------------------

    /** Wrapper: zero-pad to a power of two, recurse, crop the result back to the original size. */
    public static long[][] strassen(long[][] A, long[][] B, int cutoff) {
        int n = A.length;
        if (n == 0) return new long[0][0];
        if (A[0].length != n || B.length != n || B[0].length != n) {
            throw new IllegalArgumentException("Strassen wrapper expects two square matrices of the same size.");
        }
        int size = nextPowerOfTwo(n);
        long[][] C = strassenRec(pad(A, size), pad(B, size), size, Math.max(cutoff, 1));
        return crop(C, n);
    }

    private static long[][] strassenRec(long[][] A, long[][] B, int n, int cutoff) {
        if (n <= cutoff || n == 1) {
            return classical(A, B);
        }
        int h = n / 2;
        // Split into quadrants
        long[][] a11 = block(A, 0, 0, h), a12 = block(A, 0, h, h);
        long[][] a21 = block(A, h, 0, h), a22 = block(A, h, h, h);
        long[][] b11 = block(B, 0, 0, h), b12 = block(B, 0, h, h);
        long[][] b21 = block(B, h, 0, h), b22 = block(B, h, h, h);

        // The seven sub-products
        long[][] m1 = strassenRec(add(a11, a22), add(b11, b22), h, cutoff);
        long[][] m2 = strassenRec(add(a21, a22), b11, h, cutoff);
        long[][] m3 = strassenRec(a11, sub(b12, b22), h, cutoff);
        long[][] m4 = strassenRec(a22, sub(b21, b11), h, cutoff);
        long[][] m5 = strassenRec(add(a11, a12), b22, h, cutoff);
        long[][] m6 = strassenRec(sub(a21, a11), add(b11, b12), h, cutoff);
        long[][] m7 = strassenRec(sub(a12, a22), add(b21, b22), h, cutoff);

        // Recombine
        long[][] c11 = add(sub(add(m1, m4), m5), m7);
        long[][] c12 = add(m3, m5);
        long[][] c21 = add(m2, m4);
        long[][] c22 = add(add(sub(m1, m2), m3), m6);

        long[][] C = new long[n][n];
        place(C, c11, 0, 0);
        place(C, c12, 0, h);
        place(C, c21, h, 0);
        place(C, c22, h, h);
        return C;
    }

    private static long[][] add(long[][] x, long[][] y) {
        int n = x.length;
        long[][] r = new long[n][n];
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++)
                r[i][j] = x[i][j] + y[i][j];
        return r;
    }

    private static long[][] sub(long[][] x, long[][] y) {
        int n = x.length;
        long[][] r = new long[n][n];
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++)
                r[i][j] = x[i][j] - y[i][j];
        return r;
    }

    /** Copies the h x h block whose top-left corner is (row, col). */
    private static long[][] block(long[][] m, int row, int col, int h) {
        long[][] r = new long[h][h];
        for (int i = 0; i < h; i++)
            for (int j = 0; j < h; j++)
                r[i][j] = m[row + i][col + j];
        return r;
    }

    /** Writes a small block into the big matrix at (row, col). */
    private static void place(long[][] big, long[][] small, int row, int col) {
        for (int i = 0; i < small.length; i++)
            for (int j = 0; j < small.length; j++)
                big[row + i][col + j] = small[i][j];
    }

    private static int nextPowerOfTwo(int n) {
        int p = 1;
        while (p < n) p <<= 1;
        return p;
    }

    private static long[][] pad(long[][] m, int size) {
        long[][] r = new long[size][size];          // new cells are 0
        for (int i = 0; i < m.length; i++)
            for (int j = 0; j < m.length; j++)
                r[i][j] = m[i][j];
        return r;
    }

    private static long[][] crop(long[][] m, int n) {
        long[][] r = new long[n][n];
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++)
                r[i][j] = m[i][j];
        return r;
    }

    // ------------------------------------------------------------------
    // Utilities for tests, benchmarks and the MATRIX demo
    // ------------------------------------------------------------------

    public static boolean equal(long[][] x, long[][] y) {
        if (x.length != y.length) return false;
        for (int i = 0; i < x.length; i++) {
            if (x[i].length != y[i].length) return false;
            for (int j = 0; j < x[i].length; j++)
                if (x[i][j] != y[i][j]) return false;
        }
        return true;
    }

    /** Deterministic random n x n matrix with entries in [0, maxVal]. */
    public static long[][] randomMatrix(int n, long seed, int maxVal) {
        Random rnd = new Random(seed);
        long[][] m = new long[n][n];
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++)
                m[i][j] = rnd.nextInt(maxVal + 1);
        return m;
    }

    private static long sumOf(long[][] m) {
        long s = 0;
        for (long[] row : m) for (long v : row) s += v;
        return s;
    }

    private static void printMatrix(StringBuilder sb, long[][] m, String[] codes) {
        if (codes.length > 20) {
            sb.append("(matrix too large to print)\n");
            return;
        }
        sb.append(String.format("%6s", ""));
        for (String c : codes) sb.append(String.format("%6s", c));
        sb.append('\n');
        for (int i = 0; i < m.length; i++) {
            sb.append(String.format("%6s", codes[i]));
            for (int j = 0; j < m.length; j++) sb.append(String.format("%6d", m[i][j]));
            sb.append('\n');
        }
    }

    /** Output of the MATRIX command: correctness check on odd/even sizes, then the real airport matrix. */
    public static String demo(List<Flight> all, boolean trace) {
        StringBuilder sb = new StringBuilder();

        sb.append("--- Classical vs Strassen on random matrices (cutoff 2, so Strassen really recurses) ---\n");
        int[] sizes = {3, 4, 5, 7, 8, 13};
        for (int n : sizes) {
            long[][] A = randomMatrix(n, 1000 + n, 9);
            long[][] B = randomMatrix(n, 2000 + n, 9);
            boolean ok = equal(classical(A, B), strassen(A, B, 2));
            sb.append(String.format("n=%2d (%s): %s%n", n, (n % 2 == 0 ? "even" : "odd "), ok ? "MATCH" : "MISMATCH"));
        }

        String[] codes = airportCodes(all);
        long[][] A = buildFlightMatrix(all, codes);
        int n = codes.length;
        sb.append("--- Airport flight-count matrix A (N = ").append(n)
          .append(" airports, ").append(sumOf(A)).append(" flights) ---\n");
        if (trace) printMatrix(sb, A, codes);

        long t0 = System.nanoTime();
        long[][] c1 = classical(A, A);
        long t1 = System.nanoTime();
        long[][] c2 = strassen(A, A, DEFAULT_CUTOFF);
        long t2 = System.nanoTime();

        sb.append("A x A is a two-leg route-count proxy (NOT passengers, NOT guaranteed connections).\n");
        sb.append("Sum of all entries of A x A: ").append(sumOf(c1)).append('\n');
        sb.append("Classical: ").append((t1 - t0) / 1000).append(" us | Strassen (cutoff ")
          .append(DEFAULT_CUTOFF).append("): ").append((t2 - t1) / 1000).append(" us | results ")
          .append(equal(c1, c2) ? "MATCH" : "MISMATCH").append('\n');
        if (trace) {
            sb.append("A x A:\n");
            printMatrix(sb, c1, codes);
        }
        return sb.toString();
    }
}
