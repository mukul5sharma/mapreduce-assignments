-- JOIN FIRST Version 2 --

REGISTER file:/home/hadoop/lib/pig/piggybank.jar;
DEFINE CSVLoader org.apache.pig.piggybank.storage.CSVLoader; 

-- Setting 10 Reducers for the job --
SET default_parallel 10;

-- Loading flight data for leg one and leg 2 using CSV loader --
FlightData1 = load 's3://mukulbucket/input/' USING CSVLoader;
FlightData2 = load 's3://mukulbucket/input/' USING CSVLoader;

-- Extracting the required fields from the flight data for both legs --
Flights1 = FOREACH FlightData1 GENERATE (chararray)$11 AS origin, (chararray)$17 AS dest,
					(int)$35 AS arrTime, (int)$24 AS depTime,
					(float)$37 AS arrDelayMinutes, (chararray)$5 AS flightDate,
					(int)$41 AS canceled, (int)$43 AS diverted;
							
Flights2 = FOREACH FlightData2 GENERATE (chararray)$11 AS origin, (chararray)$17 AS dest,
					(int)$35 AS arrTime, (int)$24 AS depTime,
					(float)$37 AS arrDelayMinutes, (chararray)$5 AS flightDate,
					(int)$41 AS canceled, (int)$43 AS diverted;

--------- ALL FILTERS -----------

-- Filtering flights 1 and 2 for correct destination/origin, as given the 
-- flight starting from ORD cannot end at JFK and a flight ending at JFK 
-- cannot start from ORD --
Flights1 = FILTER Flights1 BY (origin == 'ORD' AND dest != 'JFK'); 							
Flights2 = FILTER Flights2 BY (origin != 'ORD' AND dest == 'JFK');
		
-- Filtering flights on the basis of cancelled or delayed
Flights1 = FILTER Flights1 BY (canceled != 1 AND diverted != 1); 
Flights2 = FILTER Flights2 BY (canceled != 1 AND diverted != 1);
           

------------ JOIN ---------------

-- JOIN according to date for flights1 and flights 2 and destination of flight1
-- matches the origin of flight 2
afterJoinOndateAndDest = JOIN Flights1 BY (dest, flightDate), Flights2 BY (origin, flightDate);     
							
-- Filter for arrival time of flight1 < departure of flight 2, that is
-- leg 1 arrives before leg 2 leaves
legalTime = FILTER afterJoinOndateAndDest BY $2 < $11;


--------Version 2 Specific requirement ---------
-- Filter for date range, extracting the flights that lie in the given date range
-- for flights 1. --       
legalDateRange = FILTER legalTime BY ToDate($5, 'yyyy-MM-dd') < ToDate('2008-06-01','yyyy-MM-dd')
					AND ToDate($5, 'yyyy-MM-dd') > ToDate('2007-05-31','yyyy-MM-dd');
										   
-- Calculate average
sum = FOREACH legalDateRange GENERATE (float)($4+$12) AS delay; 
groupedSum = GROUP sum ALL;
average = FOREACH groupedSum GENERATE AVG(sum.delay) AS average;

--store the result into joinfv2 folder
STORE average INTO 's3://mukulbucket/hw3pig/joinfv2/' USING PigStorage();;
