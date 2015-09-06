import java.lang.System;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.StringTokenizer;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;

//
// This is an implementation of a simplified version of a command 
// line ftp client. The program takes no arguments. 
//
public class CSftp {

	public static boolean controlConnectionOpen;
	static final int MAX_LEN = 255;

	//
	// COMMAND Connection
	//
	protected static Socket commandSocket;
	private static PrintStream outp;
	private static BufferedReader inp;
	private static BufferedReader stdIN;

	//
	// DATA Connection
	//
	protected static Socket dataSocket;
	private static PrintWriter dataOUT;
	private static BufferedReader dataIN;
	private static BufferedReader dataSTDIN;

	public static void main(String[] args) {
		initializePrompt();
	}

	public static void initializePrompt(){
		controlConnectionOpen=false;
		try {
			for (int len = 1; len > 0;) {
				byte cmdString[] = new byte[MAX_LEN];
				System.out.print("csftp> ");
				len = System.in.read(cmdString);
				if (len <= 0) {
					printError("900 Invalid command.");
					break;
				}
				
				// Start processing the command here.
				String command = new String(cmdString, "UTF-8");
				parseCommand(command);
			}
		} 
		catch (Exception exception) {
			printError("899 Processing error. Error details: ");
			exception.printStackTrace();
		}
	}

	public static void parseCommand(String command) {

		String trimmedCommand = command.trim();

		try {
		if (trimmedCommand.length() >= 4 && trimmedCommand.toUpperCase().substring(0, 4).equals("OPEN")) {
			if (!controlConnectionOpen){
				open(trimmedCommand);
			} else {
				printError("803 Supplied command not expected at this time.");
			}
		} else if (command.substring(0, 1).equals("#")  // silently ignore lines beginning with "#"
				|| trimmedCommand.isEmpty()){			// silently ignore empty lines
			return; 
			
		} else if (trimmedCommand.toUpperCase().equals("CLOSE")){
			if(!checkParameters(trimmedCommand, "CLOSE")){
				return;
			}
			close();
		} else if (trimmedCommand.toUpperCase().equals("QUIT")){
			if(!checkParameters(trimmedCommand, "QUIT")){
				return;
			}
			quit();
		} else if (trimmedCommand.toUpperCase().equals("DIR")){
			if(!checkParameters(trimmedCommand, "DIR")){
				return;
			}
			if (controlConnectionOpen){
				processDIRCommand();
				printReceivedMsg(getServerMessage(inp));
			} else {
				printError("803 Supplied command not expected at this time.");
			}
		}
		else if (trimmedCommand.length() > 2 && trimmedCommand.substring(0, 2).toUpperCase().equals("CD")){
			if(!checkParameters(trimmedCommand, "CD")){
				return;
			}
			String pathname = trimmedCommand.substring(3, trimmedCommand.length()).trim();
			sendCommandWithParameter("CWD", pathname);
			printReceivedMsg(getServerMessage(inp));

		} else if (trimmedCommand.length() >= 3 && trimmedCommand.substring(0, 3).toUpperCase().equals("GET")){
			if(!checkParameters(trimmedCommand, "GET")){
				return;
			}
			String pathname = trimmedCommand.substring(4, trimmedCommand.length());
			get(pathname);
			
		} else if (trimmedCommand.length() >= 3 && trimmedCommand.substring(0, 3).toUpperCase().equals("PUT")){
			if (!checkParameters(trimmedCommand, "PUT")){
				return;
			}
			String pathname = trimmedCommand.substring(4, trimmedCommand.length());
			put(pathname);	
		}else{
			//otherwise do not accept the command
			printError("800 Invalid command");
		}
		} catch (IOException e){
			printError("898 Input error while reading commands, terminating.");
			closeActiveConnections();
			System.exit(0);
		}
		return;
	}

