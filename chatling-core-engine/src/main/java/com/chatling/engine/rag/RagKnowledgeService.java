package com.chatling.engine.rag;

import com.chatling.common.dto.OpenAiDto;
import com.chatling.common.rag.KnowledgeDoc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class RagKnowledgeService {
    private static final Logger log = LoggerFactory.getLogger(RagKnowledgeService.class);

    private final Map<String, KnowledgeDoc> knowledgeBase = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        // 预置 58 集团/企业内部规章与知识库切片数据
        addDoc(new KnowledgeDoc("doc-001", "2026年员工休假管理制度", "58集团全员每年享有法定年假及5天司龄带薪年假，婚假为10个工作日，产假与育儿假按各城市最新法案执行。", "HR制度", Arrays.asList("年假", "休假", "福利", "请假")));
        addDoc(new KnowledgeDoc("doc-002", "灵犀 AI 网关架构与限流规范", "灵犀 AI 网关核心基于 Envoy + Java + Groovy 双引擎，支持按租户专属 QPM/TPM 限流，配额用尽返回 429 Too Many Requests。", "技术规范", Arrays.asList("网关", "限流", "QPM", "TPM", "架构")));
        addDoc(new KnowledgeDoc("doc-003", "房产经纪人发帖审核标准", "房产发帖严禁虚假房源与重复铺量，一房一码真实房源优先推荐，租金与售价必须与实地一致。", "业务规则", Arrays.asList("房产", "发帖", "审核", "虚假房源")));
    }

    public void addDoc(KnowledgeDoc doc) {
        knowledgeBase.put(doc.getDocId(), doc);
    }

    public Collection<KnowledgeDoc> getAllDocs() {
        return knowledgeBase.values();
    }

    public void deleteDoc(String docId) {
        knowledgeBase.remove(docId);
    }

    /**
     * 基于关键词与语义相关度检索知识库 Top-K
     */
    public List<KnowledgeDoc> searchTopK(String query, int topK) {
        if (query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }
        String lowerQuery = query.toLowerCase();

        return knowledgeBase.values().stream()
                .map(doc -> {
                    int score = 0;
                    if (doc.getTitle() != null && lowerQuery.contains(doc.getTitle().toLowerCase())) score += 10;
                    if (doc.getTags() != null) {
                        for (String tag : doc.getTags()) {
                            if (lowerQuery.contains(tag.toLowerCase())) score += 5;
                        }
                    }
                    if (doc.getContent() != null) {
                        // 简易词频重合度计分
                        String[] words = doc.getContent().split("[,，.。;；\\s]+");
                        for (String w : words) {
                            if (w.length() >= 2 && lowerQuery.contains(w.toLowerCase())) {
                                score += 2;
                            }
                        }
                    }
                    return new AbstractMap.SimpleEntry<>(doc, score);
                })
                .filter(entry -> entry.getValue() > 0)
                .sorted((e1, e2) -> Integer.compare(e2.getValue(), e1.getValue()))
                .limit(topK)
                .map(AbstractMap.SimpleEntry::getKey)
                .collect(Collectors.toList());
    }

    /**
     * RAG 知识检索上下文自动装饰：
     * 如果命中知识库，自动在 Prompt 首部动态注入背景知识
     */
    public List<OpenAiDto.ChatMessage> augmentMessagesWithKnowledge(List<OpenAiDto.ChatMessage> messages, String userPrompt) {
        List<KnowledgeDoc> docs = searchTopK(userPrompt, 2);
        if (docs.isEmpty()) {
            return messages;
        }

        StringBuilder contextBuilder = new StringBuilder();
        contextBuilder.append("【以下是来自企业内部知识库检索到的参考知识切片，请优先结合以下事实准确回答用户的问题】：\n");
        for (int i = 0; i < docs.size(); i++) {
            KnowledgeDoc doc = docs.get(i);
            contextBuilder.append(String.format(">> [参考资料 %d - 《%s》]: %s\n", i + 1, doc.getTitle(), doc.getContent()));
        }
        contextBuilder.append("【请基于以上背景给出清晰专业的回答】：\n");

        List<OpenAiDto.ChatMessage> augmented = new ArrayList<>();
        // 注入 System 知识上下文
        augmented.add(OpenAiDto.ChatMessage.builder()
                .role("system")
                .content(contextBuilder.toString())
                .build());
        augmented.addAll(messages);
        log.info("RAG 知识库检索增强成功: 命中 {} 篇文档切片注入上下文", docs.size());
        return augmented;
    }
}
