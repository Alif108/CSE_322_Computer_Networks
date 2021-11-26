import java.io.*;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.*;

public class ClientThread implements Runnable {

    private Socket socket;
    private String student_ID;
    private DataOutputStream dataOutputStream;
    private DataInputStream dataInputStream;
    private ObjectInputStream ois;
    private Scanner sc;
    private Thread t;
    private String client_file_directory = "E:\\Others\\Practice_on_Networking\\File_Server\\Client\\src\\downloads\\";

    ClientThread(Socket socket){
        try {
            this.socket = socket;
            this.dataOutputStream = new DataOutputStream(socket.getOutputStream());
            this.dataInputStream = new DataInputStream(socket.getInputStream());
            this.ois = new ObjectInputStream(socket.getInputStream());
            this.sc = new Scanner(System.in);

            t = new Thread(this);
            t.start();
        }
        catch (IOException ex)
        {
            ex.printStackTrace();
        }
    }

    @Override
    public void run() {

        if (!logIn())                                            // if login is failed
        {
            System.out.println("Login Failed");
            logout();
            return;
        }

        while(true)
        {
            try {

                System.out.print("\n");
                System.out.println("Profile ID #" + student_ID);
                System.out.println("-------------------------");

                // -- checking of arrival of new messages -- //
                if(dataInputStream.readBoolean())
                    System.out.println("New Messages In Inbox");

                System.out.println("0. Refresh");
                System.out.println("1. Upload File");
                System.out.println("2. Download File");
                System.out.println("3. Lookup Own Files");
                System.out.println("4. Lookup All Public Files");
                System.out.println("5. Lookup Users");
                System.out.println("6. Request File");
                System.out.println("7. Show File Requests");
                System.out.println("8. Upload For Request");
                System.out.println("9. View Messages");
                System.out.println("10. Logout");

                int choice = sc.nextInt();                              // taking choice from user

                dataOutputStream.writeInt(choice);                      // sending the choice to server

                // --- Refresh --- //
                if(choice == 0)
                    continue;

                // --- upload file --- //
                else if (choice == 1) {
                    System.out.println("1. Public");
                    System.out.println("2. Private");

                    int privacy_choice = sc.nextInt();                      // taking the privacy choice
                    dataOutputStream.writeInt(privacy_choice);              // sending the privacy choice

                    if (privacy_choice != 1 && privacy_choice != 2) {
                        System.out.println("Action Not Available");
                        continue;
                    }

                    upload_file_util();
                    System.out.print("\n");
                }

                // --- download file --- //
                else if (choice == 2)
                {
                    download_file_util();
                    System.out.print("\n");
                }

                // --- lookup own files --- //
                else if (choice == 3)
                {
                    list_own_files();
                    System.out.print("\n");
                }

                // --- lookup all public files --- //
                else if (choice == 4)
                {
                    show_public_files();
                    System.out.print("\n");
                }

                // --- lookup users --- //
                else if(choice == 5)
                {
                    show_client_list();
                    System.out.print("\n");
                }

                // --- request file --- //
                else if(choice == 6)
                {
                    System.out.print("Enter Request Description > ");
                    String req_desc = sc.next();
                    dataOutputStream.writeUTF(req_desc);                                // sending the request description to server

                    System.out.println("From Server: " + dataInputStream.readUTF());    // server sends : File Request Issued
                    System.out.println("From Server: " + dataInputStream.readUTF());    // server sends : Request ID: #req_id

                    System.out.print("\n");
                }

                // --- show all the file requests --- //
                else if(choice == 7)
                {
                    show_file_requests();
                    System.out.print("\n");
                }

                // --- upload a file against an issued request --- //
                else if(choice == 8)
                {
                    System.out.print("Enter the Request ID of the Request #");
                    int req_id = sc.nextInt();

                    dataOutputStream.writeInt(req_id);              // sending the request_id to server

                    if(dataInputStream.readBoolean())               // if confirmed from server
                    {
                        upload_file_util();
                    }
                    System.out.println("From Server: " + dataInputStream.readUTF());        // printing server message

                    System.out.print("\n");
                }

                // --- view all unread messages --- //
                else if(choice == 9)
                {
                    view_messages();
                    System.out.print("\n");
                }

                // --- logout --- //
                else if (choice == 10)
                {
                    logout();                               // logout closes all the streams and socket
                    break;
                }

                // --- invalid action --- //
                else
                {
                    System.out.println("Action Not Available");
                    System.out.print("\n");
                }
            }
            catch (Exception ex)
            {
                ex.printStackTrace();
                return;
            }
        }
    }


