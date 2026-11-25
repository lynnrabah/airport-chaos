public class SortUtil {
	
	public static Flight[] countingSortByPriority(Flight[] live, boolean trace) {
		Flight[] res=new Flight[live.length];
		int[] freq=new int[4]; //priority options are 1 2 3
		for (int i=0;i<live.length;i++){
			freq[live[i].priority]++;
		}
		if (trace) {
		    System.out.println("Counts: p1="+freq[1]+", p2="+freq[2]+", p3="+freq[3]);
		}
		int[] prefix=new int[4];
		prefix[0]=freq[0];
		for (int i=1;i<4;i++){
			prefix[i]=prefix[i-1]+freq[i];
		}
		for (int i=live.length-1;i>=0;i--){
			prefix[live[i].priority]--;
			res[prefix[live[i].priority]]=live[i];
		}
		return res;
	}
	
	//0 means "this ID is too short to have a character here"
	private static int digitAt(String id,int pos){
		if (pos>=id.length()) return 0;
		return id.charAt(pos);
	}

	public static Flight[] countingPassById(Flight[] live,int pos){
		int n=live.length;
		Flight[] res=new Flight[n];
		int[] freq=new int[128]; //buckets 0..127 -> ASCII values
		for (int i=0;i<n;i++){
			freq[digitAt(live[i].flightId,pos)]++;
		}
		int[] prefix=new int[128];
		prefix[0]=freq[0];
		for (int i=1;i<128;i++){
			prefix[i]=prefix[i-1]+freq[i];
		}
		for (int i=n-1;i>=0;i--){
			int d=digitAt(live[i].flightId,pos);
			prefix[d]--;
			res[prefix[d]]=live[i];
		}
		return res;
	}

	public static Flight[] radixSortById(Flight[] live,boolean trace){
		//number of passes=length of the longest ID
		int max=0;
		for (int i=0;i<live.length;i++){
			if (live[i].flightId.length()>max){
				max=live[i].flightId.length();
			}
		}
		for (int pos=max-1;pos>=0;pos--){
			live=countingPassById(live,pos);
			if (trace){
				System.out.println("Pass on character position "+pos+", first IDs:");
				for (int i=0;i<Math.min(5,live.length);i++){
					System.out.println("  "+live[i].flightId);
				}
			}
		}
		return live;
	}


}