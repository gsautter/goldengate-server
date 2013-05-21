Copyright (c) 2006-, IPD Boehm, Universitaet Karlsruhe (TH) / KIT, by Guido Sautter
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright
      notice, this list of conditions and the following disclaimer in the
      documentation and/or other materials provided with the distribution.
    * Neither the name of the Universität Karlsruhe (TH) nor the
      names of its contributors may be used to endorse or promote products
      derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY UNIVERSITÄT KARLSRUHE (TH) / KIT AND CONTRIBUTORS 
"AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
ARE DISCLAIMED. IN NO EVENT SHALL THE REGENTS OR CONTRIBUTORS BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.



SYSTEM REQUIREMENTS

Java Runtime Environment 1.5 or higher, Sun/Oracle JRE recommended

Apache Tomcat 5.5 or higher (other servlet containers should work as well, but have not been tested yet)

A database server, e.g. PostgreSQL (drivers included for version 8.2) or Microsoft SQL Server (drivers included)
Instead, you can also use Apache Derby embedded database (included)
(using Apache Derby is the default configuration, so you can test RefBank without setting up a database)



SETTING UP A GoldenGATE Server web frontend

Download RefBank.war into Tomcat's webapps folder

Call http://localhost:8080/GgServer/ to make Tomcat extract GgServer.war
(you might have to re-start Tomcat for it to recognize the WAR file)
(adjust server name and port if working remotely or Tomcat runs on a different port, respectively)

Tomcat's webapps folder should have a GgServer sub folder now, and it's time for some configuration:

    To enable the GoldenGATE Server web frontend node to cache data in the file system and connect to the backing GoldenGATE Server, give the web application the permission to create and manipulate files and folders within its deployment folder and to establish outgoing network connections (there are two ways to achieve this):

        The simple, but very coarse way is to disable Tomcat's security manager altogether (not recommended)

        More finegrained way is to add the permission statement below to Tomcat's security configuration (recommended); the security configuration resides inside Tomcat's conf folder, which is located on the same level as the webapps folder; the actual configuration file to add the permission to is either catalina.policy directly in the conf folder, or 04webapps.policy in the conf/policy.d folder, whichever is present; if both files are present, either will do:

        grant codeBase "file:${catalina.base}/webapps/GgServer/WEB-INF/-" {
        	permission java.net.SocketPermission "*.*", "connect";
        	permission java.io.FilePermission "WEB-INF/-", "read,write,delete,execute";
        }

    Adjust the web.cnfg file in the WEB-INF folder:

        Adjust the host name and port of the backing GoldenGATE Server

To make your GoldenGATE Server credit your institution, do the following:

    Put your own institution logo in the images folder

    Customize the files footer.html and popupFooter.html in the WEB-INF folder to include your institution name and logo by replacing
    yourLogo.gif with the name of your logo image file,
    yourUrl.org with the link to your institution,
    Your Institution with the name of your institution,
    YourInstitutionAcronym with the acronym of your institution