    /// sends file in chunks from the filePath
    /// sends acknowledgement
    private void upload_file(String filePath, int fileSize, int chunk_size)
    {
        System.out.println("Uploading file: " + filePath);

        FileInputStream fileInputStream;
        try
        {
            fileInputStream = new FileInputStream(filePath);            // taking the file to be sent in InputStream
        }
        catch (FileNotFoundException e) {
            e.printStackTrace();
            System.out.println("File Cannot Be Found");
            return;
        }

        int occupied_buffer_bytes = 0;                                  // buffer is empty

        int bytes_left = fileSize;                                      // whole file is left to sent
        byte[] buffer = new byte[chunk_size];                           // buffer with size of chunk_size

        try
        {
            // sending the file in chunks //
            while (bytes_left > 0 && occupied_buffer_bytes != -1)
            {
                occupied_buffer_bytes = fileInputStream.read(buffer, 0, Math.min(buffer.length, bytes_left));       // reading bytes from file and putting them into buffer

                dataOutputStream.write(buffer, 0, occupied_buffer_bytes);                                           // sending the bytes

                int ack = dataInputStream.readInt();                                                                    // receiving the acknowledgement

//                System.out.println("Client Sent: " + occupied_buffer_bytes + " bytes");
//                System.out.println("Server Received: " + ack + " bytes");

                bytes_left -= occupied_buffer_bytes;                                                                    // bytes left to send
            }
            dataOutputStream.flush();

            // sending completion confirmation //
            dataOutputStream.writeUTF("File Uploading Completed");            // sending a confirmation message
            String server_confirmation = dataInputStream.readUTF();             // receiving a server confirmation
            System.out.println("From Server: " + server_confirmation);

            fileInputStream.close();
        }
        catch (IOException ex)
        {
//            dataOutputStream.writeUTF("Connection Timed Out");
            ex.printStackTrace();
        }
    }

    /// performs all the functionalities of uploading a file
    private void upload_file_util()
    {
        try {

//            String filePath = "E:\\Others\\Practice_on_Networking\\File_Server\\Client\\src\\files\\img5.JPG";      // file to be uploaded from client side
//            File MyFile = new File(filePath);

            File MyFile = null;
            String filePath = null;

            while(true) {
                System.out.print("Enter The File Path >");
                filePath = sc.next().trim();

                MyFile = new File(filePath);                           // getting the file object

                if(MyFile.exists())
                    break;

                System.out.println("Please Insert A Correct File Path");
            }

            String fileName = MyFile.getName();                         // getting the file name    i.e. 1705108.pdf
            int fileSize = (int) MyFile.length();                       // collecting the file size

            dataOutputStream.writeUTF(fileName);                        // sending the fileName
            dataOutputStream.writeInt(fileSize);                        // sending the fileSize

            String server_response = dataInputStream.readUTF();         // receiving the server response
            System.out.println("From Server: " + server_response);

            if (server_response.equalsIgnoreCase("File Sending Allowed")) {
                int chunk_size = dataInputStream.readInt();             // getting the chunk size chosen by server
                String file_ID = dataInputStream.readUTF();             // getting the file_ID generated by server

                System.out.println("File will be sent in " + chunk_size + " bytes chunk");
                System.out.println("File ID #" + file_ID);

                upload_file(filePath, fileSize, chunk_size);               // sending the file
            }
        }
        catch (IOException ex)
        {
            ex.printStackTrace();
        }
    }

