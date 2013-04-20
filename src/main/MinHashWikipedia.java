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
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapred.FileInputFormat;
import org.apache.hadoop.mapred.FileOutputFormat;
import org.apache.hadoop.mapred.JobClient;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.MapReduceBase;
import org.apache.hadoop.mapred.Mapper;
import org.apache.hadoop.mapred.Reducer;
import org.apache.hadoop.mapred.OutputCollector;
import org.apache.hadoop.mapred.Reporter;
import org.apache.hadoop.mapred.TextOutputFormat;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;
import org.apache.log4j.Logger;

import courseproj.hash.MultiplyShiftHash;
import courseproj.wikipedia.WikipediaPage;
import courseproj.wikipedia.WikipediaPageInputFormat;
import edu.umd.cloud9.io.array.ArrayListOfLongsWritable;
import edu.umd.cloud9.io.pair.PairOfLongInt;


/**
 * @author Samet Ayhan
 * @author Joshua Bradley
 * @author Sarah Weissman
 */
public class MinHashWikipedia extends Configured implements Tool {
    private static final Logger LOG = Logger.getLogger(MinHashWikipedia.class);

    private static class SentenceMapperRegex extends MapReduceBase implements
    Mapper<LongWritable, WikipediaPage, ArrayListOfLongsWritable, Text> {

        
        static long rseed = 1123456;
        static long seeds[];
        static long sigseed; // Seed to use when randoly selecting signature vectors
        static long MINHASH[];
        
        static int NHASH = 20; // Total number of hashes per sentence
        static int K = 8; // Length of hash vector
        static int N = 5; // Number of hashes per input sentence (N < NHASH)
        static int NHASHOUTPUTBITS = 30;
        static int SHINGLELEN = 15;
        static int MINLEN = 20;
        //static int NSENTENCE = 3; // Number of sentences to match at a time
        static MultiplyShiftHash hashfamily;

        // The minhash signature
        static final ArrayListOfLongsWritable SIG = new ArrayListOfLongsWritable(K);
        
        // The document-sentence identifier
        static final PairOfLongInt DOCSENT = new PairOfLongInt();
        // for testing
        static final Text SENTENCE = new Text();
        
        
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
        
        // seed list could be produced in job and passed as message
        static{
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
          for(int i=0; i<K; i++){
              SIG.add(0);
          }
        
        }   
        
        public void map(LongWritable key, WikipediaPage p, OutputCollector<ArrayListOfLongsWritable, Text> output,
                Reporter reporter) throws IOException {
            
            String line = p.getContent().replace("\n", " ");
            Matcher m = sentenceregex.matcher(line);

            // Assume each doc is on its own line; track sentence number by counting
            int sentencect = 0;

            // For each sentence in the input text:
            while(m.find()){
              // Initialize the minhash vector
              for(int i=0;i<NHASH;i++){
                MINHASH[i] = Long.MAX_VALUE;
              }
              String sentence = m.group(1);
              //System.out.println("Sentence: " + sentence);

              
              int shinglect = 0;
              // Calculate hash vector for each shingle
              String hashval[] = new String[seeds.length];
              // skip sentences that are too short
              if(sentence.length() < SHINGLELEN) return;
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
              
              /*
              for(int i=0;i<MINHASH.length;i++){
                  System.out.print(MINHASH[i] + " ");
              }
              System.out.println();
              for(int i=0;i<hashval.length;i++){
                  System.out.print(hashval[i] + " ");
              }
              System.out.println();
              */

              // If the sentence meads min shingle ct requirements, emit the signature and the sentence/doc ID
              if(shinglect > MINLEN){
                  DOCSENT.set(key.get(), sentencect);
                  SENTENCE.set(sentence + " " + key.get() + ":" + sentencect);
                
                // generate N k-minhash-signatures
                // start from same seed, otherwise doesn't work so well
                Random r = new Random(sigseed);
                for(int j=0; j<N; j++){
                    for(int i=0; i<K; i++){
                        int x = r.nextInt(NHASH);
                        SIG.set(i, MINHASH[x]);
                    }
                    //context.write(SIG, DOCSENT);
                    output.collect(SIG, SENTENCE);
                }
                
              }
              

              sentencect++;
            }
          }
    }
    
