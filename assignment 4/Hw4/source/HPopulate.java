package hw4hbasep;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.util.GenericOptionsParser;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HColumnDescriptor;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.KeyValue;
import org.apache.hadoop.hbase.ZooKeeperConnectionException;
import org.apache.hadoop.hbase.client.HBaseAdmin;
import org.apache.hadoop.hbase.client.HTable;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.io.ImmutableBytesWritable;
import org.apache.hadoop.hbase.mapreduce.HFileOutputFormat;
import org.apache.hadoop.hbase.mapreduce.TableOutputFormat;
import org.apache.hadoop.hbase.util.Bytes;

import au.com.bytecode.opencsv.CSVParser;

public class HPopulate {
	
	public static final String F_TABLE_NAME = "FlightData";
	public static final String flight_fam = "family";
	
	//Create all columns 
	public static final String[] columns = {"Year","Quarter","Month",
		"DayofMonth","DayOfWeek","FlightDate","UniqueCarrier",
		"AirlineID","Carrier","TailNum","FlightNum","Origin",
		"OriginCityName","OriginState","OriginStateFips","OriginStateName",
		"OriginWac","Dest","DestCityName","DestState","DestStateFips",
		"DestStateName","DestWac","CRSDepTime","DepTime","DepDelay",
		"DepDelayMinutes","DepDel15","DepartureDelayGroups","DepTimeBlk",
		"TaxiOut","WheelsOff","WheelsOn","TaxiIn","CRSArrTime","ArrTime",
		"ArrDelay","ArrDelayMinutes","ArrDel15","ArrivalDelayGroups",
		"ArrTimeBlk","Cancelled","CancellationCode","Diverted","CRSElapsedTime",
		"ActualElapsedTime","AirTime","Flights","Distance","DistanceGroup",
		"CarrierDelay","WeatherDelay","NASDelay","SecurityDelay","LateAircraftDelay"};
	
	/*------- Mapper Class ------- */
	public static class HPopulateMapper extends Mapper<Object, Text, Text, IntWritable>{
		
		CSVParser parser = new CSVParser();
		Configuration config;
		HTable table;
		List <Put> p;
	    long count = 0;
		
		//Table Setup
		protected void setup (Context context) throws IOException, InterruptedException{
			config = HBaseConfiguration.create();
			table = new HTable(config, F_TABLE_NAME);
			table.setAutoFlush(false);
			p = new LinkedList<Put>();
		}
		
		// Map Function
		public void map(Object key, Text value, Context context) throws IOException{
			//get data from CSV file line by line
			String[] lineEntries = parser.parseLine(value.toString());
			int fligthMonth = Integer.parseInt(lineEntries[2]);
			int flightYear = Integer.parseInt(lineEntries[0]);
			if(flightYear == 2008){
				String myRow = lineEntries[6] + lineEntries[2];
				int lengthofKeyPart1 = myRow.length();
				Put puts = new Put(Bytes.toBytes(myRow+System.nanoTime()));
				for(int i=0; i< 54 ; i++){
					puts.add(Bytes.toBytes(flight_fam), Bytes.toBytes(columns[i]), 
						Bytes.toBytes(lineEntries[i]));
				}
				p.add(puts);
			}
		}
		
		// Cleanup; Close table
		protected void cleanup(Context context) throws IOException,InterruptedException {
			table.put(p);
			table.close();
       }
	}
	
	/*---------- Driver Function ---------*/
	public static void main(String[] args) throws Exception{
		
		Configuration conf = HBaseConfiguration.create();
		String[] otherargs = new GenericOptionsParser(conf, args).getRemainingArgs();
		
		if(otherargs.length != 2){
			System.err.println("Usage <input path> <output>");
			System.exit(2);
		}
		
		HBaseAdmin admin = new HBaseAdmin(conf);
		HTableDescriptor htd = new HTableDescriptor(F_TABLE_NAME);
		HColumnDescriptor hcd = new HColumnDescriptor(flight_fam);
		htd.addFamily(hcd);
		admin.createTable(htd);
		admin.close();
		Job job = new Job(conf, "HBase Populate");
		job.setJarByClass(HPopulate.class);
		job.setMapperClass(HPopulateMapper.class);
		job.setNumReduceTasks(10);
		job.setOutputKeyClass(Text.class);
	    job.setOutputValueClass(IntWritable.class);
		job.setOutputFormatClass(TableOutputFormat.class);
		job.getConfiguration().set(TableOutputFormat.OUTPUT_TABLE, F_TABLE_NAME);
			
		FileInputFormat.addInputPath(job, new Path(args[0]));
		FileOutputFormat.setOutputPath(job, new Path(args[1]));
		
		if(job.waitForCompletion(true)){
			admin.close();
		}
		else{	
			System.exit(1);
		}
	}
}