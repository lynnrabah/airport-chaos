import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

//Automated tests (no JUnit needed): java -cp out TestRunner
//Each test checks PROPERTIES (no overlaps left, unaffected flights unmoved,
//affected = delayed + cancelled...), never numbers memorized from the official CSV,
//so the tests still make sense if the professor uses a different file.
//The tests build their own small CSV files in the temp folder, so data/ is never touched.
public class TestRunner {

	static int passed=0;
	static int failed=0;

	static final String DAY="2026-11-27";
	static final String HEADER="flight_id,airline,origin,destination,runway,start_time,end_time,priority,status";
	static final String[] AIRPORTS={"BEY","CDG","DXB","JFK"};

	static final String[] TESTS={
		"01 TimeUtil parse/format round trip",
		"02 Malformed timestamps are rejected",
		"03 CSV parsing: good rows kept, bad rows reported",
		"04 Duplicate flight IDs are rejected",
		"05 Hash table: 1000 inserts, every flight found",
		"06 Hash table: collision + tombstone keeps the chain alive",
		"07 Hash table: resize keeps every flight",
		"08 BST: root deletion (two children), leaf and one child",
		"09 BST: predecessor / successor at the edges",
		"10 BST: exact boundary does not overlap [s,e)",
		"11 Different runways never conflict",
		"12 Conflicting ADD is refused and changes nothing",
		"13 RESCHEDULE: free slot moves, conflict restores",
		"14 Scenario A (runway closure) from a fresh LOAD",
		"15 Scenario B (emergency insert) from a fresh LOAD",
		"16 Scenario C (mass cancel) from a fresh LOAD",
		"17 LOAD resets everything (scenarios never chain)",
		"18 Counting sort on priority is stable",
		"19 Radix sort on ID gives lexicographic order",
		"20 Max subarray: divide & conquer = brute force",
		"21 Disruption buckets count the right flights",
		"22 Strassen = classical on odd and even sizes",
		"23 Perfect hashing: present found, absent rejected",
		"24 Registry and BSTs stay consistent after updates",
		"25 Official CSV loads cleanly (if data/airport_flights.csv exists)"
	};

	public static void main(String[] args) {
		System.out.println("Running "+TESTS.length+" tests...");
		for (int i=0;i<TESTS.length;i++) {
			int before=failed;
			try {
				runTest(i+1);
			}
			catch (Exception e) {
				failed++;
				System.out.println("FAIL: "+TESTS[i]+" crashed with "+e);
			}
			System.out.println((failed==before ? "  ok    " : "  FAILED")+"  "+TESTS[i]);
		}
		System.out.println();
		System.out.println(passed+" checks passed, "+failed+" failed");
	}

	static void runTest(int n) throws Exception {
		switch (n) {
			case 1: testTimeUtil(); break;
			case 2: testMalformedTimestamps(); break;
			case 3: testCsvParsing(); break;
			case 4: testDuplicateIds(); break;
			case 5: testHashInsertFind(); break;
			case 6: testHashTombstone(); break;
			case 7: testHashResize(); break;
			case 8: testBstDeletion(); break;
			case 9: testBstPredSucc(); break;
			case 10: testBoundary(); break;
			case 11: testCrossRunway(); break;
			case 12: testConflictInsertion(); break;
			case 13: testReschedule(); break;
			case 14: testScenarioA(); break;
			case 15: testScenarioB(); break;
			case 16: testScenarioC(); break;
			case 17: testFreshLoad(); break;
			case 18: testCountingSortStable(); break;
			case 19: testRadixSort(); break;
			case 20: testMaxSubarray(); break;
			case 21: testBuckets(); break;
			case 22: testStrassen(); break;
			case 23: testPerfectHash(); break;
			case 24: testConsistency(); break;
			case 25: testOfficialCsv(); break;
		}
	}

	//records one check, prints FAIL: name when the condition is false
	static void check(String name,boolean condition) {
		if (condition) {
			passed++;
		}
		else {
			failed++;
			System.out.println("FAIL: "+name);
		}
	}

	//------------------------------------------------------------------
	//helpers
	//------------------------------------------------------------------

	//"09:00" -> minutes, always on the test day
	static long t(String hhmm) {
		return TimeUtil.parse(DAY+" "+hhmm);
	}

	static Flight flight(String id,String runway,String from,String to,int priority) {
		return new Flight(id,"TS","BEY","CDG",runway,t(from),t(to),priority);
	}

	//writes a CSV (header + rows) in the temp folder and returns its path
	static String writeCsv(List<String> rows) throws IOException {
		File f=File.createTempFile("cp3test",".csv");
		f.deleteOnExit();
		try (PrintWriter w=new PrintWriter(f)) {
			w.println(HEADER);
			for (String r : rows) {
				w.println(r);
			}
		}
		return f.getPath();
	}

