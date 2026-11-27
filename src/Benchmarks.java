import java.util.Locale;
import java.util.Random;

//Timing experiments for the report (BENCHMARK command, or java -cp out Benchmarks).
//Benchmarks measure SPEED; TestRunner checks CORRECTNESS.
//All data is synthetic and built with a fixed seed (12345), so every run uses the same inputs.
//Each time is the AVERAGE of several repeats, after one warm-up run that is thrown away
//(the first run of Java code is slower because the JIT compiler has not optimized it yet).
public class Benchmarks {

	static final long SEED=12345;

	public static void main(String[] args) {
		System.out.println(runAll());
	}

	public static String runAll() {
		StringBuilder s=new StringBuilder();
		s.append("=== BENCHMARKS ===\n");
		s.append(environment()).append("\n\n");
		s.append(hashBenchmark()).append("\n");
		s.append(bstBenchmark()).append("\n");
		s.append(sortBenchmark()).append("\n");
		s.append(matrixBenchmark());
		return s.toString();
	}

	static String environment() {
		Runtime rt=Runtime.getRuntime();
		return "Environment: Java "+System.getProperty("java.version")
			+" | "+System.getProperty("os.name")+" "+System.getProperty("os.arch")
			+" | "+rt.availableProcessors()+" processors"
			+" | max heap "+(rt.maxMemory()/(1024*1024))+" MB";
	}

	//------------------------------------------------------------------
	//A. Hash table: probes and lookup time at different load factors
	//------------------------------------------------------------------
	public static String hashBenchmark() {
		StringBuilder s=new StringBuilder();
		int capacity=20000;
		int repeats=20;
		s.append("--- Module A: hash lookups vs load factor (table size m=").append(capacity)
			.append(", ").append(repeats).append(" repeats) ---\n");
		s.append(String.format(Locale.ROOT,"%-6s %-7s %-16s %-16s %-14s%n","load","n","probes/hit","probes/miss","ns/lookup"));
		double[] loads={0.1,0.2,0.3,0.4,0.5};
		for (double load : loads) {
			final int n=(int)(load*capacity);
			final FlightRegistry reg=new FlightRegistry(capacity);  //big enough: never resizes up to 0.5
			final String[] hits=new String[n];
			final String[] misses=new String[n];
			for (int i=0;i<n;i++) {
				hits[i]="HB"+i;
				misses[i]="MISS"+i;
				reg.insert(new Flight(hits[i],"BM","AAA","BBB","R1",i*10L,i*10L+5,1));
			}
			long p0=reg.totalProbes;
			for (int i=0;i<n;i++) {
				reg.find(hits[i]);
			}
			double probesHit=(double)(reg.totalProbes-p0)/n;
			p0=reg.totalProbes;
			for (int i=0;i<n;i++) {
				reg.find(misses[i]);
			}
			double probesMiss=(double)(reg.totalProbes-p0)/n;
			long ns=timeNs(()->{
				for (int i=0;i<n;i++) {
					reg.find(hits[i]);
				}
			},repeats);
			s.append(String.format(Locale.ROOT,"%-6.1f %-7d %-16.3f %-16.3f %-14.1f%n",load,n,probesHit,probesMiss,(double)ns/n));
		}
		s.append("Expected (linear probing): about (1+1/(1-a))/2 probes per hit and (1+1/(1-a)^2)/2 per miss.\n");
		s.append("The registry resizes above a=0.5, so higher loads are never reached in normal use.\n");
		return s.toString();
	}

	//------------------------------------------------------------------
	//B. BST: sorted (worst case, a chain) vs shuffled (random) insertion order
	//------------------------------------------------------------------
	public static String bstBenchmark() {
		StringBuilder s=new StringBuilder();
		int repeats=10;
		s.append("--- Module B: BST, sorted vs shuffled insertion order (").append(repeats).append(" repeats) ---\n");
		s.append(String.format(Locale.ROOT,"%-6s %-9s %-8s %-18s %-18s%n","n","order","height","insert all (ms)","search all (ms)"));
		int[] sizes={500,1000,2000};
		for (int n : sizes) {
			Flight[] sorted=new Flight[n];
			for (int i=0;i<n;i++) {
				sorted[i]=new Flight("BS"+i,"BM","AAA","BBB","R1",i*10L,i*10L+5,1);
			}
			Flight[] shuffled=shuffle(sorted,SEED);
			s.append(bstRow(n,"sorted",sorted,repeats));
			s.append(bstRow(n,"shuffled",shuffled,repeats));
		}
		s.append("Sorted input builds a chain (height = n), so search is O(n); shuffled input gives height ~ O(log n).\n");
		s.append("Insert also runs the conflict check (RunwayBST.findConflicts), which visits every flight starting\n");
		s.append("before the new one, so insertion grows like O(n) per flight in BOTH orders.\n");
		return s.toString();
	}

	private static String bstRow(int n,String label,final Flight[] order,int repeats) {
		long insertNs=timeNs(()->{
			RunwayBST b=new RunwayBST();
			for (Flight f : order) {
				b.insert(f);
			}
		},repeats);
		final RunwayBST tree=new RunwayBST();
		for (Flight f : order) {
			tree.insert(f);
		}
		long searchNs=timeNs(()->{
			for (Flight f : order) {
				tree.search(f);
			}
		},repeats);
		return String.format(Locale.ROOT,"%-6d %-9s %-8d %-18.3f %-18.3f%n",n,label,tree.height(),insertNs/1e6,searchNs/1e6);
	}

