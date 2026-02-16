package me.maxih.itunes_backup_explorer.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class MediaConverter {
    private static final Logger logger = LoggerFactory.getLogger(MediaConverter.class);

    private static final Set<String> HEIF_EXTENSIONS = Set.of("heic", "heif");
    private static final Set<String> VIDEO_EXTENSIONS = Set.of("mov", "mp4", "m4v", "avi");

    private static Boolean ffmpegAvailable;
    private static Boolean imageMagickAvailable;
    private static String imageMagickCommand;

    public static boolean isFfmpegAvailable() {
        if (ffmpegAvailable == null) {
            ffmpegAvailable = testCommand("ffmpeg", "-version");
            logger.info(ffmpegAvailable
                    ? "ffmpeg detectado - thumbnails de vídeo ativados"
                    : "ffmpeg não encontrado - vídeos usarão placeholders");
        }
        return ffmpegAvailable;
    }

    public static boolean isImageMagickAvailable() {
        if (imageMagickAvailable == null) {
            if (testCommand("magick", "-version")) {
                imageMagickAvailable = true;
                imageMagickCommand = "magick";
            } else if (testCommand("convert", "-version")) {
                imageMagickAvailable = true;
                imageMagickCommand = "convert";
            } else {
                imageMagickAvailable = false;
            }
            logger.info(imageMagickAvailable
                    ? "ImageMagick detectado ({}) - suporte a HEIC ativado".replace("{}", imageMagickCommand)
                    : "ImageMagick não encontrado - HEIC usará placeholders");
        }
        return imageMagickAvailable;
    }

    private static boolean testCommand(String... command) {
        try {
            Process p = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();
            p.getInputStream().readAllBytes();
            boolean finished = p.waitFor(5, TimeUnit.SECONDS);
            return finished && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isHeif(String extension) {
        return extension != null && HEIF_EXTENSIONS.contains(extension.toLowerCase());
    }

    public static boolean isVideo(String extension) {
        return extension != null && VIDEO_EXTENSIONS.contains(extension.toLowerCase());
    }

    public static boolean needsConversion(String extension) {
        return isHeif(extension) || isVideo(extension);
    }

    public static File convertToJpeg(File source, String extension, int maxSize) throws IOException {
        if (isHeif(extension)) {
            return convertHeifToJpeg(source, maxSize);
        }
        if (isVideo(extension)) {
            return extractVideoFrame(source, maxSize);
        }
        return null;
    }

    private static File convertHeifToJpeg(File source, int maxSize) throws IOException {
        if (!isImageMagickAvailable()) return null;

        File output = Files.createTempFile("heic_", ".jpg").toFile();
        output.deleteOnExit();

        try {
            ProcessBuilder pb = new ProcessBuilder(
                    imageMagickCommand,
                    source.getAbsolutePath(),
                    "-resize", maxSize + "x" + maxSize + ">",
                    "-quality", "90",
                    output.getAbsolutePath()
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();
            process.getInputStream().readAllBytes();
            boolean finished = process.waitFor(15, TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                output.delete();
                return null;
            }

            if (process.exitValue() != 0 || !output.exists() || output.length() == 0) {
                output.delete();
                return null;
            }

            return output;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            output.delete();
            return null;
        } catch (Exception e) {
            logger.warn("Falha ao converter HEIC {}: {}", source.getName(), e.getMessage());
            output.delete();
            return null;
        }
    }

    private static File extractVideoFrame(File source, int maxSize) throws IOException {
        if (!isFfmpegAvailable()) return null;

        File output = Files.createTempFile("frame_", ".jpg").toFile();
        output.deleteOnExit();

        try {
            String scale = "scale='min(" + maxSize + ",iw)':'min(" + maxSize + ",ih)':force_original_aspect_ratio=decrease";
            ProcessBuilder pb = new ProcessBuilder(
                    "ffmpeg", "-y",
                    "-i", source.getAbsolutePath(),
                    "-ss", "00:00:00.5",
                    "-frames:v", "1",
                    "-vf", scale,
                    "-q:v", "2",
                    output.getAbsolutePath()
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();
            process.getInputStream().readAllBytes();
            boolean finished = process.waitFor(15, TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                output.delete();
                return null;
            }

            if (process.exitValue() != 0 || !output.exists() || output.length() == 0) {
                output.delete();
                return null;
            }

            return output;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            output.delete();
            return null;
        } catch (Exception e) {
            logger.warn("Falha ao extrair frame de {}: {}", source.getName(), e.getMessage());
            output.delete();
            return null;
        }
    }

    public static void detectTools() {
        isFfmpegAvailable();
        isImageMagickAvailable();
    }
}
