package com.example.server_springboot.kb.service.impl;

import com.example.server_springboot.dto.ApiResponse;
import com.example.server_springboot.kb.dto.KnowledgeRagUploadResponse;
import com.example.server_springboot.kb.dto.CreateKnowledgeArticleRequest;
import com.example.server_springboot.kb.service.KnowledgeArticleChunkService;
import com.example.server_springboot.kb.service.KnowledgeArticleService;
import com.example.server_springboot.kb.service.KnowledgeArticleUploadService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class KnowledgeArticleUploadServiceImpl implements KnowledgeArticleUploadService {
  private final KnowledgeArticleService knowledgeArticleService;
  private final KnowledgeArticleChunkService chunkService;
  private final ObjectMapper objectMapper;

  @Override
  public ApiResponse<List<KnowledgeRagUploadResponse>> uploadAndIngest(List<MultipartFile> files, Long userId) {
    if (files == null || files.isEmpty()) {
      return ApiResponse.error("请先选择要上传的文件");
    }
    List<KnowledgeRagUploadResponse> result = new ArrayList<>();
    for (MultipartFile file : files) {
      if (file == null || file.isEmpty()) {
        continue;
      }
      String filename = file.getOriginalFilename();
      String title = StringUtils.hasText(filename) ? filename.replaceFirst("\\.[^.]+$", "") : "未命名文档";
      String contentText;
      try {
        contentText = new String(file.getBytes(), StandardCharsets.UTF_8);
      } catch (Exception ex) {
        return ApiResponse.error("文件读取失败");
      }
      var request = new CreateKnowledgeArticleRequest();
      request.setTitle(title);
      request.setSummary(extractSummary(contentText));
      request.setContent(parseFileContent(file, contentText));
      var created = knowledgeArticleService.createArticle(request, userId);
      if (!created.isSuccess() || created.getData() == null) {
        return ApiResponse.error(created.getMessage());
      }
      Long articleId = created.getData().getArticleId();
      var ingestResult = chunkService.ingestArticle(articleId, userId);
      if (!ingestResult.isSuccess() || ingestResult.getData() == null) {
        return ApiResponse.error("文章已创建，但切片入库失败：" + ingestResult.getMessage());
      }
      Integer chunkCount = (Integer) ingestResult.getData().get("chunkCount");
      result.add(new KnowledgeRagUploadResponse(articleId, title, chunkCount == null ? 0 : chunkCount));
    }
    return ApiResponse.success("上传并入库成功", result);
  }

  private JsonNode parseFileContent(MultipartFile file, String contentText) {
    String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
    try {
      if (filename.endsWith(".json")) {
        return objectMapper.readTree(contentText);
      }
      if (filename.endsWith(".md")) {
        return markdownToTiptap(contentText);
      }
      if (filename.endsWith(".txt")) {
        return plainTextToTiptap(contentText);
      }
      return plainTextToTiptap(contentText);
    } catch (Exception ex) {
      return plainTextToTiptap(contentText);
    }
  }

  private JsonNode markdownToTiptap(String markdown) {
    ObjectNode doc = objectMapper.createObjectNode();
    doc.put("type", "doc");
    ArrayNode content = objectMapper.createArrayNode();

    if (!StringUtils.hasText(markdown)) {
      doc.set("content", content);
      return doc;
    }

    String[] lines = markdown.replace("\r\n", "\n").replace('\r', '\n').split("\n");
    ArrayNode currentBulletItems = null;
    ArrayNode currentOrderedItems = null;
    boolean inBulletList = false;
    boolean inOrderedList = false;

    for (String rawLine : lines) {
      String line = rawLine == null ? "" : rawLine.trim();
      if (!StringUtils.hasText(line)) {
        inBulletList = flushBulletList(content, currentBulletItems, inBulletList);
        currentBulletItems = null;
        inOrderedList = flushOrderedList(content, currentOrderedItems, inOrderedList);
        currentOrderedItems = null;
        continue;
      }

      if (line.startsWith("#")) {
        inBulletList = flushBulletList(content, currentBulletItems, inBulletList);
        currentBulletItems = null;
        inOrderedList = flushOrderedList(content, currentOrderedItems, inOrderedList);
        currentOrderedItems = null;
        int level = 0;
        while (level < line.length() && line.charAt(level) == '#') {
          level++;
        }
        String text = line.substring(level).trim();
        if (StringUtils.hasText(text)) {
          content.add(buildHeadingNode(Math.min(Math.max(level, 1), 6), text));
        }
        continue;
      }

      if (isBulletLine(line)) {
        inOrderedList = flushOrderedList(content, currentOrderedItems, inOrderedList);
        currentOrderedItems = null;
        if (currentBulletItems == null) {
          currentBulletItems = objectMapper.createArrayNode();
        }
        currentBulletItems.add(buildListItemNode(stripBulletPrefix(line)));
        inBulletList = true;
        continue;
      }

      if (isOrderedLine(line)) {
        inBulletList = flushBulletList(content, currentBulletItems, inBulletList);
        currentBulletItems = null;
        if (currentOrderedItems == null) {
          currentOrderedItems = objectMapper.createArrayNode();
        }
        currentOrderedItems.add(buildListItemNode(stripOrderedPrefix(line)));
        inOrderedList = true;
        continue;
      }

      inBulletList = flushBulletList(content, currentBulletItems, inBulletList);
      currentBulletItems = null;
      inOrderedList = flushOrderedList(content, currentOrderedItems, inOrderedList);
      currentOrderedItems = null;
      content.add(buildParagraphNode(line));
    }

    flushBulletList(content, currentBulletItems, inBulletList);
    flushOrderedList(content, currentOrderedItems, inOrderedList);
    doc.set("content", content);
    return doc;
  }

  private JsonNode plainTextToTiptap(String text) {
    ObjectNode doc = objectMapper.createObjectNode();
    doc.put("type", "doc");
    ArrayNode content = objectMapper.createArrayNode();
    if (StringUtils.hasText(text)) {
      for (String paragraph : text.replace("\r\n", "\n").replace('\r', '\n').split("\n\n+")) {
        String line = paragraph.trim();
        if (StringUtils.hasText(line)) {
          content.add(buildParagraphNode(line));
        }
      }
    }
    doc.set("content", content);
    return doc;
  }

  private ObjectNode buildHeadingNode(int level, String text) {
    ObjectNode node = objectMapper.createObjectNode();
    node.put("type", "heading");
    ObjectNode attrs = objectMapper.createObjectNode();
    attrs.put("level", level);
    node.set("attrs", attrs);
    node.set("content", buildTextContent(text));
    return node;
  }

  private ObjectNode buildParagraphNode(String text) {
    ObjectNode node = objectMapper.createObjectNode();
    node.put("type", "paragraph");
    node.set("content", buildTextContent(text));
    return node;
  }

  private ObjectNode buildListItemNode(String text) {
    ObjectNode node = objectMapper.createObjectNode();
    node.put("type", "listItem");
    ArrayNode content = objectMapper.createArrayNode();
    content.add(buildParagraphNode(text));
    node.set("content", content);
    return node;
  }

  private ArrayNode buildTextContent(String text) {
    ArrayNode content = objectMapper.createArrayNode();
    ObjectNode textNode = objectMapper.createObjectNode();
    textNode.put("type", "text");
    textNode.put("text", text);
    content.add(textNode);
    return content;
  }

  private boolean flushBulletList(ArrayNode content, ArrayNode items, boolean active) {
    if (active && items != null && items.size() > 0) {
      ObjectNode list = objectMapper.createObjectNode();
      list.put("type", "bulletList");
      list.set("content", items);
      content.add(list);
    }
    return false;
  }

  private boolean flushOrderedList(ArrayNode content, ArrayNode items, boolean active) {
    if (active && items != null && items.size() > 0) {
      ObjectNode list = objectMapper.createObjectNode();
      list.put("type", "orderedList");
      list.set("content", items);
      content.add(list);
    }
    return false;
  }

  private boolean isBulletLine(String line) {
    return line.startsWith("- ") || line.startsWith("* ") || line.startsWith("+ ");
  }

  private boolean isOrderedLine(String line) {
    return line.matches("^\\d+\\.\\s+.*");
  }

  private String stripBulletPrefix(String line) {
    return line.length() > 2 ? line.substring(2).trim() : line;
  }

  private String stripOrderedPrefix(String line) {
    int dotIndex = line.indexOf('.');
    return dotIndex >= 0 && dotIndex + 1 < line.length() ? line.substring(dotIndex + 1).trim() : line;
  }

  private String extractSummary(String contentText) {
    if (!StringUtils.hasText(contentText)) {
      return null;
    }
    String singleLine = contentText.replaceAll("\\s+", " ").trim();
    return singleLine.length() <= 120 ? singleLine : singleLine.substring(0, 120);
  }
}
