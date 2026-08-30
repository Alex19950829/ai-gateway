package com.chatling.common.rag;

import java.io.Serializable;
import java.util.List;

public class KnowledgeDoc implements Serializable {
    private String docId;
    private String title;
    private String content;
    private String category;
    private List<String> tags;

    public KnowledgeDoc() {}

    public KnowledgeDoc(String docId, String title, String content, String category, List<String> tags) {
        this.docId = docId;
        this.title = title;
        this.content = content;
        this.category = category;
        this.tags = tags;
    }

    public String getDocId() { return docId; }
    public void setDocId(String docId) { this.docId = docId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }
}