	static String row(String id,String origin,String dest,String runway,long start,long end,int priority) {
		return id+",TS,"+origin+","+dest+","+runway+","+TimeUtil.format(start)+","+TimeUtil.format(end)+","+priority+",SCHEDULED";
	}

	//Standard test day: on each runway, a 5-minute flight every 10 minutes from 08:00 to 15:50.
	//The 5-minute holes between flights give the closure something to fill.
	static List<String> standardRows() {
		List<String> rows=new ArrayList<String>();
		long first=t("08:00");
		for (int r=1;r<=5;r++) {
			for (int i=0;i<48;i++) {
				long s=first+10*i;
				String id="F"+r+(i<10 ? "00" : "0")+i;
				int priority=(i*7+r)%3+1;
				rows.add(row(id,AIRPORTS[i%4],AIRPORTS[(i+1)%4],"R"+r,s,s+5,priority));
			}
		}
		return rows;
	}

	static Scheduler freshScheduler() throws IOException {
		Scheduler s=new Scheduler();
		List<String> errors=new ArrayList<String>();
		s.load(writeCsv(standardRows()),errors);
		return s;
	}

	//true if the flights (in time order) never overlap: each one ends before the next starts
	static boolean noOverlaps(List<Flight> inorder) {
		for (int i=1;i<inorder.size();i++) {
			if (inorder.get(i-1).end>inorder.get(i).start) {
				return false;
			}
		}
		return true;
	}

	static boolean sortedByKey(List<Flight> inorder) {
		for (int i=1;i<inorder.size();i++) {
			Flight a=inorder.get(i-1);
			Flight b=inorder.get(i);
			if (a.start>b.start || (a.start==b.start && a.flightId.compareTo(b.flightId)>=0)) {
				return false;
			}
		}
		return true;
	}

	//copy of every flight's start time, read before a scenario, so we can check who moved
	static long[] startsOf(Flight[] flights) {
		long[] s=new long[flights.length];
		for (int i=0;i<flights.length;i++) {
			s[i]=flights[i].start;
		}
		return s;
	}

	static boolean throwsIllegalArgument(String text) {
		try {
			TimeUtil.parse(text);
			return false;
		}
		catch (IllegalArgumentException e) {
			return true;
		}
	}

	//------------------------------------------------------------------
	//the tests
	//------------------------------------------------------------------

	static void testTimeUtil() {
		String[] samples={"2026-11-27 09:00","1970-01-01 00:00","2024-02-29 23:59","2026-12-31 23:55"};
		for (String s : samples) {
			check("format(parse(x))==x for "+s,TimeUtil.format(TimeUtil.parse(s)).equals(s));
		}
		check("1970-01-01 00:00 is minute 0",TimeUtil.parse("1970-01-01 00:00")==0);
		check("09:05 is 5 minutes after 09:00",t("09:05")-t("09:00")==5);
		check("midnight crossing is 1 minute",TimeUtil.parse("2026-11-28 00:00")-TimeUtil.parse("2026-11-27 23:59")==1);
	}

	static void testMalformedTimestamps() {
		String[] bad={"2026-02-30 09:00","2026-13-01 09:00","2026-11-27 24:00","2026-11-27 09:60",
			"2026-11-27","09:00","2026/11/27 09:00","2026-11-27 9:00","abcd-ef-gh ij:kl","","2026-11-27  09:00"};
		for (String s : bad) {
			check("rejects '"+s+"'",throwsIllegalArgument(s));
		}
		check("rejects null",throwsIllegalArgument(null));
		check("accepts a leap day",!throwsIllegalArgument("2024-02-29 10:00"));
	}

	static void testCsvParsing() throws IOException {
		List<String> rows=new ArrayList<String>();
		rows.add("G1,TS,BEY,CDG,R1,2026-11-27 09:00,2026-11-27 09:05,1,SCHEDULED");
		rows.add("G2,TS,BEY,CDG,R2,2026-11-27 09:00,2026-11-27 09:05,2,SCHEDULED");
		rows.add("");                                                                   //blank: ignored, no error
		rows.add("B1,TS,BEY,CDG,R9,2026-11-27 09:00,2026-11-27 09:05,1,SCHEDULED");    //bad runway
		rows.add("B2,TS,BEY,CDG,R1,2026-11-27 10:00,2026-11-27 10:05,4,SCHEDULED");    //bad priority
		rows.add("B3,TS,BEY,CDG,R1,2026-02-30 10:00,2026-02-30 10:05,1,SCHEDULED");    //impossible date
		rows.add("B4,TS,BEY,CDG,R1,2026-11-27 11:05,2026-11-27 11:00,1,SCHEDULED");    //start after end
		rows.add("B5,TS,BEY,CDG,R1,2026-11-27 12:00,2026-11-27 12:05,1");              //8 columns
		rows.add(",TS,BEY,CDG,R1,2026-11-27 13:00,2026-11-27 13:05,1,SCHEDULED");      //empty ID
		rows.add("G3,TS,BEY,CDG,R3,2026-11-27 09:00,2026-11-27 09:20,3,SCHEDULED");
		List<String> errors=new ArrayList<String>();
		ArrayList<Flight> flights=CsvLoader.load(writeCsv(rows),errors);
		check("3 valid rows loaded",flights.size()==3);
		check("6 bad rows reported",errors.size()==6);
		check("duration comes from the row (20 min), not hardcoded",flights.size()==3 && flights.get(2).duration()==20);
		check("new flights start as SCHEDULED",flights.size()>0 && flights.get(0).status.equals("SCHEDULED"));
		boolean crashed=false;
		try {
			CsvLoader.load("this/file/does/not/exist.csv",new ArrayList<String>());
		}
		catch (IOException e) {
			crashed=true; //a missing file is reported as an IOException, not a crash
		}
		check("missing file gives an IOException",crashed);
	}

