package pl.zzpj.watermark_service.service;

import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.SingularValueDecomposition;
import org.apache.commons.math3.util.FastMath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import pl.zzpj.watermark_service.dto.DetectWatermarkResponse;
import pl.zzpj.watermark_service.dto.ExtractedTextResponse;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * Implements the image watermarking workflow used by the watermark service.
 *
 * <p>The service embeds encrypted owner-bound payloads into images using a hybrid
 * DWT-DCT-SVD technique that is resistant to JPEG compression.
 * It can also detect and extract watermarks created by the same application key.</p>
 */
@Service
public class SteganographyService {

    private static final int BLOCK_SIZE = 4;
    private static final int REDUNDANCY = 3;
    private static final double DELTA = 80.0d;
    private static final int HEADER_BITS = 32;
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private static final byte[] MAGIC = "STGC".getBytes(StandardCharsets.US_ASCII);
    private static final int VERSION = 3;
    private static final int MAX_PAYLOAD_BYTES = 16_384;
    private static final int DWT_BLOCK_ALIGNMENT = BLOCK_SIZE * 2;

    private static final Logger log = LoggerFactory.getLogger(SteganographyService.class);

    private final String appKey;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Creates a new service instance.
     *
     * @param appKey application-wide secret used to derive watermark keys
     */
    public SteganographyService(@Value("${watermark.app-key}") String appKey) {
        if (appKey == null || appKey.isBlank()) {
            throw new IllegalArgumentException("watermark.app-key must not be blank");
        }
        this.appKey = appKey;
    }

    // ---- Public API methods ----

    /**
     * Embeds a watermark into the provided image.
     *
     * @param file    uploaded source image
     * @param message text payload to embed
     * @param ownerIdentity owner identifier stored with the watermark
     * @return generated PNG bytes containing the watermark
     */
    public byte[] embedMessage(MultipartFile file, String message, String ownerIdentity) {
        validateOwnerIdentity(ownerIdentity);
        validateMessage(message);
        try {
            BufferedImage image = readImage(file.getBytes());
            log.info("Embedding watermark: ownerIdentity={}, imageSize={}x{}, textLength={}",
                    ownerIdentity, image.getWidth(), image.getHeight(), message.length());
            BufferedImage watermarked = embedMessage(image, message, ownerIdentity);
            log.info("Watermark embedded successfully for ownerIdentity={}", ownerIdentity);
            return writePng(watermarked);
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not embed watermark into image", e);
        }
    }

    /**
     * Detects whether an uploaded image contains a watermark created by this service.
     *
     * @param file uploaded image to inspect
     * @return detection result with watermark metadata when available
     */
    public DetectWatermarkResponse detectWatermark(MultipartFile file) {
        try {
            BufferedImage image = readImage(file.getBytes());
            log.info("Detecting watermark: imageSize={}x{}", image.getWidth(), image.getHeight());
            DetectWatermarkResponse response = detectWatermark(image)
                    .map(envelope -> new DetectWatermarkResponse(true, envelope.ownerIdentity(), envelope.version()))
                    .orElseGet(() -> new DetectWatermarkResponse(false, null, null));
            log.info("Detection result: watermarked={}, ownerIdentity={}", response.watermarked(), response.ownerIdentity());
            return response;
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not inspect image", e);
        }
    }

