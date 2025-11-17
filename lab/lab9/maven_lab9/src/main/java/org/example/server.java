package org.example;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class server {
    private static final int PORT = 1234;
    private static final Set<ClientHandler> clients = ConcurrentHashMap.newKeySet();

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server started ......");
            while (true) {
                Socket socket = serverSocket.accept();
                ClientHandler handler = new ClientHandler(socket);
                clients.add(handler);
                new Thread(handler).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 广播消息到所有客户端
    public static void broadcast(String message, ClientHandler from) {
        for (ClientHandler client : clients) {
            if (client != from) { // 不发给自己
                client.sendMessage(message);
            }
        }
        // 自己也显示
        from.sendMessage(message);
    }

    public static void removeClient(ClientHandler client, String name) {
        clients.remove(client);
        broadcast(name + " left the chat!", null);
    }

    static class ClientHandler implements Runnable {
        private Socket socket;
        private PrintWriter out;
        private BufferedReader in;
        private String name;

        public ClientHandler(Socket socket) {
            try {
                this.socket = socket;
                this.out = new PrintWriter(socket.getOutputStream(), true);
                this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void sendMessage(String message) {
            out.println(message);
        }

        public void run() {
            try {
                // 客户端连接后先发名字
                name = in.readLine();
                server.broadcast(name + " joined the chat!", this);
                String msg;
                while ((msg = in.readLine()) != null) {
                    if ("QUIT".equalsIgnoreCase(msg.trim())) {
                        break;
                    }
                    server.broadcast(name + ": " + msg, this);
                }
            } catch (IOException e) {
                // 客户端断开连接
            } finally {
                try {
                    socket.close();
                } catch (IOException e) {
                    // 忽略
                }
                server.removeClient(this, name);
            }
        }
    }
}