	static void testDuplicateIds() throws IOException {
		List<String> rows=new ArrayList<String>();
		rows.add(row("D1","BEY","CDG","R1",t("09:00"),t("09:05"),1));
		rows.add(row("D2","BEY","CDG","R1",t("09:10"),t("09:15"),1));
		rows.add(row("D1","BEY","CDG","R2",t("10:00"),t("10:05"),2)); //same ID, other runway
		Scheduler s=new Scheduler();
		List<String> errors=new ArrayList<String>();
		int loaded=s.load(writeCsv(rows),errors);
		check("only 2 flights loaded",loaded==2);
		check("the duplicate is reported",errors.size()==1 && errors.get(0).contains("D1"));
		check("first D1 kept (R1)",s.getRegistry().find("D1")!=null && s.getRegistry().find("D1").runway.equals("R1"));
		check("R2 tree stayed empty",s.getRunway("R2").size()==0);

		FlightRegistry reg=new FlightRegistry(16);
		check("registry insert returns true the first time",reg.insert(flight("X1","R1","09:00","09:05",1)));
		check("registry insert returns false for the same ID",!reg.insert(flight("X1","R2","10:00","10:05",1)));
		check("size counts the ID once",reg.size==1);
		check("IDs are case-sensitive",reg.insert(flight("x1","R1","11:00","11:05",1)));
	}

	static void testHashInsertFind() {
		FlightRegistry reg=new FlightRegistry(16);
		for (int i=0;i<1000;i++) {
			reg.insert(new Flight("H"+i,"TS","BEY","CDG","R1",i*10,i*10+5,1));
		}
		boolean all=true;
		for (int i=0;i<1000;i++) {
			Flight f=reg.find("H"+i);
			if (f==null || !f.flightId.equals("H"+i)) {
				all=false;
			}
		}
		check("all 1000 flights found",all);
		check("size is 1000",reg.size==1000);
		check("load factor stays <= 0.5",reg.loadFactor()<=0.5);
		check("table grew from 16",reg.m>16);
		check("missing ID returns null",reg.find("NOPE")==null);
		check("liveFlights has exactly size entries",reg.liveFlights().length==1000);
		check("every probe count is at least 1",reg.lastProbes>=1);
	}

	static void testHashTombstone() {
		//find two IDs whose home slot is the same in a table of 64 slots
		FlightRegistry reg=new FlightRegistry(64);
		int[] firstWithHash=new int[64];
		for (int i=0;i<64;i++) {
			firstWithHash[i]=-1;
		}
		String idA=null;
		String idB=null;
		for (int i=0;i<10000 && idA==null;i++) {
			int h=reg.hash("T"+i);
			if (firstWithHash[h]==-1) {
				firstWithHash[h]=i;
			}
			else {
				idA="T"+firstWithHash[h];
				idB="T"+i;
			}
		}
		check("found two colliding IDs",idA!=null);
		int home=reg.hash(idA);
		reg.insert(new Flight(idA,"TS","BEY","CDG","R1",0,5,1));
		reg.insert(new Flight(idB,"TS","BEY","CDG","R1",10,15,1));
		check("one collision counted",reg.collisions==1);
		check("A sits in its home slot",reg.findIndex(idA)==home);
		check("B was pushed to the next slot",reg.findIndex(idB)==(home+1)%reg.m);
		check("finding B took 2 probes",reg.lastProbes==2);

		check("delete A succeeds",reg.delete(idA));
		check("A's slot is now a tombstone",reg.state[home]==FlightRegistry.DELETED);
		check("size went down, deleted went up",reg.size==1 && reg.deleted==1);
		check("A is gone",reg.find(idA)==null);
		check("B is still found past the tombstone",reg.find(idB)!=null);
		check("deleting A again returns false",!reg.delete(idA));

		reg.insert(new Flight(idA,"TS","BEY","CDG","R1",20,25,1));
		check("re-inserted A reuses the tombstone",reg.findIndex(idA)==home);
		check("no tombstones left",reg.deleted==0);
	}

