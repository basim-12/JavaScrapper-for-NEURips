package test;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

public class Scraper {
    private static final int MAX_RETRIES = 3;
    private static final int TIMEOUT = 20000; // Increased timeout to 20 seconds
    private static final ExecutorService executorService = Executors.newFixedThreadPool(10); // Shared thread pool
    private static final String CSV_FILE_PATH = "papers_metadata.csv"; // Path to the CSV file

    public static void main(String[] args) {
        String baseUrl = "https://papers.nips.cc/";

        try {
            // Initialize CSV file with headers
            initializeCsvFile();

            System.out.println("Connecting to: " + baseUrl);
            Document mainPage = Jsoup.connect(baseUrl)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/94.0.4606.61 Safari/537.36")
                .timeout(TIMEOUT)
                .get();

            System.out.println("Successfully connected to the main page.");

            Elements yearLinks = mainPage.select("body > div.container-fluid > div.col-sm > ul > li > a"); // ul > lis
            if (yearLinks.isEmpty()) {
                System.out.println("No year links found.");
            }
            for (Element yearLink : yearLinks) {
                String yearUrl = yearLink.attr("href");
                String fullYearUrl = baseUrl + yearUrl;

                // Extract the year from the URL
                String year = extractYearFromUrl(yearUrl);
                System.out.println("Processing year: " + year);

                // Process one year fully before moving to the next
                scrapeYearlyPage(fullYearUrl, baseUrl, year);
            }

            executorService.shutdown();
            if (!executorService.awaitTermination(5, TimeUnit.MINUTES)) {
                System.out.println("Executor did not terminate in the specified time.");
                executorService.shutdownNow();
            }

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            Thread.currentThread().interrupt();
        }
    }

    private static void initializeCsvFile() {
        try (FileWriter writer = new FileWriter(CSV_FILE_PATH)) {
            // Write CSV header
            writer.append("Title,Year,Authors,PDF Link\n");
            System.out.println("Initialized CSV file with headers.");
        } catch (IOException e) {
            System.err.println("Failed to initialize CSV file: " + e.getMessage());
        }
    }

    private static String extractYearFromUrl(String url) {
        String[] parts = url.split("/");
        for (String part : parts) {
            if (part.matches("\\d{4}")) {
                return part;
            }
        }
        return null;
    }

    private static void scrapeYearlyPage(String yearUrl, String baseUrl, String year) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                System.out.println("Scraping yearly page: " + yearUrl + " (Attempt " + attempt + ")");
                Document yearPage = Jsoup.connect(yearUrl)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/94.0.4606.61 Safari/537.36")
                    .timeout(TIMEOUT)
                    .get();

                Elements paperLinks = yearPage.select("a[href*='/paper/']");
                if (paperLinks.isEmpty()) {
                    System.out.println("No paper links found for: " + yearUrl);
                    return;
                }

                AtomicInteger completedDownloads = new AtomicInteger(0);
                int totalPapers = paperLinks.size();

                // Create a folder for the year
                File yearFolder = new File(year);
                if (!yearFolder.exists()) {
                    if (yearFolder.mkdir()) {
                        System.out.println("Created folder for year: " + year);
                    } else {
                        System.err.println("Failed to create folder for year: " + year);
                        return;
                    }
                }

                for (Element paperLink : paperLinks) {
                    String paperUrl = paperLink.attr("href");
                    String fullPaperUrl = baseUrl + paperUrl;

                    scrapePaperPage(fullPaperUrl, year, completedDownloads, totalPapers);
                }

