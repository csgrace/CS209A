package importdata;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.file.Files;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;

public class InsertDatabase {
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/";
    private static final String DB_NAME = "cs209a_grace";
    //cs209_final
    private static final String FULL_DB_URL = "jdbc:postgresql://localhost:5432/" + DB_NAME;
    private static final String DB_USER = "postgres";
    private static final String DB_PASSWORD = "huarui66";
    // post2053
    // huarui66
    private static final String DATA_DIR = "D:\\E\\SUSTECH\\grade3\\3_up\\CS\\CS209A\\project\\CS209A_project\\data_1000_new";
    // C:\GitHub\CS209A_project\data_1000_new"
    // D:\E\SUSTECH\grade3\3_up\CS\CS209A\project\CS209A_project\data_1000_new

    private Connection connection;
    private Map<Long, Boolean> existingUsers = new HashMap<>();
    private Map<Long, Boolean> existingQuestions = new HashMap<>();
    private Map<Long, Boolean> existingAnswers = new HashMap<>();


    public InsertDatabase() throws SQLException {
        Connection totalcon = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
        createdatabase(totalcon);
        totalcon.close();
        this.connection = DriverManager.getConnection(FULL_DB_URL, DB_USER, DB_PASSWORD);
        System.out.println("successfully connect: " + FULL_DB_URL);
    }

    public void createdatabase(Connection connection) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            String createDbSql = "CREATE DATABASE " + DB_NAME +
                    " WITH ENCODING = 'UTF8' " +
                    "LC_COLLATE = 'en_US.UTF-8' " +
                    "LC_CTYPE = 'en_US.UTF-8' " +
                    "TEMPLATE = template0";

