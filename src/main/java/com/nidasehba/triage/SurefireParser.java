package com.nidasehba.triage;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/** Reads the JUnit-format XML that Maven Surefire, TestNG and most CI tools produce. */
public final class SurefireParser {

    private SurefireParser() {
    }

    /** Every failure and error in the TEST-*.xml files of a directory, in file order. */
    public static List<TestFailure> parseDirectory(Path dir) throws Exception {
        List<Path> files;
        try (Stream<Path> s = Files.list(dir)) {
            files = s.filter(p -> p.getFileName().toString().matches("TEST-.*\\.xml")).sorted().toList();
        }
        List<TestFailure> failures = new ArrayList<>();
        for (Path f : files) {
            failures.addAll(parse(f));
        }
        return failures;
    }

    public static List<TestFailure> parse(Path file) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        // Reports can come from anywhere, so refuse external entities (XXE).
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc;
        try {
            doc = builder.parse(file.toFile());
        } catch (IOException e) {
            throw new IOException("Cannot read " + file, e);
        }

        List<TestFailure> failures = new ArrayList<>();
        NodeList cases = doc.getElementsByTagName("testcase");
        for (int i = 0; i < cases.getLength(); i++) {
            Element tc = (Element) cases.item(i);
            for (String tag : List.of("failure", "error")) {
                NodeList problems = tc.getElementsByTagName(tag);
                if (problems.getLength() > 0) {
                    Element p = (Element) problems.item(0);
                    failures.add(new TestFailure(tc.getAttribute("classname"), tc.getAttribute("name"),
                            p.getAttribute("type"), p.getAttribute("message"), p.getTextContent().strip()));
                }
            }
        }
        return failures;
    }
}
