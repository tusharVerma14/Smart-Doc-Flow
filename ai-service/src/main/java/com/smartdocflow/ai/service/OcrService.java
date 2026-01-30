package com.smartdocflow.ai.service;

import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

@Service
@Slf4j
public class OcrService {

    private final Tesseract tesseract;

    public OcrService() {
        tesseract = new Tesseract();

        String dataPath = System.getenv("TESSDATA_PREFIX");
        if (dataPath != null) {
            tesseract.setDatapath(dataPath);
        }

        tesseract.setLanguage("eng");
        tesseract.setPageSegMode(1);
        tesseract.setOcrEngineMode(1);
    }

    public String extractText(File file) {
        String name = file.getName().toLowerCase();

        if (name.endsWith(".pdf")) {
            return extractTextFromPdf(file);
        } else if (name.endsWith(".jpg") || name.endsWith(".jpeg")
                || name.endsWith(".png") || name.endsWith(".tiff")
                || name.endsWith(".bmp")) {
            return extractTextFromImage(file);
        } else if (name.endsWith(".txt")) {
            return extractTextFromTextFile(file);
        }

        throw new IllegalArgumentException("Unsupported file format: " + name);
    }

    private String extractTextFromImage(File imageFile) {
        try {
            String text = tesseract.doOCR(imageFile);
            log.info("OCR image extracted {} chars", text.length());
            return text;
        } catch (TesseractException e) {
            throw new RuntimeException("OCR failed for image", e);
        }
    }

    private String extractTextFromPdf(File pdfFile) {
        try (PDDocument document = Loader.loadPDF(pdfFile)) {
            PDFRenderer renderer = new PDFRenderer(document);
            StringBuilder text = new StringBuilder();

            for (int i = 0; i < document.getNumberOfPages(); i++) {
                BufferedImage image = renderer.renderImageWithDPI(i, 300);
                text.append(tesseract.doOCR(image)).append("\n");
            }

            log.info("OCR PDF extracted {} chars", text.length());
            return text.toString();

        } catch (IOException | TesseractException e) {
            throw new RuntimeException("OCR failed for PDF", e);
        }
    }

    private String extractTextFromTextFile(File file) {
        try {
            return java.nio.file.Files.readString(file.toPath());
        } catch (IOException e) {
            throw new RuntimeException("Failed to read text file", e);
        }
    }
}
