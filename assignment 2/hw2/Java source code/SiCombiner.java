/**
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import java.io.IOException;
import java.util.StringTokenizer;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.Partitioner;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.util.GenericOptionsParser;

public class SiCombiner {

	
	//Partitioner class
    public static class WordCountPartitioner extends Partitioner<Text, IntWritable>{
  	  
  	  @Override
  	  public int getPartition(Text t, IntWritable value, int numPartitions) {
  		  String s = t.toString();
  		  char first = s.charAt(0);
  		  if(first=='m'||first=='M')
			  return 0;
		  if(first=='n'||first=='N')	
			  return 1;
		  if(first=='o'||first=='O')
			  return 2;
		  if(first=='p'||first=='P')
			  return 3;
		  if(first=='q'||first=='Q')
			  return 4;
  		  else 
  			  return 0;
  	  }
    }	
	
  public static class TokenizerMapper 
       extends Mapper<Object, Text, Text, IntWritable>{
    
    private final static IntWritable one = new IntWritable(1);
    private Text word = new Text();
    
  
      
    public void map(Object key, Text value, Context context
                    ) throws IOException, InterruptedException 
    {
      StringTokenizer itr = new StringTokenizer(value.toString());
      while (itr.hasMoreTokens()) 
    	  
    	  {   String s = itr.nextToken();
    	      char first = s.charAt(0);
    	      if(isLegal(first))
    	      {
    	    	  word.set(s);
    	    	  context.write(word, one);
    	      }
    	  }
    }
    
    public boolean isLegal(char first)
    {
    	if(first >= 'm' && first <= 'q' || first >='M' && first <= 'Q')
    	{
    		return true;
    	}
    	else 
    		return false;
    }
  }
  
  public static class IntSumReducer 
       extends Reducer<Text,IntWritable,Text,IntWritable> {
    private IntWritable result = new IntWritable();

    public void reduce(Text key, Iterable<IntWritable> values, 
                       Context context
                       ) throws IOException, InterruptedException {
      int sum = 0;
      for (IntWritable val : values) {
        sum += val.get();
      }
      result.set(sum);
      context.write(key, result);
    }
  }
  
  

  public static void main(String[] args) throws Exception {
    Configuration conf = new Configuration();
    String[] otherArgs = new GenericOptionsParser(conf, args).getRemainingArgs();
    if (otherArgs.length != 2) {
      System.err.println("Usage: wordcount <in> <out>");
      System.exit(2);
    }
    Job job = new Job(conf, "word count");
    job.setJarByClass(SiCombiner.class);
    job.setMapperClass(TokenizerMapper.class);
    
    job.setCombinerClass(IntSumReducer.class);
    
    //setting multiple reducer classes
    job.setNumReduceTasks(5);
    job.setPartitionerClass(WordCountPartitioner.class);
    job.setReducerClass(IntSumReducer.class);
    
    job.setOutputKeyClass(Text.class);
    job.setOutputValueClass(IntWritable.class);
    FileInputFormat.addInputPath(job, new Path(otherArgs[0]));
    FileOutputFormat.setOutputPath(job, new Path(otherArgs[1]));
    System.exit(job.waitForCompletion(true) ? 0 : 1);
  }
}