    /**
     * Extracts a watermark from the provided image for an authorized requester.
     *
     * @param file        uploaded image to process
     * @param requesterId identifier of the user requesting extraction
     * @return extracted watermark data
     */
    public ExtractedTextResponse extractMessage(MultipartFile file, String requesterId) {
        validateOwnerIdentity(requesterId);
        try {
            BufferedImage image = readImage(file.getBytes());
            log.info("Extracting watermark: requesterId={}, imageSize={}x{}",
                    requesterId, image.getWidth(), image.getHeight());
            WatermarkEnvelope envelope = detectWatermark(image)
                    .orElseThrow(() -> new IllegalArgumentException("No watermark found in this image"));

            if (!envelope.ownerIdentity().equals(requesterId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Requester is not allowed to read this watermark");
            }

            String text = decryptOwnerPayload(envelope.ownerIdentity(), envelope.ownerPayload());
            log.info("Watermark extracted successfully for ownerIdentity={}", envelope.ownerIdentity());
            return new ExtractedTextResponse(envelope.ownerIdentity(), text);
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not extract watermark from image", e);
        }
    }

    /**
     * Generates a visualization of watermark block locations in the provided image.
     *
     * @param file uploaded image to visualize
     * @return PNG bytes of the image with highlighted watermark blocks
     */
    public byte[] visualizeWatermark(MultipartFile file) {
        try {
            BufferedImage image = readImage(file.getBytes());
            BufferedImage visualization = visualizeWatermark(image);
            return writePng(visualization);
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not visualize watermark in image", e);
        }
    }

    // ---- Core watermark operations ----

    BufferedImage visualizeWatermark(BufferedImage sourceImage) {
        WatermarkImageData imageData = WatermarkImageData.from(sourceImage);
        double[][] luminance = imageData.luminance();
        double[][] dwtCoeffs = applyHaarDwt2D(luminance);
        int halfHeight = luminance.length / 2;
        int halfWidth = luminance[0].length / 2;
        double[][] llBand = extractSubband(dwtCoeffs, 0, 0, halfHeight, halfWidth);
        List<BlockCoordinates> blockCoordinates = shuffledBlocks(llBand);
        int totalBits = blockCoordinates.size() / REDUNDANCY;

        int usedBlocks = 0;
        try {
            List<Integer> bits = new ArrayList<>(totalBits);
            for (int bitIndex = 0; bitIndex < totalBits; bitIndex++) {
                int ones = 0;
                for (int copy = 0; copy < REDUNDANCY; copy++) {
                    int blockIdx = bitIndex * REDUNDANCY + copy;
                    if (blockIdx >= blockCoordinates.size()) break;
                    BlockCoordinates block = blockCoordinates.get(blockIdx);
                    double[][] currentBlock = copyBlock(llBand, block.row(), block.column());
                    double[][] dctBlock = applyDct2D(currentBlock);
                    ones += extractBitFromBlock(dctBlock);
                }
                bits.add(ones > REDUNDANCY / 2 ? 1 : 0);
            }
            byte[] envelopeBytes = decodeBitsToBytes(bits);
            parseApplicationEnvelope(envelopeBytes);
            int envelopeBits = envelopeBytes.length * Byte.SIZE;
            usedBlocks = envelopeBits * REDUNDANCY;
        } catch (IllegalArgumentException ignored) {
        }

        int outputWidth = luminance[0].length;
        int outputHeight = luminance.length;
        BufferedImage result = new BufferedImage(outputWidth, outputHeight, BufferedImage.TYPE_INT_RGB);

        for (int row = 0; row < outputHeight; row++) {
            for (int column = 0; column < outputWidth; column++) {
                int gray = WatermarkImageData.clampToRange((int) FastMath.round(luminance[row][column]), 0, 255);
                result.setRGB(column, row, new Color(gray, gray, gray).getRGB());
            }
        }

        if (usedBlocks > 0) {
            Graphics2D g2d = result.createGraphics();
            try {
                g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.45f));
                g2d.setColor(Color.RED);
                for (int i = 0; i < usedBlocks && i < blockCoordinates.size(); i++) {
                    BlockCoordinates block = blockCoordinates.get(i);
                    g2d.fillRect(block.column() * 2, block.row() * 2, BLOCK_SIZE * 2, BLOCK_SIZE * 2);
                }
            } finally {
                g2d.dispose();
            }
        }

