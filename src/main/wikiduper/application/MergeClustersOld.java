package wikiduper.application;

import java.io.EOFException;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.SequenceFile;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;
import org.apache.log4j.Logger;

import edu.umd.cloud9.io.array.ArrayListOfLongsWritable;
import edu.umd.cloud9.io.array.ArrayListWritable;
import edu.umd.cloud9.io.pair.PairOfInts;
import edu.umd.cloud9.io.pair.PairOfStringInt;

public class MergeClustersOld extends Configured implements Tool {
    private static final Logger LOG = Logger.getLogger(MergeClustersOld.class);


    private static final String INPUT = "input";
    private static final String OUTPUT = "output";

    @SuppressWarnings("static-access")
    @Override
    public int run(String[] args) throws Exception {
        Options options = new Options();
        options.addOption(OptionBuilder.withArgName("path")
                .hasArg().withDescription("output path").create(OUTPUT));
        options.addOption(OptionBuilder.withArgName("path")
                .hasArg().withDescription("minhash output buckets").create(INPUT));

        CommandLine cmdline;
        CommandLineParser parser = new GnuParser();
        try {
            cmdline = parser.parse(options, args);
        } catch (ParseException exp) {
            System.err.println("Error parsing command line: " + exp.getMessage());
            return -1;
        }

        if (!cmdline.hasOption(OUTPUT) || !cmdline.hasOption(INPUT)){
            HelpFormatter formatter = new HelpFormatter();
            formatter.setWidth(120);
            formatter.printHelp(this.getClass().getName(), options);
            ToolRunner.printGenericCommandUsage(System.out);
            return -1;
        }

        String outputPath = cmdline.getOptionValue(OUTPUT);
        String inputPath = cmdline.getOptionValue(INPUT);
        
        LOG.info("Tool name: " + this.getClass().getName());
        LOG.info(" - output file: " + outputPath);
        
        JobConf conf = new JobConf(getConf(), MergeClustersOld.class);

        /* Get Clusters from MinhashWikipediaPages pair output */
        
        getClusters(inputPath,conf,outputPath);

        return 0;
    }

