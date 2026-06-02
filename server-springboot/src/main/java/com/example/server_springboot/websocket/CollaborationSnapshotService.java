package com.example.server_springboot.websocket;

import com.example.server_springboot.collabdocument.dto.DocumentResponse;
import com.example.server_springboot.collabdocument.dto.UpdateDocumentRequest;
import com.example.server_springboot.collabdocument.service.DocumentService;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class CollaborationSnapshotService {

  private final DocumentService documentService;

  public CollaborationSnapshotService(DocumentService documentService) {
    this.documentService = documentService;
  }

  public void saveSnapshot(Long docId, String snapshot) {
    if (docId == null || snapshot == null || snapshot.isBlank()) {
      return;
    }

    try {
      DocumentResponse current = documentService.getDocumentMetadataForInternal(docId).getData();
      if (current == null) {
        return;
      }
      UpdateDocumentRequest request = new UpdateDocumentRequest();
      request.setTitle(current.getTitle());
      request.setLatestSnapshot(snapshot);
      documentService.updateDocumentSnapshot(docId, request);
    } catch (Exception ignored) {
      // keep websocket responsive even if persistence temporarily fails
    }
  }

  public Map<String, Object> buildSnapshotPayload(Long docId, DocumentResponse response) {
    return Map.of(
        "docId", docId,
        "snapshot", response == null ? Map.of() : response
    );
  }
}
