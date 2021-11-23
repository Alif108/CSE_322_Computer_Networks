import java.io.*;
import java.util.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicInteger;

public class Server {

    private static HashMap<String, Boolean> client_list;                                // clientID, active_status
    private static HashMap<String, HashMap<String, String>> file_list;                  // clientID, (fileID, filename)
    private static int MAX_BUFFER_SIZE = 16 * 1024 * 1024;                              // 16 MB
    private static int MAX_CHUNK_SIZE = 4 * 1024;                                       // 4 KB
    private static int MIN_CHUNK_SIZE = 1024;                                           // 1 KB
    private static AtomicInteger chunks_stored;                                         // keeps track of the chunks stored in the buffers

    public static void main(String[] args)
    {
        try(ServerSocket serverSocket = new ServerSocket(5000))
        {
            client_list = new HashMap<String, Boolean>();
            file_list = new HashMap<String, HashMap<String, String>>();
            chunks_stored = new AtomicInteger(0);

            System.out.println("Server Started...");
            System.out.println("Listening to port:5000...");

            while(true)
            {
                Socket socket = serverSocket.accept();
                System.out.println( socket + " connected...");
                System.out.println("Client IP: " + socket.getRemoteSocketAddress().toString());

                new ServerThread(socket, client_list, file_list, chunks_stored, MAX_BUFFER_SIZE, MAX_CHUNK_SIZE, MIN_CHUNK_SIZE);
            }
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }
}