package importdata;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;

public class StackOverflowDataCollector {
    private static final String BASE_URL = "https://api.stackexchange.com/2.3";
    private static final String OUTPUT_DIR = "D:\\E\\SUSTECH\\grade3\\3_up\\CS\\CS209A\\project\\CS209A_project\\data_1000_new";
    // C:\GitHub\CS209A_project\data_1000_new
    // D:\E\SUSTECH\grade3\3_up\CS\CS209A\project\CS209A_project\data_1000_new
    private static final String API_KEY = "rl_BN6TcCkgWwdVE6WWUhdWmnfEk"; // 如果需要API密钥

    private final HttpClient httpClient;

    public StackOverflowDataCollector() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    public void collectJavaQuestions(int totalQuestions) throws Exception {
        Files.createDirectories(Paths.get(OUTPUT_DIR));

        int collected = 0;
//        int page = 1;
        int pageSize = 10;

        Random random = new Random();
        int maxPage = 300000;

        Set<Long> collectedQuestionIds = new HashSet<>();

        System.out.println("-------starting--------");

        while (collected < totalQuestions) {
            int page = random.nextInt(maxPage) + 1;

            System.out.println("第 " + page + " 页...");

            List<Long> questionIds = getQuestionIds(page, pageSize);

            if (questionIds.isEmpty()) {
                System.out.println("第 " + page + " 页没有数据 停止");
                continue;
            }

            System.out.println("找到 " + questionIds.size() + " 个问题");

            for (Long questionId : questionIds) {
                if (collected >= totalQuestions) break;

                if (collectedQuestionIds.contains(questionId)) {
                    System.out.println("ID=" + questionId + " 已收集，跳过");
                    continue;
                }

                try {
                    System.out.println("ID=" + questionId);

                    JSONObject fullQuestion = getFullQuestionWithBody(questionId);

                    // 验证tag含有“java”
                    if (isValidJavaQuestion(fullQuestion)) {
                        saveQuestionToFile(collected, fullQuestion);
                        collectedQuestionIds.add(questionId);
                        collected++;

                        double progress = (collected * 100.0) / totalQuestions;
                        System.out.printf("collected: " + collected);

                        JSONObject question = fullQuestion.getJSONObject("question");
                        String title = question.optString("title", "").substring(0, Math.min(50, question.optString("title", "").length()));
                        boolean hasBody = question.has("body") && !question.isNull("body") && question.getString("body").length() > 0;
                    } else {
                        System.out.println("ID=" + questionId + "no java tag");
                    }
                    Thread.sleep(800);

                } catch (Exception e) {
                    System.err.println("收集问题 " + questionId + " 失败: " + e.getMessage());
                    Thread.sleep(3000);
                }
            }

            page++;

            // 页间延迟
            if (collected < totalQuestions) {
                Thread.sleep(8000);
            }
        }

        System.out.println("success");
        System.out.println("total: " + collected);
    }

    private List<Long> getQuestionIds(int page, int pageSize) throws Exception {
        String url = BASE_URL + "/questions?" +
                "page=" + page +
                "&pagesize=" + pageSize +
                "&order=desc" +
                "&sort=creation" +
                "&tagged=java" +
                "&site=stackoverflow";

        if (API_KEY != null && !API_KEY.trim().isEmpty()) {
            url += "&key=" + API_KEY.trim();
        }

        String response = sendGetRequest(url);
        JSONObject json = new JSONObject(response);

        if (json.has("error_id")) {
            throw new Exception("api error: " + json.optString("error_message", "Unknown"));
        }

        JSONArray items = json.getJSONArray("items");
        List<Long> ids = new ArrayList<>();

        for (int i = 0; i < items.length(); i++) {
            ids.add(items.getJSONObject(i).getLong("question_id"));
        }

        return ids;
    }

    private JSONObject getFullQuestionWithBody(long questionId) throws Exception {
        JSONObject result = new JSONObject();

        // question
        JSONObject question = getQuestionWithBody(questionId);
        result.put("question", question);

        // answer
        JSONArray answers = getAnswersWithBody(questionId);
        result.put("answers", answers);

        // comment of question
        JSONArray questionComments = getQuestionComments(questionId);
        result.put("question_comments", questionComments);

        // comment of answer
        JSONObject answerComments = new JSONObject();
        for (int i = 0; i < answers.length(); i++) {
            JSONObject answer = answers.getJSONObject(i);
            long answerId = answer.getLong("answer_id");

            try {
                JSONArray comments = getAnswerComments(answerId);
                if (comments.length() > 0) {
                    answerComments.put(String.valueOf(answerId), comments);
                }
                Thread.sleep(100);
            } catch (Exception e) {
                System.err.println("收集答案评论失败: " + e.getMessage());
            }
        }
        result.put("answer_comments", answerComments);

        return result;
    }

