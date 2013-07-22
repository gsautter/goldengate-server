/*
 * Copyright (c) 2006-2008, IPD Boehm, Universitaet Karlsruhe (TH)
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of the Universität Karlsruhe (TH) nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY UNIVERSITÄT KARLSRUHE (TH) AND CONTRIBUTORS 
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE REGENTS OR CONTRIBUTORS BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package de.uka.ipd.idaho.goldenGateServer.utilities;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Utility class holding shared methods for webapp extension and server
 * component installer.
 * 
 * @author sautter
 */
class InstallerUtils {
	static void updateFile(File baseFolderPath, String fileName, InputStream source, long sourceLastModified) throws IOException {
		
		//	check if target file switched off in local installation, and if so, leave it like that
		File offTargetFile = new File(baseFolderPath, (fileName + ".off"));
		if (offTargetFile.exists())
			fileName = (fileName + ".off");
		
		//	create target file
		File targetFile = new File(baseFolderPath, fileName);
		boolean targetFileExists = targetFile.exists();
		
		//	check if more recent version of file available in file system
		if (targetFile.exists() && (sourceLastModified < (targetFile.lastModified() + 1000))) {
			System.out.println(" - retaining " + fileName);
			return;
		}
		
		//	make sure folders exist
		targetFile.getParentFile().mkdirs();
		
		//	create target file
		targetFile.createNewFile();
		
		//	report status
		System.out.println(" - " + (targetFileExists ? "updating" : "installing") + " " + fileName);
		
		//	copy file
		OutputStream target = new BufferedOutputStream(new FileOutputStream(targetFile));
		int count;
		byte[] data = new byte[1024];
		while ((count = source.read(data, 0, 1024)) != -1)
			target.write(data, 0, count);
		
		//	close streams
		target.flush();
		target.close();
		
		//	set timestamp of copied file
		try {
			targetFile.setLastModified(sourceLastModified);
//			System.out.println("   - last modified set to " + zipLastModified);
//			System.out.println("   --> file extracted");
		}
		catch (RuntimeException re) {
//			System.out.println("   - error setting file timestamp: " + re.getClass().getName() + " (" + re.getMessage() + ")");
			re.printStackTrace(System.out);
		}
	}
}