	static void testHashResize() {
		FlightRegistry reg=new FlightRegistry(4);
		reg.insert(new Flight("R-1","TS","BEY","CDG","R1",0,5,1));
		reg.insert(new Flight("R-2","TS","BEY","CDG","R1",10,15,1));
		check("no resize at exactly 0.5",reg.m==4);
		reg.insert(new Flight("R-3","TS","BEY","CDG","R1",20,25,1));
		check("third insert doubles the table",reg.m==8);
		check("all three survive the rehash",reg.find("R-1")!=null && reg.find("R-2")!=null && reg.find("R-3")!=null);
		reg.delete("R-2");
		reg.rehash(16);
		check("rehash drops tombstones",reg.deleted==0 && reg.size==2 && reg.m==16);
		check("flights still found after manual rehash",reg.find("R-1")!=null && reg.find("R-3")!=null);
	}

	static void testBstDeletion() {
		RunwayBST bst=new RunwayBST();
		//insertion order builds this tree:      50
		//                                    20      80
		//                                  10  30  70  90
		int[] minutes={50,20,80,10,30,70,90};
		Flight[] f=new Flight[7];
		for (int i=0;i<7;i++) {
			f[i]=new Flight("K"+minutes[i],"TS","BEY","CDG","R1",minutes[i],minutes[i]+5,1);
			bst.insert(f[i]);
		}
		check("7 nodes, height 3",bst.size()==7 && bst.height()==3);
		check("delete the root (two children)",bst.delete(f[0].start,f[0].flightId));
		check("root flight gone",bst.search(f[0])==null);
		check("6 left and still in order",bst.size()==6 && sortedByKey(bst.inorder()));
		check("delete a leaf (10)",bst.delete(f[3].start,f[3].flightId));
		check("delete a one-child node (20 has only 30 left)",bst.delete(f[1].start,f[1].flightId));
		List<Flight> left=bst.inorder();
		check("remaining 30,70,80,90",left.size()==4 && left.get(0)==f[4] && left.get(1)==f[5] && left.get(2)==f[2] && left.get(3)==f[6]);
		check("deleting a missing key returns false",!bst.delete(999,"NOPE"));
		while (bst.size()>0) {
			Flight first=bst.inorder().get(0);
			bst.delete(first.start,first.flightId);
		}
		check("tree can be emptied completely",bst.size()==0 && bst.height()==0);
	}

	static void testBstPredSucc() {
		RunwayBST bst=new RunwayBST();
		int[] minutes={50,20,80,10,30,70,90};
		Flight[] f=new Flight[7];
		for (int i=0;i<7;i++) {
			f[i]=new Flight("K"+minutes[i],"TS","BEY","CDG","R1",minutes[i],minutes[i]+5,1);
			bst.insert(f[i]);
		}
		//f[3] is the earliest (10), f[6] the latest (90)
		check("earliest flight has no predecessor",bst.predecessor(f[3])==null);
		check("latest flight has no successor",bst.successor(f[6])==null);
		check("a middle flight has both neighbours",bst.predecessor(f[4])!=null && bst.successor(f[4])!=null);
		check("a leaf has a successor through its ancestors (30 -> 50)",bst.successor(f[4])!=null);
		check("the root has both neighbours",bst.predecessor(f[0])!=null && bst.successor(f[0])!=null);
	}

	static void testBoundary() {
		RunwayBST bst=new RunwayBST();
		Flight a=flight("A","R1","09:00","09:20",1);
		bst.insert(a);
		check("[09:20,09:25) does not overlap [09:00,09:20)",bst.findConflicts(t("09:20"),t("09:25")).isEmpty());
		check("[08:55,09:00) does not overlap [09:00,09:20)",bst.findConflicts(t("08:55"),t("09:00")).isEmpty());
		check("[09:19,09:25) overlaps",bst.findConflicts(t("09:19"),t("09:25")).size()==1);
		check("[09:05,09:10) inside overlaps",bst.findConflicts(t("09:05"),t("09:10")).size()==1);
		boolean ok=true;
		try {
			bst.insert(flight("B","R1","09:20","09:25",1));
		}
		catch (IllegalArgumentException e) {
			ok=false;
		}
		check("a flight starting exactly at 09:20 can be inserted",ok && bst.size()==2);
	}

	static void testCrossRunway() {
		Scheduler s=new Scheduler();
		StringBuilder msg=new StringBuilder();
		check("R1 09:00-09:05 added",s.addFlight(flight("C1","R1","09:00","09:05",1),msg));
		check("R2 at the same time is fine",s.addFlight(flight("C2","R2","09:00","09:05",1),msg));
		check("R3 at the same time is fine",s.addFlight(flight("C3","R3","09:00","09:05",1),msg));
		check("each tree holds one flight",s.getRunway("R1").size()==1 && s.getRunway("R2").size()==1 && s.getRunway("R3").size()==1);
	}

