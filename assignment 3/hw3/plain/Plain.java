	
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.FloatWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Counters;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.util.GenericOptionsParser;

import au.com.bytecode.opencsv.CSVParse;

public class Plain {

	public enum DelayCounter {
		Total_Delay,  //Global Counter to Store delay sum
		Entities      //Counter to keep track of total flight pairs
	}

	public static class flightDelayPlainMapper extends Mapper<Object, Text, Text, Text>
	{
		@Override
		public void map(Object key, Text value, Context context) throws IOException, InterruptedException 
		{
			if(((LongWritable) key).get()>0){

				//get data from CSV file line by line
				CSVParser parser = new CSVParser();
				String[] lineEntries = parser.parseLine(value.toString());

				//get all values for processing
				String flightDate = lineEntries[5];
				String origin = lineEntries[11];
				String destination = lineEntries[17];
				String arrivalTime = lineEntries[35];
				String departureTime = lineEntries[24];
				String ArrDelayMinutes = lineEntries[37];
				String cancelled = lineEntries[41];
				String diverted = lineEntries[43];
				Text nkey;

				//Check if the flight is a valid flight
				if(isValidFlight(origin, destination, 
						flightDate, cancelled, diverted)){
					//Store all required attributes of valid flight 
					String val =  origin + "," + destination + "," +
							flightDate + "," + arrivalTime + "," + 
							departureTime + "," + ArrDelayMinutes;

					//Assign keys according to flightDate and origin/destination
					if(origin.equals("ORD"))
						nkey = new Text(destination + "-" + flightDate);
					else
						nkey = new Text(origin + "-" + flightDate);

					Text nVal = new Text(val);
					context.write(nkey, nVal);
				}
			}
		}

		/*Applying Filters
		 * 1. Filter all flights whose origin is "ORD" then their desti
		 * nation is not "JFK" and if destination is JFK then origin
		 * is not ORD
		 * 
		 * 2. The Flight lies between the given dates.
		 * 
		 * 3. The flight is not cancelled or diverted 
		 * */
		boolean isValidFlight(String origin,String destination,
				String flightDate,String cancelled,String diverted){

			if(hasValidOriginAndDestination(origin, destination)
					&& hasValidDate(flightDate) 
					&& notCanceledOrDiverted(cancelled, diverted)){
				return true;
			}
			else 
				return false;
		}

		/*Filter all flights whose origin is "ORD" then their desti
		 * nation is not "JFK" and if destination is JFK then origin
		 * is not ORD 
		 * */
		boolean hasValidOriginAndDestination(String origin, String destination){
			if(origin.equals("ORD") && !(destination.equals("JFK"))
					|| !(origin.equals("ORD")) && (destination.equals("JFK"))){
				return true;
			}
			else 
				return false;
		}

		/* 2. The Flight lies between the given dates.*/
		boolean hasValidDate(String flightDate){
			SimpleDateFormat ft = new SimpleDateFormat ("yyyy-MM-dd");
			Date min = null,max = null,d = null;
			try {
				min = ft.parse("2007-05-31");

				max = ft.parse("2008-06-01");
				d = ft.parse(flightDate);
			} catch (ParseException e) {
				e.printStackTrace();
			}
			return (d.after(min) && d.before(max));
		}

		/* 3. The flight is not cancelled or diverted */
		boolean notCanceledOrDiverted(String cancelled, String diverted){
			if( cancelled.equals("0.00") && diverted.equals("0.00"))
				return true;
			else
				return false;
		}
	}

	/*Reducer Class*/
	public static class flightDelayReducer 
	extends Reducer<Text,Text,Text,FloatWritable> {

		float delays = 0;

		@Override
		public void reduce(Text key, Iterable<Text> values, Context context) 
				throws IOException, InterruptedException {

			/* For Storing different list elements */
			String[] elementsF1, elementsF2;
			String itemF1=null, itemF2=null;

			/*List for storing flights originating for ORD*/ 
			ArrayList<String> f1Data = new ArrayList<String>();
			/*List for all other flights*/
			ArrayList<String> f2Data = new ArrayList<String>();

			/*Separate the potential two legs in different lists*/
			for(Text vItem : values){
				String vItemToString = vItem.toString();
				String[] itemBreak = vItemToString.split(",");
				if(itemBreak[0].equals("ORD"))
					f1Data.add(vItemToString);
				else
					f2Data.add(vItemToString);
			}

			/* Iterate over both of the above created lists to determine
			 * correct pair of flights where arrival time of leg 1 is before
			 *  the departure time of leg 2  */
			for(Iterator<String> i = f1Data.iterator(); i.hasNext(); ) {
				itemF1 = i.next();
				elementsF1 = itemF1.split(",");
				for(Iterator<String> j = f2Data.iterator(); j.hasNext(); ) {
					itemF2 = j.next();
					elementsF2 = itemF2.split(",");
					if(legalFlightTime(elementsF1[3],elementsF2[4])){
						delays = Float.parseFloat(elementsF1[5])+
								Float.parseFloat(elementsF2[5]);
						context.getCounter(DelayCounter.Total_Delay).increment(
								(long) delays);
						context.getCounter(DelayCounter.Entities).increment(1);
					}
				}
			}
		}

		/*To check if arrival time of leg 1 is before the departure time of leg 2 */
		boolean legalFlightTime(String arrF1,String depF2){
			return (Integer.parseInt(depF2) > Integer.parseInt(arrF1));
		}
	}

	public static void main(String[] args) throws Exception {
		Configuration conf = new Configuration();
		String[] otherArgs = new GenericOptionsParser(conf, args).getRemainingArgs();
	    if (otherArgs.length != 2) {
	      System.err.println("Usage: Plain <input> <output>");
	      System.exit(2);
	    }
		Job job = new Job(conf, "Average Flight Delay");
		job.setJarByClass(Plain.class);
		job.setMapperClass(flightDelayPlainMapper.class);
		job.setReducerClass(flightDelayReducer.class);
		job.setNumReduceTasks(10);
		job.setMapOutputKeyClass(Text.class);
		job.setMapOutputValueClass(Text.class);
		job.setOutputKeyClass(Text.class);
		job.setOutputValueClass(Text.class);
		FileInputFormat.addInputPath(job, new Path(otherArgs[0]));
		FileOutputFormat.setOutputPath(job, new Path(otherArgs[1]));

		if(job.waitForCompletion(true)){
			Counters counters = job.getCounters();
			float total = counters.findCounter(DelayCounter.Total_Delay).getValue();
			float numbers = counters.findCounter(DelayCounter.Entities).getValue();
			float average = total/numbers;
			System.out.println("The total average is = "+average);
			System.exit(0);
		}
		else
			System.exit(1);
	}
}
