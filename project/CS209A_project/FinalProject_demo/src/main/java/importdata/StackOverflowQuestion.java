package importdata;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class StackOverflowQuestion {
    private QuestionData question;
    private List<AnswerData> answers;
    private List<CommentData> questionComments;
    private List<CommentData> answerComments;

    public StackOverflowQuestion() {}

    public QuestionData getQuestion() { return question; }
    public void setQuestion(QuestionData question) { this.question = question; }

    public List<AnswerData> getAnswers() { return answers; }
    public void setAnswers(List<AnswerData> answers) { this.answers = answers; }

    @JsonProperty("question_comments")
    public List<CommentData> getQuestionComments() { return questionComments; }
    public void setQuestionComments(List<CommentData> questionComments) {
        this.questionComments = questionComments;
    }

    @JsonProperty("answer_comments")
    public List<CommentData> getAnswerComments() { return answerComments; }
    public void setAnswerComments(List<CommentData> answerComments) {
        this.answerComments = answerComments;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class QuestionData {
        private List<String> tags;
        private Owner owner;
        @JsonProperty("is_answered")
        private boolean isAnswered;
        @JsonProperty("view_count")
        private int viewCount;
        @JsonProperty("answer_count")
        private int answerCount;
        private int score;
        @JsonProperty("last_activity_date")
        private long lastActivityDate;
        @JsonProperty("creation_date")
        private long creationDate;
        @JsonProperty("last_edit_date")
        private Long lastEditDate;
        @JsonProperty("question_id")
        private long questionId;
        @JsonProperty("content_license")
        private String contentLicense;
        private String link;
        private String title;
        private String body;

        public List<String> getTags() { return tags; }
        public void setTags(List<String> tags) { this.tags = tags; }

        public Owner getOwner() { return owner; }
        public void setOwner(Owner owner) { this.owner = owner; }

        public boolean isAnswered() { return isAnswered; }
        public void setAnswered(boolean answered) { isAnswered = answered; }

        public int getViewCount() { return viewCount; }
        public void setViewCount(int viewCount) { this.viewCount = viewCount; }

        public int getAnswerCount() { return answerCount; }
        public void setAnswerCount(int answerCount) { this.answerCount = answerCount; }

        public int getScore() { return score; }
        public void setScore(int score) { this.score = score; }

        public long getLastActivityDate() { return lastActivityDate; }
        public void setLastActivityDate(long lastActivityDate) { this.lastActivityDate = lastActivityDate; }

        public long getCreationDate() { return creationDate; }
        public void setCreationDate(long creationDate) { this.creationDate = creationDate; }

        public Long getLastEditDate() { return lastEditDate; }
        public void setLastEditDate(Long lastEditDate) { this.lastEditDate = lastEditDate; }

        public long getQuestionId() { return questionId; }
        public void setQuestionId(long questionId) { this.questionId = questionId; }

        public String getContentLicense() { return contentLicense; }
        public void setContentLicense(String contentLicense) { this.contentLicense = contentLicense; }

        public String getLink() { return link; }
        public void setLink(String link) { this.link = link; }

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }

        public String getBody() { return body; }
        public void setBody(String body) { this.body = body; }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AnswerData {
        @JsonProperty("answer_id")
        private long answerId;
        private Owner owner;
        @JsonProperty("is_accepted")
        private boolean isAccepted;
        private int score;
        @JsonProperty("creation_date")
        private long creationDate;
        @JsonProperty("last_activity_date")
        private long lastActivityDate;
        @JsonProperty("last_edit_date")
        private Long lastEditDate;
        @JsonProperty("question_id")
        private long questionId;
        @JsonProperty("content_license")
        private String contentLicense;
        private String body;

        public long getAnswerId() { return answerId; }
        public void setAnswerId(long answerId) { this.answerId = answerId; }

        public Owner getOwner() { return owner; }
        public void setOwner(Owner owner) { this.owner = owner; }

        public boolean isAccepted() { return isAccepted; }
        public void setAccepted(boolean accepted) { isAccepted = accepted; }

        public int getScore() { return score; }
        public void setScore(int score) { this.score = score; }

        public long getCreationDate() { return creationDate; }
        public void setCreationDate(long creationDate) { this.creationDate = creationDate; }

        public long getLastActivityDate() { return lastActivityDate; }
        public void setLastActivityDate(long lastActivityDate) { this.lastActivityDate = lastActivityDate; }

        public Long getLastEditDate() { return lastEditDate; }
        public void setLastEditDate(Long lastEditDate) { this.lastEditDate = lastEditDate; }

        public long getQuestionId() { return questionId; }
        public void setQuestionId(long questionId) { this.questionId = questionId; }

        public String getContentLicense() { return contentLicense; }
        public void setContentLicense(String contentLicense) { this.contentLicense = contentLicense; }

        public String getBody() { return body; }
        public void setBody(String body) { this.body = body; }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CommentData {
        @JsonProperty("comment_id")
        private long commentId;
        private Owner owner;
        private boolean edited;
        private int score;
        @JsonProperty("creation_date")
        private long creationDate;
        @JsonProperty("post_id")
        private long postId;
        @JsonProperty("content_license")
        private String contentLicense;
        private String body;

        public long getCommentId() { return commentId; }
        public void setCommentId(long commentId) { this.commentId = commentId; }

        public Owner getOwner() { return owner; }
        public void setOwner(Owner owner) { this.owner = owner; }

        public boolean isEdited() { return edited; }
        public void setEdited(boolean edited) { this.edited = edited; }

        public int getScore() { return score; }
        public void setScore(int score) { this.score = score; }

        public long getCreationDate() { return creationDate; }
        public void setCreationDate(long creationDate) { this.creationDate = creationDate; }

        public long getPostId() { return postId; }
        public void setPostId(long postId) { this.postId = postId; }

        public String getContentLicense() { return contentLicense; }
        public void setContentLicense(String contentLicense) { this.contentLicense = contentLicense; }

        public String getBody() { return body; }
        public void setBody(String body) { this.body = body; }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Owner {
        @JsonProperty("account_id")
        private long accountId;
        private int reputation;
        @JsonProperty("user_id")
        private long userId;
        @JsonProperty("user_type")
        private String userType;
        @JsonProperty("profile_image")
        private String profileImage;
        @JsonProperty("display_name")
        private String displayName;
        private String link;

        public long getAccountId() { return accountId; }
        public void setAccountId(long accountId) { this.accountId = accountId; }

        public int getReputation() { return reputation; }
        public void setReputation(int reputation) { this.reputation = reputation; }

        public long getUserId() { return userId; }
        public void setUserId(long userId) { this.userId = userId; }

        public String getUserType() { return userType; }
        public void setUserType(String userType) { this.userType = userType; }

        public String getProfileImage() { return profileImage; }
        public void setProfileImage(String profileImage) { this.profileImage = profileImage; }

        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }

        public String getLink() { return link; }
        public void setLink(String link) { this.link = link; }
    }
}