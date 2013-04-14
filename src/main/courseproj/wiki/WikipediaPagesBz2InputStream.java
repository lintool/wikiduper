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

package courseproj.wiki;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.regex.Pattern;

import org.apache.tools.bzip2.CBZip2InputStream;

import edu.umd.cloud9.collection.wikipedia.WikipediaPage;
import edu.umd.cloud9.collection.wikipedia.language.WikipediaPageFactory;

/**
 * Class for working with bz2-compressed Wikipedia article dump files on local
 * disk.
 * 
 * @author Jimmy Lin
 * @author Peter Exner
 */
public class WikipediaPagesBz2InputStream {
	private static int DEFAULT_STRINGBUFFER_CAPACITY = 1024;

	private BufferedReader br;
	private FileInputStream fis;
  private long curroffset = 0;
	/**
	 * Creates an input stream for reading Wikipedia articles from a
	 * bz2-compressed dump file.
	 * 
	 * @param file
	 *            path to dump file
	 * @throws IOException
	 */
	public WikipediaPagesBz2InputStream(String file) throws IOException {
    br = null;
    fis = new FileInputStream(file);
    byte[] ignoreBytes = new byte[2];
    fis.read(ignoreBytes); // "B", "Z" bytes from commandline tools
    br = new BufferedReader(new InputStreamReader(new CBZip2InputStream(fis)));
	}
	
	public boolean nextStream() throws IOException{
	  byte[] ignoreBytes = new byte[2];
     if(fis.available() > 0){
       fis.read(ignoreBytes); // "B", "Z" bytes from commandline tools
       br = new BufferedReader(new InputStreamReader(new CBZip2InputStream(fis)));
       return true;
     }else return false;
	}
	
	
	/**
	 * Reads the next Wikipedia page.
	 * 
	 * @param page
	 *            WikipediaPage object to read into
	 * @return <code>true</code> if page is successfully read
	 * @throws IOException
	 */
public boolean readNext(WikipediaPage page) throws IOException {
    
    String s = null;
    StringBuffer sb = new StringBuffer(DEFAULT_STRINGBUFFER_CAPACITY);

    while ((s = br.readLine()) != null || (nextStream() && (s = br.readLine()) != null)) {
       if (s.endsWith("<page>"))
         break;
    }

    
    if (s == null){
      fis.close();
      br.close();
      return false;
    }

    sb.append(s + "\n");

    while ((s = br.readLine()) != null) {
      //System.out.println("line: " + s);
      sb.append(s + "\n");

      if (s.endsWith("</page>"))
        break;
    }

    WikipediaPage.readPage(page, sb.toString());

    return true;
  }

public boolean readPage(WikipediaPage page, long streamoffset, String id) throws IOException {
  //System.out.println("in id = " + id);
  //System.out.println("in offset = " + streamoffset);
  //System.out.println("start offset = " + this.curroffset);
  long skipn = streamoffset - this.curroffset;
  long n = br.skip(skipn);
  this.curroffset += n;
  
  while(n != 0 && skipn - n > 0){
    //System.out.println("asked skip = " + skipn + " actual = " + n);
    nextStream();
    skipn = streamoffset - this.curroffset;
    n = br.skip(skipn);
    this.curroffset += n;
  }
  
  //System.out.println("asked skip = " + skipn + " curroffset = " + this.curroffset);
  //fis.skip(streamoffset - this.curroffset);
  String s = null;
  StringBuffer sb = new StringBuffer(DEFAULT_STRINGBUFFER_CAPACITY);
  int ct = 0;
  while ((s = br.readLine()) != null || (nextStream() && (s = br.readLine()) != null)) {
    if (s.endsWith("<page>")){

      ct++;
      sb.setLength(0);
      sb.append(s + "\n");
      sb.append(br.readLine()); //title
      sb.append(br.readLine()); //ns
      s = br.readLine();
      //System.out.println("id ? " + s);
      //if(ct%1000 == 0){
      //  System.out.println("ct = " + ct + " " + s);
      //}
      if(s.endsWith(id+"</id>"))
       break;
    }
  }

  
  if (s == null){
    //System.out.println("s null");
    fis.close();
    br.close();
    return false;
  }

  sb.append(s + "\n");

  //Pattern idpat = Pattern.compile(".*<id>([0-9]+)</id>.*");
  
  while ((s = br.readLine()) != null) {
    sb.append(s + "\n");
    if (s.endsWith("</page>"))
      break;
  }

  WikipediaPage.readPage(page, sb.toString());

  return true;
}

	public static void main(String[] args) throws Exception {
		if (args.length != 2) {
			System.err.println("usage: [dump file] [language]");
			System.exit(-1);
		}
	  
		WikipediaPage p = WikipediaPageFactory.createWikipediaPage(args[1]);
		
		WikipediaPagesBz2InputStream stream = new WikipediaPagesBz2InputStream(args[0]);

   while (stream.readNext(p)){
     System.out.println(p.getContent().replace("\n", ""));
     
     //System.out.println(p.getContent());
		}

	}
	

}
