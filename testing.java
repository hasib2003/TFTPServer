//package main;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.File;
import java.net.*;
import java.nio.ByteBuffer;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Arrays;


public class testing
{
    public static final int TFTPPORT = 4970;
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
        if (args.length > 0)
        {
            System.err.printf("usage: java \n", testing.class.getCanonicalName());
            System.exit(1);
        }
        //Starting the server
        try
        {
            testing server= new testing();
            server.start();
        }
        catch (SocketException e)
        {e.printStackTrace();}
    }

    private void start() throws SocketException
    {
        byte[] buf= new byte[BUFSIZE];

        // Create socket
        DatagramSocket socket= new DatagramSocket(null);

        // Create local bind point
        // Create a address with TFTP port and wildcard address ; localhost i suppose

        SocketAddress localBindPoint= new InetSocketAddress(TFTPPORT);
        socket.bind(localBindPoint);

        System.out.printf("Listening at port for new requests " + TFTPPORT);

        // Loop to handle client requests
        while (true)
        {

            try{
                final InetSocketAddress clientAddress = receiveFrom(socket, buf);

                // If clientAddress is null, an error occurred in receiveFrom()
                if (clientAddress == null)
                    continue;

                final StringBuffer requestedFile= new StringBuffer();

                final int reqtype = ParseRQ(buf, requestedFile);



                new Thread()
                {
                    public void run()
                    {
                        try
                        {
                            DatagramSocket sendSocket= new DatagramSocket(null);

                            // Connect to client
                            sendSocket.connect(clientAddress);

                            System.out.printf(" request for from using port " ,
                                    (reqtype == OP_RRQ)?"Read":"Write" ,
                                    clientAddress.getHostName(), clientAddress.getPort());

                            // Read request
                            if (reqtype == OP_RRQ)
                            {
                                requestedFile.insert(0, READDIR);
                                HandleRQ(sendSocket, requestedFile.toString(), OP_RRQ);
                            }
                            // Write request
                            else
                            {
                                requestedFile.insert(0, WRITEDIR);
                                HandleRQ(sendSocket,requestedFile.toString(),OP_WRQ);
                            }
                            sendSocket.close();
                        }
                        catch (IOException e)
                        {e.printStackTrace();}
                    }
                }.start();
            }
            catch (Exception ex)
            {
                System.out.printf("ERROR HAS OCCURED :: ", ex);
            }
        }

    }

    /**
     * Reads the first block of data, i.e., the request for an action (read or write).
     * @param socket (socket to read from)
     * @param buf (where to store the read data)
     * @return socketAddress (the socket address of the client)
     */
    private InetSocketAddress receiveFrom(DatagramSocket socket, byte[] buf) throws Exception
    {
        // Create datagram packet
        DatagramPacket receivePacket = new DatagramPacket(buf, buf.length);
        // Receive packet
        try {
            socket.receive(receivePacket);
        } catch (SocketException e) {
            e.printStackTrace();
        }
        // Get client address and port from the packet
        InetSocketAddress clientInfo= new InetSocketAddress(receivePacket.getAddress(), receivePacket.getPort());
        return clientInfo;
    }

    /**
     * Parses the request in buf to retrieve the type of request and requestedFile
     *
     * @param buf (received request)
     * @param requestedFile (name of file to read/write)
     * @return opcode (request type: RRQ or WRQ)
     */
    private int ParseRQ(byte[] buf, StringBuffer requestedFile) throws  Exception
    {
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
        String fileName = new String(buf, 2, index-2);


        requestedFile.append(fileName);


        return opcode;
    }

    /**
     * Handles RRQ and WRQ requests
     *
     * @param sendSocket (socket used to send/receive packets)
     * @param requestedFile (name of file to read/write)
     * @param opcode (RRQ or WRQ)
     */
    private void HandleRQ(DatagramSocket sendSocket, String requestedFile, int opcode) throws IOException {
        if(opcode == OP_RRQ)
        {

            int block = 0;
            boolean result = send_DATA_receive_ACK(sendSocket, requestedFile,++block);

        }
        else if (opcode == OP_WRQ)
        {
            boolean result = receive_DATA_send_ACK(sendSocket,requestedFile,0);
        }
        else
        {
            System.err.println("Invalid request. Sending an error packet.");
            // See "TFTP Formats" in TFTP specification for the ERROR packet contents
            send_ERR(sendSocket, "Error packet Sent", 0);
            return;
        }
    }

    /**
     To be implemented
     */
    private boolean send_DATA_receive_ACK(DatagramSocket socket, String file, int block)
    {
        boolean checkSize = false;
        try {
            // split file name into an array of strings using the null character.


            String[] name = file.split("\n");
            File fileName = new File(name[0]);

            System.out.println("\n"+ fileName);

            //Checking if the file exist, if not send error 1 to client
            if (!fileName.exists()) {
                send_ERR(socket, "File not found!", 1);
                System.out.println("FILE DOES NOT EXISTS ");

            }
            else{

                System.out.println("FILE EXISTS ");

                // file exists and has read/write permissions. the FileInputStream object is created to read the file.
                FileInputStream in = new FileInputStream(fileName);
                byte[] buffer = new byte[BUFSIZE - 4];
                int bytesRead;

                while ((bytesRead = in.read(buffer)) != -1) {

                    // DATA packet contains Opcode, Block # and data
                    ByteBuffer data = ByteBuffer.allocate(BUFSIZE);
                    data.putShort((short) OP_DAT);
                    data.putShort((short) block);
                    data.put(buffer, 0, bytesRead);

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

                        System.out.println("ACK OPCODE :: " + opcode);
                        System.out.println("ACK BLOCK :: " + ackBlock);

                        if (opcode == OP_ERR) {
                            send_ERR(socket, "Error packet sent", 0);
                            break;
                        }

                        if (ackBlock != block) {
                            throw new IOException("Unexpected ACK block number");
                        }

                        block++;

                        if (bytesRead < buffer.length) {
                            checkSize = true;
                        }

                        if (checkSize) {
                            break;
                        }

                    } catch (SocketTimeoutException e) {
                        System.out.println("Socket timeout, re-transmitting the previous packet");
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }


        return true;

    }

    private boolean receive_DATA_send_ACK(DatagramSocket socket, String fileName, int blockNumber) {

        boolean isFileSizeValid = false;
        try {
            String[] fileNameSplit = fileName.split("\0");
            File file = new File(fileNameSplit[0]);
            if (file.exists()) {
                send_ERR(socket, "File already exists!", 6);
            } else {
                FileOutputStream outputStream = new FileOutputStream(file);

                ByteBuffer ackBuffer = ByteBuffer.allocate(4);
                ackBuffer.putShort((short) 4);
                ackBuffer.putShort((short) blockNumber);

                DatagramPacket ackPacket = new DatagramPacket(
                        ackBuffer.array(),
                        ackBuffer.array().length
                );
                socket.send(ackPacket);

                while (true) {
                    byte[] data = new byte[512];
                    DatagramPacket dataPacket = new DatagramPacket(data, data.length);
                    socket.receive(dataPacket);

                    if (dataPacket.getData().length < 512) {
                        isFileSizeValid = true;
                    }

                    ByteBuffer buffer = ByteBuffer.wrap(data);
                    short opCode = buffer.getShort();

                    if (opCode == 3) {
                        outputStream.write(
                                Arrays.copyOfRange(
                                        dataPacket.getData(),
                                        4,
                                        dataPacket.getLength()
                                )
                        );
                        ByteBuffer sendAck = ByteBuffer.allocate(4);
                        sendAck.putShort((short) 4);
                        sendAck.putShort(buffer.getShort());
                        DatagramPacket ackPacket1 = new DatagramPacket(
                                sendAck.array(),
                                sendAck.array().length
                        );
                        socket.send(ackPacket1);
                    }

                    if (isFileSizeValid) {
                        break;
                    }
                }

                outputStream.flush();
                outputStream.close();
            }
        } catch (IOException e) {
            try {
                send_ERR(socket, "Access violation", 2);
            } catch (IOException e1) {
                e1.printStackTrace();
            }
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

}



