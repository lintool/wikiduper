package courseproj.application;

/*
 * Cloud9: A MapReduce Library for Hadoop
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you
 * may not use this file except in compliance with the License. You may
 * obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */


import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.mapred.FileInputFormat;
import org.apache.hadoop.mapred.FileOutputFormat;
import org.apache.hadoop.mapred.JobClient;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.MapReduceBase;
import org.apache.hadoop.mapred.Mapper;
import org.apache.hadoop.mapred.OutputCollector;
import org.apache.hadoop.mapred.Reporter;
import org.apache.hadoop.mapred.Reducer;
import org.apache.hadoop.mapred.SequenceFileInputFormat;
import org.apache.hadoop.mapred.SequenceFileOutputFormat;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;
import org.apache.log4j.Logger;

import courseproj.hash.MultiplyShiftHash;
import courseproj.wikipedia.WikipediaPage;
import courseproj.wikipedia.WikipediaPageInputFormat;
import edu.umd.cloud9.io.array.ArrayListOfLongsWritable;
import edu.umd.cloud9.io.array.ArrayListWritable;
import edu.umd.cloud9.io.pair.PairOfStringInt;

public class MinhashWikipediaPages extends Configured implements Tool {
    private static final Logger LOG = Logger.getLogger(MinhashWikipediaPages.class);

    /* SignatureeMapper
     * 
     * Parameters that can be tweaked: NHASH, NHASHOUTPUTBITS, MINLEN
     * 
     * Pulls out sentences from text input using a regex. 
     * Emits one NHASH-length minhash signature per sentence.
     * Each hash is NHASHOUTPUTBITS long. (So signature is NHASH*NHASHOUTPUTBITS long.)
     * Sentences are shingled by individual words. 
     * If sentences are less than MINLEN words, then they are skipped.
     * 
     * 
     * Output values are (offset,nsentence) where offset is the byte offset of the input line in the
     * input text and nsentence is the number of the sentence in the line. (starting from 0)
     * 
     */

    private static enum PageTypes {
        TOTAL, REDIRECT, DISAMBIGUATION, EMPTY, ARTICLE, STUB, NON_ARTICLE
    };
    