	static void testConflictInsertion() {
		Scheduler s=new Scheduler();
		StringBuilder msg=new StringBuilder();
		s.addFlight(flight("E1","R1","09:00","09:10",1),msg);
		msg=new StringBuilder();
		check("overlapping ADD refused",!s.addFlight(flight("E2","R1","09:05","09:15",2),msg));
		check("message names the conflicting flight",msg.toString().contains("E1"));
		check("refused flight is not in the registry",s.getRegistry().find("E2")==null);
		check("refused flight is not in the tree",s.getRunway("R1").size()==1);
		check("CHECK_CONFLICT lists E1",s.checkConflict("R1",t("09:05"),t("09:15")).size()==1);
		check("bad runway name refused",!s.addFlight(flight("E3","R7","09:00","09:05",1),new StringBuilder()));
	}

	static void testReschedule() throws IOException {
		Scheduler s=freshScheduler();
		Flight f=s.getRegistry().find("F1000");          //R1 08:00-08:05, hole at 08:05
		StringBuilder msg=new StringBuilder();
		check("move into the free 08:05 slot",s.rescheduleFlight("F1000",t("08:05"),msg));
		check("times moved, duration kept",f.start==t("08:05") && f.end==t("08:10"));
		check("status became DELAYED",f.status.equals("DELAYED"));
		check("tree still sorted and conflict-free",sortedByKey(s.getRunway("R1").inorder()) && noOverlaps(s.getRunway("R1").inorder()));

		Flight g=s.getRegistry().find("F1001");          //R1 08:10-08:15
		check("move onto F1000's new slot is refused",!s.rescheduleFlight("F1001",t("08:05"),new StringBuilder()));
		check("refused flight kept its time",g.start==t("08:10") && g.status.equals("SCHEDULED"));
		check("refused flight is back in the tree",s.getRunway("R1").search(g)!=null);
		check("missing ID refused",!s.rescheduleFlight("NOPE",t("08:05"),new StringBuilder()));
	}

	static void testScenarioA() throws IOException {
		Scheduler s=freshScheduler();
		CrisisEngine crisis=new CrisisEngine(s);
		long from=t("09:00");
		long to=t("11:00");
		Flight[] all=s.getRegistry().liveFlights();
		long[] before=startsOf(all);

		//which flights SHOULD be affected (computed independently of CrisisEngine)
		int expected=0;
		for (Flight f : all) {
			if (f.runway.equals("R1") && f.isActive() && f.start<to && f.end>from) {
				expected++;
			}
		}
		CrisisReport rep=crisis.runwayClosure("R1",from,to);

		check("affected count matches the overlap rule",rep.affected==expected && expected>0);
		check("affected = delayed + cancelled",rep.affected==rep.delayed+rep.cancelled);
		List<Flight> r1=s.getRunway("R1").inorder();
		check("R1 sorted and conflict-free after recovery",sortedByKey(r1) && noOverlaps(r1));

		boolean nothingInWindow=true;
		for (Flight f : r1) {
			if (f.start<to && f.end>from) {
				nothingInWindow=false;
			}
		}
		check("no active R1 flight inside the closure",nothingInWindow);

		boolean othersUnmoved=true;
		boolean delayRule=true;
		boolean cancelRule=true;
		long totalDelay=0;
		int delayedCount=0;
		int cancelledCount=0;
		for (int i=0;i<all.length;i++) {
			Flight f=all[i];
			boolean wasAffected=f.runway.equals("R1") && f.originalStart<to && f.originalEnd>from;
			if (!wasAffected) {
				if (f.start!=before[i] || !f.status.equals("SCHEDULED")) {
					othersUnmoved=false;
				}
			}
			else if (f.status.equals("DELAYED")) {
				delayedCount++;
				long delay=f.start-f.originalStart;
				totalDelay+=delay;
				if (f.start<to || delay>CrisisEngine.MAX_DELAY_MINUTES || f.duration()!=f.originalEnd-f.originalStart || (f.start-to)%5!=0) {
					delayRule=false;
				}
			}
			else {
				cancelledCount++;
				if (!f.status.equals("CANCELLED") || s.getRunway("R1").search(f)!=null || s.getRegistry().find(f.flightId)!=f) {
					cancelRule=false;
				}
			}
		}
		check("unaffected flights (all runways) did not move",othersUnmoved);
		check("delayed: at/after closure end, <=120 min, same duration, 5-min grid",delayRule);
		check("cancelled: out of the tree, still in the registry",cancelRule);
		check("report totals match the flights",delayedCount==rep.delayed && cancelledCount==rep.cancelled && totalDelay==rep.totalDelayMinutes);

		//priority order: walking the delayed flights in (priority, original start, ID) order,
		//the new slots must keep increasing (every test flight lasts 5 minutes)
		Flight[] delayed=new Flight[rep.delayed];
		int k=0;
		for (Flight f : all) {
			if (f.status.equals("DELAYED")) {
				delayed[k++]=f;
			}
		}
		for (int i=1;i<delayed.length;i++) {        //insertion sort by the processing key
			Flight key=delayed[i];
			int j=i-1;
			while (j>=0 && processedAfter(delayed[j],key)) {
				delayed[j+1]=delayed[j];
				j--;
			}
			delayed[j+1]=key;
		}
		boolean greedyOrder=true;
		for (int i=1;i<delayed.length;i++) {
			if (delayed[i].start<=delayed[i-1].start) {
				greedyOrder=false;
			}
		}
		check("higher priority got the earlier slots",greedyOrder);
	}

