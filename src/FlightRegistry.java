public class FlightRegistry {
	
	public Flight[] table;
	
	public byte[] state; //3 possible values
	
	private static final byte EMPTY=0;
	private static final byte OCCUPIED=1;
	private static final byte DELETED=2;
	
	public int m; //hash table size
	public int size; 
	public int deleted;
	
	//hashing parameters
	public static final long P=2147483647L; // well known prime (2^31-1) above every encoded key and above m
	public static final long a=48271;      // any value from 1 to P-1
	public static final long b=12345;      // any value from 0 to P-1
	
	public static final long BASE=131; //prime above the biggest ASCII character code (127), to prevent IDs from blending together.
	
	public long totalProbes;
	public long collisions;
	public long lastProbes;
	
	public FlightRegistry(int initialCapacity) {
		this.m=initialCapacity;
		this.table=new Flight[this.m];
		this.state=new byte[this.m];
		this.size=0;
		this.deleted=0;
	}
	
	public long encode(String ID) {
		long k=0;
		for (int i=0;i<ID.length();i++) {
			k*=BASE; //shift the running number one "place" to make room for the next character
			k+=ID.charAt(i);
			k%=P; //keep k below P so it can never overflow
		}
		return k;
	}
	
	public int hash(String ID) {
		long k=encode(ID);
		return (int) (((a*k+b)%P)%this.m);
	}
	
	public double loadFactor() {
	    return (double) this.size/this.m;
	}
	
	public Flight[] liveFlights() {
		Flight[] live=new Flight[this.size];
		int j=0;
		for (int i=0;i<this.m;i++) {
			if (state[i]==OCCUPIED) {
				live[j]=this.table[i];
				j++;
			}
		}
		return live;
	}
	
	public int findIndex(String ID) {
	    lastProbes=0;
	    int HASH=hash(ID);

	    for (int i=0;i<m;i++) {
	        int idx=(HASH+i)%m;
	        lastProbes++;

	        if (state[idx]==EMPTY) {
	            totalProbes+=lastProbes;
	            return -1;
	        }
	        if (state[idx]==OCCUPIED && table[idx].flightId.equals(ID)) {
	            totalProbes+=lastProbes;
	            return idx;
	        }
	    }

	    totalProbes+=lastProbes;
	    return -1;
	}
	
	public Flight find(String ID) {
	    int idx=findIndex(ID);
	    return (idx==-1) ? null : table[idx];
	}
	
	//true if deletion was successful, false if it was unsuccessful
	
	public boolean delete(String ID) {
	    //deletion is not a search operation, so its probes must not count in totalProbes
	    long savedTotalProbes=this.totalProbes;
	    int idx=findIndex(ID);
	    this.totalProbes=savedTotalProbes;
	    
	    if (idx==-1) {
	        return false;
	    }
	    table[idx]=null;
	    state[idx]=DELETED;
	    size--;
	    this.deleted++;
	    return true;
	}
	
	public void rehash(int newCapacity) {
	    Flight[] oldtable=this.table;
	    byte[] oldstate=this.state;
	    int oldm=this.m;

	    this.m=newCapacity;
	    this.table=new Flight[newCapacity];
	    this.state=new byte[newCapacity];
	    this.size=0;
	    this.deleted=0;
	    
	 // save statistics so the reinsertions don't count as user operations
	    long savedTotalProbes=this.totalProbes;
	    long savedCollisions=this.collisions;
	    long savedLastProbes=this.lastProbes;
	    
	    for (int i=0;i<oldm;i++) {
	        if (oldstate[i]==OCCUPIED) {
	            insert(oldtable[i]);
	        }
	    }
	    
	 // restore statistics
	    this.totalProbes=savedTotalProbes;
	    this.collisions=savedCollisions;
	    this.lastProbes=savedLastProbes;
	}
	
	// insert: adds a flight to the table, or returns false if its ID is already there.
	// One walk along the flight's probe chain (starting at its home slot HASH) does two jobs:
	//   1. duplicate check: compare the full IDs of the live flights on the chain
	//   2. slot choice: use the first deleted slot we passed, otherwise the empty slot that ends the chain
	
	public boolean insert(Flight flight) {
	    lastProbes=0;
	    int HASH=hash(flight.flightId);
	    boolean collided=(state[HASH]!=EMPTY);
	    int firstdeleted=-1;
	    int nextoption=-1;

	    for (int i=0;i<m;i++) {
	        int idx=(HASH+i)%m;
	        lastProbes++;

	        if (state[idx]==EMPTY) { //ID is not in the table. Safe to insert
	            nextoption=(firstdeleted!=-1)?firstdeleted:idx; 
	            break;
	        }
	     // deleted: keep searching for a duplicate. We may find it later in the chain
	     // remember only the first deleted slot: it is the closest to the initial hash slot, so searching needs fewer probes
	        else if (state[idx]==DELETED) {
	            if (firstdeleted==-1) {
	                firstdeleted=idx;
	            }
	        }
	     //duplicate. We cannot insert
	        else if (table[idx].flightId.equals(flight.flightId)) {
	            totalProbes+=lastProbes;
	            return false;
	        }
	    }

	    //table has no empty slot but we passed a deleted slot, so we can insert it there
	    if (nextoption==-1 && firstdeleted!=-1) nextoption=firstdeleted;
	    
	    //no empty slot and no deleted slot. Expand the table and try again
	    if (nextoption==-1){
	        totalProbes+=lastProbes;
	        rehash(m*2);
	        return insert(flight);
	    }
	    
	    if (state[nextoption]==DELETED) { //if we're inserting in a deleted slot, decrease number of deleted slots
	        this.deleted--;
	    }
	    
	    //the actual insert 
	    
	    table[nextoption]=flight;
	    state[nextoption]=OCCUPIED;
	    size++;

	    totalProbes+=lastProbes;
	    if (collided) {
	        collisions++;
	    }
	    //if the table is more than half full, also expand it
	    if (loadFactor()>0.5) {
	        rehash(m*2);
	    }
	    return true;
	}

}