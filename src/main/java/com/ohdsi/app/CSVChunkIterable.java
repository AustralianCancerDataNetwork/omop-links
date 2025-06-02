package com.ohdsi.app;

import org.apache.commons.csv.*;
import java.io.*;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Iterator;

public class CSVChunkIterable implements Iterable<List<Map<String, String>>> {

    private final Reader reader;
    private final int chunkSize;
    private final char delimiter;

    public CSVChunkIterable(File csvFile, int chunkSize) throws FileNotFoundException, IOException {

        BufferedReader br = new BufferedReader(new FileReader(csvFile));
        String headerLine = br.readLine();

        this.reader = new FileReader(csvFile);
        this.chunkSize = chunkSize;
        this.delimiter = detectDelimiter(headerLine);
    }

    private static char detectDelimiter(String line) {
        int commaCount = line.split(",", -1).length;
        int tabCount = line.split("\t", -1).length;
        return (tabCount > commaCount) ? '\t' : ',';
    }

    @Override
    public Iterator<List<Map<String, String>>> iterator() {
        try {
            CSVParser parser = new CSVParser(reader,
                    CSVFormat.DEFAULT
                            .withDelimiter(delimiter)
                            .withQuote(null)
                            .withFirstRecordAsHeader()
            );
            return new CSVChunkIterator(parser, chunkSize);
        } catch (IOException e) {
            throw new RuntimeException("Failed to open CSV parser", e);
        }
    }

    static class CSVChunkIterator implements Iterator<List<Map<String, String>>> {
        private final Iterator<CSVRecord> innerIterator;
        private final int chunkSize;
        private final CSVParser parser;

        public CSVChunkIterator(CSVParser parser, int chunkSize) {
            this.parser = parser;
            this.innerIterator = parser.iterator();
            this.chunkSize = chunkSize;
        }

        @Override
        public boolean hasNext() {
            return innerIterator.hasNext();
        }

        @Override
        public List<Map<String, String>> next() {
            List<Map<String, String>> chunk = new ArrayList<>(chunkSize);
            int count = 0;
            while (innerIterator.hasNext() && count < chunkSize) {
                CSVRecord record = innerIterator.next();
                Map<String, String> labeledRow = new HashMap<>();
                for (String header : record.getParser().getHeaderNames()) {
                    labeledRow.put(header, record.get(header));
                }
                chunk.add(labeledRow);
                count++;
            }
            return chunk;
        }
//        public List<CSVRecord> next() {
//            List<CSVRecord> chunk = new ArrayList<>(chunkSize);
//            int count = 0;
//            while (innerIterator.hasNext() && count < chunkSize) {
//                chunk.add(innerIterator.next());
//                count++;
//            }
//            return chunk;
//        }
    }
}

//
//    public static List<CSVRecord> readNextChunk(CSVParser parser, Iterator<CSVRecord> iterator, int chunkSize) {
//        List<CSVRecord> chunk = new ArrayList<>();
//        int count = 0;
//        while (iterator.hasNext() && count < chunkSize) {
//            chunk.add(iterator.next());
//            count++;
//        }
//        return chunk;
//    }
//
//
//    public static void loadCsv(File file) throws IOException {
//        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
//            String headerLine = reader.readLine();
//            char delimiter = detectDelimiter(headerLine);
//
//            try (Reader fullReader = new FileReader(file);
//                 CSVParser parser = CSVFormat.DEFAULT
//                         .withDelimiter(delimiter)
//                         .withFirstRecordAsHeader()
//                         .parse(fullReader)) {
//
//                for (CSVRecord record : parser) {
//                    System.out.println("First column: " + record.get(0));
//                    break;
//                }
//            }
//        }
//    }
//
//
//    public static void loadCsv(File file) throws IOException {
//        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
//            String headerLine = reader.readLine();
//            char delimiter = detectDelimiter(headerLine);
//
//            try (Reader fullReader = new FileReader(file);
//                CSVParser parser = CSVFormat.DEFAULT
//                        .withDelimiter(delimiter)
//                        .withFirstRecordAsHeader()
//                        .parse(fullReader)) {
//
//                    Iterator<CSVRecord> iterator = parser.iterator();
//                    int chunkSize = 1000;
//                    int chunkNumber = 1;
//
//            while (iterator.hasNext()) {
//                List<CSVRecord> chunk = readNextChunk(parser, iterator, chunkSize);
//                System.out.println("Chunk " + chunkNumber + " has " + chunk.size() + " rows");
//
//                // Do something with the chunk here
//                // e.g., process or convert to your own Row object
//
//                chunkNumber++;
//            }
//        }
//    }

//}