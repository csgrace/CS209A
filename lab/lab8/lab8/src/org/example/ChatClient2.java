package org.example;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;


public class ChatClient2 {
    public static void main(String[] args) {
        try {
            Socket socket = new Socket("localhost", 1234);
            System.out.println("Connected to server");

            System.out.print("Enter your name: ");
            Scanner scanner = new Scanner(System.in);
            String name = scanner.nextLine();

            new Thread(new MessageSender(socket, name)).start();
            new Thread(new MessageReceiver(socket)).start();

        } catch (IOException e) {
            System.out.println("Disconnected from server");
        }
    }
}

class MessageSender2 implements Runnable {
    private final PrintWriter out;
    private final BufferedReader consoleReader;

    public MessageSender2(Socket socket, String name) throws IOException {
        this.out = new PrintWriter(socket.getOutputStream(), true);
        this.consoleReader = new BufferedReader(new InputStreamReader(System.in));
        out.println(name);
    }

    @Override
    public void run() {
        try {
            String message;
            while ((message = consoleReader.readLine()) != null) {
                out.println(message);
            }
        } catch (IOException e) {
            System.out.println("Error sending message");
        }
    }
}

class MessageReceiver2 implements Runnable {
    private final BufferedReader in;

    public MessageReceiver2(Socket socket) throws IOException {
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
    }

    @Override
    public void run() {
        try {
            String message;
            while ((message = in.readLine()) != null) {
                System.out.println(message);
            }
        } catch (IOException e) {
            System.out.println("Disconnected from server");
        }
    }
}