    /**
     * Emits groups of sentences that hash to the same value. Only emits if there is more than one value for the key. 
     *
     */
    //private static class GroupReducer extends Reducer<ArrayListOfLongsWritable, PairOfLongInt, ArrayListOfLongsWritable, PairOfLongInt> {
    private static class GroupReducer extends MapReduceBase implements Reducer<ArrayListOfLongsWritable, Text, ArrayListOfLongsWritable, Text> {
  /*
      @Override
        @Override
        public void reduce(ArrayListOfLongsWritable key, Iterator<PairOfLongInt> values,
                OutputCollector<ArrayListOfLongsWritable, PairOfLongInt> output, Reporter reporter)
                throws IOException {
            boolean gt1 = false;
            
            while (values.hasNext()) {
             PairOfLongInt val = values.next();
              if(values.hasNext()) gt1 = true;
              if(gt1) output.collect(key, val);
            }
            
        }
        
      }
      */

        @Override
        public void reduce(ArrayListOfLongsWritable key, Iterator<Text> values,
                OutputCollector<ArrayListOfLongsWritable, Text> output, Reporter reporter)
                throws IOException {
            boolean gt1 = false;
            
            while (values.hasNext()) {
              Text val = values.next();
              if(values.hasNext()) gt1 = true;
              if(gt1) output.collect(key, val);
            }
            
        }
    }


    private static final String INPUT_OPTION = "input";
    private static final String OUTPUT_OPTION = "output";
    private static final String LANGUAGE_OPTION = "wiki_language";

    @SuppressWarnings("static-access")
    @Override
    public int run(String[] args) throws Exception {
        Options options = new Options();
        options.addOption(OptionBuilder.withArgName("path")
                .hasArg().withDescription("XML dump file").create(INPUT_OPTION));
        options.addOption(OptionBuilder.withArgName("path")
                .hasArg().withDescription("output path").create(OUTPUT_OPTION));
        options.addOption(OptionBuilder.withArgName("en|sv|de|cs|es|zh|ar|tr").hasArg()
                .withDescription("two-letter language code").create(LANGUAGE_OPTION));
        
        CommandLine cmdline;
        CommandLineParser parser = new GnuParser();
        try {
            cmdline = parser.parse(options, args);
        } catch (ParseException exp) {
            System.err.println("Error parsing command line: " + exp.getMessage());
            return -1;
        }

        if (!cmdline.hasOption(INPUT_OPTION) || !cmdline.hasOption(OUTPUT_OPTION)) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp(this.getClass().getName(), options);
            ToolRunner.printGenericCommandUsage(System.out);
            return -1;
        }

        String language = null;
        if (cmdline.hasOption(LANGUAGE_OPTION)) {
            language = cmdline.getOptionValue(LANGUAGE_OPTION);
            if(language.length()!=2){
                System.err.println("Error: \"" + language + "\" unknown language!");
                return -1;
            }
        }

        String inputPath = cmdline.getOptionValue(INPUT_OPTION);
        String outputPath = cmdline.getOptionValue(OUTPUT_OPTION);

        LOG.info("Tool name: " + this.getClass().getName());
        LOG.info(" - XML dump file: " + inputPath);
        LOG.info(" - language: " + language);
        LOG.info(" - output path: " + outputPath);
        
        JobConf conf = new JobConf(getConf(), MinHashWikipedia.class);
        conf.setJobName(String.format("WikiReader[%s: %s, %s: %s]", INPUT_OPTION, inputPath, LANGUAGE_OPTION, language));

        conf.setNumMapTasks(10);
        conf.setNumReduceTasks(1);

        FileInputFormat.setInputPaths(conf, new Path(inputPath));
        FileOutputFormat.setOutputPath(conf, new Path(outputPath));

        if(language != null){
            conf.set("wiki.language", language);
        }
        conf.setMapOutputKeyClass(ArrayListOfLongsWritable.class);
        conf.setMapOutputValueClass(Text.class);
        conf.setInputFormat(WikipediaPageInputFormat.class);
        conf.setOutputFormat(TextOutputFormat.class);
        conf.setMapperClass(SentenceMapperRegex.class);
        conf.setReducerClass(GroupReducer.class);

        // Delete the output directory if it exists already.
        Path outputDir = new Path(outputPath);
        FileSystem.get(conf).delete(outputDir, true);
        
        JobClient.runJob(conf);

        return 0;
    }

    public MinHashWikipedia() {}

    public static void main(String[] args) throws Exception {
        ToolRunner.run(new MinHashWikipedia(), args);
    }
}