	//Fisher-Yates shuffle on a copy (we write it ourselves instead of using a library)
	private static Flight[] shuffle(Flight[] a,long seed) {
		Flight[] r=new Flight[a.length];
		for (int i=0;i<a.length;i++) {
			r[i]=a[i];
		}
		Random rnd=new Random(seed);
		for (int i=r.length-1;i>0;i--) {
			int j=rnd.nextInt(i+1);
			Flight tmp=r[i];
			r[i]=r[j];
			r[j]=tmp;
		}
		return r;
	}

	//------------------------------------------------------------------
	//D. Sorting: counting and radix vs a comparison sort (our own merge sort)
	//------------------------------------------------------------------
	public static String sortBenchmark() {
		StringBuilder s=new StringBuilder();
		int repeats=10;
		s.append("--- Module D: counting / radix vs merge sort baseline (").append(repeats).append(" repeats) ---\n");
		s.append(String.format(Locale.ROOT,"%-8s %-22s %-20s %-24s%n","n","counting by prio (ms)","radix by ID (ms)","merge sort by ID (ms)"));
		int[] sizes={1000,10000,100000};
		Random rnd=new Random(SEED);
		for (int n : sizes) {
			final Flight[] data=new Flight[n];
			for (int i=0;i<n;i++) {
				//IDs like B610505: one capital letter + 6 digits
				String id=(char)('A'+rnd.nextInt(26))+String.valueOf(100000+rnd.nextInt(900000));
				data[i]=new Flight(id,"BM","AAA","BBB","R1",i*10L,i*10L+5,1+rnd.nextInt(3));
			}
			long counting=timeNs(()->SortUtil.countingSortByPriority(data,false),repeats);
			long radix=timeNs(()->SortUtil.radixSortById(data,false),repeats);
			long merge=timeNs(()->mergeSortById(data),repeats);
			s.append(String.format(Locale.ROOT,"%-8d %-22.3f %-20.3f %-24.3f%n",n,counting/1e6,radix/1e6,merge/1e6));
		}
		s.append("Counting sort is O(n+k) with k=3; radix is O(d(n+k)) with d=7 characters, k=128.\n");
		s.append("Merge sort compares IDs, so it is O(n log n), the lower bound for comparison sorts.\n");
		return s.toString();
	}

	//Baseline comparison sort, written by us (Arrays.sort is not allowed in the project)
	static Flight[] mergeSortById(Flight[] a) {
		Flight[] copy=new Flight[a.length];
		for (int i=0;i<a.length;i++) {
			copy[i]=a[i];
		}
		Flight[] buffer=new Flight[a.length];
		mergeSort(copy,buffer,0,a.length-1);
		return copy;
	}

	private static void mergeSort(Flight[] a,Flight[] buffer,int lo,int hi) {
		if (lo>=hi) return;
		int mid=(lo+hi)/2;
		mergeSort(a,buffer,lo,mid);
		mergeSort(a,buffer,mid+1,hi);
		int i=lo;
		int j=mid+1;
		int k=lo;
		while (i<=mid && j<=hi) {
			if (a[i].flightId.compareTo(a[j].flightId)<=0) {
				buffer[k++]=a[i++];
			}
			else {
				buffer[k++]=a[j++];
			}
		}
		while (i<=mid) buffer[k++]=a[i++];
		while (j<=hi) buffer[k++]=a[j++];
		for (k=lo;k<=hi;k++) {
			a[k]=buffer[k];
		}
	}

	//------------------------------------------------------------------
	//F. Matrices: classical O(n^3) vs Strassen O(n^2.81)
	//------------------------------------------------------------------
	public static String matrixBenchmark() {
		StringBuilder s=new StringBuilder();
		int repeats=10;
		s.append("--- Module F: classical vs Strassen (").append(repeats).append(" repeats) ---\n");
		s.append(String.format(Locale.ROOT,"%-6s %-16s %-24s %-22s %-8s%n","n","classical (ms)",
			"Strassen cutoff "+MatrixOps.DEFAULT_CUTOFF+" (ms)","Strassen cutoff 1 (ms)","result"));
		int[] sizes={32,64,128,256};
		for (int n : sizes) {
			final long[][] A=MatrixOps.randomMatrix(n,SEED+n,9);
			final long[][] B=MatrixOps.randomMatrix(n,SEED+2*n,9);
			long classical=timeNs(()->MatrixOps.classical(A,B),repeats);
			long strassen=timeNs(()->MatrixOps.strassen(A,B,MatrixOps.DEFAULT_CUTOFF),repeats);
			String pure="-";
			if (n<=64) {  //cutoff 1 recurses down to 1x1 blocks: far too slow for big n
				long ns=timeNs(()->MatrixOps.strassen(A,B,1),repeats);
				pure=String.format(Locale.ROOT,"%.3f",ns/1e6);
			}
			boolean match=MatrixOps.equal(MatrixOps.classical(A,B),MatrixOps.strassen(A,B,MatrixOps.DEFAULT_CUTOFF));
			s.append(String.format(Locale.ROOT,"%-6d %-16.3f %-24.3f %-22s %-8s%n",n,classical/1e6,strassen/1e6,pure,match ? "MATCH" : "MISMATCH"));
		}
		s.append("Strassen does 7 recursive products instead of 8, but pays for many additions and new arrays,\n");
		s.append("so on small matrices (and with cutoff 1) the classical algorithm wins.\n");
		return s.toString();
	}

	//------------------------------------------------------------------
	//timing helper
	//------------------------------------------------------------------

	//Runs r once to warm up, then 'repeats' times, and returns the average time in nanoseconds.
	//A Runnable is just "a piece of code passed as a value": ()->{ ... } is the code to time.
	private static long timeNs(Runnable r,int repeats) {
		r.run();
		long total=0;
		for (int i=0;i<repeats;i++) {
			long start=System.nanoTime();
			r.run();
			total+=System.nanoTime()-start;
		}
		return total/repeats;
	}
}
