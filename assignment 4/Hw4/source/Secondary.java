

import java.io.IOException;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.WritableComparable;
import org.apache.hadoop.io.WritableComparator;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Partitioner;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.util.GenericOptionsParser;

import au.com.bytecode.opencsv.CSVParser;


public class Secondary {
	
	//------Key Comparator------//
	public static class KeyComparator extends WritableComparator {
	    protected KeyComparator() {
	      super(FlightKey.class, true);
	    }
	    
	    @Override
	    public int compare(WritableComparable w1, WritableComparable w2) {
	    	FlightKey ip1 = (FlightKey) w1;
	    	FlightKey ip2 = (FlightKey) w2;
	    	
	        int cmp = ip1.getFlightName().compareTo(ip2.getFlightName());
	        if(cmp == 0)
	        return (ip1.getMonth().compareTo(ip2.getMonth()));
	        else
	    	  return cmp;
	    }
	  }
	  
	  /*-------- Group Comparator ------- */
	  public static class GroupComparator extends WritableComparator {
	    protected GroupComparator() {
	      super(FlightKey.class, true);
	    }	
	    
	    @Override
	    public int compare(WritableComparable w1, WritableComparable w2) {
	      FlightKey ip1 = (FlightKey) w1;
	      FlightKey ip2 = (FlightKey) w2;
	      return (ip1.getFlightName().compareTo(ip2.getFlightName()));
	      
	    }
	  }
	
   /*--------- Partitioner -----------*/ 
    public static class airlinePartitioner extends Partitioner<FlightKey, Text>
    {
    	@Override
    	public int getPartition(FlightKey key, Text value, int num)
    	{
    		int n = Math.abs(key.getFlightName().hashCode()) %  num;
    		return n;
    	}
    }
		  
	/*----------- Mapper Class -------------*/
    public static class flightDelayMapper extends Mapper<Object, Text, FlightKey, Text>
	{
		@Override
		public void map(Object key, Text value, Context context) throws IOException, InterruptedException 
		{	
			if(((LongWritable) key).get()>0)
			{
				// Get data from CSV file line by line
				CSVParser parser = new CSVParser();
				String[] lineEntries = parser.parseLine(value.toString());
				
				// Extract all values
				String ArrDelayMinutes = lineEntries[37];
				String cancelled = lineEntries[41];
				String diverted = lineEntries[43];
				String month = lineEntries[2];
				String year = lineEntries[0];
				String flightNum = lineEntries[6];
				
				FlightKey nKey = new FlightKey(); 
				Text nVal;
				
				//Check if the current flight is the eligible flight
				if(hasValidDate(year) && notCanceledOrDiverted(cancelled, diverted)
						&& flightNum.length()!=0 && month.length()!=0 
						&& ArrDelayMinutes.length()!=0)
				{	String id = flightNum;
					IntWritable m = new IntWritable(Integer.parseInt(month));
					nKey.set(id, m);
					nVal = new Text(ArrDelayMinutes);
					context.write(nKey, nVal);
				}
			}
		}
		
		/* 2. The Flight lies IN the year 2008. */
		boolean hasValidDate(String year){
			if (year.equals("2008"))
				return true;
			else
				return false;
		}

		/* 3. The flight is not cancelled or diverted */
		boolean notCanceledOrDiverted(String cancelled, String diverted){
			if( cancelled.equals("0.00") && diverted.equals("0.00"))
				return true;
			else 
				return false;
		}
	
	}	
	
	
	/******* R E D U C E *******/
	public static class flightDelayReducer extends Reducer<FlightKey,Text,Text,Text> 
	{	
		private String airline = null;
		private Text pairs = new Text();
		
		
		@Override
		public void reduce(FlightKey key, Iterable<Text> values, Context context)throws IOException, InterruptedException 
		{
			float totalDelay = 0;
			float currentDelay = 0;
			int lastMonth = 1;
			int flights = 0;
			int currentMonth = 0;
			int[] averageDelay = new int[12];
			StringBuilder outputStr = new StringBuilder("");
			
			for(Text vItem : values)
			{				
				airline = key.getFlightName();
				currentMonth = key.getMonth().get();
				currentDelay = Float.parseFloat(vItem.toString());
				
				if(currentMonth != lastMonth)
				{   
					averageDelay[lastMonth-1] = (int)Math.ceil(totalDelay/flights);
					flights = 0;
					totalDelay = 0;
					lastMonth = currentMonth;
				}
				totalDelay = totalDelay + currentDelay;
				flights ++;
			}
			
			//average for last month
			averageDelay[lastMonth-1] = (int)Math.ceil(totalDelay/flights);
			
			//Create output string 
			for(int i = 0; i < averageDelay.length; i++)
			{
				if(i<0)
					{
						outputStr.append(",");
					}
				outputStr.append("(");
				outputStr.append(i+1);
				outputStr.append(",");
				outputStr.append(averageDelay[i]);
				outputStr.append(")");
			}
			
			//Emit
			pairs.set(outputStr.toString());
			Text rAirline = new Text(airline);
			context.write(rAirline, pairs);
		}
	}
	
	/* D r i v e r */
	public static void main(String[] args) throws Exception {
		Configuration conf = new Configuration();
		String[] otherArgs = new GenericOptionsParser(conf, args).getRemainingArgs();
	    if (otherArgs.length != 2) {
	      System.err.println("Usage: Secondary <input> <output>");
	      System.exit(2);
	    }
		Job job = new Job(conf, "Average Flight Delay Pattern");
		job.setJarByClass(Secondary.class);
		job.setMapperClass(flightDelayMapper.class);
		job.setReducerClass(flightDelayReducer.class);
		
		job.setPartitionerClass(airlinePartitioner.class);
		job.setGroupingComparatorClass(GroupComparator.class);
		job.setSortComparatorClass(KeyComparator.class);
		
		job.setNumReduceTasks(10);
		job.setMapOutputKeyClass(FlightKey.class);
		job.setMapOutputValueClass(Text.class);
		job.setOutputKeyClass(IntWritable.class);
		job.setOutputValueClass(Text.class);
		FileInputFormat.addInputPath(job, new Path(args[0]));
		FileOutputFormat.setOutputPath(job, new Path(args[1]));

		if(job.waitForCompletion(true)){
			System.exit(0);
		}
		else
			System.exit(1);
	}
		
}