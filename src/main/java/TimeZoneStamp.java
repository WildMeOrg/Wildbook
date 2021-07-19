import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class TimeZoneStamp {

  public static void main(String[] args) {
    // TODO Auto-generated method stub
        TimeZone timeZone = TimeZone.getTimeZone("America/NewYork");
        SimpleDateFormat date_format=new SimpleDateFormat("yyyy-MM-dd hh:mm:ss z");
        Date date=new Date();
        String current_date_time=date_format.format(date);
        System.out.println("America/NewYork-" +current_date_time);
        
        timeZone = TimeZone.getTimeZone("CST");
        date_format.setTimeZone(timeZone);
        current_date_time=date_format.format(date);
        System.out.println("CST-" +current_date_time);
        
        timeZone = TimeZone.getTimeZone("MST");
        date_format.setTimeZone(timeZone);
        current_date_time=date_format.format(date);
        System.out.println("MST-" +current_date_time);
        
        timeZone = TimeZone.getTimeZone("PST");
        date_format.setTimeZone(timeZone);
        current_date_time=date_format.format(date);
        System.out.println("PST-" +current_date_time);      
  }

}
