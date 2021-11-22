import java.io.*;
import java.net.Socket;

public class Client {

    @SuppressWarnings("resource")
    public static void main(String[] args) throws IOException {

        Socket socket = new Socket("localhost", 5000);
        System.out.println("Connected to Server...");

        new ClientThread(socket);
        }
    }