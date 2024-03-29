import java.io.IOException;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.SortedSet;

import org.encog.ml.data.MLDataPair;
import org.encog.ml.data.MLDataSet;
import org.encog.ml.data.basic.BasicMLDataSet;

public class Main2 {
	
	public static void main(String[] args) throws IOException{

		// This creates a new WeatherData and print some nice information about it
		long start = System.currentTimeMillis();
		WeatherData wd = DataParser.parse("SMHI_3hours_clim_7142.txt");
		System.out.println("parsed in:"+(System.currentTimeMillis()-start));
		
		/*System.out.println(wd.info());
		System.out.println();
		
		System.out.print(wd.getDataWith(new WeatherData.Value[]{
				WeatherData.Value.WIND_SPEED,
				WeatherData.Value.WIND_DIRECTION,
				WeatherData.Value.TEMPERATURE,
				WeatherData.Value.HUMIDITY,
				WeatherData.Value.RAIN}).size());
		System.out.println("\twind speed/direction, temp, humidity, rain\n");*/
		
		start = System.currentTimeMillis();
		SortedSet<DataPoint> summer = wd.getDataBetweenMonths(Calendar.MAY, Calendar.AUGUST);
		System.out.println("total summer data: "+summer.size()+" obtained in:"+(System.currentTimeMillis()-start));
		
		start = System.currentTimeMillis();
		wd.purgeData(summer, new WeatherData.Value[]{
				WeatherData.Value.WIND_SPEED,
				WeatherData.Value.WIND_DIRECTION,
				WeatherData.Value.TEMPERATURE,
				WeatherData.Value.HUMIDITY,
				WeatherData.Value.AIR_PRESSURE});
		
		/*SortedSet<DataPoint> summer = wd.getDataWith(new WeatherData.Value[] {
				WeatherData.Value.WIND_SPEED,
				WeatherData.Value.WIND_DIRECTION,
				WeatherData.Value.TEMPERATURE,
				WeatherData.Value.HUMIDITY,
				WeatherData.Value.AIR_PRESSURE});
		*/
		System.out.println("good summer data: "+summer.size()+" obtained in:"+(System.currentTimeMillis()-start));
		
		SimpleAdjuster adjuster = new SimpleAdjuster(summer);
		List<MLDataPair> trainingData = adjuster.makeTraningData(summer);
		
		//Cut out 40 % of the training set for validation
		List<MLDataPair> validationData = trainingData.subList( (int) (0.6 * trainingData.size()), trainingData.size());
		List<MLDataPair> trainDataNew = trainingData.subList(0, (int) (0.6 * trainingData.size() - 1));
		
		System.out.println("data pairs:"+trainingData.size());
		System.out.println("training data size:"+trainDataNew.size());
		System.out.println("validation data size:"+validationData.size());
		
		//do some clean up before we start some heavy training
		summer=null;
		wd = null;
		//adjuster = null;
		System.gc();
		
		MLDataSet train = new BasicMLDataSet(trainingData);

		//int[] bestLayer = findGoodLayer(train); //6, 14, 4
		int[] bestLayer = new int[] {24, 12, 21};
		NeuralNetwork nn = new NeuralNetwork(SimpleAdjuster.NUMBER_OF_INPUT, 1, bestLayer);

		System.out.print("training");
		start = System.currentTimeMillis();
		nn.train(train, trainingData.size());
		System.out.println("Finished in " + ((System.currentTimeMillis()-start)/1000.00) + " seconds");
		
		//try the network some
		
		Iterator<MLDataPair> it = trainingData.iterator();
		
		int c = 0;
		int rainC = 0;
		double totalError = 0;
		double rainTotalError = 0;
		int correct = 0;
		int rainCorrect = 0;
		double rainLimit = 0.42;
		while (it.hasNext()){
			c++;
			MLDataPair mldp = it.next();
			//data with rain is not working atm, skip stuff with less than 0.5 rain
			//while(mldp.getIdealArray()[0]<0.5) mldp=it.next();
			
			double r = nn.predictRain(mldp.getInputArray());
			r = adjuster.max[adjuster.RAIN] * r;
			if (r < rainLimit) r = 0;
			
			double ideal = mldp.getIdeal().getData(0);
			ideal = adjuster.max[adjuster.RAIN] * ideal;

			double err = Math.abs(r - ideal);
			if (ideal > 0) rainC++;
			System.out.println(c + ": out: " + r + " ideal: " + ideal + " error: " + err);
			//"in: "+toString(mldp.getInputArray())+"
			totalError += err;
			if (ideal > 0) {
				rainTotalError += err;
			}
			if ((r == 0 && ideal == 0) || (r > 0 && ideal > 0)) {
				correct++;
				if (r > 0 && ideal > 0) {
					rainCorrect++;
				}
			}
		}
		System.out.println("Average error: " + totalError/c + " mm");
		System.out.println("Rain average error: " + rainTotalError/rainC + " mm");
		System.out.println("No-rain average error: " + (totalError-rainTotalError)/(c-rainC) + " mm");
		System.out.println("Accuracy: " + (Math.round((double)((double)correct/c) * 100)) + "% (" + correct + "/" + c + ")");
		System.out.println("Rain accuracy: " + (Math.round((double)((double)rainCorrect/rainC) * 100)) + "% (" + rainCorrect + "/" + rainC + ")");
		System.out.println("No-rain accuracy: " + (Math.round((double)((double)(correct-rainCorrect)/(c-rainC)) * 100)) + "% (" + (correct-rainCorrect) + "/" + (c-rainC) + ")");
	}
	
