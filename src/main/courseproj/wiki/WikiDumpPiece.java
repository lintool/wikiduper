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

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

import org.apache.tools.bzip2.CBZip2InputStream;

/**
 * Class for working with bz2-compressed Wikipedia article dump files on local
 * disk.
 * 
 * @author Jimmy Lin
 * @author Peter Exner
 */
public class WikiDumpPiece {

	public static void main(String[] args) throws Exception {
		
	  if (args.length != 3) {
			System.err.println("usage: [dump file] [offset] [output]");
			System.exit(-1);
		}

	  long streamoffset = Long.parseLong(args[1]);
	  FileOutputStream fos = new FileOutputStream(args[2]);
	  OutputStreamWriter ow = new OutputStreamWriter(fos);
	  FileInputStream fis = new FileInputStream(args[0]);
	  byte[] ignoreBytes = new byte[2];
	  fis.read(ignoreBytes); // "B", "Z" bytes from commandline tools
	  InputStreamReader in = new InputStreamReader(new CBZip2InputStream(fis));
	  char cbuf[] = new char[100];

	  int n;
	  int ct = 0;

	  System.out.println("streamoffset: " + streamoffset);
	  while(true){
	   while((n = in.read(cbuf)) != -1){ // || (nextStream() && (n = in.read(cbuf)) != -1)){
	     ct += n;
	     ow.write(cbuf);
	   }

	   if(ct >= streamoffset){
	     break;
	   }else if(fis.available() > 0){
	     fis.read(ignoreBytes);
	     in = new InputStreamReader(new CBZip2InputStream(fis));
	   }else{
	     break;
	   }
	  }
	  System.out.println("Byte ct = " + ct);
	  ow.flush();	  
	  in.close();
	  ow.close();
	}

}