	static boolean processedAfter(Flight x,Flight y) {
		if (x.priority!=y.priority) return x.priority>y.priority;
		if (x.originalStart!=y.originalStart) return x.originalStart>y.originalStart;
		return x.flightId.compareTo(y.flightId)>0;
	}

	static void testScenarioB() throws IOException {
		Scheduler s=freshScheduler();
		CrisisEngine crisis=new CrisisEngine(s);
		Flight[] all=s.getRegistry().liveFlights();
		long[] before=startsOf(all);

		Flight em=flight("EM90001","R1","09:02","09:07",1);   //overlaps F1006 (09:00-09:05)
		CrisisReport rep=crisis.emergencyInsert(em);
		check("conflict reported",!rep.conflicts.isEmpty() && rep.conflicts.contains("F1006"));
		Flight placed=s.getRegistry().find("EM90001");
		check("emergency flight inserted somewhere",placed!=null);
		check("inserted LATER than requested, same duration",placed!=null && placed.start>t("09:02") && placed.duration()==5);
		check("inserted into the R1 tree",placed!=null && s.getRunway("R1").search(placed)!=null);
		List<Flight> r1=s.getRunway("R1").inorder();
		check("R1 still conflict-free",noOverlaps(r1));
		boolean nobodyMoved=true;
		for (int i=0;i<all.length;i++) {
			if (all[i].start!=before[i]) {
				nobodyMoved=false;
			}
		}
		check("no existing flight was moved",nobodyMoved);

		CrisisReport ok=crisis.emergencyInsert(flight("EM90002","R2","09:05","09:10",1)); //a hole on R2
		Flight g=s.getRegistry().find("EM90002");
		check("conflict-free request inserted at the requested time",g!=null && g.start==t("09:05") && ok.conflicts.isEmpty());
		CrisisReport dup=crisis.emergencyInsert(flight("EM90002","R3","12:05","12:10",1));
		boolean refused=s.getRunway("R3").size()==48;
		check("duplicate emergency ID refused, R3 untouched",refused && dup.notes.size()>0 && dup.notes.get(0).contains("already exists"));
	}

	static void testScenarioC() throws IOException {
		Scheduler s=freshScheduler();
		CrisisEngine crisis=new CrisisEngine(s);
		long from=t("14:00");
		long to=t("15:00");
		Flight[] all=s.getRegistry().liveFlights();
		int expected=0;
		for (Flight f : all) {
			if (f.isActive() && f.start>=from && f.start<to) {
				expected++;
			}
		}
		CrisisReport rep=crisis.massCancel(from,to);
		check("cancel count matches flights starting in [from,to)",rep.cancelled==expected && rep.affected==expected);

		boolean rightOnes=true;
		boolean boundary=true;
		for (Flight f : all) {
			boolean inWindow=f.start>=from && f.start<to;
			if (inWindow && !f.status.equals("CANCELLED")) rightOnes=false;
			if (!inWindow && f.status.equals("CANCELLED")) rightOnes=false;
			if (f.start==to && f.status.equals("CANCELLED")) boundary=false;
		}
		check("exactly the flights starting in the window are CANCELLED",rightOnes);
		check("a flight starting exactly at 'to' is NOT cancelled (half-open window)",boundary);
		int inTrees=0;
		for (int i=0;i<5;i++) {
			inTrees+=s.getRunway(i).size();
		}
		check("cancelled flights left the trees",inTrees==all.length-rep.cancelled);
		check("cancelled flights stay in the registry",s.getRegistry().size==all.length);
	}

