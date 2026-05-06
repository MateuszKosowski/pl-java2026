package pl.zzpj.watermark_service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Iterator;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false",
        "watermark.app-key=test-watermark-app-key",
        "spring.profiles.active="
})
@AutoConfigureMockMvc(addFilters = false)
class ApplicationTests {

    private static final String OWNER_ID = "user-123";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void embedsDetectsAndExtractsMessageFromGeneratedImage() throws Exception {
        byte[] sourceImage = createTestImage();

        byte[] embeddedImage = mockMvc.perform(multipart("/api/watermark/embed")
                        .file(new MockMultipartFile("image", "source.png", MediaType.IMAGE_PNG_VALUE, sourceImage))
                        .param("text", "Hello StegoCloud")
                        .param("ownerIdentityentity", OWNER_ID))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        BufferedImage embedded = ImageIO.read(new ByteArrayInputStream(embeddedImage));
        assertNotNull(embedded);
        assertEquals(512, embedded.getWidth());
        assertEquals(512, embedded.getHeight());
        assertTrue(containsColorPixels(embedded), "Embedded image should preserve RGB color information");

        mockMvc.perform(multipart("/api/watermark/detect")
                        .file(new MockMultipartFile("image", "embedded.png", MediaType.IMAGE_PNG_VALUE, embeddedImage)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.watermarked").value(true))
                .andExpect(jsonPath("$.ownerIdentityentity").value(OWNER_ID))
                .andExpect(jsonPath("$.version").value(3));

        mockMvc.perform(multipart("/api/watermark/extract")
                        .file(new MockMultipartFile("image", "embedded.png", MediaType.IMAGE_PNG_VALUE, embeddedImage))
                        .param("requesterId", OWNER_ID))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.ownerIdentityentity").value(OWNER_ID))
                .andExpect(jsonPath("$.text").value("Hello StegoCloud"));
    }

    @Test
    void rejectsExtractionForDifferentRequester() throws Exception {
        byte[] sourceImage = createTestImage();

        byte[] embeddedImage = mockMvc.perform(multipart("/api/watermark/embed")
                        .file(new MockMultipartFile("image", "source.png", MediaType.IMAGE_PNG_VALUE, sourceImage))
                        .param("text", "Hello StegoCloud")
                        .param("ownerIdentityentity", OWNER_ID))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        mockMvc.perform(multipart("/api/watermark/extract")
                        .file(new MockMultipartFile("image", "embedded.png", MediaType.IMAGE_PNG_VALUE, embeddedImage))
                        .param("requesterId", "user-999"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void returnsFalseForImageWithoutApplicationWatermark() throws Exception {
        byte[] sourceImage = createTestImage();

        mockMvc.perform(multipart("/api/watermark/detect")
                        .file(new MockMultipartFile("image", "plain.png", MediaType.IMAGE_PNG_VALUE, sourceImage)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.watermarked").value(false))
                .andExpect(jsonPath("$.ownerIdentityentity").doesNotExist())
                .andExpect(jsonPath("$.version").doesNotExist());
    }

    @Test
    void embedsAndExtractsImageWithOddDimensions() throws Exception {
        byte[] oddImage = createTestImage(513, 513);

        byte[] embeddedImage = mockMvc.perform(multipart("/api/watermark/embed")
                        .file(new MockMultipartFile("image", "odd.png", MediaType.IMAGE_PNG_VALUE, oddImage))
                        .param("text", "Hello StegoCloud")
                        .param("ownerIdentityentity", OWNER_ID))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        BufferedImage embedded = ImageIO.read(new ByteArrayInputStream(embeddedImage));
        assertNotNull(embedded);
        assertEquals(513, embedded.getWidth());
        assertEquals(513, embedded.getHeight());

        mockMvc.perform(multipart("/api/watermark/extract")
                        .file(new MockMultipartFile("image", "odd-embedded.png", MediaType.IMAGE_PNG_VALUE, embeddedImage))
                        .param("requesterId", OWNER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("Hello StegoCloud"));
    }

    @Test
    void rejectsImagesWithoutEnoughCapacity() throws Exception {
        byte[] smallImage = createTestImage(64, 64);

        mockMvc.perform(multipart("/api/watermark/embed")
                        .file(new MockMultipartFile("image", "small.png", MediaType.IMAGE_PNG_VALUE, smallImage))
                        .param("text", "Hello StegoCloud")
                        .param("ownerIdentityentity", OWNER_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("Message is too long for this image")));
    }

    @Test
    void exposesOpenApiDocumentation() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$['paths']['/api/watermark/embed']").exists())
                .andExpect(jsonPath("$['paths']['/api/watermark/detect']").exists())
                .andExpect(jsonPath("$['paths']['/api/watermark/extract']").exists())
                .andExpect(jsonPath("$['paths']['/api/watermark/visualize']").exists());
    }

    @ParameterizedTest(name = "watermark survives JPEG quality {0}%")
    @ValueSource(floats = {0.95f, 0.90f, 0.85f, 0.80f, 0.75f, 0.70f, 0.65f, 0.60f, 0.55f, 0.50f, 0.45f, 0.40f, 0.35f, 0.30f})
    void watermarkSurvivesJpegCompression(float quality) throws Exception {
        byte[] sourceImage = createTestImage(512, 512);

        byte[] embeddedPng = mockMvc.perform(multipart("/api/watermark/embed")
                        .file(new MockMultipartFile("image", "source.png", MediaType.IMAGE_PNG_VALUE, sourceImage))
                        .param("text", "JPEG test")
                        .param("ownerIdentityentity", OWNER_ID))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        byte[] jpegBytes = convertToJpeg(embeddedPng, quality);

        mockMvc.perform(multipart("/api/watermark/detect")
                        .file(new MockMultipartFile("image", "compressed.jpg", MediaType.IMAGE_JPEG_VALUE, jpegBytes)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.watermarked").value(true))
                .andExpect(jsonPath("$.ownerIdentityentity").value(OWNER_ID));

        mockMvc.perform(multipart("/api/watermark/extract")
                        .file(new MockMultipartFile("image", "compressed.jpg", MediaType.IMAGE_JPEG_VALUE, jpegBytes))
                        .param("requesterId", OWNER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("JPEG test"));
    }

    @ParameterizedTest(name = "watermark survives JPEG Q75 on {0}x{1} image")
    @CsvSource({"512,512", "640,480", "800,600", "1024,768", "1920,1080"})
    void watermarkSurvivesJpegCompressionAtDifferentSizes(int width, int height) throws Exception {
        byte[] sourceImage = createTestImage(width, height);

        byte[] embeddedPng = mockMvc.perform(multipart("/api/watermark/embed")
                        .file(new MockMultipartFile("image", "source.png", MediaType.IMAGE_PNG_VALUE, sourceImage))
                        .param("text", "Size test")
                        .param("ownerIdentityentity", OWNER_ID))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        byte[] jpegBytes = convertToJpeg(embeddedPng, 0.75f);

        mockMvc.perform(multipart("/api/watermark/detect")
                        .file(new MockMultipartFile("image", "compressed.jpg", MediaType.IMAGE_JPEG_VALUE, jpegBytes)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.watermarked").value(true))
                .andExpect(jsonPath("$.ownerIdentityentity").value(OWNER_ID));

        mockMvc.perform(multipart("/api/watermark/extract")
                        .file(new MockMultipartFile("image", "compressed.jpg", MediaType.IMAGE_JPEG_VALUE, jpegBytes))
                        .param("requesterId", OWNER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("Size test"));
    }

    @Test
    void visualizeHighlightsBlocksInWatermarkedImage() throws Exception {
        byte[] sourceImage = createTestImage(512, 512);

        byte[] embeddedImage = mockMvc.perform(multipart("/api/watermark/embed")
                        .file(new MockMultipartFile("image", "source.png", MediaType.IMAGE_PNG_VALUE, sourceImage))
                        .param("text", "Visualize test")
                        .param("ownerIdentityentity", OWNER_ID))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        byte[] visualization = mockMvc.perform(multipart("/api/watermark/visualize")
                        .file(new MockMultipartFile("image", "embedded.png", MediaType.IMAGE_PNG_VALUE, embeddedImage)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        BufferedImage vizImage = ImageIO.read(new ByteArrayInputStream(visualization));
        assertNotNull(vizImage);

        int redPixels = 0;
        for (int y = 0; y < vizImage.getHeight(); y++) {
            for (int x = 0; x < vizImage.getWidth(); x++) {
                Color color = new Color(vizImage.getRGB(x, y));
                if (color.getRed() > color.getGreen() + 30 && color.getRed() > color.getBlue() + 30) {
                    redPixels++;
                }
            }
        }
        assertTrue(redPixels > 100, "Visualization should contain red-highlighted watermark blocks, found " + redPixels + " red pixels");
    }

    @Test
    void visualizeReturnsUnhighlightedImageWhenNoWatermark() throws Exception {
        byte[] sourceImage = createTestImage(512, 512);

        byte[] visualization = mockMvc.perform(multipart("/api/watermark/visualize")
                        .file(new MockMultipartFile("image", "plain.png", MediaType.IMAGE_PNG_VALUE, sourceImage)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        BufferedImage vizImage = ImageIO.read(new ByteArrayInputStream(visualization));
        assertNotNull(vizImage);
    }

    @ParameterizedTest(name = "watermark survives double JPEG compression Q{0} -> Q{1}")
    @CsvSource({"0.85,0.75", "0.75,0.75", "0.90,0.60"})
    void watermarkSurvivesDoubleJpegCompression(float quality1, float quality2) throws Exception {
        byte[] sourceImage = createTestImage(512, 512);

        byte[] embeddedPng = mockMvc.perform(multipart("/api/watermark/embed")
                        .file(new MockMultipartFile("image", "source.png", MediaType.IMAGE_PNG_VALUE, sourceImage))
                        .param("text", "Double JPEG")
                        .param("ownerIdentityentity", OWNER_ID))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        byte[] jpeg1 = convertToJpeg(embeddedPng, quality1);
        byte[] jpeg2 = convertToJpeg(jpeg1, quality2);

        mockMvc.perform(multipart("/api/watermark/detect")
                        .file(new MockMultipartFile("image", "compressed.jpg", MediaType.IMAGE_JPEG_VALUE, jpeg2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.watermarked").value(true))
                .andExpect(jsonPath("$.ownerIdentityentity").value(OWNER_ID));

        mockMvc.perform(multipart("/api/watermark/extract")
                        .file(new MockMultipartFile("image", "compressed.jpg", MediaType.IMAGE_JPEG_VALUE, jpeg2))
                        .param("requesterId", OWNER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("Double JPEG"));
    }

    @Test
    void stressTestWith100ImagesAnd20UsersAfterJpegCompression() throws Exception {
        long seed = System.nanoTime();
        Random random = new Random(seed);
        System.out.println("Stress test seed: " + seed);

        int imageCount = 100;
        int userCount = 20;

        String[] users = new String[userCount];
        for (int i = 0; i < userCount; i++) {
            users[i] = "user-" + (i + 1);
        }

        int[] imageSizes = {512, 640, 768, 800, 1024};
        float[] jpegQualities = {0.30f, 0.40f, 0.50f, 0.60f, 0.70f, 0.75f, 0.80f, 0.90f};

        int successCount = 0;

        for (int i = 0; i < imageCount; i++) {
            String ownerIdentityentity = users[random.nextInt(userCount)];
            int size = imageSizes[random.nextInt(imageSizes.length)];
            float quality = jpegQualities[random.nextInt(jpegQualities.length)];
            String text = "img-" + i + "-owner-" + ownerIdentityentity;

            byte[] sourceImage = createTestImage(size, size);

            byte[] embeddedPng = mockMvc.perform(multipart("/api/watermark/embed")
                            .file(new MockMultipartFile("image", "img" + i + ".png", MediaType.IMAGE_PNG_VALUE, sourceImage))
                            .param("text", text)
                            .param("ownerIdentityentity", ownerIdentityentity))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getContentAsByteArray();

            byte[] jpeg = convertToJpeg(embeddedPng, quality);

            String detectResponse = mockMvc.perform(multipart("/api/watermark/detect")
                            .file(new MockMultipartFile("image", "img" + i + ".jpg", MediaType.IMAGE_JPEG_VALUE, jpeg)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.watermarked").value(true))
                    .andExpect(jsonPath("$.ownerIdentityentity").value(ownerIdentityentity))
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            String extractResponse = mockMvc.perform(multipart("/api/watermark/extract")
                            .file(new MockMultipartFile("image", "img" + i + ".jpg", MediaType.IMAGE_JPEG_VALUE, jpeg))
                            .param("requesterId", ownerIdentityentity))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ownerIdentityentity").value(ownerIdentityentity))
                    .andExpect(jsonPath("$.text").value(text))
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            successCount++;

            if ((i + 1) % 25 == 0) {
                System.out.println("Progress: " + (i + 1) + "/" + imageCount
                        + " images processed, all correct so far");
            }
        }

        assertEquals(imageCount, successCount,
                "All " + imageCount + " images must survive JPEG compression with correct owner extraction");
        System.out.println("Stress test PASSED: " + successCount + "/" + imageCount
                + " images with " + userCount + " users, seed=" + seed);
    }

    // ==================== AGGRESSIVE / EDGE-CASE TESTS ====================

    @ParameterizedTest(name = "scaling to {0}% — robustness probe (informational)")
    @ValueSource(doubles = {50.0, 75.0, 90.0, 110.0, 125.0, 150.0, 200.0})
    void scalingRobustnessProbe(double scalePercent) throws Exception {
        byte[] sourceImage = createTestImage(512, 512);

        byte[] embeddedPng = mockMvc.perform(multipart("/api/watermark/embed")
                        .file(new MockMultipartFile("image", "source.png", MediaType.IMAGE_PNG_VALUE, sourceImage))
                        .param("text", "Scale test")
                        .param("ownerIdentityentity", OWNER_ID))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        byte[] scaled = scaleImage(embeddedPng, scalePercent / 100.0);

        String detectJson = mockMvc.perform(multipart("/api/watermark/detect")
                        .file(new MockMultipartFile("image", "scaled.png", MediaType.IMAGE_PNG_VALUE, scaled)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        System.out.println("Scale " + scalePercent + "% detect: " + detectJson);
    }

    @ParameterizedTest(name = "scaling {0}% + JPEG Q{1} — robustness probe (informational)")
    @CsvSource({"75.0,0.75", "90.0,0.50", "110.0,0.75", "150.0,0.60"})
    void scalingPlusJpegRobustnessProbe(double scalePercent, float jpegQuality) throws Exception {
        byte[] sourceImage = createTestImage(512, 512);

        byte[] embeddedPng = mockMvc.perform(multipart("/api/watermark/embed")
                        .file(new MockMultipartFile("image", "source.png", MediaType.IMAGE_PNG_VALUE, sourceImage))
                        .param("text", "Combo test")
                        .param("ownerIdentityentity", OWNER_ID))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        byte[] scaled = scaleImage(embeddedPng, scalePercent / 100.0);
        byte[] jpeg = convertToJpeg(scaled, jpegQuality);

        String detectJson = mockMvc.perform(multipart("/api/watermark/detect")
                        .file(new MockMultipartFile("image", "combo.jpg", MediaType.IMAGE_JPEG_VALUE, jpeg)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        System.out.println("Scale " + scalePercent + "% + JPEG Q" + jpegQuality + " detect: " + detectJson);
    }

    @Test
    void gaussianBlurRobustnessProbe() throws Exception {
        byte[] sourceImage = createTestImage(512, 512);

        byte[] embeddedPng = mockMvc.perform(multipart("/api/watermark/embed")
                        .file(new MockMultipartFile("image", "source.png", MediaType.IMAGE_PNG_VALUE, sourceImage))
                        .param("text", "Blur test")
                        .param("ownerIdentityentity", OWNER_ID))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        byte[] blurred = applyGaussianBlur(embeddedPng, 3);

        String detectJson = mockMvc.perform(multipart("/api/watermark/detect")
                        .file(new MockMultipartFile("image", "blurred.png", MediaType.IMAGE_PNG_VALUE, blurred)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        System.out.println("Gaussian blur (radius=3) detect: " + detectJson);
    }

    @Test
    void watermarkSurvivesTripleJpegCompression() throws Exception {
        byte[] sourceImage = createTestImage(1024, 768);

        byte[] embeddedPng = mockMvc.perform(multipart("/api/watermark/embed")
                        .file(new MockMultipartFile("image", "source.png", MediaType.IMAGE_PNG_VALUE, sourceImage))
                        .param("text", "Triple JPEG")
                        .param("ownerIdentityentity", OWNER_ID))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        byte[] jpeg1 = convertToJpeg(embeddedPng, 0.85f);
        byte[] jpeg2 = convertToJpeg(jpeg1, 0.70f);
        byte[] jpeg3 = convertToJpeg(jpeg2, 0.60f);

        mockMvc.perform(multipart("/api/watermark/detect")
                        .file(new MockMultipartFile("image", "triple.jpg", MediaType.IMAGE_JPEG_VALUE, jpeg3)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.watermarked").value(true))
                .andExpect(jsonPath("$.ownerIdentityentity").value(OWNER_ID));

        mockMvc.perform(multipart("/api/watermark/extract")
                        .file(new MockMultipartFile("image", "triple.jpg", MediaType.IMAGE_JPEG_VALUE, jpeg3))
                        .param("requesterId", OWNER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("Triple JPEG"));
    }

    @Test
    void jpegThenBlurRobustnessProbe() throws Exception {
        byte[] sourceImage = createTestImage(512, 512);

        byte[] embeddedPng = mockMvc.perform(multipart("/api/watermark/embed")
                        .file(new MockMultipartFile("image", "source.png", MediaType.IMAGE_PNG_VALUE, sourceImage))
                        .param("text", "JPEG+Blur")
                        .param("ownerIdentityentity", OWNER_ID))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        byte[] jpeg = convertToJpeg(embeddedPng, 0.60f);
        byte[] blurred = applyGaussianBlur(jpeg, 3);

        String detectJson = mockMvc.perform(multipart("/api/watermark/detect")
                        .file(new MockMultipartFile("image", "jpegblur.png", MediaType.IMAGE_PNG_VALUE, blurred)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        System.out.println("JPEG Q60 + Gaussian blur detect: " + detectJson);
    }

    @ParameterizedTest(name = "watermark survives JPEG Q{0} on 1920x1080 image")
    @ValueSource(floats = {0.30f, 0.40f, 0.50f})
    void watermarkSurvivesAggressiveJpegOnLargeImage(float quality) throws Exception {
        byte[] sourceImage = createTestImage(1920, 1080);

        byte[] embeddedPng = mockMvc.perform(multipart("/api/watermark/embed")
                        .file(new MockMultipartFile("image", "source.png", MediaType.IMAGE_PNG_VALUE, sourceImage))
                        .param("text", "Large aggressive")
                        .param("ownerIdentityentity", OWNER_ID))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        byte[] jpeg = convertToJpeg(embeddedPng, quality);

        mockMvc.perform(multipart("/api/watermark/detect")
                        .file(new MockMultipartFile("image", "large.jpg", MediaType.IMAGE_JPEG_VALUE, jpeg)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.watermarked").value(true))
                .andExpect(jsonPath("$.ownerIdentityentity").value(OWNER_ID));

        mockMvc.perform(multipart("/api/watermark/extract")
                        .file(new MockMultipartFile("image", "large.jpg", MediaType.IMAGE_JPEG_VALUE, jpeg))
                        .param("requesterId", OWNER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("Large aggressive"));
    }

    @Test
    void watermarkSurvivesVeryAggressiveJpegQ20() throws Exception {
        byte[] sourceImage = createTestImage(1024, 768);

        byte[] embeddedPng = mockMvc.perform(multipart("/api/watermark/embed")
                        .file(new MockMultipartFile("image", "source.png", MediaType.IMAGE_PNG_VALUE, sourceImage))
                        .param("text", "Q20 test")
                        .param("ownerIdentityentity", OWNER_ID))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        byte[] jpeg = convertToJpeg(embeddedPng, 0.20f);

        String detectJson = mockMvc.perform(multipart("/api/watermark/detect")
                        .file(new MockMultipartFile("image", "q20.jpg", MediaType.IMAGE_JPEG_VALUE, jpeg)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        System.out.println("JPEG Q20 detect: " + detectJson);

        mockMvc.perform(multipart("/api/watermark/detect")
                        .file(new MockMultipartFile("image", "q20.jpg", MediaType.IMAGE_JPEG_VALUE, jpeg)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.watermarked").value(true));
    }

    @Test
    void jpegQ10RobustnessProbe() throws Exception {
        byte[] sourceImage = createTestImage(2048, 1536);

        byte[] embeddedPng = mockMvc.perform(multipart("/api/watermark/embed")
                        .file(new MockMultipartFile("image", "source.png", MediaType.IMAGE_PNG_VALUE, sourceImage))
                        .param("text", "Q10 extreme")
                        .param("ownerIdentityentity", OWNER_ID))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        byte[] jpeg = convertToJpeg(embeddedPng, 0.10f);

        String detectJson = mockMvc.perform(multipart("/api/watermark/detect")
                        .file(new MockMultipartFile("image", "q10.jpg", MediaType.IMAGE_JPEG_VALUE, jpeg)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        System.out.println("JPEG Q10 on 2048x1536 detect: " + detectJson);
    }

    // ==================== HELPER METHODS ====================

    private byte[] createTestImage() throws Exception {
        return createTestImage(512, 512);
    }

    private byte[] createTestImage(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int red = (x * 255) / Math.max(1, width - 1);
                    int green = (y * 255) / Math.max(1, height - 1);
                    int blue = ((x + y) * 255) / Math.max(1, width + height - 2);
                    image.setRGB(x, y, new Color(red, green, blue).getRGB());
                }
            }

            graphics.setColor(new Color(255, 255, 255, 90));
            graphics.fillOval(width / 5, height / 5, Math.max(40, width / 2), Math.max(40, height / 2));
            graphics.setColor(new Color(30, 30, 30, 120));
            graphics.fillRect(width / 4, height / 2 - 20, Math.max(40, width / 2), 40);
        } finally {
            graphics.dispose();
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(image, "png", outputStream);
        return outputStream.toByteArray();
    }

    private byte[] convertToJpeg(byte[] pngBytes, float quality) throws Exception {
        BufferedImage original = ImageIO.read(new ByteArrayInputStream(pngBytes));
        BufferedImage image = new BufferedImage(original.getWidth(), original.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.drawImage(original, 0, 0, null);
        g.dispose();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        ImageWriter writer = writers.next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(quality);

        try (ImageOutputStream imageOutputStream = ImageIO.createImageOutputStream(outputStream)) {
            writer.setOutput(imageOutputStream);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }

        return outputStream.toByteArray();
    }

    private boolean containsColorPixels(BufferedImage image) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                Color color = new Color(image.getRGB(x, y));
                if (color.getRed() != color.getGreen() || color.getGreen() != color.getBlue()) {
                    return true;
                }
            }
        }
        return false;
    }

    private byte[] scaleImage(byte[] imageBytes, double scale) throws Exception {
        BufferedImage original = ImageIO.read(new ByteArrayInputStream(imageBytes));
        int newWidth = (int) Math.round(original.getWidth() * scale);
        int newHeight = (int) Math.round(original.getHeight() * scale);
        BufferedImage scaled = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(original, 0, 0, newWidth, newHeight, null);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(scaled, "png", out);
        return out.toByteArray();
    }

    private byte[] applyGaussianBlur(byte[] imageBytes, int radius) throws Exception {
        BufferedImage original = ImageIO.read(new ByteArrayInputStream(imageBytes));
        BufferedImage src = new BufferedImage(original.getWidth(), original.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = src.createGraphics();
        g.drawImage(original, 0, 0, null);
        g.dispose();
        int size = radius * 2 + 1;
        float[] data = new float[size * size];
        float value = 1.0f / (size * size);
        java.util.Arrays.fill(data, value);
        Kernel kernel = new Kernel(size, size, data);
        ConvolveOp op = new ConvolveOp(kernel, ConvolveOp.EDGE_NO_OP, null);
        BufferedImage blurred = op.filter(src, null);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(blurred, "png", out);
        return out.toByteArray();
    }

}
