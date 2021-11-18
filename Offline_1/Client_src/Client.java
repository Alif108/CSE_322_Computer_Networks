import java.io.*;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Scanner;

public class Client {

    private static DataOutputStream dataOutputStream = null;
    private static DataInputStream dataInputStream = null;

    @SuppressWarnings("resource")
    public static void main(String[] args) throws UnknownHostException, IOException {

        Socket socket = new Socket("localhost", 5000);
        System.out.println("Connected to Server...");

        String filePath1 = "E:\\Others\\Practice_on_Networking\\File_Server\\Client\\src\\files\\alif1.txt";
        String filePath2 = "E:\\Others\\Practice_on_Networking\\File_Server\\Client\\src\\files\\alif2.txt";
        String filePath3 = "E:\\Others\\Practice_on_Networking\\File_Server\\Client\\src\\files\\img.png";
        String filePath4 = "E:\\Others\\Practice_on_Networking\\File_Server\\Client\\src\\files\\img2.png";
        String filePath5 = "E:\\Others\\Practice_on_Networking\\File_Server\\Client\\src\\files\\img3.png";

        dataInputStream = new DataInputStream(socket.getInputStream());
        dataOutputStream = new DataOutputStream(socket.getOutputStream());

        sendFile(filePath1);
        sendFile(filePath2);
        sendFile(filePath3);
        sendFile(filePath4);
        sendFile(filePath5);

        dataInputStream.close();
        dataOutputStream.close();
        socket.close();
    }

//    public static void sendFile(String filePath) throws IOException {
//        File MyFile = new File(filePath);
//        int fileSize = (int) MyFile.length();
//
//        byte[] buffer = new byte[fileSize];
//        FileInputStream fileInputStream = new FileInputStream(filePath);
//        int bytes = fileInputStream.read(buffer,0,buffer.length);
//
//        dataOutputStream.writeInt(fileSize);
//        dataOutputStream.write(buffer,0,bytes);
//    }

    public static void sendFile(String filePath) throws IOException {

        System.out.println("Sending file: " + filePath);

        FileInputStream fileInputStream = new FileInputStream(filePath);
        int chunk_size = 4 * 1024;                                      // 4 KB
        int occupied_buffer_bytes = 0;                                  // buffer is empty

        File MyFile = new File(filePath);
        int fileSize = (int) MyFile.length();                           // collecting the file size
        int bytes_left = fileSize;                                      // whole file is left to sent
        byte[] buffer = new byte[chunk_size];                           // buffer with size of chunk_size

        dataOutputStream.writeInt(fileSize);                            // sending the fileSize

        while(bytes_left>0 && occupied_buffer_bytes != -1)
        {
            occupied_buffer_bytes = fileInputStream.read(buffer, 0, (int)Math.min(buffer.length, bytes_left));  // reading bytes from file
            dataOutputStream.write(buffer, 0, occupied_buffer_bytes);                                           // sending the bytes
            System.out.println(occupied_buffer_bytes + " bytes sent");

            bytes_left -= occupied_buffer_bytes;                                                                    // bytes left to send
            System.out.println(bytes_left + " bytes left");
        }

        System.out.println("File Sent Successfully");
        fileInputStream.close();
    }
}