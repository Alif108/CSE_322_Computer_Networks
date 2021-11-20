import java.io.*;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Scanner;

public class Client {

    @SuppressWarnings("resource")
    public static void main(String[] args) throws UnknownHostException, IOException {

        Socket socket = new Socket("localhost", 5000);
        System.out.println("Connected to Server...");

        new ClientThread(socket);
        }
    }