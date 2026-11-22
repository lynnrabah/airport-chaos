import java.time.*;
import java.time.temporal.ChronoUnit;

public class TimeUtil {

	public static long parse(String text) {
		if (text==null) throw new IllegalArgumentException();
		String[] parts=text.split(" ");
		if (parts.length!=2) throw new IllegalArgumentException();
		String[] date=parts[0].split("-");
		String[] time=parts[1].split(":");
		if (date.length!=3 || time.length!=2) throw new IllegalArgumentException();
		String year=date[0];
		String month=date[1];
		String day=date[2];
		String hours=time[0];
		String minutes=time[1];
		if (year.length()!=4 || month.length()!=2 || day.length()!=2 || hours.length()!=2 || minutes.length()!=2) {
			throw new IllegalArgumentException();
		}
		int YYYY;
		int MM;
		int DD;
		int hh;
		int mm;
		try {
			YYYY=Integer.parseInt(year);
			MM=Integer.parseInt(month);
			DD=Integer.parseInt(day);
			hh=Integer.parseInt(hours);
			mm=Integer.parseInt(minutes);
		}
		catch (NumberFormatException e) {
			throw new IllegalArgumentException();
		}
		try {
			//validating that MM is bounded between 1 and 12, DD between 1 and 31, hh between 0 and 23, mm between 0 and 59
			LocalDate.of(YYYY,MM,DD);
			LocalTime.of(hh,mm);
		}
		catch(DateTimeException e) {
			throw new IllegalArgumentException();
		}
		long dayselapsed=ChronoUnit.DAYS.between(LocalDate.of(1970,1,1),LocalDate.of(YYYY,MM,DD)); //takes into account leap years and 30/31 day months
		long ans=dayselapsed*1440+hh*60+mm;
		return ans;
	}
	
	public static String format(long minutes) {
		if (minutes<0) throw new IllegalArgumentException();
		long dayselapsed=minutes/1440;
		LocalDate D=LocalDate.of(1970,1,1).plusDays(dayselapsed);
		int YYYY=D.getYear();
		int MM=D.getMonthValue();
		int DD=D.getDayOfMonth();
		long leftoverminutes=minutes%1440;
		long hh=leftoverminutes/60;
		long mm=leftoverminutes%60;
		StringBuilder s=new StringBuilder();
		s.append(YYYY).append("-");
		s.append((MM/10==0) ? "0"+MM+"-" : MM+"-");
		s.append((DD/10==0) ? "0"+DD+" " : DD+" ");
		s.append((hh/10==0) ? "0"+hh+":" : hh+":");
		s.append((mm/10==0) ? "0"+mm : mm);
		return s.toString();
	}
}