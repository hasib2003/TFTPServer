//package main;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.Arrays;


public class TFTPServer {
	public static final int TFTPPORT = 4970;

	public static final byte  PATTERN = (byte) 0xea;
	public static final int BUFSIZE = 516;
	public static final String READDIR = "/home/hasib/testDir/read/"; //custom address at your PC
	public static final String WRITEDIR = "/home/hasib/testDir/write/"; //custom address at your PC
	// OP codes
	public static final int OP_RRQ = 1;
	public static final int OP_WRQ = 2;
	public static final int OP_DAT = 3;
	public static final int OP_ACK = 4;
	public static final int OP_ERR = 5;

	public static void main(String[] args) {
		if (args.length > 0) {
			System.err.printf("usage: java \n", TFTPServer.class.getCanonicalName());
			System.exit(1);
		}
		//Starting the server
		try {
			TFTPServer server = new TFTPServer();
			server.start();
		} catch (SocketException e) {
			e.printStackTrace();
		}
	}

	private void start() throws SocketException {
		byte[] buf = new byte[BUFSIZE];

		// Create socket
		DatagramSocket socket = new DatagramSocket(null);

		// Create local bind point
		// Create a address with TFTP port and wildcard address ; localhost i suppose

		SocketAddress localBindPoint = new InetSocketAddress(TFTPPORT);
		socket.bind(localBindPoint);

		System.out.printf("Listening at port for new requests " + TFTPPORT);

		// Loop to handle client requests
		while (true) {

			try {
				final InetSocketAddress clientAddress = receiveFrom(socket, buf);

				// If clientAddress is null, an error occurred in receiveFrom()
				if (clientAddress == null)
					continue;

				final StringBuffer requestedFile = new StringBuffer();

				final int reqtype = ParseRQ(buf, requestedFile);


				new Thread() {
					public void run() {
						try {
							DatagramSocket sendSocket = new DatagramSocket(null);

							// Connect to client
							sendSocket.connect(clientAddress);

							System.out.printf(" request for from using port ",
									(reqtype == OP_RRQ) ? "Read" : "Write",
									clientAddress.getHostName(), clientAddress.getPort());

							// Read request
							if (reqtype == OP_RRQ) {
								requestedFile.insert(0, READDIR);
								HandleRQ(sendSocket, requestedFile.toString(), OP_RRQ);
							}
							// Write request
							else if (reqtype == OP_WRQ){
								requestedFile.insert(0, WRITEDIR);
								HandleRQ(sendSocket, requestedFile.toString(), OP_WRQ);
							}
							else
							{
								send_ERR(sendSocket,"BAD OPCODE :: Unknown Opcode",4);
							}
							sendSocket.close();
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
				}.start();
			} catch (Exception ex) {
				System.out.printf("ERROR HAS OCCURED :: ", ex);
			}
		}

	}

	/**
	 * Reads the first block of data, i.e., the request for an action (read or write).
	 *
	 * @param socket (socket to read from)
	 * @param buf    (where to store the read data)
	 * @return socketAddress (the socket address of the client)
	 */
	private InetSocketAddress receiveFrom(DatagramSocket socket, byte[] buf) throws Exception {
		// Create datagram packet
		DatagramPacket receivePacket = new DatagramPacket(buf, buf.length);
		// Receive packet
		try {
			socket.receive(receivePacket);
		} catch (SocketException e) {
			e.printStackTrace();
		}
		// Get client address and port from the packet
		InetSocketAddress clientInfo = new InetSocketAddress(receivePacket.getAddress(), receivePacket.getPort());
		System.out.println();
		System.out.println("SOURCE :: " + clientInfo.getAddress() + " || " + clientInfo.getPort());


		return clientInfo;
	}

	/**
	 * Parses the request in buf to retrieve the type of request and requestedFile
	 *
	 * @param buf           (received request)
	 * @param requestedFile (name of file to read/write)
	 * @return opcode (request type: RRQ or WRQ)
	 */
	private int ParseRQ(byte[] buf, StringBuffer requestedFile) throws Exception {
		// See "TFTP Formats" in TFTP specification for the RRQ/WRQ request contents

		// See "TFTP Formats" in TFTP specification for the RRQ/WRQ request contents.


		// searching for the zero byte
		int index = -1;
		for (int i = 1; i < buf.length; i++) {
			if (buf[i] == 0) {
				index = i;
				break;
			}
		}
		ByteBuffer wrap = ByteBuffer.wrap(buf);
		short opcode = wrap.getShort();
		//reading the file name starting from 2 untill the zero byte occur
		String fileName = new String(buf, 2, index - 2);


		requestedFile.append(fileName);

		System.out.println("OPCODE :: " + opcode);
		System.out.println("FILE :: " + fileName);

		return opcode;
	}

	/**
	 * Handles RRQ and WRQ requests
	 *
	 * @param sendSocket    (socket used to send/receive packets)
	 * @param requestedFile (name of file to read/write)
	 * @param opcode        (RRQ or WRQ)
	 */
	private void HandleRQ(DatagramSocket sendSocket, String requestedFile, int opcode) throws IOException {
		if (opcode == OP_RRQ) {

			int block = 0;
			boolean result = send_DATA_receive_ACK(sendSocket, requestedFile, ++block);

		} else if (opcode == OP_WRQ) {


			if(Check_If_Permitted(sendSocket,requestedFile))

			{
				boolean result = receive_DATA_send_ACK(sendSocket, requestedFile);
			}
			else
			{
				System.out.println("Not permitted");
			}

		} else {
			System.err.println("Invalid request. Sending an error packet.");
			// See "TFTP Formats" in TFTP specification for the ERROR packet contents
			send_ERR(sendSocket, "Error packet Sent", 0);
			return;
		}
	}

	/**
	 * To be implemented
	 */
	private boolean send_DATA_receive_ACK(DatagramSocket socket, String file, int block) {
		boolean checkSize = false;
		try {
			// split file name into an array of strings using the null character.


			String[] name = file.split("\n");
			File fileName = new File(name[0]);
			System.out.println("\n" + fileName);

			//Checking if the file exist, if not send error 1 to client
			if (!fileName.exists()) {
				send_ERR(socket, "File not found", 1);
				System.out.println("FILE DOES NOT EXISTS ");

			} else if (!fileName.canRead()) {
				send_ERR(socket, "Not Authorized to Read the File", 2);
			} else {

				System.out.println("FILE EXISTS ");
				long totalBytes = fileName.length();
				int maxSize = BUFSIZE - 4;
				System.out.println("TOTAL BYTES :: " + totalBytes);

				// file exists and has read/write permissions. the FileInputStream object is created to read the file.

				FileInputStream in = new FileInputStream(fileName);
				byte[] buffer = new byte[BUFSIZE - 4];

				int bytesRead = 0;

				int failed = 0;


				while (maxSize <= totalBytes) {

					if (failed == 0 || failed >= 2) {

						if(maxSize <= totalBytes) {
							bytesRead += in.read(buffer);

						}
						else
						{
							byte[] lastBufer = new byte[(int) totalBytes];
							bytesRead += in.read(lastBufer);

						}
						totalBytes -= maxSize;
						System.out.println("TOTAL BYTES :: " + totalBytes);
						System.out.println("READ BYTES  :: " + bytesRead);

					} else {
						totalBytes -= maxSize;
						System.out.println("Retransmit Attempt :: " + failed);
					}

					// DATA packet contains Opcode, Block # and data
					ByteBuffer data = ByteBuffer.allocate(BUFSIZE);
					data.putShort((short) OP_DAT);
					data.putShort((short) block);
					data.put(buffer, 0, maxSize);


					try {
						// send packet
						DatagramPacket packet = new DatagramPacket(data.array(), BUFSIZE);
						socket.send(packet);

						// receive acknowledgement
						ByteBuffer ack = ByteBuffer.allocate(OP_ACK);
						DatagramPacket ackPacket = new DatagramPacket(ack.array(), OP_ACK);
						socket.setSoTimeout(3000);
						socket.receive(ackPacket);

						ByteBuffer ackBuffer = ByteBuffer.wrap(ackPacket.getData());
						short opcode = ackBuffer.getShort();
						short ackBlock = ackBuffer.getShort();


						System.out.println(" --- RECIEVED ACK PACKET --- ");
						System.out.println("OPCODE :: " + opcode);
						System.out.println("BLOCK  :: " + ackBlock);

						if (opcode == OP_ERR) {
							send_ERR(socket, "Error packet sent", 0);
							break;
						}

						if (ackBlock != block) {
							throw new IOException("Unexpected ACK block number");
						}

						block++;
						failed = 0;

						if (bytesRead < buffer.length) {
							checkSize = true;
						}

						if (checkSize) {
							socket.close();
							break;
						}

					} catch (SocketTimeoutException e) {
						System.out.println("Socket timeout, re-transmitting the previous packet");
						failed++;
						totalBytes += maxSize;

					}
				}

				// reading the last chuck left to be sent


				while (failed == 0 || failed >= 2) {
					System.out.println("TOTAL BYTES :: " + totalBytes);
					byte[] lastBufer = new byte[(int) totalBytes];

					if(failed == 0 || failed >=2) {
						bytesRead += in.read(lastBufer);
					}

					// DATA packet contains Opcode, Block # and data

					ByteBuffer data = ByteBuffer.allocate((int) totalBytes + 4);
					data.putShort((short) OP_DAT);
					data.putShort((short) block);
					data.put(lastBufer, 0, (int) totalBytes);


					System.out.println(" --- LAST PACKET --- ");
					System.out.println("SIZE :: " + (totalBytes + 4));


					try {
						// send packet
						DatagramPacket packet = new DatagramPacket(data.array(), (int) totalBytes + 4);
						socket.send(packet);

						// receive acknowledgement
						ByteBuffer ack = ByteBuffer.allocate(OP_ACK);
						DatagramPacket ackPacket = new DatagramPacket(ack.array(), OP_ACK);
						socket.setSoTimeout(3000);
						socket.receive(ackPacket);

						ByteBuffer ackBuffer = ByteBuffer.wrap(ackPacket.getData());
						short opcode = ackBuffer.getShort();
						short ackBlock = ackBuffer.getShort();

						System.out.println(" --- RECIEVED ACK PACKET --- ");
						System.out.println("OPCODE :: " + opcode);
						System.out.println("BLOCK  :: " + ackBlock);

						failed = 0;
						socket.close();

						if (opcode == OP_ERR) {
							send_ERR(socket, "Error packet sent", 0);
						}

						if (ackBlock != block) {
							throw new IOException("Unexpected ACK block number");
						}


					} catch (SocketTimeoutException e) {
						System.out.println("Socket timeout, re-transmitting the previous packet");
						failed ++;

					}
				}


				}
			} catch(IOException e){
				e.printStackTrace();
			}



		return true;

	}

	private boolean receive_DATA_send_ACK(DatagramSocket senderSocket,String newFile) throws IOException {


			// extracting the fileName
			String[] name = newFile.split("\n");
			File fileName = new File(name[0]);


					// new file has been created and if it was not already present
					fileName.createNewFile();

					if(fileName.canWrite()) {

						byte[] rawBuffer = new byte[BUFSIZE];

						DatagramPacket recievedPacket = new DatagramPacket(rawBuffer, BUFSIZE);
						senderSocket.setSoTimeout(3000);
						int dataLength = 516;

						while (dataLength == 516) {

							// receiving the data sent by socket into datagram packet
							senderSocket.receive(recievedPacket);

							dataLength = recievedPacket.getLength();

							ByteBuffer targetBuffer = ByteBuffer.wrap(recievedPacket.getData());

							short opcode = targetBuffer.getShort();
							short ackBlock = targetBuffer.getShort();

							System.out.println(" --- Received data with --- ");
							System.out.println("OPCODE :: " + opcode);
							System.out.println("BLOCK  :: " + ackBlock);
							System.out.println("SIZE   :: " + dataLength);

							// finally we need to write the data into the file
							// opening file in the append mode to support data more than 512 bytes
							FileOutputStream out = new FileOutputStream(fileName, true);
							out.write(targetBuffer.array(), 4, dataLength - 4);
							send_ACK(senderSocket, ackBlock);
						}
					}
			 	else
					{
						System.out.println("FATAL :: Not Authorized to Write File");
						send_ERR(senderSocket,"Not Authorized to Write File", 2);
					}



			return true;

	}


	private void send_ERR(DatagramSocket socket, String error, int errorNumber) throws IOException {
		ByteBuffer errBuf = ByteBuffer.allocate(error.length() + OP_ERR);
		errBuf.putShort((short) OP_ERR);
		errBuf.putShort((short) errorNumber);
		errBuf.put(error.getBytes());

		DatagramPacket sendError = new DatagramPacket(errBuf.array(), errBuf.array().length);

		socket.send(sendError);
	}

	private void send_ACK(DatagramSocket socket, int block) throws SocketException {
		System.out.println();
		System.out.println("DESTINATION :: " + socket.getInetAddress() + " || " + socket.getPort());


		ByteBuffer ackBuffer = ByteBuffer.allocate(OP_ACK);

		ackBuffer.putShort((short) OP_ACK);
		ackBuffer.putShort((short) block);

		DatagramPacket packet = new DatagramPacket(ackBuffer.array(), OP_ACK);
		try {
			socket.send(packet);
			System.out.println("ACK SENT");
		} catch (Exception ex) {
			ex.printStackTrace();
		}

	}

	private int Byte_Buffer_Length (ByteBuffer buf)
	{

		byte[] arr = buf.array();
		int hit = 0;
		int i= 0;
		for (i=0; i< arr.length;i++)
		{
			if (arr[i] == PATTERN)
			{
				hit++;
			}
			if(hit == 2)
			{
				break;
			}

		}

		System.out.println("i " + i);
		return i-5;

	}

private boolean Check_If_Permitted(DatagramSocket socket, String newFile) throws IOException {

	// permitting the write request
	String[] name = newFile.split("\n");
	File fileName = new File(name[0]);

	if(!fileName.exists()) {
			try {
				send_ACK(socket, 0);
				return true;

			}
			catch (SocketException ex)
			{
				ex.printStackTrace();
				return false;
			}

		}
		else {

			System.out.println("File Already Present");
			send_ERR(socket,"File Already Present",6);
			return false;
		}


}
}







