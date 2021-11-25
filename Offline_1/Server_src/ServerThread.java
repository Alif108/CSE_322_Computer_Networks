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

    private HashMap<String, Boolean> client_list;                                   // ( ClientID, online/offline )
    private HashMap<String, HashMap<String, String>> all_file_list;                 // ( ClientID, ( fileID, fileName ) )
    private HashMap<String, String> own_file_list;                                  // ( fileID, fileName )

    private HashMap<Integer, FileRequest> file_request_list;                        // keeps all the file requests -> ( req_id, fileRequest )
    private AtomicInteger request_id;                                               // for generating req_id

    private ArrayList<ClientMessages> message_list;                                 // message_list contains all the messages of clients -> (clientID, [ req_id ])

    private AtomicInteger chunks_stored;
    private int MAX_BUFFER_SIZE;
    private int MAX_CHUNK_SIZE;
    private int MIN_CHUNK_SIZE;


    ServerThread(Socket clientSocket, HashMap<String, Boolean> c_list, HashMap<String, HashMap<String, String>> f_list, AtomicInteger chunks_stored, int max_buffer_size,
                 int max_chunk_size, int min_chunk_size, HashMap<Integer, FileRequest> file_request_list, AtomicInteger req_id,
                 ArrayList<ClientMessages> message_list)
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

            this.file_request_list = file_request_list;
            this.request_id = req_id;

            this.message_list = message_list;

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
            if(!client_login())                                         // if client login failed
            {
                dataOutputStream.writeUTF("Login Failed");          // send "login failed" message
                return;
            }
            else
                dataOutputStream.writeUTF("Login Successful");      // send "login successful" message

            int choice = 0;
            while(true)
            {
                // --- notifying client of new messages --- //
                if(check_messages())
                    dataOutputStream.writeBoolean(true);
                else
                    dataOutputStream.writeBoolean(false);

                // --- reading client choice --- //
                choice = dataInputStream.readInt();

                // --- Client Refresh --- //
                if(choice == 0)
                    continue;

                // --- client chooses to send file --- //
                else if(choice == 1)
                {
                    int privacy_choice = dataInputStream.readInt();                     // getting the privacy choice from client

                    if(privacy_choice == 1 || privacy_choice == 2)                      // 1 -> public,  2 -> private
                    {
                        receive_file_util(privacy_choice);                              // performs all the receive file functionalities
                    }
                    else                                                                // invalid privacy choice
                        continue;
                }

                // --- client chooses to lookup users --- //
                else if(choice == 2)
                {
                    System.out.println(clientID + " : look up users");

                    dataOutputStream.writeInt(client_list.size());

                    client_list.forEach((user, online) ->
                    {
                        try {
                            dataOutputStream.writeUTF(user);
                            dataOutputStream.writeBoolean(online);
                        }
                        catch (IOException ex)
                        {
                            ex.printStackTrace();
                        }
                    });
                }

                // --- client chooses to see his/her uploaded contents --- //
                else if(choice == 3)
                {
//                    send_own_file_list();
                    oos.writeObject(own_file_list);
                }

                // --- client chooses to see all the public files --- //
                else if(choice == 4)
                {
                    oos.writeObject(all_file_list);                     // sending the all_file_list to the client
                }

                // --- client chooses to download file --- //
                else if(choice == 5)
                {
                    String requested_file_id = dataInputStream.readUTF();
                    System.out.println("Client wants to download: " + requested_file_id);

                    String filepath = get_file_directory(requested_file_id);            // getting the file_path

                    if (filepath == null) {
                        System.out.println("File Not Found");
                        dataOutputStream.writeUTF("File Not Found");
                        continue;
                    } else
                        dataOutputStream.writeUTF("File Exists");

                    try {
                        File myFile = new File(filepath);
                        String filename = myFile.getName();                             // getting the filename
                        int fileSize = (int) myFile.length();                           // getting the filesize

                        dataOutputStream.writeUTF(filename);                            // sending the filename
                        dataOutputStream.writeInt(fileSize);                            // sending the file size
                        dataOutputStream.writeInt(MAX_CHUNK_SIZE);                      // sending the chunk_size

                        sendFile(filepath, fileSize);
                    }
                    catch (NullPointerException e)
                    {
                        e.printStackTrace();
                        System.out.println("File Not Found");
                        dataOutputStream.writeUTF("File Not Found");
                        continue;
                    }
                }

                // --- client chooses to request a file --- //
                else if (choice == 6)
                {
                    int req_id = request_id.incrementAndGet();
                    String req_desc = dataInputStream.readUTF();                                        // getting the requst description

                    FileRequest file_request = new FileRequest(req_id, clientID, req_desc);             // (req_id, requester, req_desc)
                    file_request_list.put(req_id, file_request);                                        // adding the request to the file_request_list

                    dataOutputStream.writeUTF("File Request Issued");
                    dataOutputStream.writeUTF("Request ID: " + req_id);

                    System.out.println("File Request Issued By: " + clientID);
                    System.out.println("Request ID: " + req_id);
                }

                // --- client chooses to see file requests --- //
                else if(choice == 7)
                {
                    dataOutputStream.writeInt(file_request_list.size());                                // sending the list size

                    for(Map.Entry<Integer, FileRequest>entry : file_request_list.entrySet())
                    {
                        dataOutputStream.writeUTF(entry.getValue().get_requester());                    // sending requester clientID
                        dataOutputStream.writeInt(entry.getKey());                                      // sending request_id
                        dataOutputStream.writeUTF(entry.getValue().get_req_desc());                     // sending request_description
                    }
                }

                // --- client chooses to upload a file against a request --- //
                else if(choice == 8)
                {
                    int req_id = dataInputStream.readInt();

                    FileRequest fr = file_request_list.get(req_id);
                    if(fr != null)
                    {
                        dataOutputStream.writeBoolean(true);                                    // sending confirmation to client
                        System.out.println(clientID + ": Requested File About To Be Received");
                        String file_id = receive_file_util(1);                          // receiving file from client   // 1 -> public, 2 -> private

                        if(file_id != null) {                                                                   // file sending successful
                            fr.add_file(clientID, file_id);                                                     // ( uploader, fileID )
                            System.out.println(clientID + ": Requested File Uploaded To The Server");
                            dataOutputStream.writeUTF("Requested File Upload Successful");                   // sending success message to client

                            ClientMessages msg = new ClientMessages(file_id, fr.get_requester(), req_id, clientID);         // new message -> (file_id, requester, req_id, uploader)
                            message_list.add(msg);
                        }
                        else
                        {
                            System.out.println(clientID + ": Requested File Upload Failed");
                            dataOutputStream.writeUTF("Requested File Upload Failed");
                        }
                    }
                    else {
                        dataOutputStream.writeBoolean(false);                                   // sending rejection to client
                        dataOutputStream.writeUTF("RequestID Does Not Exist");
                    }
                }

                // -- client chooses to view all the messages -- //
                else if(choice == 9)
                {
                    view_messages();
                }

                // --- client chooses to logout --- //
                else if(choice == 10)
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


    /// send the client a list of his own uploaded files
//    private void send_own_file_list()
//    {
//        try{
//            File public_dir = new File(get_client_directory(clientID, 1));
//            File private_dir = new File(get_client_directory(clientID, 2));
//
//            String[] public_files = public_dir.list();          // listing all the public filenames as String[]
//            String[] private_files = private_dir.list();        // listing all the private filenames as String[]
//
//            oos.writeObject(public_files);                      // sending the public_files list to client
//            oos.writeObject(private_files);                     // sending the private_files list to client
//        }
//        catch (Exception e)
//        {
//            e.printStackTrace();
//        }
//    }


    /// this method puts the client in the client_list, makes him online and fetches/initializes his files_list and file_count
    private boolean client_login()
    {
        try {
            clientID = dataInputStream.readUTF();                                   // reading the clientID from client

            if(!client_list.containsKey(clientID))                                  // if client has never logged in before
            {
                client_list.put(clientID, true);                                    // making the client online
                this.own_file_list = new HashMap<String, String>();                 // initializing the own_file_list

                return makeDirectory(clientID);
            }
            else                                                                    // client has logged in before
            {
                if(client_list.get(clientID))                                       // if client is active from some other source
                {
                    System.out.println("Client " + clientID + " already logged in");
                    return false;
                }
                else
                {
                    client_list.put(clientID, true);                                // making the client active
                    this.own_file_list = all_file_list.get(clientID);               // getting the own_file_list

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

    /// this method closes all the streams and socket and makes the client offline
    private void client_logout()
    {
        try
        {
            synchronized (client_list)
            {
                client_list.put(clientID, false);
            }

            dataInputStream.close();
            dataOutputStream.close();
            clientSocket.close();

            System.out.println("Client " + clientID + " Logged Out");
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
        }
    }

    /// this method reads the file_count saved in the txt file, and adds 1 to it
    private void increase_file_count()
    {
        try
        {
            String dir = serverDirectory + clientID + "\\file_count.txt";       // getting the directory

            FileReader fr = new FileReader(dir);
            int fileCount = Character.getNumericValue(fr.read());               // reading the count
            fr.close();

            fileCount += 1;                                                     // increasing the count

            FileWriter fw = new FileWriter(dir);
            fw.write((char)(fileCount+'0'));                                    // writing count to the file
            fw.close();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    /// this method returns the file count
    private int get_file_count()
    {
        try {
            String dir = serverDirectory + clientID + "\\file_count.txt";       // getting the directory

            FileReader fr = new FileReader(dir);
            int fileCount = Character.getNumericValue(fr.read());               // reading the file count
            fr.close();

            return fileCount;
        }
        catch (Exception e)
        {
            e.printStackTrace();
            return -1;
        }
    }

    /// this method create directories (main directory, private and public) and file_count.txt file in the main directory
    /// returns true if directory creates/exists, false otherwise
    private boolean makeDirectory(String clientID) throws IOException
    {
        String directory = serverDirectory + clientID;                      // e.g "E:/Server/1705108"
        File file = new File(directory);

        if (!file.exists())                                                 // if directory does not exist
        {
            if(file.mkdir())                                                // if main directory is created
            {
                String private_directory = directory + "\\private";
                String public_directory = directory + "\\public";

                String file_count_directory = directory + "\\file_count.txt";       // "file_count.txt" contains how many files client has
                File fileCount = new File(file_count_directory);                    // keeps the track files being uploaded

                if((new File(private_directory).mkdir()) && (new File(public_directory).mkdir()) && (fileCount.createNewFile()))       // if public, private directories and file_count are created
                {
                    System.out.println(clientID + " logged in");
                    System.out.println(clientID + ": Directories Created Successfully");

                    // -- initializing the file count -- //
                    FileWriter fw = new FileWriter(fileCount);
                    fw.write("0");                                       // file_count = 0
                    fw.close();

                    return true;
                }
                else
                    return false;
            }
            else                                                            // main directory cannot be created
            {
                System.out.println(clientID + ": Sorry couldn’t create specified directory");
                return false;
            }
        }
        else                                                                // directory exists previously
        {
            System.out.println(clientID + " logged in");
            System.out.println(clientID + ": Directory Exists");
            return true;
        }
    }

    /// this method returns the public/private directory of a user
    private String get_client_directory(String clientID, int choice)
    {
        if(choice == 1)
            return (serverDirectory + clientID + "\\public\\");             // e.g. server/1705108/public/
        else
            return (serverDirectory + clientID + "\\private\\");            // e.g. server/1705108/private/
    }

    /// returns the corresponding file directory againt a fileID
    /// used in sending file to client
    private String get_file_directory(String fileID)
    {
        String[] file_info = fileID.split("_");                                                 // "1705108_1_23" -> ["1705108", "1", "23"]

        HashMap<String, String> client_file_list = all_file_list.get(file_info[0]);                 // getting the file list of the corresponding user

        if(client_file_list == null)                                                                // if no user found
            return null;

        String filename = client_file_list.get(fileID);                                             // filename = "img.JPG"

        if(filename == null)                                                                        // if no file found under this ID
            return null;

        String directory = get_client_directory(file_info[0], Integer.parseInt(file_info[1]));      // directory = "serverDir/1705108/public/"
        return directory + filename;                                                                // "serverDir/1705108/public/img.JPG"
    }

    /// returns a file extension    e.g. "1705108.pdf" -> "pdf"
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
            System.out.println(clientID + ": File Extention Not Specified");
            return null;
        }
    }

    /// checks the total size of all chunks stored in the buffer plus the new file size. If it overflows the
    /// maximum size, the server does not allow the transmission
    private boolean receiveApproval(int file_size)
    {
        return !(chunks_stored.get() + file_size > MAX_BUFFER_SIZE);
    }

    /// deletes the specified file -> mainly for corrupted file deletion
    private boolean delete_file(String filePath)
    {
        File file_to_be_deleted = new File(filePath);

        if(file_to_be_deleted.delete())
        {
            System.out.println(clientID + ": Corrupted File Deleted");
            return true;
        }
        else
        {
            System.out.println(clientID + ": Corrupted File Not Deleted");
            return false;
        }
    }

    /// performs all the functionalities of receiving a file
    private String receive_file_util(int privacy_choice)
    {
        try {
            String clientFileName = dataInputStream.readUTF();              // read file name from client
            int clientFilesize = dataInputStream.readInt();                 // read file size from client

            if (receiveApproval(clientFilesize))                             // if file is allowed to be sent
            {
                dataOutputStream.writeUTF("File Sending Allowed");      // sending the approval message

                int chunk_size = (int) Math.floor(Math.random() * (MAX_CHUNK_SIZE - MIN_CHUNK_SIZE + 1) + MIN_CHUNK_SIZE);   // generating a chunk size
                String file_ID = clientID + "_" + privacy_choice + "_" + get_file_count();                                // e.g 1705108_1_0

                dataOutputStream.writeInt(chunk_size);                      // sending the chunk_size
                dataOutputStream.writeUTF(file_ID);                         // sending the file_ID

                String file_to_be_saved = get_client_directory(clientID, privacy_choice) + clientFileName;     // e.g. "serverDir/1705108/private/random.txt"

                receiveFile(file_to_be_saved, clientFilesize, chunk_size);          // receiving the file
                increase_file_count();                                              // file_count increases

                own_file_list.put(file_ID, clientFileName);                 // adding the file to own files list
                all_file_list.put(clientID, own_file_list);                 // updating the list in all files list

                return file_ID;
            } else {
                dataOutputStream.writeUTF("Buffer Overflow, Please Wait");  // sending the non-approval message
                return null;
            }
        }
        catch (IOException ex)
        {
            ex.printStackTrace();
            return null;
        }
    }

    /// receives file in chunks from a client
    /// puts the file in filePath
    /// sends acknowledgement to client
    private void receiveFile(String filePath, int clientFileSize, int chunk_size) throws IOException
    {
        int occupied_buffer_bytes = 0;                                          // buffer is empty
        FileOutputStream fileOutputStream = new FileOutputStream(filePath);

        byte[] buffer = new byte[chunk_size];                                   // buffer with size of chunk_size
        int bytes_left = clientFileSize;                                        // whole file is left to be sent
        int chunks_received = 0;

        chunks_stored.addAndGet(chunk_size);                                    // adding the occupied buffer size
//        System.out.println("Chunks Stored (before): " + chunks_stored.get());

        // receiving the file in chunks //
        while (bytes_left > 0 && occupied_buffer_bytes != -1) {
            occupied_buffer_bytes = dataInputStream.read(buffer, 0, Math.min(buffer.length, bytes_left));       // reading from input stream and putting it into buffer
            dataOutputStream.writeInt(occupied_buffer_bytes);                                                       // sending the acknowledgement

            fileOutputStream.write(buffer, 0, occupied_buffer_bytes);                                            // writing to file

            bytes_left -= occupied_buffer_bytes;
            chunks_received += occupied_buffer_bytes;
        }

        chunks_stored.addAndGet(-chunk_size);                                   // freeing up the occupied buffer size
//        System.out.println("Chunks Stored (after): " + chunks_stored.get());

        // File Transfer Completion Confirmation //
        String completion_confirmation = dataInputStream.readUTF();             // getting client confirmation message of completion
        System.out.println(clientID + ": " + completion_confirmation);

        if(chunks_received == clientFileSize)                                   // if all the received chunks sums up to file_size
        {
            dataOutputStream.writeUTF("File Received Successfully");
            System.out.println(clientID + ": File Received Successfully");
            System.out.println(clientID + ": File Path -> " + filePath);
        }
        else
        {
            dataOutputStream.writeUTF("File Receiving Failed");
            System.out.println(clientID + ": File Receiving Failed");
        }

        fileOutputStream.close();
    }

    /// sends file (from filePath) to client
    /// no acknowledgement
    private void sendFile(String filePath, int fileSize)
    {
        System.out.println(clientID + ": Sending file -> " + filePath);

        FileInputStream fileInputStream;
        try
        {
            fileInputStream = new FileInputStream(filePath);            // taking the file to be sent in InputStream
        }
        catch (FileNotFoundException e) {
            e.printStackTrace();
            System.out.println(clientID + ": File Cannot Be Found");
            return;
        }

        int occupied_buffer_bytes = 0;                                  // buffer is empty
        int chunk_size = MAX_CHUNK_SIZE;                                // for client_download, chunks will be sent in max_chunk_size
        int bytes_left = fileSize;                                      // whole file is left to sent
        byte[] buffer = new byte[chunk_size];                           // buffer with size of chunk_size

        try
        {
            // sending the file in chunks //
            while (bytes_left > 0 && occupied_buffer_bytes != -1)
            {
                occupied_buffer_bytes = fileInputStream.read(buffer, 0, Math.min(buffer.length, bytes_left));       // reading bytes from file and putting them into buffer

                dataOutputStream.write(buffer, 0, occupied_buffer_bytes);                                           // sending the bytes

                bytes_left -= occupied_buffer_bytes;                                                                    // bytes left to send
            }
            dataOutputStream.flush();

            dataOutputStream.writeUTF("File Sending Completed");                //  completion message from the server
            System.out.println(clientID + ": File Sending Completed");

            fileInputStream.close();
        }
        catch (IOException e)
        {
            System.out.println(clientID + ": File Sending Failed");
            e.printStackTrace();
        }
    }

    /// this method sends the client his/her messages
    private void view_messages() throws IOException
    {
        ArrayList<ClientMessages> this_client_msg = new ArrayList<ClientMessages>();

        for(int i=0; i<message_list.size(); i++)
        {
            if(message_list.get(i).get_requester().equalsIgnoreCase(clientID))
                this_client_msg.add(message_list.get(i));                           // taking an arraylist of individual client messages
        }

        int msg_count = this_client_msg.size();
        dataOutputStream.writeInt(msg_count);                                       // send how many messages of the requester

        if(msg_count>0)
        {
            for (int i = 0; i < msg_count; i++) {
                dataOutputStream.writeUTF(this_client_msg.get(i).get_uploader());       // sending the uploader name
                dataOutputStream.writeUTF(this_client_msg.get(i).get_fileID());         // sending the fileID
                dataOutputStream.writeInt(this_client_msg.get(i).get_req_id());         // sending the req_id
            }

            delete_messages(clientID);                                                  // deleting the messages after viewing
        }
    }

    /// this method deletes the messages of a given client
    private void delete_messages(String requester)
    {
        for(int i=0; i<message_list.size(); i++)
        {
            if(message_list.get(i).get_requester().equalsIgnoreCase(clientID))
                message_list.remove(message_list.get(i));
        }
    }

    /// notifies the client of new messages
    private boolean check_messages()
    {
        for(int i=0; i<message_list.size(); i++)
        {
            if(message_list.get(i).get_requester().equalsIgnoreCase(clientID))          // if there is any message found
                return true;
        }
        return false;
    }

    private boolean check_connection()
    {
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

            int con = in.read();
            if (con == -1) {
                System.out.println("Connection Lost");
                in.close();
                return false;
            } else
                return true;
        }
        catch (Exception e)
        {
            e.printStackTrace();
            return false;
        }
    }
}