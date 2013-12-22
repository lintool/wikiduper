package wikiduper.analysis;



import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;

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
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.SequenceFile;
import org.apache.hadoop.io.SequenceFile.Writer;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;
import org.apache.log4j.Logger;

import edu.umd.cloud9.io.pair.PairOfStrings;


import wikiduper.utils.MergeClusters;

/**
 * 
 * @author weissman
 *
 */
public class LargeClusters extends Configured implements Tool {

    private static final Logger LOG = Logger.getLogger(MergeClusters.class);

    private static final String INPUT = "input";
    private static final String OUTPUT = "output";
    private static final String THRESHOLD = "threshold"; 

    @SuppressWarnings("static-access")
    @Override
    public int run(String[] args) throws Exception {

            Options options = new Options();
            options.addOption(OptionBuilder.withArgName("path")
                    .hasArg().withDescription("minhash pipeline output").create(INPUT));
            options.addOption(OptionBuilder.withArgName("path")
                    .hasArg().withDescription("output file for identical clusters").create(OUTPUT));

            CommandLine cmdline;
            CommandLineParser parser = new GnuParser();
            try {
                cmdline = parser.parse(options, args);
            } catch (ParseException exp) {
                System.err.println("Error parsing command line: " + exp.getMessage());
                return -1;
            }

            if (!cmdline.hasOption(INPUT)  || !cmdline.hasOption(OUTPUT) || !cmdline.hasOption(THRESHOLD)){ 
                HelpFormatter formatter = new HelpFormatter();
                formatter.setWidth(120);
                formatter.printHelp(this.getClass().getName(), options);
                ToolRunner.printGenericCommandUsage(System.out);
                return -1;
            }

            String inputPath = cmdline.getOptionValue(INPUT);
            String outputPath = cmdline.getOptionValue(OUTPUT);
            int threshold = Integer.valueOf(THRESHOLD);
            
            LOG.info("Tool name: " + this.getClass().getName());
            
            JobConf conf = new JobConf(getConf(), MergeClusters.class);

            conf.set("mapred.reduce.child.java.opts", "-Xmx6144m");

            getLargeClusters(inputPath, outputPath, threshold, conf);

            return 0;
        }
    
    public void getLargeClusters(String filein, String fileout, int threshold, JobConf conf){
        
        int largect = 0;
        ArrayList<PairOfStrings> cluster = new ArrayList<PairOfStrings>();
        int clustersize = 0;        
        int clusterct = 0;
        long clustcurr = -1;
        int maxclustersize = 0;
        
        LongWritable clusterid = null;
        try {
            
            
            FileSystem fs = FileSystem.get(conf);
            SequenceFile.Writer clusterWriter  = SequenceFile.createWriter(conf, Writer.file(new Path(fileout)),
                    Writer.keyClass(LongWritable.class), Writer.valueClass(PairOfStrings.class));
           
        System.out.println("filein = " + filein);
        FileStatus[] infiles = fs.globStatus(new Path(filein + "/part-*"));
        for(FileStatus filestatus : infiles){
            System.out.println(filestatus.getPath().toString());
            try{
                FSDataInputStream in = fs.open(filestatus.getPath());
                SequenceFile.Reader reader = new SequenceFile.Reader(conf, SequenceFile.Reader.stream(in));
                clusterid = new LongWritable();
                PairOfStrings articlesentence = new PairOfStrings();

            while(reader.next(clusterid, articlesentence)){

                if(clustcurr == -1){
                    clustcurr = clusterid.get();  
                }
                    
                if(!(clustcurr == clusterid.get())){
                    if(clusterct % 10000 == 0) System.err.println("clusterct = " + clusterct);
                    // Once we've found a new cluster Update each histogram
                    if(clustersize > maxclustersize){
                        maxclustersize = clustersize;
                    }

                    if(clustersize > threshold){
                        largect++;
                        LongWritable clusterIdOut = new LongWritable();
                        clusterIdOut.set(clustcurr);
                    
                        for(PairOfStrings s : cluster){                    
                            clusterWriter.append(clusterIdOut, s);
                        }
                    }

                    clustersize = 0;
                    cluster.clear();
                    clusterct++;
                }
                    
                clustcurr = clusterid.get();
                clustersize++;
                cluster.add(articlesentence);
                
                clusterid = new LongWritable();
                articlesentence = new PairOfStrings();

            }
            reader.close();
            }catch (EOFException e) {
                // For some reason it doesn't know when the input stream is done??
               }

            if(clustersize > threshold){
                largect++;
                LongWritable clusterIdOut = new LongWritable();
                clusterIdOut.set(clustcurr);
                for(PairOfStrings s : cluster){
                    clusterWriter.append(clusterIdOut, s);
                }
            }


            clustersize = 0;
            cluster.clear();
            clusterct++;
        }
        
        clusterWriter.close();
        
        System.out.println("Max cluster size: " + maxclustersize);
        System.out.println("Number of large clusters: " + largect);
        
        
        }catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
    
    
    public LargeClusters() {}

    public static void main(String[] args) throws Exception {
        ToolRunner.run(new LargeClusters(), args);
    }
}
