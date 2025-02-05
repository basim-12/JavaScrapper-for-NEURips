# NIPS Papers Scraper

This Java-based web scraper extracts metadata and PDF links from NIPS (NeurIPS) conference papers. It stores the data in a CSV file and downloads the PDFs to local directories by year.

## Features

- Scrapes paper metadata (title, authors, year, PDF link) from multiple years of NIPS.
- Downloads PDFs into year-specific folders.
- Stores metadata in `papers_metadata.csv`.
- Retry mechanism with exponential backoff for network errors.
- Progress bar for individual paper downloads and overall progress.

## Requirements

- Java 8 or higher
- [Jsoup](https://jsoup.org/) for HTML parsing
- [Apache HttpClient](https://hc.apache.org/httpcomponents-client-5.1.x/) for HTTP requests

## Installation

1. Clone this repository:
   ```bash
   git clone https://github.com/yourusername/nips-papers-scraper.git
2. Add the required dependencies: Jsoup and Apache HttpClient to your project.
3. Compile and run the Scraper class.
   
## Usage

1. Run the Scraper class.
2. The scraper will:
   -Scrape metadata and download PDFs from NIPS papers.
   -Store metadata in papers_metadata.csv.
   -Create year-specific directories to save PDFs.
3. CSV Format:
   -"Title", "Year", "Authors", "PDF Link"


## Download Progress

Progress bars are shown for each paper download and overall progress.
Updates are printed in the terminal during the download process.


## Customization

1. Thread Pool Size: Adjust the thread pool size (newFixedThreadPool(10)) for more or fewer threads.
2. CSV Path: Change the CSV_FILE_PATH constant to customize the CSV location.
3. Retries & Timeouts: Adjust the retry count and timeouts with constants like MAX_RETRIES and TIMEOUT.

   Published by basim-12
