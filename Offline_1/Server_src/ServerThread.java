import java.io.*;
import java.util.*;
import java.net.Socket;

public class ServerThread implements Runnable
{
    private String clientID;
    private Socket clientSocket;
    private Thread t;
    private DataInputStream dataInputStream = null;
    private DataOutputStream dataOutputStream = null;
    private Scanner sc = null;
    private String serverDirectory = "E:\\Others\\Practice_on_Networking\\File_Server\\Server\\src\\files\\";
    private ArrayList<String> client_list = null;
    private ArrayList<String> active_client_list = null;

    ServerThread(Socket clientSocket, ArrayList<String> c_list, ArrayList<String> ac_list)
    {
        try
        {
            this.clientSocket = clientSocket;
            t = new Thread(this);

            this.dataOutputStream = new DataOutputStream(clientSocket.getOutputStream());
            this.dataInputStream = new DataInputStream(clientSocket.getInputStream());
            this.client_list = c_list;
            this.active_client_list = ac_list;

            t.start();
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
        }
    }


    @Override
    public void run()
    {
        try
        {
            if(!client_login())                                         // client login failed
            {
                dataOutputStream.writeUTF("Login Failed");          // send login failed message
                return;
            }
            else
                dataOutputStream.writeUTF("Login Successful");      // send login successful message

            int choice = 0;

            while(true)
            {
                choice = dataInputStream.readInt();                             // reading client choice

                if(choice == 1)                                                 // client chooses to send file
                {
                    receiveFile(getDirectory(clientID) + "1.txt", clientID);
                    receiveFile(getDirectory(clientID) + "2.txt", clientID);
                    receiveFile(getDirectory(clientID) + "3.png", clientID);
                    receiveFile(getDirectory(clientID) + "4.png", clientID);
                    receiveFile(getDirectory(clientID) + "5.png", clientID);
                    receiveFile(getDirectory(clientID) + "6.JPG", clientID);
                    receiveFile(getDirectory(clientID) + "7.JPG", clientID);
                }
                else if(choice == 2)                                            // client chooses logout
                {
                    // closing the socket
                    try {
                        dataInputStream.close();
                        dataOutputStream.close();
                        clientSocket.close();
                        System.out.println("Client" + clientID + " Logged Out");
                    }
                    catch (Exception ex) {
                        ex.printStackTrace();
                    }
                    break;
                }
                else
                {
                    System.out.println("Undefined Action");
                }
            }

        }
        catch (Exception ex)
        {
            ex.printStackTrace();
        }
    }


    private boolean client_login()
    {
        try {
            clientID = dataInputStream.readUTF();               // reading the clientID from client

            if(!client_list.contains(clientID))                 // if client has never logged in previously
            {
                client_list.add(clientID);                      // client is now in all_clients_list
                active_client_list.add(clientID);               // client is now active

                System.out.println(clientID + " logged in successfully");
                return makeDirectory(clientID);
            }
            else                                                // client has logged in previously
            {
                if(active_client_list.contains(clientID))       // if client is active from another account
                {
                    System.out.println("Client Already Logged In");
                    return false;
                }
                else                                            // if client not active from other source
                {
                    System.out.println(clientID + " logged in");
                    return makeDirectory(clientID);
                }
            }
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
            return false;
        }
    }


    private boolean makeDirectory(String clientID)
    {
        String directory = serverDirectory + clientID;                      // e.g "E:/Server/1705108/"
        File file = new File(directory);

        if (!file.exists())                                                 // if directory does not exist
        {
            boolean bool = file.mkdir();
            if(bool)
            {
                System.out.println("New Directory created successfully");
                return true;
            }
            else
            {
                System.out.println("Sorry couldn’t create specified directory");
                return false;
            }
        }
        else                                                                // directory exists previously
        {
            System.out.println("Directory Exists");
            return true;
        }
    }


    private String getDirectory(String clientID)
    {
        return (serverDirectory + clientID + "\\");
    }


    private String getFileExtenstion(String fileName)
    {
        int index = fileName.lastIndexOf('.');
        if(index > 0)
        {
            String extension = fileName.substring(index + 1);
            return extension;
        }
        else
        {
            System.out.println("File Extention Not Specified");
            return null;
        }
    }

    private void receiveFile(String fileName, String clientID) throws Exception{    // TODO: remove clientID from arguments

        System.out.println("Receiving file: " + fileName);
        System.out.println("File extension: " + getFileExtenstion(fileName));

        int chunk_size = 4 * 1024;                                              // 4 KB
        int occupied_buffer_bytes = 0;                                          // buffer is empty
        FileOutputStream fileOutputStream = new FileOutputStream(fileName);

        int clientFilesize = dataInputStream.readInt();                         // read file size
        System.out.println("Incoming File Size:" + clientFilesize);

        byte[] buffer = new byte[chunk_size];                                   // buffer with size of chunk_size
        int bytes_left = clientFilesize;                                        // whole file is left to be sent

        while (bytes_left > 0 && occupied_buffer_bytes != -1)
        {
            System.out.println("Available bytes : " + dataInputStream.available());

            occupied_buffer_bytes = dataInputStream.read(buffer, 0, (int)Math.min(buffer.length, bytes_left));      // reading from input stream and putting it into buffer
            fileOutputStream.write(buffer,0, occupied_buffer_bytes);                                                // writing to file

            System.out.println("Client: " + clientID + "->" + occupied_buffer_bytes + " bytes received");

            bytes_left -= occupied_buffer_bytes;
            System.out.println("Client: " + clientID + "->" + bytes_left + " bytes left");
        }
        System.out.println("File Received Successfully");
        fileOutputStream.close();
    }
}