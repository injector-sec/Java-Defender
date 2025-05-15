# Java-Defender
Java defender is a Java application that uses utilizes the socket programming, Client-server architecture and Meta defender API to perfrom seeveral duties.
![Screenshot 2025-05-15 8 14 13 PM](https://github.com/user-attachments/assets/59dd7fce-4ce2-4976-8556-701e88c381bc)

## Functionalities
1. Trim Files and Strings: You can give selected text strings and files to remove all leading and trailing spaces from it.
2. Sort Files and Strings: Given Files or Strings are sorted.
3. Scan Files: You can give any file that you feel might be suspicious and click send. That file, then will be sent to server from where it will be sent to MetaDefender and scanned using 60 Anti-viruses and later results are fetched by the server and sent back to client.
4. Scan URL: Using MetaDefender urls are scanned for phishing or spamming using large databse of MetaDefender
5. Save Server logs
6. Save Client logs

**All Those functions are done using server's resources**
This project can be an excellent step for you to navigate in socket programming and client server architecture.
Json library is added to help program parse json.
Bash scripts are added to automatically compile and run the files.
## How to Run?
If you are on a linux environment just do this:
  $ chmod +x RunClient.sh RunServer.sh
  $ ./RunServer.sh
  $ ./RunClient.java
you can manually compile it by running following command
 **Compile both files with the JSON library**
javac -cp .:json.jar MetaDefenderServer.java MetaDefenderClient.java

**Second terminal - run the client (in a new terminal window)**
java -cp .:json.jar MetaDefenderClient

In Windows, run it using these commands:

javac -cp .;json.jar MetaDefenderServer.java MetaDefenderClient.java

java -cp .;json.jar MetaDefenderClient

## Pictures
### Options
![Screenshot 2025-05-15 8 14 02 PM](https://github.com/user-attachments/assets/1a5634fd-5b18-4d92-ad81-7365309cd5be)

### 2. File Scan Results
![Screenshot 2025-05-15 8 15 04 PM](https://github.com/user-attachments/assets/7e83cea8-2fa4-4d6f-98b0-d20dcf05cbb5)

### 3. URL Scan Results
![Screenshot 2025-05-15 8 15 44 PM](https://github.com/user-attachments/assets/1c13b5b9-53b8-4dd2-a482-c3bc2719f08d)
### 4. Server Logs
![Screenshot 2025-05-15 8 16 09 PM](https://github.com/user-attachments/assets/1d116ff4-4564-492b-b2e5-bb3d3cff5770)
## Technologies Used:
1. Java Swing
2. Core Java
3. Socket Programming

Made with <3

