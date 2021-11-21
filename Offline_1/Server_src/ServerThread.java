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
    private ObjectOutputStream oos = null;
    private Scanner sc = null;
    private String serverDirectory = "E:\\Others\\Practice_on_Networking\\File_Server\\Server\\src\\files\\";
    private static HashMap<String, Boolean> client_list = null;
    private static HashMap<String, String> file_list = null;
    private int file_id;                                          // this file_id will be unique

    ServerThread(Socket clientSocket, HashMap<String, Boolean> c_list, HashMap<String, String> f_list)
    {
        try
        {
            this.clientSocket = clientSocket;
            t = new Thread(this);

            this.dataOutputStream = new DataOutputStream(clientSocket.getOutputStream());
            this.dataInputStream = new DataInputStream(clientSocket.getInputStream());
            this.oos = new ObjectOutputStream(clientSocket.getOutputStream());
            client_list = c_list;
            file_list = f_list;
            file_id = 0;

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

                // --- client chooses to send file --- //
                if(choice == 1)
                {
                    int privacy_choice = dataInputStream.readInt();             // getting the privacy choice from client

                    if(privacy_choice == 1 || privacy_choice == 2)
                    {
                        String clientFileName = dataInputStream.readUTF();      // read file name
                        int clientFilesize = dataInputStream.readInt();         // read file size

                        String file_to_be_saved = getDirectory(clientID, privacy_choice) + clientID + "_" + file_id + "." + getFileExtenstion(clientFileName);    // e.g. "private/1705108_1.txt"

                        receiveFile(file_to_be_saved, clientFilesize);
                        file_id += 1;                                           // file_id increases
                    }
                    else                                                        // invalid privacy choice
                        continue;
                }

                // --- client chooses to lookup users --- //
                else if(choice == 2)
                {
                    oos.writeObject(client_list);
                }

                // --- client chooses to see his/her uploaded contents --- //
                else if(choice == 3)
                {
                    File public_dir = new File(getDirectory(clientID, 1));
                    File private_dir = new File(getDirectory(clientID, 2));

                    String[] public_files = public_dir.list();          // listing all the public filenames as String[]
                    String[] private_files = private_dir.list();        // listing all the private filenames as String[]

                    oos.writeObject(public_files);                      // sending the public_files list to client
                    oos.writeObject(private_files);                     // sending the private_files list to client
                }

                // --- client chooses to logout --- //
                else if(choice == 4)
                {
                    // closing the socket
                    try
                    {
                        dataInputStream.close();
                        dataOutputStream.close();
                        clientSocket.close();

                        client_list.put(clientID, false);

                        System.out.println("Client " + clientID + " Logged Out");
                    }
                    catch (Exception ex)
                    {
                        ex.printStackTrace();
                    }
                    break;
                }

                // --- no action --- //
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

            if(!client_list.containsKey(clientID))
            {
                client_list.put(clientID, true);

                System.out.println(clientID + " logged in successfully");
                return makeDirectory(clientID);
            }
            else
            {
                if(client_list.get(clientID))
                {
                    System.out.println("Client " + clientID + " already logged in");
                    return false;
                }
                else
                {
                    client_list.put(clientID, true);
                    System.out.println(clientID + " logged in successfully");
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
            if(file.mkdir())                                                // if main directory is created
            {
                String private_directory = directory + "\\private";
                String public_directory = directory + "\\public";

                if((new File(private_directory).mkdir()) && (new File(public_directory).mkdir()))       // if both public and private directories are created
                {
                    System.out.println("Directories Created Successfully");
                    return true;
                }
                else
                    return false;
            }
            else                                                            // main directory cannot be created
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


    private String getDirectory(String clientID, int choice)
    {
        if(choice == 1)
            return (serverDirectory + clientID + "\\public\\");
        else
            return (serverDirectory + clientID + "\\private\\");
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

    private void receiveFile(String fileName, int clientFileSize) throws Exception
    {                 // TODO: remove clientID from arguments

        System.out.println("Receiving file: " + fileName);

        int chunk_size = 4 * 1024;                                              // 4 KB
        int occupied_buffer_bytes = 0;                                          // buffer is empty
        FileOutputStream fileOutputStream = new FileOutputStream(fileName);

        System.out.println("Incoming File Size:" + clientFileSize);

        byte[] buffer = new byte[chunk_size];                                   // buffer with size of chunk_size
        int bytes_left = clientFileSize;                                        // whole file is left to be sent

        while (bytes_left > 0 && occupied_buffer_bytes != -1)
        {
            System.out.println("Available bytes : " + dataInputStream.available());

            occupied_buffer_bytes = dataInputStream.read(buffer, 0, Math.min(buffer.length, bytes_left));      // reading from input stream and putting it into buffer
            fileOutputStream.write(buffer,0, occupied_buffer_bytes);                                            // writing to file

            System.out.println("Client: " + clientID + "->" + occupied_buffer_bytes + " bytes received");

            bytes_left -= occupied_buffer_bytes;
            System.out.println("Client: " + clientID + "->" + bytes_left + " bytes left");
        }
        System.out.println("File Received Successfully");
        fileOutputStream.close();
    }
}