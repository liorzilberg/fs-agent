package org.whitesource.agent.dependency.resolver.html;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.whitesource.agent.Constants;
import org.whitesource.agent.DependencyInfoFactory;
import org.whitesource.agent.api.model.DependencyInfo;
import org.whitesource.agent.api.model.DependencyType;
import org.whitesource.agent.dependency.resolver.AbstractDependencyResolver;
import org.whitesource.agent.dependency.resolver.ResolutionResult;
import org.whitesource.agent.utils.FilesUtils;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by anna.rozin
 */
public class HtmlDependencyResolver extends AbstractDependencyResolver {

    /* --- Static members --- */

    private final Logger logger = LoggerFactory.getLogger(HtmlDependencyResolver.class);

    private final String[] archiveIncludesPattern = {Constants.PATTERN + Constants.HTML, Constants.PATTERN + Constants.HTM};

    private final Pattern patternOfFirstLetter = Pattern.compile("[a-zA-Z].*");
    private final Pattern patternOfLegitSrcUrl = Pattern.compile("<%.*%>");
    public static final String WHITESOURCE_HTML_RESOLVER = "whitesource-html-resolver";

    /* --- Overridden methods --- */

    @Override
    protected ResolutionResult resolveDependencies(String projectFolder, String topLevelFolder, Set<String> bomFiles) {
        Collection<DependencyInfo> dependencies = new LinkedList<>();
        for (String htmlFile : bomFiles) {
            Document htmlFileDocument;
            try {
                htmlFileDocument = Jsoup.parse(new File(htmlFile), "UTF-8");
                Elements script = htmlFileDocument.getElementsByAttribute(Constants.SRC);
                //create list of links for .js files for each html file
                List<String> scriptUrls = new LinkedList<>();
                for (Element srcLink : script) {
                    String src = srcLink.attr(Constants.SRC);
                    if (src != null && isLegitSrcUrl(src)) {
                        String srcUrl = fixUrls(src);
                        if (srcUrl != null) {
                            scriptUrls.add(srcUrl);
                        }
                    }
                }
                dependencies.addAll(collectJsFilesAndCalcHashes(scriptUrls, htmlFile));
            } catch (IOException e) {
                logger.debug("Cannot parse the html file: {}", htmlFile);
            }
        }
        // check the type and excludes
        return new ResolutionResult(dependencies, getExcludes(), getDependencyType(), topLevelFolder);
    }

    private boolean isLegitSrcUrl(String srcUrl) {
        if (srcUrl.endsWith(Constants.JS_EXTENSION)) {
            Matcher matcher = this.patternOfLegitSrcUrl.matcher(srcUrl);
            if (!matcher.find()) {
                return true;
            }
        }
        return false;
    }

    private List<DependencyInfo> collectJsFilesAndCalcHashes(List<String> scriptUrls, String htmlFilePath) {
        List<DependencyInfo> dependencies = new LinkedList<>();
        String tempFolder = new FilesUtils().createTmpFolder(false, WHITESOURCE_HTML_RESOLVER);
        File tempFolderFile = new File(tempFolder);
        int counterForName = 0;
        RestTemplate restTemplate = new RestTemplate();
        if (tempFolder != null) {
            for (String scriptUrl : scriptUrls) {
                URI uriScopeDep;
                try {
                    uriScopeDep = new URI(scriptUrl);
                    HttpHeaders httpHeaders = new HttpHeaders();
                    HttpEntity entity = new HttpEntity(httpHeaders);
                    String body = restTemplate.exchange(uriScopeDep, HttpMethod.GET, entity, String.class).getBody();
                    String dependencyFileName = tempFolder + File.separator + counterForName + Constants.JS_EXTENSION;
                    PrintWriter writer;
                    writer = new PrintWriter(dependencyFileName, "UTF-8");
                    writer.println(body);
                    writer.close();
                    DependencyInfoFactory dependencyInfoFactory = new DependencyInfoFactory();
                    DependencyInfo dependencyInfo = dependencyInfoFactory.createDependencyInfo(tempFolderFile, counterForName + Constants.JS_EXTENSION);
                    if (dependencyInfo != null) {
                        dependencies.add(dependencyInfo);
                        dependencyInfo.setSystemPath(htmlFilePath);
                    }
                } catch (RestClientException e) {
                    logger.debug("Could not reach the registry using the URL: {}. Got an error: {}", scriptUrl, e.getMessage());
                } catch (URISyntaxException e) {
                    logger.debug("Failed creating uri of {}", scriptUrl);
                } catch (IOException e) {
                    logger.debug("Failed writing to file");
                }
                counterForName++;
            }
            FilesUtils.deleteDirectory(tempFolderFile);
        }
        return dependencies;
    }

    private String fixUrls(String scriptUrl) {
        if (scriptUrl.startsWith(Constants.HTTP) || scriptUrl.startsWith(Constants.HTTPS)) {
            return scriptUrl;
        }
        Matcher matcher = this.patternOfFirstLetter.matcher(scriptUrl);
        matcher.find();
        if (matcher.group(0) != null) {
            return Constants.HTTP + "://" + matcher.group(0);
        } else {
            return null;
        }
    }

    @Override
    protected Collection<String> getExcludes() {
        return new ArrayList<>();
    }

    @Override
    protected Collection<String> getSourceFileExtensions() {
        return Arrays.asList(Constants.HTML, Constants.HTM);
    }

    @Override
    protected DependencyType getDependencyType() {
        return null;
    }

    @Override
    protected String[] getBomPattern() {
        return new String[]{Constants.PATTERN + Constants.DOT + Constants.HTML,
                Constants.PATTERN + Constants.DOT + Constants.HTM} ;
    }

    @Override
    protected Collection<String> getLanguageExcludes() {
        return new ArrayList<>();
    }
}