    private static class SignatureMapper extends MapReduceBase implements
    Mapper<IntWritable, WikipediaPage, ArrayListOfLongsWritable, PairOfStringInt> {
    //Mapper<LongWritable, WikipediaPage, ArrayListOfLongsWritable, PairOfStringInt> {
        
        static long rseed;
        static long seeds[];
        static long sigseed; // Seed to use when randoly selecting signature vectors
        static long MINHASH[];

        static int NHASH; // Total number of hashes per sentence
        static int K; // Length of hash vector
        static int N; // Number of hashes per input sentence (N < NHASH)
        static int NHASHOUTPUTBITS;
        static int SHINGLELEN;
        static int MINLEN;
        static int MAXLEN;
        //static int NSENTENCE = 3; // Number of sentences to match at a time
        static MultiplyShiftHash hashfamily;

        // The minhash signature
        static final ArrayListOfLongsWritable SIG = new ArrayListOfLongsWritable(K);

        // The document-sentence identifier
        static final PairOfStringInt DOCSENT = new PairOfStringInt();

        //Adapted from http://stackoverflow.com/questions/5553410/regular-expression-match-a-sentence
        static final Pattern sentenceregex = Pattern.compile(
                "# Match a sentence ending in punctuation or EOS.\n" +
                        "[\\s]*    # Leading white space\n" + 
                        "([A-Z\"]    # First char capital letter or quotation\n" +
                        "[^.!?]*      # Greedily consume up to punctuation.\n" +
                        "(?:          # Group for unrolling the loop.\n" +
                        "  [.!?]      # (special) inner punctuation ok if\n" +
                        "  (?!['\"]?\\s|$)  # not followed by ws or EOS.\n" +
                        "  [^.!?]*    # Greedily consume up to punctuation.\n" +
                        ")*           # Zero or more (special normal*)\n" +
                        "[.!?]?       # Optional ending punctuation.\n" +
                        "['\"]?)       # Optional closing quote.\n" +
                        "\\s*$?       # Trailing white space\n",
                        Pattern.MULTILINE | Pattern.COMMENTS);
        
        
        //public void map(LongWritable key, WikipediaPage p, OutputCollector<ArrayListOfLongsWritable, PairOfStringInt> output,
          //      Reporter reporter) throws IOException {
           public void map(IntWritable key, WikipediaPage p, OutputCollector<ArrayListOfLongsWritable, PairOfStringInt> output,
                    Reporter reporter) throws IOException {

            if (p.isRedirect()) {
                reporter.incrCounter(PageTypes.REDIRECT, 1);

            } else if (p.isDisambiguation()) {
                reporter.incrCounter(PageTypes.DISAMBIGUATION, 1);
            } else if (p.isEmpty()) {
                reporter.incrCounter(PageTypes.EMPTY, 1);
            } else if (p.isArticle()) {
                reporter.incrCounter(PageTypes.ARTICLE, 1);

                if (p.isStub()) {
                    reporter.incrCounter(PageTypes.STUB, 1);
                }
            } else {
                reporter.incrCounter(PageTypes.NON_ARTICLE, 1);
            }
            
            if(!p.isArticle() || p.isEmpty()) return;
            String content = p.getContent();
            if(content == null) return;
            if(p.getDocid() == null) return;
            String line = content
                    .replace("\n", " ")
                    .replace("  ", " ")
                    .replace(",","")
                    .replace("(b.", "(b")
                    .replace("(d.", "(d");
            Matcher m = sentenceregex.matcher(line);

            // Assume a whole Wikipedia article has been passed to the mapper; track sentence number by counting

            int sentencect = 0;
            try{
            // For each sentence in the input text:
            while(m.find()){
                // Initialize the minhash vector
                for(int i=0;i<NHASH;i++){
                    MINHASH[i] = Long.MAX_VALUE;
                }
                String sentence = m.group(1);


                int shinglect = 0;
                // Calculate hash vector for each shingle
                String hashval[] = new String[seeds.length];
                // skip sentences that are too short

                if(sentence.length() >= SHINGLELEN){

                for(int i=0;i<sentence.length() - SHINGLELEN + 1; i++){
                    String shingle = sentence.substring(i, i+SHINGLELEN);
                    long hash[] = hashfamily.hash(shingle);
                    // Update the minhash signature
                    for(int j=0;j<hash.length;j++){
                        if(hash[j] < MINHASH[j]){
                            MINHASH[j] = hash[j];
                            hashval[j] = shingle;
                        }
                        //System.out.println("word: " + word + " " + hashes[j]);
                    }
                    // Keep track of the word ct to avoid short sentences
                    shinglect++;
                }
                
                // If the sentence meets min shingle ct requirements, emit the signature and the sentence/doc ID
                if(shinglect > MINLEN && shinglect < MAXLEN){
                    DOCSENT.set(p.getDocid(), sentencect);
                    
                    // generate N k-minhash-signatures
                    // start from same seed, otherwise doesn't work so well
                    Random r = new Random(sigseed);
                    for(int j=0; j<N; j++){
                        for(int i=0; i<K; i++){
                            int x = r.nextInt(NHASH);
                            SIG.set(i, MINHASH[x]);
                        }
                        //context.write(SIG, DOCSENT);
                        output.collect(SIG, DOCSENT);
                    }

                }
            }
                sentencect++;
            }
            
            }catch(Throwable e){
                System.err.println("WARNING: Possible stack overflow from regex at docid " + p.getDocid() + " and sentence # " + sentencect);
            }
        }

        
        
        
        public void configure(JobConf job) {
            rseed = job.getLong("rseed", 112345);
            NHASH = job.getInt("NHASH", 20);
            NHASHOUTPUTBITS = job.getInt("NHASHOUTPUTBITS", 30);
            MINLEN = job.getInt("MINLEN", 75);
            MAXLEN = job.getInt("MAXLEN", 600);
            K = job.getInt("K",  10);
            N = job.getInt("N", 10);
            SHINGLELEN = job.getInt("SHINGLELEN",12);

            seeds = new long[NHASH];
            Random r = new Random(rseed);
            int ct = 0;
            while(ct < NHASH){
                seeds[ct] = r.nextLong();
                ct++;
            }
            sigseed = r.nextLong();
            hashfamily = new MultiplyShiftHash(NHASHOUTPUTBITS,seeds);
            MINHASH = new long[NHASH];
            if(SIG.size() != K){
                for(int i=0; i<K; i++){
                    SIG.add(0);
                }
            }

        }
    }

 
    /**
     * Emits groups of sentences that have the same hash signature. Only emit if there is more than one value for the key. 
     *
     */
    /*
    private static class SignatureReducer extends MapReduceBase implements Reducer<ArrayListOfLongsWritable, PairOfStringInt, PairOfStringInt, PairOfStringInt> {

        // collect all sentences that have hashed to the same hash signature
        //static final ArrayListWritable<PairOfStringInt> nearDuplicateSentenceList = new ArrayListWritable<PairOfStringInt>();
        ArrayList<PairOfStringInt> sentenceList = new ArrayList<PairOfStringInt>();
        @Override
        public void reduce(ArrayListOfLongsWritable key, Iterator<PairOfStringInt> values,
                OutputCollector<PairOfStringInt, PairOfStringInt> output, Reporter reporter)
                        throws IOException {
            sentenceList.clear();

            while (values.hasNext()) {
                PairOfStringInt val = values.next().clone();
                sentenceList.add(val);
            }
            
            if(sentenceList.size() == 1) return;

            for(int i=0;i<sentenceList.size();i++){
                for(int j=i+1;j<sentenceList.size();j++){
                    output.collect(sentenceList.get(i), sentenceList.get(j));
            
                }
            }
        }
    }
    */
    /**
     * Emits groups of sentences that have the same hash signature. Only emit if there is more than one value for the key. 
     *
     */
    private static class SignatureReducer extends MapReduceBase implements Reducer<ArrayListOfLongsWritable, PairOfStringInt, ArrayListOfLongsWritable, ArrayListWritable<PairOfStringInt>> {

