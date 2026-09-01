package com.xufg.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

public interface VectorRagService {

    void storeText(String text);

    List<String> findSimilarTexts(String query, int maxResults);

    String storeText4PDF(MultipartFile file) throws IOException;
}
