package com.cugcoding.forum.model;

import java.util.List;

public class PostDetail extends Post {
    private String authorName;
    private List<CommentView> comments;
    private Boolean liked;

    public String getAuthorName() { return authorName; }
    public void setAuthorName(String authorName) { this.authorName = authorName; }
    public List<CommentView> getComments() { return comments; }
    public void setComments(List<CommentView> comments) { this.comments = comments; }
    public Boolean getLiked() { return liked; }
    public void setLiked(Boolean liked) { this.liked = liked; }
}