        // collect all sentences that have hashed to the same hash signature
        static final ArrayListWritable<PairOfStringInt> nearDuplicateSentenceList = new ArrayListWritable<PairOfStringInt>();
        @Override
        public void reduce(ArrayListOfLongsWritable key, Iterator<PairOfStringInt> values,
                OutputCollector<ArrayListOfLongsWritable, ArrayListWritable<PairOfStringInt>> output, Reporter reporter)
                        throws IOException {
            nearDuplicateSentenceList.clear();

            while (values.hasNext()) {
                PairOfStringInt val = values.next().clone();
                nearDuplicateSentenceList.add(val);
            }
            
            if(nearDuplicateSentenceList.size() == 1) return;
            output.collect(key, nearDuplicateSentenceList);

        }
    }
    
    
    private static final String INPUT = "input";
    private static final String OUTPUT = "output";
    private static final String NUM_REDUCERS = "numReducers";
    private static final String LANGUAGE_OPTION = "wiki_language";

    @SuppressWarnings("static-access")
    @Override
    public int run(String[] args) throws Exception {
        Options options = new Options();
        options.addOption(OptionBuilder.withArgName("path")
                .hasArg().withDescription("bz2 input path").create(INPUT));
        options.addOption(OptionBuilder.withArgName("path")
                .hasArg().withDescription("output path").create(OUTPUT));
        options.addOption(OptionBuilder.withArgName("en|sv|de|cs|es|zh|ar|tr").hasArg()
                .withDescription("two-letter language code").create(LANGUAGE_OPTION));
        options.addOption(OptionBuilder.withArgName("num").hasArg()
                .withDescription("number of reducers").create(NUM_REDUCERS));

        CommandLine cmdline;
        CommandLineParser parser = new GnuParser();
        try {
            cmdline = parser.parse(options, args);
        } catch (ParseException exp) {
            System.err.println("Error parsing command line: " + exp.getMessage());
            return -1;
        }

        if (!cmdline.hasOption(INPUT) || !cmdline.hasOption(OUTPUT)) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.setWidth(120);
            formatter.printHelp(this.getClass().getName(), options);
            ToolRunner.printGenericCommandUsage(System.out);
            return -1;
        }

