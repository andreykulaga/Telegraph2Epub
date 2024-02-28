package org.example;


import nl.siegmann.epublib.domain.Author;
import nl.siegmann.epublib.domain.Book;
import nl.siegmann.epublib.domain.Resource;
import nl.siegmann.epublib.epub.EpubWriter;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Random;

public class Main {
    public static void main(String[] args) {
        try {
            Book book = new Book();

            //read properties
            BufferedReader br = new BufferedReader(new FileReader("properties.txt"));
            StringBuilder jsonString = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                jsonString.append(line);
            }
            br.close();

            JSONObject jsonObject = new JSONObject(jsonString.toString());
            String bookTitle = jsonObject.getString("bookTitle");
            boolean doWeNeedToAddCover = jsonObject.getBoolean("cover");
            String authorFirstName = jsonObject.getString("authorFirstName");
            String authorLastName = jsonObject.getString("authorLastName");

            // Set the title
            book.getMetadata().addTitle(bookTitle);

            // Add an Author
            book.getMetadata().addAuthor(new Author(authorFirstName, authorLastName));


            if (doWeNeedToAddCover) {
                // Load your book cover image
                File coverImageFile = new File("cover.png");
                byte[] coverImageBytes = java.nio.file.Files.readAllBytes(coverImageFile.toPath());
                // Create a resource from the cover image
                Resource coverImageResource = new Resource(coverImageBytes, "cover.png");
                // Set the cover image resource as the cover of the book
                book.setCoverImage(coverImageResource);
            }


            String folderPath = getFolderForTempFiles();

            //read links to chapters
            BufferedReader reader = new BufferedReader(new FileReader("links.txt"));
            String urlString;
            while ((urlString = reader.readLine()) != null) {
                String apiUrl = "https://api.telegra.ph/getPage/"
                        .concat(urlString.substring(19))
                        .concat("?return_content=true");


                String title = saveHTMLContentFromTelegraphAPI(apiUrl, folderPath);

                addHtmlFile(book, title, folderPath.concat("/").concat(title).concat(".html"));

                //delete chapters
                File file = new File(folderPath.concat("/").concat(title).concat(".html"));
                file.delete();


            }
            reader.close();

            //add all pictures
            File folder = new File(folderPath);
            File[] files = folder.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (!file.getName().endsWith(".html")) {
                        InputStream inputStream = new FileInputStream(file);
                        Resource resource = new Resource(inputStream, file.getName());
                        book.addResource(resource);
                        file.delete();
                    }
                }
            }


            // Delete temp folder
            Path tempFolder = Paths.get(folderPath);
            try {
                // Delete the folder using Files.delete() method
                Files.delete(tempFolder);
                System.out.println("Temporary folder deleted successfully.");
            } catch (IOException e) {
                System.err.println("Failed to delete Temporary folder: " + e.getMessage());
            }

            // Create EpubWriter
            EpubWriter epubWriter = new EpubWriter();

            // Write the Book as Epub
            FileOutputStream outputStream = new FileOutputStream(bookTitle + " - " + authorFirstName + " " + authorLastName + ".epub");
            epubWriter.write(book, outputStream);
            outputStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String getFolderForTempFiles() {
        // Get the system's temporary directory
        String tempDir = System.getProperty("java.io.tmpdir");
        Random random = new Random();

        // Specify the path of the folder to be created
        String folderPath = tempDir + "telegraph2epub" + random.nextInt(100);

        // Create a Path object representing the folder
        Path folder = Paths.get(folderPath);

        try {
            // Create the folder using Files.createDirectory()
            Files.createDirectory(folder);
            System.out.println("Temporary folder created successfully.");
        } catch (FileAlreadyExistsException e) {
            System.err.println("Temporary folder already exists.");
        } catch (IOException e) {
            System.err.println("Failed to create Temporary folder: " + e.getMessage());
        }
        return folderPath;
    }

    private static void addHtmlFile(Book book, String title, String filePath) throws IOException {
        BufferedReader bufferedReader = new BufferedReader(new FileReader(filePath));
        Resource resource = new Resource(bufferedReader, title + ".html");
        book.addSection(title, resource);
        bufferedReader.close();
    }





    public static String saveHTMLContentFromTelegraphAPI(String apiUrl, String outputDirectory) {
        String output = "error";
        try {
            URL url = new URL(apiUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String inputLine;

                    while ((inputLine = in.readLine()) != null) {
                        response.append(inputLine);
                    }

                    JSONObject jsonResponse = new JSONObject(response.toString());
                    JSONObject result = jsonResponse.getJSONObject("result");
                    String title = result.getString("title");
                    JSONArray contentArray = result.getJSONArray("content");

                    // Extract content
                    StringBuilder htmlContentBuilder = new StringBuilder();
                    htmlContentBuilder.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd\">\n<html xmlns=\"http://www.w3.org/1999/xhtml\">\n<head>\n<meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\" />\n<title>")
                            .append(title).append("</title>\n</head>\n<body>\n<h1>").append(title).append("</h1>\n");

                    processContentArray(htmlContentBuilder, contentArray, outputDirectory);

                    htmlContentBuilder.append("</body>\n</html>");

                    String htmlContent = htmlContentBuilder.toString();


                    String outputFilePath = outputDirectory + "/" + title + ".html";
                    try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputFilePath)))) {
                        writer.write(htmlContent);
                    }
                    output = title;
                }
            } else {
                System.out.println("Failed to fetch HTML content. Response code: " + responseCode);
                return "";
            }
        } catch (IOException | JSONException e) {
            e.printStackTrace();
        }
        return output;
    }
    private static String escapeHtml(String text) {
        return text
                .replaceAll("&", "&amp;")
                .replaceAll("<", "&lt;")
                .replaceAll(">", "&gt;")
                .replaceAll("\"", "&quot;")
                .replaceAll("'", "&apos;");
    }


    private static void processContentArray(StringBuilder htmlContentBuilder, JSONArray contentArray, String folderPath) {
        if (contentArray != null) {
            for (Object contentObj:contentArray) {
                if (contentObj instanceof String) {
                    // Escape special characters
                    String escapedText = escapeHtml((String) contentObj);
                    htmlContentBuilder.append(escapedText);
                } else if (contentObj instanceof JSONObject childObj) {
                    String childTag = childObj.optString("tag");
                    JSONArray grandChildren = childObj.optJSONArray("children");

                    //image and video processing
                    if (childTag.equalsIgnoreCase("img") || childTag.equalsIgnoreCase("video")) {
                        processImageOrVideo(htmlContentBuilder, childObj, folderPath);
                    } else {
                        //all other tags
                        if (!childTag.isEmpty()) {
                            htmlContentBuilder.append("<").append(childTag).append(">");
                        }
                        processContentArray(htmlContentBuilder, grandChildren, folderPath);

                        if (!childTag.isEmpty()) {
                            htmlContentBuilder.append("</").append(childTag).append(">");
                        }
                    }

                }
            }
        }
    }

    private static void processImageOrVideo(StringBuilder htmlContentBuilder, JSONObject childObj, String folderPath) {
        String childTag = childObj.optString("tag");
        if (childTag.equalsIgnoreCase("img")) {
            JSONObject attrs = childObj.getJSONObject("attrs");
            String sourceUrl = "https://telegra.ph".concat(attrs.getString("src"));

            URL url;
            try {
                url = new URL(sourceUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");

                int responseCode = conn.getResponseCode();
                //check that type of image is supported by epub from the box
                String contentType = conn.getHeaderField("Content-Type");
                boolean contentTypeSupportedByEpub = contentType.equalsIgnoreCase("image/png") ||
                        contentType.equalsIgnoreCase("image/jpeg") ||
                        contentType.equalsIgnoreCase("image/gif") ||
                        contentType.equalsIgnoreCase("image/svg+xml");

                if (responseCode == HttpURLConnection.HTTP_OK && contentTypeSupportedByEpub) {
                    InputStream inputStream = conn.getInputStream();

                    String fileName = attrs.getString("src").substring(6); // index 6 because starts with "/file/

                    OutputStream outputStream = new FileOutputStream(folderPath + "/" + fileName);

                    byte[] buffer = new byte[2048];
                    int length;

                    while ((length = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, length);
                    }

                    inputStream.close();
                    outputStream.close();

                    //add to resulted HTML
                    htmlContentBuilder.append("<img src=\"").append(fileName).append("\" alt=\"image\" />");
                } else {
                    System.out.println("Failed to fetch image. Response code: " + responseCode);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            htmlContentBuilder.append("<p>").append(childTag).append(" is not supported").append("</p>");
        }
    }
}