    private JSONObject getQuestionWithBody(long questionId) throws Exception {
        String url = BASE_URL + "/questions/" + questionId +
                "?site=stackoverflow" +
                "&filter=withbody";

        if (API_KEY != null && !API_KEY.trim().isEmpty()) {
            url += "&key=" + API_KEY.trim();
        }

        String response = sendGetRequest(url);
        JSONObject json = new JSONObject(response);
        JSONArray items = json.getJSONArray("items");

        if (items.length() == 0) {
            throw new Exception("问题不存在: " + questionId);
        }

        return items.getJSONObject(0);
    }

    private JSONArray getAnswersWithBody(long questionId) throws Exception {
        String url = BASE_URL + "/questions/" + questionId + "/answers" +
                "?site=stackoverflow" +
                "&order=desc" +
                "&sort=votes" +
                "&filter=withbody";

        if (API_KEY != null && !API_KEY.trim().isEmpty()) {
            url += "&key=" + API_KEY.trim();
        }

        String response = sendGetRequest(url);
        JSONObject json = new JSONObject(response);

        if (json.has("items")) {
            return json.getJSONArray("items");
        }

        return new JSONArray();
    }

    private JSONArray getQuestionComments(long questionId) throws Exception {
        String url = BASE_URL + "/questions/" + questionId + "/comments" +
                "?site=stackoverflow" +
                "&order=desc" +
                "&sort=creation";

        if (API_KEY != null && !API_KEY.trim().isEmpty()) {
            url += "&key=" + API_KEY.trim();
        }

        String response = sendGetRequest(url);
        JSONObject json = new JSONObject(response);

        if (json.has("items")) {
            return json.getJSONArray("items");
        }

        return new JSONArray();
    }

    private JSONArray getAnswerComments(long answerId) throws Exception {
        String url = BASE_URL + "/answers/" + answerId + "/comments" +
                "?site=stackoverflow" +
                "&order=desc" +
                "&sort=creation";

        if (API_KEY != null && !API_KEY.trim().isEmpty()) {
            url += "&key=" + API_KEY.trim();
        }

        String response = sendGetRequest(url);
        JSONObject json = new JSONObject(response);

        if (json.has("items")) {
            return json.getJSONArray("items");
        }

        return new JSONArray();
    }

    private boolean isValidJavaQuestion(JSONObject fullQuestion) {
        if (!fullQuestion.has("question")) return false;

        JSONObject question = fullQuestion.getJSONObject("question");

        if (!question.has("tags")) {
            return false;
        }

        JSONArray tags = question.getJSONArray("tags");
        boolean hasJava = false;
        for (int i = 0; i < tags.length(); i++) {
            String tag = tags.getString(i);
            if ("java".equalsIgnoreCase(tag)) {
                hasJava = true;
                break;
            }
        }

        // 检查是否有body
        boolean hasBody = question.has("body") && !question.isNull("body") && question.getString("body").length() > 0;

        return hasJava && hasBody;
    }

    private void saveQuestionToFile(long questionId, JSONObject data) throws IOException {
        String fileName = String.format("%s/question_%08d.json", OUTPUT_DIR, questionId);

        try (FileWriter writer = new FileWriter(fileName)) {
            writer.write(data.toString(4));
        }
    }

    private String sendGetRequest(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/json")
                .header("User-Agent", "Java-Collector/1.0")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        int statusCode = response.statusCode();

        if (statusCode != 200) {
            String errorBody = response.body();
            System.err.println("HTTP错误 " + statusCode + ": " + errorBody);

            // 如果是过滤器错误 尝试不使用过滤器
            if (statusCode == 400 && errorBody.contains("filter")) {
                System.out.println("过滤器错误，尝试无过滤器版本...");
                return "{\"items\":[]}";
            }

            if (statusCode == 429) {
                System.out.println("api限流，等待15秒...");
                Thread.sleep(15000);
                throw new Exception("API限流");
            }

            throw new Exception("HTTP " + statusCode);
        }

        return response.body();
    }

    public static void main(String[] args) {
        try {
            StackOverflowDataCollector collector = new StackOverflowDataCollector();

            int targetCount = 2000;
            if (args.length > 0) {
                try {
                    targetCount = Integer.parseInt(args[0]);
                } catch (NumberFormatException e) {
                    System.out.println("error: " + targetCount);
                }
            }

            collector.collectJavaQuestions(targetCount);

        } catch (Exception e) {
            System.err.println("error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}