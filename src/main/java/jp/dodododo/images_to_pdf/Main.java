package jp.dodododo.images_to_pdf;

import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.plugins.jpeg.JPEGImageWriteParam;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class Main {
    private static final int IMAGE_SIZE = 500;

    public static void main(String... args) throws Exception {
        String dirPath = args[0];

        try (PDDocument document = new PDDocument();) {

            List<File> imageFiles = getImageFiles(new File(dirPath));

            for (File f : imageFiles) {
                PDPage page = new PDPage();
                f = toJpegFromHeic(f);
                changeImageSize(f);
                addImageIntoPage(document, page, f);
                document.addPage(page);
            }

            String today = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
            document.save(today + ".pdf");
        }
    }

    private static List<File> getImageFiles(File dir) {
        return sort(dir.listFiles((dir1, name) -> {
            String[] images = new String[]{"jpg", "jepg", "png", "HEIC"};
            for (String image : images) {
                if (name.toLowerCase().endsWith(image.toLowerCase())) {
                    return true;
                }
            }
            return false;
        }));

    }

    private static void addImageIntoPage(PDDocument document, PDPage page, File image) throws IOException {
        try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
            String file = image.getPath();
            PDImageXObject imageObj = PDImageXObject.createFromFile(file, document);
            int height = imageObj.getHeight();
            int width = imageObj.getWidth();
            if (height > IMAGE_SIZE || width > IMAGE_SIZE) {
                height = height / 2;
                width = width / 2;
                imageObj.setWidth(width);
                imageObj.setHeight(height);
            }
            page.getCOSObject().setItem(COSName.MEDIA_BOX, new PDRectangle(width, height));

            float x = 0f;
            float y = 0f;
            stream.drawImage(imageObj, x, y);
        }
    }

    private static File toJpegFromHeic(File imageFile) throws IOException, InterruptedException {
        String fileName = imageFile.getName();
        if (!fileName.toLowerCase().endsWith("heic")) {
            return imageFile;
        }
        File jpegFile = toJpegFile(imageFile);
        Runtime runtime = Runtime.getRuntime();
        Process process = runtime.exec(new String[]{"sips", "--setProperty", "format", "jpeg", imageFile.getAbsolutePath(), "--out", jpegFile.getAbsolutePath()});
        process.waitFor();
        process.destroy();
        return jpegFile;
    }

    private static File toJpegFile(File imageFile) {
        String newName = imageFile.getName().replaceAll("HEIC", "jpeg").replaceAll("heic", "jpeg");
        return new File(imageFile.getParentFile(), newName);
    }

    private static void changeImageSize(File imageFile) throws IOException {

        BufferedImage image = ImageIO.read(imageFile);
        int width = image.getWidth();
        int height = image.getHeight();
        if (width < IMAGE_SIZE && height < IMAGE_SIZE) {
            return;
        }
        int scaleW = (width / IMAGE_SIZE) + 1;
        int scaleH = (height / IMAGE_SIZE) + 1;
        int scale = Math.max(scaleW, scaleH);
        BufferedImage newImage = new BufferedImage(width / scale, height / scale, BufferedImage.TYPE_3BYTE_BGR);
        newImage.createGraphics().drawImage(image.getScaledInstance(
                        width / scale, height / scale, Image.SCALE_AREA_AVERAGING)
                , 0, 0, width / scale, height / scale, null);
        imageFile.delete();
        try (ImageOutputStream imageStream = ImageIO.createImageOutputStream(imageFile)) {
            JPEGImageWriteParam param = new JPEGImageWriteParam(Locale.getDefault());
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(1f);
            String formatName = getFormatName(imageFile.getName());
            ImageWriter writer = ImageIO.getImageWritersByFormatName(formatName).next();
            writer.setOutput(imageStream);
            writer.write(null, new IIOImage(newImage, null, null), param);
            imageStream.flush();
            writer.dispose();
        }
    }

    private static String getFormatName(String fileName) {
        String fName = fileName.toLowerCase();
        if (fName.endsWith("jpg") || fName.endsWith("jpeg")) {
            return "jpg";
        }
        if (fName.endsWith("png")) {
            return "png";
        }
        if (fName.endsWith("HEIC")) {
            return "heic";
        }
        throw new UnsupportedOperationException(fileName);
    }

    private static List<File> sort(File[] files) {
        List<File> fileList = Arrays.asList(files);
        fileList.sort((f1, f2) -> {
            long lastModified1 = f1.lastModified();
            long lastModified2 = f2.lastModified();
            return (int) (lastModified1 - lastModified2);
        });
        return fileList;
    }
}