                while (completedDownloads.get() < totalPapers) {
                    try {
                        Thread.sleep(2000); // Wait for downloads to complete
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                return; // Exit retry loop if successful
            } catch (IOException e) {
                System.err.println("Failed to scrape yearly page: " + yearUrl + " (Attempt " + attempt + ")");
                if (attempt == MAX_RETRIES) e.printStackTrace();
                try {
                    Thread.sleep(2000 * attempt); // Exponential backoff
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private static void scrapePaperPage(String paperUrl, String year, AtomicInteger completedDownloads, int totalPapers) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                System.out.println("Scraping paper page: " + paperUrl + " (Attempt " + attempt + ")");
                Document paperPage = Jsoup.connect(paperUrl)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/94.0.4606.61 Safari/537.36")
                    .timeout(TIMEOUT)
                    .get();

                // Extract title
                String title = paperPage.select("h4").first().text();

                // Extract authors
                String authors = "N/A"; // Default value if authors are not found
                Element authorHeader = paperPage.selectFirst("h4:contains(Authors)");
                if (authorHeader != null) {
                    Element authorElement = authorHeader.nextElementSibling();
                    if (authorElement != null) {
                        authors = authorElement.text().trim();
                    }
                }

                // Print authors to console for debugging
                System.out.println("Authors: " + authors);

                // Extract PDF link
                Element pdfLink = paperPage.select("a:contains(Paper)").first();
                if (pdfLink == null) {
                    System.out.println("No PDF link found for: " + paperUrl);
                    completedDownloads.incrementAndGet();
                    return;
                }
                String pdfUrl = pdfLink.attr("href");
                String fullPdfUrl = pdfUrl.startsWith("http") ? pdfUrl : "https://papers.nips.cc" + pdfUrl;

                // Download PDF
                PdfDownloader.downloadPdf(fullPdfUrl, year, completedDownloads, totalPapers);

                // Write metadata to CSV
                writeMetadataToCsv(title, year, authors, fullPdfUrl);

                return; // Exit retry loop if successful
            } catch (IOException e) {
                System.err.println("Failed to scrape paper page: " + paperUrl + " (Attempt " + attempt + ")");
                if (attempt == MAX_RETRIES) e.printStackTrace();
                try {
                    Thread.sleep(2000 * attempt);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
    private static void writeMetadataToCsv(String title, String year, String authors, String pdfLink) {
        try (FileWriter writer = new FileWriter(CSV_FILE_PATH, true)) {
            // Append metadata to CSV
            writer.append(String.format("\"%s\",%s,\"%s\",%s\n", title, year, authors, pdfLink));
            System.out.println("Metadata written to CSV for: " + title);
        } catch (IOException e) {
            System.err.println("Failed to write metadata to CSV: " + e.getMessage());
        }
    }
}

class PdfDownloader {
    private static final DecimalFormat PROGRESS_FORMAT = new DecimalFormat("0.00");
    private static final int MAX_RETRIES = 5;
    private static final int INITIAL_RETRY_DELAY = 1000;
    private static final int CONNECTION_TIMEOUT = 10000;
    private static final int SOCKET_TIMEOUT = 10000;
    private static final int PROGRESS_BAR_LENGTH = 50;

    public static void downloadPdf(String pdfUrl, String year, AtomicInteger completedDownloads, int totalPapers) {
        int retryCount = 0;
        boolean success = false;

        while (retryCount < MAX_RETRIES && !success) {
            try (CloseableHttpClient httpClient = HttpClients.custom()
                    .setDefaultRequestConfig(RequestConfig.custom()
                            .setConnectTimeout(CONNECTION_TIMEOUT)
                            .setSocketTimeout(SOCKET_TIMEOUT)
                            .build())
                    .build()) {

                HttpGet request = new HttpGet(pdfUrl);
                request.setHeader("User-Agent", "Mozilla/5.0");

                HttpResponse response = httpClient.execute(request);
                String fileName = pdfUrl.substring(pdfUrl.lastIndexOf('/') + 1);
                File outputFile = new File(year + File.separator + fileName); // Save in the year folder
                long contentLength = response.getEntity().getContentLength();

                try (InputStream inputStream = response.getEntity().getContent();
                     FileOutputStream outputStream = new FileOutputStream(outputFile)) {

                    byte[] buffer = new byte[1024];
                    int bytesRead;
                    long totalBytesRead = 0;
                    double lastPrintedProgress = -1; // Track last progress printed to avoid excessive printing

                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                        totalBytesRead += bytesRead;

                        // Calculate progress
                        double progress = (double) totalBytesRead / contentLength;
                        int completedBars = (int) (progress * PROGRESS_BAR_LENGTH);
                        int remainingBars = PROGRESS_BAR_LENGTH - completedBars;

                        // Ensure progress updates only when it changes
                        if (Math.abs(progress - lastPrintedProgress) >= 0.01) {
                            lastPrintedProgress = progress;

                            // Build progress bar string
                            String progressBar = "[" + "=".repeat(completedBars) + (remainingBars > 0 ? ">" : "") +
                                    " ".repeat(Math.max(remainingBars - 1, 0)) + "]";
                            String progressText = String.format("\rYear: %s | Downloading %s %s %.2f%%", 
                                    year, fileName, progressBar, progress * 100);

                            // Clear previous progress and print updated progress bar
                            System.out.print("\r" + " ".repeat(100) + "\r" + progressText);
                            System.out.flush();
                        }
                    }
                }

                // Increment completed downloads
                completedDownloads.incrementAndGet();

                // Clear previous progress and show overall progress
                double overallProgress = (double) completedDownloads.get() / totalPapers;
                int completedOverallBars = (int) (overallProgress * PROGRESS_BAR_LENGTH);
                int remainingOverallBars = PROGRESS_BAR_LENGTH - completedOverallBars;

                String overallProgressBar = "[" + "=".repeat(completedOverallBars) + (remainingOverallBars > 0 ? ">" : "") +
                        " ".repeat(Math.max(remainingOverallBars - 1, 0)) + "]";
                String overallProgressText = String.format("\rYear: %s | Overall Progress %s %.2f%%", 
                        year, overallProgressBar, overallProgress * 100);

                System.out.print("\r" + " ".repeat(100) + "\r" + overallProgressText);
                System.out.flush();

                success = true;
            } catch (Exception e) {
                retryCount++;
                if (retryCount < MAX_RETRIES) {
                    System.err.println("\nRetry " + retryCount + " for: " + pdfUrl + " | Error: " + e.getMessage());
                    try {
                        TimeUnit.MILLISECONDS.sleep(INITIAL_RETRY_DELAY * (int) Math.pow(2, retryCount - 1));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                } else {
                    System.err.println("\nFailed to download: " + pdfUrl + " after " + MAX_RETRIES + " retries.");
                }
            }
        }
    }
}