	static void testFreshLoad() throws IOException {
		Scheduler s=new Scheduler();
		CrisisEngine crisis=new CrisisEngine(s);
		String path=writeCsv(standardRows());
		List<String> errors=new ArrayList<String>();
		int n1=s.load(path,errors);
		crisis.runwayClosure("R1",t("09:00"),t("11:00"));
		crisis.massCancel(t("14:00"),t("15:00"));
		int n2=s.load(path,new ArrayList<String>());
		check("same number of flights after reload",n1==n2);
		boolean allScheduled=true;
		for (Flight f : s.getRegistry().liveFlights()) {
			if (!f.status.equals("SCHEDULED") || f.start!=f.originalStart) {
				allScheduled=false;
			}
		}
		check("every flight is SCHEDULED at its original time again",allScheduled);
		int inTrees=0;
		for (int i=0;i<5;i++) {
			inTrees+=s.getRunway(i).size();
		}
		check("all flights back in the trees",inTrees==n2);
	}

	static void testCountingSortStable() {
		int[] pr={2,1,3,1,2,3,1,2};
		Flight[] in=new Flight[pr.length];
		for (int i=0;i<pr.length;i++) {
			in[i]=new Flight("S"+i,"TS","BEY","CDG","R1",i*10,i*10+5,pr[i]);
		}
		Flight[] out=SortUtil.countingSortByPriority(in,false);
		boolean ordered=true;
		boolean stable=true;
		for (int i=1;i<out.length;i++) {
			if (out[i-1].priority>out[i].priority) ordered=false;
			//same priority -> must keep the input order (S1 before S3 before S6)
			if (out[i-1].priority==out[i].priority && out[i-1].start>out[i].start) stable=false;
		}
		check("priorities in order 1,2,3",ordered);
		check("equal priorities keep their input order",stable);
		check("nothing lost",out.length==in.length);
		check("input array not changed",in[0].flightId.equals("S0") && in[1].flightId.equals("S1"));
	}

	static void testRadixSort() {
		String[] ids={"B610505","A10","A1","B2","AB","A","ZZ9","A100","a1","B61050"};
		Flight[] in=new Flight[ids.length];
		for (int i=0;i<ids.length;i++) {
			in[i]=new Flight(ids[i],"TS","BEY","CDG","R1",i*10,i*10+5,1);
		}
		Flight[] out=SortUtil.radixSortById(in,false);
		boolean lexicographic=true;
		for (int i=1;i<out.length;i++) {
			if (out[i-1].flightId.compareTo(out[i].flightId)>0) {
				lexicographic=false;
			}
		}
		check("IDs in exact lexicographic order",lexicographic);
		check("shorter ID first: A < A1 < A10 < A100",out[0].flightId.equals("A") && out[1].flightId.equals("A1") && out[2].flightId.equals("A10") && out[3].flightId.equals("A100"));
		check("nothing lost",out.length==in.length);
	}

	static void testMaxSubarray() {
		long[] known={0,0,3,0,0,2,0,5,0};
		DisruptionAnalyzer.Result r=DisruptionAnalyzer.maxSubarray(known);
		check("known array: sum 10",r.sum==10);
		//tie-break = earliest start: the empty buckets 0 and 1 add 0, so the range starting at 0 ties and wins
		check("known array: buckets 0..7 (earliest start on ties)",r.left==0 && r.right==7);

		long[] zeros=new long[288];
		check("all-zero day gives sum 0 in both algorithms",DisruptionAnalyzer.maxSubarray(zeros).sum==0 && DisruptionAnalyzer.bruteForce(zeros).sum==0);

		Random rnd=new Random(12345);
		boolean sameSum=true;
		boolean sameRange=true;
		for (int trial=0;trial<300;trial++) {
			int n=1+rnd.nextInt(288);
			long[] a=new long[n];
			for (int i=0;i<n;i++) {
				a[i]=(rnd.nextInt(4)==0) ? rnd.nextInt(6) : 0;   //mostly empty buckets, like a real day
			}
			DisruptionAnalyzer.Result dc=DisruptionAnalyzer.maxSubarray(a);
			DisruptionAnalyzer.Result bf=DisruptionAnalyzer.bruteForce(a);
			if (dc.sum!=bf.sum) sameSum=false;
			if (bf.sum>0 && (dc.left!=bf.left || dc.right!=bf.right)) sameRange=false;
		}
		check("300 random days: same best sum",sameSum);
		check("300 random days: same range (tie-break agrees)",sameRange);
	}

	static void testBuckets() {
		long dayStart=t("00:00");
		ArrayList<Flight> list=new ArrayList<Flight>();
		Flight a=flight("U1","R1","09:00","09:05",1);
		Flight b=flight("U2","R1","09:04","09:09",1);
		Flight c=flight("U3","R2","09:05","09:10",1);
		Flight d=flight("U4","R2","10:00","10:05",1);
		a.status="DELAYED";
		b.status="CANCELLED";
		c.status="DELAYED";
		//d stays SCHEDULED, so it must not be counted
		list.add(a);
		list.add(b);
		list.add(c);
		list.add(d);
		long[] buckets=DisruptionAnalyzer.buildBuckets(list,dayStart);
		check("288 buckets of 5 minutes",buckets.length==288);
		check("09:00 and 09:04 share bucket 108",buckets[108]==2);
		check("09:05 starts bucket 109",buckets[109]==1);
		check("scheduled flights are not counted",buckets[120]==0);
		long total=0;
		for (long x : buckets) total+=x;
		check("3 disrupted flights in total",total==3);
	}

