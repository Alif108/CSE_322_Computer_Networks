import java.io.*;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class ClientThread implements Runnable {

    private Socket socket;
    private String student_ID;
    private DataOutputStream dataOutputStream;
    private DataInputStream dataInputStream;
    private ObjectInputStream ois;
    private Scanner sc;
    private Thread t;

    ClientThread(Socket socket) throws IOException {
        this.socket = socket;
        this.dataOutputStream = new DataOutputStream(socket.getOutputStream());
        this.dataInputStream = new DataInputStream(socket.getInputStream());
        this.ois = new ObjectInputStream(socket.getInputStream());
        this.sc = new Scanner(System.in);

        t = new Thread(this);
        t.start();
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
            System.out.println("1. Upload File");
            System.out.println("2. Lookup Users");
            System.out.println("3. Lookup Own Files");
            System.out.println("4. Lookup All Public Files");
            System.out.println("5. Logout");

            int choice = sc.nextInt();                              // taking choice from user
            try {
                dataOutputStream.writeInt(choice);                  // sending the choice to server
            } catch (IOException e) {
                e.printStackTrace();
            }

            // --- upload file --- //
            if (choice == 1)
            {
                System.out.println("1. Public");
                System.out.println("2. Private");

                try
                {
                    int privacy_choice = sc.nextInt();                      // taking the privacy choice
                    dataOutputStream.writeInt(privacy_choice);              // sending the privacy choice

                    if(privacy_choice != 1  && privacy_choice != 2)
                    {
                        System.out.println("Action Not Available");
                        continue;
                    }
                }
                catch (IOException e) {
                    e.printStackTrace();
                    return;
                }

                String filePath = "E:\\Others\\Practice_on_Networking\\File_Server\\Client\\src\\files\\img5.JPG";      // file to be uploaded from client side

                File MyFile = new File(filePath);                               // getting the file object
                String fileName = MyFile.getName();
                int fileSize = (int) MyFile.length();                           // collecting the file size

                try
                {
                    dataOutputStream.writeUTF(fileName);                        // sending the fileName
                    dataOutputStream.writeInt(fileSize);                        // sending the fileSize

                    String server_response = dataInputStream.readUTF();
                    System.out.println(server_response);

                    if(server_response.equalsIgnoreCase("File Sending Allowed"))
                    {
                        int chunk_size = dataInputStream.readInt();
                        String file_ID = dataInputStream.readUTF();

                        System.out.println("File will be sent in " + chunk_size + " bytes chunk");
                        System.out.println("File ID: " + file_ID);

                        sendFile(filePath, fileSize, chunk_size);               // sending the file
                    }
                }
                catch (IOException e) {
                    e.printStackTrace();
                }
            }

            // --- lookup users --- //
            else if(choice == 2)
            {
                show_client_list();
            }

            // --- lookup own files --- //
            else if(choice == 3)
            {
                list_own_files();
            }

            // --- lookup all public files --- //
            else if(choice == 4)
            {
                show_public_files();
            }

            // --- logout --- //
            else if (choice == 5)
            {
                logout();                                                       // logout closes all the streams and socket
                break;
            }
            else
            {
                System.out.println("Action Not Available");
            }
        }
    }


    private void sendFile(String filePath, int fileSize, int chunk_size){

        System.out.println("Sending file: " + filePath);

        FileInputStream fileInputStream;
        try
        {
            fileInputStream = new FileInputStream(filePath);            // taking the file to be sent in InputStream
        }
        catch (FileNotFoundException e) {
            e.printStackTrace();
            return;
        }

        int occupied_buffer_bytes = 0;                                  // buffer is empty

        int bytes_left = fileSize;                                      // whole file is left to sent
        byte[] buffer = new byte[chunk_size];                           // buffer with size of chunk_size

        try
        {
            // sending the file
            while (bytes_left > 0 && occupied_buffer_bytes != -1)
            {
                occupied_buffer_bytes = fileInputStream.read(buffer, 0, Math.min(buffer.length, bytes_left));       // reading bytes from file and putting them into buffer
                dataOutputStream.write(buffer, 0, occupied_buffer_bytes);                                           // sending the bytes
                

                bytes_left -= occupied_buffer_bytes;                                                                    // bytes left to send
//                System.out.println(bytes_left + " bytes left");
            }

            dataOutputStream.flush();
            System.out.println("File Sent Successfully");
            fileInputStream.close();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }


    private boolean logIn(){
        System.out.println("Input Your Student ID > ");
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

    private void logout(){
        try
        {
            dataOutputStream.close();
            dataInputStream.close();
            socket.close();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void list_own_files()
    {
        try
        {
            String[] public_files = (String[])ois.readObject();             // receiving the public files list
            String[] private_files = (String[])ois.readObject();            // receiving the private files list

            // showing public files
            System.out.println("Public Files");
            for (String pathname : public_files)
            {
                System.out.println(pathname);
            }
            System.out.println("");

            // showing private files
            System.out.println("Private Files");
            for (String pathname : private_files)
            {
                System.out.println(pathname);
            }
            System.out.println("");
        }
        catch (IOException | ClassNotFoundException e)
        {
            e.printStackTrace();
        }
    }

    private void show_client_list()
    {
        try
        {
            @SuppressWarnings("unchecked") HashMap<String, Boolean> client_list = (HashMap<String, Boolean>)ois.readObject();
            System.out.println("");

            for(Map.Entry<String, Boolean>entry : client_list.entrySet())
            {
                System.out.print(entry.getKey());
                if(entry.getValue())
                    System.out.println(" : online");
                else
                    System.out.println(" : offline");
            }
        }
        catch (IOException | ClassNotFoundException e)
        {
            e.printStackTrace();
        }
    }

    private void show_public_files()
    {
        try
        {
            @SuppressWarnings("unchecked") HashMap<String, HashMap<String, Integer>> all_file_list = (HashMap<String, HashMap<String, Integer>>) ois.readObject();
            System.out.println("");

            all_file_list.forEach((clientID, files_list) ->
            {
                System.out.println("Files of " + clientID);
                files_list.forEach((file_name, privacy) ->
                {
                    if(privacy == 1)                                                // 1-> public, 2 -> private
                        System.out.println(file_name);
                });
            });
            System.out.println("");
        }
        catch (IOException | ClassNotFoundException e)
        {
            e.printStackTrace();
        }
    }
}
