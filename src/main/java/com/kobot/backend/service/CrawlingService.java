package com.kobot.backend.service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.Set;

@Slf4j
@Service
public class CrawlingService {


    private static final int MAX_DEPTH = 5;

    // 사용자 input url parsing 과정이 중복되어 느려져서 해당 부분 분리
    public void startCrawling(String startUrl) throws IOException, URISyntaxException {

        Map<String, String> subLinks = new HashMap<>();

        // input된 url 인코딩 진행
        String encodedUrl = encodeUrl(startUrl);
        URI startUri = new URI(encodedUrl);
        String domain = startUri.getHost();

        List<String> disallowedPaths = new ArrayList<>();

        // robots.txt 파일에서 Disallow 경로 가져오기
        loadRobotsTxt(startUri, disallowedPaths);

        crawlPage(encodedUrl, domain, 0, disallowedPaths, subLinks);

        for (Map.Entry<String, String> entry : subLinks.entrySet()) {
            System.out.println("Key: " + entry.getKey() + ", Value: " + entry.getValue());
        }

    }

    public void crawlPage(String url, String domain, int depth, List<String> disallowedPaths
        , Map<String, String> subLinks) throws IOException, URISyntaxException {
        if (depth > MAX_DEPTH) {
            return;
        }

        if (!subLinks.containsKey(url)) {

            if (isDisallowed(url, disallowedPaths)) {
                log.warn("URL is disallowed by robots.txt: {}", url);
                return;
            }

            // 크롤링 시, 웹 콘텐츠가 삭제된 url에 대해 try-catch 구문을 작성해 크롤링 종료 안되게 처리
            try {
                // 크롤링 할 url의 contentType 무시하는 설정
                Connection connection = Jsoup.connect(url).ignoreContentType(true);
                Document document = connection.get();

                String text = document.body().text();
                subLinks.put(url, text);  // URL과 크롤링된 텍스트를 Map에 저장

                Elements links = document.select("a[href]");

                for (Element link : links) {
                    String absHref = link.attr("abs:href");
                    URI linkUri = null;

                    try {
                        linkUri = new URI(absHref);
                    } catch (URISyntaxException e) {
                        String encodedUrl = encodeUrl(absHref);
                        linkUri = new URI(encodedUrl);
                    }

                    if (linkUri.getHost() != null && linkUri.getHost().equals(domain))
                        crawlPage(absHref, domain, depth + 1, disallowedPaths, subLinks);

                }
            } catch (IOException e) {
                log.error("Failed to crawl the website: URL={}, Error Message={}", url
                    , e.getMessage());
            }
        }
    }

    // robots.txt 파일 로드 및 Disallow 경로 파싱
    private void loadRobotsTxt(URI startUri, List<String> disallowedPaths) throws IOException {
        String robotsUrl = startUri.getScheme() + "://" + startUri.getHost() + "/robots.txt";
        String line;

        try(BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(
            new URI(robotsUrl).toURL().openStream()))) {
            while ((line = bufferedReader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("Disallow:")) {
                    String disallowPath = line.split(":", 2)[1].trim();
                    disallowedPaths.add(disallowPath);
                }
            }
        } catch (IOException | URISyntaxException e) {
            log.warn("Failed to load robots.txt, assuming no restrictions.");
        }
    }

    // 주어진 URL이 Disallow 목록에 있는지 확인
    private boolean isDisallowed(String url, List<String> disallowedPaths) throws URISyntaxException {
        URI uri = new URI(url);
        String path = uri.getPath();

        for (String disallowedPath : disallowedPaths) {
            if (path.startsWith(disallowedPath))
                return true;
        }
        return false;
    }

    // url에 있는 공백, 한글 인코딩
    private String encodeUrl(String url) throws URISyntaxException, IOException {
        URL urlObj = new URL(url);
        URI uri = new URI(
            urlObj.getProtocol(),
            urlObj.getUserInfo(),
            urlObj.getHost(),
            urlObj.getPort(),
            urlObj.getPath(),
            urlObj.getQuery(),
            urlObj.getRef()
        );
        return uri.toASCIIString();  // 인코딩된 URL 반환
    }
}