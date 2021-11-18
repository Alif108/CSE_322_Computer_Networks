import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class Server {
    private static DataOutputStream dataOutputStream = null;
    private static DataInputStream dataInputStream = null;

    public static void main(String[] args) {

        try(ServerSocket serverSocket = new ServerSocket(5000))
        {
            System.out.println("Server Started...");
            System.out.println("Listening to port:5000...");
            Socket clientSocket = serverSocket.accept();
            System.out.println(clientSocket + " connected...");

            dataInputStream = new DataInputStream(clientSocket.getInputStream());
            dataOutputStream = new DataOutputStream(clientSocket.getOutputStream());

            receiveFile("E:\\Others\\Practice_on_Networking\\File_Server\\Server\\src\\files\\1.txt");
            receiveFile("E:\\Others\\Practice_on_Networking\\File_Server\\Server\\src\\files\\2.txt");
            receiveFile("E:\\Others\\Practice_on_Networking\\File_Server\\Server\\src\\files\\3.png");
            receiveFile("E:\\Others\\Practice_on_Networking\\File_Server\\Server\\src\\files\\4.png");
            receiveFile("E:\\Others\\Practice_on_Networking\\File_Server\\Server\\src\\files\\5.png");

            dataInputStream.close();
            dataOutputStream.close();
            clientSocket.close();
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }

    private static void receiveFile(String fileName) throws Exception{

        System.out.println("Receiving file: " + fileName);

        int chunk_size = 4 * 1024;                                              // 4 KB
        int occupied_buffer_bytes = 0;                                          // buffer is empty
        FileOutputStream fileOutputStream = new FileOutputStream(fileName);

        int clientFilesize = dataInputStream.readInt();                         // read file size
        System.out.println("Incoming File Size:" + clientFilesize);

        byte[] buffer = new byte[chunk_size];                                   // buffer with size of chunk_size
        int bytes_left = clientFilesize;                                        // whole file is left to be sent

        while (bytes_left > 0 && occupied_buffer_bytes != -1)
        {
            occupied_buffer_bytes = dataInputStream.read(buffer, 0, (int)Math.min(buffer.length, bytes_left));      // reading from input stream and putting it into buffer
            fileOutputStream.write(buffer,0, occupied_buffer_bytes);                                                // writing to file

            System.out.println(occupied_buffer_bytes + " bytes received");

            bytes_left -= occupied_buffer_bytes;
            System.out.println(bytes_left + " bytes left");
        }
        System.out.println("File Received Successfully");
        fileOutputStream.close();
    }
}