    // Reads in pairs from MinhahsWikipediaPages output and performs connected component analysis
    // Creates a global cluster numbering and a map from doc numbers to sentences and their cluster numbers
    // Writes the docmap to docmapFile
    public static void getClusters(String filein, JobConf conf, String docmapFile){

        try {
            TreeMap<Integer, HashSet<PairOfStringInt>> clustermap = new TreeMap<Integer, HashSet<PairOfStringInt>>();
            // map from doc id to sentence numbers
            TreeMap<Integer, TreeSet<PairOfInts>> docmap = new TreeMap<Integer, TreeSet<PairOfInts>>();
            readBuckets(filein,conf,clustermap);
            
            // Renumber components
            int componentct = 0;
            for(Integer cnum : clustermap.keySet()){
                HashSet<PairOfStringInt> comp = clustermap.get(cnum);
                for(PairOfStringInt p : comp){
                    int docid = Integer.valueOf(p.getLeftElement());
                    int sentencenum = p.getRightElement();
                    if(!docmap.containsKey(docid)){
                        docmap.put(docid, new TreeSet<PairOfInts>());
                    }
                    docmap.get(docid).add(new PairOfInts(sentencenum, componentct));
                }
                componentct++;

            }

            FileSystem fs = FileSystem.get(conf);
            Path clustersOut = new Path(docmapFile);
            FileSystem.get(conf).delete(clustersOut, true);
            SequenceFile.Writer writer = SequenceFile.createWriter(conf, 
                    SequenceFile.Writer.file(clustersOut), 
                    SequenceFile.Writer.keyClass(IntWritable.class), 
                    SequenceFile.Writer.valueClass(ArrayListWritable.class));
            ArrayListWritable<PairOfInts> sentlist;
            IntWritable doc;
            for(int docid : docmap.navigableKeySet()){
                doc = new IntWritable();
                sentlist = new ArrayListWritable<PairOfInts>();
                sentlist.clear();
                doc.set(docid);
                for(PairOfInts sentcomp : docmap.get(docid)){
                    sentlist.add(sentcomp);
                }
                writer.append(doc,sentlist);
            }
            
            writer.close();
            fs.close();
            System.out.println("N components: " + componentct);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public static void readBuckets(String filein, JobConf conf, TreeMap<Integer, HashSet<PairOfStringInt>> cluster2sentencemap){
        HashMap<PairOfStringInt, Integer> sentence2clustermap = new HashMap<PairOfStringInt,Integer>();
        try {
        FileSystem fs = FileSystem.get(conf);
        System.out.println("filein = " + filein);
        FileStatus[] infiles = fs.globStatus(new Path(filein + "/part-*"));
        int clusterct = 0;
        long ct = 0;
        for(FileStatus filestatus : infiles){
            System.out.println(filestatus.getPath().toString());
            try{
            FSDataInputStream in = fs.open(filestatus.getPath());
            SequenceFile.Reader reader;
            reader = new SequenceFile.Reader(conf, SequenceFile.Reader.stream(in));
            ArrayListOfLongsWritable bucket = new ArrayListOfLongsWritable();
            
            ArrayListWritable<PairOfStringInt> sentenceList = new ArrayListWritable<PairOfStringInt>();
            HashSet<Integer> clusterSet = new HashSet<Integer>();
            while(reader.next(bucket, sentenceList)){
                //System.out.println("cluster2sentencemap");
                //System.out.println("\t" + cluster2sentencemap.keySet());
                
                //System.out.println("sentence2clustermap");
                //System.out.println("\t" + sentence2clustermap.keySet());
                ct++;
                if(ct % 1000 == 0) System.out.println("Count:"+ct);
                if(ct % 1000 == 0) System.out.println("\t"+cluster2sentencemap.keySet().size());
                if(ct % 1000 == 0) System.out.println("\t"+sentence2clustermap.keySet().size());
                if(ct % 1000 == 0) System.out.println("\t"+sentenceList.size());
                clusterSet.clear();
                //System.out.println("Sentencelist " + sentenceList);
                for(PairOfStringInt docsentence : sentenceList){
                    if(sentence2clustermap.containsKey(docsentence)){
                       clusterSet.add(sentence2clustermap.get(docsentence));
                    }
                }
                if(ct % 1000 == 0) System.out.println("\t"+clusterSet.size());
                //System.out.println("Cluster set" + clusterSet);
                cluster2sentencemap.put(clusterct, new HashSet<PairOfStringInt>());
                if(!clusterSet.isEmpty()){
                    for(int cluster : clusterSet){
                        // for each cluster merge the sentences into a new cluster
                        for(PairOfStringInt docsentence : cluster2sentencemap.get(cluster)){
                            cluster2sentencemap.get(clusterct).add(docsentence);
                            sentence2clustermap.put(docsentence, clusterct);
                        }
                        // Remove the old cluster from cluster2sentencemap
                        cluster2sentencemap.remove(cluster);
                    }
                }
                // Add all of the docsentences in the current list to the new cluster
                cluster2sentencemap.get(clusterct).addAll(sentenceList);
                for(PairOfStringInt docsentence : sentenceList){
                    sentence2clustermap.put(docsentence, clusterct);
                }
                //bucket = new ArrayListOfLongsWritable();
                sentenceList = new ArrayListWritable<PairOfStringInt>();
                clusterct++;
                
            }
            reader.close();
          }catch (EOFException e) {
           // For some reason it doesn't know when the input stream is done??
          }
        }
    }catch (IOException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
    }


    }
    
    static HashSet<PairOfInts> getConnectedComponent(PairOfInts entity, TreeMap<PairOfInts, HashSet<PairOfInts>> matchmap){
        HashSet<PairOfInts> component = new HashSet<PairOfInts>();
        component.add(entity);
        boolean hasmatchcomponent = true;
        while(!matchmap.isEmpty() && hasmatchcomponent){
            hasmatchcomponent = false;
            HashSet<PairOfInts> comp = (HashSet<PairOfInts>) component.clone();
            for(PairOfInts e : comp){
                if(matchmap.containsKey(e)){
                    hasmatchcomponent = true;
                    HashSet <PairOfInts> matches = matchmap.remove(e);
                    component.addAll(matches);
                }
            }
        }

        return component;
    }
    
    static HashSet<PairOfInts> getConnectedComponentRecursive(PairOfInts entity, TreeMap<PairOfInts, HashSet<PairOfInts>> matchmap){
        HashSet<PairOfInts> component = new HashSet<PairOfInts>();
        component.add(entity);
        if(matchmap.isEmpty() || !matchmap.containsKey(entity)){
            return component;
        }

        HashSet <PairOfInts> matches = matchmap.remove(entity);
        for(PairOfInts m : matches){
            HashSet<PairOfInts> c = getConnectedComponentRecursive(m, matchmap);
            component.addAll(c);
        }
        return component;
    }

    public MergeClustersOld() {}

    public static void main(String[] args) throws Exception {
        ToolRunner.run(new MergeClustersOld(), args);
    }
}