	private static boolean checkParameters(String userInput, String string) {
		
		int count=0;
		StringTokenizer st = new StringTokenizer(userInput, " ");	
		
		while(st.hasMoreElements()){		
			count++;
			st.nextToken();
			}
		
		//Check for proper number of arguments
		if(string.equals("CLOSE")){
			if (!(count==1)){
				printError("801: Incorrect Number of Arguments.");
				return false;
			}
		}
		else if (string.equals("CD")){
			if (!(count==2)){
				printError("801: Incorrect Number of Arguments.");
				return false;
			}
		}
		else if (string.equals("DIR")){
			if (!(count==1)){
				printError("801: Incorrect Number of Arguments.");
				return false;
			}
		}
		else if (string.equals("GET")){
			if (!(count== 2)){
				printError("801: Incorrect Number of Arguments.");
				return false;
			}
		}
		else if (string.equals("OPEN")){
			if (!(count== 3)){
				printError("801: Incorrect Number of Arguments.");
				return false;
			}
		}
		else if (string.equals("PUT")){
			if (!(count==2)){
				printError("801: Incorrect Number of Arguments.");
				return false;
			}
		}
		else if (string.equals("QUIT")){
			if (!(count==1)){
				printError("801: Incorrect Number of Arguments.");
				return false;
			}
		}
		
		//Check for proper type of arguments
		
		if(string.equals("CLOSE")){
			return true;	
		} else if (string.equals("DIR")){
			return true;	
		} else if (string.equals("GET")){
			return true;
		}else if (string.equals("PUT")){
			return true;
		}else if (string.equals("QUIT")){
			return true;	
		}else if (string.equals("CD")){
			return true;
		}else{
			printError("800 Invalid command");
			return false;
		}
	}

	private static void printError(String error) {
		System.err.println(error);
	}

	private static void closeActiveConnections() {
		controlConnectionOpen = false;
		try {
			if (commandSocket != null){
				commandSocket.close();
			}
			if (dataSocket != null) {
				dataSocket.close();
			}
		} catch (IOException e) {
			//do nothing as it is quite possible these are already closed.
		}
	}

	private static void open(String command) {
		// TODO Add another if clause for situation where port number is not provided.
		String woOPEN = command.substring(5, command.length());
		int firstSpace = woOPEN.indexOf(' ');
		String SERVER;
		String PORT;
		if (firstSpace != -1) {
			SERVER = woOPEN.substring(0, firstSpace);
			PORT = woOPEN.substring(firstSpace + 1, woOPEN.length());
		} else {
			SERVER = woOPEN.trim();
			PORT = "21";
		}
		runSession(SERVER, PORT);
		return;
	}

