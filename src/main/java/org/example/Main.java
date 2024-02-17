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


            //read links to chapters
            BufferedReader reader = new BufferedReader(new FileReader("links.txt"));
            String urlString;
            while ((urlString = reader.readLine()) != null) {
                String apiUrl = "https://api.telegra.ph/getPage/"
                        .concat(urlString.substring(19))
                        .concat("?return_content=true");

                String title = saveHTMLContentFromTelegraphAPI(apiUrl, "src/main/resources/");

                addHtmlFile(book, title, "src/main/resources/".concat(title).concat(".html"));

                //delete chapters
                File file = new File("src/main/resources/".concat(title).concat(".html"));
                file.deleteOnExit();

            }
            reader.close();

// Create EpubWriter
            EpubWriter epubWriter = new EpubWriter();

// Write the Book as Epub
            epubWriter.write(book, new FileOutputStream(bookTitle + " - " + authorFirstName + " " + authorLastName + ".epub"));
        } catch (Exception e) {
            e.printStackTrace();
        }
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
                try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
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

                    processContentArray(htmlContentBuilder, contentArray);

                    htmlContentBuilder.append("</body>\n</html>");

                    String htmlContent = htmlContentBuilder.toString();


                    String titleWithReplacements = title;
                    String outputFilePath = outputDirectory + titleWithReplacements + ".html";
                    try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputFilePath)))) {
                        writer.write(htmlContent);
                    }

                    System.out.println("HTML content saved to: " + outputFilePath);
                    output = titleWithReplacements;
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

//    private static void processChildren(StringBuilder htmlContentBuilder, JSONArray children) {
//        for (int j = 0; j < children.length(); j++) {
//            Object child = children.get(j);
//            if (child instanceof String) {
//                // Escape special characters
//                String escapedText = escapeHtml((String) child);
//                htmlContentBuilder.append(escapedText);
//            } else if (child instanceof JSONObject) {
//                JSONObject childObj = (JSONObject) child;
//                String tag = childObj.optString("tag");
//                String text = childObj.optString("text");
//                if (tag != null && !tag.isEmpty() && text != null && !text.isEmpty()) {
//                    htmlContentBuilder.append("<").append(tag).append(">");
//                    // Escape special characters
//                    String escapedText = escapeHtml(text);
//                    htmlContentBuilder.append(escapedText);
//                    htmlContentBuilder.append("</").append(tag).append(">");
//                }
//            }
//        }
//    }

    private static void processContentArray(StringBuilder htmlContentBuilder, JSONArray contentArray) {
        if (contentArray != null) {
            for (Object contentObj:contentArray) {
                if (contentObj instanceof String) {
                    // Escape special characters
                    String escapedText = escapeHtml((String) contentObj);
                    htmlContentBuilder.append(escapedText);
                } else if (contentObj instanceof JSONObject) {
                    JSONObject childObj = (JSONObject) contentObj;
                    String childTag = childObj.optString("tag");
                    JSONArray grandChildren = childObj.optJSONArray("children");

                    if (childTag != null && !childTag.isEmpty()) {
                        htmlContentBuilder.append("<").append(childTag).append(">");
                    }
                    processContentArray(htmlContentBuilder, grandChildren);

                    if (childTag != null && !childTag.isEmpty()) {
                        htmlContentBuilder.append("</").append(childTag).append(">");
                    }
                }
            }
        }
    }

//    private static void processChildren(StringBuilder htmlContentBuilder, JSONArray children, String tag) {
//        for (int j = 0; j < children.length(); j++) {
//            Object child = children.get(j);
//            if (child instanceof String) {
//                // Escape special characters
//                String escapedText = escapeHtml((String) child);
//                if (tag != null && !tag.isEmpty()) {
//                    htmlContentBuilder.append("<").append(tag).append(">");
//                    htmlContentBuilder.append(escapedText);
//                    htmlContentBuilder.append("</").append(tag).append(">");
//                } else {
//                    htmlContentBuilder.append(escapedText);
//                }
//
//            } else if (child instanceof JSONObject) {
//                JSONObject childObj = (JSONObject) child;
//                String childTag = childObj.optString("tag");
//                JSONArray grandChildren = childObj.optJSONArray("children");
//                processChildren(htmlContentBuilder, grandChildren, childTag);
////                String text = childObj.optString("text");
////                if (tag != null && !tag.isEmpty()) {
////                    htmlContentBuilder.append("<").append(tag).append(">");
////                    // Escape special characters
////                    String escapedText = escapeHtml(text);
////                    htmlContentBuilder.append(escapedText);
////                    htmlContentBuilder.append("</").append(tag).append(">");
////                }
//            }
//        }
//    }

//    private static void processChildren(StringBuilder htmlContentBuilder, JSONArray children) {
//        htmlContentBuilder.append("<p>");
//                            for (int j = 0; j < children.length(); j++) {
//                                Object child = children.get(j);
//                                if (child instanceof String) {
//                                    // Escape special characters
//                                    String escapedText = escapeHtml((String) child);
//                                    htmlContentBuilder.append(escapedText);
//                                } else if (child instanceof JSONObject) {
//                                    JSONObject childObj = (JSONObject) child;
//                                    String text = childObj.optString("text");
//                                    if (text != null && !text.isEmpty()) {
//                                        // Escape special characters
//                                        String escapedText = escapeHtml(text);
//                                        htmlContentBuilder.append("<em>").append(escapedText).append("</em>");
//                                    }
//                                }
//                            }
//                            htmlContentBuilder.append("</p>\n");
//    }
}