    /// downloads file in chunks in the filePath
    /// no acknowledgement
    private void download_file(String filePath, int fileSize, int chunk_size)
    {
        try
        {
            int occupied_buffer_bytes = 0;                                          // buffer is empty
            FileOutputStream fileOutputStream = new FileOutputStream(filePath);

            byte[] buffer = new byte[chunk_size];                                   // buffer with size of chunk_size
            int bytes_left = fileSize;                                              // whole file is left to be sent
            int chunks_received = 0;                                                // no chunks received yet

            // receiving the file in chunks //
            while (bytes_left > 0 && occupied_buffer_bytes != -1)
            {
                occupied_buffer_bytes = dataInputStream.read(buffer, 0, Math.min(buffer.length, bytes_left));       // reading from input stream and putting it into buffer

                fileOutputStream.write(buffer,0, occupied_buffer_bytes);                                            // writing to file

                bytes_left -= occupied_buffer_bytes;
                chunks_received += occupied_buffer_bytes;
            }

            System.out.println(dataInputStream.readUTF());                          // server completion message

            // File Transfer Completion Confirmation //
            if(chunks_received == fileSize)
            {
                System.out.println("File Downloaded Successfully");
                System.out.println("File Path: " + filePath);
            }
            else
            {
                System.out.println("File Downloading Failed");
            }

            fileOutputStream.close();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    /// performs all the functionalities of downloading a file
    private boolean download_file_util() throws IOException
    {
        System.out.print("Enter File ID #");
        String requested_file_id = sc.next();
        dataOutputStream.writeUTF(requested_file_id);           // sending the requested file id

        String server_response = dataInputStream.readUTF();     // getting the server_response
        System.out.println("From Server: " + server_response);

        if(server_response.equalsIgnoreCase("File Not Found"))
            return false;                                           // if server could not locate the targeted file

        boolean download_dir_exists = false;
        String input_dir = null;

        while(!download_dir_exists)
        {
            System.out.print("Enter Download Directory > ");
            input_dir = sc.next().trim();

            download_dir_exists = new File(input_dir).isDirectory();

            if(!download_dir_exists)
                System.out.println("Please Enter An Existing Directory");
        }

        String filename = dataInputStream.readUTF();            // receiving the file name
        int file_size = dataInputStream.readInt();              // receiving the file_size
        int chunk_size = dataInputStream.readInt();             // receiving the chunk_size

//                    download_file(client_file_directory + filename, file_size, chunk_size);
        download_file(input_dir + filename, file_size, chunk_size);

        return true;
    }

    /// sends the ID to the server
    private boolean logIn(){
        System.out.print("Input Your Student ID to Login > ");
        this.student_ID = sc.nextLine();                            // taking the student ID input

        try
        {
            dataOutputStream.writeUTF(student_ID);                      // sending ID to server
            String serverApproval = dataInputStream.readUTF();         // getting the server response

            return !serverApproval.equalsIgnoreCase("login failed");
        }
        catch (IOException e)
        {
            e.printStackTrace();
            return false;
        }
    }

    /// this method closes all the streams and socket
    private void logout(){
        try
        {
            dataOutputStream.close();
            dataInputStream.close();
            ois.close();
            socket.close();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    /// this function shows all the files uploaded by the client
//    private void list_own_files()
//    {
//        try
//        {
//            String[] public_files = (String[])ois.readObject();             // receiving the public files list
//            String[] private_files = (String[])ois.readObject();            // receiving the private files list
//
//            // showing public files
//            System.out.println("Public Files");
//            for (String filename : public_files)
//            {
//                System.out.println(filename);
//            }
//            System.out.println("\n");
//
//            // showing private files
//            System.out.println("Private Files");
//            for (String filename : private_files)
//            {
//                System.out.println(filename);
//            }
//            System.out.println("\n");
//        }
//        catch (IOException | ClassNotFoundException e)
//        {
//            e.printStackTrace();
//        }
//    }

    /// this function shows all the files and their IDs uploaded by the client
    private void list_own_files()
    {
//        try{
//            @SuppressWarnings("unchecked") HashMap<String, String> own_file_list = (HashMap<String, String>) ois.readObject();
//
//            own_file_list.forEach((fileID, fileName) ->
//            {
//                String[] file_info = fileID.split("_");                     // splits ID "1705108_1_0" -> ["1705108", "1", "0"]
//
//                System.out.println("File Name: " + fileName);
//                System.out.println("File ID: " + fileID);
//                if(file_info[1].equalsIgnoreCase("1"))                  // 1 -> Public, 2 -> Private
//                    System.out.println("File Privacy: Public");
//                else
//                    System.out.println("File Privacy: Private");
//                System.out.print("\n");
//            });
//        }
//        catch (Exception e)
//        {
//            e.printStackTrace();
//        }
        try{
            String[] file_info = null;
            int file_list_size = dataInputStream.readInt();

            for(int i=0; i<file_list_size; i++)
            {
                String fileID = dataInputStream.readUTF();                  // getting the fileID
                String fileName = dataInputStream.readUTF();                // getting the fileName

                System.out.println("File Name: " + fileName);
                System.out.println("File ID #" + fileID);

                file_info = fileID.split("_");                      // splits ID "1705108_1_0" -> ["1705108", "1", "0"]

                if(file_info[1].equalsIgnoreCase("1"))          // 1-> public, 2 -> private
                    System.out.println("File Privacy: Public");
                else
                    System.out.println("File Privacy: Private");

                System.out.print("\n");
            }
        }
        catch (IOException ex)
        {
            ex.printStackTrace();
        }
    }

    /// this function shows all the clients and their active status
    private void show_client_list()
    {
        try {
            int no_of_clients = dataInputStream.readInt();

            for(int i=0; i<no_of_clients; i++)
            {
                System.out.print(dataInputStream.readUTF());

                boolean online = dataInputStream.readBoolean();
                if(online)
                    System.out.println(" : online");
                else
                    System.out.println(" : offline");
            }
        }
        catch (IOException ex)
        {
            ex.printStackTrace();
        }
    }

    /// all_files_list has records as (clientID, (filename, privacy_status))
    private void show_public_files()
    {
        try
        {
            @SuppressWarnings("unchecked") HashMap<String, HashMap<String, String>> all_file_list = (HashMap<String, HashMap<String, String>>) ois.readObject();
            System.out.print("\n");

            all_file_list.forEach((clientID, files_list) ->
            {
                System.out.println("Files of " + clientID);
                System.out.println("----------------------");

                files_list.forEach((fileID, fileName) ->
                {
                    String[] file_info = fileID.split("_");                     // splits ID "1705108_1_0" -> ["1705108", "1", "0"]
                    if(file_info[1].equalsIgnoreCase("1"))              // 1 -> public, 2 -> private
                    {
                        System.out.println("File Name: " + fileName);
                        System.out.println("File ID #" + fileID);
                        System.out.print("\n");
                    }
                });
                System.out.print("\n");
            });
            System.out.print("\n");
        }
        catch (IOException | ClassNotFoundException e)
        {
            e.printStackTrace();
        }
    }

    // this method shows the messages of client
    private void view_messages() throws IOException
    {
        int msg_count = dataInputStream.readInt();

        if(msg_count > 0)
        {
            for (int i = 0; i < msg_count; i++)
            {
                String uploader = dataInputStream.readUTF();            // getting the uploader
                String fileID = dataInputStream.readUTF();              // getting the fileID
                int req_id = dataInputStream.readInt();                 // getting the req_id

                System.out.println(uploader + " uploaded File #" + fileID + " for your Request ID #" + req_id);
            }
        }
        else
            System.out.println("No New Messages");
    }

    /// shows all the issued requests for files from other clients
    private void show_file_requests()
    {
        try {
            int list_size = dataInputStream.readInt();                                          // server sends the list_size, to iterate the for loop

            for(int i=0; i<list_size; i++)
            {
                System.out.println("Requester ID: " + dataInputStream.readUTF());               // server sends the requester id
                System.out.println("Request ID: " + dataInputStream.readInt());                 // server sends the request id
                System.out.println("Request Description: " + dataInputStream.readUTF());        // server sends the request description

                @SuppressWarnings("unchecked") HashMap<String, ArrayList<String>> uploaded_files = (HashMap<String, ArrayList<String>>)ois.readObject();
                uploaded_files.forEach((uploader, files_list) ->
                {
                    System.out.println("Uploader: " + uploader);                                // printing teh uploader
                    files_list.forEach((fileID) ->
                    {
                        System.out.println("File ID #" + fileID);                               // printing the uploader's files
                    });
                });
                System.out.print("\n");
            }
        }
        catch (IOException | ClassNotFoundException ex)
        {
            ex.printStackTrace();
        }
    }

    /// confirm connection
    private boolean confirm_connection() throws IOException
    {
        try {
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            out.write(1);

            out.close();

            return dataInputStream.readBoolean();
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
            return dataInputStream.readBoolean();
        }
    }
}