        return result;
    }

    BufferedImage embedMessage(BufferedImage sourceImage, String message, String ownerIdentity) {
        byte[] appEnvelope = createApplicationEnvelope(ownerIdentity, message);
        int[] bits = encodeBytesToBits(appEnvelope);
        int requiredBlocks = bits.length * REDUNDANCY;
        WatermarkImageData imageData = WatermarkImageData.from(sourceImage);
        double[][] luminance = imageData.luminance();
        double[][] dwtCoeffs = applyHaarDwt2D(luminance);
        int halfHeight = luminance.length / 2;
        int halfWidth = luminance[0].length / 2;
        double[][] llBand = extractSubband(dwtCoeffs, 0, 0, halfHeight, halfWidth);
        List<BlockCoordinates> blockCoordinates = shuffledBlocks(llBand);

        if (requiredBlocks > blockCoordinates.size()) {
            int capacityChars = estimateTextCapacity(blockCoordinates.size(), ownerIdentity);
            throw new IllegalArgumentException(
                    "Message is too long for this image. Maximum text length for this image: ~" + capacityChars + " characters."
            );
        }

        for (int bitIndex = 0; bitIndex < bits.length; bitIndex++) {
            for (int copy = 0; copy < REDUNDANCY; copy++) {
                BlockCoordinates block = blockCoordinates.get(bitIndex * REDUNDANCY + copy);
                double[][] currentBlock = copyBlock(llBand, block.row(), block.column());
                double[][] dctBlock = applyDct2D(currentBlock);
                double[][] modifiedDct = embedBitInBlock(dctBlock, bits[bitIndex]);
                writeBlock(llBand, block.row(), block.column(), applyInverseDct2D(modifiedDct));
            }
        }

        writeSubband(dwtCoeffs, 0, 0, llBand);
        double[][] reconstructed = applyInverseHaarDwt2D(dwtCoeffs);
        return imageData.withUpdatedLuminance(reconstructed);
    }

    Optional<WatermarkEnvelope> detectWatermark(BufferedImage sourceImage) {
        try {
            WatermarkImageData imageData = WatermarkImageData.from(sourceImage);
            double[][] luminance = imageData.luminance();
            double[][] dwtCoeffs = applyHaarDwt2D(luminance);
            int halfHeight = luminance.length / 2;
            int halfWidth = luminance[0].length / 2;
            double[][] llBand = extractSubband(dwtCoeffs, 0, 0, halfHeight, halfWidth);
            List<BlockCoordinates> blockCoordinates = shuffledBlocks(llBand);
            int totalBits = blockCoordinates.size() / REDUNDANCY;
            List<Integer> bits = new ArrayList<>(totalBits);

            for (int bitIndex = 0; bitIndex < totalBits; bitIndex++) {
                int ones = 0;
                for (int copy = 0; copy < REDUNDANCY; copy++) {
                    int blockIdx = bitIndex * REDUNDANCY + copy;
                    if (blockIdx >= blockCoordinates.size()) break;
                    BlockCoordinates block = blockCoordinates.get(blockIdx);
                    double[][] currentBlock = copyBlock(llBand, block.row(), block.column());
                    double[][] dctBlock = applyDct2D(currentBlock);
                    ones += extractBitFromBlock(dctBlock);
                }
                bits.add(ones > REDUNDANCY / 2 ? 1 : 0);
            }

            byte[] envelopeBytes = decodeBitsToBytes(bits);
            return Optional.of(parseApplicationEnvelope(envelopeBytes));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    // ---- Validation ----

    private void validateOwnerIdentity(String ownerIdentity) {
        if (ownerIdentity == null || ownerIdentity.isBlank()) {
            throw new IllegalArgumentException("Owner identity must not be blank");
        }
    }

    private void validateMessage(String message) {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("Watermark message must not be blank");
        }
    }

    private BufferedImage readImage(byte[] imageBytes) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
        if (image == null) {
            throw new IllegalArgumentException("Unsupported image format");
        }
        validateImageDimensions(image);
        return image;
    }

    private void validateImageDimensions(BufferedImage image) {
        if (image.getWidth() < DWT_BLOCK_ALIGNMENT * 2 || image.getHeight() < DWT_BLOCK_ALIGNMENT * 2) {
            throw new IllegalArgumentException("Image must be at least " + (DWT_BLOCK_ALIGNMENT * 2) + "x" + (DWT_BLOCK_ALIGNMENT * 2) + " pixels");
        }
    }

    private byte[] writePng(BufferedImage image) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(image, "png", outputStream);
        return outputStream.toByteArray();
    }

    private int estimateTextCapacity(int totalBlocks, String ownerIdentity) {
        int availableBits = totalBlocks / REDUNDANCY;
        int availableBytes = availableBits / Byte.SIZE;
        int ownerBytes = ownerIdentity.getBytes(StandardCharsets.UTF_8).length;
        int envelopeOverhead = MAGIC.length + Integer.BYTES + Integer.BYTES + ownerBytes
                + Integer.BYTES + GCM_IV_LENGTH + GCM_TAG_LENGTH / Byte.SIZE;
        int outerOverhead = Integer.BYTES + GCM_IV_LENGTH + GCM_TAG_LENGTH / Byte.SIZE;
        int textOverhead = GCM_IV_LENGTH + GCM_TAG_LENGTH / Byte.SIZE;
        int totalOverhead = envelopeOverhead + outerOverhead + textOverhead + Integer.BYTES;
        return Math.max(0, availableBytes - totalOverhead);
    }

    // ---- Haar DWT 2D ----

    private double[][] applyHaarDwt2D(double[][] input) {
        int rows = input.length;
        int cols = input[0].length;
        double[][] result = new double[rows][cols];
        for (int r = 0; r < rows; r++) {
            System.arraycopy(input[r], 0, result[r], 0, cols);
        }

        // Transform rows
        for (int r = 0; r < rows; r++) {
            double[] row = result[r];
            int half = cols / 2;
            double[] temp = new double[cols];
            for (int c = 0; c < half; c++) {
                temp[c] = (row[2 * c] + row[2 * c + 1]) / FastMath.sqrt(2.0d);
                temp[half + c] = (row[2 * c] - row[2 * c + 1]) / FastMath.sqrt(2.0d);
            }
            result[r] = temp;
        }

        // Transform columns
        for (int c = 0; c < cols; c++) {
            int half = rows / 2;
            double[] temp = new double[rows];
            for (int r = 0; r < half; r++) {
                temp[r] = (result[2 * r][c] + result[2 * r + 1][c]) / FastMath.sqrt(2.0d);
                temp[half + r] = (result[2 * r][c] - result[2 * r + 1][c]) / FastMath.sqrt(2.0d);
            }
            for (int r = 0; r < rows; r++) {
                result[r][c] = temp[r];
            }
        }

        return result;
    }

    private double[][] applyInverseHaarDwt2D(double[][] input) {
        int rows = input.length;
        int cols = input[0].length;
        double[][] result = new double[rows][cols];
        for (int r = 0; r < rows; r++) {
            System.arraycopy(input[r], 0, result[r], 0, cols);
        }

        // Inverse transform columns
        for (int c = 0; c < cols; c++) {
            int half = rows / 2;
            double[] temp = new double[rows];
            for (int r = 0; r < half; r++) {
                temp[2 * r] = (result[r][c] + result[half + r][c]) / FastMath.sqrt(2.0d);
                temp[2 * r + 1] = (result[r][c] - result[half + r][c]) / FastMath.sqrt(2.0d);
            }
            for (int r = 0; r < rows; r++) {
                result[r][c] = temp[r];
            }
        }

        // Inverse transform rows
        for (int r = 0; r < rows; r++) {
            double[] row = result[r];
            int half = cols / 2;
            double[] temp = new double[cols];
            for (int c = 0; c < half; c++) {
                temp[2 * c] = (row[c] + row[half + c]) / FastMath.sqrt(2.0d);
                temp[2 * c + 1] = (row[c] - row[half + c]) / FastMath.sqrt(2.0d);
            }
            result[r] = temp;
        }

        return result;
    }

    // ---- Subband operations ----

    private double[][] extractSubband(double[][] dwtCoeffs, int startRow, int startCol, int height, int width) {
        double[][] subband = new double[height][width];
        for (int r = 0; r < height; r++) {
            System.arraycopy(dwtCoeffs[startRow + r], startCol, subband[r], 0, width);
        }
        return subband;
    }

    private void writeSubband(double[][] dwtCoeffs, int startRow, int startCol, double[][] subband) {
        for (int r = 0; r < subband.length; r++) {
            System.arraycopy(subband[r], 0, dwtCoeffs[startRow + r], startCol, subband[r].length);
        }
    }

    // ---- Envelope creation and parsing ----

    private byte[] createApplicationEnvelope(String ownerIdentity, String message) {
        byte[] ownerBytes = ownerIdentity.getBytes(StandardCharsets.UTF_8);
        byte[] encryptedOwnerPayload = encryptOwnerPayload(ownerIdentity, message);
        ByteBuffer plainPayload = ByteBuffer.allocate(
                MAGIC.length + Integer.BYTES + Integer.BYTES + ownerBytes.length + Integer.BYTES + encryptedOwnerPayload.length
        );
        plainPayload.put(MAGIC);
        plainPayload.putInt(VERSION);
        plainPayload.putInt(ownerBytes.length);
        plainPayload.put(ownerBytes);
        plainPayload.putInt(encryptedOwnerPayload.length);
        plainPayload.put(encryptedOwnerPayload);

        byte[] sealedPayload = encryptWithKey(plainPayload.array(), deriveAppAesKey());
        ByteBuffer envelope = ByteBuffer.allocate(Integer.BYTES + sealedPayload.length);
        envelope.putInt(sealedPayload.length);
        envelope.put(sealedPayload);
        return envelope.array();
    }

    private WatermarkEnvelope parseApplicationEnvelope(byte[] envelopeBytes) {
        ByteBuffer buffer = ByteBuffer.wrap(envelopeBytes);
        int sealedLength = buffer.getInt();
        if (sealedLength <= GCM_IV_LENGTH || sealedLength > buffer.remaining()) {
            throw new IllegalArgumentException("Watermark envelope is invalid");
        }

        byte[] sealedPayload = new byte[sealedLength];
        buffer.get(sealedPayload);

        byte[] plainPayload = decryptWithKey(sealedPayload, deriveAppAesKey(), "application envelope");
        ByteBuffer payloadBuffer = ByteBuffer.wrap(plainPayload);

        byte[] magic = new byte[MAGIC.length];
        payloadBuffer.get(magic);
        for (int index = 0; index < MAGIC.length; index++) {
            if (magic[index] != MAGIC[index]) {
                throw new IllegalArgumentException("Watermark magic bytes do not match");
            }
        }

        int version = payloadBuffer.getInt();
        int ownerIdentityLength = payloadBuffer.getInt();
        if (ownerIdentityLength <= 0 || ownerIdentityLength > payloadBuffer.remaining()) {
            throw new IllegalArgumentException("Watermark owner identifier is invalid");
        }

        byte[] ownerIdentityBytes = new byte[ownerIdentityLength];
        payloadBuffer.get(ownerIdentityBytes);
        String ownerIdentity = new String(ownerIdentityBytes, StandardCharsets.UTF_8);

        int ownerPayloadLength = payloadBuffer.getInt();
        if (ownerPayloadLength <= GCM_IV_LENGTH || ownerPayloadLength > payloadBuffer.remaining()) {
            throw new IllegalArgumentException("Watermark content payload is invalid");
        }

        byte[] ownerPayload = new byte[ownerPayloadLength];
        payloadBuffer.get(ownerPayload);

        return new WatermarkEnvelope(version, ownerIdentity, ownerPayload);
    }

    // ---- Encryption ----

    private byte[] encryptOwnerPayload(String ownerIdentity, String message) {
        return encryptWithKey(
                message.getBytes(StandardCharsets.UTF_8),
                deriveOwnerAesKey(ownerIdentity)
        );
    }

    private String decryptOwnerPayload(String ownerIdentity, byte[] payload) {
        byte[] plainBytes = decryptWithKey(payload, deriveOwnerAesKey(ownerIdentity), "owner payload");
        return new String(plainBytes, StandardCharsets.UTF_8);
    }

    private byte[] encryptWithKey(byte[] plainBytes, SecretKeySpec key) {
        byte[] iv = new byte[GCM_IV_LENGTH];
        secureRandom.nextBytes(iv);

        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] cipherBytes = cipher.doFinal(plainBytes);

            ByteBuffer buffer = ByteBuffer.allocate(iv.length + cipherBytes.length);
            buffer.put(iv);
            buffer.put(cipherBytes);
            return buffer.array();
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("Could not encrypt watermark payload", e);
        }
    }

    private byte[] decryptWithKey(byte[] encryptedBytes, SecretKeySpec key, String purpose) {
        if (encryptedBytes.length <= GCM_IV_LENGTH) {
            throw new IllegalArgumentException("Encrypted watermark payload is invalid");
        }

        byte[] iv = new byte[GCM_IV_LENGTH];
        byte[] cipherBytes = new byte[encryptedBytes.length - GCM_IV_LENGTH];
        System.arraycopy(encryptedBytes, 0, iv, 0, GCM_IV_LENGTH);
        System.arraycopy(encryptedBytes, GCM_IV_LENGTH, cipherBytes, 0, cipherBytes.length);

        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            return cipher.doFinal(cipherBytes);
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("Could not decrypt watermark payload for " + purpose, e);
        }
    }

    private SecretKeySpec deriveAppAesKey() {
        return new SecretKeySpec(first16Bytes(sha256(("app:" + appKey).getBytes(StandardCharsets.UTF_8))), "AES");
    }

    private SecretKeySpec deriveOwnerAesKey(String ownerIdentity) {
        return new SecretKeySpec(first16Bytes(sha256(("owner:" + ownerIdentity + ":" + appKey).getBytes(StandardCharsets.UTF_8))), "AES");
    }

    private byte[] first16Bytes(byte[] bytes) {
        byte[] keyBytes = new byte[16];
        System.arraycopy(bytes, 0, keyBytes, 0, keyBytes.length);
        return keyBytes;
    }

    private byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("Could not compute key digest", e);
        }
    }

    // ---- Bit encoding / decoding ----

    private int[] encodeBytesToBits(byte[] payload) {
        int[] bits = new int[payload.length * Byte.SIZE];
        int bitIndex = 0;
        for (byte payloadByte : payload) {
            for (int shift = 7; shift >= 0; shift--) {
                bits[bitIndex++] = (payloadByte >> shift) & 1;
            }
        }
        return bits;
    }

    private byte[] decodeBitsToBytes(List<Integer> bits) {
        if (bits.size() < HEADER_BITS) {
            throw new IllegalArgumentException("Image does not contain enough watermark data");
        }

        int payloadLength = 0;
        for (int index = 0; index < HEADER_BITS; index++) {
            payloadLength = (payloadLength << 1) | bits.get(index);
        }

        long requiredBits = HEADER_BITS + ((long) payloadLength * Byte.SIZE);
        if (payloadLength <= GCM_IV_LENGTH || payloadLength > MAX_PAYLOAD_BYTES || requiredBits > bits.size()) {
            throw new IllegalArgumentException("Embedded watermark length is invalid");
        }

        byte[] payloadBytes = new byte[Integer.BYTES + payloadLength];
        payloadBytes[0] = (byte) ((payloadLength >>> 24) & 0xFF);
        payloadBytes[1] = (byte) ((payloadLength >>> 16) & 0xFF);
        payloadBytes[2] = (byte) ((payloadLength >>> 8) & 0xFF);
        payloadBytes[3] = (byte) (payloadLength & 0xFF);

        int bitIndex = HEADER_BITS;
        for (int byteIndex = Integer.BYTES; byteIndex < payloadBytes.length; byteIndex++) {
            int value = 0;
            for (int shift = 0; shift < Byte.SIZE; shift++) {
                value = (value << 1) | bits.get(bitIndex++);
            }
            payloadBytes[byteIndex] = (byte) value;
        }

        return payloadBytes;
    }

    // ---- Block operations ----

    private List<BlockCoordinates> shuffledBlocks(double[][] image) {
        List<BlockCoordinates> coordinates = collectBlocks(image);
        Collections.shuffle(coordinates, new Random(seedFromAppKey("block-order")));
        return coordinates;
    }

    private long seedFromAppKey(String purpose) {
        byte[] hash = sha256((purpose + ":" + appKey).getBytes(StandardCharsets.UTF_8));
        return ByteBuffer.wrap(hash).getLong();
    }

    private List<BlockCoordinates> collectBlocks(double[][] image) {
        List<BlockCoordinates> coordinates = new ArrayList<>();
        for (int row = 0; row <= image.length - BLOCK_SIZE; row += BLOCK_SIZE) {
            for (int column = 0; column <= image[0].length - BLOCK_SIZE; column += BLOCK_SIZE) {
                coordinates.add(new BlockCoordinates(row, column));
            }
        }
        return coordinates;
    }

    private double[][] copyBlock(double[][] source, int row, int column) {
        double[][] block = new double[BLOCK_SIZE][BLOCK_SIZE];
        for (int blockRow = 0; blockRow < BLOCK_SIZE; blockRow++) {
            System.arraycopy(source[row + blockRow], column, block[blockRow], 0, BLOCK_SIZE);
        }
        return block;
    }

    private void writeBlock(double[][] target, int row, int column, double[][] block) {
        for (int blockRow = 0; blockRow < BLOCK_SIZE; blockRow++) {
            System.arraycopy(block[blockRow], 0, target[row + blockRow], column, BLOCK_SIZE);
        }
    }

    // ---- DWT-DCT-SVD embedding / extraction ----

    private double[][] embedBitInBlock(double[][] dctBlock, int bit) {
        RealMatrix matrix = MatrixUtils.createRealMatrix(dctBlock);
        SingularValueDecomposition svd = new SingularValueDecomposition(matrix);
        double[] singularValues = svd.getSingularValues().clone();

        double s0 = singularValues[0];
        double quantized = DELTA * FastMath.floor(s0 / DELTA);
        singularValues[0] = (bit == 1) ? quantized + 0.75d * DELTA : quantized + 0.25d * DELTA;

        RealMatrix sMatrix = MatrixUtils.createRealDiagonalMatrix(singularValues);
        RealMatrix reconstructed = svd.getU().multiply(sMatrix).multiply(svd.getVT());
        return reconstructed.getData();
    }

    private int extractBitFromBlock(double[][] dctBlock) {
        RealMatrix matrix = MatrixUtils.createRealMatrix(dctBlock);
        SingularValueDecomposition svd = new SingularValueDecomposition(matrix);
        double s0 = svd.getSingularValues()[0];
        double remainder = s0 - DELTA * FastMath.floor(s0 / DELTA);
        return (remainder > 0.5d * DELTA) ? 1 : 0;
    }

    // ---- DCT 2D ----

    private double[][] applyDct2D(double[][] input) {
        double[][] output = new double[BLOCK_SIZE][BLOCK_SIZE];
        for (int u = 0; u < BLOCK_SIZE; u++) {
            for (int v = 0; v < BLOCK_SIZE; v++) {
                double sum = 0.0d;
                for (int x = 0; x < BLOCK_SIZE; x++) {
                    for (int y = 0; y < BLOCK_SIZE; y++) {
                        sum += input[x][y]
                                * FastMath.cos(((2 * x) + 1) * u * FastMath.PI / (2.0d * BLOCK_SIZE))
                                * FastMath.cos(((2 * y) + 1) * v * FastMath.PI / (2.0d * BLOCK_SIZE));
                    }
                }
                output[u][v] = normalizationFactor(u) * normalizationFactor(v) * sum;
            }
        }
        return output;
    }

    private double[][] applyInverseDct2D(double[][] input) {
        double[][] output = new double[BLOCK_SIZE][BLOCK_SIZE];
        for (int x = 0; x < BLOCK_SIZE; x++) {
            for (int y = 0; y < BLOCK_SIZE; y++) {
                double sum = 0.0d;
                for (int u = 0; u < BLOCK_SIZE; u++) {
                    for (int v = 0; v < BLOCK_SIZE; v++) {
                        sum += normalizationFactor(u) * normalizationFactor(v) * input[u][v]
                                * FastMath.cos(((2 * x) + 1) * u * FastMath.PI / (2.0d * BLOCK_SIZE))
                                * FastMath.cos(((2 * y) + 1) * v * FastMath.PI / (2.0d * BLOCK_SIZE));
                    }
                }
                output[x][y] = sum;
            }
        }
        return output;
    }

    private double normalizationFactor(int index) {
        return index == 0
                ? FastMath.sqrt(1.0d / BLOCK_SIZE)
                : FastMath.sqrt(2.0d / BLOCK_SIZE);
    }

    // ---- Internal records ----

    private record BlockCoordinates(int row, int column) {
    }

    private record WatermarkEnvelope(int version, String ownerIdentity, byte[] ownerPayload) {
    }

    private record WatermarkImageData(int width, int height, double[][] luminance, double[][] cb, double[][] cr) {

        private static WatermarkImageData from(BufferedImage image) {
            int originalWidth = image.getWidth();
            int originalHeight = image.getHeight();
            int paddedWidth = makeBlockAligned(originalWidth);
            int paddedHeight = makeBlockAligned(originalHeight);
            double[][] luminance = new double[paddedHeight][paddedWidth];
            double[][] cb = new double[originalHeight][originalWidth];
            double[][] cr = new double[originalHeight][originalWidth];
            Raster raster = image.getRaster();
            int bands = raster.getNumBands();
            double maxSample = FastMath.pow(2.0d, image.getColorModel().getComponentSize(0)) - 1.0d;
            double sampleScale = 255.0d / maxSample;

            for (int row = 0; row < originalHeight; row++) {
                for (int column = 0; column < originalWidth; column++) {
                    if (bands == 1) {
                        double sample = raster.getSampleDouble(column, row, 0) * sampleScale;
                        luminance[row][column] = sample;
                    } else {
                        double red = raster.getSampleDouble(column, row, 0) * sampleScale;
                        double green = raster.getSampleDouble(column, row, 1) * sampleScale;
                        double blue = raster.getSampleDouble(column, row, 2) * sampleScale;
                        luminance[row][column] = (0.299d * red) + (0.587d * green) + (0.114d * blue);
                        cb[row][column] = (-0.168736d * red) - (0.331264d * green) + (0.5d * blue);
                        cr[row][column] = (0.5d * red) - (0.418688d * green) - (0.081312d * blue);
                    }
                }
            }

            for (int row = originalHeight; row < paddedHeight; row++) {
                System.arraycopy(luminance[originalHeight - 1], 0, luminance[row], 0, originalWidth);
            }
            if (paddedWidth != originalWidth) {
                for (int row = 0; row < paddedHeight; row++) {
                    for (int column = originalWidth; column < paddedWidth; column++) {
                        luminance[row][column] = luminance[row][originalWidth - 1];
                    }
                }
            }

            return new WatermarkImageData(originalWidth, originalHeight, luminance, cb, cr);
        }

        private BufferedImage withUpdatedLuminance(double[][] updatedLuminance) {
            BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

            for (int row = 0; row < height; row++) {
                for (int column = 0; column < width; column++) {
                    double y = updatedLuminance[row][column];
                    double cbValue = cb[row][column];
                    double crValue = cr[row][column];
                    int red = clampToRange((int) FastMath.round(y + (1.402d * crValue)), 0, 255);
                    int green = clampToRange((int) FastMath.round(y - (0.344136d * cbValue) - (0.714136d * crValue)), 0, 255);
                    int blue = clampToRange((int) FastMath.round(y + (1.772d * cbValue)), 0, 255);
                    result.setRGB(column, row, (red << 16) | (green << 8) | blue);
                }
            }

            return result;
        }

        static int clampToRange(int value, int min, int max) {
            return Math.max(min, Math.min(max, value));
        }

        private static int makeBlockAligned(int value) {
            return (value % DWT_BLOCK_ALIGNMENT == 0) ? value : value + (DWT_BLOCK_ALIGNMENT - (value % DWT_BLOCK_ALIGNMENT));
        }
    }
}