            try {
                stmt.executeUpdate(createDbSql);
                System.out.println("数据库创建成功: " + DB_NAME);
            } catch (SQLException e) {
                if (e.getMessage().contains("already exists")) {
                    System.out.println("数据库已存在，跳过创建");
                } else {
                    throw e;
                }
            }
        }

        try (Connection dbConn = DriverManager.getConnection(FULL_DB_URL, DB_USER, DB_PASSWORD);
             Statement stmt = dbConn.createStatement()) {

            String createUsersTable = "CREATE TABLE IF NOT EXISTS users (" +
                    "user_id BIGINT PRIMARY KEY," +
                    "account_id BIGINT," +
                    "reputation INTEGER," +
                    "user_type VARCHAR(50)," +
                    "profile_image TEXT," +
                    "display_name VARCHAR(255)," +
                    "link TEXT," +
                    "accept_rate INTEGER" +
                    ")";
            stmt.executeUpdate(createUsersTable);
            System.out.println("users表创建成功");

            String createQuestionsTable = "CREATE TABLE IF NOT EXISTS questions (" +
                    "question_id BIGINT PRIMARY KEY," +
                    "user_id BIGINT REFERENCES users(user_id)," +
                    "title TEXT," +
                    "body TEXT," +
                    "view_count INTEGER DEFAULT 0," +
                    "answer_count INTEGER DEFAULT 0," +
                    "score INTEGER DEFAULT 0," +
                    "is_answered BOOLEAN DEFAULT FALSE," +
                    "creation_date BIGINT NOT NULL," +
                    "last_activity_date BIGINT," +
                    "content_license VARCHAR(50)," +
                    "link TEXT" +
                    ")";
            stmt.executeUpdate(createQuestionsTable);
            System.out.println("questions表创建成功");

            String createAnswersTable = "CREATE TABLE IF NOT EXISTS answers (" +
                    "answer_id BIGINT PRIMARY KEY," +
                    "question_id BIGINT REFERENCES questions(question_id)," +
                    "user_id BIGINT REFERENCES users(user_id)," +
                    "body TEXT," +
                    "score INTEGER DEFAULT 0," +
                    "is_accepted BOOLEAN DEFAULT FALSE," +
                    "creation_date BIGINT NOT NULL," +
                    "last_activity_date BIGINT," +
                    "content_license VARCHAR(50)" +
                    ")";
            stmt.executeUpdate(createAnswersTable);
            System.out.println("answers表创建成功");

            String createQuestionCommentsTable = "CREATE TABLE IF NOT EXISTS question_comments (" +
                    "comment_id BIGINT PRIMARY KEY," +
                    "question_id BIGINT REFERENCES questions(question_id)," +
                    "score INTEGER DEFAULT 0," +
                    "edited BOOLEAN DEFAULT FALSE," +
                    "creation_date BIGINT NOT NULL," +
                    "content_license VARCHAR(50)," +
                    "post_id BIGINT," +
                    "owner_id BIGINT REFERENCES users(user_id)" +
                    ")";
            stmt.executeUpdate(createQuestionCommentsTable);
            System.out.println("question_comments表创建成功");

            String createAnswerCommentsTable = "CREATE TABLE IF NOT EXISTS answer_comments (" +
                    "comment_id BIGINT PRIMARY KEY," +
                    "answer_id BIGINT REFERENCES answers(answer_id)," +
                    "score INTEGER DEFAULT 0," +
                    "edited BOOLEAN DEFAULT FALSE," +
                    "creation_date BIGINT NOT NULL," +
                    "content_license VARCHAR(50)," +
                    "owner_id BIGINT REFERENCES users(user_id)," +
                    "reply_to_user_id BIGINT REFERENCES users(user_id)," +
                    "post_id BIGINT" +
                    ")";
            stmt.executeUpdate(createAnswerCommentsTable);
            System.out.println("answer_comments表创建成功");

            // 7. 创建问题标签表
            String createQuestionTagsTable = "CREATE TABLE IF NOT EXISTS question_tags (" +
                    "question_id BIGINT REFERENCES questions(question_id)," +
                    "tag VARCHAR(50)" +
                    ")";
            stmt.executeUpdate(createQuestionTagsTable);
            System.out.println("tags表创建成功");

            System.out.println("所有数据库表创建完成");

        } catch (SQLException e) {
            System.err.println("error: " + e.getMessage());
            throw e;
        }
    }

    public void importAllData() throws Exception {
        File dir = new File(DATA_DIR);
        File[] files = dir.listFiles();

        for (File file : files) {
            try{
                String jsonContent = new String(Files.readAllBytes(file.toPath()));
                JSONObject data = new JSONObject(jsonContent);

                startimport(data);
            }catch (Exception e) {
                e.printStackTrace();
            }
        }
    }



    private void startimport(JSONObject data) throws Exception {
        connection.setAutoCommit(false);

        try{
            JSONObject question = data.getJSONObject("question");

            JSONObject user = question.getJSONObject("owner");
            long userId = user.getLong("user_id");
            importUser(user);

            long questionId = question.getLong("question_id");
            importQuestion(question, userId);

            if (question.has("tags")) {
                JSONArray tags = question.getJSONArray("tags");
                importQuestionTags(questionId, tags);
            }

            if (data.has("question_comments")) {
                JSONArray questionComments = data.getJSONArray("question_comments");
                for (int i = 0; i < questionComments.length(); i++) {
                    JSONObject comment = questionComments.getJSONObject(i);
                    importQuestionComment(comment, questionId);
                }
            }

            if (data.has("answers")) {
                JSONArray answers = data.getJSONArray("answers");
                for (int i = 0; i < answers.length(); i++) {
                    JSONObject answer = answers.getJSONObject(i);
                    importAnswer(answer, questionId);
                }
            }

            if (data.has("answer_comments")) {
                JSONObject answerComments = data.getJSONObject("answer_comments");
                importAnswerComments(answerComments);
            }

            connection.commit();





        }catch (Exception e) {
            connection.rollback();
        } finally {
            connection.setAutoCommit(true);
        }
    }

    public void importUser(JSONObject user) throws Exception {
        long userId = user.getLong("user_id");
        if (existingUsers.containsKey(userId)) {
            return;
        }

        String sql = "INSERT INTO users (user_id, account_id, reputation, user_type, " +
                "profile_image, display_name, link, accept_rate) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT (user_id) DO NOTHING";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            stmt.setLong(2, user.getLong("account_id"));
            stmt.setInt(3, user.getInt("reputation"));
            stmt.setString(4, user.getString("user_type"));
            stmt.setString(5, user.optString("profile_image", null));
            stmt.setString(6, user.getString("display_name"));
            stmt.setString(7, user.optString("link", null));

            if (user.has("accept_rate")) {
                stmt.setInt(8, user.getInt("accept_rate"));
            } else {
                stmt.setNull(8, Types.INTEGER);
            }

            stmt.executeUpdate();
            existingUsers.put(userId, true);
        }
    }

    private void importQuestion(JSONObject question, long userId) throws SQLException {
        long questionId = question.getLong("question_id");

        if (existingQuestions.containsKey(questionId)) {
            return;
        }

        String sql = "INSERT INTO questions (question_id, user_id, title, body, view_count, " +
                "answer_count, score, is_answered, creation_date, last_activity_date, " +
                "content_license, link) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT (question_id) DO NOTHING";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, questionId);
            stmt.setLong(2, userId);
            stmt.setString(3, question.getString("title"));
            if (question.has("body") && !question.isNull("body")) {
                stmt.setString(4, question.getString("body"));
            } else {
                stmt.setNull(4, Types.VARCHAR);
            }
            stmt.setInt(5, question.getInt("view_count"));
            stmt.setInt(6, question.getInt("answer_count"));
            stmt.setInt(7, question.getInt("score"));
            stmt.setBoolean(8, question.getBoolean("is_answered"));
            stmt.setLong(9, question.getLong("creation_date"));
            stmt.setLong(10, question.getLong("last_activity_date"));
            if (question.has("content_license") && !question.isNull("content_license")) {
                stmt.setString(11, question.getString("content_license"));
            } else {
                stmt.setNull(11, Types.VARCHAR);
            }
            stmt.setString(12, question.getString("link"));

            stmt.executeUpdate();
            existingQuestions.put(questionId, true);
        }
    }

    private void importQuestionTags(long questionId, JSONArray tags) throws SQLException {
        String sql = "INSERT INTO question_tags (question_id, tag) VALUES (?, ?) " +
                "ON CONFLICT DO NOTHING";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            for (int i = 0; i < tags.length(); i++) {
                String tag = tags.getString(i);
                stmt.setLong(1, questionId);
                stmt.setString(2, tag);
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
    }

    private void importQuestionComment(JSONObject comment, long questionId) throws Exception {
        if (comment.has("owner")) {
            importUser(comment.getJSONObject("owner"));
        }

        String sql = "INSERT INTO question_comments (comment_id, question_id, score, " +
                "edited, creation_date, content_license, post_id, owner_id) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT (comment_id) DO NOTHING";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, comment.getLong("comment_id"));
            stmt.setLong(2, questionId);
            stmt.setInt(3, comment.getInt("score"));
            stmt.setBoolean(4, comment.getBoolean("edited"));
            stmt.setLong(5, comment.getLong("creation_date"));
            if (comment.has("content_license") && !comment.isNull("content_license")) {
                stmt.setString(6, comment.getString("content_license"));
            } else {
                stmt.setNull(6, Types.VARCHAR);
            }
            if (comment.has("post_id") && !comment.isNull("post_id")) {
                stmt.setLong(7, comment.getLong("post_id"));
            } else {
                stmt.setLong(7, questionId); // 如果没有post_id，使用question_id
            }
            if (comment.has("owner")) {
                stmt.setLong(8, comment.getJSONObject("owner").getLong("user_id"));
            } else {
                stmt.setNull(8, Types.BIGINT);
            }

            stmt.executeUpdate();
        }
    }

    private void importAnswer(JSONObject answer, long questionId) throws Exception {
        long answerId = answer.getLong("answer_id");

        if (existingAnswers.containsKey(answerId)) {
            return;
        }

        if (answer.has("owner")) {
            importUser(answer.getJSONObject("owner"));
        }

        String sql = "INSERT INTO answers (answer_id, question_id, user_id, body, score, is_accepted, " +
                "creation_date, last_activity_date, content_license) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT (answer_id) DO NOTHING";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, answerId);
            stmt.setLong(2, questionId);

            if (answer.has("owner")) {
                stmt.setLong(3, answer.getJSONObject("owner").getLong("user_id"));
            } else {
                stmt.setNull(3, Types.BIGINT);
            }

            if (answer.has("body") && !answer.isNull("body")) {
                stmt.setString(4, answer.getString("body"));
            } else {
                stmt.setNull(4, Types.VARCHAR);
            }

            stmt.setInt(5, answer.getInt("score"));
            stmt.setBoolean(6, answer.getBoolean("is_accepted"));
            stmt.setLong(7, answer.getLong("creation_date"));

            if (answer.has("last_activity_date") && !answer.isNull("last_activity_date")) {
                stmt.setLong(8, answer.getLong("last_activity_date"));
            } else {
                stmt.setLong(8, answer.getLong("creation_date"));
            }

            if (answer.has("content_license") && !answer.isNull("content_license")) {
                stmt.setString(9, answer.getString("content_license"));
            } else {
                stmt.setNull(9, Types.VARCHAR);
            }

            stmt.executeUpdate();
            existingAnswers.put(answerId, true);
        }
    }

    private void importAnswerComments(JSONObject answerComments) throws Exception {
        for (String answerIdStr : answerComments.keySet()) {
            long answerId = Long.parseLong(answerIdStr);
            JSONArray comments = answerComments.getJSONArray(answerIdStr);

            for (int i = 0; i < comments.length(); i++) {
                JSONObject comment = comments.getJSONObject(i);
                importAnswerComment(comment, answerId);
            }
        }
    }

    private void importAnswerComment(JSONObject comment, long answerId) throws Exception {
        if (comment.has("owner")) {
            importUser(comment.getJSONObject("owner"));
        }

        if (comment.has("reply_to_user")) {
            importUser(comment.getJSONObject("reply_to_user"));
        }

        String sql = "INSERT INTO answer_comments (comment_id, answer_id, score, " +
                "edited, creation_date, content_license, owner_id, " +
                "reply_to_user_id, post_id) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT (comment_id) DO NOTHING";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, comment.getLong("comment_id"));
            stmt.setLong(2, answerId);
            stmt.setInt(3, comment.getInt("score"));
            stmt.setBoolean(4, comment.getBoolean("edited"));
            stmt.setLong(5, comment.getLong("creation_date"));

            if (comment.has("content_license") && !comment.isNull("content_license")) {
                stmt.setString(6, comment.getString("content_license"));
            } else {
                stmt.setNull(6, Types.VARCHAR);
            }

            if (comment.has("owner")) {
                stmt.setLong(7, comment.getJSONObject("owner").getLong("user_id"));
            } else {
                stmt.setNull(7, Types.BIGINT);
            }

            if (comment.has("reply_to_user")) {
                stmt.setLong(8, comment.getJSONObject("reply_to_user").getLong("user_id"));
            } else {
                stmt.setNull(8, Types.BIGINT);
            }

            if (comment.has("post_id") && !comment.isNull("post_id")) {
                stmt.setLong(9, comment.getLong("post_id"));
            } else {
                stmt.setLong(9, answerId);
            }

            stmt.executeUpdate();
        }
    }

    public static void main(String[] args) {
        InsertDatabase insert = null;
        try{
            insert = new InsertDatabase();

            insert.importAllData();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