	private static String dateToString(DataPoint dp){
		return dp.getYear()+"/"+dp.getMonth()+"/"+dp.getDay()+"/"+dp.getHour();
	}
	
	public static String toString(double[] a){
		String s = "[";
		for (double d:a){
			s = s+d+",";
		}
		return s+"]";
	}
	
	/**
	 * Will try a bunch of different layers setup and train them and compare the error to find the best layers.
	 * @param trainData the data to train on.
	 */
	static int[] findGoodLayer(MLDataSet trainData){
		double bestError = Double.NEGATIVE_INFINITY;
		int[] bestLayer = new int[0];
		
		for (int nLay = 1; nLay<=3; nLay++){//try between 1 and 5 hidden layers
			
			int[] layer = new int[nLay];
			
			for (int i=0; i<20; i++){//try 20 different setups with nLay number of layers
				System.out.print("[");
				for (int j=0; j<nLay; j++){
					layer[j] = (int)(Math.random()*15.0)+1;//each layers has between 1 and 7 nodes
					System.out.print(","+layer[j]);
				}
				System.out.println("] ");
				
				//setup a network with those layers and train it 50 times
				NeuralNetwork nn = new NeuralNetwork(5, 1, layer);
				double error = nn.train(trainData, 1);
				
				System.out.println("\terror:"+error);
				
				//see if its better then our current best
				if (error > bestError){
					bestError = error;
					bestLayer = Arrays.copyOf(layer, layer.length);
				}
			}
		}
		
		System.out.println("best error:"+bestError);
		System.out.print("[");
		for (int i: bestLayer){
			System.out.print(","+i);
		}
		System.out.println("]");
		
		return bestLayer;
	}
	
	/**
	 * If you make a Hashtable pairing an MLDataPair to the two data points this function can print
	 * some of those to see exactly what the adjuster does.
	 * @param d
	 */
	public static void printData(Hashtable<MLDataPair,DataPoint[]> d){
		int i=0;
		for (MLDataPair key : d.keySet()){
			if (i>500){
			DataPoint[] points = d.get(key);
			
			System.out.println("from:"+dateToString(points[0])+" to:"+dateToString(points[1]));
			System.out.println("\thumi: "+points[0].get(DataPoint.HUMIDITY)+" = "+key.getInputArray()[0]);
			System.out.println("\ttemp: "+points[0].get(DataPoint.TEMPERATURE)+" = "+key.getInputArray()[1]);
			System.out.println("\tsped: "+points[0].get(DataPoint.WIND_SPEED)+" = "+key.getInputArray()[2]);
			System.out.println("\tnorth: "+points[0].get(DataPoint.WIND_DIRECTION)+" = "+key.getInputArray()[3]);
			System.out.println("\twest: "+key.getInputArray()[4]);
			System.out.println("\ttime: "+points[0].getMonth()+" = "+key.getInputArray()[5]);
			System.out.println("\t----------");
			System.out.println("\tout: "+points[1].get(DataPoint.RAIN)+" = "+key.getIdealArray()[0]);
				i++;
				i=0;
			}
			else{
				i++;
			}
		}
	}
	
	/**
	 * Given a collection of MLDataPairs makes sure there are as many data points where it rained as data points without any rain.
	 * @param trainingData
	 * @param limit The definition of rain, above this value it is raining.
	 */
	public static void balanceRain(Collection<MLDataPair> trainingData, float limit){
		int nrain = 0;
		Iterator<MLDataPair> it = trainingData.iterator();
		while(it.hasNext()){
			if (it.next().getIdealArray()[0]>limit){
				nrain++;
			}else{
				if (nrain>0){
					nrain--;
				}
				else{
					it.remove();
				}
			}
		}
	}
}