	static void testStrassen() {
		int[] sizes={1,2,3,5,7,8,13,17};
		for (int n : sizes) {
			long[][] A=MatrixOps.randomMatrix(n,100+n,9);
			long[][] B=MatrixOps.randomMatrix(n,200+n,9);
			long[][] c=MatrixOps.classical(A,B);
			check("n="+n+" Strassen cutoff 1 = classical",MatrixOps.equal(c,MatrixOps.strassen(A,B,1)));
			check("n="+n+" Strassen cutoff 2 = classical",MatrixOps.equal(c,MatrixOps.strassen(A,B,2)));
		}
		long[][] I={{1,0,0},{0,1,0},{0,0,1}};
		long[][] M={{1,2,3},{4,5,6},{7,8,9}};
		check("identity times M is M",MatrixOps.equal(MatrixOps.strassen(I,M,1),M));
	}

	static void testPerfectHash() {
		PerfectHash ph=new PerfectHash(PerfectHash.RULE_IDS);
		boolean all=true;
		for (int id : PerfectHash.RULE_IDS) {
			if (!ph.contains(id)) all=false;
		}
		check("all 20 rule IDs found",all);
		int[] absent={100,102,150,200,284,0,-7,1000,109,281};
		boolean none=true;
		for (int id : absent) {
			if (ph.contains(id)) none=false;
		}
		check("absent IDs are rejected",none);
		check("storage is linear (< 5n slots)",ph.totalStorage()<5*PerfectHash.RULE_IDS.length);
		PerfectHash again=new PerfectHash(PerfectHash.RULE_IDS);
		check("fixed seed: same storage on every build",again.totalStorage()==ph.totalStorage());
	}

	static void testConsistency() throws IOException {
		Scheduler s=freshScheduler();
		CrisisEngine crisis=new CrisisEngine(s);
		crisis.runwayClosure("R2",t("10:00"),t("11:30"));
		crisis.massCancel(t("13:00"),t("13:30"));
		s.cancelFlight("F3000");
		s.cancelFlight("F3000");                                   //second cancel must be harmless
		s.rescheduleFlight("F4000",t("08:05"),new StringBuilder());
		s.rescheduleFlight("F4001",t("08:05"),new StringBuilder()); //conflict, must be refused
		crisis.emergencyInsert(flight("EM1","R5","09:01","09:06",1));
		s.addFlight(flight("NEW1","R3","16:00","16:05",2),new StringBuilder());

		Flight[] all=s.getRegistry().liveFlights();
		boolean treesOk=true;
		boolean registryOk=true;
		for (int r=0;r<5;r++) {
			RunwayBST bst=s.getRunway(r);
			List<Flight> inorder=bst.inorder();
			if (!sortedByKey(inorder) || !noOverlaps(inorder)) treesOk=false;
			int activeHere=0;
			for (Flight f : all) {
				if (f.isActive() && f.runway.equals(Scheduler.RUNWAY_NAMES[r])) {
					activeHere++;
					if (bst.search(f)==null) registryOk=false;     //active in registry -> must be in its tree
				}
			}
			if (activeHere!=bst.size()) registryOk=false;
			for (Flight f : inorder) {
				if (!f.isActive() || s.getRegistry().find(f.flightId)!=f || !f.runway.equals(Scheduler.RUNWAY_NAMES[r])) {
					registryOk=false;                              //in a tree -> active, same object in registry, right runway
				}
			}
		}
		check("every tree is sorted and conflict-free",treesOk);
		check("active flights <-> tree nodes match one to one",registryOk);
	}

	static void testOfficialCsv() throws IOException {
		File f=new File("data/airport_flights.csv");
		if (!f.exists()) {
			System.out.println("  (skipped: data/airport_flights.csv not found from "+new File(".").getAbsolutePath()+")");
			return;
		}
		Scheduler s=new Scheduler();
		List<String> errors=new ArrayList<String>();
		int n=s.load(f.getPath(),errors);
		check("official CSV: no rejected rows",errors.isEmpty());
		check("official CSV: flights loaded",n>0 && n==s.getRegistry().size);
		int inTrees=0;
		boolean clean=true;
		for (int i=0;i<5;i++) {
			inTrees+=s.getRunway(i).size();
			if (!noOverlaps(s.getRunway(i).inorder())) clean=false;
		}
		check("official CSV: every flight is in a tree",inTrees==n);
		check("official CSV: no overlaps on any runway",clean);
	}
}
