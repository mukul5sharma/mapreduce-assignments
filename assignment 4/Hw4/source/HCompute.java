package hw4basec;

import java.io.IOException;
import java.util.Arrays;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hbase.client.HTable;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.io.ImmutableBytesWritable;
import org.apache.hadoop.hbase.mapreduce.TableMapReduceUtil;
import org.apache.hadoop.hbase.mapreduce.TableMapper;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.util.GenericOptionsParser;

public class HCompute {
	
	private static String t = "FlightData";
	private static String flight_fam = "family";
	private static String fmonth = "Month";
	private static String fdelay = "ArrDelayMinutes";
	private static String uc = "UniqueCarrier";
	private static String can = "Cancelled";
	private static String y = "Year";
	
	/*------- MAPPER -------*/
	public static class HComputeMapper extends TableMapper<Text, Text>{
		private Text fKey = new Text();
		private Text fVal = new Text();
		
		@Override
		public void map(ImmutableBytesWritable rkey, Result values, Context context) 
				throws IOException, InterruptedException{
			
			// Extract all values
			String carrier = Bytes.toString(values.getValue(Bytes.toBytes(flight_fam), Bytes.toBytes(uc)));
			String month = Bytes.toString(values.getValue(Bytes.toBytes(flight_fam), Bytes.toBytes(fmonth)));
			String year = Bytes.toString(values.getValue(Bytes.toBytes(flight_fam), Bytes.toBytes(y)));
			String delay = Bytes.toString(values.getValue(Bytes.toBytes(flight_fam), Bytes.toBytes(fdelay)));
			String canceled = Bytes.toString(values.getValue(Bytes.toBytes(flight_fam), Bytes.toBytes(can)));
			
				fKey.set(carrier);
				fVal.set(month + "," + delay);
				context.write(fKey, fVal);
		}
		
	}	
			
	/*------- REDUCER -------*/
	public static class HComputeReducer extends Reducer<Text, Text, Text, Text>	{
		
		@Override
		public void reduce (Text key, Iterable<Text> values, Context context){
			String flight = key.toString();
			double[] totalDelay = new double[12];
			int[] count= new int[12];
			Arrays.fill(count, 0);
			Arrays.fill(totalDelay, 0);
		 
			for (Text val : values){
				String parts[] = val.toString().split(",");
				int month = Integer.parseInt(parts[0]);
				double delayMinutes = Double.parseDouble(parts[1]);
				totalDelay[month -1] += delayMinutes;
				count[month - 1] += 1;
			}
			
			// Creating output String
			StringBuilder sBuilder  = new StringBuilder();
			for(int i=0; i< totalDelay.length; i++){
				if(i > 0){
					sBuilder.append(",");
				}
				int avgDelay = (int) Math.ceil(((double) totalDelay[i])/count[i]);
				sBuilder.append("(").append(i+1).append(",").append(avgDelay).append(")");
			}
			
			try {
				context.write(new Text (flight), new Text(sBuilder.toString()));
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
		} 
	}
			
    /*-------- DRIVER --------*/
	public static void main(String[] args) throws IOException, ClassNotFoundException, InterruptedException{
		Configuration conf = new Configuration();
		String[] otherArgs = new GenericOptionsParser(conf, args).getRemainingArgs();
		
		if(otherArgs.length != 2){
			System.err.println("Usage: HCompute <Input> <Output>");
			System.exit(2);
		}
		
		Scan scan = new Scan();
		scan.setCaching(500);
		scan.setCacheBlocks(false);
		
		Job job = new Job(conf, "Monthly Flight Delay");
		job .setJarByClass(HCompute.class);
		TableMapReduceUtil.initTableMapperJob(t, scan,
				HComputeMapper.class, Text.class, Text.class, job);
		job.setMapOutputKeyClass(Text.class);
		job.setMapOutputValueClass(Text.class);
		job.setReducerClass(HComputeReducer.class);
		job.setNumReduceTasks(10);
		
		FileInputFormat.addInputPath(job, new Path(otherArgs[0]));
		FileOutputFormat.setOutputPath(job, new Path(otherArgs[1]));

		if(job.waitForCompletion(true)){
			System.exit(0);
		}
		else
			System.exit(1);
		
	}
	
}