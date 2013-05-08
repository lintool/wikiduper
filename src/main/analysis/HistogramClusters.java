package analysis;



import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HistogramClusters {

    public static void main(String args[]){
		
	    if(args.length != 1){
	        System.out.println("Usage: FilterClusters <cluster output file>n");
			System.exit(-1);
	    }
		Pattern linepat = Pattern.compile("([0-9]+)\t(.*)\t(.*)$");
		    
		FileInputStream fin;
		
		TreeMap<Integer,Integer> histogram = new TreeMap<Integer,Integer>();
		HashSet<String> articleset = new HashSet<String>();
		ArrayList<String> cluster = new ArrayList<String>();
        int clusterct = 0;
		try {

            File filedir = new File(args[0]);
            String line;

            for(File file : filedir.listFiles()){
                System.out.println("Reading files " + file.getName() + "...");
                fin = new FileInputStream(file);
                BufferedReader bin = new BufferedReader(new InputStreamReader(fin));
                
                String clustcurr = null;
                while((line = bin.readLine()) != null){
	                Matcher m = linepat.matcher(line);
	                String clust = "";
	                String title = "";
	                String sentence = "";
	                if(m.matches()){
	                    clust = m.group(1);
	                    title = m.group(2);
	                    sentence = m.group(3);

                    }else{
                        System.out.println("Bad line: " + line);
                        System.exit(-1);
                    }

					if(clustcurr == null){
	                      clustcurr = clust;  
	                }
					
	                if(!clustcurr.equals(clust)){
	                    if(clusterct % 1000 == 0) System.out.println("clusterct = " + clusterct);

	                    int size = cluster.size();
	                    if(!histogram.containsKey(size)){
	                        histogram.put(size, 0);    
	                    }
	                    histogram.put(size, histogram.get(size) + 1);
	                    cluster.clear();
                        clusterct++;
	                }
	                
	                clustcurr = clust;
                    cluster.add(line);
                    articleset.add(title);

	            }
	              bin.close();
	              fin.close();
            }
		 	 } catch (FileNotFoundException e) {
		 		// TODO Auto-generated catch block
		 		e.printStackTrace();
		 	 } catch (IOException e) {
		 			// TODO Auto-generated catch block
		 			e.printStackTrace();
		     }
		    
		    
            System.out.println("N clusters: " + clusterct);            
            for(int b : histogram.keySet()){
                System.out.println(b + "\t" + histogram.get(b));
            }

	}


}
