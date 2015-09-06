import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;


public class ConnectionThread implements Runnable {
	
	private boolean dataSocket;
	private String hostname;
	private int portNumber;
	
	ConnectionThread(String hostname, int portNumber, boolean data){
		this.dataSocket = data;
		this.hostname=hostname;
		this.portNumber=portNumber;		
	}

	public void run() {
		try {
			if(!dataSocket)
				CSftp.commandSocket = new Socket(InetAddress.getByName(hostname), portNumber);
			else{		
				CSftp.dataSocket = new Socket(InetAddress.getByName(hostname), portNumber);
				}
		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
