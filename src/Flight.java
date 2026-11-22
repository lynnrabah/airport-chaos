public class Flight {
	
	public final String flightId;
	public final String airline;
	public final String origin;
	public final String destination;
	public final String runway;
	public final int priority;
	public String status;
	public long start;
	public long end;
	public final long originalStart;
	public final long originalEnd;
	
	public Flight(String flightId, String airline, String origin, String destination, String runway, long start, long end, int priority) {
		this.flightId=flightId;
		this.airline=airline;
		this.origin=origin;
		this.destination=destination;
		this.runway=runway;
		this.priority=priority;
		this.status="SCHEDULED";
		this.start=start;
		this.end=end;
		this.originalStart=start;
		this.originalEnd=end;
	}
	
	public long duration() {
		return this.end-this.start;
	}
	
	public boolean isActive() {
		return (!this.status.equals("CANCELLED"));
	}
	
	@Override
	public String toString() {
		StringBuilder s=new StringBuilder();
		s.append(this.flightId).append(" | ");
		s.append(this.runway).append(" | ");
		s.append(TimeUtil.format(this.start)).append(" - ");
		s.append(TimeUtil.format(this.end).substring(11)).append(" | ");
		s.append("P"+this.priority).append(" | ");
		s.append(this.status);
		return s.toString();
	}
}