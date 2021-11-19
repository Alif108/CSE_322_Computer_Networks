import java.io.*;
import java.util.*;
import java.net.ServerSocket;
import java.net.Socket;

public class Server {
//    private static DataOutputStream dataOutputStream = null;
//    private static DataInputStream dataInputStream = null;
    private static ArrayList<String> client_list = null;
    private static ArrayList<String> active_client_list = null;

    public static void main(String[] args)
    {
        try(ServerSocket serverSocket = new ServerSocket(5000))
        {
            client_list = new ArrayList<String>();
            active_client_list = new ArrayList<String>();

            System.out.println("Server Started...");
            System.out.println("Listening to port:5000...");

            while(true)
            {
                Socket socket = serverSocket.accept();
                System.out.println( socket + " connected...");

                new ServerThread(socket, client_list, active_client_list);
            }
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }
}