        String language = "en";
        if (cmdline.hasOption(LANGUAGE_OPTION)) {
            language = cmdline.getOptionValue(LANGUAGE_OPTION);
            if(language.length()!=2){
                System.err.println("Error: \"" + language + "\" unknown language!");
                return -1;
            }
        }

        String inputPath = cmdline.getOptionValue(INPUT);
        String outputPath = cmdline.getOptionValue(OUTPUT);
        int reduceTasks = cmdline.hasOption(NUM_REDUCERS) ? Integer.parseInt(cmdline.getOptionValue(NUM_REDUCERS)) : 4;

        LOG.info("Tool name: " + this.getClass().getName());
        LOG.info(" - bz2 file: " + inputPath);
        LOG.info(" - output file: " + outputPath);
        LOG.info(" - language: " + language);

        JobConf conf = new JobConf(getConf(), MinhashWikipediaPages.class);
        conf.setJobName(String.format("MinhashWikipediaPages[%s: %s, %s: %s, %s: %s]", INPUT, inputPath, OUTPUT, outputPath, LANGUAGE_OPTION, language));

        conf.setLong("rseed", 1123456);
        conf.setInt("NHASH", 20);
        conf.setInt("NHASHOUTPUTBITS", 30);
        //conf.setInt("MINLEN", 20);
        conf.setInt("MINLEN", 75);
        conf.setInt("MAXLEN", 600);
        conf.setInt("K",  10);
        conf.setInt("N", 10);
        conf.setInt("SHINGLELEN",12);

        conf.setNumMapTasks(4);
        conf.setNumReduceTasks(reduceTasks);

        FileInputFormat.setInputPaths(conf, new Path(inputPath));
        FileOutputFormat.setOutputPath(conf, new Path(outputPath));

        if(language != null){
            conf.set("wiki.language", language);
        }
        
        conf.setMapperClass(SignatureMapper.class);
        conf.setReducerClass(SignatureReducer.class);
        
        //conf.setInputFormat(WikipediaPageInputFormat.class);
        conf.setInputFormat(SequenceFileInputFormat.class);
        conf.setOutputFormat(SequenceFileOutputFormat.class);
        //conf.setOutputFormat(TextOutputFormat.class);
        
        // Set heap space - using old API
        conf.set("mapred.job.map.memory.mb", "2048");
        conf.set("mapred.map.child.java.opts", "-Xmx2048m");
        conf.set("mapred.job.reduce.memory.mb", "2048");
        conf.set("mapred.reduce.child.java.opts", "-Xmx2048m");
        //conf.set("mapred.child.java.opts", "-Xmx2048m");
        
        conf.setMapOutputKeyClass(ArrayListOfLongsWritable.class);
        conf.setMapOutputValueClass(PairOfStringInt.class);
        
        conf.setOutputKeyClass(ArrayListOfLongsWritable.class);
        conf.setOutputValueClass(ArrayListWritable.class);

        // Delete the output directory if it exists already.
        Path outputDir = new Path(outputPath);
        FileSystem.get(conf).delete(outputDir, true);

        JobClient.runJob(conf);

        return 0;
    }

    public MinhashWikipediaPages() {}

    public static void main(String[] args) throws Exception {
        ToolRunner.run(new MinhashWikipediaPages(), args);
    }
}
