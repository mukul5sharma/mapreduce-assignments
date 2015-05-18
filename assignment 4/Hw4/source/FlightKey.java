

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.WritableComparable;

// Class to represent each FlightKey
public class FlightKey implements WritableComparable<FlightKey>{
	private String flight = null;
	private IntWritable month = new IntWritable();
	
	public String getFlightName(){
		return flight;
	}
	
	public IntWritable getMonth(){
		return month;
	}
	
	public void setFlightName(String id){
		this.flight = id;
	}
	
	public void setMonth(IntWritable month){
		this.month = month;
	}
	
	public FlightKey(){
		setFlightName(null);
		setMonth(null);
	}
	
	public void set(String id, IntWritable month ){
		this.flight = id;
		this.month = month;
	}

	@Override
	public void readFields(DataInput arg0) throws IOException {
		// TODO Auto-generated method stub
		if(month == null){
			month = new IntWritable();
		}
		flight = arg0.readUTF();
		month.readFields(arg0);
	}

	@Override
	public void write(DataOutput arg0) throws IOException {
		// TODO Auto-generated method stub
		arg0.writeUTF(flight);
		month.write(arg0);
		
	}

	@Override
	public int compareTo(FlightKey arg0) {
		// TODO Auto-generated method stub
		int result = this.flight.compareTo(arg0.getFlightName());
		if(result == 0)
			return this.month.compareTo(arg0.getMonth());
		else		
		return result;
	}
	
	@Override
	public int hashCode(){
		int result = flight.hashCode()*5+month.hashCode();
		return result;
	}
		
}
