package tutorial.lab9;

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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PokemonServer {
    private static final int PORT = 8888;

    public static void main(String[] args) {
        // 第三步：实现 main 方法 —— 线程池 + ServerSocket + 接受连接
        ExecutorService executor = Executors.newCachedThreadPool();
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Waiting for clients to connect...");
            while (true) {
                Socket clientSocket = serverSocket.accept(); // 阻塞等待客户端
                System.out.println("Client connected.");
                executor.submit(() -> handleClient(clientSocket)); // 交给线程池处理
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            // 退出时关闭线程池
            // 注意：这里理想情况下不会走到 finally，除非 ServerSocket 抛异常退出
            // executor.shutdown(); // 如需优雅退出可在合适位置调用
        }
    }

    // 第四步：处理单个客户端
    private static void handleClient(Socket clientSocket) {
        try {
            // 输入：读客户端发送的宝可梦名字
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(clientSocket.getInputStream()));
            // 输出：把查询结果回写给客户端（true 表示自动 flush）
            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);

            String pokemonName;
            // 循环读取客户端输入
            while ((pokemonName = in.readLine()) != null) {
                if ("QUIT".equalsIgnoreCase(pokemonName.trim())) {
                    System.out.println("Client quits.");
                    break;
                }
                if (pokemonName.isBlank()) {
                    out.println("Please enter a non-empty pokemon name.");
                    continue;
                }

                // 第五步：调用 PokeAPI 获取数据
                String info = getPokemonInfo(pokemonName.trim());

                // 发送给客户端（info 是多行文本，客户端逐行打印）
                out.println(info);

                // 服务端日志
                System.out.println("Info of " + pokemonName.toLowerCase() + " sent");
            }
        } catch (IOException e) {
            System.out.println("Client quits.");
        } finally {
            try { clientSocket.close(); } catch (IOException ignored) {}
        }
    }

    // 第五步：HTTP + JSON 解析（使用 Java 11 HttpClient + Gson）
    private static String getPokemonInfo(String pokemonName) {
        String url = "https://pokeapi.co/api/v2/pokemon/" + pokemonName.toLowerCase() + "/";
        HttpClient http = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .GET()
                .header("User-Agent", "JavaHttpClient/11 (pokemon-lab)")
                .build();
        try {
            HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                return "Pokemon not found: " + pokemonName;
            }

            // 解析 JSON（注意这一行不要断行）
            com.google.gson.JsonObject root =
                    com.google.gson.JsonParser.parseString(resp.body()).getAsJsonObject();

            String name = root.get("name").getAsString();
            int height = root.get("height").getAsInt();
            int weight = root.get("weight").getAsInt();

            com.google.gson.JsonArray abilitiesArr = root.getAsJsonArray("abilities");
            StringBuilder abilities = new StringBuilder();
            abilities.append("[");
            boolean first = true;
            for (com.google.gson.JsonElement e : abilitiesArr) {
                com.google.gson.JsonObject abilityObj = e.getAsJsonObject().getAsJsonObject("ability");
                String abilityName = abilityObj.get("name").getAsString();
                if (!first) abilities.append(", ");
                abilities.append(abilityName);
                first = false;
            }
            abilities.append("]");

            // 按题目要求格式化输出
            return "Name: " + name + "\n"
                    + "Height: " + height + "\n"
                    + "Weight: " + weight + "\n"
                    + "Abilities: " + abilities;
        } catch (Exception e) {
            return "Error fetching data from PokeAPI: " + e.getMessage();
        }
    }
}