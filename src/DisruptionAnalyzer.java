import java.util.ArrayList;

public class DisruptionAnalyzer {
	
	private static class Result{
		int left;
		int right;
		long sum;
	}
	
	public static long[] buildBuckets(ArrayList<Flight> all, long dayStart) {
		long[] buckets=new long[288];
		for (Flight f : all) {
			 if (f.status.equals("DELAYED") || f.status.equals("CANCELLED")) {
				 int i=(int) (f.originalStart-dayStart)/5;
				 if (i>=0 && i<=287) {
					 buckets[i]++;
				 } 
			 }
		}
		return buckets;
	}
	
	public static Result bruteForce(long[] buckets) {
		Result res=new Result();
		res.left=0;
		res.right=0;
		res.sum=buckets[0];
		for (int i=0;i<buckets.length;i++) {
			long s=0;
			for (int j=i;j<buckets.length;j++) {
				s+=buckets[j];
				if (s>res.sum) {
					res.sum=s;
					res.left=i;
					res.right=j;
				}
			}
		}
		return res;
	}
	
	public static Result crossing(long[] buckets,int lower,int mid,int upper){
		//left
		long sum=0;
		long bestLeftSum=buckets[mid];
		int bestStart=mid;
		for (int i=mid;i>=lower;i--){
			sum+=buckets[i];
			if (sum>=bestLeftSum){ //or equal >= : on a tie keep the longer range (earliest start)
				bestLeftSum=sum;
				bestStart=i;
			}
		}
		//right
		sum=0;
		long bestRightSum=buckets[mid+1];
		int bestEnd=mid+1;
		for (int j=mid+1;j<=upper;j++){
			sum+=buckets[j];
			if (sum>bestRightSum){ //strictly > : on a tie keep the shorter range (earliest end)
				bestRightSum=sum;
				bestEnd=j;
			}
		}
		Result res=new Result();
		res.left=bestStart;
		res.right=bestEnd;
		res.sum=bestLeftSum+bestRightSum;
		return res;
	}
	
	public static Result maxSubarray(long[] buckets,int lower,int upper) {
		if (lower==upper) { //or upper, doesn't matter
			Result res=new Result();
			res.left=lower;
			res.right=lower;
			res.sum=buckets[lower];
			return res;
		}
		int mid=(lower+upper)/2;
		Result option1=maxSubarray(buckets,lower,mid);
		Result option2=maxSubarray(buckets,mid+1,upper);
		Result option3=crossing(buckets,lower,mid,upper);
		long max=Math.max(option1.sum,Math.max(option2.sum,option3.sum));
		if (option1.sum==max) return option1;
		if (option3.sum==max) return option3;
		return option2;
	}

	public static Result maxSubarray(long[] buckets) {
		return maxSubarray(buckets,0,buckets.length-1);
	}
}