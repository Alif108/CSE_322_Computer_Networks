import java.io.*;
import java.util.*;
import java.net.ServerSocket;
import java.net.Socket;

public class Server {

    private static HashMap<String, Boolean> client_list = null;             // clientID, active_status
    private static HashMap<String, String> file_list = null;                // fileName, fileID

    public static void main(String[] args)
    {
        try(ServerSocket serverSocket = new ServerSocket(5000))
        {
            client_list = new HashMap<String, Boolean>();
            file_list = new HashMap<String, String>();

            System.out.println("Server Started...");
            System.out.println("Listening to port:5000...");

            while(true)
            {
                Socket socket = serverSocket.accept();
                System.out.println( socket + " connected...");

                new ServerThread(socket, client_list, file_list);
            }
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }
}