	private static void runSession(String server, String prt) {
		try {
			int port = Integer.parseInt(prt);
			String hostname = server;
			// Create socket and make connection
			establishConnection(hostname, port);

			//process commands and replies
			String reply = "...";
			reply = getServerMessage(inp);
			printReceivedMsg(reply);

			// if able to connect to FTP server, display login prompts.
			if (checkReturnCode(reply, "220")){
			displayUserLoginPrompt(hostname);
			} else {
				printError("Error: Unable to connect to FTP server");
				return;
			}

			reply = getServerMessage(inp); 
			printReceivedMsg(reply);
			
			if (checkReturnCode(reply, "230")){
			//displaycsftpPrompt(reply);
				return;
			} else {
				inp.close();
				commandSocket.close();
				controlConnectionOpen = false;
				printError("Error: Unable to login to FTP server");
				return;
			}
			
			//inp.close();
			//commandSocket.close();
		} catch (UnknownHostException e) {
			e.printStackTrace();
			System.exit(1);
		} catch (NullPointerException e) {
			printError("Please attempt connection again...");
		}
		catch (NumberFormatException e){
			printError("802: Invalid argument: port number must be int");
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

private static void processDIRCommand() throws UnknownHostException, IOException {
		String reply;
		String files;
		String hostName = "";
		int portNumb = 0;
		try{
			// print (and therefore clear) any buffered, unprinted messages from the server.
			if (inp.ready()){
				reply = getServerMessage(inp);
				printReceivedMsg(reply);
			}
		
			sendCommand("TYPE I"); // set data transfer type to binary. 
			reply = getServerMessage(inp);
			printReceivedMsg(reply);
			sendCommand("PASV");
			reply = getServerMessage(inp);
			printReceivedMsg(reply);
			if (checkReturnCode(reply, "227")){
				hostName = parseAddress(reply);
				portNumb = parsePort(reply);
				establishDataConnection(hostName, portNumb);
				sendCommand("LIST");
				reply = inp.readLine();
				files = getServerMessage(dataIN);
				printReceivedMsg(reply);
				System.out.println(files);
			} else {
				return;
			}
		}
	catch(NullPointerException e){
		printError("Please attempt data connection again...");
	}
	}

	/**
	 * Checks whether a reply from the FTP server contains a specified return code.
	 * @param serverReply the reply received from the server.
	 * @param returnCode the return code to check for in the reply
	 */
	private static boolean checkReturnCode(String serverReply, String returnCode) {
		
		// The server reply may contain multiple lines, possibly only one of which 
		// contains the desired return code. Each line must be checked.
		
		// Split a single server message containing multiple lines into separate strings.
		String[] messageLines = serverReply.split("\\r?\\n"); 

		// Check each separate string for the desired return code:
		for (int i = 0; i < messageLines.length; i++){
			if (serverReply.length() >= 3 && serverReply.substring(0,3).equals(returnCode)){
				return true;}
		} return false;
	}

	private static void displayUserLoginPrompt(String server) throws IOException {

		System.out.println("Name ("+ server +"): ");
		String username = promptForInput().trim();

		//Send the command to the server
		sendCommandWithParameter("USER", username);

		//Print the server reply. User types in password, and goes to 331 below
		String reply = getServerMessage(inp);
		printReceivedMsg(reply);

		System.out.println("Password: ");	
		String password = promptForInput().trim();

		//Send password to server.
		sendCommandWithParameter("PASS", password);

	}

	/**
	 * prompts the user for input following "csftp> "
	 * returns the user input as a String
	 */
	private static String promptForInput() {
		byte userInput[] = new byte[MAX_LEN];
		System.out.print("csftp> ");

		try {
			System.in.read(userInput);
		} 
		catch (IOException exception) {
			System.err.println("998 Input error while reading commands, terminating.");
		}	

		// convert user input into a String, then return
		String input = null;
		try {
			input = new String(userInput, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			System.err.println("998 Input error while reading commands, terminating.");
		}
		return input;	
	}

	private synchronized static String getServerMessage(BufferedReader br){
		String msg = null;
		try {
			msg = br.readLine();
			while (br.ready()){
				msg = msg + '\n' + br.readLine();
			}
		} catch (IOException e) {
			printError("825 Control connection I/O error, closing control connection");
			try {
				commandSocket.close();
			} catch (IOException f) {
				printError("899 Processing error. Unable to close control connection socket.");
			}
			controlConnectionOpen=false;
		}
		return msg;
	}

	/**
	 * Prints a message received from the FTP server.
	 * If message string contains new line characters, string is separated and printed on
	 * multiple lines.
	 * Each printed line is prefixed with '<-- ' to indicate that it is from the server.
	 * @param message A message string received from the server.
	 */
	private static void printReceivedMsg(String message) {
		// Split a message full of line terminating characters into an array of separate Strings.
		String[] messageLines = message.split("\\r?\\n"); 

		// Print each separate string:
		for (int i = 0; i < messageLines.length; i++){
			System.out.println("<-- " + messageLines[i]);
		}
	}

	/**
	 * Echos a message that has been sent to the server to the console. 
	 * Typically this message would be a command, such as PASV, TYPE I, etc.
	 * Each line is prefixed with '--> ' to indicate that it was sent from the client to the server.
	 * @param message A message string to echo back to the console.
	 */
	private static void printSentMsg(String message) {
		System.out.println("--> " + message);
	}

	private static void establishConnection(String hostname, int port) throws NullPointerException, UnknownHostException, IOException {
		
		ConnectionThread ct = new ConnectionThread(hostname, port, false);
		Thread newThrd = new Thread(ct);
		newThrd.start();
	
		long endTimeMillis = System.currentTimeMillis() + 30000;
        while (newThrd.isAlive()) {
            if (System.currentTimeMillis() > endTimeMillis) {
                printError("820: Control connection to "+hostname+" on port "+port+" failed to open.");
                controlConnectionOpen=false;
                return;
            }
        } 
        controlConnectionOpen=true;
        System.out.println("Connected to " + hostname + ".");

		// Output stream to write to the server. It gives you a stream to output on.
		outp      = new PrintStream(commandSocket.getOutputStream(), true);
		// Input stream. Enables to get messages from the server.
		inp    = new BufferedReader(new InputStreamReader(commandSocket.getInputStream()));
		// We want the user to input something. We take it from the std.in. Send this to server.
		stdIN = new BufferedReader(new InputStreamReader(System.in));

	}

	// establish a new data connection
	private static void establishDataConnection(String hostname, int port) throws UnknownHostException, IOException{

		ConnectionThread ct = new ConnectionThread(hostname, port, true);
		Thread newThrd = new Thread(ct);
		newThrd.start();
	
		long endTimeMillis = System.currentTimeMillis() + 30000;
        while (newThrd.isAlive()) {
            if (System.currentTimeMillis() > endTimeMillis) {
            	printError("830: Data transfer connection to "+hostname+" at port "+port+" failed to open.");
                controlConnectionOpen=false;
                return;
            }
        }
        
        System.out.println("Connected to " + hostname + ".");

		// Output stream to write to the server. It gives you a stream to output on.
		dataOUT      = new PrintWriter(dataSocket.getOutputStream(), true);
		// Input stream. Enables to get messages from the server.
		dataIN    = new BufferedReader(new InputStreamReader(dataSocket.getInputStream()));
		// We want the user to input something. We take it from the std.in. Send this to server.
		dataSTDIN = new BufferedReader(new InputStreamReader(System.in));

	}
	
	// parse the port required to connect passively to the FTP server
	private static int parsePort(String message) {
		int openingParenthesis = message.indexOf('(');
		int closingParenthesis = message.indexOf(')');

		String addp = message.substring(openingParenthesis+1, closingParenthesis);

		StringTokenizer st = new StringTokenizer(addp, ",");
		String hostName = st.nextToken() + "." + st.nextToken() + "." + 
				st.nextToken() + "." + st.nextToken();

		int p1 = Integer.parseInt(st.nextToken());
		int p2 = Integer.parseInt(st.nextToken());
		int port = (p1*256) + p2;
		return port;
	}

	// parse the PASV address provided by the server
	private static String parseAddress(String message) {

		int openingParenthesis = message.indexOf('(');
		int closingParenthesis = message.indexOf(')');

		String addp = message.substring(openingParenthesis+1, closingParenthesis);

		StringTokenizer st = new StringTokenizer(addp, ",");
		String hostName = st.nextToken() + "." + st.nextToken() + "." + 
				st.nextToken() + "." + st.nextToken();
		return hostName;

	}

	/**
	 *  Sends an FTP command with a parameter to the server. 
	 * @param command An FTP command, e.g. USER or PASS
	 * @param parameter The parameter for the command, e.g. "anonymous"
	 */
	private static void sendCommandWithParameter(String command, String parameter) {
		command = command.toUpperCase();
		sendCommand(command.toUpperCase() + " " + parameter);
	}

	// Sends command to server with line termination characters expected by Microsoft FTP ("\r\n")
	private static void sendCommand(String command) {
		String lineTermination = "\r"; // doesn't need to be "\r\n" because println appends the "\n"
		outp.println(command+lineTermination);
		
		// if there was an IO error while sending the command to the server, display error message.
		if (outp.checkError()){
			printError("825 Control connection I/O error, closing control connection.");
			try {
				commandSocket.close();
			} catch (IOException e) {
				printError("899 Processing error. Unable to close control connection socket.");
			}
			controlConnectionOpen=false;
		} else {
		printSentMsg(command); // echo sent command to console (beginning with "--> ")
		}
	}

	private static void get(String pathname) throws IOException {
		int fileData;
		String reply;

		sendCommand("TYPE I"); // set data transfer type to binary. 
		reply = getServerMessage(inp); // get reply from server
		printReceivedMsg(reply); // print reply
		sendCommand("PASV"); // send PASV command
		reply = getServerMessage(inp); // get IP address and port number to connect to for data transfer
		printReceivedMsg(reply);
		if (checkReturnCode(reply, "227")){

			// if PASV reply received, then parse out IP address and port number.
			String hostName = parseAddress(reply);
			int    portNumb = parsePort(reply);

			// create new socket for transferring file
			Socket dataSocket = new Socket(hostName, portNumb); 

			// request file from FTP server. pathname = e.g. "cats.jpg"
			sendCommandWithParameter("RETR", pathname);	

			//print any message back from the server
			reply = getServerMessage(inp);
			printReceivedMsg(reply);

			//file arrives as an input stream on the socket created moments ago
			BufferedInputStream incomingData = new BufferedInputStream(dataSocket.getInputStream());

			//write the bytes of the arriving stream to a file of the same name
			BufferedOutputStream savedData = new BufferedOutputStream(new FileOutputStream(pathname));
			while ((fileData = incomingData.read()) != -1){
				try {
					savedData.write(fileData);
				} catch (IOException e) {
					printError("810 Access to local file " + pathname +" denied");
				}
			}

			//close socket and buffers
			dataSocket.close();
			incomingData.close();
			savedData.close();

			//print any message back from server (e.g., file successfully transferred)
			reply = getServerMessage(inp);
			printReceivedMsg(reply);


		} else {
			System.out.println ("Error getting file");}
	}

	private static void put(String pathname) throws IOException {
		String reply;

		sendCommand("TYPE I"); // set data transfer type to binary. 
		reply = getServerMessage(inp); // get reply from server
		printReceivedMsg(reply); // print reply
		sendCommand("PASV"); // send PASV command
		reply = getServerMessage(inp); // get IP address and port number to connect to for data transfer
		printReceivedMsg(reply);
		if (checkReturnCode(reply, "227")){
			
			// if PASV reply received, then parse out IP address and port number.
			String hostName = parseAddress(reply);
			int    portNumb = parsePort(reply);

			// create new socket for transferring file
			Socket dataSocket = new Socket(hostName, portNumb); 
			// Output stream to write to the server. It gives you a stream to output on.
			PrintStream outgoing = new PrintStream(dataSocket.getOutputStream(), true);
			// Input stream. Enables to get messages from the server.
			BufferedReader  dest = new BufferedReader(new InputStreamReader(dataSocket.getInputStream()));

			sendCommandWithParameter("STOR", pathname);	

			//print any message back from the server
			reply = getServerMessage(inp);
			printReceivedMsg(reply); 
			
			//*****Create the file object out of the pathname*****
			File myFile = null;
			try {
			myFile = new File(pathname);
			} catch (NullPointerException e){
				printError("810 Access to local file " + pathname + " denied");
			}
			// allocate a byte array to store the contents of the file. 
			byte [] mybytearray  = new byte [(int)myFile.length()];
			// FileInputStream reads the bytes that are in myFile
			FileInputStream fis = new FileInputStream(myFile);
			BufferedInputStream bis = new BufferedInputStream(fis);
			// read the bytes from the buffer into the preallocated byte array
			bis.read(mybytearray,0,mybytearray.length);
			
			System.out.println("Sending " + pathname + "(" + mybytearray.length + " bytes)");
			outgoing.write(mybytearray,0,mybytearray.length);
			outgoing.flush();
	        System.out.println("Done.");
			
	        if (bis != null) bis.close();
	        if (outgoing != null) outgoing.close();
	        if (dataSocket!=null) dataSocket.close();
	        
			//print any message back from server (e.g., file successfully transferred)
			reply = getServerMessage(inp);
			printReceivedMsg(reply);
	}
}

	private static void close() throws IOException {
		if ((commandSocket != null) && commandSocket.isConnected()){
		sendCommand("QUIT");
		printReceivedMsg(getServerMessage(inp));
		closeActiveConnections();
		}
	}

	/*
	 * Closes any established connections and quits the program.
	 */
	private static void quit() throws IOException {
		close();
		System.exit(0);
	}

}
