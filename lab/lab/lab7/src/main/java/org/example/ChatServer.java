package main.java.org.example;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ChatServer {
    public static void main(String[] args) {
        Set<ClientHandler> clients = ConcurrentHashMap.newKeySet();

        try (ServerSocket serverSocket = new ServerSocket(1234)) {
            System.out.println("Server started ......");

            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("New client connected.");

                ClientHandler clientHandler = new ClientHandler(socket);
                clients.add(clientHandler);
                new Thread(clientHandler).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

class ClientHandler implements Runnable {
    private final Socket socket;
    private final BufferedReader in;
    private final PrintWriter out;

    public ClientHandler(Socket socket) throws IOException {
        this.socket = socket;
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.out = new PrintWriter(socket.getOutputStream(), true);
    }

    @Override
    public void run() {
        try {
            String name;
            while ((name = in.readLine()) != null) {
                if (name.equalsIgnoreCase("QUIT")) {
                    out.println("end!");
                    System.out.println("Client quits.");
                    break;
                }

                String info = Info(name);
                out.println(info);
                System.out.println("Info of " + name + " sent");
            }
        } catch (IOException e) {
            System.out.println("Client disconnected");
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private String Info(String name) {
        try {
            String url = "https://pokeapi.co/api/v2/pokemon/" + name.toLowerCase();
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String json = response.body();

            JSONObject obj = new JSONObject(json);
            int height = obj.getInt("height");
            int weight = obj.getInt("weight");
            JSONArray abilities = obj.getJSONArray("abilities");

            StringBuilder sb = new StringBuilder();
            sb.append("Name: ").append(name).append("\n");
            sb.append("Height: ").append(height).append("\n");
            sb.append("Weight: ").append(weight).append("\n");
            sb.append("Abilities: [");

            for (int i = 0; i < abilities.length(); i++) {
                sb.append(abilities.getJSONObject(i).getJSONObject("ability").getString("name"));
                if (i < abilities.length() - 1) sb.append(", ");
            }
            sb.append("]").append("\n");

            return sb.toString();

        } catch (Exception e) {
            return "Error";
        }
    }
}
