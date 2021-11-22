import java.io.*;
import java.util.*;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicInteger;

public class ServerThread implements Runnable
{
    private String clientID;
    private Socket clientSocket;
    private Thread t;

    private DataInputStream dataInputStream;
    private DataOutputStream dataOutputStream;
    private ObjectOutputStream oos;
    private Scanner sc;

    private String serverDirectory = "E:\\Others\\Practice_on_Networking\\File_Server\\Server\\src\\files\\";
    private HashMap<String, Boolean> client_list;
    private HashMap<String, HashMap<String, Integer>> all_file_list;
    private HashMap<String, Integer> own_file_list;
    private int file_count;                                          // this file_id will be unique

    private AtomicInteger chunks_stored;
    private int MAX_BUFFER_SIZE;
    private int MAX_CHUNK_SIZE;
    private int MIN_CHUNK_SIZE;

    ServerThread(Socket clientSocket, HashMap<String, Boolean> c_list, HashMap<String, HashMap<String, Integer>> f_list, AtomicInteger chunks_stored, int max_buffer_size,
                 int max_chunk_size, int min_chunk_size)
    {
        try
        {
            this.clientSocket = clientSocket;
            t = new Thread(this);

            this.dataOutputStream = new DataOutputStream(clientSocket.getOutputStream());
            this.dataInputStream = new DataInputStream(clientSocket.getInputStream());
            this.oos = new ObjectOutputStream(clientSocket.getOutputStream());

            this.client_list = c_list;
            this.all_file_list = f_list;
            this.chunks_stored = chunks_stored;

            this.MAX_BUFFER_SIZE = max_buffer_size;
            this.MAX_CHUNK_SIZE = max_chunk_size;
            this.MIN_CHUNK_SIZE = min_chunk_size;

            file_count = 0;

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

                    if(privacy_choice == 1 || privacy_choice == 2)              // 1 -> public,  2 -> private
                    {
                        String clientFileName = dataInputStream.readUTF();      // read file name
                        int clientFilesize = dataInputStream.readInt();         // read file size

                        if(receiveApproval(clientFilesize))                     // if file is allowed to be sent
                        {
                            dataOutputStream.writeUTF("File Sending Allowed");      // sending the approval message

                            int chunk_size = (int)Math.floor(Math.random()*(MAX_CHUNK_SIZE-MIN_CHUNK_SIZE+1)+MIN_CHUNK_SIZE);               // generating a chunk size
                            String file_ID = clientID + "_" + privacy_choice + "_" + file_count + "." + getFileExtenstion(clientFileName);  // e.g 1705108_1_0

                            dataOutputStream.writeInt(chunk_size);                  // sending the chunk_size
                            dataOutputStream.writeUTF(file_ID);                     // sending the file_ID

                            String file_to_be_saved = getDirectory(clientID, privacy_choice) + file_ID;     // e.g. "private/1705108_1_0.txt"

                            receiveFile(file_to_be_saved, clientFilesize, chunk_size);              // receiving the file
                            file_count += 1;                                        // file_count increases

                            own_file_list.put(file_ID, privacy_choice);             // adding the file to own files list
                            all_file_list.put(clientID, own_file_list);             // updating the list in all files list
                        }
                        else
                        {
                            dataOutputStream.writeUTF("Buffer Overflow, Please Wait");
                        }
                    }
                    else                                                            // invalid privacy choice
                        continue;
                }

                // --- client chooses to lookup users --- //
                else if(choice == 2)
                {
                    System.out.println(client_list);
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

                // --- client chooses to see all the public files --- //
                else if(choice == 4)
                {
                    oos.writeObject(all_file_list);                     // sending the all_file_list to the client
                }

                // --- client chooses to logout --- //
                else if(choice == 5)
                {
                    // closing the socket
                    client_logout();
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
            clientID = dataInputStream.readUTF();                                   // reading the clientID from client

            if(!client_list.containsKey(clientID))
            {
                synchronized (client_list)
                {
                    client_list.put(clientID, true);                                // making the client active
                }
                // --- setting up the list of own files --- ///
                if(all_file_list.get(clientID) != null)
                    this.own_file_list = all_file_list.get(clientID);               // getting the own_file_list
                else
                    this.own_file_list = new HashMap<String, Integer>();            // initializing the own_file_list

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
                    synchronized (client_list)
                    {
                        client_list.put(clientID, true);                                // making the client active
                    }

                    // --- setting up the list of own files --- ///
                    if(all_file_list.get(clientID) != null)
                        this.own_file_list = all_file_list.get(clientID);               // getting the own_file_list
                    else
                        this.own_file_list = new HashMap<String, Integer>();            // initializing the own_file_list

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

    private void client_logout()
    {
        try
        {
            dataInputStream.close();
            dataOutputStream.close();
            clientSocket.close();

            synchronized (client_list)
            {
                client_list.put(clientID, false);
            }

            System.out.println("Client " + clientID + " Logged Out");
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
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

    private boolean receiveApproval(int file_size)
    {
        return !(chunks_stored.get() + file_size > MAX_BUFFER_SIZE);
    }


    private void receiveFile(String filePath, int clientFileSize, int chunk_size) throws Exception
    {
        int occupied_buffer_bytes = 0;                                          // buffer is empty
        FileOutputStream fileOutputStream = new FileOutputStream(filePath);

        byte[] buffer = new byte[chunk_size];                                   // buffer with size of chunk_size
        int bytes_left = clientFileSize;                                        // whole file is left to be sent

        chunks_stored.addAndGet(chunk_size);

        while (bytes_left > 0 && occupied_buffer_bytes != -1)
        {
//            System.out.println("Available bytes : " + dataInputStream.available());

            occupied_buffer_bytes = dataInputStream.read(buffer, 0, Math.min(buffer.length, bytes_left));      // reading from input stream and putting it into buffer

            fileOutputStream.write(buffer,0, occupied_buffer_bytes);                                            // writing to file

//            System.out.println("Client: " + clientID + "->" + occupied_buffer_bytes + " bytes received");

            bytes_left -= occupied_buffer_bytes;
//            System.out.println("Client: " + clientID + "->" + bytes_left + " bytes left");
        }

        chunks_stored.addAndGet(-chunk_size);

        System.out.println("File Received Successfully");
        System.out.println("File Saved In: " + filePath);
        fileOutputStream.close();
    }
}